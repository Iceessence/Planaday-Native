package com.planaday.nativeapp.core.math

import com.planaday.nativeapp.core.model.LabWetNirPair
import kotlin.math.abs
import kotlin.math.sqrt

data class RsdStats(
    val n: Int,
    val mean: Double,
    val sampleSd: Double?,
    val rsdPct: Double?,
)

data class WetNirPointAnalysis(
    val pair: LabWetNirPair,
    val wetMean: Double,
    val nirMean: Double,
    val signedDiff: Double, // NIR - Wet
    val absDiff: Double,
    val pctDiff: Double?, // ((NIR - Wet) / Wet) * 100 if Wet != 0
)

data class WetNirComparisonSummary(
    val nIncluded: Int,
    val nExcluded: Int,
    val units: List<String>,
    val mixedUnitsWarning: Boolean,
    val meanWet: Double?,
    val meanNir: Double?,
    val bias: Double?, // mean(NIR - Wet)
    val sdDiffSep: Double?, // Sample SD of differences (n-1 denominator)
    val rmse: Double?, // sqrt(mean((NIR - Wet)^2))
    val meanAbsDiff: Double?,
    val meanAbsPctDiff: Double?,
    val pearsonR: Double?,
    val rSquared: Double?,
    val slope: Double?, // OLS NIR = slope * Wet + intercept
    val intercept: Double?,
    val includedPoints: List<WetNirPointAnalysis>,
    val excludedPoints: List<WetNirPointAnalysis>,
    val methodologyDisclaimer: String,
)

/**
 * Analytical Chemistry & Laboratory Statistical Reference Engine (R-0157..R-0167, PF-24, PF-56).
 * Operational management aid ONLY — does NOT claim ISO/IEC 17025 or 21 CFR Part 11 regulatory validation.
 */
object LabAnalyticsEngine {
    const val NON_LIMS_DISCLAIMER: String =
        "Operational analytical comparison aid only — does NOT establish ISO/IEC 17025 or 21 CFR Part 11 regulatory validation."

    /** Solves C1 * V1 = C2 * V2 for V1 = (C2 * V2) / C1. */
    fun dilutionVolumeV1(c1: Double, c2: Double, v2: Double): Double {
        require(c1 > 0.0) { "Stock concentration C1 must be positive" }
        require(c2 >= 0.0 && v2 >= 0.0) { "Target concentration and volume must be non-negative" }
        return (c2 * v2) / c1
    }

    /** Computes spike percent recovery: ((spiked - unspiked) / theoreticalSpike) * 100. */
    fun percentRecovery(spikedMeasured: Double, unspikedMeasured: Double, spikeAdded: Double): Double {
        require(spikeAdded > 0.0) { "Spike added must be positive" }
        return ((spikedMeasured - unspikedMeasured) / spikeAdded) * 100.0
    }

    /** Sample mean, sample SD (n-1 denominator), and %RSD = (SD / |mean|) * 100. */
    fun computeRsd(values: List<Double>): RsdStats {
        val finite = values.filter { it.isFinite() }
        val n = finite.size
        require(n > 0) { "At least one finite measurement is required" }
        val mean = finite.sum() / n
        if (n < 2) return RsdStats(n = n, mean = mean, sampleSd = null, rsdPct = null)
        val variance = finite.sumOf { (it - mean) * (it - mean) } / (n - 1)
        val sd = sqrt(variance)
        val rsd = if (mean != 0.0) (sd / abs(mean)) * 100.0 else null
        return RsdStats(n = n, mean = mean, sampleSd = sd, rsdPct = rsd)
    }

    /**
     * Wet Chemistry vs. NIR paired statistical comparison (LA-001, LA-002):
     * handles replicate averaging, documented outlier exclusions, mixed-unit warnings,
     * missing/NaN filtering, zero wet baselines, Bias, SEP (n-1), RMSE, OLS regression, Pearson r & R².
     */
    fun compareWetVsNir(pairs: List<LabWetNirPair>): WetNirComparisonSummary {
        val evaluated = pairs.map { pair ->
            val wetMean = if (pair.wetReplicates.isNotEmpty()) {
                pair.wetReplicates.filter { it.isFinite() }.let { if (it.isEmpty()) Double.NaN else it.sum() / it.size }
            } else {
                pair.wetResult
            }
            val nirMean = if (pair.nirReplicates.isNotEmpty()) {
                pair.nirReplicates.filter { it.isFinite() }.let { if (it.isEmpty()) Double.NaN else it.sum() / it.size }
            } else {
                pair.nirResult
            }
            val diff = nirMean - wetMean
            val pct = if (wetMean.isFinite() && nirMean.isFinite() && wetMean != 0.0) (diff / wetMean) * 100.0 else null
            WetNirPointAnalysis(
                pair = pair,
                wetMean = wetMean,
                nirMean = nirMean,
                signedDiff = diff,
                absDiff = abs(diff),
                pctDiff = pct,
            )
        }

        val excluded = evaluated.filter { it.pair.excluded }
        val included = evaluated.filter { !it.pair.excluded && it.wetMean.isFinite() && it.nirMean.isFinite() }
        val units = included.map { it.pair.unit.trim().ifEmpty { "%" } }.distinct()
        val mixedUnits = units.size > 1
        val n = included.size

        if (n == 0) {
            return WetNirComparisonSummary(
                nIncluded = 0,
                nExcluded = excluded.size,
                units = units,
                mixedUnitsWarning = mixedUnits,
                meanWet = null,
                meanNir = null,
                bias = null,
                sdDiffSep = null,
                rmse = null,
                meanAbsDiff = null,
                meanAbsPctDiff = null,
                pearsonR = null,
                rSquared = null,
                slope = null,
                intercept = null,
                includedPoints = emptyList(),
                excludedPoints = excluded,
                methodologyDisclaimer = NON_LIMS_DISCLAIMER,
            )
        }

        val meanWet = included.sumOf { it.wetMean } / n
        val meanNir = included.sumOf { it.nirMean } / n
        val bias = included.sumOf { it.signedDiff } / n
        val meanAbsDiff = included.sumOf { it.absDiff } / n
        val validPcts = included.mapNotNull { it.pctDiff?.let(::abs) }
        val meanAbsPct = if (validPcts.isNotEmpty()) validPcts.sum() / validPcts.size else null
        val rmse = sqrt(included.sumOf { it.signedDiff * it.signedDiff } / n)
        val sep = if (n >= 2) {
            sqrt(included.sumOf { (it.signedDiff - bias) * (it.signedDiff - bias) } / (n - 1))
        } else null

        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (p in included) {
            val dx = p.wetMean - meanWet
            val dy = p.nirMean - meanNir
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }

        var slope: Double? = null
        var intercept: Double? = null
        var r: Double? = null
        var r2: Double? = null
        if (n >= 2 && sxx > 0.0) {
            slope = sxy / sxx
            intercept = meanNir - slope * meanWet
            if (syy > 0.0) {
                r = sxy / sqrt(sxx * syy)
                r2 = r * r
            }
        }

        return WetNirComparisonSummary(
            nIncluded = n,
            nExcluded = excluded.size,
            units = units,
            mixedUnitsWarning = mixedUnits,
            meanWet = meanWet,
            meanNir = meanNir,
            bias = bias,
            sdDiffSep = sep,
            rmse = rmse,
            meanAbsDiff = meanAbsDiff,
            meanAbsPctDiff = meanAbsPct,
            pearsonR = r,
            rSquared = r2,
            slope = slope,
            intercept = intercept,
            includedPoints = included,
            excludedPoints = excluded,
            methodologyDisclaimer = "Signed Diff = NIR - Wet; Bias = mean(Diff); SEP uses (n-1); RMSE = sqrt(mean(Diff^2)); OLS fits NIR = slope*Wet + intercept; R^2 = r^2. $NON_LIMS_DISCLAIMER",
        )
    }
}
