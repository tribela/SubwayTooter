package jp.juggler.subwaytooter.span

import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import jp.juggler.util.ui.FontSpan

class InlineCodeSpan(
    tf: Typeface = Typeface.MONOSPACE,
    private val bgAlpha: Int = 0x22,
) : FontSpan(tf) {
    override fun updateDrawState(drawState: TextPaint) {
        super.updateDrawState(drawState)
        drawState.bgColor = ColorUtils.setAlphaComponent(drawState.color, bgAlpha)
    }
}
