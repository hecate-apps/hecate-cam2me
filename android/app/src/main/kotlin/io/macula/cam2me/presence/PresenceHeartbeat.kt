package io.macula.cam2me.presence

import io.macula.sdk.FfiEvent
import io.macula.sdk.FfiKeyPair
import io.macula.sdk.FfiSession
import io.macula.sdk.FfiValue
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import io.macula.cam2me.APP_REALM
import io.macula.cam2me.contacts.ContactDao

private const val HEARTBEAT_INTERVAL_MS = 30_000L

/** How long each recv_event poll blocks before this loop comes back
 * around to check whether a publish is due. Short enough that publish
 * stays close to on-schedule, long enough not to busy-loop. */
private const val POLL_TIMEOUT_MS = 5_000L

/** Type-level topic, no ids -- every device's own hashed phone number,
 * node id and stations live in the payload, never in the topic name. */
private const val PRESENCE_TOPIC = "cam2me.presence"

/**
 * Publishes this device's own (hashed) phone number + EVERY station it's
 * currently reachable through on [PRESENCE_TOPIC] every
 * [HEARTBEAT_INTERVAL_MS], and listens for the same from everyone else,
 * updating [PhonePresenceDao] wherever an incoming hash matches a known
 * contact's phone number. The sender's node_id comes from
 * [FfiEvent.publisher] itself -- no id ever appears in the topic name or
 * needs duplicating into the payload.
 *
 * One instance per active session (see [MeshSessionPool] -- up to 3, for
 * redundancy), each running its own independent loop against its own
 * [session]. [myStations] is the FULL set every instance advertises, not
 * just the one it's bound to: a contact who only overhears ONE of the
 * (up to) 3 heartbeats still learns every viable dial address, rather
 * than needing to receive from all three to build the complete picture.
 *
 * Runs as ONE sequential loop, never two concurrent coroutines touching
 * the same [FfiSession]. The control stream is one-frame-at-a-time per
 * [`Session::recv_event`]'s own doc: a caller expecting a pubsub delivery
 * has no reason to expect anything else to arrive first, which only holds
 * if nothing else (like a concurrent publish) is racing it for the next
 * frame off the wire. macula-rust-sdk's own live pubsub test follows the
 * same subscribe -> publish -> recv_event order, sequentially, for the
 * same reason. Multiple instances running concurrently against DIFFERENT
 * sessions is unaffected by that constraint -- it's per-session, not
 * per-process.
 */
class PresenceHeartbeat(
    private val scope: CoroutineScope,
    private val session: FfiSession,
    private val identity: FfiKeyPair,
    private val myPhoneNumber: String,
    private val myStations: List<DialTarget>,
    private val contactDao: ContactDao,
    private val phonePresenceDao: PhonePresenceDao,
) {
    private val seq = AtomicLong(0)
    private var job: Job? = null

    fun start() {
        stop()
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun loop() {
        session.subscribe(PRESENCE_TOPIC, APP_REALM, identity)
        val myHash = sha256Hex(myPhoneNumber)
        var lastPublishAtMs = 0L

        while (scope.isActive) {
            val now = System.currentTimeMillis()
            if (now - lastPublishAtMs >= HEARTBEAT_INTERVAL_MS) {
                publish(myHash)
                lastPublishAtMs = now
            }

            val event = try {
                session.recvEvent(POLL_TIMEOUT_MS.toULong())
            } catch (e: Exception) {
                // The overwhelmingly common case: nothing arrived within
                // this poll window, which is not an error worth logging
                // every 5 seconds -- only genuinely unexpected shapes are.
                null
            }
            if (event != null && event.topic == PRESENCE_TOPIC) {
                handleEvent(event)
            }
        }
    }

    private suspend fun publish(myHash: String) {
        val stations = JSONArray()
        myStations.forEach { stations.put(JSONObject().put("host", it.host).put("port", it.port)) }
        val payload = JSONObject()
            .put("phone_hash", myHash)
            .put("stations", stations)
            .toString()
        session.publish(
            PRESENCE_TOPIC,
            APP_REALM,
            seq.incrementAndGet().toULong(),
            FfiValue.Text(payload),
            System.currentTimeMillis().toULong(),
            identity,
        )
    }

    private suspend fun handleEvent(event: FfiEvent) {
        val text = (event.payload as? FfiValue.Text)?.v1 ?: return
        val json = JSONObject(text)
        val phoneHash = json.optString("phone_hash").ifEmpty { return }
        val stationsArray = json.optJSONArray("stations") ?: return
        val stations = (0 until stationsArray.length()).mapNotNull { i ->
            val o = stationsArray.optJSONObject(i) ?: return@mapNotNull null
            val host = o.optString("host").ifEmpty { return@mapNotNull null }
            val port = o.optInt("port", -1).takeIf { it > 0 } ?: return@mapNotNull null
            DialTarget(host, port)
        }
        if (stations.isEmpty()) return

        // Self-delivery is harmless and needs no special-casing: this
        // device's own phone number is never one of its own contacts, so
        // its own heartbeat simply never matches anything below.
        val matched = contactDao.listAll().firstOrNull { sha256Hex(it.phoneNumber) == phoneHash }
            ?: return

        phonePresenceDao.upsert(
            PhonePresence(
                phoneNumber = matched.phoneNumber,
                nodeIdHex = event.publisher.joinToString("") { "%02x".format(it) },
                stationsJson = PhonePresence.encodeStations(stations),
                lastSeenOnlineAtMs = System.currentTimeMillis(),
            )
        )
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
