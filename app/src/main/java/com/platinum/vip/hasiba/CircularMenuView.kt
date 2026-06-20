package com.platinum.vip.hasiba

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome
import kotlin.math.*

class CircularMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val items = mutableListOf<MenuItem>()

    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var innerRadius = 0f

    private val itemAnimations = mutableListOf<Float>()
    private var isShowing = false
    private var isHiding = false
    private var selectedIndex = -1

    // تم إزالة الألوان الثابتة من هنا لنقوم بتحديثها ديناميكياً
    private var backgroundColor = 0
    private var accentColor = 0
    private var dividerColor = 0
    private val overlayColor = 0xCC000000.toInt()

    private var currentHideAnimator: ValueAnimator? = null
    private val showAnimators = mutableListOf<ValueAnimator>()

    // متغير مهم لإعلام الكلاس الأب (MainActivity) بأن القائمة اختفت ليصفر المتغير
    var onMenuDismissed: (() -> Unit)? = null

    data class MenuItem(
        val icon: IIcon,
        val label: String,
        val onClick: () -> Unit
    )

    init {
        setBackgroundColor(0x00000000)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        outerRadius = min(w, h) * 0.4f
        innerRadius = outerRadius * 0.35f
    }

    fun addMenuItem(icon: IIcon, label: String, onClick: () -> Unit) {
        items.add(MenuItem(icon, label, onClick))
        itemAnimations.add(0f)
        invalidate()
    }

    // تم التعديل هنا: سحب الألوان في كل مرة تظهر فيها القائمة لتطابق الثيم
    private fun updateThemeColors() {
        backgroundColor = ContextCompat.getColor(context, R.color.card_surface)
        accentColor = ContextCompat.getColor(context, R.color.bronze_accent)
        dividerColor = ContextCompat.getColor(context, R.color.border) // يفضل لون الـ border ليكون أنعم من النص
    }

    fun show() {
        if (isShowing || isHiding) return

        updateThemeColors() // تحديث الألوان قبل العرض

        cancelAllAnimations()
        isShowing = true
        isHiding = false
        visibility = VISIBLE

        items.indices.forEach { index ->
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300
                startDelay = index * 80L
                interpolator = OvershootInterpolator(1.5f)
                addUpdateListener { animation ->
                    itemAnimations[index] = animation.animatedValue as Float
                    invalidate()
                }
            }
            showAnimators.add(animator)
            animator.start()
        }
    }

    fun hide(onComplete: (() -> Unit)? = null) {
        if (isHiding || (!isShowing && itemAnimations.all { it == 0f })) {
            onComplete?.invoke()
            onMenuDismissed?.invoke() // إعلام الكلاس الأب بالإغلاق
            return
        }
        cancelAllAnimations()
        isHiding = true
        isShowing = false

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                for (i in itemAnimations.indices) {
                    itemAnimations[i] = progress
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isHiding = false
                    visibility = GONE
                    post {
                        onComplete?.invoke()
                        onMenuDismissed?.invoke() // إعلام الكلاس الأب بالإغلاق
                    }
                }
            })
        }
        currentHideAnimator = animator
        animator.start()
    }

    private fun cancelAllAnimations() {
        showAnimators.forEach { it.cancel() }
        showAnimators.clear()
        currentHideAnimator?.cancel()
        currentHideAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isShowing && !isHiding && itemAnimations.all { it == 0f }) return

        canvas.drawColor(overlayColor)

        val itemCount = items.size
        if (itemCount == 0) return

        val anglePerItem = 360f / itemCount

        items.forEachIndexed { index, item ->
            val progress = itemAnimations[index]
            if (progress > 0) {
                val startAngle = -90f + (anglePerItem * index)
                val sweepAngle = anglePerItem * progress

                val path = Path()
                path.moveTo(centerX, centerY)

                val rect = RectF(
                    centerX - outerRadius,
                    centerY - outerRadius,
                    centerX + outerRadius,
                    centerY + outerRadius
                )

                path.arcTo(rect, startAngle, sweepAngle)
                path.lineTo(centerX, centerY)
                path.close()

                paint.color = if (index == selectedIndex) accentColor else backgroundColor
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)

                paint.color = dividerColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawPath(path, paint)

                if (progress >= 0.5f) {
                    val iconProgress = (progress - 0.5f) * 2f
                    val angleRad = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
                    val iconDistance = (innerRadius + outerRadius) / 2

                    val iconX = (centerX + cos(angleRad) * iconDistance).toFloat()
                    val iconY = (centerY + sin(angleRad) * iconDistance).toFloat()
                    val iconSize = (outerRadius * 0.2f * iconProgress).toInt()

                    val iconDrawable = IconicsDrawable(context, item.icon).apply {
                        setTint(if (index == selectedIndex) backgroundColor else accentColor)
                        setBounds(
                            (iconX - iconSize / 2).toInt(),
                            (iconY - iconSize / 2).toInt(),
                            (iconX + iconSize / 2).toInt(),
                            (iconY + iconSize / 2).toInt()
                        )
                        alpha = (255 * iconProgress).toInt()
                    }
                    iconDrawable.draw(canvas)
                }
            }
        }

        val maxProgress = itemAnimations.maxOrNull() ?: 0f
        if (maxProgress > 0) {
            paint.color = 0x40000000
            paint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY + 4, innerRadius * maxProgress, paint)

            paint.color = accentColor
            canvas.drawCircle(centerX, centerY, innerRadius * maxProgress, paint)

            paint.color = backgroundColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawCircle(centerX, centerY, innerRadius * maxProgress * 0.95f, paint)

            if (maxProgress >= 0.7f) {
                val iconProgress = (maxProgress - 0.7f) * 3.33f
                val iconSize = (innerRadius * 0.5f * iconProgress).toInt()
                val backIcon = IconicsDrawable(context, FontAwesome.Icon.faw_times).apply {
                    setTint(backgroundColor)
                    setBounds(
                        (centerX - iconSize / 2).toInt(),
                        (centerY - iconSize / 2).toInt(),
                        (centerX + iconSize / 2).toInt(),
                        (centerY + iconSize / 2).toInt()
                    )
                    alpha = (255 * iconProgress).toInt()
                }
                backIcon.draw(canvas)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val touchX = event.x
                val touchY = event.y
                val dx = touchX - centerX
                val dy = touchY - centerY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance <= innerRadius) {
                    selectedIndex = -2
                    invalidate()
                    return true
                }

                if (distance <= outerRadius && distance > innerRadius) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    angle = (angle + 90f) % 360f

                    val anglePerItem = 360f / items.size
                    val index = (angle / anglePerItem).toInt()

                    if (index != selectedIndex && itemAnimations.getOrNull(index) == 1f) {
                        selectedIndex = index
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        invalidate()
                    }
                    return true
                } else {
                    selectedIndex = -1
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (selectedIndex == -2) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    hide() // تم التعديل: استدعاء الإخفاء بدون إرسال أمر إضافي
                } else if (selectedIndex >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val clickedIndex = selectedIndex
                    selectedIndex = -1
                    hide {
                        items.getOrNull(clickedIndex)?.onClick?.invoke()
                    }
                } else {
                    hide() // تم التعديل: إغلاق القائمة عند النقر في الفراغ
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                selectedIndex = -1
                hide()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}