package com.example.fitlog.ui.theme

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * FitLog's surface and corner hierarchy, taken from the M3 component specs. Every container
 * in a screen is one of these levels; screens do not pick surface roles or corners ad hoc.
 *
 * | Level | Color | Shape |
 * |---|---|---|
 * | Page body | `surface` | — |
 * | Content card | Material `Card` defaults: filled = `surfaceContainerHighest`, elevated = `surfaceContainerLow` + Level1, outlined = `surface` + `outlineVariant` | `medium` 12dp (`CardDefaults.shape`) |
 * | Tile nested in a card | [nestedColor] | [nestedShape] (12dp − 16dp padding → smallest step) |
 * | List group (segmented list, settings group) | [pageListItemColors] / [sheetListItemColors] | `ListItemDefaults.segmentedShapes` (outer `large` 16dp, inner `extraSmall` 4dp) |
 * | Hero container (one per hero screen) | `primaryContainer` / `onPrimaryContainer` | [heroShape] 28dp |
 * | Bottom sheet, dialog | Material defaults (`surfaceContainerLow`, `surfaceContainerHigh`) | Material defaults (`extraLarge`) |
 *
 * Accents mark jobs: `primary` = main action and key data, `secondaryContainer` = selection,
 * `tertiary` / `tertiaryContainer` = status and achievements, `error` = errors only.
 * Text is `onSurface`, lower emphasis `onSurfaceVariant`, and `onX` on every `X` fill.
 */
object FitLogContainment {
    /** Fill of a tile inside a filled card (`surfaceContainerHighest`): two tonal steps down. */
    val nestedColor: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerLow

    /** Corner of a tile inside a 12dp card with 16dp padding (inner = outer − padding). */
    val nestedShape: Shape
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.extraSmall

    /** Corner of the screen's single hero container. */
    val heroShape: Shape
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.extraLarge

    /** Segmented list items on the page body: one step above `surface`. */
    @Composable
    fun pageListItemColors(): ListItemColors =
        ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

    /** Segmented list items inside a bottom sheet (`surfaceContainerLow`): one step above it. */
    @Composable
    fun sheetListItemColors(): ListItemColors =
        ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

    /** Fill of a list group drawn as one container (settings groups): same level as list items. */
    val listGroupColor: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer

    /** Corner of a list group drawn as one container: the M3 list container shape. */
    val listGroupShape: Shape
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.large
}
