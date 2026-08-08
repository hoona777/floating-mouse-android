package com.floatingmouse.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
 * Advanced Physical Floating Mouse Accessibility Service
 * Target File Path in GitHub: app/src/main/java/com/floatingmouse/app/FloatingMouseAccessibilityService.kt
 */
class FloatingMouseAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_SHOW_MOUSE = "com.floatingmouse.app.ACTION_SHOW_MOUSE"
        const val NOTIFICATION_ID = 9981
        const val CHANNEL_ID = "floating_mouse_service_channel"
    }

    private var windowManager: WindowManager? = null

    // Separate Independent Floating Overlay Windows
    private var mousePointerView: ImageView? = null
    private var mouseShellView: LinearLayout? = null // Touchpad Body Window
    private var clickBarView: LinearLayout? = null   // Click Bar Panel Window
    private var minimizedBubbleView: TextView? = null // Minimized Floating Badge

    // Independent Window Manager Layout Parameters
    private var pointerParams: WindowManager.LayoutParams? = null
    private var mouseShellParams: WindowManager.LayoutParams? = null
    private var clickBarParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // Flags & States
    private var isDragModeActive = false
    private var isGrabPointSaved = false
    private var grabStartX = 0f
    private var grabStartY = 0f

    private var isMinimized = false
    private var currentSkinIndex = 0
    private val skinNames = arrayOf("فلش فیزیکی کوچک", "دست/پوینتر", "گیمینگ RGB", "نئون glow", "نقطه لیزر")

    private var screenWidth = 1080
    private var screenHeight = 2400

    private var grabButtonRef: Button? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
        this.serviceInfo = info

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        createNotificationChannel()
        startForegroundNotification()

        setupFloatingComponents()
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
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Restore and make all floating windows visible on screen
     */
    fun restoreAndShowAllOverlays() {
        isMinimized = false
        if (mousePointerView == null || mouseShellView == null || clickBarView == null) {
            setupFloatingComponents()
            return
        }

        try {
            minimizedBubbleView?.visibility = View.GONE

            // Bring Pointer to visible area
            pointerParams?.let {
                it.x = screenWidth / 2
                it.y = screenHeight / 3
                mousePointerView?.visibility = View.VISIBLE
                windowManager?.updateViewLayout(mousePointerView, it)
            }

            // Bring Touchpad Shell to visible area (Bottom Right)
            mouseShellParams?.let {
                it.x = (screenWidth * 0.45).toInt()
                it.y = (screenHeight * 0.55).toInt()
                mouseShellView?.visibility = View.VISIBLE
                windowManager?.updateViewLayout(mouseShellView, it)
            }

            // Bring Click Bar Panel to visible area (Bottom Left)
            clickBarParams?.let {
                it.x = 40
                it.y = (screenHeight * 0.55).toInt()
                clickBarView?.visibility = View.VISIBLE
                windowManager?.updateViewLayout(clickBarView, it)
            }

            Toast.makeText(this, "موس و پنل‌های شناور بازیابی و ظاهر شدند 🖱️", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            setupFloatingComponents()
        }
    }

    private fun setupFloatingComponents() {
        removeAllViewsQuietly()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. MOUSE POINTER CURSOR WINDOW
        mousePointerView = ImageView(this).apply {
            setImageBitmap(createPointerBitmap(currentSkinIndex))
        }

        pointerParams = WindowManager.LayoutParams(
            60, 60,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = screenWidth / 2
            y = screenHeight / 3
        }

        // 2. MINIMIZED FLOATING BUBBLE
        minimizedBubbleView = TextView(this).apply {
            text = "🖱️ موس"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 30f
                setStroke(3, Color.parseColor("#00E5FF"))
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
            x = 50
            y = 200
        }

        // 3. INDEPENDENT TOUCHPAD BODY WINDOW (mouseShellView)
        mouseShellView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 8, 10, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0F172A"))
                cornerRadius = 16f
                setStroke(3, Color.parseColor("#00E5FF"))
            }
        }

        mouseShellParams = WindowManager.LayoutParams(
            420, 360,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (screenWidth * 0.45).toInt()
            y = (screenHeight * 0.55).toInt()
        }

        val touchpadHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 2, 4, 6)
        }

        val touchpadTitle = TextView(this).apply {
            text = "::: 🖱️ تاچ‌پد شناور :::"
            textSize = 10.5f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val centerPointerBtn = Button(this).apply {
            text = "🎯"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = 8f
            }
            layoutParams = LinearLayout.LayoutParams(36, 36).apply { setMargins(0, 0, 4, 0) }
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
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = 8f
            }
            layoutParams = LinearLayout.LayoutParams(36, 36)
            setOnClickListener {
                minimizeToBubble()
            }
        }

        touchpadHeader.addView(touchpadTitle)
        touchpadHeader.addView(centerPointerBtn)
        touchpadHeader.addView(minimizeBtn)

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

        val padSurface = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 12f
                setStroke(1, Color.parseColor("#334155"))
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
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - lastTouchX) * 1.8f
                    val dy = (event.y - lastTouchY) * 1.8f
                    lastTouchX = event.x
                    lastTouchY = event.y

                    pointerParams?.let {
                        it.x = (it.x + dx.toInt()).coerceIn(0, screenWidth - 20)
                        it.y = (it.y + dy.toInt()).coerceIn(0, screenHeight - 20)
                        mousePointerView?.let { v -> windowManager?.updateViewLayout(v, it) }

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

        // 4. INDEPENDENT CLICK BAR PANEL WINDOW (clickBarView)
        clickBarView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 8, 10, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0F172A"))
                cornerRadius = 16f
                setStroke(3, Color.parseColor("#FF4081"))
            }
        }

        clickBarParams = WindowManager.LayoutParams(
            440, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 40
            y = (screenHeight * 0.55).toInt()
        }

        val clickBarHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 2, 4, 6)
        }

        val clickBarTitle = TextView(this).apply {
            text = "::: 🖐️ پنل کلیک شناور :::"
            textSize = 10.5f
            setTextColor(Color.parseColor("#FF4081"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        clickBarHeader.addView(clickBarTitle)

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

        val clicksRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, 2, 0, 2)
        }

        val leftClickBtn = Button(this).apply {
            text = "چپ"
            textSize = 9.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B2A4A"))
                cornerRadius = 10f
                setStroke(2, Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(0, 0, 1, 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectTouchClick(tipX, tipY)
            }
        }

        val longPressBtn = Button(this).apply {
            text = "نگه‌داشتن"
            textSize = 8.5f
            setTextColor(Color.YELLOW)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A241B"))
                cornerRadius = 10f
                setStroke(2, Color.parseColor("#FFB300"))
            }
            layoutParams = LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(1, 0, 1, 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectLongPress(tipX, tipY)
            }
        }

        val rightClickBtn = Button(this).apply {
            text = "راست"
            textSize = 9.5f
            setTextColor(Color.LTGRAY)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A1B2A"))
                cornerRadius = 10f
                setStroke(2, Color.parseColor("#FF4081"))
            }
            layoutParams = LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(1, 0, 0, 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectRightClick(tipX, tipY)
            }
        }

        clicksRow.addView(leftClickBtn)
        clicksRow.addView(longPressBtn)
        clicksRow.addView(rightClickBtn)

        val dragToolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 2, 0, 2)
        }

        grabButtonRef = Button(this).apply {
            text = "گرفتن 📍"
            textSize = 9f
            setTextColor(Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F2027"))
                cornerRadius = 8f
                setStroke(2, Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(0, 0, 2, 0) }
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
                        cornerRadius = 8f
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "محل شیء/مفصل ثبت شد. نشانگر را به مقصد برده و 'رها 🎯' را بزنید.", Toast.LENGTH_SHORT).show()
                } else {
                    injectPrecisionDragAndDrop(grabStartX, grabStartY, currentTipX, currentTipY)
                    isGrabPointSaved = false
                    text = "گرفتن 📍"
                    setTextColor(Color.parseColor("#00E5FF"))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#0F2027"))
                        cornerRadius = 8f
                        setStroke(2, Color.parseColor("#00E5FF"))
                    }
                }
            }
        }

        val dragToggleBtn = Button(this).apply {
            text = "کشیدن"
            textSize = 9f
            setTextColor(Color.parseColor("#FFD700"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222510"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#FFD700"))
            }
            layoutParams = LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(2, 0, 0, 0) }
            setOnClickListener {
                isDragModeActive = !isDragModeActive
                if (isDragModeActive) {
                    text = "کشیدن [فعال]"
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00E676"))
                        cornerRadius = 8f
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "حالت کشیدن پیوسته فعال شد. حرکت تاچ‌پد، شیء زیر موس را می‌کشد.", Toast.LENGTH_SHORT).show()
                } else {
                    text = "کشیدن"
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#222510"))
                        cornerRadius = 8f
                        setStroke(1, Color.parseColor("#FFD700"))
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "حالت کشیدن غیرفعال شد", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dragToolsRow.addView(grabButtonRef)
        dragToolsRow.addView(dragToggleBtn)

        val utilsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, 2, 0, 0)
        }

        val scrollUpBtn = Button(this).apply {
            text = "اسکرول ▲"
            textSize = 8.5f
            setTextColor(Color.CYAN)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 8f
            }
            layoutParams = LinearLayout.LayoutParams(0, 42, 1f).apply { setMargins(0, 0, 1, 0) }
            setOnClickListener {
                val (px, py) = getPointerHotspot()
                injectTouchDrag(px, py + 300f, px, py - 300f, 300)
            }
        }

        val scrollDownBtn = Button(this).apply {
            text = "اسکرول ▼"
            textSize = 8.5f
            setTextColor(Color.CYAN)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 8f
            }
            layoutParams = LinearLayout.LayoutParams(0, 42, 1f).apply { setMargins(1, 0, 1, 0) }
            setOnClickListener {
                val (px, py) = getPointerHotspot()
                injectTouchDrag(px, py - 300f, px, py + 300f, 300)
            }
        }

        val skinBtn = Button(this).apply {
            text = "پوسته"
            textSize = 8.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10222A"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, 42, 1f).apply { setMargins(1, 0, 0, 0) }
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
        clickBarView?.addView(utilsRow)

        try {
            windowManager?.addView(mousePointerView, pointerParams)
            windowManager?.addView(minimizedBubbleView, bubbleParams)
            windowManager?.addView(mouseShellView, mouseShellParams)
            windowManager?.addView(clickBarView, clickBarParams)
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
        val px = (pointerParams?.x ?: 0).toFloat()
        val py = (pointerParams?.y ?: 0).toFloat()
        return when (currentSkinIndex) {
            1 -> Pair(px + 28f, py + 22f)
            3, 4 -> Pair(px + 28f, py + 28f)
            else -> Pair(px + 6f, py + 6f)
        }
    }

    private fun createPointerBitmap(skinIndex: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        when (skinIndex) {
            1 -> {
                paint.color = Color.parseColor("#FFCC80")
                canvas.drawCircle(28f, 22f, 11f, paint)
                canvas.drawRect(22f, 22f, 34f, 44f, paint)
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(28f, 22f, 11f, paint)
            }
            2 -> {
                paint.color = Color.parseColor("#9C27B0")
                val path = Path().apply {
                    moveTo(6f, 6f)
                    lineTo(50f, 28f)
                    lineTo(30f, 30f)
                    lineTo(28f, 50f)
                    close()
                }
                canvas.drawPath(path, paint)
                paint.color = Color.parseColor("#00E5FF")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                canvas.drawPath(path, paint)
            }
            3 -> {
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(28f, 28f, 20f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(28f, 28f, 7f, paint)
            }
            4 -> {
                paint.color = Color.RED
                canvas.drawCircle(28f, 28f, 12f, paint)
                paint.color = Color.parseColor("#FFCDD2")
                canvas.drawCircle(28f, 28f, 5f, paint)
            }
            else -> {
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(6f, 6f)
                    lineTo(25f, 52f)
                    lineTo(32f, 32f)
                    lineTo(52f, 25f)
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
        val clickPath = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectLongPress(x: Float, y: Float) {
        val clickPath = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.1f, y + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 800)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectRightClick(x: Float, y: Float) {
        val clickPath = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 750)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectPrecisionDragAndDrop(startX: Float, startY: Float, endX: Float, endY: Float) {
        val clampedStartX = startX.coerceIn(10f, (screenWidth - 10).toFloat())
        val clampedStartY = startY.coerceIn(10f, (screenHeight - 10).toFloat())
        val clampedEndX = endX.coerceIn(10f, (screenWidth - 10).toFloat())
        val clampedEndY = endY.coerceIn(10f, (screenHeight - 10).toFloat())

        val holdPath = Path().apply {
            moveTo(clampedStartX, clampedStartY)
            lineTo(clampedStartX + 0.1f, clampedStartY + 0.1f)
        }
        val holdStroke = GestureDescription.StrokeDescription(holdPath, 0, 750, true)

        val dragPath = Path().apply {
            moveTo(clampedStartX + 0.1f, clampedStartY + 0.1f)
            val steps = 30
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val curX = clampedStartX + (clampedEndX - clampedStartX) * progress
                val curY = clampedStartY + (clampedEndY - clampedStartY) * progress
                lineTo(curX, curY)
            }
            lineTo(clampedEndX, clampedEndY)
        }
        val moveStroke = holdStroke.continueStroke(dragPath, 0, 850, false)

        val gesture = GestureDescription.Builder()
            .addStroke(moveStroke)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Toast.makeText(this@FloatingMouseAccessibilityService, "انتقال آیکون/شیء انجام شد ✅", Toast.LENGTH_SHORT).show()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Toast.makeText(this@FloatingMouseAccessibilityService, "انتقال لغو شد - دوباره امتحان کنید", Toast.LENGTH_SHORT).show()
            }
        }, null)
    }

    fun injectTouchDrag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 250) {
        val clampedStartY = startY.coerceIn(50f, (screenHeight - 50).toFloat())
        val clampedEndY = endY.coerceIn(50f, (screenHeight - 50).toFloat())
        val clampedStartX = startX.coerceIn(20f, (screenWidth - 20).toFloat())
        val clampedEndX = endX.coerceIn(20f, (screenWidth - 20).toFloat())

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
            mousePointerView?.let { windowManager?.removeView(it) }
            mouseShellView?.let { windowManager?.removeView(it) }
            clickBarView?.let { windowManager?.removeView(it) }
            minimizedBubbleView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAllViewsQuietly()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
