package io.macula.cam2me

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.macula.sdk.FfiKeyPair
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import io.macula.cam2me.data.Cam2MeDatabase
import io.macula.cam2me.data.Contact
import io.macula.cam2me.data.DialTarget
import io.macula.cam2me.data.KnownStation
import io.macula.cam2me.identity.IdentityStore
import io.macula.cam2me.location.LocationFix
import io.macula.cam2me.mesh.MeshSessionPool
import io.macula.cam2me.mesh.PresenceHeartbeat
import io.macula.cam2me.mesh.StationDiscovery
import io.macula.cam2me.settings.SettingsStore
import io.macula.cam2me.ui.ContactListScreen
import io.macula.cam2me.ui.ContactRow
import io.macula.cam2me.ui.OnboardingScreen
import io.macula.cam2me.ui.SettingsScreen

/**
 * Wires identity persistence, nearest-station discovery, mesh connect, the
 * presence heartbeat and the contact list together. Camera capture,
 * dial/pickup and a real call UI are the next feature pass, not this one.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var isReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isReady }

        val db = Cam2MeDatabase.getInstance(applicationContext)

        setContent {
            MaterialTheme {
                Cam2MeApp(this, db, onReady = { isReady = true })
            }
        }
    }
}

@Composable
private fun Cam2MeApp(activity: ComponentActivity, db: Cam2MeDatabase, onReady: () -> Unit) {
    val context = activity.applicationContext

    val identity by produceState<FfiKeyPair?>(initialValue = null) {
        value = IdentityStore.loadOrCreate(context)
    }
    val settings by SettingsStore.observe(context).collectAsStateWithLifecycle(initialValue = null)

    val currentIdentity = identity
    val currentSettings = settings
    if (currentIdentity == null || currentSettings == null) {
        return
    }
    LaunchedEffect(Unit) { onReady() }

    if (currentSettings.myPhoneNumber == null) {
        OnboardingScreen(onPhoneNumberEntered = { phoneNumber ->
            activity.lifecycleScope.launch {
                SettingsStore.setMyPhoneNumber(context, phoneNumber)
            }
        })
        return
    }

    ConnectedApp(activity, db, currentIdentity, currentSettings, currentSettings.myPhoneNumber)
}

private sealed class AppScreen {
    object Contacts : AppScreen()
    object Settings : AppScreen()
}

@Composable
private fun ConnectedApp(
    activity: ComponentActivity,
    db: Cam2MeDatabase,
    identity: FfiKeyPair,
    settings: SettingsStore.Settings,
    myPhoneNumber: String,
) {
    val context = activity.applicationContext
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Contacts) }
    var heartbeats by remember { mutableStateOf<List<PresenceHeartbeat>>(emptyList()) }

    var locationGranted by remember { mutableStateOf(LocationFix.hasPermission(context)) }
    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locationGranted = granted }

    // Auto discovery needs the permission dialog exactly once per launch,
    // not once per recomposition -- Unit as the key, not settings.autoDiscovery,
    // so turning auto mode off and back on in the same session doesn't
    // re-prompt a user who already answered.
    LaunchedEffect(Unit) {
        if (settings.autoDiscovery && !locationGranted) {
            requestLocation.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val manualTarget = KnownStation(settings.stationHost, settings.stationPort, "", "")

    // Re-runs whenever the manual fallback OR auto-mode OR the permission
    // answer changes. Two-phase connect: the bootstrap/fallback station
    // connects first (discovery needs SOME station to route the RPC
    // through), then the real target list connects -- connectAll's own
    // diffing reuses the bootstrap session for free if it turns out to be
    // among the nearest 3, and closes it otherwise.
    LaunchedEffect(settings.autoDiscovery, settings.stationHost, settings.stationPort, locationGranted) {
        val bootstrap = MeshSessionPool.connectAll(listOf(manualTarget), identity).firstOrNull()?.session

        val targets = if (settings.autoDiscovery && locationGranted && bootstrap != null) {
            val fix = LocationFix.lastKnown(context)
            val discovered = fix?.let {
                StationDiscovery.nearestStations(bootstrap, identity, it.latitude, it.longitude, limit = 3)
            }
            discovered?.takeIf { it.isNotEmpty() } ?: listOf(manualTarget)
        } else {
            listOf(manualTarget)
        }

        heartbeats.forEach { it.stop() }
        val activeStations = MeshSessionPool.connectAll(targets, identity)
        val myStations = activeStations.map { DialTarget(it.target.host, it.target.port) }
        heartbeats = activeStations.map { active ->
            PresenceHeartbeat(
                scope = activity.lifecycleScope,
                session = active.session,
                identity = identity,
                myPhoneNumber = myPhoneNumber,
                myStations = myStations,
                contactDao = db.contactDao(),
                phonePresenceDao = db.phonePresenceDao(),
            ).also { it.start() }
        }
    }

    when (screen) {
        AppScreen.Settings -> {
            SettingsScreen(
                currentAutoDiscovery = settings.autoDiscovery,
                currentHost = settings.stationHost,
                currentPort = settings.stationPort,
                onBack = { screen = AppScreen.Contacts },
                onSaveAuto = {
                    activity.lifecycleScope.launch {
                        if (!locationGranted) requestLocation.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        SettingsStore.setAutoDiscovery(context)
                    }
                    screen = AppScreen.Contacts
                },
                onSaveManual = { host, port ->
                    activity.lifecycleScope.launch {
                        SettingsStore.setManualStation(context, host, port)
                    }
                    screen = AppScreen.Contacts
                },
            )
        }
        AppScreen.Contacts -> {
            ContactsScreen(activity, db, onOpenSettings = { screen = AppScreen.Settings })
        }
    }
}

@Composable
private fun ContactsScreen(activity: ComponentActivity, db: Cam2MeDatabase, onOpenSettings: () -> Unit) {
    val rows by remember {
        combine(db.contactDao().observeAll(), db.phonePresenceDao().observeForContacts()) { contacts, presenceList ->
            val presenceByNumber = presenceList.associateBy { it.phoneNumber }
            contacts.map { ContactRow(it, presenceByNumber[it.phoneNumber]) }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Ticks the online/offline indicator forward even when nothing new
    // arrives from Room -- otherwise a contact who stopped heartbeating
    // would stay "online" forever in a screen that never recomposes.
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(5_000)
        }
    }

    ContactListScreen(
        contacts = rows,
        nowMs = nowMs,
        onAddContact = { phoneNumber, displayName ->
            activity.lifecycleScope.launch {
                db.contactDao().upsert(
                    Contact(
                        phoneNumber = phoneNumber,
                        displayName = displayName,
                        addedAtMs = System.currentTimeMillis(),
                    )
                )
            }
        },
        onOpenSettings = onOpenSettings,
    )
}
