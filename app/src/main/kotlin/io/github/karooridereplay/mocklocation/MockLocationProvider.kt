package io.github.karooridereplay.mocklocation

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.karooridereplay.replay.FitRecord
import io.github.karooridereplay.replay.ReplayEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Publishes the [ReplayEngine]'s current FIT record as a mock GPS location
 * via Android's [LocationManager] test-provider API.
 *
 * The app must be designated as the device's mock-location app
 * (Developer options → "Select mock location app") AND hold
 * `ACCESS_MOCK_LOCATION`. The manifest already declares the permission;
 * the designation is a user action.
 *
 * Why we re-implement this instead of using FakeTraveler:
 *
 *   - FakeTraveler hardcodes `Location.speed = 0.01F` and `altitude = 3F`,
 *     which makes Karoo's ride engine auto-pause and leaves the altimeter
 *     flat. Reverse-engineered + documented during the testing arc on
 *     2026-05-25.
 *   - We have the FIT records right here. Set speed and altitude from the
 *     records themselves, fall back to delta-derived values when the FIT
 *     omits them (some old files don't include `speed`).
 *
 * Lifecycle: [start] installs test providers and begins streaming from
 * [ReplayEngine.currentRecord]. [stop] removes the test providers and
 * cancels the collection coroutine. Idempotent.
 */
class MockLocationProvider(
    context: Context,
    private val replayEngine: ReplayEngine
) {

    companion object {
        private const val TAG = "MockLocationProvider"
        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val DEFAULT_ACCURACY_M = 3.0f
        private const val DEFAULT_SPEED_ACCURACY = 0.5f
        private const val DEFAULT_BEARING_ACCURACY = 1.0f
        private const val DEFAULT_VERTICAL_ACCURACY = 1.0f
    }

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Providers we register against. GPS is the canonical one; NETWORK and
     * FUSED are added so apps that subscribe to those (rather than GPS
     * directly) still see our mock data. FUSED only exists from Android S+.
     */
    private val providers: List<String> = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(LocationManager.FUSED_PROVIDER)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null
    private var previousRecord: FitRecord? = null
    private var running = false

    /**
     * Install test providers and begin streaming mock locations from the
     * replay engine. Safe to call repeatedly; no-op if already running.
     */
    fun start() {
        if (running) return
        running = true
        installProviders()
        previousRecord = null
        collectJob = scope.launch {
            replayEngine.currentRecord.collect { record ->
                if (record != null && record.hasPosition) {
                    publishLocation(record)
                    previousRecord = record
                }
            }
        }
    }

    /** Cancel coroutine + remove test providers. Idempotent. */
    fun stop() {
        if (!running) return
        running = false
        collectJob?.cancel()
        collectJob = null
        removeProviders()
        previousRecord = null
    }

    /** Tear down on extension destroy. */
    fun destroy() {
        stop()
        scope.cancel()
    }

    // ─── provider lifecycle ────────────────────────────────────────────────

    private fun installProviders() {
        providers.forEach { provider ->
            try {
                @Suppress("DEPRECATION") // Criteria args needed for older API levels
                locationManager.addTestProvider(
                    provider,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(provider, true)
                Log.d(TAG, "installed test provider: $provider")
            } catch (e: SecurityException) {
                Log.w(TAG, "addTestProvider($provider) denied — app not designated as mock location app: ${e.message}")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "addTestProvider($provider) failed: ${e.message}")
            }
        }
    }

    private fun removeProviders() {
        providers.forEach { provider ->
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                // Ignore — provider may not be installed
            }
        }
    }

    // ─── publishing ────────────────────────────────────────────────────────

    private fun publishLocation(record: FitRecord) {
        val lat = record.lat ?: return
        val lng = record.lng ?: return

        // Speed: prefer FIT's recorded value; fall back to delta-derived
        val speedMps = record.speed ?: computeSpeedFromDelta(record)
        val bearingDeg = computeBearingFromDelta(record)
        val altitudeM = record.altitude ?: 0.0

        providers.forEach { provider ->
            try {
                val location = Location(provider).apply {
                    latitude = lat
                    longitude = lng
                    this.altitude = altitudeM
                    accuracy = DEFAULT_ACCURACY_M
                    speed = speedMps.toFloat().coerceAtLeast(0f)
                    bearing = bearingDeg.toFloat()
                    time = record.timestampMs
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        speedAccuracyMetersPerSecond = DEFAULT_SPEED_ACCURACY
                        bearingAccuracyDegrees = DEFAULT_BEARING_ACCURACY
                        verticalAccuracyMeters = DEFAULT_VERTICAL_ACCURACY
                    }
                }
                locationManager.setTestProviderLocation(provider, location)
            } catch (e: SecurityException) {
                Log.w(TAG, "setTestProviderLocation($provider) denied: ${e.message}")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "setTestProviderLocation($provider) failed: ${e.message}")
            }
        }
    }

    // ─── geo math ──────────────────────────────────────────────────────────

    private fun computeSpeedFromDelta(current: FitRecord): Double {
        val prev = previousRecord ?: return 0.0
        if (!prev.hasPosition || !current.hasPosition) return 0.0
        val distanceM = haversineMeters(prev.lat!!, prev.lng!!, current.lat!!, current.lng!!)
        val dtSec = (current.timestampMs - prev.timestampMs) / 1000.0
        return if (dtSec > 0) distanceM / dtSec else 0.0
    }

    private fun computeBearingFromDelta(current: FitRecord): Double {
        val prev = previousRecord ?: return 0.0
        if (!prev.hasPosition || !current.hasPosition) return 0.0
        return bearingDegrees(prev.lat!!, prev.lng!!, current.lat!!, current.lng!!)
    }

    /**
     * Haversine great-circle distance between two lat/lon pairs.
     * Within ~0.5% of true distance for short hops; plenty accurate at the
     * 1Hz sampling rate of typical FIT files.
     */
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2.0).pow(2.0) + cos(phi1) * cos(phi2) * sin(dLambda / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_M * c
    }

    /** Forward azimuth between two points, in degrees clockwise from north. */
    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val theta = atan2(y, x)
        return (Math.toDegrees(theta) + 360.0) % 360.0
    }
}
