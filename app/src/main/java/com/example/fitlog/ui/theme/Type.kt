package com.example.fitlog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.fitlog.R

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

private val base = Typography()
val Typography = base.copy(
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
