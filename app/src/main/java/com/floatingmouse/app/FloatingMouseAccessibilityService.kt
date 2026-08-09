package com.floatingmouse/app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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

    private var windowManager: WindowManager? = null
    
    // Overlays
    private var pointerView: FrameLayout? = null
    private var pointerImageView: ImageView? = null
    private var pointerParams: WindowManager.LayoutParams? = null

    private var touchPadView: LinearLayout? = null
    private var touchPadParams: WindowManager.LayoutParams? = null

    private var clickBarView: LinearLayout? = null
    private var clickBarParams: WindowManager.LayoutParams? = null

    private var isDragModeActive = false

    // Precision Grab & Drop coordinates
    private var isGrabPointSaved = false
    private var grabStartX = 0f
    private var grabStartY = 0f
    private var isTouchHoldActive = false
    private var activeHoldStroke: GestureDescription.StrokeDescription? = null

    private var isMinimized = false
    private var currentSkinIndex = 0

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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        createPointerOverlay()
        createTouchPadOverlay()
        createClickBarOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun createPointerOverlay() {
        pointerParams = WindowManager.LayoutParams(
            dpToPx(32),
            dpToPx(32),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = screenWidth / 2
            y = screenHeight / 2
        }

        pointerImageView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setColorFilter(Color.RED)
        }

        pointerView = FrameLayout(this).apply {
            addView(pointerImageView)
        }

        try {
            windowManager?.addView(pointerView, pointerParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePointerSkin() {
        when (currentSkinIndex) {
            0 -> {
                pointerImageView?.setImageResource(android.R.drawable.ic_menu_compass)
                pointerImageView?.setColorFilter(Color.RED)
            }
            1 -> {
                pointerImageView?.setImageResource(android.R.drawable.ic_menu_directions)
                pointerImageView?.setColorFilter(Color.GREEN)
            }
            2 -> {
                pointerImageView?.setImageResource(android.R.drawable.ic_menu_mylocation)
                pointerImageView?.setColorFilter(Color.CYAN)
            }
            3 -> {
                pointerImageView?.setImageResource(android.R.drawable.ic_menu_gallery)
                pointerImageView?.setColorFilter(Color.YELLOW)
            }
            4 -> {
                pointerImageView?.setImageResource(android.R.drawable.ic_menu_camera)
                pointerImageView?.setColorFilter(Color.MAGENTA)
            }
        }
    }

    private fun createTouchPadOverlay() {
        touchPadParams = WindowManager.LayoutParams(
            dpToPx(240),
            dpToPx(200),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            x = dpToPx(16)
            y = dpToPx(16)
        }

        touchPadView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00BCD4"))
            }
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3300BCD4"))
                cornerRadius = dpToPx(10).toFloat()
            }
        }

        val dragHandle = TextView(this).apply {
            text = "::: تاچ‌پد :::"
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = touchPadParams?.x ?: 0
                    initialY = touchPadParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    touchPadParams?.x = initialX - (event.rawX - initialTouchX).toInt()
                    touchPadParams?.y = initialY - (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(touchPadView, touchPadParams)
                    true
                }
                else -> false
            }
        }

        val centerBtn = Button(this).apply {
            text = "🎯"
            textSize = 10f
            background = null
            setOnClickListener {
                pointerParams?.x = screenWidth / 2
                pointerParams?.y = screenHeight / 2
                windowManager?.updateViewLayout(pointerView, pointerParams)
            }
        }

        val minimizeBtn = Button(this).apply {
            text = "━"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = null
            setOnClickListener {
                isMinimized = !isMinimized
                val padArea = touchPadView?.findViewById<View>(1001)
                if (isMinimized) {
                    padArea?.visibility = View.GONE
                    text = "❏"
                } else {
                    padArea?.visibility = View.VISIBLE
                    text = "━"
                }
            }
        }

        headerLayout.addView(dragHandle)
        headerLayout.addView(centerBtn)
        headerLayout.addView(minimizeBtn)

        val padArea = FrameLayout(this).apply {
            id = 1001
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(8)) }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dpToPx(8).toFloat()
            }
        }

        var lastTouchX = 0f
        var lastTouchY = 0f

        padArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastTouchX) * 1.6f
                    val dy = (event.rawY - lastTouchY) * 1.6f

                    var newX = (pointerParams?.x ?: 0) + dx.toInt()
                    var newY = (pointerParams?.y ?: 0) + dy.toInt()

                    newX = newX.coerceIn(0, screenWidth - dpToPx(24))
                    newY = newY.coerceIn(0, screenHeight - dpToPx(24))

                    pointerParams?.x = newX
                    pointerParams?.y = newY
                    windowManager?.updateViewLayout(pointerView, pointerParams)

                    if (isDragModeActive) {
                        val (tipX, tipY) = getPointerHotspot()
                        injectTouchDrag(tipX, tipY, tipX + dx, tipY + dy, 40)
                    }

                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                else -> false
            }
        }

        touchPadView?.addView(headerLayout)
        touchPadView?.addView(padArea)

        try {
            windowManager?.addView(touchPadView, touchPadParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createClickBarOverlay() {
        clickBarParams = WindowManager.LayoutParams(
            dpToPx(210),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.LEFT
            x = dpToPx(16)
            y = dpToPx(16)
        }

        clickBarView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            padding = dpToPx(8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F00F172A"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#FF007A"))
            }
        }

        val clickBarHeader = TextView(this).apply {
            text = "::: پنل کلیک ::: ✋"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(2), 0, dpToPx(6))
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        clickBarHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = clickBarParams?.x ?: 0
                    initialY = clickBarParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    clickBarParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                    clickBarParams?.y = initialY - (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(clickBarView, clickBarParams)
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
            textSize = 8.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E3A8A"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#3B82F6"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                injectTouchClick(tipX, tipY)
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
            textSize = 8.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4C1D95"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#8B5CF6"))
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

        // Row 2: Precision Grab & Drop + Touch Hold Lock
        val dragToolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        grabButtonRef = Button(this).apply {
            text = "گرفتن 📍"
            textSize = 8.5f
            setTextColor(Color.CYAN)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#064E3B"))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#10B981"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener {
                val (tipX, tipY) = getPointerHotspot()
                if (!isGrabPointSaved) {
                    grabStartX = tipX
                    grabStartY = tipY
                    isGrabPointSaved = true
                    text = "رها 🎯"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#B91C1C"))
                        cornerRadius = dpToPx(6).toFloat()
                    }
                    Toast.makeText(this@FloatingMouseAccessibilityService, "مکان مبدا ثبت شد 📍. پوینتر را به مقصد برده و 'رها 🎯' را بزنید.", Toast.LENGTH_SHORT).show()
                } else {
                    injectPrecisionDragAndDrop(grabStartX, grabStartY, tipX, tipY)
                    isGrabPointSaved = false
                    text = "گرفتن 📍"
                    setTextColor(Color.CYAN)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#064E3B"))
                        cornerRadius = dpToPx(6).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#10B981"))
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
                        setColor(Color.parseColor("#854D0E"))
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
            setPadding(0, dpToPx(2), 0, dpToPx(2))
        }

        val scrollUpBtn = Button(this).apply {
            text = "بالا ▲"
            textSize = 8f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(0, 0, dpToPx(1), 0) }
            setOnClickListener {
                injectTouchDrag(screenWidth / 2f, screenHeight * 0.3f, screenWidth / 2f, screenHeight * 0.7f, 200)
            }
        }

        val scrollDownBtn = Button(this).apply {
            text = "پایین ▼"
            textSize = 8f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(dpToPx(1), 0, dpToPx(1), 0) }
            setOnClickListener {
                injectTouchDrag(screenWidth / 2f, screenHeight * 0.7f, screenWidth / 2f, screenHeight * 0.3f, 200)
            }
        }

        val skinBtn = Button(this).apply {
            text = "پوسته"
            textSize = 8f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply { setMargins(dpToPx(1), 0, 0, 0) }
            setOnClickListener {
                currentSkinIndex = (currentSkinIndex + 1) % 5
                updatePointerSkin()
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

        try {
            windowManager?.addView(clickBarView, clickBarParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPointerHotspot(): Pair<Float, Float> {
        val px = (pointerParams?.x ?: 0).toFloat()
        val py = (pointerParams?.y ?: 0).toFloat()
        val size = dpToPx(32).toFloat()
        return when (currentSkinIndex) {
            1 -> Pair(px + size / 2f, py + size / 2f - 2f)
            3, 4 -> Pair(px + size / 2f, py + size / 2f)
            else -> Pair(px + 3f, py + 3f)
        }
    }

    fun injectTouchClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val clickPath = Path().apply { moveTo(clampedX, clampedY) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectDoubleClick(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val clickPath = Path().apply { moveTo(clampedX, clampedY) }
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
        val stroke = GestureDescription.StrokeDescription(holdPath, 0, 60000, true)
        activeHoldStroke = stroke
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        isTouchHoldActive = true
    }

    fun releaseTouchHold(x: Float, y: Float) {
        val clampedX = x.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedY = y.coerceIn(1f, (screenHeight - 1).toFloat())
        val path = Path().apply {
            moveTo(clampedX, clampedY)
            lineTo(clampedX + 0.1f, clampedY + 0.1f)
        }
        val stroke = activeHoldStroke?.continueStroke(path, 0, 100, false)
            ?: GestureDescription.StrokeDescription(path, 0, 100, false)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        activeHoldStroke = null
        isTouchHoldActive = false
    }

    /**
     * High-Precision Drag & Drop Gesture for Mixamo Rigging & Android Launcher Icons
     * Uses sequential 2-phase GestureDescription (Hold -> Move) to prevent multi-touch screen pan.
     */
    fun injectPrecisionDragAndDrop(startX: Float, startY: Float, endX: Float, endY: Float) {
        val clampedStartX = startX.coerceIn(5f, (screenWidth - 5).toFloat())
        val clampedStartY = startY.coerceIn(5f, (screenHeight - 5).toFloat())
        val clampedEndX = endX.coerceIn(5f, (screenWidth - 5).toFloat())
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

    fun injectTouchDrag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 250) {
        val clampedStartX = startX.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedStartY = startY.coerceIn(1f, (screenHeight - 1).toFloat())
        val clampedEndX = endX.coerceIn(1f, (screenWidth - 1).toFloat())
        val clampedEndY = endY.coerceIn(1f, (screenHeight - 1).toFloat())

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
        pointerView?.let { windowManager?.removeView(it) }
        touchPadView?.let { windowManager?.removeView(it) }
        clickBarView?.let { windowManager?.removeView(it) }
    }
}
