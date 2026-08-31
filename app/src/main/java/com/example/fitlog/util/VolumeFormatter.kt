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

    /**
     * 重量数值文案：整数值去掉小数尾巴（60.0 → "60"，62.5 → "62.5"）。
     *
     * 组录入行/训练明细/PR 摘要的统一重量格式化口径（此前三处各有一份
     * 私有实现，收口至此消除漂移面）。
     *
     * @param weightKg 重量（kg）
     */
    fun formatWeightKg(weightKg: Float): String =
        if (weightKg == weightKg.toInt().toFloat()) {
            weightKg.toInt().toString()
        } else {
            weightKg.toString()
        }
}
