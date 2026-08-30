package com.example.fitlog.util

import java.util.Locale

/**
 * 训练容量文案的统一格式化口径（Today 周进度与 Stats 系 builder 共用）。
 *
 * 此前 Today 与 Stats 各有一份实现且舍入方式漂移（四舍五入 vs 截断），
 * 同一容量在两个页面显示不一致（如 850.9kg 显示 "851 kg" 与 "850 kg"）。
 *
 * 口径：≥1000kg 显示吨（一位小数，四舍五入），否则整数千克（四舍五入）；
 * Locale.US 固定小数点，避免非点号 locale 把数字变形。
 */
object VolumeFormatter {

    /**
     * 容量文案（如 "851 kg"、"1.2 吨"）。
     *
     * @param volumeKg 容量（kg），Σ 正式组 weight×reps
     */
    fun formatVolume(volumeKg: Double): String =
        if (volumeKg >= 1000) {
            String.format(Locale.US, "%.1f 吨", volumeKg / 1000)
        } else {
            String.format(Locale.US, "%.0f kg", volumeKg)
        }
}
