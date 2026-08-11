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

class FloatingMouseAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_SHOW_MOUSE = "com.floatingmouse.app.ACTION_SHOW_MOUSE"
        const val NOTIFICATION_ID = 9981
        const val CHANNEL_ID = "floating_mouse_service_channel"

        var isServiceRunning = false
    }

    private var windowManager: WindowManager? = null

    // Separate Floating Windows
    private var mousePointerView: ImageView? = null
    private var mouseShellView: LinearLayout? = null
    private var clickBarView: LinearLayout? = null
    private var minimizedBubbleView: LinearLayout? = null

    private var pointerParams: WindowManager.LayoutParams? = null
    private var mouseShellParams: WindowManager.LayoutParams? = null
    private var clickBarParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var exactPointerX = 0f
    private var exactPointerY = 0f
    private var lastDragDispatchTime = 0L

    // Drag & Drop Live Touch States
    private var isGrabPointSaved = false
    private var isDragModeActive = false

    // Touch Hold State
    private var isTouchHoldActive = false
    private var activeHoldStroke: GestureDescription.StrokeDescription? = null

    private var currentSkinIndex = 0
    private var screenWidth = 1080
    private var screenHeight = 1920

    private var lastTouchX = 0f
    private var lastTouchY = 0f

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
                .setContentText("برای نمایش مجدد موس، اینجا کلیک کنید")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreAndShowAllOverlays() {
        if (minimizedBubbleView?.visibility == View.VISIBLE) {
            minimizedBubbleView?.visibility = View.GONE
        }
        mousePointerView?.visibility = View.VISIBLE
        mouseShellView?.visibility = View.VISIBLE
        clickBarView?.visibility = View.VISIBLE
    }

    private fun setupFloatingComponents() {
        if (!Settings.canDrawOverlays(this)) return

        removeAllViewsQuietly()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val pointerSize = dpToPx(32)
        exactPointerX = (screenWidth / 2).toFloat()
        exactPointerY = (screenHeight / 3).toFloat()

        mousePointerView = ImageView(this).apply {
            setImageBitmap(createPointerBitmap(currentSkinIndex))
        }

        pointerParams = WindowManager.LayoutParams(
            pointerSize, pointerSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = exactPointerX.toInt()
            y = exactPointerY.toInt()
        }

        // Touchpad Panel
        val shellWidth = dpToPx(160)
        val shellHeight = dpToPx(170)

        mouseShellView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xEE1E1E2C.toInt())
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), 0xFF00E5FF.toInt())
            }
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(6))
        }

        val titleText = TextView(this).apply {
            text = "تاچ‌پد 🖱️"
            setTextColor(0xFF00E5FF.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnSkin = Button(this).apply {
            text = "پوینتر"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0xFF334155.toInt())
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(26))
            setOnClickListener {
                currentSkinIndex = (currentSkinIndex + 1) % 5
                mousePointerView?.setImageBitmap(createPointerBitmap(currentSkinIndex))
            }
        }

        val btnMinimize = Button(this).apply {
            text = "—"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0xFFEF4444.toInt())
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(26)).apply {
                setMargins(dpToPx(4), 0, 0, 0)
            }
            setOnClickListener {
                mousePointerView?.visibility = View.GONE
                mouseShellView?.visibility = View.GONE
                clickBarView?.visibility = View.GONE
                minimizedBubbleView?.visibility = View.VISIBLE
            }
        }

        headerLayout.addView(titleText)
        headerLayout.addView(btnSkin)
        headerLayout.addView(btnMinimize)

        val moveHeaderView = TextView(this).apply {
            text = "❖ جابه‌جایی پنل"
            textSize = 9f
            setTextColor(0xFF94A3B8.toInt())
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF2A2D3D.toInt())
                cornerRadius = dpToPx(4).toFloat()
            }
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        var headerTouchX = 0f
        var headerTouchY = 0f
        moveHeaderView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    headerTouchX = event.rawX
                    headerTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - headerTouchX
                    val dy = event.rawY - headerTouchY
                    mouseShellParams?.let {
                        it.x = (it.x + dx.toInt()).coerceIn(0, screenWidth - dpToPx(120))
                        it.y = (it.y + dy.toInt()).coerceIn(0, screenHeight - dpToPx(120))
                        windowManager?.updateViewLayout(mouseShellView, it)
                    }
                    headerTouchX = event.rawX
                    headerTouchY = event.rawY
                    true
                }
                else -> false
            }
        }

        val touchpadArea = View(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF12131C.toInt())
                cornerRadius = dpToPx(10).toFloat()
                setStroke(dpToPx(1), 0xFF334155.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            ).apply {
                setMargins(0, dpToPx(4), 0, 0)
            }
        }

        touchpadArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    updateScreenDimensions()
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
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

                        if (isTouchHoldActive) {
                            moveContinuousDrag(oldTip.first, oldTip.second, newTip.first, newTip.second)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        mouseShellView?.addView(headerLayout)
        mouseShellView?.addView(moveHeaderView)
        mouseShellView?.addView(touchpadArea)

        mouseShellParams = WindowManager.LayoutParams(
            shellWidth, shellHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = screenWidth - shellWidth - dpToPx(16)
            y = screenHeight / 2 - shellHeight / 2
        }

        // Action Buttons Bar
        clickBarView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xEE1E1E2C.toInt())
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(2), 0xFFFF4081.toInt())
            }
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
        }

        val clickBarHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2D3D"))
                cornerRadius = dpToPx(4).toFloat()
            }
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
        }

        val clickBarTitle = TextView(this).apply {
            text = "❖ کلیک‌ها"
            textSize = 9f
            setTextColor(0xFFFF4081.toInt())
            gravity = Gravity.CENTER
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
            
            var isHoldingLeft = false
            val holdRunnable = Runnable {
                isHoldingLeft = true
                val (tipX, tipY) = getPointerHotspot()
                startTouchHold(tipX, tipY)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#00E5FF"))
                    cornerRadius = dpToPx(6).toFloat()
                }
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isHoldingLeft = false
                        v.handler?.postDelayed(holdRunnable, 180)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.handler?.removeCallbacks(holdRunnable)
                        val (tipX, tipY) = getPointerHotspot()
                        if (isHoldingLeft) {
                            releaseTouchHold(tipX, tipY)
                            isHoldingLeft = false
                        } else {
                            injectTouchClick(tipX, tipY)
                        }
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#1B2A4A"))
                            cornerRadius = dpToPx(6).toFloat()
                            setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
                        }
                        true
                    }
                    else -> false
                }
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

        // Row 2: Precision Live Grab & Drop + Touch Hold Lock
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
                    Toast.makeText(this@FloatingMouseAccessibilityService, "مارکر Mixamo/آیکون قفل شد 📍! با تاچ‌پد حرکت دهید و 'رها 🎯' را بزنید.", Toast.LENGTH_SHORT).show()
                } else {
                    releaseTouchHold(currentTipX, currentTipY)
                    Toast.makeText(this@FloatingMouseAccessibilityService, "مارکر/آیکون رها شد 🎯", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@FloatingMouseAccessibilityService, "لمس قفل شد 🔒. پس از کشیدن 'رها 🔓' را بزنید.", Toast.LENGTH_SHORT).show()
                } else {
                    releaseTouchHold(tipX, tipY)
                    Toast.makeText(this@FloatingMouseAccessibilityService, "لمس رها شد 🔓", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dragToolsRow.addView(grabButtonRef)
        dragToolsRow.addView(touchHoldBtnRef)

        // Row 3: Mixamo Canvas Tap & Long Press
        val liveModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        val mixamoTapBtn = Button(this).apply {
            text = "تک‌کلیک WebGL 🎯"
            textSize = 8f
            setTextColor(Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#003847"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(34), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectMixamoCanvasTap(tipX, tipY)
                Toast.makeText(this@FloatingMouseAccessibilityService, "کلیک دقیق ۶۰ms بوم Mixamo انجام شد 🎯", Toast.LENGTH_SHORT).show()
            }
        }

        val longPressBtn = Button(this).apply {
            text = "مکث/نگه"
            textSize = 8f
            setTextColor(Color.parseColor("#FFAB91"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3E2723"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(34), 1f).apply { setMargins(dpToPx(1), 0, 0, 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectLongPress(tipX, tipY)
            }
        }

        liveModeRow.addView(mixamoTapBtn)
        liveModeRow.addView(longPressBtn)

        clickBarView?.addView(clickBarHeader)
        clickBarView?.addView(clicksRow)
        clickBarView?.addView(dragToolsRow)
        clickBarView?.addView(liveModeRow)

        val barWidth = dpToPx(140)
        val barHeight = dpToPx(160)

        clickBarParams = WindowManager.LayoutParams(
            barWidth, barHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = screenWidth - shellWidth - barWidth - dpToPx(28)
            y = screenHeight / 2 - barHeight / 2
        }

        // Minimized Floating Bubble
        minimizedBubbleView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xEE1E1E2C.toInt())
                cornerRadius = dpToPx(24).toFloat()
                setStroke(dpToPx(2), 0xFF00E5FF.toInt())
            }
            visibility = View.GONE
        }

        val bubbleText = TextView(this).apply {
            text = "🖱️ موس شناور"
            setTextColor(0xFF00E5FF.toInt())
            textSize = 12f
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
        }
        minimizedBubbleView?.addView(bubbleText)

        minimizedBubbleView?.setOnClickListener {
            restoreAndShowAllOverlays()
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = dpToPx(16)
            y = dpToPx(100)
        }

        try {
            windowManager?.addView(mousePointerView, pointerParams)
            windowManager?.addView(mouseShellView, mouseShellParams)
            windowManager?.addView(clickBarView, clickBarParams)
            windowManager?.addView(minimizedBubbleView, bubbleParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPointerHotspot(): Pair<Float, Float> {
        val px = exactPointerX
        val py = exactPointerY
        val size = dpToPx(32).toFloat()
        return when (currentSkinIndex) {
            1 -> Pair(px + size / 2f, py + size / 2f - 2f)
            2 -> Pair(px + size / 2f, py + size / 2f)
            3 -> Pair(px + size / 2f, py + size / 2f)
            4 -> Pair(px + size / 2f, py + size / 2f)
            else -> Pair(px, py) // Default arrow tip exact top-left (0,0)
        }
    }

    private fun createPointerBitmap(skinIndex: Int): Bitmap {
        val size = dpToPx(32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val center = size / 2f

        when (skinIndex) {
            0 -> {
                // Classic Sharp Arrow (Tip exact at 0,0)
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size * 0.85f, size * 0.55f)
                    lineTo(size * 0.5f, size * 0.55f)
                    lineTo(size * 0.75f, size.toFloat())
                    lineTo(size * 0.55f, size.toFloat())
                    lineTo(size * 0.35f, size * 0.6f)
                    lineTo(0f, size * 0.9f)
                    close()
                }
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dpToPx(2).toFloat()
                canvas.drawPath(path, paint)

                paint.color = 0xFF00E5FF.toInt()
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)
            }
            1 -> {
                // Precision Target Crosshair
                paint.color = 0xFFFF4081.toInt()
                paint.style = Paint.Style.FILL
                canvas.drawCircle(center, center, size / 3f, paint)

                paint.color = Color.WHITE
                paint.strokeWidth = dpToPx(2).toFloat()
                canvas.drawLine(center, 0f, center, size.toFloat(), paint)
                canvas.drawLine(0f, center, size.toFloat(), center, paint)
            }
            2 -> {
                // Cyber Ring
                paint.color = Color.parseColor("#00E5FF")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                val path = Path().apply {
                    addCircle(center, center, size * 0.4f, Path.Direction.CW)
                }
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
        }
        return bitmap
    }

    fun injectTouchClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val clickPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX + 0.1f, clampedY + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 120)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectDoubleClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val clickPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX + 0.1f, clampedY + 0.1f)
        }
        val stroke1 = GestureDescription.StrokeDescription(clickPath, 0, 90)
        val stroke2 = GestureDescription.StrokeDescription(clickPath, 140, 90)
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
            lineTo(clampedX + 0.1f, clampedY + 0.1f)
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
            lineTo(clampedX + 0.1f, clampedY + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 750)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Touch Hold Lock with Micro-Movement (Bypasses Chrome's Long-Press Context Menu)
     * Moving 2px within 40ms forces Chrome to cancel long-press text selection / popups 
     * and routes events directly to WebGL / Canvas pointermove handlers for Mixamo rigging!
     */
    fun startTouchHold(x: Float, y: Float) {
        val clampedX = x.coerceIn(5f, (screenWidth - 5).toFloat())
        val clampedY = y.coerceIn(5f, (screenHeight - 5).toFloat())

        val holdPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX + 2.0f, clampedY + 2.0f)
        }
        val stroke = GestureDescription.StrokeDescription(holdPath, 0, 400, true)
        activeHoldStroke = stroke
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        isTouchHoldActive = true
    }

    /**
     * Specialized 60ms WebGL Canvas Tap for Mixamo 2-Tap Marker Placement
     * (Tap 1: Select Chin/Wrist Marker -> Tap 2: Touch 3D Model Joint)
     */
    fun injectMixamoCanvasTap(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val tapPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX + 0.1f, clampedY + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(tapPath, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
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
            lineTo(clampedX + 0.1f, clampedY + 0.1f)
        }
        val stroke = activeHoldStroke?.continueStroke(path, 0, 80, false)
            ?: GestureDescription.StrokeDescription(path, 0, 80, false)
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

        pointerParams?.let { p ->
            p.x = p.x.coerceIn(0, screenWidth - dpToPx(15))
            p.y = p.y.coerceIn(0, screenHeight - dpToPx(15))
            mousePointerView?.let { v ->
                if (v.windowToken != null) windowManager?.updateViewLayout(v, p)
            }
        }

        mouseShellParams?.let { sp ->
            sp.x = sp.x.coerceIn(0, screenWidth - dpToPx(120))
            sp.y = sp.y.coerceIn(0, screenHeight - dpToPx(120))
            mouseShellView?.let { sv ->
                if (sv.windowToken != null) windowManager?.updateViewLayout(sv, sp)
            }
        }

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
