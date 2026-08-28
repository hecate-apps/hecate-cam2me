package social.hecate.cam2me.mesh

import io.macula.sdk.FfiKeyPair
import io.macula.sdk.FfiSession
import io.macula.sdk.FfiTrust

/**
 * The one mesh connection this process holds, reused by presence and (once
 * built) dialing. Connecting is a real QUIC handshake -- expensive enough
 * that a session is meant to live for as long as the chosen station
 * doesn't change, not reopened per call.
 */
object MeshSession {
    @Volatile
    private var session: FfiSession? = null
    private var connectedHost: String? = null
    private var connectedPort: Int? = null

    /**
     * Returns the existing session unchanged if already connected to this
     * exact host/port. A different host/port (the settings screen's
     * station picker) closes the old session first -- otherwise it would
     * leak, still connected to a station this device no longer wants.
     *
     * [FfiTrust.WebPki]: standard CA-bundle validation, matching what the
     * public macula-demo station fleet presents. A self-hosted station
     * outside that fleet would need [FfiTrust.Pinned] instead -- not
     * needed for this app's known-stations list.
     */
    suspend fun connect(host: String, port: Int, identity: FfiKeyPair): FfiSession {
        val existing = session
        if (existing != null && connectedHost == host && connectedPort == port) {
            return existing
        }
        existing?.close(identity)
        val opened = FfiSession.connect(host, port.toUShort(), FfiTrust.WebPki, identity)
        session = opened
        connectedHost = host
        connectedPort = port
        return opened
    }

    fun current(): FfiSession? = session
}
