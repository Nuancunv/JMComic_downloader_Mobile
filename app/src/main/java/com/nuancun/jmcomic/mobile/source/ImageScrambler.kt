package com.nuancun.jmcomic.mobile.source

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect

object ImageScrambler {
    fun segmentationCount(scrambleId: Int, photoId: String, fileName: String): Int {
        val id = photoId.toIntOrNull() ?: throw SourceException("章节 ID 无效")
        if (id < scrambleId) return 0
        if (id < 268850) return 10
        val modulus = if (id < 421926) 10 else 8
        val lastHex = JmCrypto.md5Hex("$id$fileName").last().code
        return (lastHex % modulus) * 2 + 2
    }

    fun decode(source: Bitmap, segments: Int): Bitmap {
        if (segments == 0) return source.copy(Bitmap.Config.RGB_565, false)
        require(segments > 0) { "segments must be positive" }
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(output)
        val remainder = source.height % segments
        val baseHeight = source.height / segments
        repeat(segments) { index ->
            var height = baseHeight
            val sourceTop = source.height - baseHeight * (index + 1) - remainder
            var targetTop = baseHeight * index
            if (index == 0) height += remainder else targetTop += remainder
            canvas.drawBitmap(
                source,
                Rect(0, sourceTop, source.width, sourceTop + height),
                Rect(0, targetTop, source.width, targetTop + height),
                null,
            )
        }
        return output
    }
}
