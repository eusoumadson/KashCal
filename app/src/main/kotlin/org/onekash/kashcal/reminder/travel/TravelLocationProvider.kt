package org.onekash.kashcal.reminder.travel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.onekash.kashcal.di.IoDispatcher
import org.onekash.kashcal.util.location.LocationSuggestionService
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Provides device location and address geocoding for travel time calculations.
 *
 * Uses native Android [LocationManager] with pure AOSP APIs (no proprietary GMS dependencies),
 * ensuring full compatibility with F-Droid and IzzyOnDroid.
 */
@Singleton
class TravelLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationSuggestionService: LocationSuggestionService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        const val LOCATION_TIMEOUT_MS = 8_000L
    }

    private val locationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return hasLocationPermission()
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Resolves current device coordinates.
     *
     * Tries [LocationManager.getCurrentLocation] with an 8-second timeout,
     * falling back to [LocationManager.getLastKnownLocation] if unavailable.
     */
    suspend fun getCurrentLocation(): Pair<Double, Double>? = withContext(ioDispatcher) {
        if (!hasLocationPermission()) return@withContext null
        val lm = locationManager ?: return@withContext null

        // Try getting fresh location
        val freshLocation = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            requestFreshLocation(lm)
        }
        if (freshLocation != null) {
            return@withContext Pair(freshLocation.latitude, freshLocation.longitude)
        }

        // Fall back to best last known location
        val lastLocation = getLastKnownLocation(lm)
        if (lastLocation != null) {
            return@withContext Pair(lastLocation.latitude, lastLocation.longitude)
        }

        null
    }

    private suspend fun requestFreshLocation(lm: LocationManager): Location? {
        val providers = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { provider ->
            try {
                lm.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }

        for (provider in providers) {
            val location = suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    lm.getCurrentLocation(
                        provider,
                        signal,
                        context.mainExecutor,
                        Consumer { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    )
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            if (location != null) return location
        }
        return null
    }

    private fun getLastKnownLocation(lm: LocationManager): Location? {
        val providers = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.time > bestLocation.time) {
                    bestLocation = loc
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        return bestLocation
    }

    /**
     * Resolves text address to (lat, lon) coordinates via Geocoder.
     */
    suspend fun resolveCoordinates(locationOrAddress: String): Pair<Double, Double>? {
        if (locationOrAddress.isBlank()) return null
        return try {
            val suggestions = locationSuggestionService.getSuggestions(locationOrAddress, maxResults = 1)
            val first = suggestions.firstOrNull()
            if (first?.latitude != null && first.longitude != null) {
                Pair(first.latitude, first.longitude)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
