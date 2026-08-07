package com.floatingmouse.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Physical Floating Mouse Accessibility Service for Android
 * Featuring:
 * - Touchpad Pointer Navigation with Exact Arrow Hotspot Offset
 * - Direct Touch-Through Pointer Overlay (FLAG_NOT_TOUCHABLE) for 100% Reliable Clicks in Websites/Apps
 * - Smooth Page Scroll Up & Scroll Down Gestures
 * - Minimize / Maximize Floating Bubble
 * - Reliable Drag & Drop Lock Mode
 * - Easy Draggable Floating Mouse Shell
 */
class FloatingMouseAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var mousePointerView: ImageView? = null
    private var mouseShellView: LinearLayout? = null
    private var minimizedBubbleView: TextView? = null

    private var isDragModeActive = false
    private var isMinimized = false
    private var currentSkinIndex = 0
    private val skinNames = arrayOf("فلش فیزیکی کوچک", "دست/پوینتر", "گیمینگ RGB", "نئون glow", "نقطه لیزر")

    private var screenWidth = 1080
    private var screenHeight = 2400

    private var dragStartX = 0f
    private var dragStartY = 0f

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

        setupFloatingMouseAndPointer()
    }

    private fun setupFloatingMouseAndPointer() {
        // 1. Pointer Window Params - Pass Through Touch Events (FLAG_NOT_TOUCHABLE)
        val pointerParams = WindowManager.LayoutParams(
            60,
            60,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2
            y = screenHeight / 3
        }

        mousePointerView = ImageView(this).apply {
            setImageBitmap(createPointerBitmap(currentSkinIndex))
        }

        // 2. Physical Mouse Overlay Widget
        val mouseShellParams = WindowManager.LayoutParams(
            260,
            240,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - 280).coerceAtLeast(20)
            y = (screenHeight - 500).coerceAtLeast(100)
        }

        // 3. Minimized Floating Bubble Params
        val bubbleParams = WindowManager.LayoutParams(
            110,
            110,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - 140).coerceAtLeast(20)
            y = (screenHeight - 500).coerceAtLeast(100)
        }

        // --- Create Minimized Bubble ---
        minimizedBubbleView = TextView(this).apply {
            text = "🖱️"
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12131F"))
                cornerRadius = 55f
                setStroke(4, Color.parseColor("#00E5FF"))
            }
            visibility = View.GONE
        }

        // Touch Listener for Minimized Bubble (Drag or Tap to Restore)
        minimizedBubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var bInitialX = 0
            private var bInitialY = 0
            private var bTouchX = 0f
            private var bTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        bInitialX = bubbleParams.x
                        bInitialY = bubbleParams.y
                        bTouchX = event.rawX
                        bTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        bubbleParams.x = (bInitialX + (event.rawX - bTouchX)).toInt().coerceIn(0, screenWidth - 110)
                        bubbleParams.y = (bInitialY + (event.rawY - bTouchY)).toInt().coerceIn(0, screenHeight - 110)
                        windowManager?.updateViewLayout(minimizedBubbleView, bubbleParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val distMoved = Math.hypot(
                            (event.rawX - bTouchX).toDouble(),
                            (event.rawY - bTouchY).toDouble()
                        )
                        if (distMoved < 15) {
                            // Restore Mouse Shell
                            isMinimized = false
                            minimizedBubbleView?.visibility = View.GONE
                            mouseShellParams.x = bubbleParams.x
                            mouseShellParams.y = bubbleParams.y
                            mouseShellView?.visibility = View.VISIBLE
                            windowManager?.updateViewLayout(mouseShellView, mouseShellParams)
                        }
                        return true
                    }
                }
                return false
            }
        })

        // --- Create Mouse Shell Container ---
        mouseShellView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12131F"))
                cornerRadius = 24f
                setStroke(3, Color.parseColor("#00E5FF"))
            }
        }

        // Drag Move Listener for Full Shell Window
        val dragShellListener = object : View.OnTouchListener {
            private var sInitialX = 0
            private var sInitialY = 0
            private var sTouchX = 0f
            private var sTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sInitialX = mouseShellParams.x
                        sInitialY = mouseShellParams.y
                        sTouchX = event.rawX
                        sTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        mouseShellParams.x = (sInitialX + (event.rawX - sTouchX)).toInt().coerceIn(0, screenWidth - 260)
                        mouseShellParams.y = (sInitialY + (event.rawY - sTouchY)).toInt().coerceIn(0, screenHeight - 240)
                        windowManager?.updateViewLayout(mouseShellView, mouseShellParams)
                        return true
                    }
                }
                return false
            }
        }

        // 1. Header Bar: Drag Handle + Minimize Button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 2, 4, 4)
            setOnTouchListener(dragShellListener)
        }

        val dragTitle = TextView(this).apply {
            text = "::: موس شناور :::"
            textSize = 10f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        val minimizeBtn = Button(this).apply {
            text = "—"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = 8f
            }
            layoutParams = LinearLayout.LayoutParams(36, 36)
            setOnClickListener {
                isMinimized = true
                mouseShellView?.visibility = View.GONE
                bubbleParams.x = mouseShellParams.x
                bubbleParams.y = mouseShellParams.y
                minimizedBubbleView?.visibility = View.VISIBLE
                windowManager?.updateViewLayout(minimizedBubbleView, bubbleParams)
            }
        }

        headerRow.addView(dragTitle)
        headerRow.addView(minimizeBtn)

        // 2. Buttons Row: [کلیک چپ] [پوسته / قفل کشیدن] [کلیک راست]
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 5f
            setPadding(0, 2, 0, 4)
        }

        val leftClickBtn = Button(this).apply {
            text = "چپ"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B2A4A"))
                cornerRadius = 10f
                setStroke(2, Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, 54, 2f).apply {
                setMargins(0, 0, 2, 0)
            }
            setOnClickListener {
                val targetX = pointerParams.x + 12f
                val targetY = pointerParams.y + 12f
                injectTouchClick(targetX, targetY)
            }
        }

        val centerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(1, 0, 1, 0)
            }
        }

        val skinBtn = Button(this).apply {
            text = "پوسته"
            textSize = 7.5f
            setTextColor(Color.CYAN)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10222A"))
                cornerRadius = 6f
                setStroke(1, Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 26)
            setOnClickListener {
                currentSkinIndex = (currentSkinIndex + 1) % skinNames.size
                val newBitmap = createPointerBitmap(currentSkinIndex)
                mousePointerView?.setImageBitmap(newBitmap)
                Toast.makeText(this@FloatingMouseAccessibilityService, "پوسته: ${skinNames[currentSkinIndex]}", Toast.LENGTH_SHORT).show()
            }
        }

        val dragToggleBtn = Button(this).apply {
            text = "کشیدن"
            textSize = 7.5f
            setPadding(0, 0, 0, 0)
            setTextColor(Color.parseColor("#FFD700"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222510"))
                cornerRadius = 6f
                setStroke(1, Color.parseColor("#FFD700"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 26).apply {
                setMargins(0, 2, 0, 0)
            }
            setOnClickListener {
                isDragModeActive = !isDragModeActive
                if (isDragModeActive) {
                    text = "فعال"
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00E676"))
                        cornerRadius = 6f
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "حالت کشیدن فعال شد. تاچ‌پد را بکشید.", Toast.LENGTH_SHORT).show()
                } else {
                    text = "کشیدن"
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#222510"))
                        cornerRadius = 6f
                        setStroke(1, Color.parseColor("#FFD700"))
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "حالت کشیدن غیرفعال شد.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        centerCol.addView(skinBtn)
        centerCol.addView(dragToggleBtn)

        val rightClickBtn = Button(this).apply {
            text = "راست"
            textSize = 9f
            setTextColor(Color.LTGRAY)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A1B2A"))
                cornerRadius = 10f
                setStroke(2, Color.parseColor("#FF4081"))
            }
            layoutParams = LinearLayout.LayoutParams(0, 54, 2f).apply {
                setMargins(2, 0, 0, 0)
            }
            setOnClickListener {
                val targetX = pointerParams.x + 12f
                val targetY = pointerParams.y + 12f
                injectRightClick(targetX, targetY)
            }
        }

        buttonsRow.addView(leftClickBtn)
        buttonsRow.addView(centerCol)
        buttonsRow.addView(rightClickBtn)

        // 3. Touchpad Area with Scroll Up/Down Buttons
        val touchpadContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            ).apply {
                setMargins(0, 2, 0, 0)
            }
        }

        val touchpadView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#080911"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#1E293B"))
            }
        }

        // Touchpad Controller for Pointer Navigation & Drag Lock Handling
        touchpadView.setOnTouchListener(object : View.OnTouchListener {
            private var lastTouchX = 0f
            private var lastTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        dragStartX = pointerParams.x + 12f
                        dragStartY = pointerParams.y + 12f
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.x - lastTouchX) * 1.8f
                        val dy = (event.y - lastTouchY) * 1.8f

                        pointerParams.x = (pointerParams.x + dx).toInt().coerceIn(0, screenWidth - 60)
                        pointerParams.y = (pointerParams.y + dy).toInt().coerceIn(0, screenHeight - 60)

                        windowManager?.updateViewLayout(mousePointerView, pointerParams)

                        lastTouchX = event.x
                        lastTouchY = event.y
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val currentX = pointerParams.x + 12f
                        val currentY = pointerParams.y + 12f
                        if (isDragModeActive) {
                            // Perform Drag Swipe Gesture from Drag Start to Current Position
                            injectTouchDrag(dragStartX, dragStartY, currentX, currentY, 250)
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Scroll Up Button
        val scrollUpBtn = Button(this).apply {
            text = "▲"
            textSize = 9f
            setTextColor(Color.CYAN)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 8f
            }
            layoutParams = FrameLayout.LayoutParams(38, 38).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(6, 0, 0, 6)
            }
            setOnClickListener {
                val px = pointerParams.x + 12f
                val py = pointerParams.y + 12f
                injectTouchDrag(px, py + 250f, px, py - 250f, 300)
            }
        }

        // Scroll Down Button
        val scrollDownBtn = Button(this).apply {
            text = "▼"
            textSize = 9f
            setTextColor(Color.CYAN)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 8f
            }
            layoutParams = FrameLayout.LayoutParams(38, 38).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 6, 6)
            }
            setOnClickListener {
                val px = pointerParams.x + 12f
                val py = pointerParams.y + 12f
                injectTouchDrag(px, py - 250f, px, py + 250f, 300)
            }
        }

        touchpadContainer.addView(touchpadView)
        touchpadContainer.addView(scrollUpBtn)
        touchpadContainer.addView(scrollDownBtn)

        // Assemble Physical Mouse Layout
        mouseShellView?.addView(headerRow)
        mouseShellView?.addView(buttonsRow)
        mouseShellView?.addView(touchpadContainer)

        try {
            windowManager?.addView(mousePointerView, pointerParams)
            windowManager?.addView(minimizedBubbleView, bubbleParams)
            windowManager?.addView(mouseShellView, mouseShellParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Create Pointer Bitmaps (60x60 px)
    private fun createPointerBitmap(skinIndex: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        when (skinIndex) {
            1 -> { // Hand Pointer
                paint.color = Color.parseColor("#FFCC80")
                canvas.drawCircle(28f, 22f, 11f, paint)
                canvas.drawRect(22f, 22f, 34f, 44f, paint)
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(28f, 22f, 11f, paint)
            }
            2 -> { // Gamer RGB Arrow
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
            3 -> { // Neon Glow Ring
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(28f, 28f, 20f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(28f, 28f, 7f, paint)
            }
            4 -> { // Laser Red Dot
                paint.color = Color.RED
                canvas.drawCircle(28f, 28f, 12f, paint)
                paint.color = Color.parseColor("#FFCDD2")
                canvas.drawCircle(28f, 28f, 5f, paint)
            }
            else -> { // Physical White Mouse Arrow
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

    fun injectRightClick(x: Float, y: Float) {
        val clickPath = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 450)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectTouchDrag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 250) {
        val clampedStartY = startY.coerceIn(100f, (screenHeight - 100).toFloat())
        val clampedEndY = endY.coerceIn(100f, (screenHeight - 100).toFloat())
        val clampedStartX = startX.coerceIn(50f, (screenWidth - 50).toFloat())
        val clampedEndX = endX.coerceIn(50f, (screenWidth - 50).toFloat())

        val dragPath = Path().apply {
            moveTo(clampedStartX, clampedStartY)
            lineTo(clampedEndX, clampedEndY)
        }
        val stroke = GestureDescription.StrokeDescription(dragPath, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (mousePointerView != null) windowManager?.removeView(mousePointerView)
            if (mouseShellView != null) windowManager?.removeView(mouseShellView)
            if (minimizedBubbleView != null) windowManager?.removeView(minimizedBubbleView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Do not re-create views inside event listener to prevent service crashes
    }

    override fun onInterrupt() {}
}
