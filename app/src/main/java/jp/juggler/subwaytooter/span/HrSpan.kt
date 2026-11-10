package jp.juggler.subwaytooter.span

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import jp.juggler.subwaytooter.pref.lazyContext
import kotlin.math.max
import kotlin.math.min

/**
 * 水平線のスパン。線の色は描画時の本文色から取るので、どのテーマでも見える。
 */
class HrSpan(
    context: Context = lazyContext,
    private var lineHeight: Int = (context.resources.displayMetrics.density * 1f + 0.5f).toInt(),
) : MetricAffectingSpan(), LeadingMarginSpan {

    private val rect = Rect()
    private val paint = Paint()

    private fun apply(paint: TextPaint) {
        paint.color = 0
    }

    override fun updateDrawState(tp: TextPaint) {
        apply(tp)
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        apply(textPaint)
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return 0
    }

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence?,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = p.color

        val lineY = (top + bottom).shr(1)
        val lineT = lineY - lineHeight.shr(1)
        val lineB = lineT + lineHeight

        val line = layout.getLineForOffset(start)
        val left = layout.getParagraphLeft(line)
        val right = layout.getParagraphRight(line)
        rect.set(min(left, right), lineT, max(left, right), lineB)
        c.drawRect(rect, paint)
    }
}
