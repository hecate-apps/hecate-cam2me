package io.macula.cam2me

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import io.macula.cam2me.identity.IdentityStore
import io.macula.cam2me.mesh.MeshSession
import io.macula.cam2me.mesh.PresenceHeartbeat
import io.macula.cam2me.settings.SettingsStore
import io.macula.cam2me.ui.ContactListScreen
import io.macula.cam2me.ui.ContactRow
import io.macula.cam2me.ui.OnboardingScreen
import io.macula.cam2me.ui.SettingsScreen

/**
 * Wires identity persistence, mesh connect, the presence heartbeat and the
 * contact list together. Camera capture, dial/pickup and a real call UI
 * are the next feature pass, not this one.
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

    ConnectedApp(activity, db, currentIdentity, currentSettings.stationHost, currentSettings.stationPort, currentSettings.myPhoneNumber)
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
    stationHost: String,
    stationPort: Int,
    myPhoneNumber: String,
) {
    val context = activity.applicationContext
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Contacts) }
    var currentHeartbeat by remember { mutableStateOf<PresenceHeartbeat?>(null) }

    // Re-runs whenever the station changes (the settings screen's picker),
    // not just once: MeshSession.connect closes the old connection and
    // opens a new one when host/port differ, and the previous heartbeat
    // has to stop -- it would otherwise keep running against a session
    // that's no longer connected to anything.
    LaunchedEffect(stationHost, stationPort) {
        currentHeartbeat?.stop()
        val session = MeshSession.connect(stationHost, stationPort, identity)
        val heartbeat = PresenceHeartbeat(
            scope = activity.lifecycleScope,
            session = session,
            identity = identity,
            myPhoneNumber = myPhoneNumber,
            stationHost = stationHost,
            stationPort = stationPort,
            contactDao = db.contactDao(),
            phonePresenceDao = db.phonePresenceDao(),
        )
        heartbeat.start()
        currentHeartbeat = heartbeat
    }

    when (screen) {
        AppScreen.Settings -> {
            SettingsScreen(
                currentHost = stationHost,
                currentPort = stationPort,
                onBack = { screen = AppScreen.Contacts },
                onSave = { host, port ->
                    activity.lifecycleScope.launch {
                        SettingsStore.setStation(context, host, port)
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
