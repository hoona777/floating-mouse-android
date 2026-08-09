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
            private set
    }

    private var windowManager: WindowManager? = null

    private var mousePointerView: ImageView? = null
    private var mouseShellView: LinearLayout? = null
    private var clickBarView: LinearLayout? = null
    private var minimizedBubbleView: TextView? = null

    private var pointerParams: WindowManager.LayoutParams? = null
    private var mouseShellParams: WindowManager.LayoutParams? = null
    private var clickBarParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

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

        setupFloatingComponents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_MOUSE) {
            restoreAndShowAllOverlays()
        }
        return START_STICKY
    }

    private fun updateScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager?.currentWindowMetrics
            windowMetrics?.bounds?.let { bounds ->
                screenWidth = bounds.width()
                screenHeight = bounds.height()
            }
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "سرویس موس شناور",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "جهت فعال نگه‌داشتن موس شناور روی صفحه"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        try {
            val intent = Intent(this, FloatingMouseAccessibilityService::class.java).apply {
                action = ACTION_SHOW_MOUSE
            }
            val pendingIntent = PendingIntent.getService(
                this, 0, intent,
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

    fun restoreAndShowAllOverlays() {
        isMinimized = false
        updateScreenDimensions()
        if (mousePointerView == null || mouseShellView == null || clickBarView == null ||
            mousePointerView?.windowToken == null) {
            setupFloatingComponents()
            return
        }

        minimizedBubbleView?.visibility = View.GONE
        mousePointerView?.visibility = View.VISIBLE
        mouseShellView?.visibility = View.VISIBLE
        clickBarView?.visibility = View.VISIBLE
    }

    private fun minimizeAllOverlays() {
        isMinimized = true
        mousePointerView?.visibility = View.GONE
        mouseShellView?.visibility = View.GONE
        clickBarView?.visibility = View.GONE
        minimizedBubbleView?.visibility = View.VISIBLE
    }

    private fun setupFloatingComponents() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "لطفاً مجوز Display over other apps را صادر کنید", Toast.LENGTH_LONG).show()
            return
        }

        try {
            removeAllOverlays()

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // 1. Mouse Pointer Overlay Window
            pointerParams = WindowManager.LayoutParams(
                dpToPx(38), dpToPx(38),
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = screenWidth / 2
                y = screenHeight / 3
            }

            mousePointerView = ImageView(this).apply {
                setImageBitmap(drawCursorBitmap(currentSkinIndex))
            }
            windowManager?.addView(mousePointerView, pointerParams)

            // 2. Touchpad Shell Window
            val shellWidth = dpToPx(190)
            val shellHeight = dpToPx(130)
            mouseShellParams = WindowManager.LayoutParams(
                shellWidth, shellHeight,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (screenWidth - shellWidth) / 2
                y = screenHeight - shellHeight - dpToPx(120)
            }

            mouseShellView = buildTouchpadShellView()
            windowManager?.addView(mouseShellView, mouseShellParams)

            // 3. Click Bar Panel Window
            val barWidth = dpToPx(280)
            val barHeight = dpToPx(52)
            clickBarParams = WindowManager.LayoutParams(
                barWidth, barHeight,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (screenWidth - barWidth) / 2
                y = screenHeight - barHeight - dpToPx(50)
            }

            clickBarView = buildClickBarView()
            windowManager?.addView(clickBarView, clickBarParams)

            // 4. Minimized Bubble Window
            bubbleParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dpToPx(16)
                y = screenHeight / 2
            }

            minimizedBubbleView = TextView(this).apply {
                text = "🖱️ موس"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#CC111827"))
                    setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
                    cornerRadius = dpToPx(20).toFloat()
                }
                visibility = View.GONE

                var initX = 0
                var initY = 0
                var touchX = 0f
                var touchY = 0f

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initX = bubbleParams?.x ?: 0
                            initY = bubbleParams?.y ?: 0
                            touchX = event.rawX
                            touchY = event.rawY
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - touchX).toInt()
                            val dy = (event.rawY - touchY).toInt()
                            bubbleParams?.x = initX + dx
                            bubbleParams?.y = initY + dy
                            windowManager?.updateViewLayout(this, bubbleParams)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            val dx = Math.abs(event.rawX - touchX)
                            val dy = Math.abs(event.rawY - touchY)
                            if (dx < 10 && dy < 10) {
                                restoreAndShowAllOverlays()
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
            windowManager?.addView(minimizedBubbleView, bubbleParams)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildTouchpadShellView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A"))
                setStroke(dpToPx(2), Color.parseColor("#38BDF8"))
                cornerRadius = dpToPx(16).toFloat()
            }
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))

            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                val title = TextView(context).apply {
                    text = "تاچ‌پد"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val minBtn = Button(context).apply {
                    text = "⚊"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    background = null
                    setOnClickListener { minimizeAllOverlays() }
                }

                addView(title)
                addView(minBtn)
            }

            val padArea = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(10).toFloat()
                }

                var lastX = 0f
                var lastY = 0f

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = event.rawX
                            lastY = event.rawY
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - lastX
                            val dy = event.rawY - lastY
                            lastX = event.rawX
                            lastY = event.rawY

                            pointerParams?.let { p ->
                                p.x = (p.x + dx.toInt()).coerceIn(0, screenWidth - dpToPx(20))
                                p.y = (p.y + dy.toInt()).coerceIn(0, screenHeight - dpToPx(20))
                                mousePointerView?.let { v -> windowManager?.updateViewLayout(v, p) }

                                if (isDragModeActive && isGrabPointSaved) {
                                    performDragMoveGesture(p.x.toFloat(), p.y.toFloat())
                                }
                            }
                            true
                        }
                        else -> true
                    }
                }
            }

            addView(header)
            addView(padArea)
        }
    }

    private fun buildClickBarView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F10F172A"))
                setStroke(dpToPx(1), Color.parseColor("#475569"))
                cornerRadius = dpToPx(14).toFloat()
            }
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))

            val btnClick = createActionButton("کلیک") {
                performClickAtPointer()
            }

            grabButtonRef = createActionButton("درگ 🔓") {
                toggleDragMode()
            }

            touchHoldBtnRef = createActionButton("نگه داشتن 👆") {
                toggleTouchHold()
            }

            val btnSkin = createActionButton("پوسته 🎨") {
                currentSkinIndex = (currentSkinIndex + 1) % skinNames.size
                mousePointerView?.setImageBitmap(drawCursorBitmap(currentSkinIndex))
                Toast.makeText(context, "پوسته: ${skinNames[currentSkinIndex]}", Toast.SHORT).show()
            }

            addView(btnClick)
            addView(grabButtonRef)
            addView(touchHoldBtnRef)
            addView(btnSkin)
        }
    }

    private fun createActionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 10f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(2), 0, dpToPx(2), 0)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = dpToPx(8).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun performClickAtPointer() {
        val px = (pointerParams?.x ?: 0) + dpToPx(10)
        val py = (pointerParams?.y ?: 0) + dpToPx(10)

        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(px.toFloat(), py.toFloat()) },
            0, 50
        )
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun toggleDragMode() {
        isDragModeActive = !isDragModeActive
        if (isDragModeActive) {
            val px = (pointerParams?.x ?: 0) + dpToPx(10)
            val py = (pointerParams?.y ?: 0) + dpToPx(10)
            grabStartX = px.toFloat()
            grabStartY = py.toFloat()
            isGrabPointSaved = true

            grabButtonRef?.text = "درگ 🔒"
            grabButtonRef?.background = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444"))
                cornerRadius = dpToPx(8).toFloat()
            }
            Toast.makeText(this, "حالت درگ فعال شد", Toast.SHORT).show()
        } else {
            if (isGrabPointSaved) {
                val endX = ((pointerParams?.x ?: 0) + dpToPx(10)).toFloat()
                val endY = ((pointerParams?.y ?: 0) + dpToPx(10)).toFloat()
                performFinalDragRelease(grabStartX, grabStartY, endX, endY)
            }
            isGrabPointSaved = false
            grabButtonRef?.text = "درگ 🔓"
            grabButtonRef?.background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = dpToPx(8).toFloat()
            }
            Toast.makeText(this, "درگ رها شد", Toast.SHORT).show()
        }
    }

    private fun performDragMoveGesture(currentX: Float, currentY: Float) {
        val path = Path().apply {
            moveTo(grabStartX, grabStartY)
            lineTo(currentX, currentY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun performFinalDragRelease(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 250)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun toggleTouchHold() {
        isTouchHoldActive = !isTouchHoldActive
        if (isTouchHoldActive) {
            val px = ((pointerParams?.x ?: 0) + dpToPx(10)).toFloat()
            val py = ((pointerParams?.y ?: 0) + dpToPx(10)).toFloat()

            val path = Path().apply { moveTo(px, py) }
            activeHoldStroke = GestureDescription.StrokeDescription(path, 0, 60000, true)
            dispatchGesture(GestureDescription.Builder().addStroke(activeHoldStroke!!).build(), null, null)

            touchHoldBtnRef?.text = "رهاسازی ✋"
            touchHoldBtnRef?.background = GradientDrawable().apply {
                setColor(Color.parseColor("#F59E0B"))
                cornerRadius = dpToPx(8).toFloat()
            }
        } else {
            touchHoldBtnRef?.text = "نگه داشتن 👆"
            touchHoldBtnRef?.background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = dpToPx(8).toFloat()
            }
            activeHoldStroke = null
        }
    }

    private fun drawCursorBitmap(skinIndex: Int): Bitmap {
        val size = dpToPx(38)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply { isAntiAlias = true }

        when (skinIndex) {
            0 -> {
                val path = Path().apply {
                    moveTo(4f, 4f)
                    lineTo(28f, 18f)
                    lineTo(18f, 20f)
                    lineTo(24f, 32f)
                    lineTo(18f, 34f)
                    lineTo(12f, 22f)
                    lineTo(4f, 28f)
                    close()
                }
                paint.color = Color.BLACK
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)

                val innerPath = Path().apply {
                    moveTo(7f, 8f)
                    lineTo(24f, 18f)
                    lineTo(16f, 20f)
                    lineTo(11f, 25f)
                    close()
                }
                paint.color = Color.WHITE
                canvas.drawPath(innerPath, paint)
            }
            1 -> {
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dpToPx(2).toFloat()
                canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
            }
            2 -> {
                paint.color = Color.parseColor("#38BDF8")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
            }
            3 -> {
                paint.color = Color.parseColor("#F43F5E")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)
            }
            else -> {
                paint.color = Color.RED
                canvas.drawCircle(size / 2f, size / 2f, 10f, paint)
            }
        }

        return bitmap
    }

    private fun removeAllOverlays() {
        try {
            mousePointerView?.let { windowManager?.removeView(it) }
            mouseShellView?.let { windowManager?.removeView(it) }
            clickBarView?.let { windowManager?.removeView(it) }
            minimizedBubbleView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mousePointerView = null
        mouseShellView = null
        clickBarView = null
        minimizedBubbleView = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        removeAllOverlays()
    }
}
