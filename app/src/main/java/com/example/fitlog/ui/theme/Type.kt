package com.example.fitlog.ui.theme

import androidx.annotation.FontRes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.fitlog.R

/**
 * The app's complete type system, built from three bundled variable faces:
 * - Big Shoulders Display: the brand voice for Display and Headline (tall, condensed, sporty).
 * - Google Sans Flex: Title, Body and Label at default axes for readability.
 * - Google Sans Code: numbers and data (weights, reps, dates, counts) with tabular figures.
 *
 * Sizes, line heights and tracking come from the Material scale. Screens read
 * `MaterialTheme.typography` for roles and the voices below for numbers; no screen sets
 * `fontSize`, `fontWeight` or `fontFamily` directly. CJK text uses Android's font fallback.
 */
object FitLogType {
    private val base = Typography()

    private const val BRAND_WEIGHT = 600
    private const val BRAND_EMPHASIZED_WEIGHT = 800

    val typography = base.copy(
        displayLarge = base.displayLarge.brand(BRAND_WEIGHT),
        displayMedium = base.displayMedium.brand(BRAND_WEIGHT),
        displaySmall = base.displaySmall.brand(BRAND_WEIGHT),
        headlineLarge = base.headlineLarge.brand(BRAND_WEIGHT),
        headlineMedium = base.headlineMedium.brand(BRAND_WEIGHT),
        headlineSmall = base.headlineSmall.brand(BRAND_WEIGHT),
        titleLarge = base.titleLarge.text(),
        titleMedium = base.titleMedium.text(),
        titleSmall = base.titleSmall.text(),
        bodyLarge = base.bodyLarge.text(),
        bodyMedium = base.bodyMedium.text(),
        bodySmall = base.bodySmall.text(),
        labelLarge = base.labelLarge.text(),
        labelMedium = base.labelMedium.text(),
        labelSmall = base.labelSmall.text(),
        displayLargeEmphasized = base.displayLargeEmphasized.brand(BRAND_EMPHASIZED_WEIGHT),
        displayMediumEmphasized = base.displayMediumEmphasized.brand(BRAND_EMPHASIZED_WEIGHT),
        displaySmallEmphasized = base.displaySmallEmphasized.brand(BRAND_EMPHASIZED_WEIGHT),
        headlineLargeEmphasized = base.headlineLargeEmphasized.brand(BRAND_EMPHASIZED_WEIGHT),
        headlineMediumEmphasized = base.headlineMediumEmphasized.brand(BRAND_EMPHASIZED_WEIGHT),
        headlineSmallEmphasized = base.headlineSmallEmphasized.brand(BRAND_EMPHASIZED_WEIGHT),
        // Emphasized text styles keep Material's heavier weights (Regular -> Medium, Medium -> Bold).
        titleLargeEmphasized = base.titleLargeEmphasized.text(),
        titleMediumEmphasized = base.titleMediumEmphasized.text(),
        titleSmallEmphasized = base.titleSmallEmphasized.text(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.text(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.text(),
        bodySmallEmphasized = base.bodySmallEmphasized.text(),
        labelLargeEmphasized = base.labelLargeEmphasized.text(),
        labelMediumEmphasized = base.labelMediumEmphasized.text(),
        labelSmallEmphasized = base.labelSmallEmphasized.text(),
    )

    /** Key metric figures (Insight quantities). Tabular figures keep changing values from shifting. */
    val metricNumber = typography.headlineMediumEmphasized.tabular()

    /** The weekly frequency figure; it drops a step when the user scales text up. */
    @Composable
    fun heroNumber(): TextStyle {
        val largeText = LocalDensity.current.fontScale >= 1.5f
        return typography.displayLargeEmphasized.copy(
            fontSize = if (largeText) 48.sp else 72.sp,
            lineHeight = if (largeText) 56.sp else 80.sp,
        ).brand(900).tabular()
    }

    /** Centered number used by the correction sheet's set-count field. */
    val centeredNumber = typography.headlineMediumEmphasized.copy(textAlign = TextAlign.Center).tabular()

    /** Numbers and data in running text: weights, reps, set counts, dates, durations. */
    val data = typography.bodyMedium.code(500)

    /** Emphasized data, such as the current set's weight × reps. */
    val dataEmphasized = typography.bodyLarge.code(700)

    /** Compact data inside chips, axis labels and metadata rows. */
    val dataLabel = typography.labelMedium.code(500)
}

private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private fun TextStyle.brand(weight: Int): TextStyle = voice(R.font.big_shoulders_display, weight)

private fun TextStyle.text(): TextStyle = voice(
    R.font.google_sans_flex,
    fontWeight?.weight ?: FontWeight.Normal.weight,
    FontVariation.width(100f),
    FontVariation.Setting("ROND", 0f),
)

private fun TextStyle.code(weight: Int): TextStyle = voice(R.font.google_sans_code, weight).tabular()

/**
 * One bundled variable face at fixed axes. A variable font needs its weight set per [Font],
 * otherwise every weight draws at 400. Faces without an `opsz` axis ignore optical sizing.
 */
private fun TextStyle.voice(@FontRes font: Int, weight: Int, vararg axes: FontVariation.Setting): TextStyle = copy(
    fontFamily = FontFamily(
        Font(
            resId = font,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.opticalSizing(fontSize),
                *axes,
            ),
        ),
    ),
    fontWeight = FontWeight(weight),
)
