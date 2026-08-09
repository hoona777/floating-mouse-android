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
            private set
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

        if (Settings.canDrawOverlays(this)) {
            setupFloatingWindows()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_MOUSE) {
            if (isMinimized) {
                restoreFromMinimized()
            } else if (mousePointerView == null && Settings.canDrawOverlays(this)) {
                setupFloatingWindows()
            }
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        removeAllViewsSafely()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenDimensions()
    }

    private fun updateScreenDimensions() {
        val displayMetrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Mouse Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "سرویس فعال نگه‌داشتن ماوس و تاچ‌پد شناور"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ماوس و تاچ‌پد شناور فعال است")
            .setContentText("برای تنظیمات لمس فرمایید")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupFloatingWindows() {
        removeAllViewsSafely()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // ==========================================
        // 1. MINIMIZED FLOATING BADGE (minimizedBubbleView)
        // ==========================================
        minimizedBubbleView = TextView(this).apply {
            text = "🖱️"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A"))
                cornerRadius = dpToPx(30).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
            }
            visibility = View.GONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = screenWidth - dpToPx(70)
            y = screenHeight / 3
        }

        var bubbleStartX = 0f
        var bubbleStartY = 0f
        var bubbleInitialX = 0
        var bubbleInitialY = 0
        var isBubbleClick = true

        minimizedBubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleStartX = event.rawX
                    bubbleStartY = event.rawY
                    bubbleInitialX = bubbleParams?.x ?: 0
                    bubbleInitialY = bubbleParams?.y ?: 0
                    isBubbleClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - bubbleStartX).toInt()
                    val dy = (event.rawY - bubbleStartY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isBubbleClick = false
                    }
                    bubbleParams?.let {
                        it.x = bubbleInitialX + dx
                        it.y = bubbleInitialY + dy
                        windowManager?.updateViewLayout(minimizedBubbleView, it)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isBubbleClick) {
                        restoreFromMinimized()
                    }
                    true
                }
                else -> false
            }
        }

        // ==========================================
        // 2. MOUSE POINTER WINDOW (mousePointerView)
        // ==========================================
        mousePointerView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        updatePointerSkinDrawable()

        pointerParams = WindowManager.LayoutParams(
            dpToPx(48), dpToPx(48),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = screenWidth / 2
            y = screenHeight / 3
        }

        // ==========================================
        // 3. TOUCHPAD BODY WINDOW (mouseShellView)
        // ==========================================
        mouseShellView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F00F172A"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
            }
        }

        mouseShellParams = WindowManager.LayoutParams(
            dpToPx(240), dpToPx(220),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (screenWidth - dpToPx(250)).coerceAtLeast(dpToPx(10))
            y = (screenHeight * 0.52).toInt()
        }

        // Header with Title, Drag Handle & Minimize Button
        val touchpadHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(6))
        }

        val headerTitle = TextView(this).apply {
            text = "::: 🖱️ تاچ‌پد شناور :::"
            textSize = 11f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minimizeBtn = TextView(this).apply {
            text = " 🗕 "
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = dpToPx(6).toFloat()
            }
            setOnClickListener {
                minimizeToBubble()
            }
        }

        touchpadHeader.addView(headerTitle)
        touchpadHeader.addView(minimizeBtn)

        // Dragging Touchpad Body via Header
        var shellDragStartX = 0f
        var shellDragStartY = 0f
        var shellInitialX = 0
        var shellInitialY = 0

        touchpadHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    shellDragStartX = event.rawX
                    shellDragStartY = event.rawY
                    shellInitialX = mouseShellParams?.x ?: 0
                    shellInitialY = mouseShellParams?.y ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - shellDragStartX).toInt()
                    val dy = (event.rawY - shellDragStartY).toInt()
                    mouseShellParams?.let {
                        it.x = shellInitialX + dx
                        it.y = shellInitialY + dy
                        windowManager?.updateViewLayout(mouseShellView, it)
                    }
                    true
                }
                else -> false
            }
        }

        // Touchpad Surface for Relative Trackpad Dragging
        val padSurface = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A00E5FF"))
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#3300E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            ).apply { setMargins(0, dpToPx(4), 0, dpToPx(4)) }
        }

        var lastTouchX = 0f
        var lastTouchY = 0f

        padSurface.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y

                    pointerParams?.let {
                        it.x = (it.x + (dx * 1.6f).toInt()).coerceIn(0, screenWidth - dpToPx(30))
                        it.y = (it.y + (dy * 1.6f).toInt()).coerceIn(0, screenHeight - dpToPx(30))
                        windowManager?.updateViewLayout(mousePointerView, it)

                        if (isDragModeActive) {
                            val (tipX, tipY) = getPointerHotspot()
                            injectTouchDrag(tipX - dx, tipY - dy, tipX, tipY, 40)
                        }
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
                setColor(Color.parseColor("#F00F172A"))
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#FF4081"))
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

        clickBarHeader.addView(clickBarTitle)

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

                if (!isGrabPointSaved) {
                    grabStartX = currentTipX
                    grabStartY = currentTipY
                    isGrabPointSaved = true
                    text = "رها 🎯"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00C853"))
                        cornerRadius = dpToPx(6).toFloat()
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "محل آیکون/مفصل ثبت شد. نشانگر را منتقل و 'رها 🎯' را بزنید.", Toast.LENGTH_SHORT).show()
                } else {
                    injectPrecisionDragAndDrop(grabStartX, grabStartY, currentTipX, currentTipY)
                    isGrabPointSaved = false
                    text = "گرفتن 📍"
                    setTextColor(Color.parseColor("#00E5FF"))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#0F2027"))
                        cornerRadius = dpToPx(6).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
                    }
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
                    text = "فشار 🔒"
                    setTextColor(Color.YELLOW)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#2A241B"))
                        cornerRadius = dpToPx(6).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#FFB300"))
                    }
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

        // Row 4: Scroll Up/Down & Skin Switcher
        val utilsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, dpToPx(2), 0, 0)
        }

        val scrollUpBtn = Button(this).apply {
            text = "بالا ▲"
            textSize = 8f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2937"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener { injectScroll(isUp = true) }
        }

        val scrollDownBtn = Button(this).apply {
            text = "پایین ▼"
            textSize = 8f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2937"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(dpToPx(1), 0, dpToPx(1), 0) }
            setOnClickListener { injectScroll(isUp = false) }
        }

        val skinBtn = Button(this).apply {
            text = "پوسته 🎨"
            textSize = 8f
            setTextColor(Color.parseColor("#A7F3D0"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#064E3B"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(dpToPx(1), 0, 0, 0) }
            setOnClickListener {
                currentSkinIndex = (currentSkinIndex + 1) % skinNames.size
                updatePointerSkinDrawable()
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

        // Add all windows to WindowManager separately
        try {
            windowManager?.addView(minimizedBubbleView, bubbleParams)
            windowManager?.addView(mousePointerView, pointerParams)
            windowManager?.addView(mouseShellView, mouseShellParams)
            windowManager?.addView(clickBarView, clickBarParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPointerHotspot(): Pair<Float, Float> {
        val px = pointerParams?.x?.toFloat() ?: (screenWidth / 2f)
        val py = pointerParams?.y?.toFloat() ?: (screenHeight / 3f)
        return Pair(px + dpToPx(4), py + dpToPx(4))
    }

    private fun minimizeToBubble() {
        isMinimized = true
        mousePointerView?.visibility = View.GONE
        mouseShellView?.visibility = View.GONE
        clickBarView?.visibility = View.GONE
        minimizedBubbleView?.visibility = View.VISIBLE
    }

    private fun restoreFromMinimized() {
        isMinimized = false
        minimizedBubbleView?.visibility = View.GONE
        mousePointerView?.visibility = View.VISIBLE
        mouseShellView?.visibility = View.VISIBLE
        clickBarView?.visibility = View.VISIBLE
    }

    private fun removeAllViewsSafely() {
        try {
            minimizedBubbleView?.let { windowManager?.removeViewImmediate(it) }
            mousePointerView?.let { windowManager?.removeViewImmediate(it) }
            mouseShellView?.let { windowManager?.removeViewImmediate(it) }
            clickBarView?.let { windowManager?.removeViewImmediate(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        minimizedBubbleView = null
        mousePointerView = null
        mouseShellView = null
        clickBarView = null
    }

    // ==========================================
    // ACCESSIBILITY TOUCH INJECTION METHODS
    // ==========================================

    private fun injectTouchClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun injectDoubleClick(x: Float, y: Float) {
        injectTouchClick(x, y)
        Handler(Looper.getMainLooper()).postDelayed({
            injectTouchClick(x, y)
        }, 120)
    }

    private fun injectRightClick(x: Float, y: Float) {
        injectLongPress(x, y)
    }

    private fun injectLongPress(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 700)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun injectPrecisionDragAndDrop(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 600)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun startTouchHold(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        activeHoldStroke = GestureDescription.StrokeDescription(path, 0, 50000, true)
        val gesture = GestureDescription.Builder().addStroke(activeHoldStroke!!).build()
        dispatchGesture(gesture, null, null)
        isTouchHoldActive = true
    }

    private fun releaseTouchHold(x: Float, y: Float) {
        if (activeHoldStroke != null) {
            val path = Path().apply { moveTo(x, y) }
            val continueStroke = activeHoldStroke!!.continueStroke(path, 0, 20, false)
            val gesture = GestureDescription.Builder().addStroke(continueStroke).build()
            dispatchGesture(gesture, null, null)
            activeHoldStroke = null
        }
        isTouchHoldActive = false
    }

    private fun injectTouchDrag(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(fromX.coerceIn(0f, screenWidth.toFloat()), fromY.coerceIn(0f, screenHeight.toFloat()))
            lineTo(toX.coerceIn(0f, screenWidth.toFloat()), toY.coerceIn(0f, screenHeight.toFloat()))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun injectScroll(isUp: Boolean) {
        val centerX = screenWidth / 2f
        val startY = if (isUp) screenHeight * 0.4f else screenHeight * 0.6f
        val endY = if (isUp) screenHeight * 0.7f else screenHeight * 0.3f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // ==========================================
    // VECTOR DRAWABLE GENERATORS FOR MOUSE SKINS
    // ==========================================

    private fun updatePointerSkinDrawable() {
        val sizePx = dpToPx(48)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (currentSkinIndex) {
            0 -> drawClassicArrowSkin(canvas, sizePx)
            1 -> drawHandPointerSkin(canvas, sizePx)
            2 -> drawGamingRgbSkin(canvas, sizePx)
            3 -> drawNeonGlowSkin(canvas, sizePx)
            4 -> drawLaserDotSkin(canvas, sizePx)
            else -> drawClassicArrowSkin(canvas, sizePx)
        }

        mousePointerView?.setImageBitmap(bitmap)
    }

    private fun drawClassicArrowSkin(canvas: Canvas, size: Int) {
        val path = Path().apply {
            moveTo(2f, 2f)
            lineTo(size * 0.7f, size * 0.45f)
            lineTo(size * 0.42f, size * 0.48f)
            lineTo(size * 0.62f, size * 0.88f)
            lineTo(size * 0.48f, size * 0.95f)
            lineTo(size * 0.28f, size * 0.55f)
            lineTo(2f, size * 0.72f)
            close()
        }

        val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(2).toFloat()
            color = Color.BLACK
        }

        canvas.drawPath(path, paintFill)
        canvas.drawPath(path, paintStroke)
    }

    private fun drawHandPointerSkin(canvas: Canvas, size: Int) {
        val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FFD54F")
        }
        val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(2).toFloat()
            color = Color.parseColor("#37474F")
        }

        val path = Path().apply {
            moveTo(size * 0.35f, size * 0.1f)
            lineTo(size * 0.45f, size * 0.1f)
            lineTo(size * 0.45f, size * 0.5f)
            lineTo(size * 0.8f, size * 0.5f)
            lineTo(size * 0.8f, size * 0.85f)
            lineTo(size * 0.2f, size * 0.85f)
            lineTo(size * 0.2f, size * 0.5f)
            close()
        }

        canvas.drawPath(path, paintFill)
        canvas.drawPath(path, paintStroke)
    }

    private fun drawGamingRgbSkin(canvas: Canvas, size: Int) {
        val path = Path().apply {
            moveTo(2f, 2f)
            lineTo(size * 0.75f, size * 0.4f)
            lineTo(size * 0.45f, size * 0.45f)
            lineTo(size * 0.4f, size * 0.75f)
            close()
        }

        val shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA),
            null, Shader.TileMode.CLAMP
        )

        val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.shader = shader
        }

        val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(2).toFloat()
            color = Color.WHITE
        }

        canvas.drawPath(path, paintFill)
        canvas.drawPath(path, paintStroke)
    }

    private fun drawNeonGlowSkin(canvas: Canvas, size: Int) {
        val center = size / 2f
        val radius = size * 0.35f

        val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#8000E5FF")
        }

        val paintCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#00E5FF")
        }

        canvas.drawCircle(center, center, radius, paintGlow)
        canvas.drawCircle(center, center, radius * 0.5f, paintCore)
    }

    private fun drawLaserDotSkin(canvas: Canvas, size: Int) {
        val center = size / 2f
        val radius = size * 0.25f

        val paintCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.RED
        }

        val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(2).toFloat()
            color = Color.YELLOW
        }

        canvas.drawCircle(center, center, radius, paintCore)
        canvas.drawCircle(center, center, radius * 1.4f, paintRing)
    }
}
