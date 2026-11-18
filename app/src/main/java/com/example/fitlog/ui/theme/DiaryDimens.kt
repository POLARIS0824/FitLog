package com.example.fitlog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Diary detail, review editor and set picker. */
@Immutable
class DiaryDimens {
    /** Weight and reps value buttons in a set row; keeps a 48dp touch target. */
    val setValueMinHeight: Dp = 48.dp

    /** Text scale above which a set row stacks its label above the value buttons. */
    val setRowStackFontScale: Float = 1.3f

    /** Smallest row of the number snap picker, before the label line height is added. */
    val pickerItemMinHeight: Dp = 56.dp

    /** Smallest width of a horizontal weight picker item at 100% text. */
    val pickerItemMinWidth: Dp = 112.dp

    /** Estimated width of one picker label character, used to size horizontal items. */
    val pickerCharWidth: Dp = 14.dp

    /** Horizontal room around a picker label inside its pill. */
    val pickerLabelInset: Dp = 40.dp

    /** Scale of picker items that are not centered. */
    val pickerUnselectedScale: Float = 0.88f

    /** Rows visible in the vertical reps picker; the centered one is selected. */
    val pickerVisibleRows: Int = 5
}
