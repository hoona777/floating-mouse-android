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

/**
 * Advanced Physical Floating Mouse Accessibility Service
 * Target File Path in GitHub: app/src/main/java/com/floatingmouse/app/FloatingMouseAccessibilityService.kt
 */
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
    private var isAttachedToTouchpad = false
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

        minimizedBubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var bInitialX = 0
            private var bInitialY = 0
            private var bTouchX = 0f
            private var bTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        bInitialX = bubbleParams?.x ?: 0
                        bInitialY = bubbleParams?.y ?: 0
                        bTouchX = event.rawX
                        bTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        bubbleParams?.let { bp ->
                            bp.x = (bInitialX + (event.rawX - bTouchX)).toInt().coerceIn(0, screenWidth - 110)
                            bp.y = (bInitialY + (event.rawY - bTouchY)).toInt().coerceIn(0, screenHeight - 110)
                            windowManager?.updateViewLayout(minimizedBubbleView, bp)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val distMoved = Math.hypot(
                            (event.rawX - bTouchX).toDouble(),
                            (event.rawY - bTouchY).toDouble()
                        )
                        if (distMoved < 15) {
                            restoreAndShowAllOverlays()
                        }
                        return true
                    }
                }
                return false
            }
        })

        mouseShellView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12131F"))
                cornerRadius = 24f
                setStroke(3, Color.parseColor("#00E5FF"))
            }
        }

        val dragShellListener = object : View.OnTouchListener {
            private var sInitialX = 0
            private var sInitialY = 0
            private var sTouchX = 0f
            private var sTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sInitialX = mouseShellParams?.x ?: 0
                        sInitialY = mouseShellParams?.y ?: 0
                        sTouchX = event.rawX
                        sTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        mouseShellParams?.let { msp ->
                            msp.x = (sInitialX + (event.rawX - sTouchX)).toInt().coerceIn(0, screenWidth - 270)
                            msp.y = (sInitialY + (event.rawY - sTouchY)).toInt().coerceIn(0, screenHeight - 210)
                            windowManager?.updateViewLayout(mouseShellView, msp)

                            if (isAttachedToTouchpad) {
                                clickBarParams?.let { cbp ->
                                    cbp.x = (msp.x - 300).coerceAtLeast(0)
                                    cbp.y = msp.y
                                    windowManager?.updateViewLayout(clickBarView, cbp)
                                }
                            }
                        }
                        return true
                    }
                }
                return false
            }
        }

        val touchpadHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(6, 4, 6, 4)
            setOnTouchListener(dragShellListener)
        }

        val touchpadTitle = TextView(this).apply {
            text = "::: 🖐️ تاچ‌پد شناور :::"
            textSize = 10f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        val minimizeTouchpadBtn = Button(this).apply {
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
                minimizeToBubble()
            }
        }

        touchpadHeader.addView(touchpadTitle)
        touchpadHeader.addView(minimizeTouchpadBtn)

        val touchpadContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            ).apply {
                setMargins(0, 4, 0, 0)
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

        touchpadView.setOnTouchListener(object : View.OnTouchListener {
            private var lastTouchX = 0f
            private var lastTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        val (hx, hy) = getPointerHotspot()
                        touchpadDragStartX = hx
                        touchpadDragStartY = hy
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.x - lastTouchX) * 1.8f
                        val dy = (event.y - lastTouchY) * 1.8f

                        pointerParams?.let { pp ->
                            pp.x = (pp.x + dx).toInt().coerceIn(0, screenWidth - 60)
                            pp.y = (pp.y + dy).toInt().coerceIn(0, screenHeight - 60)
                            windowManager?.updateViewLayout(mousePointerView, pp)
                        }

                        lastTouchX = event.x
                        lastTouchY = event.y
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val (curX, curY) = getPointerHotspot()
                        if (isDragModeActive) {
                            injectPrecisionDragAndDrop(touchpadDragStartX, touchpadDragStartY, curX, curY)
                        }
                        return true
                    }
                }
                return false
            }
        })

        touchpadContainer.addView(touchpadView)
        mouseShellView?.addView(touchpadHeader)
        mouseShellView?.addView(touchpadContainer)

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

        val clickBarHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 2, 4, 4)
            setOnTouchListener(dragClickBarListener)
        }

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
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#2A241B"))
                        cornerRadius = 10f
                        setStroke(2, Color.YELLOW)
                    }
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
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1B2A4A"))
                        cornerRadius = 10f
                        setStroke(2, Color.parseColor("#00E5FF"))
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "پنل کلیک جدا شد! 🔓", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val closeClickBarBtn = Button(this).apply {
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
                minimizeToBubble()
            }
        }

        clickBarHeader.addView(clickBarGripHandle)
        clickBarHeader.addView(dockToggleBtnRef)
        clickBarHeader.addView(closeClickBarBtn)

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
            layoutParams = LinearLayout.LayoutParams(0, 50, 1f).apply {
                setMargins(0, 0, 1, 0)
            }
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
            layoutParams = LinearLayout.LayoutParams(0, 50, 1f).apply {
                setMargins(1, 0, 1, 0)
            }
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
            layoutParams = LinearLayout.LayoutParams(0, 50, 1f).apply {
                setMargins(1, 0, 0, 0)
            }
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
            layoutParams = LinearLayout.LayoutParams(0, 48, 1f).apply {
                setMargins(0, 0, 2, 0)
            }
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
                    Toast.makeText(this@FloatingMouseAccessibilityService, "محل ثبت شد. نشانگر را به مقصد برده و 'رها 🎯' را بزنید.", Toast.LENGTH_SHORT).show()
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
            layoutParams = LinearLayout.LayoutParams(0, 48, 1f).apply {
                setMargins(2, 0, 0, 0)
            }
            setOnClickListener {
                isDragModeActive = !isDragModeActive
                if (isDragModeActive) {
                    text = "کشیدن [فعال]"
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00E676"))
                        cornerRadius = 8f
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "حالت کشیدن پیوسته فعال شد", Toast.LENGTH_SHORT).show()
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
            layoutParams = LinearLayout.LayoutParams(0, 42, 1f).apply {
                setMargins(0, 0, 1, 0)
            }
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
            layoutParams = LinearLayout.LayoutParams(0, 42, 1f).apply {
                setMargins(1, 0, 1, 0)
            }
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
            layoutParams = LinearLayout.LayoutParams(0, 42, 1f).apply {
                setMargins(1, 0, 0, 0)
            }
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
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 600)
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

        val dragPath = Path().apply {
            moveTo(clampedStartX, clampedStartY)
            lineTo(clampedStartX + 0.1f, clampedStartY + 0.1f)

            val steps = 25
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val curX = clampedStartX + (clampedEndX - clampedStartX) * progress
                val curY = clampedStartY + (clampedEndY - clampedStartY) * progress
                lineTo(curX, curY)
            }

            lineTo(clampedEndX + 0.1f, clampedEndY + 0.1f)
        }

        val stroke = GestureDescription.StrokeDescription(dragPath, 0, 900)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Toast.makeText(this@FloatingMouseAccessibilityService, "انتقال شیء/مفصل انجام شد ✅", Toast.LENGTH_SHORT).show()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Toast.makeText(this@FloatingMouseAccessibilityService, "انتقال لغو شد - دوباره تلاش کنید", Toast.LENGTH_SHORT).show()
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
