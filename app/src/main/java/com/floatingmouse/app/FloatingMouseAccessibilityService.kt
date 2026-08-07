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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

class FloatingMouseAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var mousePointerView: ImageView? = null
    private var capsuleView: LinearLayout? = null

    private var isDragModeActive = false
    private var currentSkinIndex = 0
    private val skinNames = arrayOf("فلش فیزیکی", "دست/پوینتر", "گیمینگ RGB", "نئون", "نقطه لیزر")

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
        this.serviceInfo = info

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingMouseAndCapsule()
    }

    private fun showFloatingMouseAndCapsule() {
        // تنظیمات نشانگر موس شناور
        val pointerParams = WindowManager.LayoutParams(
            120,
            120,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 300
            y = 500
        }

        mousePointerView = ImageView(this).apply {
            setImageBitmap(createPointerBitmap(currentSkinIndex))
        }

        // لمس و کشیدن نشانگر موس
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
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        val oldX = pointerParams.x
                        val oldY = pointerParams.y

                        pointerParams.x = initialX + deltaX
                        pointerParams.y = initialY + deltaY
                        windowManager?.updateViewLayout(mousePointerView, pointerParams)

                        // در صورت فعال بودن حالت کشیدن مفصل‌های میکسامو
                        if (isDragModeActive) {
                            injectTouchDrag(oldX.toFloat(), oldY.toFloat(), pointerParams.x.toFloat(), pointerParams.y.toFloat(), 100)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val distMoved = Math.hypot(
                            (event.rawX - initialTouchX).toDouble(),
                            (event.rawY - initialTouchY).toDouble()
                        )
                        if (distMoved < 15 && !isDragModeActive) {
                            injectTouchClick(pointerParams.x.toFloat(), pointerParams.y.toFloat())
                        }
                        return true
                    }
                }
                return false
            }
        })

        // تنظیمات کپسول کنترلی شناور
        val capsuleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        capsuleView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(28, 16, 28, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B1C2A"))
                cornerRadius = 50f
                setStroke(3, Color.parseColor("#00E5FF"))
            }
        }

        // امکان جابه‌جایی کپسول روی صفحه
        capsuleView?.setOnTouchListener(object : View.OnTouchListener {
            private var cInitialX = 0
            private var cInitialY = 0
            private var cTouchX = 0f
            private var cTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        cInitialX = capsuleParams.x
                        cInitialY = capsuleParams.y
                        cTouchX = event.rawX
                        cTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        capsuleParams.x = cInitialX + (event.rawX - cTouchX).toInt()
                        capsuleParams.y = cInitialY - (event.rawY - cTouchY).toInt()
                        windowManager?.updateViewLayout(capsuleView, capsuleParams)
                        return true
                    }
                }
                return false
            }
        })

        // دکمه کلیک چپ
        val leftClickBtn = Button(this).apply {
            text = "👈 کلیک چپ"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                mousePointerView?.let {
                    injectTouchClick(pointerParams.x.toFloat(), pointerParams.y.toFloat())
                }
            }
        }

        // دکمه کلیک راست
        val rightClickBtn = Button(this).apply {
            text = "👉 کلیک راست"
            textSize = 10f
            setTextColor(Color.LTGRAY)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                mousePointerView?.let {
                    injectRightClick(pointerParams.x.toFloat(), pointerParams.y.toFloat())
                }
            }
        }

        // دکمه کشیدن مفصل‌های میکسامو
        val dragToggleBtn = Button(this).apply {
            text = "✊ کشیدن مفصل"
            textSize = 10f
            setTextColor(Color.parseColor("#FFD700"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                isDragModeActive = !isDragModeActive
                if (isDragModeActive) {
                    setTextColor(Color.parseColor("#00FF66"))
                    text = "🟢 حالت کشیدن فعال"
                    Toast.makeText(this@FloatingMouseAccessibilityService, "حالت کشیدن مفصل‌های میکسامو فعال شد.", Toast.LENGTH_SHORT).show()
                } else {
                    setTextColor(Color.parseColor("#FFD700"))
                    text = "✊ کشیدن مفصل"
                    Toast.makeText(this@FloatingMouseAccessibilityService, "حالت کشیدن غیرفعال شد.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // دکمه تغییر پوسته
        val skinBtn = Button(this).apply {
            text = "🎨 پوسته"
            textSize = 10f
            setTextColor(Color.CYAN)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                currentSkinIndex = (currentSkinIndex + 1) % skinNames.size
                mousePointerView?.setImageBitmap(createPointerBitmap(currentSkinIndex))
                Toast.makeText(this@FloatingMouseAccessibilityService, "پوسته موس: ${skinNames[currentSkinIndex]}", Toast.LENGTH_SHORT).show()
            }
        }

        capsuleView?.addView(leftClickBtn)
        capsuleView?.addView(rightClickBtn)
        capsuleView?.addView(dragToggleBtn)
        capsuleView?.addView(skinBtn)

        try {
            windowManager?.addView(mousePointerView, pointerParams)
            windowManager?.addView(capsuleView, capsuleParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createPointerBitmap(skinIndex: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        when (skinIndex) {
            1 -> { // پوسته دست (Pointer Hand)
                paint.color = Color.parseColor("#FFCC80")
                canvas.drawCircle(50f, 40f, 20f, paint)
                canvas.drawRect(40f, 40f, 60f, 80f, paint)
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawCircle(50f, 40f, 20f, paint)
            }
            2 -> { // پوسته گیمینگ RGB
                paint.color = Color.parseColor("#9C27B0")
                val path = Path().apply {
                    moveTo(10f, 10f)
                    lineTo(90f, 50f)
                    lineTo(55f, 55f)
                    lineTo(50f, 90f)
                    close()
                }
                canvas.drawPath(path, paint)
                paint.color = Color.parseColor("#00E5FF")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                canvas.drawPath(path, paint)
            }
            3 -> { // پوسته نئون
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(50f, 50f, 35f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(50f, 50f, 12f, paint)
            }
            4 -> { // پوسته لیزر
                paint.color = Color.RED
                canvas.drawCircle(50f, 50f, 18f, paint)
                paint.color = Color.parseColor("#FFCDD2")
                canvas.drawCircle(50f, 50f, 8f, paint)
            }
            else -> { // فلش فیزیکی واقعی سفید با حاشیه مشکی
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(10f, 10f)
                    lineTo(45f, 95f)
                    lineTo(60f, 60f)
                    lineTo(95f, 45f)
                    close()
                }
                canvas.drawPath(path, paint)
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
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
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 600)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectTouchDrag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 100) {
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
        if (capsuleView != null) windowManager?.removeView(capsuleView)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
