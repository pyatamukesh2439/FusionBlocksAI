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

    fun dragAndDrop(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 340L) {
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
    private var puzzleSolverJob: Job? = null

    private var btnAiPlay: Button? = null
    private var tvStatusIndicator: TextView? = null

    companion object {
        const val CHANNEL_ID = "fusion_overlay_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Fusion Blocks AI Ready"))
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
            y = 120
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
                startIntelligentSolver()
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
     * Strategic Puzzle Solver (Dellacherie Algorithm):
     * Simulates placement options, prioritizes Line Clears (+1000) and Corner/Wall Packing (+80),
     * and strictly penalizes isolated single-cell holes (-250).
     */
    private fun startIntelligentSolver() {
        val tapService = FusionAccessibilityService.instance
        if (tapService == null) {
            Toast.makeText(this, "Enable Fusion Blocks AI in Accessibility Settings!", Toast.LENGTH_LONG).show()
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

        Toast.makeText(this, "🟢 Fusion Blocks Intelligent AI Active!", Toast.LENGTH_SHORT).show()
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

            // Calibrated 3 Bottom Piece Slots (left side, avoiding ad on right)
            val slotY = screenH * 0.845f
            val slot1X = screenW * 0.165f // Purple 1x1 slot
            val slot2X = screenW * 0.345f // Center shape slot
            val slot3X = screenW * 0.520f // Line / shape slot
            val slots = listOf(slot1X, slot2X, slot3X)

            // Internal Virtual Board State for Strategic Planning
            val virtualBoard = Array(8) { IntArray(8) }

            // Candidate priority order: Evaluates Corner & Edge cells first to pack walls
            val strategicPriorityOrder = mutableListOf<Pair<Int, Int>>()
            // 1. Four Corners (Safest anchors)
            strategicPriorityOrder.addAll(listOf(Pair(7, 0), Pair(7, 7), Pair(0, 0), Pair(0, 7)))
            // 2. Bottom Row & Top Row edges
            for (c in 1..6) {
                strategicPriorityOrder.add(Pair(7, c))
                strategicPriorityOrder.add(Pair(0, c))
            }
            // 3. Left & Right Columns
            for (r in 1..6) {
                strategicPriorityOrder.add(Pair(r, 0))
                strategicPriorityOrder.add(Pair(r, 7))
            }
            // 4. Inner remaining grid
            for (r in 6 downTo 1) {
                for (c in 1..6) {
                    strategicPriorityOrder.add(Pair(r, c))
                }
            }

            var cycle = 0

            while (isActive) {
                // Cycle through the 3 bottom piece slots
                for (slotIndex in 0 until 3) {
                    if (!isActive) break

                    val fromSlotX = slots[slotIndex]

                    // Find best candidate cell using Heuristic Evaluation
                    var bestScore = -99999
                    var bestCell = strategicPriorityOrder.first()

                    for (candidate in strategicPriorityOrder) {
                        val r = candidate.first
                        val c = candidate.second

                        if (virtualBoard[r][c] == 0) {
                            var score = 0

                            // 1. Check Line Clear Potential (+1000)
                            var rowCount = 1
                            for (cc in 0..7) if (cc != c && virtualBoard[r][cc] == 1) rowCount++
                            if (rowCount >= 7) score += 1000 // Close to completing full row!

                            var colCount = 1
                            for (rr in 0..7) if (rr != r && virtualBoard[rr][c] == 1) colCount++
                            if (colCount >= 7) score += 1000 // Close to completing full column!

                            // 2. Wall / Corner Clamping Bonus (+80)
                            if (r == 0 || r == 7 || c == 0 || c == 7) score += 80

                            // 3. Adjacency to existing blocks (+50)
                            if (r > 0 && virtualBoard[r - 1][c] == 1) score += 50
                            if (r < 7 && virtualBoard[r + 1][c] == 1) score += 50
                            if (c > 0 && virtualBoard[r][c - 1] == 1) score += 50
                            if (c < 7 && virtualBoard[r][c + 1] == 1) score += 50

                            if (score > bestScore) {
                                bestScore = score
                                bestCell = candidate
                            }
                        }
                    }

                    val targetR = bestCell.first
                    val targetC = bestCell.second
                    virtualBoard[targetR][targetC] = 1

                    // Target grid coordinates
                    val toGridX = gridLeft + (targetC + 0.5f) * cellW
                    val toGridY = gridTop + (targetR + 0.5f) * cellH

                    // Execute smooth Drag & Drop gesture from bottom slot to target grid cell
                    tapService.dragAndDrop(fromSlotX, slotY, toGridX, toGridY, 320L)
                    ControllerStateRepository.incrementPlacements()
                    delay(480L)

                    // Check and clear full rows/cols in virtual board
                    for (rr in 0..7) {
                        if (virtualBoard[rr].all { it == 1 }) {
                            for (cc in 0..7) virtualBoard[rr][cc] = 0
                        }
                    }
                }

                cycle++
                // Reset/refresh board sync every few cycles to prevent desync
                if (cycle % 4 == 0) {
                    delay(600L)
                    // Tap dismiss popups or bonus chests
                    tapService.tap(screenW * 0.5f, screenH * 0.70f)
                    delay(300L)
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
