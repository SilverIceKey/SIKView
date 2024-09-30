package com.sik.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 控制器的自定义View
 *
 *
 * 支持外环的颜色配置，外环的背景配置
 *
 *
 * 支持内圆的颜色配置，内圆的背景配置
 *
 *
 * 支持指示器独自配置，支持格式有res，文本
 *
 * 这边提供了默认的背景供使用
 *
 * 内圆配置@see[R.drawable.inner_circle_gray]
 *
 * 外环配置@see[R.drawable.outer_ring_white_with_gray_border]
 *
 * 指示器配置@see[R.drawable.indicator_small_circle_gray]
 *
 * xml配置参数@see[R.styleable.RemoteControlView]
 *
 * 使用样例:
 * ```
 *  <com.sik.view.RemoteControlView
 *      android:id="@+id/remoteControl"
 *      android:layout_width="300dp"
 *      android:layout_height="300dp"
 *      app:outerBackground="@drawable/outer_ring_white_with_gray_border"
 *      app:centerBackground="@drawable/inner_circle_gray"
 *      app:upImage="@drawable/indicator_small_circle_gray"
 *      app:downImage="@drawable/indicator_small_circle_gray"
 *      app:leftImage="@drawable/indicator_small_circle_gray"
 *      app:rightImage="@drawable/indicator_small_circle_gray" />
 * ```
 * @author SilverIceKey
 *
 */
class RemoteControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 自定义属性
    private var centerDiameter: Float = 0f
    private var outerThickness: Float = 0f
    private var centerColor: Int = Color.BLUE
    private var outerColor: Int = Color.GRAY

    // 新的自定义属性
    private var outerBackground: Drawable? = null
    private var centerBackground: Drawable? = null

    private var upImage: Drawable? = null
    private var downImage: Drawable? = null
    private var leftImage: Drawable? = null
    private var rightImage: Drawable? = null

    private var upText: String = "↑"
    private var downText: String = "↓"
    private var leftText: String = "←"
    private var rightText: String = "→"

    // 画笔
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 文字画笔（用于方向指示）
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    // 监听器
    var onDirectionClickListener: OnDirectionClickListener? = null

    // 点击区域
    private val buttonRegions = mutableMapOf<String, Region>()

    // 视图中心点
    private var centerX: Float = 0f
    private var centerY: Float = 0f

    // Paths for drawing
    private val outerCirclePath = Path()
    private val innerCirclePath = Path()

    // 黄金比例常数
    private val CUSTOM_GOLDEN_RATIO = 1.618f // 保持与之前一致

    // 缓存方向指示的绘制参数
    private data class DirectionIndicator(
        val direction: String,
        var drawable: Drawable?,
        var text: String,
        val angle: Float,
        var rect: RectF = RectF(),
        var scaledDrawable: Drawable? = null
    )

    private val directionIndicators = listOf(
        DirectionIndicator("UP", null, upText, 270f),
        DirectionIndicator("DOWN", null, downText, 90f),
        DirectionIndicator("RIGHT", null, rightText, 0f),
        DirectionIndicator("LEFT", null, leftText, 180f)
    )

    private var directionIndicatorsDrawableRect = Rect()

    // 缓存 RectF 实例，避免在 onDraw 中创建
    private val arcRectF = RectF()

    // 缓存缩放后的 Drawable
    private var scaledOuterBackground: Bitmap? = null
    private var scaledCenterBackground: Bitmap? = null

    // Handler 和 Runnable 用于长按功能
    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var currentPressedDirection: String? = null

    // 长按触发延迟和重复间隔（毫秒）
    private val LONG_PRESS_DELAY = 500L
    private val REPEAT_INTERVAL = 100L

    init {
        // 读取自定义属性
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.RemoteControlView, 0, 0)
            centerDiameter =
                typedArray.getDimension(R.styleable.RemoteControlView_centerDiameter, 0f)
            outerThickness =
                typedArray.getDimension(R.styleable.RemoteControlView_outerThickness, 0f)
            centerColor = typedArray.getColor(R.styleable.RemoteControlView_centerColor, Color.BLUE)
            outerColor = typedArray.getColor(R.styleable.RemoteControlView_outerColor, Color.GRAY)

            // 加载新的属性
            outerBackground = typedArray.getDrawable(R.styleable.RemoteControlView_outerBackground)
            centerBackground =
                typedArray.getDrawable(R.styleable.RemoteControlView_centerBackground)

            upImage = typedArray.getDrawable(R.styleable.RemoteControlView_upImage)
            downImage = typedArray.getDrawable(R.styleable.RemoteControlView_downImage)
            leftImage = typedArray.getDrawable(R.styleable.RemoteControlView_leftImage)
            rightImage = typedArray.getDrawable(R.styleable.RemoteControlView_rightImage)

            upText = typedArray.getString(R.styleable.RemoteControlView_upText) ?: "↑"
            downText = typedArray.getString(R.styleable.RemoteControlView_downText) ?: "↓"
            leftText = typedArray.getString(R.styleable.RemoteControlView_leftText) ?: "←"
            rightText = typedArray.getString(R.styleable.RemoteControlView_rightText) ?: "→"

            typedArray.recycle()
        }

        centerPaint.color = centerColor
        outerPaint.color = outerColor

        // Assign drawables to direction indicators
        directionIndicators[0].drawable = upImage
        directionIndicators[1].drawable = downImage
        directionIndicators[2].drawable = rightImage
        directionIndicators[3].drawable = leftImage

        // Assign texts to direction indicators
        directionIndicators[0].text = upText
        directionIndicators[1].text = downText
        directionIndicators[2].text = rightText
        directionIndicators[3].text = leftText
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 确保视图为正方形
        val size =
            min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        initializeRegions()
        cacheDrawables(w, h)
    }

    /**
     * 根据黄金比例或自定义属性初始化 regions 和 direction indicators 的绘制参数
     */
    private fun initializeRegions() {
        buttonRegions.clear()
        val totalRadius = min(width, height) / 2f

        // 如果未在XML中设置 centerDiameter 和 outerThickness，按黄金比例计算
        if (centerDiameter == 0f && outerThickness == 0f) {
            // 根据黄金比例分割
            // innerRadius + outerThickness = totalRadius
            // outerThickness = GOLDEN_RATIO * innerRadius
            // innerRadius + GOLDEN_RATIO * innerRadius = totalRadius
            // innerRadius * (1 + GOLDEN_RATIO) = totalRadius
            // innerRadius = totalRadius / (1 + GOLDEN_RATIO)
            val innerRadius =
                totalRadius / (1 + CUSTOM_GOLDEN_RATIO) // innerRadius = totalRadius / 2.618
            centerDiameter = innerRadius * 2f
            outerThickness = totalRadius - innerRadius // outerThickness = 1.618 * innerRadius
        }

        val innerRadius = centerDiameter / 2f
        val outerRadius = innerRadius + outerThickness

        // 创建内环路径
        val innerPath = Path().apply {
            addCircle(centerX, centerY, innerRadius, Path.Direction.CCW)
        }

        // 定义四个方向及其角度
        val directions = listOf("UP", "DOWN", "RIGHT", "LEFT")
        val angles = listOf(
            Pair(225f, 90f),    // UP: 从225度开始，扫过90度（225到315度）
            Pair(45f, 90f),     // DOWN: 从45度开始，扫过90度（45到135度）
            Pair(315f, 90f),    // RIGHT: 从315度开始，扫过90度（315到45度）
            Pair(135f, 90f)     // LEFT: 从135度开始，扫过90度（135到225度）
        )

        // 预计算绘制区域
        for ((index, anglePair) in angles.withIndex()) {
            val path = Path().apply {
                moveTo(centerX, centerY)
                arcTo(
                    getArcRectF(centerX, centerY, outerRadius),
                    anglePair.first,
                    anglePair.second,
                    false
                )
                lineTo(centerX, centerY)
                close()
            }

            // 从扇形路径中减去内环路径
            path.op(innerPath, Path.Op.DIFFERENCE)

            // 创建区域
            val region = PathRegion(path, width, height)
            buttonRegions[directions[index]] = region.region
        }
    }

    /**
     * 缓存绘制所需的 Drawable 对象，避免在 onDraw 中创建
     */
    private fun cacheDrawables(w: Int, h: Int) {
        // 缓存外环背景
        if (outerBackground != null) {
            scaledOuterBackground = drawableToBitmap(outerBackground!!, w, h)
        } else {
            scaledOuterBackground = null
        }

        // 缓存内圆背景
        val innerRadius = centerDiameter / 2f
        if (centerBackground != null) {
            scaledCenterBackground = drawableToBitmap(
                centerBackground!!,
                (innerRadius * 2).toInt(),
                (innerRadius * 2).toInt()
            )
        } else {
            scaledCenterBackground = null
        }

        // 缓存方向指示的 Drawable
        directionIndicators.forEach { indicator ->
            indicator.scaledDrawable = indicator.drawable?.let { drawable ->
                val desiredSize = outerThickness / 2f
                drawableToScaledDrawable(drawable, desiredSize)
            }
        }
    }

    /**
     * 将 Drawable 缩放到指定大小并返回 Bitmap
     */
    private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * 将 Drawable 缩放到指定大小并返回新的 Drawable
     */
    private fun drawableToScaledDrawable(drawable: Drawable, desiredSize: Float): Drawable {
        val bitmap =
            Bitmap.createBitmap(desiredSize.toInt(), desiredSize.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, desiredSize.toInt(), desiredSize.toInt())
        drawable.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    /**
     * 获取绘制弧形的 RectF
     */
    private fun getArcRectF(centerX: Float, centerY: Float, radius: Float): RectF {
        arcRectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        return arcRectF
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val outerRadius = centerDiameter / 2f + outerThickness
        val innerRadius = centerDiameter / 2f

        // 绘制外圆环背景
        if (scaledOuterBackground != null) {
            canvas.drawBitmap(scaledOuterBackground!!, 0f, 0f, null)
        } else {
            // 默认颜色填充外圆环
            outerCirclePath.reset()
            outerCirclePath.addCircle(centerX, centerY, outerRadius, Path.Direction.CW)
            canvas.drawPath(outerCirclePath, outerPaint)
        }

        // 绘制内圆环背景
        if (scaledCenterBackground != null) {
            val left = centerX - innerRadius
            val top = centerY - innerRadius
            canvas.drawBitmap(scaledCenterBackground!!, left, top, null)
        } else {
            // 默认颜色填充内圆环
            innerCirclePath.reset()
            innerCirclePath.addCircle(centerX, centerY, innerRadius, Path.Direction.CCW)
            canvas.drawPath(innerCirclePath, centerPaint)
        }

        // 绘制方向指示图片或文本
        val indicatorRadius = innerRadius + outerThickness / 2f

        directionIndicators.forEach { indicator ->
            if (indicator.scaledDrawable != null) {
                // 绘制图片
                val radians = Math.toRadians(indicator.angle.toDouble())
                val xPos = centerX + (indicatorRadius * Math.cos(radians)).toFloat()
                val yPos = centerY + (indicatorRadius * Math.sin(radians)).toFloat()

                // 计算绘制位置
                val drawable = indicator.scaledDrawable!!
                val drawableWidth = drawable.intrinsicWidth
                val drawableHeight = drawable.intrinsicHeight

                val left = xPos - drawableWidth / 2f
                val top = yPos - drawableHeight / 2f

                // 设置 Drawable 的位置并绘制
                directionIndicatorsDrawableRect.left = left.toInt()
                directionIndicatorsDrawableRect.top = top.toInt()
                directionIndicatorsDrawableRect.right = (left + drawableWidth).toInt()
                directionIndicatorsDrawableRect.bottom = (top + drawableHeight).toInt()
                drawable.bounds = directionIndicatorsDrawableRect
                drawable.draw(canvas)
            } else {
                // 绘制文本箭头
                val textX =
                    centerX + (indicatorRadius * Math.cos(Math.toRadians(indicator.angle.toDouble()))).toFloat()
                val textY =
                    centerY + (indicatorRadius * Math.sin(Math.toRadians(indicator.angle.toDouble()))).toFloat() + (textPaint.textSize / 2)

                canvas.drawText(indicator.text, textX, textY, textPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x.toInt()
                val y = event.y.toInt()

                // 检测是否点击了中心按钮
                if (isCenterClicked(event.x, event.y)) {
                    onDirectionClickListener?.onClick("CENTER")
                    // 不触发长按事件 for center
                    return true
                }

                // 检查外围按钮
                for ((direction, region) in buttonRegions) {
                    if (region.contains(x, y)) {
                        if (currentPressedDirection != direction) {
                            // 切换到新的方向
                            stopRepeatingClick()
                            currentPressedDirection = direction
                            onDirectionClickListener?.onClick(direction)
                            startRepeatingClick(direction)
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val x = event.x.toInt()
                val y = event.y.toInt()

                if (currentPressedDirection != null) {
                    val currentRegion = buttonRegions[currentPressedDirection!!]
                    if (currentRegion != null && currentRegion.contains(x, y)) {
                        // 仍然在当前方向区域内，无需操作
                        return true
                    }

                    // 检测是否移动到了其他方向区域
                    for ((direction, region) in buttonRegions) {
                        if (region.contains(x, y)) {
                            if (currentPressedDirection != direction) {
                                // 切换方向
                                stopRepeatingClick()
                                currentPressedDirection = direction
                                onDirectionClickListener?.onClick(direction)
                                startRepeatingClick(direction)
                            }
                            return true
                        }
                    }

                    // 如果移动到非任何方向区域，停止持续点击
                    stopRepeatingClick()
                    currentPressedDirection = null
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 用户松开或取消触摸，停止持续点击
                stopRepeatingClick()
                currentPressedDirection = null
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 检查是否点击了中心按钮
     */
    private fun isCenterClicked(x: Float, y: Float): Boolean {
        val radius = centerDiameter / 2f
        val dx = x - centerX
        val dy = y - centerY
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    /**
     * 开始重复点击事件
     */
    private fun startRepeatingClick(direction: String) {
        // 定义重复执行的 Runnable
        repeatRunnable = object : Runnable {
            override fun run() {
                onDirectionClickListener?.onClick(direction)
                // 继续执行
                handler.postDelayed(this, REPEAT_INTERVAL)
            }
        }

        // 启动第一次延迟后的执行
        handler.postDelayed(repeatRunnable!!, LONG_PRESS_DELAY)
    }

    /**
     * 停止重复点击事件
     */
    private fun stopRepeatingClick() {
        repeatRunnable?.let {
            handler.removeCallbacks(it)
        }
        repeatRunnable = null
    }

    /**
     * 当视图从窗口移除时，确保移除所有回调，防止内存泄漏
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRepeatingClick()
    }

    fun interface OnDirectionClickListener {
        fun onClick(direction: String)
    }

    /**
     * 辅助类，用于将Path转换为Region
     */
    private class PathRegion(path: Path, viewWidth: Int, viewHeight: Int) {
        val region: Region

        init {
            // 创建一个剪裁区域，覆盖整个视图
            val clipRegion = Region(0, 0, viewWidth, viewHeight)
            region = Region()
            region.setPath(path, clipRegion)
        }
    }
}
