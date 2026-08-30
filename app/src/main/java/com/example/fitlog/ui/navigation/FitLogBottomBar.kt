package com.example.fitlog.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.example.fitlog.ui.theme.fitLogColors

/**
 * 单个底部 tab 的静态配置。
 *
 * @param key 对应的 Navigation3 路由 Key
 * @param label tab 文字标签
 * @param icon tab 图标
 */
private data class FitLogTab(
    val key: NavKey,
    val label: String,
    val icon: ImageVector,
)

/**
 * 底部导航栏的 4 个顶级 tab，顺序即展示顺序：
 * Today（主页）→ Chat（AI 教练）→ Stats（统计）→ Settings（设置）。
 */
private val FitLogTabs = listOf(
    FitLogTab(TodayKey, "Today", Icons.Filled.Today),
    FitLogTab(ChatKey, "Chat", Icons.AutoMirrored.Filled.Chat),
    FitLogTab(StatsKey, "Stats", Icons.Filled.BarChart),
    FitLogTab(SettingsKey, "Settings", Icons.Filled.Settings),
)

/**
 * 判断 [this] 是否为底部导航栏的顶级目的地（即 4 个 tab Key 之一），
 * 用于控制底栏显隐：仅栈顶为顶级目的地时显示。
 */
fun NavKey?.isTabDestination(): Boolean = FitLogTabs.any { it.key == this }

/**
 * 应用底部导航栏（Material 3 [NavigationBar]，4 tab：Today / Chat / Stats / Settings）。
 *
 * 选中态由调用方从 Navigation3 回退栈顶推导（底栏状态与导航状态同源），
 * 点击回调也交由调用方执行栈切换，本组件不持有导航状态。
 *
 * @param selectedTab 当前选中的 tab Key（回退栈顶）；为 null 或二级页 Key 时无高亮项
 * @param onTabSelected tab 点击回调，参数为目标 tab 的 Key
 * @param modifier 修饰符
 */
@Composable
fun FitLogBottomBar(
    selectedTab: NavKey?,
    onTabSelected: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.fitLogColors.navigationBar,
    ) {
        FitLogTabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.key == selectedTab,
                onClick = { onTabSelected(tab.key) },
                icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                label = { Text(text = tab.label) },
            )
        }
    }
}
