package com.floatingmouse/app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
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
import android.util.DisplayMetrics
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingMouseAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null

    // Overlay Views
    private var mousePointerView: View? = null
    private var mouseShellView: View? = null
    private var clickBarView: View? = null

    // WindowManager LayoutParams
    private var pointerParams: WindowManager.LayoutParams? = null
    private var mouseShellParams: WindowManager.LayoutParams? = null
    private var clickBarParams: WindowManager.LayoutParams? = null

    // Screen Dimensions
    private var screenWidth = 1080
    private var screenHeight = 2340

    // Touchpad Drag & Motion tracking
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Shell Drag tracking
    private var shellInitialX = 0
    private var shellInitialY = 0
    private var shellTouchStartX = 0f
    private var shellTouchStartY = 0f

    // ClickBar Drag tracking
    private var clickBarInitialX = 0
    private var clickBarInitialY = 0
    private var clickBarTouchStartX = 0f
    private var clickBarTouchStartY = 0f

    // Floating Mode State
    private var isMinimized = false
    private var minimizedView: View? = null
    private var minimizedParams: WindowManager.LayoutParams? = null

    // Customization Settings
    private var pointerSpeedMultiplier = 1.6f
    private var mouseColor = Color.parseColor("#00BCD4") // Cyan Neon default
    private var isLeftHandMode = false
    private var isHapticFeedbackEnabled = true

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        updateScreenDimensions()

        createNotificationChannel()
        startForegroundNotification()

        setupFloatingComponents()
    }

    private fun setupFloatingComponents() {
        createMousePointerView()
        createMouseShellView()
        createClickBarView()
    }

    /**
     * Restore full overlay UI from minimized floating button
     */
    fun restoreAndShowAllOverlays() {
        isMinimized = false
        updateScreenDimensions()
        if (mousePointerView == null || mouseShellView == null || clickBarView == null ||
            mousePointerView?.windowToken == null) {
            setupFloatingComponents()
        } else {
            mousePointerView?.visibility = View.VISIBLE
            mouseShellView?.visibility = View.VISIBLE
            clickBarView?.visibility = View.VISIBLE
        }

        minimizedView?.let {
            if (it.windowToken != null) {
                windowManager?.removeView(it)
            }
        }
        minimizedView = null
    }

    /**
     * Minimize UI to a compact floating circle icon
     */
    fun minimizeToFloatingBubble() {
        if (isMinimized) return
        isMinimized = true

        mousePointerView?.visibility = View.GONE
        mouseShellView?.visibility = View.GONE
        clickBarView?.visibility = View.GONE

        createFloatingMinimizedBubble()
    }

    private fun createFloatingMinimizedBubble() {
        val bubbleSize = dpToPx(52)
        val bubbleView = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E293B"))
                setStroke(dpToPx(2), mouseColor)
            }
            elevation = dpToPx(10).toFloat()

            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_compass)
                setColorFilter(mouseColor)
            }
            addView(icon, FrameLayout.LayoutParams(dpToPx(28), dpToPx(28), Gravity.CENTER))
        }

        minimizedParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLATION_MOD_TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - bubbleSize - dpToPx(16)
            y = screenHeight / 3
        }

        var initialX = 0
        var initialY = 0
        var startTouchX = 0f
        var startTouchY = 0f

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = minimizedParams?.x ?: 0
                    initialY = minimizedParams?.y ?: 0
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    minimizedParams?.x = initialX + (event.rawX - startTouchX).toInt()
                    minimizedParams?.y = initialY + (event.rawY - startTouchY).toInt()
                    minimizedView?.let { v ->
                        if (v.windowToken != null) windowManager?.updateViewLayout(v, minimizedParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = Math.abs(event.rawX - startTouchX)
                    val diffY = Math.abs(event.rawY - startTouchY)
                    if (diffX < 10 && diffY < 10) {
                        restoreAndShowAllOverlays()
                    }
                    true
                }
                else -> false
            }
        }

        minimizedView = bubbleView
        windowManager?.addView(bubbleView, minimizedParams)
    }

    /**
     * 1. Mouse Pointer View (Floating Arrow)
     */
    private fun createMousePointerView() {
        val size = dpToPx(24)
        val pointerImageView = ImageView(this).apply {
            // Drawn precision cursor arrow
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                color = mouseColor
                style = Paint.Style.FILL
                isAntiAlias = true
                pathEffect = CornerPathEffect(4f)
            }
            val borderPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(2).toFloat()
                isAntiAlias = true
            }

            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size * 0.85f, size * 0.45f)
                lineTo(size * 0.45f, size * 0.55f)
                lineTo(size * 0.75f, size * 0.95f)
                lineTo(size * 0.55f, size * 1.0f)
                lineTo(size * 0.28f, size * 0.60f)
                lineTo(0f, size * 0.85f)
                close()
            }

            canvas.drawPath(path, paint)
            canvas.drawPath(path, borderPaint)

            setImageBitmap(bitmap)
        }

        pointerParams = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLATION_MOD_TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2
            y = screenHeight / 2
        }

        mousePointerView = pointerImageView
        windowManager?.addView(pointerImageView, pointerParams)
    }

    /**
     * 2. Mouse Shell (Touchpad Container)
     */
    private fun createMouseShellView() {
        val shellWidth = dpToPx(180)
        val shellHeight = dpToPx(220)

        val shellContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC0F172A")) // Dark Translucent Slate
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#334155"))
            }
            elevation = dpToPx(8).toFloat()
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(8))
        }

        // Top Header Bar for Dragging Shell
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(6))

            val title = TextView(context).apply {
                text = "تاچ‌پد ماوس"
                textColor = Color.WHITE
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val minBtn = TextView(context).apply {
                text = "➖"
                textSize = 12f
                setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
                setOnClickListener { minimizeToFloatingBubble() }
            }

            addView(title)
            addView(minBtn)
        }

        // Touchpad Area Surface
        val padSurface = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dpToPx(10).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#475569"))
            }
        }

        shellContainer.addView(headerBar)
        shellContainer.addView(padSurface)

        // Dragging Touchpad Shell across Screen
        headerBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    shellInitialX = mouseShellParams?.x ?: 0
                    shellInitialY = mouseShellParams?.y ?: 0
                    shellTouchStartX = event.rawX
                    shellTouchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    mouseShellParams?.x = shellInitialX + (event.rawX - shellTouchStartX).toInt()
                    mouseShellParams?.y = shellInitialY + (event.rawY - shellTouchStartY).toInt()
                    mouseShellView?.let { v ->
                        if (v.windowToken != null) windowManager?.updateViewLayout(v, mouseShellParams)
                    }
                    true
                }
                else -> false
            }
        }

        // Touchpad Motion Logic
        padSurface.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    updateScreenDimensions()
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.x - lastTouchX) * pointerSpeedMultiplier
                    val deltaY = (event.y - lastTouchY) * pointerSpeedMultiplier

                    pointerParams?.let { p ->
                        p.x = (p.x + deltaX.toInt()).coerceIn(0, screenWidth - dpToPx(15))
                        p.y = (p.y + deltaY.toInt()).coerceIn(0, screenHeight - dpToPx(15))

                        mousePointerView?.let { v ->
                            if (v.windowToken != null) windowManager?.updateViewLayout(v, p)
                        }
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        }

        mouseShellParams = WindowManager.LayoutParams(
            shellWidth,
            shellHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLATION_MOD_TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(20)
            y = screenHeight - shellHeight - dpToPx(100)
        }

        mouseShellView = shellContainer
        windowManager?.addView(shellContainer, mouseShellParams)
    }

    /**
     * 3. Click Bar & Shortcut Panel View
     */
    private fun createClickBarView() {
        val barContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DD0F172A"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#334155"))
            }
            elevation = dpToPx(8).toFloat()
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
        }

        // Header for ClickBar Drag
        val clickBarHeader = TextView(this).apply {
            text = "::: کلیدها"
            textColor = Color.parseColor("#94A3B8")
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(4))
        }

        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Action Buttons
        val btnClick = createActionButton("کلیک", "#0284C7") {
            pointerParams?.let { p -> injectTap(p.x.toFloat(), p.y.toFloat()) }
        }

        val btnLongPress = createActionButton("لمس طولانی", "#D97706") {
            pointerParams?.let { p -> injectLongClick(p.x.toFloat(), p.y.toFloat()) }
        }

        val btnDrag = createActionButton("انتقال آیکون", "#059669") {
            showDragDestinationSelectionToast()
        }

        val btnScrollUp = createActionButton("اسکرول ⬆️", "#475569") {
            injectScrollUp()
        }

        val btnScrollDown = createActionButton("اسکرول ⬇️", "#475569") {
            injectScrollDown()
        }

        buttonsLayout.addView(btnClick)
        buttonsLayout.addView(btnLongPress)
        buttonsLayout.addView(btnDrag)
        buttonsLayout.addView(btnScrollUp)
        buttonsLayout.addView(btnScrollDown)

        barContainer.addView(clickBarHeader)
        barContainer.addView(buttonsLayout)

        // Dragging ClickBar Control Panel
        clickBarHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    clickBarInitialX = clickBarParams?.x ?: 0
                    clickBarInitialY = clickBarParams?.y ?: 0
                    clickBarTouchStartX = event.rawX
                    clickBarTouchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    clickBarParams?.x = clickBarInitialX + (event.rawX - clickBarTouchStartX).toInt()
                    clickBarParams?.y = clickBarInitialY + (event.rawY - clickBarTouchStartY).toInt()
                    clickBarView?.let { v ->
                        if (v.windowToken != null) windowManager?.updateViewLayout(v, clickBarParams)
                    }
                    true
                }
                else -> false
            }
        }

        clickBarParams = WindowManager.LayoutParams(
            dpToPx(110),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLATION_MOD_TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - dpToPx(130)
            y = screenHeight - dpToPx(320)
        }

        clickBarView = barContainer
        windowManager?.addView(barContainer, clickBarParams)
    }

    private fun createActionButton(label: String, colorHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textColor = Color.WHITE
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))

            val marginParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(4)
            }
            layoutParams = marginParams

            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
                cornerRadius = dpToPx(8).toFloat()
            }

            setOnClickListener {
                if (isHapticFeedbackEnabled) performHapticFeedback()
                onClick()
            }
        }
    }

    private var isSelectingDragTarget = false
    private var dragStartX = 0f
    private var dragStartY = 0f

    private fun showDragDestinationSelectionToast() {
        pointerParams?.let { p ->
            dragStartX = p.x.toFloat()
            dragStartY = p.y.toFloat()
            isSelectingDragTarget = true

            Toast.makeText(
                this,
                "نقطه شروع ثبت شد! نشانگر را به مقصد برده و دوباره کلیک کنید 📍",
                Toast.LENGTH_LONG
            ).show()

            // Temporary update drag button behavior
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                if (isSelectingDragTarget) {
                    pointerParams?.let { pEnd ->
                        injectPrecisionDragAndDrop(dragStartX, dragStartY, pEnd.x.toFloat(), pEnd.y.toFloat())
                    }
                    isSelectingDragTarget = false
                }
            }, 3500)
        }
    }

    /**
     * Accessibility Gesture Injection Engine
     */
    fun injectTap(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())

        val path = Path().apply {
            moveTo(clampedX, clampedY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, null, null)
    }

    fun injectLongClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())

        val path = Path().apply {
            moveTo(clampedX, clampedY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 700)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, null, null)
    }

    fun injectTouchDrag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 400) {
        val clampedStartX = startX.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedStartY = startY.coerceIn(1f, (screenHeight - 1).toFloat())
        val clampedEndX = endX.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedEndY = endY.coerceIn(1f, (screenHeight - 1).toFloat())

        val path = Path().apply {
            moveTo(clampedStartX, clampedStartY)
            lineTo(clampedEndX, clampedEndY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * High-Precision Drag & Drop Gesture for Mixamo Rigging & Android Launcher Icons
     * Uses sequential 2-phase GestureDescription (Hold -> Move) to prevent multi-touch screen pan.
     */
    fun injectPrecisionDragAndDrop(startX: Float, startY: Float, endX: Float, endY: Float) {
        val clampedStartX = startX.coerceIn(5f, (screenWidth - 5).toFloat())
        val clampedStartY = startY.coerceIn(5f, (screenHeight - 5).toFloat())
        val clampedEndX = endX.coerceIn(5f, (screenHeight - 5).toFloat())
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

    fun injectScrollUp() {
        val startX = (screenWidth / 2).toFloat()
        val startY = (screenHeight * 0.3f).toFloat()
        val endY = (screenHeight * 0.7f).toFloat()
        injectTouchDrag(startX, startY, startX, endY, 300)
    }

    fun injectScrollDown() {
        val startX = (screenWidth / 2).toFloat()
        val startY = (screenHeight * 0.7f).toFloat()
        val endY = (screenHeight * 0.3f).toFloat()
        injectTouchDrag(startX, startY, startX, endY, 300)
    }

    private fun performHapticFeedback() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(20)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floating_mouse_channel",
                "سرویس ماوس شناور",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, "floating_mouse_channel")
            .setContentTitle("ماوس شناور فعال است")
            .setContentText("تاچ‌پد و کلیدهای کنترل آماده استفاده می‌باشند")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun removeAllViewsQuietly() {
        try {
            mousePointerView?.let { if (it.windowToken != null) windowManager?.removeView(it) }
            mouseShellView?.let { if (it.windowToken != null) windowManager?.removeView(it) }
            clickBarView?.let { if (it.windowToken != null) windowManager?.removeView(it) }
            minimizedView?.let { if (it.windowToken != null) windowManager?.removeView(it) }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
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
