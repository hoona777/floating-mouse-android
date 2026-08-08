package com.floatingmouse.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
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

class FloatingMouseAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_SHOW_MOUSE = "com.floatingmouse.app.ACTION_SHOW_MOUSE"
    }

    private var windowManager: WindowManager? = null

    // Overlays
    private var mousePointerView: ImageView? = null
    private var mouseShellView: LinearLayout? = null
    private var clickBarView: LinearLayout? = null
    private var minimizedBubbleView: TextView? = null

    // Params
    private var pointerParams: WindowManager.LayoutParams? = null
    private var mouseShellParams: WindowManager.LayoutParams? = null
    private var clickBarParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // States
    private var isDragModeActive = false
    private var isGrabPointSaved = false
    private var isAttachedToTouchpad = false // false = Fully independent floating window
    private var grabStartX = 0f
    private var grabStartY = 0f

    private var isMinimized = false
    private var currentSkinIndex = 0
    private val skinNames = arrayOf("فلش فیزیکی کوچک", "دست/پوینتر", "گیمینگ RGB", "نئون glow", "نقطه لیزر")

    private var screenWidth = 1080
    private var screenHeight = 2400

    private var touchpadDragStartX = 0f
    private var touchpadDragStartY = 0f

    private var grabButtonRef: Button? = null
    private var dockToggleBtnRef: Button? = null

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

        setupFloatingComponents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_MOUSE) {
            restoreAndShowAllOverlays()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun restoreAndShowAllOverlays() {
        isMinimized = false
        try {
            minimizedBubbleView?.visibility = View.GONE

            if (mousePointerView == null || mouseShellView == null || clickBarView == null) {
                removeAllViewsQuietly()
                setupFloatingComponents()
            } else {
                mousePointerView?.visibility = View.VISIBLE
                mouseShellView?.visibility = View.VISIBLE
                clickBarView?.visibility = View.VISIBLE

                pointerParams?.let {
                    it.x = screenWidth / 2
                    it.y = screenHeight / 3
                    windowManager?.updateViewLayout(mousePointerView, it)
                }
                mouseShellParams?.let {
                    it.x = (screenWidth - 280).coerceAtLeast(20)
                    it.y = (screenHeight - 450).coerceAtLeast(100)
                    windowManager?.updateViewLayout(mouseShellView, it)
                }
                clickBarParams?.let {
                    it.x = 20
                    it.y = (screenHeight - 450).coerceAtLeast(100)
                    windowManager?.updateViewLayout(clickBarView, it)
                }
            }
            Toast.makeText(this, "موس شناور و پنل کلیک مستقل فعال شدند ✅", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupFloatingComponents() {
        // 1. Pointer Window (FLAG_NOT_TOUCHABLE passes events directly to underlying apps/canvas)
        pointerParams = WindowManager.LayoutParams(
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

        // 2. Touchpad Shell Window Params
        mouseShellParams = WindowManager.LayoutParams(
            270,
            210,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - 290).coerceAtLeast(20)
            y = (screenHeight - 450).coerceAtLeast(100)
        }

        // 3. Floating Click Bar Params (Independent Window)
        clickBarParams = WindowManager.LayoutParams(
            290,
            210,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = (screenHeight - 450).coerceAtLeast(100)
        }

        // 4. Minimized Bubble Params
        bubbleParams = WindowManager.LayoutParams(
            110,
            110,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - 140).coerceAtLeast(20)
            y = (screenHeight - 450).coerceAtLeast(100)
        }

        // --- Build Independent Floating Click Bar Window ---
        clickBarView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12131F"))
                cornerRadius = 24f
                setStroke(3, Color.parseColor("#FF4081"))
            }
        }

        val dragClickBarListener = object : View.OnTouchListener {
            private var cInitialX = 0
            private var cInitialY = 0
            private var cTouchX = 0f
            private var cTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        cInitialX = clickBarParams?.x ?: 0
                        cInitialY = clickBarParams?.y ?: 0
                        cTouchX = event.rawX
                        cTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        clickBarParams?.let { cbp ->
                            cbp.x = (cInitialX + (event.rawX - cTouchX)).toInt().coerceIn(0, screenWidth - 290)
                            cbp.y = (cInitialY + (event.rawY - cTouchY)).toInt().coerceIn(0, screenHeight - 210)
                            windowManager?.updateViewLayout(clickBarView, cbp)
                        }
                        return true
                    }
                }
                return false
            }
        }

        // Click Bar Header
        val clickBarHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 2, 4, 4)
            setOnTouchListener(dragClickBarListener)
        }

        // Visual Drag Handle
        val clickBarGripHandle = TextView(this).apply {
            text = "::: 🖐️ جابه‌جایی :::"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12, 6, 12, 6)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF4081"))
                cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 4, 0)
            }
            setOnTouchListener(dragClickBarListener)
        }

        dockToggleBtnRef = Button(this).apply {
            text = if (isAttachedToTouchpad) "📌 متصل" else "🔓 جدا"
            textSize = 8.5f
            setTextColor(if (isAttachedToTouchpad) Color.YELLOW else Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B2A4A"))
                cornerRadius = 10f
                setStroke(2, if (isAttachedToTouchpad) Color.YELLOW else Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(70, 38).apply {
                setMargins(0, 0, 4, 0)
            }
            setOnClickListener {
                isAttachedToTouchpad = !isAttachedToTouchpad
                if (isAttachedToTouchpad) {
                    text = "📌 متصل"
                    setTextColor(Color.YELLOW)
                    mouseShellParams?.let { msp ->
                        clickBarParams?.let { cbp ->
                            cbp.x = (msp.x - 300).coerceAtLeast(0)
                            cbp.y = msp.y
                            windowManager?.updateViewLayout(clickBarView, cbp)
                        }
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "پنل کلیک به تاچ‌پد متصل شد 📌", Toast.LENGTH_SHORT).show()
                } else {
                    text = "🔓 جدا"
                    setTextColor(Color.parseColor("#00E5FF"))
                    Toast.makeText(this@FloatingMouseAccessibilityService, "پنل کلیک جدا شد! می‌توانید آن را به هر جای صفحه بکشید 🔓", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Add views to WindowManager...
        try {
            windowManager?.addView(mousePointerView, pointerParams)
            windowManager?.addView(mouseShellView, mouseShellParams)
            windowManager?.addView(clickBarView, clickBarParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // High precision Point-to-Point Object Drag & Drop
    fun injectPrecisionDragAndDrop(startX: Float, startY: Float, endX: Float, endY: Float) {
        val dragPath = Path().apply {
            moveTo(startX, startY)
            lineTo(startX + 0.1f, startY + 0.1f) // Hold phase
            val steps = 25
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val curX = startX + (endX - startX) * progress
                val curY = startY + (endY - startY) * progress
                lineTo(curX, curY)
            }
            lineTo(endX + 0.1f, endY + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(dragPath, 0, 900)
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
