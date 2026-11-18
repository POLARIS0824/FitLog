package com.example.fitlog.ui.theme

/**
 * Material uses opacity only for scrims and disabled content. Lower text or icon emphasis by
 * stepping to a quieter color role (`onSurfaceVariant`), never with `copy(alpha = ...)`.
 */
object FitLogAlpha {
    /** Modal scrim over content (`colorScheme.scrim`). */
    const val SCRIM = 0.32f

    /** Disabled content, matching Material components. */
    const val DISABLED = 0.38f

    /**
     * Area fill under a chart line or inside a radar outline, applied to the same solid role as
     * the line (`primary`). The line itself stays fully opaque and carries the 3:1 contrast.
     */
    const val CHART_AREA = 0.24f
}
