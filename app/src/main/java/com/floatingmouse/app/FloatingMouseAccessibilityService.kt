package com.floatingmouse.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.*
import android.graphics.drawable.GradientDrawable
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
 * Physical Mini-Mouse Overlay with Interactive Touchpad & Gesture Dragging for Android
 * Supports Mixamo Rigging, Full System Control, Right Click, and Half-Sized Custom Skins
 */
class FloatingMouseAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var mousePointerView: ImageView? = null
    private var mouseShellView: LinearLayout? = null

    private var isDragModeActive = false
    private var currentSkinIndex = 0
    private val skinNames = arrayOf("فلش فیزیکی کوچک", "دست/پوینتر", "گیمینگ RGB", "نئون glow", "نقطه لیزر")

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
        this.serviceInfo = info

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showPhysicalMouseAndPointer()
    }

    private fun showPhysicalMouseAndPointer() {
        // 1. Pointer Layout Params - Half Sized Pointer (60x60 px)
        val pointerParams = WindowManager.LayoutParams(
            60,
            60,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 300
            y = 500
        }

        mousePointerView = ImageView(this).apply {
            setImageBitmap(createPointerBitmap(currentSkinIndex))
        }

        // Direct Touch Handling on Mouse Pointer Arrow
        mousePointerView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = pointerParams.x
                        initialY = pointerParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val oldX = pointerParams.x
                        val oldY = pointerParams.y

                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        pointerParams.x = initialX + deltaX
                        pointerParams.y = initialY + deltaY
                        windowManager?.updateViewLayout(mousePointerView, pointerParams)

                        // Mixamo Rigging Drag Gesture Dispatching
                        if (isDragModeActive) {
                            injectTouchDrag(oldX.toFloat(), oldY.toFloat(), pointerParams.x.toFloat(), pointerParams.y.toFloat(), 80)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val distMoved = Math.hypot(
                            (event.rawX - initialTouchX).toDouble(),
                            (event.rawY - initialTouchY).toDouble()
                        )
                        if (distMoved < 12 && !isDragModeActive) {
                            injectTouchClick(pointerParams.x.toFloat(), pointerParams.y.toFloat())
                        }
                        return true
                    }
                }
                return false
            }
        })

        // 2. Physical Mouse Overlay Widget (Super Compact & Ultra Small)
        val mouseShellParams = WindowManager.LayoutParams(
            230,
            180,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 20
            y = 100
        }

        // Mouse Container (Shape of a Physical Ergonomic Mouse)
        mouseShellView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6, 6, 6, 6)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12131F"))
                cornerRadius = 28f
                setStroke(3, Color.parseColor("#00E5FF"))
            }
        }

        // Drag Shell Listener to Move Mouse Widget on Phone Screen
        val dragMoveListener = object : View.OnTouchListener {
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
                        mouseShellParams.x = sInitialX - (event.rawX - sTouchX).toInt()
                        mouseShellParams.y = sInitialY - (event.rawY - sTouchY).toInt()
                        windowManager?.updateViewLayout(mouseShellView, mouseShellParams)
                        return true
                    }
                }
                return false
            }
        }

        // Mouse Top Buttons Layout: [Left Click] [Center Column: Skin + Drag Lock] [Right Click]
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 5f
            setPadding(0, 0, 0, 2)
        }

        val leftClickBtn = Button(this).apply {
            textSize = 8.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B2A4A"))
                cornerRadius = 12f
                setStroke(2, Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f).apply {
                setMargins(0, 0, 1, 0)
            }
            setOnClickListener {
                mousePointerView?.let {
                    injectTouchClick(pointerParams.x.toFloat(), pointerParams.y.toFloat())
                }
            }
        }

        // Center Column with Skin Switcher on top & Drag Lock on bottom
        val centerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(1, 0, 1, 0)
            }
            setOnTouchListener(dragMoveListener)
        }

        // Skin Switcher Button
        val skinBtn = Button(this).apply {
            text = "P"
            textSize = 8f
            setTextColor(Color.CYAN)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10222A"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 32)
            setOnClickListener {
                currentSkinIndex = (currentSkinIndex + 1) % skinNames.size
                val newBitmap = createPointerBitmap(currentSkinIndex)
                mousePointerView?.setImageBitmap(newBitmap)
                mousePointerView?.postInvalidate()
                Toast.makeText(this@FloatingMouseAccessibilityService, "پوسته: ${skinNames[currentSkinIndex]}", Toast.LENGTH_SHORT).show()
            }
        }

        // Tiny Drag Lock Button (Center Bottom)
        val dragToggleBtn = Button(this).apply {
            text = "L"
            textSize = 8f
            setPadding(0, 0, 0, 0)
            setTextColor(Color.parseColor("#FFD700"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222510"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#FFD700"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 32).apply {
                setMargins(0, 2, 0, 0)
            }
            setOnClickListener {
                isDragModeActive = !isDragModeActive
                if (isDragModeActive) {
                    text = "ON"
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00E676"))
                        cornerRadius = 8f
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "قفل کشیدن فعال شد.", Toast.LENGTH_SHORT).show()
                } else {
                    text = "L"
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#222510"))
                        cornerRadius = 8f
                        setStroke(1, Color.parseColor("#FFD700"))
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "قفل کشیدن غیرفعال شد.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        centerCol.addView(skinBtn)
        centerCol.addView(dragToggleBtn)

        val rightClickBtn = Button(this).apply {
            textSize = 8.5f
            setTextColor(Color.LTGRAY)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A1B2A"))
                cornerRadius = 12f
                setStroke(2, Color.parseColor("#FF4081"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f).apply {
                setMargins(1, 0, 0, 0)
            }
            setOnClickListener {
                mousePointerView?.let {
                    injectRightClick(pointerParams.x.toFloat(), pointerParams.y.toFloat())
                }
            }
        }

        buttonsRow.addView(leftClickBtn)
        buttonsRow.addView(centerCol)
        buttonsRow.addView(rightClickBtn)

        // FrameLayout container for Touchpad + Corner Scroll Arrows
        val touchpadContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                130
            ).apply {
                setMargins(0, 2, 0, 0)
            }
        }

        // Physical Touchpad Surface
        val touchpadView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#080911"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#1E293B"))
            }
        }

        // Touchpad Gesture Controller for Mouse Pointer Movement
        touchpadView.setOnTouchListener(object : View.OnTouchListener {
            private var lastTouchX = 0f
            private var lastTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.x - lastTouchX) * 1.5f
                        val dy = (event.y - lastTouchY) * 1.5f

                        val oldX = pointerParams.x
                        val oldY = pointerParams.y

                        pointerParams.x = (pointerParams.x + dx).toInt().coerceIn(0, 1400)
                        pointerParams.y = (pointerParams.y + dy).toInt().coerceIn(0, 2800)

                        windowManager?.updateViewLayout(mousePointerView, pointerParams)

                        // Dispatch Continuous Drag if Drag Mode is ON
                        if (isDragModeActive) {
                            injectTouchDrag(oldX.toFloat(), oldY.toFloat(), pointerParams.x.toFloat(), pointerParams.y.toFloat(), 60)
                        }

                        lastTouchX = event.x
                        lastTouchY = event.y
                        return true
                    }
                }
                return false
            }
        })

        // Bottom-Left Corner Tiny Scroll Up Button
        val scrollUpBtn = Button(this).apply {
            text = "▲"
            textSize = 8f
            setTextColor(Color.CYAN)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 8f
            }
            layoutParams = FrameLayout.LayoutParams(40, 40).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(8, 0, 0, 8)
            }
            setOnClickListener {
                injectTouchDrag(pointerParams.x.toFloat(), pointerParams.y.toFloat() + 200f, pointerParams.x.toFloat(), pointerParams.y.toFloat() - 200f, 100)
            }
        }

        // Bottom-Right Corner Tiny Scroll Down Button
        val scrollDownBtn = Button(this).apply {
            text = "▼"
            textSize = 8f
            setTextColor(Color.CYAN)
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 8f
            }
            layoutParams = FrameLayout.LayoutParams(40, 40).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 8, 8)
            }
            setOnClickListener {
                injectTouchDrag(pointerParams.x.toFloat(), pointerParams.y.toFloat() - 200f, pointerParams.x.toFloat(), pointerParams.y.toFloat() + 200f, 100)
            }
        }

        touchpadContainer.addView(touchpadView)
        touchpadContainer.addView(scrollUpBtn)
        touchpadContainer.addView(scrollDownBtn)

        // Assemble Physical Mouse Layout
        mouseShellView?.addView(buttonsRow)
        mouseShellView?.addView(touchpadContainer)

        try {
            windowManager?.addView(mousePointerView, pointerParams)
            windowManager?.addView(mouseShellView, mouseShellParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Create Half-Sized Pointer Bitmaps (60x60 px)
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
            else -> { // Physical White Mouse Arrow Half-Sized
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
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectRightClick(x: Float, y: Float) {
        val clickPath = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 500)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectTouchDrag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 80) {
        val dragPath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(dragPath, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mousePointerView != null) windowManager?.removeView(mousePointerView)
        if (mouseShellView != null) windowManager?.removeView(mouseShellView)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (mousePointerView == null || mouseShellView == null) {
            showPhysicalMouseAndPointer()
        }
    }

    override fun onInterrupt() {}
}
