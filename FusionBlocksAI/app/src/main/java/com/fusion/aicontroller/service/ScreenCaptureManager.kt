package com.fusion.aicontroller.service

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureManager(private val context: Context) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        // 8x8 Live Board Matrix: 0 = Empty cell, 1 = Occupied cell
        @Volatile
        var liveBoard: Array<IntArray> = Array(8) { IntArray(8) }

        // Detection of which of the 3 slots has an active piece
        @Volatile
        var activeSlots: BooleanArray = BooleanArray(3) { true }
    }

    fun startCapture(projection: MediaProjection) {
        stopCapture()
        this.mediaProjection = projection

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = 360
        val height = 640
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "FusionVisionStream",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    scanBoardAndSlots(image, width, height)
                } catch (_: Exception) {
                } finally {
                    image.close()
                }
            }
        }, handler)
    }

    /**
     * Scans the 8x8 grid cells:
     * Dark navy/blue background = Empty (0)
     * Bright colored block = Filled (1)
     */
    private fun scanBoardAndSlots(image: Image, width: Int, height: Int) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        // Grid boundaries (calibrated from screenshot)
        val gridLeft = (width * 0.106f).toInt()
        val gridRight = (width * 0.894f).toInt()
        val gridTop = (height * 0.246f).toInt()
        val gridBottom = (height * 0.596f).toInt()

        val cellW = (gridRight - gridLeft) / 8
        val cellH = (gridBottom - gridTop) / 8

        val newBoard = Array(8) { IntArray(8) }

        for (r in 0..7) {
            val centerY = gridTop + (r * cellH) + (cellH / 2)
            val rowOffset = centerY * rowStride

            for (c in 0..7) {
                val centerX = gridLeft + (c * cellW) + (cellW / 2)
                val offset = rowOffset + (centerX * pixelStride)

                if (offset + 3 < buffer.limit()) {
                    val red = buffer.get(offset).toInt() and 0xFF
                    val green = buffer.get(offset + 1).toInt() and 0xFF
                    val blue = buffer.get(offset + 2).toInt() and 0xFF

                    // Dark empty cell vs bright colored block
                    val isOccupied = (red > 90 || green > 90 || (blue > 120 && red > 50))
                    newBoard[r][c] = if (isOccupied) 1 else 0
                }
            }
        }
        liveBoard = newBoard

        // Check if the 3 bottom slots still have blocks
        val slotY = (height * 0.845f).toInt()
        val slotXs = listOf(
            (width * 0.165f).toInt(),
            (width * 0.345f).toInt(),
            (width * 0.520f).toInt()
        )

        val newActiveSlots = BooleanArray(3)
        for (i in 0..2) {
            val offset = (slotY * rowStride) + (slotXs[i] * pixelStride)
            if (offset + 3 < buffer.limit()) {
                val red = buffer.get(offset).toInt() and 0xFF
                val green = buffer.get(offset + 1).toInt() and 0xFF
                val blue = buffer.get(offset + 2).toInt() and 0xFF
                // If colored, a piece is present
                newActiveSlots[i] = (red > 60 || green > 60 || blue > 80)
            }
        }
        activeSlots = newActiveSlots
    }

    fun stopCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (_: Exception) {}
    }
}
