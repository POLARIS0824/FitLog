package com.example.fitlog.util

/**
 * 动作展示名称转换工具。
 *
 * 将动作库的标准 kebab-case 标识（如 "barbell-full-squat"）或英文缓存名
 * 映射为易于阅读的中文名称（如 "杠铃深蹲"）；未匹配时优雅回退至动作已有名称或 key。
 */
object ExerciseDisplayName {

    private val CHINESE_NAMES: Map<String, String> = mapOf(
        // 下肢
        "barbell-full-squat" to "杠铃深蹲",
        "barbell-squat" to "杠铃深蹲",
        "barbell-romanian-deadlift" to "罗马尼亚硬拉",
        "smith-leg-press" to "腿举",
        "leg-press" to "腿举",
        "lever-lying-leg-curl" to "腿弯举",
        "lever-leg-extension" to "腿伸展",
        "lever-seated-leg-curl" to "坐姿腿弯举",
        "lever-standing-calf-raise" to "提踵",
        "lever-seated-calf-raise" to "坐姿提踵",
        "bulgarian-split-squat" to "保加利亚深蹲",
        "dumbbell-lunges" to "哑铃箭步蹲",
        "barbell-deadlift" to "杠铃硬拉",

        // 推类（胸/肩/三头）
        "barbell-bench-press" to "杠铃卧推",
        "dumbbell-incline-bench-press" to "上斜哑铃卧推",
        "incline-barbell-bench-press" to "上斜杠铃卧推",
        "dumbbell-bench-press" to "哑铃卧推",
        "dumbbell-seated-shoulder-press" to "哑铃坐姿推肩",
        "barbell-seated-overhead-press" to "杠铃坐姿推举",
        "barbell-standing-overhead-press" to "杠铃推举",
        "cable-lateral-raise" to "绳索侧平举",
        "dumbbell-lateral-raise" to "哑铃侧平举",
        "cable-pushdown" to "绳索下压",
        "barbell-lying-triceps-extension-skull-crusher" to "仰卧臂屈伸",
        "cable-crossover" to "绳索夹胸",
        "push-up" to "俯卧撑",
        "dips" to "双杠臂屈伸",

        // 拉类（背/二头）
        "pull-up" to "引体向上",
        "assisted-pull-up" to "辅助引体向上",
        "lat-pulldown" to "高位下拉",
        "barbell-bent-over-row" to "杠铃划船",
        "cable-low-seated-row" to "绳索坐姿划船",
        "dumbbell-row" to "哑铃划船",
        "barbell-curl" to "杠铃弯举",
        "ez-barbell-curl" to "EZ曲杆弯举",
        "dumbbell-curl" to "哑铃弯举",
        "hammer-curl" to "锤式弯举",

        // 核心/其他
        "weighted-front-plank" to "负重平板支撑",
        "plank" to "平板支撑",
        "hanging-leg-raise" to "悬垂举腿",
        "crunch" to "卷腹",
    )

    /**
     * 获取动作的中文友好展示名。
     *
     * @param exerciseKey 动作库标识（kebab-case）
     * @param fallbackName 默认或缓存名称
     */
    fun getDisplayName(exerciseKey: String?, fallbackName: String? = null): String {
        if (exerciseKey != null) {
            val key = exerciseKey.lowercase().trim()
            CHINESE_NAMES[key]?.let { return it }
            // 模糊前缀匹配（如 "barbell-full-squat-0001"）
            CHINESE_NAMES.entries.firstOrNull { key.startsWith(it.key) }?.let { return it.value }
        }
        return fallbackName?.takeIf { it.isNotBlank() } ?: exerciseKey ?: "未命名动作"
    }
}
