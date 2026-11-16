package com.example.fitlog.ui.theme

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
 * The app's complete type system. The Material scale, the Google Sans Flex voices and the
 * numeric styles used by feature screens all live here, so a type change has one home.
 */
object FitLogType {
    private val base = Typography()

    val typography = base.copy(
        displayLarge = base.displayLarge.flex(heading = true),
        displayMedium = base.displayMedium.flex(heading = true),
        displaySmall = base.displaySmall.flex(heading = true),
        headlineLarge = base.headlineLarge.flex(heading = true),
        headlineMedium = base.headlineMedium.flex(heading = true),
        headlineSmall = base.headlineSmall.flex(heading = true),
        titleLarge = base.titleLarge.flex(),
        titleMedium = base.titleMedium.flex(),
        titleSmall = base.titleSmall.flex(),
        bodyLarge = base.bodyLarge.flex(),
        bodyMedium = base.bodyMedium.flex(),
        bodySmall = base.bodySmall.flex(),
        labelLarge = base.labelLarge.flex(),
        labelMedium = base.labelMedium.flex(),
        labelSmall = base.labelSmall.flex(),
        displayLargeEmphasized = base.displayLargeEmphasized.flex(true, true),
        displayMediumEmphasized = base.displayMediumEmphasized.flex(true, true),
        displaySmallEmphasized = base.displaySmallEmphasized.flex(true, true),
        headlineLargeEmphasized = base.headlineLargeEmphasized.flex(true, true),
        headlineMediumEmphasized = base.headlineMediumEmphasized.flex(true, true),
        headlineSmallEmphasized = base.headlineSmallEmphasized.flex(true, true),
        titleLargeEmphasized = base.titleLargeEmphasized.flex(true),
        titleMediumEmphasized = base.titleMediumEmphasized.flex(true),
        titleSmallEmphasized = base.titleSmallEmphasized.flex(true),
        bodyLargeEmphasized = base.bodyLargeEmphasized.flex(true),
        bodyMediumEmphasized = base.bodyMediumEmphasized.flex(true),
        bodySmallEmphasized = base.bodySmallEmphasized.flex(true),
        labelLargeEmphasized = base.labelLargeEmphasized.flex(true),
        labelMediumEmphasized = base.labelMediumEmphasized.flex(true),
        labelSmallEmphasized = base.labelSmallEmphasized.flex(true),
    )

    /** Tabular figures keep changing metrics from shifting width. */
    val metricNumber = typography.headlineMediumEmphasized.copy(fontFeatureSettings = "tnum")

    /** The weekly frequency figure; it drops a step when the user scales text up. */
    @Composable
    fun heroNumber(): TextStyle {
        val largeText = LocalDensity.current.fontScale >= 1.5f
        return typography.displayLargeEmphasized.copy(
            fontSize = if (largeText) 48.sp else 72.sp,
            lineHeight = if (largeText) 56.sp else 80.sp,
            fontFeatureSettings = "tnum",
        )
    }

    /** Centered number used by the correction sheet's set-count field. */
    val centeredNumber = typography.headlineMediumEmphasized.copy(textAlign = TextAlign.Center)
}

// Rounded headings and neutral reading text. CJK uses Android's font fallback.
private fun TextStyle.flex(emphasized: Boolean = false, heading: Boolean = false): TextStyle {
    val weight = if (emphasized) { if (heading) 750 else 700 } else { fontWeight?.weight ?: 400 }
    return copy(
        fontFamily = FontFamily(Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(if (heading) 90f else 100f),
                FontVariation.opticalSizing(fontSize),
                FontVariation.Setting("ROND", if (heading) 80f else 0f),
            ),
        )),
        fontWeight = FontWeight(weight),
    )
}
