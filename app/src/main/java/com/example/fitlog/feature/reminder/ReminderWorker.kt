package com.example.fitlog.feature.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.fitlog.MainActivity
import com.example.fitlog.R
import com.example.fitlog.data.repository.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 每日训练提醒的 Worker：发通知并自链下一天（见 [ReminderScheduler]）。
 *
 * 触发时先复查开关——用户可能在等待期内关掉提醒；通知权限未授予
 * （API 33+ 运行时拒绝/撤回）时跳过展示但仍保持自链，避免授权后提醒断档。
 *
 * 依赖获取用 [EntryPoint] 而非 @HiltWorker：本项目未引入 androidx.hilt-work，
 * Worker 只需要偏好仓库一个依赖，EntryPoint 足够且省一套注解处理器。
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ReminderWorkerEntryPoint::class.java,
        )
        val preferences = entryPoint.userPreferencesRepository()

        try {
            if (!preferences.reminderEnabled.first()) return Result.success()
            // 自链紧贴读取时间并先于发通知执行：缩小"读到旧值后覆盖用户
            // 恰好重排的新任务"的竞态窗口（提醒刚响、用户顺手改时间正是
            // Worker 运行期）。残余窗口为毫秒级，REPLACE 语义下无法根除，
            // 显式接受——见 ReminderScheduler KDoc
            val minutes = preferences.reminderMinutes.first()
            entryPoint.reminderScheduler().schedule(minutes)
            showNotification()
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // DataStore IO 等异常若不处理会被判 failure：不重试也不自链，
            // 每日提醒从此静默断档。退避重试可恢复链路，超过上限放弃
            Log.w(TAG, "训练提醒触发失败（第 ${runAttemptCount + 1} 次）", e)
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private fun showNotification() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "训练提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "每日到点的训练提醒通知" },
            )
        }

        // API 33+ 无权限时跳过展示（运行时权限由设置页申请）；低版本静态权限随安装生效
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("该训练了")
            .setContentText("今天的训练课等着你，动起来！")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "workout_reminder"
        const val NOTIFICATION_ID = 1001

        /** 重试上限：退避 3 次仍失败则放弃本轮，链路断档由用户下次开启/改时间重排恢复。 */
        const val MAX_RETRIES = 3
        private const val TAG = "ReminderWorker"
    }
}

/** Worker 的依赖入口（见类注释：未引入 hilt-work，走 EntryPoint 取依赖）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerEntryPoint {
    fun userPreferencesRepository(): UserPreferencesRepository

    fun reminderScheduler(): ReminderScheduler
}
