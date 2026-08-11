package com.floatingmouse.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Floating Mouse Accessibility Service
 * Target File Path in GitHub: app/src/main/java/com/floatingmouse/app/FloatingMouseAccessibilityService.kt
 */
class FloatingMouseAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_SHOW_MOUSE = "com.floatingmouse.app.ACTION_SHOW_MOUSE"
        const val NOTIFICATION_ID = 9981
        const val CHANNEL_ID = "floating_mouse_service_channel"

        var isServiceRunning = false
    }

    private var windowManager: WindowManager? = null

    // 100% Completely Separate Floating Windows
    private var mousePointerView: ImageView? = null
    private var mouseShellView: LinearLayout? = null // Touchpad Body Window
    private var clickBarView: LinearLayout? = null   // Click Bar Panel Window
    private var minimizedBubbleView: TextView? = null // Minimized Floating Badge

    // Layout Parameters
    private var pointerParams: WindowManager.LayoutParams? = null
    private var mouseShellParams: WindowManager.LayoutParams? = null
    private var clickBarParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var exactPointerX = 0f
    private var exactPointerY = 0f
    private var lastDragDispatchTime = 0L

    // Drag & Drop States
    private var isDragModeActive = false
    private var isGrabPointSaved = false
    private var grabStartX = 0f
    private var grabStartY = 0f
    private var isTouchHoldActive = false
    private var activeHoldStroke: GestureDescription.StrokeDescription? = null

    private var isMinimized = false
    private var currentSkinIndex = 0
    private val skinNames = arrayOf("فلش فیزیکی", "دست/پوینتر", "گیمینگ RGB", "نئون Glow", "نقطه لیزر")

    private var screenWidth = 1080
    private var screenHeight = 2400

    private var grabButtonRef: Button? = null
    private var touchHoldBtnRef: Button? = null

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
        this.serviceInfo = info

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        updateScreenDimensions()

        createNotificationChannel()
        startForegroundNotification()

        Handler(Looper.getMainLooper()).postDelayed({
            setupFloatingComponents()
        }, 300)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_MOUSE) {
            restoreAndShowAllOverlays()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "سرویس موس شناور",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "اعلان ماندگار برای بازیابی و نمایش سریع موس شناور"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        try {
            val showIntent = Intent(this, FloatingMouseAccessibilityService::class.java).apply {
                action = ACTION_SHOW_MOUSE
            }
            val pendingIntent = PendingIntent.getService(
                this,
                0,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("موس شناور فعال است")
                .setContentText("برای نمایش و بازیابی موس روی صفحه، اینجا کلیک کنید")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Restore and make all floating windows visible on screen
     */
    fun restoreAndShowAllOverlays() {
        isMinimized = false
        updateScreenDimensions()
        if (mousePointerView == null || mouseShellView == null || clickBarView == null ||
            mousePointerView?.windowToken == null) {
            setupFloatingComponents()
            return
        }

        try {
            minimizedBubbleView?.visibility = View.GONE

            // Bring Pointer to visible center
            pointerParams?.let {
                it.x = screenWidth / 2
                it.y = screenHeight / 3
                mousePointerView?.visibility = View.VISIBLE
                windowManager?.updateViewLayout(mousePointerView, it)
            }

            // Bring Touchpad Shell to visible area (Bottom Right)
            mouseShellParams?.let {
                it.x = (screenWidth * 0.48).toInt()
                it.y = (screenHeight * 0.52).toInt()
                mouseShellView?.visibility = View.VISIBLE
                windowManager?.updateViewLayout(mouseShellView, it)
            }

            // Bring Click Bar Panel to visible area (Bottom Left)
            clickBarParams?.let {
                it.x = dpToPx(12)
                it.y = (screenHeight * 0.52).toInt()
                clickBarView?.visibility = View.VISIBLE
                windowManager?.updateViewLayout(clickBarView, it)
            }

            Toast.makeText(this, "موس و پنل‌های شناور ظاهر شدند 🖱️", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            setupFloatingComponents()
        }
    }

    private fun setupFloatingComponents() {
        removeAllViewsQuietly()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Settings.canDrawOverlays(this)) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            }
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // ==========================================
        // 1. MOUSE POINTER CURSOR WINDOW
        // ==========================================
        val pointerSize = dpToPx(32)
        mousePointerView = ImageView(this).apply {
            setImageBitmap(createPointerBitmap(currentSkinIndex))
        }

        exactPointerX = (screenWidth / 2).toFloat()
        exactPointerY = (screenHeight / 3).toFloat()

        pointerParams = WindowManager.LayoutParams(
            pointerSize, pointerSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = exactPointerX.toInt()
            y = exactPointerY.toInt()
        }

        // ==========================================
        // 2. MINIMIZED FLOATING BUBBLE
        // ==========================================
        minimizedBubbleView = TextView(this).apply {
            text = "🖱️ موس"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
            }
            visibility = View.GONE
            setOnClickListener {
                restoreAndShowAllOverlays()
            }
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = dpToPx(16)
            y = dpToPx(100)
        }

        // ==========================================
        // 3. INDEPENDENT TOUCHPAD BODY WINDOW (mouseShellView)
        // ==========================================
        mouseShellView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F00F172A")) // Dark blue tint
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
            }
        }

        mouseShellParams = WindowManager.LayoutParams(
            dpToPx(175), dpToPx(165),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (screenWidth * 0.48).toInt()
            y = (screenHeight * 0.52).toInt()
        }

        // Touchpad Header (Drag Handle for moving Touchpad body anywhere)
        val touchpadHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(4))
        }

        val touchpadTitle = TextView(this).apply {
            text = "::: 🖱️ تاچ‌پد :::"
            textSize = 10f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val centerPointerBtn = Button(this).apply {
            text = "🎯"
            textSize = 9f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = dpToPx(4).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(26), dpToPx(26)).apply { setMargins(0, 0, dpToPx(2), 0) }
            setOnClickListener {
                pointerParams?.let {
                    it.x = screenWidth / 2
                    it.y = screenHeight / 3
                    mousePointerView?.let { v -> windowManager?.updateViewLayout(v, it) }
                }
            }
        }

        val minimizeBtn = Button(this).apply {
            text = "—"
            textSize = 9f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = dpToPx(4).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(26), dpToPx(26))
            setOnClickListener {
                minimizeToBubble()
            }
        }

        val closeAppBtn = Button(this).apply {
            text = "❌"
            textSize = 9f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D50000"))
                cornerRadius = dpToPx(4).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(26), dpToPx(26)).apply { setMargins(dpToPx(2), 0, 0, 0) }
            setOnClickListener {
                stopServiceAndClose()
            }
        }

        touchpadHeader.addView(touchpadTitle)
        touchpadHeader.addView(centerPointerBtn)
        touchpadHeader.addView(minimizeBtn)
        touchpadHeader.addView(closeAppBtn)

        // Dragging Touchpad Body via Header
        var padDragStartX = 0f
        var padDragStartY = 0f
        var padInitialX = 0
        var padInitialY = 0

        touchpadHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    padDragStartX = event.rawX
                    padDragStartY = event.rawY
                    padInitialX = mouseShellParams?.x ?: 0
                    padInitialY = mouseShellParams?.y ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - padDragStartX).toInt()
                    val dy = (event.rawY - padDragStartY).toInt()
                    mouseShellParams?.let {
                        it.x = padInitialX + dx
                        it.y = padInitialY + dy
                        windowManager?.updateViewLayout(mouseShellView, it)
                    }
                    true
                }
                else -> false
            }
        }

        // Touchpad Surface (Slide finger here to move cursor)
        val padSurface = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#334155"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        var lastTouchX = 0f
        var lastTouchY = 0f

        padSurface.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    updateScreenDimensions()
                    lastTouchX = event.x
                    lastTouchY = event.y

                    // If Live Drag Mode is active, touch DOWN at pointer location
                    if (isDragModeActive && !isTouchHoldActive) {
                        val (tipX, tipY) = getPointerHotspot()
                        startTouchHold(tipX, tipY)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - lastTouchX) * 1.8f
                    val dy = (event.y - lastTouchY) * 1.8f
                    lastTouchX = event.x
                    lastTouchY = event.y

                    val oldTip = getPointerHotspot()

                    exactPointerX = (exactPointerX + dx).coerceIn(0f, (screenWidth - dpToPx(15)).toFloat())
                    exactPointerY = (exactPointerY + dy).coerceIn(0f, (screenHeight - dpToPx(15)).toFloat())

                    pointerParams?.let {
                        it.x = exactPointerX.toInt()
                        it.y = exactPointerY.toInt()
                        mousePointerView?.let { v -> windowManager?.updateViewLayout(v, it) }

                        val newTip = getPointerHotspot()

                        // If Touch Hold Lock or Live Drag Mode is Active, continuously update drag stroke on screen
                        if (isTouchHoldActive || isDragModeActive) {
                            moveContinuousDrag(oldTip.first, oldTip.second, newTip.first, newTip.second)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // If Live Drag Mode is active (and not locked in Touch Hold button mode), release touch
                    if (isDragModeActive && isTouchHoldActive) {
                        val (tipX, tipY) = getPointerHotspot()
                        releaseTouchHold(tipX, tipY)
                    }
                    true
                }
                else -> false
            }
        }

        mouseShellView?.addView(touchpadHeader)
        mouseShellView?.addView(padSurface)

        // ==========================================
        // 4. INDEPENDENT CLICK BAR PANEL WINDOW (clickBarView)
        // ==========================================
        clickBarView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F00F172A")) // Dark blue tint
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#FF4081")) // Pink accent border
            }
        }

        clickBarParams = WindowManager.LayoutParams(
            dpToPx(180), WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = dpToPx(12)
            y = (screenHeight * 0.52).toInt()
        }

        // Header for dragging Click Bar Panel independently
        val clickBarHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(4))
        }

        val clickBarTitle = TextView(this).apply {
            text = "::: 🖐️ پنل کلیک :::"
            textSize = 10f
            setTextColor(Color.parseColor("#FF4081"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val clickBarCloseBtn = Button(this).apply {
            text = "❌"
            textSize = 9f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D50000"))
                cornerRadius = dpToPx(4).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
            setOnClickListener {
                stopServiceAndClose()
            }
        }

        clickBarHeader.addView(clickBarTitle)
        clickBarHeader.addView(clickBarCloseBtn)

        // Dragging Click Bar Panel via Header
        var barDragStartX = 0f
        var barDragStartY = 0f
        var barInitialX = 0
        var barInitialY = 0

        clickBarHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    barDragStartX = event.rawX
                    barDragStartY = event.rawY
                    barInitialX = clickBarParams?.x ?: 0
                    barInitialY = clickBarParams?.y ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - barDragStartX).toInt()
                    val dy = (event.rawY - barDragStartY).toInt()
                    clickBarParams?.let {
                        it.x = barInitialX + dx
                        it.y = barInitialY + dy
                        windowManager?.updateViewLayout(clickBarView, it)
                    }
                    true
                }
                else -> false
            }
        }

        // Row 1: Left Click, Double Click & Right Click
        val clicksRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        val leftClickBtn = Button(this).apply {
            text = "چپ"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B2A4A"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectTouchClick(tipX, tipY)
            }
        }

        val doubleClickBtn = Button(this).apply {
            text = "۲ کلیک"
            textSize = 8.5f
            setTextColor(Color.parseColor("#80DEEA"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#122A38"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00BCD4"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(dpToPx(1), 0, dpToPx(1), 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectDoubleClick(tipX, tipY)
            }
        }

        val rightClickBtn = Button(this).apply {
            text = "راست"
            textSize = 9f
            setTextColor(Color.LTGRAY)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A1B2A"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#FF4081"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(dpToPx(1), 0, 0, 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectRightClick(tipX, tipY)
            }
        }

        clicksRow.addView(leftClickBtn)
        clicksRow.addView(doubleClickBtn)
        clicksRow.addView(rightClickBtn)

        // Row 2: Precision Grab & Drop + Touch Hold Lock
        val dragToolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        grabButtonRef = Button(this).apply {
            text = "گرفتن 📍"
            textSize = 8.5f
            setTextColor(Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F2027"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener {
                val (currentTipX, currentTipY) = getPointerHotspot()

                if (!isTouchHoldActive) {
                    startTouchHold(currentTipX, currentTipY)
                    isGrabPointSaved = true
                    text = "رها 🎯"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00C853"))
                        cornerRadius = dpToPx(6).toFloat()
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "آیکون گرفته شد 📍. نشانگر را منتقل و 'رها 🎯' را بزنید.", Toast.LENGTH_SHORT).show()
                } else {
                    releaseTouchHold(currentTipX, currentTipY)
                    Toast.makeText(this@FloatingMouseAccessibilityService, "آیکون رها شد 🎯", Toast.LENGTH_SHORT).show()
                }
            }
        }

        touchHoldBtnRef = Button(this).apply {
            text = "فشار 🔒"
            textSize = 8.5f
            setTextColor(Color.YELLOW)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A241B"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#FFB300"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(dpToPx(1), 0, 0, 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                if (!isTouchHoldActive) {
                    startTouchHold(tipX, tipY)
                    text = "رها 🔓"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#D50000"))
                        cornerRadius = dpToPx(6).toFloat()
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "لمس نگه داشته شد 🔒. پس از جابه‌جایی 'رها 🔓' را بزنید.", Toast.LENGTH_SHORT).show()
                } else {
                    releaseTouchHold(tipX, tipY)
                    Toast.makeText(this@FloatingMouseAccessibilityService, "لمس رها شد 🔓", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dragToolsRow.addView(grabButtonRef)
        dragToolsRow.addView(touchHoldBtnRef)

        // Row 3: Live Drag & Long Press
        val liveModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        val dragToggleBtn = Button(this).apply {
            text = "کشیدن زنده"
            textSize = 8f
            setTextColor(Color.parseColor("#FFD700"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222510"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#FFD700"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(34), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener {
                isDragModeActive = !isDragModeActive
                if (isDragModeActive) {
                    text = "کشیدن [فعال]"
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00E676"))
                        cornerRadius = dpToPx(6).toFloat()
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "حالت کشیدن زنده فعال شد.", Toast.LENGTH_SHORT).show()
                } else {
                    text = "کشیدن زنده"
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#222510"))
                        cornerRadius = dpToPx(6).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#FFD700"))
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "حالت کشیدن غیرفعال شد", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val longPressBtn = Button(this).apply {
            text = "مکث/نگه"
            textSize = 8f
            setTextColor(Color.parseColor("#FFAB91"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3E2723"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#FF7043"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(34), 1f).apply { setMargins(dpToPx(1), 0, 0, 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectLongPress(tipX, tipY)
            }
        }

        liveModeRow.addView(dragToggleBtn)
        liveModeRow.addView(longPressBtn)

        // Row 3: Scroll Up/Down & Skin Switcher
        val utilsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, dpToPx(2), 0, 0)
        }

        val scrollUpBtn = Button(this).apply {
            text = "بالا ▲"
            textSize = 8f
            setTextColor(Color.CYAN)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener {
                val (px, py) = getPointerHotspot()
                injectTouchDrag(px, py + 300f, px, py - 300f, 300)
            }
        }

        val scrollDownBtn = Button(this).apply {
            text = "پایین ▼"
            textSize = 8f
            setTextColor(Color.CYAN)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(dpToPx(1), 0, dpToPx(1), 0) }
            setOnClickListener {
                val (px, py) = getPointerHotspot()
                injectTouchDrag(px, py - 300f, px, py + 300f, 300)
            }
        }

        val skinBtn = Button(this).apply {
            text = "پوسته"
            textSize = 8f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10222A"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(dpToPx(1), 0, 0, 0) }
            setOnClickListener {
                currentSkinIndex = (currentSkinIndex + 1) % skinNames.size
                val newBitmap = createPointerBitmap(currentSkinIndex)
                mousePointerView?.setImageBitmap(newBitmap)
                Toast.makeText(this@FloatingMouseAccessibilityService, "پوسته: ${skinNames[currentSkinIndex]}", Toast.LENGTH_SHORT).show()
            }
        }

        utilsRow.addView(scrollUpBtn)
        utilsRow.addView(scrollDownBtn)
        utilsRow.addView(skinBtn)

        clickBarView?.addView(clickBarHeader)
        clickBarView?.addView(clicksRow)
        clickBarView?.addView(dragToolsRow)
        clickBarView?.addView(liveModeRow)
        clickBarView?.addView(utilsRow)

        // Safely Add Overlays to WindowManager
        try {
            if (mousePointerView?.windowToken == null) windowManager?.addView(mousePointerView, pointerParams)
            if (minimizedBubbleView?.windowToken == null) windowManager?.addView(minimizedBubbleView, bubbleParams)
            if (mouseShellView?.windowToken == null) windowManager?.addView(mouseShellView, mouseShellParams)
            if (clickBarView?.windowToken == null) windowManager?.addView(clickBarView, clickBarParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun minimizeToBubble() {
        isMinimized = true
        mouseShellView?.visibility = View.GONE
        clickBarView?.visibility = View.GONE

        bubbleParams?.let { bp ->
            mouseShellParams?.let { msp ->
                bp.x = msp.x
                bp.y = msp.y
            }
            minimizedBubbleView?.visibility = View.VISIBLE
            windowManager?.updateViewLayout(minimizedBubbleView, bp)
        }
    }

    private fun getPointerHotspot(): Pair<Float, Float> {
        val px = exactPointerX
        val py = exactPointerY
        val size = dpToPx(32).toFloat()
        return when (currentSkinIndex) {
            1 -> Pair(px + size / 2f, py + size / 2f - 2f) // Hand pointer tip/center
            3, 4 -> Pair(px + size / 2f, py + size / 2f)   // Ring/Laser center
            else -> Pair(px + 3f, py + 3f)                 // Arrow tip at exact (3px, 3px) top-left
        }
    }

    private fun createPointerBitmap(skinIndex: Int): Bitmap {
        val size = dpToPx(32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        val center = size / 2f

        when (skinIndex) {
            1 -> { // Hand Pointer
                paint.color = Color.parseColor("#FFCC80")
                canvas.drawCircle(center, center - 2f, size * 0.28f, paint)
                canvas.drawRect(center - 5f, center, center + 5f, size - 2f, paint)
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(center, center - 2f, size * 0.28f, paint)
            }
            2 -> { // Gamer RGB Arrow
                paint.color = Color.parseColor("#9C27B0")
                val path = Path().apply {
                    moveTo(3f, 3f)
                    lineTo(size - 3f, size / 2f)
                    lineTo(size / 2f, size / 2f)
                    lineTo(size / 2f, size - 3f)
                    close()
                }
                canvas.drawPath(path, paint)
                paint.color = Color.parseColor("#00E5FF")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                canvas.drawPath(path, paint)
            }
            3 -> { // Neon Glow Ring
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(center, center, size * 0.42f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(center, center, size * 0.18f, paint)
            }
            4 -> { // Laser Red Dot
                paint.color = Color.RED
                canvas.drawCircle(center, center, size * 0.35f, paint)
                paint.color = Color.parseColor("#FFCDD2")
                canvas.drawCircle(center, center, size * 0.12f, paint)
            }
            else -> { // Physical White Mouse Arrow
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(3f, 3f)
                    lineTo(size * 0.45f, size - 3f)
                    lineTo(size * 0.58f, size * 0.58f)
                    lineTo(size - 3f, size * 0.45f)
                    close()
                }
                canvas.drawPath(path, paint)
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawPath(path, paint)
            }
        }

        return bitmap
    }

    fun injectTouchClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val clickPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX, clampedY)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectDoubleClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val clickPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX, clampedY)
        }
        val stroke1 = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val stroke2 = GestureDescription.StrokeDescription(clickPath, 100, 50)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun injectLongPress(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val clickPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX, clampedY)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 800)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectRightClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val clickPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX, clampedY)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 750)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Touch Hold Lock: Presses down and maintains touch on screen until released
     */
    fun startTouchHold(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val holdPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX, clampedY)
        }
        val stroke = GestureDescription.StrokeDescription(holdPath, 0, 100, true)
        activeHoldStroke = stroke
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        isTouchHoldActive = true
    }

    fun moveContinuousDrag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val now = System.currentTimeMillis()
        if (now - lastDragDispatchTime < 25) return // Throttle gesture dispatches to max ~40fps

        val cFromX = fromX.coerceIn(1f, (screenWidth - 1).toFloat())
        val cFromY = fromY.coerceIn(1f, (screenHeight - 1).toFloat())
        val cToX = toX.coerceIn(1f, (screenWidth - 1).toFloat())
        val cToY = toY.coerceIn(1f, (screenHeight - 1).toFloat())

        val dx = cToX - cFromX
        val dy = cToY - cFromY
        if (Math.hypot(dx.toDouble(), dy.toDouble()) < 1.0) return

        lastDragDispatchTime = now

        val movePath = Path().apply {
            moveTo(cFromX, cFromY)
            lineTo(cToX, cToY)
        }
        val currStroke = activeHoldStroke
        val newStroke = if (currStroke != null) {
            currStroke.continueStroke(movePath, 0, 60, true)
        } else {
            GestureDescription.StrokeDescription(movePath, 0, 60, true)
        }
        activeHoldStroke = newStroke
        val gesture = GestureDescription.Builder().addStroke(newStroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun releaseTouchHold(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val path = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX, clampedY)
        }
        val stroke = activeHoldStroke?.continueStroke(path, 0, 50, false)
            ?: GestureDescription.StrokeDescription(path, 0, 50, false)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        activeHoldStroke = null
        isTouchHoldActive = false
        resetHoldButtonsUI()
    }

    private fun resetHoldButtonsUI() {
        isGrabPointSaved = false
        grabButtonRef?.apply {
            text = "گرفتن 📍"
            setTextColor(Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F2027"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
            }
        }
        touchHoldBtnRef?.apply {
            text = "فشار 🔒"
            setTextColor(Color.YELLOW)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A241B"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#FFB300"))
            }
        }
    }

    private fun stopServiceAndClose() {
        isServiceRunning = false
        if (isTouchHoldActive) {
            val (tipX, tipY) = getPointerHotspot()
            releaseTouchHold(tipX, tipY)
        }
        removeAllViewsQuietly()
        stopSelf()
        Toast.makeText(this, "برنامه ماوس شناور بسته شد ❌", Toast.LENGTH_SHORT).show()
    }

    /**
     * High-Precision Drag & Drop Gesture for Mixamo Rigging & Android Launcher Icons
     * Uses sequential 2-phase GestureDescription (Hold -> Move) to prevent multi-touch screen pan.
     */
    fun injectPrecisionDragAndDrop(startX: Float, startY: Float, endX: Float, endY: Float) {
        val clampedStartX = startX.coerceIn(5f, (screenWidth - 5).toFloat())
        val clampedStartY = startY.coerceIn(5f, (screenHeight - 5).toFloat())
        val clampedEndX = endX.coerceIn(5f, (screenWidth - 5).toFloat())
        val clampedEndY = endY.coerceIn(5f, (screenHeight - 5).toFloat())

        // Phase 1: Touch DOWN at start position and HOLD for 550ms (willContinue = true)
        // Triggers Android Launcher long-press icon pickup and Web canvas pointerdown/touchstart
        val holdPath = Path().apply {
            moveTo(clampedStartX, clampedStartY)
            lineTo(clampedStartX + 0.1f, clampedStartY + 0.1f)
        }
        val holdStroke = GestureDescription.StrokeDescription(holdPath, 0, 550, true)
        val holdGesture = GestureDescription.Builder().addStroke(holdStroke).build()

        dispatchGesture(holdGesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)

                // Phase 2: Continue stroke from start point to destination over 850ms (willContinue = false)
                val movePath = Path().apply {
                    moveTo(clampedStartX + 0.1f, clampedStartY + 0.1f)
                    lineTo(clampedEndX, clampedEndY)
                }
                val moveStroke = holdStroke.continueStroke(movePath, 0, 850, false)
                val moveGesture = GestureDescription.Builder().addStroke(moveStroke).build()

                dispatchGesture(moveGesture, object : GestureResultCallback() {
                    override fun onCompleted(gd: GestureDescription?) {
                        super.onCompleted(gd)
                        Toast.makeText(this@FloatingMouseAccessibilityService, "انتقال آیکون/مفصل انجام شد ✅", Toast.LENGTH_SHORT).show()
                    }

                    override fun onCancelled(gd: GestureDescription?) {
                        super.onCancelled(gd)
                        injectTouchDrag(clampedStartX, clampedStartY, clampedEndX, clampedEndY, 400)
                    }
                }, null)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                injectTouchDrag(clampedStartX, clampedStartY, clampedEndX, clampedEndY, 400)
            }
        }, null)
    }

    fun injectTouchDrag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 250) {
        val clampedStartX = startX.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedStartY = startY.coerceIn(1f, (screenHeight - 1).toFloat())
        val clampedEndX = endX.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedEndY = endY.coerceIn(1f, (screenHeight - 1).toFloat())

        val dragPath = Path().apply {
            moveTo(clampedStartX, clampedStartY)
            lineTo(clampedEndX, clampedEndY)
        }
        val stroke = GestureDescription.StrokeDescription(dragPath, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun removeAllViewsQuietly() {
        try {
            mousePointerView?.let { if (it.windowToken != null) windowManager?.removeView(it) }
            mouseShellView?.let { if (it.windowToken != null) windowManager?.removeView(it) }
            clickBarView?.let { if (it.windowToken != null) windowManager?.removeView(it) }
            minimizedBubbleView?.let { if (it.windowToken != null) windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        removeAllViewsQuietly()
    }

    private fun updateScreenDimensions() {
        val wm = windowManager ?: (getSystemService(WINDOW_SERVICE) as? WindowManager)
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                screenWidth = bounds.width()
                screenHeight = bounds.height()
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
            }
        } else {
            val metrics = resources.displayMetrics
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenDimensions()

        // Re-clamp pointer inside new orientation bounds
        pointerParams?.let { p ->
            p.x = p.x.coerceIn(0, screenWidth - dpToPx(15))
            p.y = p.y.coerceIn(0, screenHeight - dpToPx(15))
            mousePointerView?.let { v ->
                if (v.windowToken != null) windowManager?.updateViewLayout(v, p)
            }
        }

        // Keep Touchpad Shell inside screen
        mouseShellParams?.let { sp ->
            sp.x = sp.x.coerceIn(0, screenWidth - dpToPx(120))
            sp.y = sp.y.coerceIn(0, screenHeight - dpToPx(120))
            mouseShellView?.let { sv ->
                if (sv.windowToken != null) windowManager?.updateViewLayout(sv, sp)
            }
        }

        // Keep Click Bar Panel inside screen
        clickBarParams?.let { cp ->
            cp.x = cp.x.coerceIn(0, screenWidth - dpToPx(120))
            cp.y = cp.y.coerceIn(0, screenHeight - dpToPx(120))
            clickBarView?.let { cv ->
                if (cv.windowToken != null) windowManager?.updateViewLayout(cv, cp)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
