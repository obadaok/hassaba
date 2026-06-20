package com.platinum.vip.hasiba

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.animation.*
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.cardview.widget.CardView
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome
import com.mikepenz.iconics.view.IconicsImageView
import kotlin.math.abs

class FloatingCalculatorService : Service() {

    private lateinit var windowManager: WindowManager
    private val activeInstances = mutableListOf<CalculatorInstance>()
    private var deleteZoneView: View? = null
    private var deleteIcon: ImageView? = null
    private lateinit var deleteParams: WindowManager.LayoutParams

    private val screenHeight by lazy { resources.displayMetrics.heightPixels }
    private val screenWidth by lazy { resources.displayMetrics.widthPixels }

    companion object {
        private const val PREFS_NAME = "AppPrefs"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupDeleteZone()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CREATE") {
            createNewInstance()
        } else if (intent?.action == "ACTION_CLOSE_ALL_FLOATING") {
            closeAllInstances()
        }
        return START_NOT_STICKY
    }

    private fun safeAddView(view: View?, params: WindowManager.LayoutParams) {
        try {
            if (view != null && !view.isAttachedToWindow) {
                windowManager.addView(view, params)
            }
        } catch (e: Exception) {}
    }

    private fun safeRemoveView(view: View?) {
        try {
            if (view != null && view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        } catch (e: Exception) {}
    }

    private fun safeUpdateView(view: View?, params: WindowManager.LayoutParams) {
        try {
            if (view != null && view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
            }
        } catch (e: Exception) {}
    }

    private fun setupDeleteZone() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        deleteParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        deleteParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

        val inflater = LayoutInflater.from(this)
        deleteZoneView = inflater.inflate(R.layout.layout_delete_zone, null)
        deleteIcon = deleteZoneView?.findViewById(R.id.delete_icon)

        deleteZoneView?.visibility = View.GONE
        safeAddView(deleteZoneView, deleteParams)
    }

    fun showDeleteZone() {
        if (deleteZoneView?.visibility != View.VISIBLE) {
            deleteZoneView?.visibility = View.VISIBLE
            deleteZoneView?.alpha = 0f
            deleteZoneView?.animate()?.alpha(1f)?.setDuration(200)?.start()
            deleteIcon?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.start()
            deleteIcon?.clearColorFilter()
        }
    }

    fun hideDeleteZone() {
        if (deleteZoneView?.visibility == View.VISIBLE) {
            deleteZoneView?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
                deleteZoneView?.visibility = View.GONE
            }?.start()
        }
    }

    fun checkDeleteCollision(y: Int): Boolean {
        val screenPercentage = y.toFloat() / screenHeight.toFloat()
        val inDeleteZone = screenPercentage > 0.7f

        if (inDeleteZone) {
            deleteZoneView?.setBackgroundResource(R.drawable.bg_delete_zone_active)
            deleteIcon?.setColorFilter(Color.parseColor("#CD5C0F"), PorterDuff.Mode.SRC_IN)
            deleteIcon?.animate()?.scaleX(1.3f)?.scaleY(1.3f)?.alpha(1.0f)?.setDuration(100)?.start()
            return true
        } else {
            deleteZoneView?.setBackgroundResource(R.drawable.bg_delete_zone_normal)
            deleteIcon?.clearColorFilter()
            deleteIcon?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(0.7f)?.setDuration(100)?.start()
            return false
        }
    }

    private fun createNewInstance() {
        val instance = CalculatorInstance(this)
        activeInstances.add(instance)
        instance.show()
    }

    private fun closeAllInstances() {
        activeInstances.toList().forEach { it.destroy() }
        activeInstances.clear()
        stopSelf()
    }

    inner class CalculatorInstance(private val context: Context) {
        private var windowView: View? = null
        private var bubbleView: View? = null
        private lateinit var windowParams: WindowManager.LayoutParams
        private lateinit var bubbleParams: WindowManager.LayoutParams

        private var circularMenuContainer: FrameLayout? = null
        private var circularMenu: CircularMenuView? = null

        private var isBubbleMode = false
        private var isPendingDelete = false
        private var isTransparent = false
        private var themeContext: Context? = null

        init {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedX = prefs.getInt("last_window_x", (screenWidth / 2) - 300)
            val savedY = prefs.getInt("last_window_y", (screenHeight / 2) - 400)

            windowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX
                y = savedY
            }

            bubbleParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX
                y = savedY
            }

            val config = Configuration(context.resources.configuration)
            val appNightMode = AppCompatDelegate.getDefaultNightMode()

            if (appNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
                config.uiMode = Configuration.UI_MODE_NIGHT_YES or (config.uiMode and Configuration.UI_MODE_TYPE_MASK)
            } else if (appNightMode == AppCompatDelegate.MODE_NIGHT_NO) {
                config.uiMode = Configuration.UI_MODE_NIGHT_NO or (config.uiMode and Configuration.UI_MODE_TYPE_MASK)
            }

            val localizedContext = context.createConfigurationContext(config)
            themeContext = ContextThemeWrapper(localizedContext, R.style.AppTheme)
            val inflater = LayoutInflater.from(themeContext)

            windowView = inflater.inflate(R.layout.window_floating_native, null)
            bubbleView = inflater.inflate(R.layout.layout_floating_bubble, null)

            setupWindowUI()
            setupBubbleUI()
        }

        fun show() {
            windowView?.scaleX = 0f
            windowView?.scaleY = 0f
            windowView?.alpha = 0f
            safeAddView(windowView, windowParams)

            windowView?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(1f)?.setDuration(300)
                ?.setInterpolator(OvershootInterpolator())?.start()

            windowView?.post { ensureWithinBounds(windowView!!, windowParams, false) }
        }

        fun destroy() {
            savePosition()
            safeRemoveView(windowView)
            safeRemoveView(bubbleView)
            hideCircularMenu()
        }

        private fun savePosition() {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentParams = if (isBubbleMode) bubbleParams else windowParams
            prefs.edit()
                .putInt("last_window_x", currentParams.x)
                .putInt("last_window_y", currentParams.y)
                .apply()
        }

        private fun ensureWithinBounds(targetView: View, params: WindowManager.LayoutParams, isBubble: Boolean) {
            val viewWidth = targetView.width
            val viewHeight = targetView.height

            var finalX = params.x
            var finalY = params.y

            val maxY = screenHeight - viewHeight
            if (finalY < 50) finalY = 50
            if (finalY > maxY && maxY > 0) finalY = maxY

            if (isBubble) {
                finalX = if (params.x + (viewWidth / 2) < screenWidth / 2) 0 else screenWidth - viewWidth
            } else {
                val maxX = screenWidth - viewWidth
                if (finalX < 0) finalX = 0
                if (finalX > maxX && maxX > 0) finalX = maxX
            }

            if (finalX != params.x || finalY != params.y) {
                val animX = PropertyValuesHolder.ofInt("x", params.x, finalX)
                val animY = PropertyValuesHolder.ofInt("y", params.y, finalY)
                val animator = ValueAnimator.ofPropertyValuesHolder(animX, animY)
                animator.duration = 250
                animator.interpolator = OvershootInterpolator(1.2f)
                animator.addUpdateListener {
                    params.x = it.getAnimatedValue("x") as Int
                    params.y = it.getAnimatedValue("y") as Int
                    safeUpdateView(targetView, params)
                }
                animator.start()
            }
            savePosition()
        }

        private fun setupWindowUI() {
            val view = windowView!!
            val header = view.findViewById<View>(R.id.windowHeader)
            val btnMenu = view.findViewById<IconicsImageView>(R.id.btn_window_menu)
            val btnMin = view.findViewById<IconicsImageView>(R.id.btn_window_minimize)
            val keypadGrid = view.findViewById<GridLayout>(R.id.keypadGrid)
            val tvResult = view.findViewById<TextView>(R.id.tv_window_result)
            val tvPreview = view.findViewById<TextView>(R.id.tvPreview)
            val iconCalc = view.findViewById<View>(R.id.icon_calculator)
            val rootCardView = view.findViewById<CardView>(R.id.rootCardView)

            val btnCopy = view.findViewById<IconicsImageView>(R.id.btn_copy)
            val btnPaste = view.findViewById<IconicsImageView>(R.id.btn_paste)

            startIconPulse(iconCalc)

            btnMenu.setOnClickListener { showCircularMenu() }
            btnMin.setOnClickListener { toggleKeypad(keypadGrid) }

            setupCalculatorLogic(keypadGrid, tvResult, tvPreview)

            btnCopy.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val textToCopy = tvResult.text.toString()
                if (textToCopy.isNotEmpty() && textToCopy != "0") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("calculator_result", textToCopy))
                }
            }

            btnPaste.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (clipboard.hasPrimaryClip() && clipboard.primaryClip != null) {
                    val pastedText = clipboard.primaryClip!!.getItemAt(0).text.toString()
                    val cleanText = pastedText.replace(Regex("[^0-9.+\\-×÷%]"), "")
                    if (cleanText.isNotEmpty()) {
                        tvResult.text = cleanText
                        val result = CalculatorEngine.evaluate(cleanText, roundToFour = true)
                        if (result.isNotEmpty() && result != "خطأ") {
                            tvPreview.text = "$result"
                            tvPreview.visibility = View.VISIBLE
                        }
                    }
                }
            }

            setupDrag(header, windowParams, view, isBubble = false)
        }

        private fun setupBubbleUI() {
            val view = bubbleView!!
            val bubbleIcon = view.findViewById<View>(R.id.bubble_icon)
            startIconPulse(bubbleIcon)
            setupDrag(view, bubbleParams, view, isBubble = true)
        }

        private fun showCircularMenu() {
            if (circularMenuContainer != null) return

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val menuParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            circularMenuContainer = FrameLayout(context).apply {
                setOnClickListener { hideCircularMenu() }
            }

            circularMenu = CircularMenuView(themeContext ?: context).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                onMenuDismissed = {
                    hideCircularMenu()
                }
            }

            circularMenu?.addMenuItem(FontAwesome.Icon.faw_dot_circle, "فقاعة") {
                hideCircularMenu()
                switchToBubble()
            }

            circularMenu?.addMenuItem(if (isTransparent) FontAwesome.Icon.faw_eye else FontAwesome.Icon.faw_eye_slash, "الشفافية") {
                hideCircularMenu()
                toggleTransparency()
            }

            circularMenu?.addMenuItem(FontAwesome.Icon.faw_times, "إغلاق") {
                hideCircularMenu()
                closeSelf()
            }

            circularMenuContainer?.addView(circularMenu)
            safeAddView(circularMenuContainer, menuParams)
            circularMenu?.show()
        }

        private fun hideCircularMenu() {
            try {
                if (circularMenuContainer != null && circularMenuContainer?.isAttachedToWindow == true) {
                    safeRemoveView(circularMenuContainer)
                }
            } catch (e: Exception) {}
            circularMenuContainer = null
            circularMenu = null
        }

        private fun toggleTransparency() {
            isTransparent = !isTransparent
            val targetAlpha = if (isTransparent) 0.5f else 1.0f
            windowView?.animate()?.alpha(targetAlpha)?.setDuration(200)?.start()
        }

        private fun switchToBubble() {
            bubbleParams.x = windowParams.x + 50
            bubbleParams.y = windowParams.y

            windowView?.animate()?.scaleX(0f)?.scaleY(0f)?.alpha(0f)?.setDuration(200)?.withEndAction {
                safeRemoveView(windowView)
                isBubbleMode = true
                bubbleView?.scaleX = 0f; bubbleView?.scaleY = 0f; bubbleView?.alpha = 1f
                safeAddView(bubbleView, bubbleParams)
                bubbleView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(300)?.setInterpolator(OvershootInterpolator())?.withEndAction {
                    ensureWithinBounds(bubbleView!!, bubbleParams, true)
                }?.start()
            }?.start()
        }

        private fun switchToWindow() {
            windowParams.x = bubbleParams.x - 50
            windowParams.y = bubbleParams.y

            bubbleView?.animate()?.scaleX(0f)?.scaleY(0f)?.alpha(0f)?.setDuration(200)?.setInterpolator(AccelerateInterpolator())?.withEndAction {
                safeRemoveView(bubbleView)
                isBubbleMode = false
                windowView?.scaleX = 0f; windowView?.scaleY = 0f; windowView?.alpha = if (isTransparent) 0.5f else 1f
                windowView?.visibility = View.VISIBLE
                safeAddView(windowView, windowParams)
                windowView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(300)?.setInterpolator(OvershootInterpolator())?.withEndAction {
                    ensureWithinBounds(windowView!!, windowParams, false)
                }?.start()
            }?.start()
        }

        private fun toggleKeypad(grid: GridLayout) {
            if (grid.visibility == View.VISIBLE) {
                grid.animate().alpha(0f).scaleY(0f).setDuration(250).withEndAction {
                    grid.visibility = View.GONE
                    safeUpdateView(windowView, windowParams)
                    ensureWithinBounds(windowView!!, windowParams, false)
                }.start()
            } else {
                grid.visibility = View.VISIBLE
                grid.scaleY = 0f; grid.alpha = 0f; grid.pivotY = 0f
                grid.animate().alpha(1f).scaleY(1f).setDuration(250).withEndAction {
                    safeUpdateView(windowView, windowParams)
                    ensureWithinBounds(windowView!!, windowParams, false)
                }.start()
            }
        }

        private fun setupDrag(touchView: View, params: WindowManager.LayoutParams, targetView: View, isBubble: Boolean) {
            touchView.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            if (isBubble) showDeleteZone()
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()

                            if (abs(dx) > 10 || abs(dy) > 10) {
                                isDragging = true
                                params.x = initialX + dx
                                params.y = initialY + dy
                                safeUpdateView(targetView, params)

                                if (isBubble) {
                                    val centerY = params.y + (targetView.height / 2)
                                    isPendingDelete = checkDeleteCollision(centerY)
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isBubble) hideDeleteZone()

                            if (isDragging) {
                                if (isBubble && isPendingDelete) {
                                    targetView.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200).withEndAction {
                                        closeSelf()
                                    }.start()
                                } else {
                                    ensureWithinBounds(targetView, params, isBubble)
                                }
                            } else {
                                v.performClick()
                                if (isBubble) switchToWindow()
                            }
                            isPendingDelete = false
                            return true
                        }
                    }
                    return false
                }
            })
        }

        private fun closeSelf() {
            destroy()
            activeInstances.remove(this)
            if (activeInstances.isEmpty()) stopSelf()
        }

        private fun setupCalculatorLogic(grid: GridLayout, tvResult: TextView, tvPreview: TextView) {
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                child.setOnClickListener {
                    child.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    child.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                        child.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
                    }.start()

                    val key = child.tag?.toString() ?: return@setOnClickListener
                    val txt = tvResult.text.toString().replace(",", "")

                    when (key) {
                        "AC" -> {
                            tvResult.text = "0"
                            tvPreview.visibility = View.INVISIBLE
                            tvResult.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                                .withEndAction { tvResult.animate().scaleX(1f).scaleY(1f).start() }.start()
                        }
                        "⌫" -> {
                            if (txt.length > 1 && txt != "0") tvResult.text = txt.dropLast(1) else tvResult.text = "0"
                        }
                        "=" -> {
                            val res = tvPreview.text.toString()
                            if (res.isNotEmpty() && res != "خطأ") {
                                tvResult.text = res
                                tvPreview.visibility = View.INVISIBLE
                            }
                        }
                        else -> {
                            var cur = tvResult.text.toString()
                            if (cur == "0" && key != ".") cur = ""
                            val ops = listOf("+", "-", "×", "÷", "%")
                            var allow = true

                            if (key in ops) {
                                if (cur.isEmpty() || cur.last().toString() in ops || cur.last() == '.') allow = false
                            }
                            if (key == ".") {
                                val currentNum = cur.split(*ops.toTypedArray()).last()
                                if (currentNum.contains(".")) allow = false
                            }

                            if (allow) tvResult.text = cur + key
                        }
                    }

                    val currentText = tvResult.text.toString().trim()
                    if (currentText.isNotEmpty() && currentText != "0") {
                        val cleanText = currentText.trimEnd('+', '-', '×', '÷', '%')
                        val result = CalculatorEngine.evaluate(cleanText, roundToFour = true)
                        if (result.isNotEmpty() && result != "خطأ") {
                            tvPreview.text = "$result"
                            tvPreview.visibility = View.VISIBLE
                        } else {
                            tvPreview.visibility = View.INVISIBLE
                        }
                    } else {
                        tvPreview.visibility = View.INVISIBLE
                    }
                }
            }
        }

        private fun startIconPulse(icon: View) {
            val animator = ValueAnimator.ofFloat(1f, 1.15f, 1f)
            animator.duration = 2000
            animator.repeatCount = ValueAnimator.INFINITE
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                icon.scaleX = scale
                icon.scaleY = scale
            }
            animator.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { safeRemoveView(deleteZoneView) } catch (e: Exception) {}
        closeAllInstances()
    }
}