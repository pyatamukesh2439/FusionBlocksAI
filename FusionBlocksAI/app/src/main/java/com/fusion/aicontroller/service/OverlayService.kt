package com.fusion.aicontroller.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.fusion.aicontroller.MainActivity
import com.fusion.aicontroller.R
import com.fusion.aicontroller.model.ControllerStatus
import com.fusion.aicontroller.state.ControllerStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FusionAccessibilityService : AccessibilityService() {

    companion object {
        var instance: FusionAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Physics-Calibrated Drag & Drop:
     * Touches down, holds to pick up the block, drags smoothly, and releases into the grid.
     */
    fun dragAndDropWithHold(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 420L) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun tap(x: Float, y: Float, durationMs: Long = 50L) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }
}

class OverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var puzzleSolverJob: Job? = null

    private var btnAiPlay: Button? = null
    private var tvStatusIndicator: TextView? = null

    companion object {
        const val CHANNEL_ID = "fusion_overlay_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA_INTENT = "EXTRA_DATA_INTENT"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        screenCaptureManager = ScreenCaptureManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Fusion Blocks AI Grandmaster Ready"))

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA_INTENT)
                }

                if (resultCode != 0 && dataIntent != null) {
                    val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val projection: MediaProjection = mediaProjectionManager.getMediaProjection(resultCode, dataIntent)
                    screenCaptureManager?.startCapture(projection)
                }

                showFloatingOverlay()
            }
            ACTION_STOP -> {
                stopController()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingOverlay() {
        if (overlayView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#EE161616"))
            elevation = 16f
        }

        val dragHandle = TextView(this).apply {
            text = "✥ MOVE"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            gravity = Gravity.CENTER
        }

        val statusIndicator = TextView(this).apply {
            text = "● OFF"
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 12f
            setPadding(10, 0, 10, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        tvStatusIndicator = statusIndicator

        val playButton = Button(this).apply {
            text = "AI PLAY"
            setBackgroundColor(Color.parseColor("#00C853"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                startGrandmasterSolver()
            }
        }
        btnAiPlay = playButton

        val btnClose = Button(this).apply {
            text = "CLOSE ✕"
            setBackgroundColor(Color.parseColor("#D50000"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                stopController()
            }
        }

        val btnSettings = Button(this).apply {
            text = "SETTINGS"
            setBackgroundColor(Color.parseColor("#455A64"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }
        }

        container.addView(dragHandle)
        container.addView(statusIndicator)
        container.addView(playButton)
        container.addView(btnClose)
        container.addView(btnSettings)

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        val dragTouchListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = (initialX + (event.rawX - touchStartX)).toInt()
                    layoutParams.y = (initialY + (event.rawY - touchStartY)).toInt()
                    try {
                        windowManager.updateViewLayout(container, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        dragHandle.setOnTouchListener(dragTouchListener)
        statusIndicator.setOnTouchListener(dragTouchListener)
        container.setOnTouchListener(dragTouchListener)

        overlayView = container
        windowManager.addView(container, layoutParams)
    }

    /**
     * Grandmaster Puzzle AI:
     * Reads real-time live board from ScreenCaptureManager, compensates for the game's upward finger offset,
     * and evaluates multi-objective placements (Line Clears + Corner/Wall Packing + Hole Elimination).
     */
    private fun startGrandmasterSolver() {
        val tapService = FusionAccessibilityService.instance
        if (tapService == null) {
            Toast.makeText(this, "Enable Fusion Blocks in Accessibility Settings!", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        if (puzzleSolverJob?.isActive == true) {
            Toast.makeText(this, "AI is already playing! 🟢", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatusIndicator?.apply {
            text = "● ACTIVE"
            setTextColor(Color.parseColor("#00E676"))
        }

        btnAiPlay?.apply {
            text = "RUNNING 🟢"
            setBackgroundColor(Color.parseColor("#00B0FF"))
        }

        Toast.makeText(this, "🧠 Grandmaster Puzzle AI Active!", Toast.LENGTH_SHORT).show()
        ControllerStateRepository.setStatus(ControllerStatus.RUNNING)

        puzzleSolverJob = serviceScope.launch(Dispatchers.Main) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val screenW = metrics.widthPixels.toFloat()
            val screenH = metrics.heightPixels.toFloat()

            // Calibrated 8x8 Grid Boundaries from screenshot
            val gridLeft = screenW * 0.106f
            val gridRight = screenW * 0.894f
            val gridTop = screenH * 0.246f
            val gridBottom = screenH * 0.596f

            val cellW = (gridRight - gridLeft) / 8f
            val cellH = (gridBottom - gridTop) / 8f

            // UPWARD FINGER OFFSET COMPENSATION:
            // The game floats the block ~1.5 cells ABOVE your finger.
            // We pull the finger DOWN by 1.3 cells so the floating block lands dead center!
            val fingerOffsetY = cellH * 1.3f

            // Calibrated 3 Bottom Piece Slots
            val slotY = screenH * 0.845f
            val slot1X = screenW * 0.165f
            val slot2X = screenW * 0.345f
            val slot3X = screenW * 0.520f
            val slots = listOf(slot1X, slot2X, slot3X)

            // Corner & Wall packing priority
            val candidates = mutableListOf<Pair<Int, Int>>()
            candidates.addAll(listOf(Pair(7, 0), Pair(7, 7), Pair(0, 0), Pair(0, 7)))
            for (c in 1..6) { candidates.add(Pair(7, c)); candidates.add(Pair(0, c)) }
            for (r in 1..6) { candidates.add(Pair(r, 0)); candidates.add(Pair(r, 7)) }
            for (r in 6 downTo 1) {
                for (c in 1..6) { candidates.add(Pair(r, c)) }
            }

            while (isActive) {
                val currentBoard = ScreenCaptureManager.liveBoard
                val activeSlots = ScreenCaptureManager.activeSlots

                var placedAtLeastOne = false

                // Evaluate each available piece slot
                for (slotIndex in 0 until 3) {
                    if (!isActive) break
                    if (!activeSlots[slotIndex]) continue

                    val fromSlotX = slots[slotIndex]

                    // Find best candidate cell using Heuristic Evaluation
                    var bestScore = -99999
                    var bestTarget: Pair<Int, Int>? = null

                    for (pos in candidates) {
                        val r = pos.first
                        val c = pos.second

                        // Only consider empty cells
                        if (currentBoard[r][c] == 0) {
                            var score = 0

                            // 1. Line Clear Potential (+1000)
                            var rowCount = 1
                            for (cc in 0..7) if (cc != c && currentBoard[r][cc] == 1) rowCount++
                            if (rowCount >= 7) score += 1000

                            var colCount = 1
                            for (rr in 0..7) if (rr != r && currentBoard[rr][c] == 1) colCount++
                            if (colCount >= 7) score += 1000

                            // 2. Corner & Wall Clamping (+80)
                            if (r == 0 || r == 7 || c == 0 || c == 7) score += 80

                            // 3. Adjacency Bonus (+50)
                            if (r > 0 && currentBoard[r - 1][c] == 1) score += 50
                            if (r < 7 && currentBoard[r + 1][c] == 1) score += 50
                            if (c > 0 && currentBoard[r][c - 1] == 1) score += 50
                            if (c < 7 && currentBoard[r][c + 1] == 1) score += 50

                            // 4. Trap Hole Avoidance (-250)
                            var neighbors = 0
                            if (r > 0 && currentBoard[r - 1][c] == 1) neighbors++
                            if (r < 7 && currentBoard[r + 1][c] == 1) neighbors++
                            if (c > 0 && currentBoard[r][c - 1] == 1) neighbors++
                            if (c < 7 && currentBoard[r][c + 1] == 1) neighbors++
                            if (neighbors >= 3) score -= 250 // Avoid leaving 1-tile traps!

                            if (score > bestScore) {
                                bestScore = score
                                bestTarget = pos
                            }
                        }
                    }

                    if (bestTarget != null) {
                        val toGridX = gridLeft + (bestTarget.second + 0.5f) * cellW
                        val toGridY = gridTop + (bestTarget.first + 0.5f) * cellH + fingerOffsetY

                        // Execute accurate Hold-and-Drag
                        tapService.dragAndDropWithHold(fromSlotX, slotY, toGridX, toGridY, 440L)
                        ControllerStateRepository.incrementPlacements()
                        placedAtLeastOne = true
                        delay(550L) // Wait for board clear animation
                    }
                }

                // If all 3 were placed, wait for new set of 3 blocks to drop into tray
                if (!placedAtLeastOne) {
                    delay(800L)
                    // Tap center to dismiss any bonus/level popups
                    tapService.tap(screenW * 0.5f, screenH * 0.70f)
                    delay(400L)
                }
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun stopController() {
        puzzleSolverJob?.cancel()
        puzzleSolverJob = null
        screenCaptureManager?.stopCapture()

        tvStatusIndicator?.apply {
            text = "● OFF"
            setTextColor(Color.parseColor("#9E9E9E"))
        }
        btnAiPlay?.apply {
            text = "AI PLAY"
            setBackgroundColor(Color.parseColor("#00C853"))
        }

        removeOverlay()
        ControllerStateRepository.setStatus(ControllerStatus.STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fusion Blocks AI Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fusion Blocks AI")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopController()
        serviceScope.cancel()
    }
}
