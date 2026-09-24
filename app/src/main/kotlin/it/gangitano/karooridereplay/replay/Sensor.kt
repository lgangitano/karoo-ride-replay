package it.gangitano.karooridereplay.replay

/**
 * A replayed sensor and how to read its value from a [FitRecord]. The karoo-ext
 * side (data type, field, device uid) lives in `vdevice/`; this stays pure.
 */
enum class Sensor(val valueIn: (FitRecord) -> Double?) {
    POWER({ it.power?.toDouble() }),
    HEART_RATE({ it.heartRate?.toDouble() }),
    CADENCE({ it.cadence?.toDouble() }),
    SPEED({ it.speed }),
}

/** Simulated connection state of one replayed sensor. */
enum class SensorState {
    STREAMING,
    SEARCHING,
    MISSING;

    /** Tap-cycle order: streaming → searching → missing → streaming. */
    fun next(): SensorState = entries[(ordinal + 1) % entries.size]
}
