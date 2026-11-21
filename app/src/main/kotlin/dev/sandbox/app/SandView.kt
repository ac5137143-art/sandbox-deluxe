package dev.sandbox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import dev.sandbox.core.El
import dev.sandbox.core.World
import dev.sandbox.core.composeBackground
import dev.sandbox.core.paintPixels
import kotlin.math.max
import kotlin.math.roundToInt

enum class Tool { BRUSH, LINE, RECT, ERASER, PICKER }

/**
 * Canvas that draws the simulation and turns touch gestures into [SimCommand]s.
 *
 * The bitmap is owned by the UI thread; the pixels array is copied under the engine lock, so a
 * frame can never be drawn half updated.
 */
class SandView @JvmOverloads constructor(
    context: Context,
    val engine: SandboxEngine,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val bgPaint = Paint()
    private val bitmapPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xCCFFFFFF.toInt()
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x99FFFFFF.toInt()
    }

    private var bitmap: Bitmap? = null
    private val dst = Rect()
    private var cellPx = 4

    var targetCells: Int = 120_000
        set(value) {
            if (field == value) return
            field = value
            requestResize()
        }

    var brushPx: Float = 24f
    var coverage: Int = 100
    var tool: Tool = Tool.BRUSH
    var element: Int = El.SAND.id
    var haptics: Boolean = true
    var onPick: ((Int) -> Unit)? = null
    var onPainted: (() -> Unit)? = null

    private var lastCellX = -1
    private var lastCellY = -1
    private var gestureStart: PointF? = null
    private var gestureEnd: PointF? = null

    init {
        isClickable = true
        engine.onFrame = { postInvalidateOnAnimation() }
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(0xFF101520.toInt(), 0xFF05070C.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun requestResize() {
        val w = width
        val h = height
        if (w > 0 && h > 0) engine.submit(SimCommand.Resize(w, h, targetCells))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat().coerceAtLeast(1f),
            intArrayOf(0xFF111726.toInt(), 0xFF04060A.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        bitmap = null
        requestResize()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val world: World = engine.world
        val bmp = ensureBitmap(world)
        synchronized(engine.renderLock) {
            bmp.setPixels(world.pixels, 0, world.width, 0, 0, world.width, world.height)
        }
        updateDst(world)
        canvas.drawBitmap(bmp, null, dst, bitmapPaint)
        drawPreview(canvas, world)
    }

    private fun ensureBitmap(world: World): Bitmap {
        val current = bitmap
        if (current != null && current.width == world.width && current.height == world.height) {
            return current
        }
        val created = Bitmap.createBitmap(world.width, world.height, Bitmap.Config.ARGB_8888)
        created.setHasAlpha(true)
        bitmap = created
        return created
    }

    private fun updateDst(world: World) {
        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = minOf(vw / world.width, vh / world.height)
        val dw = world.width * scale
        val dh = world.height * scale
        val left = (vw - dw) / 2f
        val top = (vh - dh) / 2f
        dst.set(
            left.roundToInt(),
            top.roundToInt(),
            (left + dw).roundToInt(),
            (top + dh).roundToInt(),
        )
        cellPx = max(1, (scale).roundToInt())
    }

    private fun drawPreview(canvas: Canvas, world: World) {
        val start = gestureStart ?: return
        val end = gestureEnd ?: return
        val brush = brushPx / 2f
        when (tool) {
            Tool.LINE -> {
                canvas.drawLine(start.x, start.y, end.x, end.y, previewPaint)
                canvas.drawCircle(end.x, end.y, brush, cursorPaint)
            }
            Tool.RECT -> {
                val r = RectF(
                    minOf(start.x, end.x), minOf(start.y, end.y),
                    maxOf(start.x, end.x), maxOf(start.y, end.y),
                )
                canvas.drawRect(r, previewPaint)
            }
            else -> Unit
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val world = engine.world
        updateDst(world)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                gestureStart = PointF(event.x, event.y)
                gestureEnd = PointF(event.x, event.y)
                lastCellX = -1
                lastCellY = -1
                applyAt(event.x, event.y, world)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                gestureEnd?.set(event.x, event.y)
                if (tool == Tool.LINE || tool == Tool.RECT) {
                    invalidate()
                } else {
                    applyAt(event.x, event.y, world)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val start = gestureStart
                val end = gestureEnd
                if (start != null && end != null && (tool == Tool.LINE || tool == Tool.RECT)) {
                    commitShape(start, end, world)
                }
                gestureStart = null
                gestureEnd = null
                lastCellX = -1
                lastCellY = -1
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun applyAt(x: Float, y: Float, world: World) {
        val cx = toCellX(x, world)
        val cy = toCellY(y, world)
        when (tool) {
            Tool.PICKER -> {
                val id = engine.sample(cx, cy)
                if (id != 0) onPick?.invoke(id)
                return
            }
            Tool.LINE, Tool.RECT -> return
            else -> Unit
        }
        val radius = radiusCells()
        val paintId = if (tool == Tool.ERASER) 0 else element
        if (lastCellX < 0) {
            engine.submit(SimCommand.Paint(cx, cy, radius, paintId, coverage))
        } else if (lastCellX != cx || lastCellY != cy) {
            engine.submit(
                SimCommand.Line(lastCellX, lastCellY, cx, cy, radius, paintId, coverage)
            )
        }
        lastCellX = cx
        lastCellY = cy
        hapticTick()
        onPainted?.invoke()
    }

    private fun commitShape(start: PointF, end: PointF, world: World) {
        val x0 = toCellX(start.x, world)
        val y0 = toCellY(start.y, world)
        val x1 = toCellX(end.x, world)
        val y1 = toCellY(end.y, world)
        when (tool) {
            Tool.LINE -> engine.submit(SimCommand.Line(x0, y0, x1, y1, radiusCells(), element, coverage))
            Tool.RECT -> engine.submit(SimCommand.Rect(x0, y0, x1, y1, element, radiusCells().coerceAtLeast(1)))
            else -> Unit
        }
        hapticTick()
        onPainted?.invoke()
    }

    private fun hapticTick() {
        if (haptics) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun radiusCells(): Int {
        val px = brushPx / 2f
        return max(1, (px / cellPx.toFloat()).roundToInt())
    }

    private fun toCellX(x: Float, world: World): Int {
        if (dst.width() <= 0) return 0
        val f = (x - dst.left) / dst.width()
        return (f * world.width).toInt().coerceIn(0, world.width - 1)
    }

    private fun toCellY(y: Float, world: World): Int {
        if (dst.height() <= 0) return 0
        val f = (y - dst.top) / dst.height()
        return (f * world.height).toInt().coerceIn(0, world.height - 1)
    }

    /** Renders the current frame (with background) into a fresh bitmap for sharing. */
    fun exportBitmap(): Bitmap {
        val world = engine.world
        val bmp = Bitmap.createBitmap(world.width, world.height, Bitmap.Config.ARGB_8888)
        val composed = IntArray(world.n)
        synchronized(engine.renderLock) {
            world.paintPixels()
            world.composeBackground(composed)
        }
        bmp.setPixels(composed, 0, world.width, 0, 0, world.width, world.height)
        return bmp
    }

    /** Colour shown for the current brush, used by the UI cursor. */
    fun currentColor(): Int = El.ALL[element].color
}
