package com.talkswithtanha.twt.core.model

import com.talkswithtanha.twt.core.data.LiveApi
import kotlin.math.abs
import kotlin.math.pow

/**
 * Where a live trade stands against the price right now: how far it has come
 * from its entry (or the last target it hit) towards the next target, and
 * how far that is in pips. The signal detail's chart shows it under the
 * candles, and the followed-trade notification fills its bar with it, so the
 * bar moves with the market rather than only when a target is hit.
 */
data class LiveProgress(
    /** 0..1 of the way from the last mark to the next target. */
    val fraction: Double,
    val nextLabel: String,
    /** Pips (or points) still to go to [nextLabel]; negative is past it. */
    val toGo: Double,
    /** Pips between price and the stop; how much room is left. */
    val toStop: Double?,
    val unit: String
)

private fun price(v: String): Double? = v.replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.takeIf { it > 0 }

/** One pip (or point) for the market, as traders count it — the same rule as the panel. */
fun LiveApi.Instrument.pip(): Pair<Double, String> = when (kind) {
    LiveApi.Kind.FX -> 10.0.pow(-(decimals - 1)) to "pips"
    LiveApi.Kind.METAL -> (if (key == "XAGUSD") 0.01 else 0.1) to "pips"
    LiveApi.Kind.ENERGY -> 0.01 to "pips"
    else -> 1.0 to "points"
}

fun Signal.liveProgress(now: Double): LiveProgress? {
    if (!status.isOngoing || isAnalysis) return null
    val entry = price(entryPrice) ?: return null
    val next = takeProfits.firstOrNull { !it.isHit } ?: return null
    val target = price(next.price) ?: return null
    val start = takeProfits.lastOrNull { it.isHit }?.let { price(it.price) } ?: entry
    if (target == start) return null
    val (size, unit) = LiveApi.find(pair)?.pip() ?: (0.0001 to "pips")
    val dir = if (type == SignalType.BUY) 1 else -1
    val stop = price(stopLoss)
    return LiveProgress(
        fraction = ((now - start) / (target - start)).coerceIn(0.0, 1.0),
        nextLabel = next.label,
        toGo = (target - now) / size * dir,
        toStop = stop?.let { abs(now - it) / size },
        unit = unit
    )
}
