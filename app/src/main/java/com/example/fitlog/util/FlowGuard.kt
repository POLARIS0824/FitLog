package com.example.fitlog.util

import com.example.fitlog.util.log.FitLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * 数据流降级守卫（guard 模式的全局共享实现）。
 *
 * 全项目约定：数据层 Flow 一旦在订阅链中抛出异常（Room 磁盘错误、
 * DataStore IO 故障等），`.stateIn` 的共享协程会被击穿——已发射错误态的
 * `catch` 终结整条流后，后续数据变化不再驱动 UI，未发射错误态则直接以
 * 未捕获异常崩溃。本扩展把"上游异常"降级为"记一条日志 + 写一次错误
 * 回调 + 发射 fallback"，保证 combine/stateIn 链路始终存活。
 *
 * 此前 TodayViewModel/StatsViewModel 各持有一份私有同款实现（仅错误
 * 通道不同），收口至此消除复制漂移；各 ViewModel 以 import alias 绑定
 * 自己的一次性错误通道，调用点语义不变。
 *
 * 注意：出错的那条源流此后停止更新（上游已因异常终止），其余源流与
 * UI 事件不受影响——这是 guard 模式的已知边界（见 TodayViewModel KDoc）。
 *
 * @param fallback 上游异常后发射的降级值（类型与流一致，调用方保证语义可降级）
 * @param context 降级来源描述，用于日志定位（如"训练历史"、"主题配置"）
 * @param onError 异常回调，用于写 ViewModel 的一次性错误通道（Snackbar/弹窗）；
 *   不写通道的纯静默降级传空即可——降级本身已由 guard 统一留痕
 */
fun <T> Flow<T>.guard(
    fallback: T,
    context: String = "共享数据流",
    onError: (Throwable) -> Unit = {},
): Flow<T> =
    catch { e ->
        // 流级降级统一留痕：此前完全静默的兜底路径（默认 onError 为空）从日志不可见
        FitLog.w(TAG, "$context 降级：${e.message}", e)
        onError(e)
        emit(fallback)
    }

private const val TAG = "FlowGuard"
