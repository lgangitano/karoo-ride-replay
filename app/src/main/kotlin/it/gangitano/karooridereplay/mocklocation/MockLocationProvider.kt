package it.gangitano.karooridereplay.mocklocation

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import it.gangitano.karooridereplay.replay.FitRecord
import it.gangitano.karooridereplay.replay.ReplayEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
 * Lifecycle: [arm] installs test providers and begins streaming from
 * [ReplayEngine.currentRecord]; [disarm] removes the test providers and
 * cancels the collection coroutine. Both idempotent.
 *
 * Providers are installed on [arm] (called when a replay actually starts),
 * NOT at extension-service startup. Registering at startup failed silently
 * whenever the app hadn't yet been picked as the device's mock-location app
 * — Android only permits `addTestProvider` once that designation is set —
 * which forced users to restart the app after configuring it (issue #1).
 * Arming at replay time also means the Karoo uses its real GPS whenever no
 * replay is running, so a subsequent real ride isn't hijacked by a stale
 * mock fix.
 */
class MockLocationProvider(
    context: Context,
    private val replayEngine: ReplayEngine
) {

    companion object {
        private const val TAG = "MockLocationProvider"
        /** How often the heartbeat re-publishes the held fix when the stream is quiet. */
        private const val HEARTBEAT_MS = 1_000L
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
    private var heartbeatJob: Job? = null
    private var previousRecord: FitRecord? = null
    private var running = false

    /** Last record we published a position from — the fix the heartbeat holds. */
    @Volatile private var lastPositioned: FitRecord? = null
    /** [SystemClock.elapsedRealtime] of the last publish, so the heartbeat only
     *  fires when the live record stream has gone quiet. */
    @Volatile private var lastPublishMs = 0L

    /**
     * Install test providers and begin streaming mock locations from the
     * replay engine. Called when a replay starts. Safe to call repeatedly;
     * no-op if already armed.
     */
    fun arm() {
        if (running) return
        running = true
        installProviders()
        previousRecord = null
        lastPositioned = null
        lastPublishMs = 0L
        collectJob = scope.launch {
            replayEngine.currentRecord.collect { record ->
                if (record != null && record.hasPosition) {
                    publishLocation(record)
                    previousRecord = record
                    lastPositioned = record
                }
            }
        }
        // Heartbeat: hold the GPS fix alive when the live record stream goes
        // quiet — the original ride is stationary / a positionless (GPS-off)
        // stretch, or playback is paused. Without a steady stream Android ages
        // out the test-provider location and consumers lose the fix. Re-publish
        // the last known position whenever nothing has gone out for HEARTBEAT_MS.
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                val idleMs = SystemClock.elapsedRealtime() - lastPublishMs
                if (idleMs >= HEARTBEAT_MS) lastPositioned?.let { publishLocation(it) }
            }
        }
    }

    /**
     * Remove test providers + stop streaming, so the Karoo falls back to its
     * real GPS. Called when the user exits the replay. Idempotent.
     */
    fun disarm() {
        if (!running) return
        running = false
        collectJob?.cancel()
        collectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        lastPositioned = null
        removeProviders()
        previousRecord = null
    }

    /** Tear down on extension destroy. */
    fun destroy() {
        disarm()
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
        lastPublishMs = SystemClock.elapsedRealtime()
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
