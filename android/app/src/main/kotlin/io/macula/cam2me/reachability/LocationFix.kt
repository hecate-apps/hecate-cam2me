package io.macula.cam2me.reachability

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * A single best-effort location fix for nearest-station discovery.
 * Coarse precision only -- see AndroidManifest's own note on why FINE
 * isn't requested; plain [LocationManager] rather than Play Services'
 * FusedLocationProvider, since a one-off cached fix doesn't need it and
 * this project's stack otherwise stays clear of Big Tech SDKs in the
 * data path. Returns null whenever a fix genuinely isn't available
 * (permission not granted, no provider enabled, or nothing cached yet)
 * -- the caller's job is to fall back to the static station list, not to
 * surface this as an app error.
 */
object LocationFix {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** The most recent cached fix from any enabled provider. Deliberately
     * NOT requesting a fresh fix (`requestLocationUpdates`, which can
     * take real time to resolve) -- this runs once at launch, and a fix
     * that's a few minutes stale is still exactly precise enough to
     * decide "which country/city is nearest". */
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return manager.allProviders
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                }
            }
            .maxByOrNull { it.time }
    }
}
