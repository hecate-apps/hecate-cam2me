package social.hecate.cam2me.data

/** One of the macula-demo fleet's public station doors -- see the
 * per-box station configs under macula-demo/infrastructure/ (each a
 * "stations-<box>/config/<name>.json") for the authoritative source
 * this list is drawn from. All on the standard 4433 QUIC port. */
data class KnownStation(val host: String, val port: Int, val city: String, val country: String)

val KNOWN_STATIONS = listOf(
    KnownStation("station-de-frankfurt.macula.io", 4433, "Frankfurt", "DE"),
    KnownStation("station-de-falkenstein.macula.io", 4433, "Falkenstein", "DE"),
    KnownStation("station-de-nuremberg.macula.io", 4433, "Nuremberg", "DE"),
    KnownStation("station-fr-paris.macula.io", 4433, "Paris", "FR"),
    KnownStation("station-it-milan.macula.io", 4433, "Milan", "IT"),
    KnownStation("station-se-stockholm.macula.io", 4433, "Stockholm", "SE"),
    KnownStation("station-fi-helsinki.macula.io", 4433, "Helsinki", "FI"),
)
