package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

data class LoadedStudyDocument(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val isPdf: Boolean,
    val bitmaps: List<Bitmap>
)

object DocumentMediaReader {

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        result = cursor.getString(idx)
                    }
                }
            }
        }
        if (result.isNullOrBlank()) {
            result = uri.lastPathSegment?.substringAfterLast('/') ?: "Uploaded_Document"
        }
        return result ?: "Study_Material"
    }

    suspend fun loadUriAsBitmaps(
        context: Context,
        uri: Uri,
        maxPagesForPdf: Int = 6
    ): Result<LoadedStudyDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val displayName = getFileName(context, uri)
            val mimeType = contentResolver.getType(uri)?.lowercase().orEmpty()
            val isPdf = mimeType.contains("pdf") || displayName.lowercase().endsWith(".pdf")

            if (isPdf) {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalArgumentException("Unable to open PDF file descriptor for $displayName")
                val bitmaps = mutableListOf<Bitmap>()
                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val pageCount = renderer.pageCount.coerceAtMost(maxPagesForPdf)
                        for (i in 0 until pageCount) {
                            renderer.openPage(i).use { page ->
                                // Render at ~1.6x scale for crisp OCR of printed/handwritten text while keeping memory low
                                val targetWidth = (page.width * 1.6f).roundToInt().coerceIn(800, 1600)
                                val scale = targetWidth.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                                val targetHeight = (page.height * scale).roundToInt().coerceIn(800, 2200)
                                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmaps.add(bitmap)
                            }
                        }
                    }
                }
                if (bitmaps.isEmpty()) {
                    throw IllegalStateException("PDF file '$displayName' contained no readable pages.")
                }
                LoadedStudyDocument(
                    uri = uri,
                    displayName = displayName,
                    mimeType = "application/pdf",
                    isPdf = true,
                    bitmaps = bitmaps
                )
            } else {
                val rawBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw IllegalArgumentException("Could not decode image '$displayName'. Ensure it is a valid JPG, PNG, or WEBP.")

                val scaledBitmap = scaleDownIfNeeded(rawBitmap, maxDimension = 1536)
                LoadedStudyDocument(
                    uri = uri,
                    displayName = displayName,
                    mimeType = mimeType.ifBlank { "image/jpeg" },
                    isPdf = false,
                    bitmaps = listOf(scaledBitmap)
                )
            }
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / largest.toFloat()
        val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
