package com.sik.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 圆形裁剪视图
 */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // 绘制白色圆形边框，预创建
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2 * resources.displayMetrics.density // 2dp
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Mask Bitmap，用于预绘制遮罩层
    private var maskBitmap: Bitmap? = null
    private var cropCircleBitmap: Bitmap? = null

    private var viewWidth = 0
    private var viewHeight = 0
    private var circleRadius = 0f
    private var circleCenterX = 0f
    private var circleCenterY = 0f

    private val matrixValues = FloatArray(9)
    private var scaleGestureDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector

    private var currentMatrix = Matrix()

    private var isMatrixInitialized = false

    // 缩放限制
    private var minScale = 1f
    private var maxScale = 2f

    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        scaleType = ScaleType.MATRIX

        // 确保硬件加速开启
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        circleRadius = min(w, h) / 2.5f
        circleCenterX = w / 2f
        circleCenterY = h / 2f

        // 初始化遮罩层
        createMask()

        // 如果 Drawable 已设置且矩阵未初始化，则初始化矩阵
        if (!isMatrixInitialized && drawable != null) {
            initMatrix()
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        isMatrixInitialized = false
        if (viewWidth > 0 && viewHeight > 0 && drawable != null) {
            initMatrix()
        }
    }

    /**
     * 初始化矩阵，确保图片适应圆形裁剪区域并居中显示
     */
    private fun initMatrix() {
        drawable?.let {
            val drawableWidth = it.intrinsicWidth.toFloat()
            val drawableHeight = it.intrinsicHeight.toFloat()

            // 计算适应圆形的缩放比例，确保图片较长边适应圆形直径
            val scale = (circleRadius * 2) / max(drawableWidth, drawableHeight)
            minScale = scale
            maxScale = scale * 2

            // 初始化矩阵：先缩放，再居中
            currentMatrix.reset()
            currentMatrix.postScale(scale, scale, 0f, 0f)
            val scaledWidth = drawableWidth * scale
            val scaledHeight = drawableHeight * scale
            val translateX = (viewWidth - scaledWidth) / 2f
            val translateY = (viewHeight - scaledHeight) / 2f
            currentMatrix.postTranslate(translateX, translateY)
            imageMatrix = currentMatrix
            isMatrixInitialized = true
        }
    }

    /**
     * 创建遮罩层位图，预绘制以提升性能
     */
    private fun createMask() {
        maskBitmap?.recycle()
        maskBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap!!)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        paint.alpha = 178 // 70% 透明度

        // 绘制全屏黑色半透明遮罩
        canvas.drawRect(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat(), paint)

        // 使用 CLEAR 模式清除圆形区域
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawCircle(circleCenterX, circleCenterY, circleRadius, paint)

        // 重置混合模式
        paint.xfermode = null

        // 绘制白色圆形边框
        canvas.drawCircle(circleCenterX, circleCenterY, circleRadius, borderPaint)
    }

    override fun onDraw(canvas: Canvas) {
        // 绘制图片
        super.onDraw(canvas)
        // 绘制预先创建的遮罩层
        maskBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    // 缩放监听器
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            // 获取当前缩放
            currentMatrix.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]
            // 计算新的缩放比例并限制在[minScale, maxScale]之间
            var newScale = currentScale * scaleFactor
            newScale = max(minScale, min(newScale, maxScale))
            val adjustedScaleFactor = newScale / currentScale
            // 进行均匀缩放
            currentMatrix.postScale(
                adjustedScaleFactor,
                adjustedScaleFactor,
                detector.focusX,
                detector.focusY
            )
            imageMatrix = currentMatrix
            invalidate()
            return true
        }
    }

    // 拖动监听器
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent,
            distanceX: Float, distanceY: Float
        ): Boolean {
            currentMatrix.postTranslate(-distanceX, -distanceY)
            imageMatrix = currentMatrix
            checkTranslate()
            invalidate()
            return true
        }
    }

    private fun checkTranslate() {
        val matrixValues = FloatArray(9)
        imageMatrix.getValues(matrixValues)
        val scaleX = matrixValues[Matrix.MSCALE_X]
        val translateX = matrixValues[Matrix.MTRANS_X]
        val translateY = matrixValues[Matrix.MTRANS_Y]
        val drawableWidth = drawable.intrinsicWidth * scaleX
        val drawableHeight = drawable.intrinsicHeight * scaleX
        val circleLeft = circleCenterX - circleRadius
        val circleRight = circleCenterX + circleRadius
        val circleTop = circleCenterY - circleRadius
        val circleBottom = circleCenterY + circleRadius
        if (translateX > circleLeft) {
            currentMatrix.postTranslate(circleLeft - translateX, 0f)
        }
        if (translateY > circleTop) {
            currentMatrix.postTranslate(0f, circleTop - translateY)
        }
        if (translateX + drawableWidth < circleRight) {
            currentMatrix.postTranslate(circleRight - drawableWidth - translateX, 0f)
        }
        if (translateY + drawableHeight < circleBottom) {
            currentMatrix.postTranslate(0f, circleBottom - drawableHeight - translateY)
        }
        imageMatrix = currentMatrix
    }


    /**
     * 获取 Drawable 的 Bitmap
     */
    private fun getDrawableBitmap(): Bitmap? {
        return drawable?.toBitmap()
    }

    /**
     * 获取裁剪后的 Bitmap
     */
    fun getCroppedBitmap(): Bitmap? {
        val bitmap = getDrawableBitmap() ?: return null

        // 创建与圆形裁剪区域相同尺寸的 Bitmap
        val output = Bitmap.createBitmap(
            (circleRadius * 2).toInt(),
            (circleRadius * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // 定义圆形路径
        val path = Path().apply {
            addCircle(circleRadius, circleRadius, circleRadius, Path.Direction.CCW)
        }

        // 剪切路径并绘制图片
        canvas.clipPath(path)
        // 计算绘制的位置和缩放
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            output.width,
            output.height,
            true
        )
        // 将图片绘制在圆形区域内
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)

        return output
    }

    /**
     * 获取裁剪后的图片的 Base64 数据
     */
    fun getCroppedImageBase64(): String? {
        val croppedBitmap = getCroppedBitmap() ?: return null
        val byteArrayOutputStream = ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}
