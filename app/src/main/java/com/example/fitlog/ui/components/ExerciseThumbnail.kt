package com.example.fitlog.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 动作示范图缩略图（assets/exercises/ 本地资源）。
 *
 * 动作库自带 1322 张示范图（此前全库无消费者），训练执行时展示可消除
 * "不认识动作无从下手"的断点；图片解码在 IO 线程按目标尺寸两次采样
 * （避免整图解码进内存），资源缺失/损坏时优雅降级为通用健身图标——
 * 数据集曾出现个别 json 引用与 assets 实际文件不一致的情况，不能假设
 * 每张图都存在。
 *
 * @param imageFileName 动作图片文件名（Exercise.imageUrl，如 "0001-2gPfomN.jpg"）
 * @param contentDescription 无障碍描述
 * @param fallbackIcon 图片缺失/加载失败时的占位图标
 */
@Composable
fun ExerciseThumbnail(
    imageFileName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Filled.FitnessCenter,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageFileName) {
        if (imageFileName.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            decodeAssetThumbnail(context, imageFileName)?.asImageBitmap()
        }
    }

    val shape = RoundedCornerShape(12.dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(56.dp)
                .clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(56.dp)
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 解码 assets 缩略图：两次采样（先读边界算 inSampleSize，再真正解码），
 * 目标约 112px（2x 密度下的 56dp），单图内存占用 ~50KB。
 * 任何异常（缺文件/损坏/流中断）返回 null，由调用方降级。
 */
private fun decodeAssetThumbnail(context: Context, fileName: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open("exercises/$fileName").use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val targetSize = 112
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= targetSize &&
        bounds.outHeight / (sampleSize * 2) >= targetSize
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    context.assets.open("exercises/$fileName").use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}.getOrNull()
