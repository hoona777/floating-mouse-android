package com.floatingmouse.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FloatingMouseAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null

    // Overlays
    private var mousePointerView: ImageView? = null
    private var mouseShellView: FrameLayout? = null
    private var touchPadView: View? = null
    private var clickBarView: LinearLayout? = null
    private var minimizedBubbleView: TextView? = null

    // Layout Params
    private var pointerParams: WindowManager.LayoutParams? = null
    private var mouseShellParams: WindowManager.LayoutParams? = null
    private var clickBarParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var exactPointerX = 0f
    private var exactPointerY = 0f
    private var lastDragDispatchTime = 0L

    // Drag & Drop States
    private var isDragModeActive = false
    private var isGrabPointSaved = false
    private var grabStartX = 0f
    private var grabStartY = 0f

    // Touch Hold Lock State
    private var isTouchHoldActive = false
    private var activeHoldStroke: GestureDescription.StrokeDescription? = null

    // UI Buttons References
    private var grabButtonRef: Button? = null
    private var touchHoldBtnRef: Button? = null

    // Screen Dimensions & Positions
    private var screenWidth = 1080
    private var screenHeight = 2400

    // TouchPad Relative Movement Tracks
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Skins Engine
    private var currentSkinIndex = 0
    private val skinNames = arrayOf("کلاسیک", "دست لمسی", "لیزری", "دایره هدف", "سایبرپانک")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()
        createOverlays()
    }

    private fun updateScreenDimensions() {
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlays() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // ==========================================
        // 1. Mouse Pointer View (Visually Decoupled)
        // ==========================================
        val pointerSize = dpToPx(32)
        mousePointerView = ImageView(this).apply {
            setImageBitmap(createPointerBitmap(currentSkinIndex))
        }

        exactPointerX = (screenWidth / 2).toFloat()
        exactPointerY = (screenHeight / 3).toFloat()

        pointerParams = WindowManager.LayoutParams(
            pointerSize, pointerSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = exactPointerX.toInt()
            y = exactPointerY.toInt()
        }

        // ==========================================
        // 2. Mouse Shell (TouchPad Container Window)
        // ==========================================
        mouseShellView = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121824"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
            }
        }

        touchPadView = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A2232"))
                cornerRadius = dpToPx(12).toFloat()
            }
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        updateScreenDimensions()
                        lastTouchX = event.x
                        lastTouchY = event.y

                        if (isDragModeActive && !isTouchHoldActive) {
                            val (tipX, tipY) = getPointerHotspot()
                            startTouchHold(tipX, tipY)
                        }
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

                            if (isTouchHoldActive || isDragModeActive) {
                                moveContinuousDrag(oldTip.first, oldTip.second, newTip.first, newTip.second)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragModeActive && isTouchHoldActive) {
                            val (tipX, tipY) = getPointerHotspot()
                            releaseTouchHold(tipX, tipY)
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        val touchPadParamsLayout = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        mouseShellView?.addView(touchPadView, touchPadParamsLayout)

        mouseShellParams = WindowManager.LayoutParams(
            dpToPx(220), dpToPx(160),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            x = dpToPx(16)
            y = dpToPx(180)
        }

        // ==========================================
        // 3. Floating Click Bar Controls
        // ==========================================
        clickBarView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B0F19"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#1E293B"))
            }
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(8))
        }

        val clickBarHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(4))
        }

        val headerTitle = TextView(this).apply {
            text = "🖱️ کلیک‌بار موس"
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minimizeBtn = Button(this).apply {
            text = "➖"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = null
            setOnClickListener { toggleMinimizeUI(true) }
        }

        clickBarHeader.addView(headerTitle)
        clickBarHeader.addView(minimizeBtn)

        // Row 1: Left Click, Double Click, Right Click
        val clicksRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }

        val leftClickBtn = Button(this).apply {
            text = "چپ کلیک"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(0, 0, dpToPx(2), 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectSingleClick(tipX, tipY)
            }
        }

        val doubleClickBtn = Button(this).apply {
            text = "دو کلیک"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C3AED"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(dpToPx(1), 0, dpToPx(1), 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectDoubleClick(tipX, tipY)
            }
        }

        val rightClickBtn = Button(this).apply {
            text = "راست کلیک"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#059669"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(dpToPx(2), 0, 0, 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectRightClick(tipX, tipY)
            }
        }

        clicksRow.addView(leftClickBtn)
        clicksRow.addView(doubleClickBtn)
        clicksRow.addView(rightClickBtn)

        // Row 2: Precision Drag & Drop Tools
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
                    Toast.makeText(this@FloatingMouseAccessibilityService, "محل آیکون ثبت شد. نشانگر را منتقل و 'رها 🎯' را بزنید.", Toast.LENGTH_SHORT).show()
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
            setTextColor(Color.CYAN)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener {
                val (px, py) = getPointerHotspot()
                injectTouchDrag(px, py + 300f, px, py - 300f, 300)
            }
        }

        val scrollDownBtn = Button(this).apply {
            text = "پایین ▼"
            textSize = 8f
            setTextColor(Color.CYAN)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(dpToPx(1), 0, dpToPx(1), 0) }
            setOnClickListener {
                val (px, py) = getPointerHotspot()
                injectTouchDrag(px, py - 300f, px, py + 300f, 300)
            }
        }

        val skinBtn = Button(this).apply {
            text = "پوسته"
            textSize = 8f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10222A"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(dpToPx(1), 0, 0, 0) }
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
        clickBarView?.addView(liveModeRow)
        clickBarView?.addView(utilsRow)

        clickBarParams = WindowManager.LayoutParams(
            dpToPx(220), WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            x = dpToPx(16)
            y = dpToPx(16)
        }

        // Minimized Bubble
        minimizedBubbleView = TextView(this).apply {
            text = "🖱️"
            textSize = 20f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E5FF"))
                cornerRadius = dpToPx(24).toFloat()
            }
            visibility = View.GONE
            setOnClickListener { toggleMinimizeUI(false) }
        }

        bubbleParams = WindowManager.LayoutParams(
            dpToPx(48), dpToPx(48),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            x = dpToPx(16)
            y = dpToPx(16)
        }

        // Safely Add Overlays to WindowManager
        try {
            if (mousePointerView?.windowToken == null) windowManager?.addView(mousePointerView, pointerParams)
            if (minimizedBubbleView?.windowToken == null) windowManager?.addView(minimizedBubbleView, bubbleParams)
            if (mouseShellView?.windowToken == null) windowManager?.addView(mouseShellView, mouseShellParams)
            if (clickBarView?.windowToken == null) windowManager?.addView(clickBarView, clickBarParams)
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
            else -> Pair(px + 2f, py + 2f)
        }
    }

    private fun toggleMinimizeUI(minimize: Boolean) {
        if (minimize) {
            mouseShellView?.visibility = View.GONE
            clickBarView?.visibility = View.GONE
            minimizedBubbleView?.visibility = View.VISIBLE
        } else {
            mouseShellView?.visibility = View.VISIBLE
            clickBarView?.visibility = View.VISIBLE
            minimizedBubbleView?.visibility = View.GONE
        }
    }

    // ==========================================
    // Gesture Injections & Touch Mechanics
    // ==========================================
    fun injectSingleClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())

        val clickPath = Path().apply {
            moveTo(clampedX, clampedY)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectDoubleClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())

        val clickPath = Path().apply {
            moveTo(clampedX, clampedY)
        }
        val stroke1 = GestureDescription.StrokeDescription(clickPath, 0, 60)
        val stroke2 = GestureDescription.StrokeDescription(clickPath, 100, 60)
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
        val clickPath = Path().apply { moveTo(clampedX, clampedY) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 750)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun startTouchHold(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val holdPath = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX + 0.1f, clampedY + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(holdPath, 0, 600, true)
        activeHoldStroke = stroke
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        isTouchHoldActive = true
    }

    fun moveContinuousDrag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val now = System.currentTimeMillis()
        if (now - lastDragDispatchTime < 25) return

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
    }

    fun injectPrecisionDragAndDrop(startX: Float, startY: Float, endX: Float, endY: Float) {
        val clampedStartX = startX.coerceIn(5f, (screenWidth - 5).toFloat())
        val clampedStartY = startY.coerceIn(5f, (screenHeight - 5).toFloat())
        val clampedEndX = endX.coerceIn(5f, (screenWidth - 5).toFloat())
        val clampedEndY = endY.coerceIn(5f, (screenHeight - 5).toFloat())

        val holdPath = Path().apply {
            moveTo(clampedStartX, clampedStartY)
            lineTo(clampedStartX + 0.1f, clampedStartY + 0.1f)
        }
        val holdStroke = GestureDescription.StrokeDescription(holdPath, 0, 550, true)
        val holdGesture = GestureDescription.Builder().addStroke(holdStroke).build()

        dispatchGesture(holdGesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)

                val movePath = Path().apply {
                    moveTo(clampedStartX + 0.1f, clampedStartY + 0.1f)
                    lineTo(clampedEndX, clampedEndY)
                }
                val moveStroke = holdStroke.continueStroke(movePath, 0, 850, false)
                val moveGesture = GestureDescription.Builder().addStroke(moveStroke).build()

                dispatchGesture(moveGesture, object : GestureResultCallback() {
                    override fun onCompleted(gd: GestureDescription?) {
                        super.onCompleted(gd)
                        Toast.makeText(this@FloatingMouseAccessibilityService, "انتقال آیکون انجام شد ✅", Toast.LENGTH_SHORT).show()
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

    fun injectTouchDrag(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 250) {
        val clampedFromX = fromX.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedFromY = fromY.coerceIn(1f, (screenHeight - 1).toFloat())
        val clampedToX = toX.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedToY = toY.coerceIn(1f, (screenHeight - 1).toFloat())

        val dragPath = Path().apply {
            moveTo(clampedFromX, clampedFromY)
            lineTo(clampedToX, clampedToY)
        }
        val stroke = GestureDescription.StrokeDescription(dragPath, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun createPointerBitmap(skinIndex: Int): Bitmap {
        val size = dpToPx(32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (skinIndex) {
            0 -> {
                val path = Path().apply {
                    moveTo(2f, 2f)
                    lineTo(size * 0.75f, size * 0.5f)
                    lineTo(size * 0.45f, size * 0.55f)
                    lineTo(size * 0.65f, size * 0.95f)
                    lineTo(size * 0.5f, size * 1.0f)
                    lineTo(size * 0.3f, size * 0.6f)
                    lineTo(2f, size * 0.85f)
                    close()
                }
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawPath(path, paint)

                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)
            }
            1 -> {
                paint.color = Color.parseColor("#FFD700")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)

                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
            }
            2 -> {
                paint.color = Color.parseColor("#FF0055")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

                paint.color = Color.parseColor("#FFD700")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, paint)
            }
            3 -> {
                paint.color = Color.parseColor("#00E5FF")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
                canvas.drawLine(size / 2f, 0f, size / 2f, size.toFloat(), paint)
                canvas.drawLine(0f, size / 2f, size.toFloat(), size / 2f, paint)
            }
            4 -> {
                paint.color = Color.parseColor("#76FF03")
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(size / 2f, 2f)
                    lineTo(size - 2f, size / 2f)
                    lineTo(size / 2f, size - 2f)
                    lineTo(2f, size / 2f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (mousePointerView != null) windowManager?.removeView(mousePointerView)
            if (mouseShellView != null) windowManager?.removeView(mouseShellView)
            if (clickBarView != null) windowManager?.removeView(clickBarView)
            if (minimizedBubbleView != null) windowManager?.removeView(minimizedBubbleView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
