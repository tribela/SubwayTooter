package jp.juggler.subwaytooter.itemviewholder

import android.text.SpannableStringBuilder
import android.view.View
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.calcIconRound
import jp.juggler.subwaytooter.table.daoMediaShown
import jp.juggler.util.ui.vg

// 引用された投稿をインラインのブロックで表示する (Mastodon 4.4+)
fun ItemViewHolder.showQuotedStatus(status: TootStatus) {

    val quoted = status.quote
    if (quoted == null) {
        llQuoted.visibility = View.GONE
        ivQuotedThumbnails.forEach {
            it.setImageUrl(0f, null)
            it.visibility = View.GONE
        }
        quotedNameInvalidator.text = ""
        quotedContentInvalidator.text = ""
        return
    }

    llQuoted.visibility = View.VISIBLE

    val whoRef = quoted.accountRef
    val who = whoRef.get()

    ivQuotedAvatar.setImageUrl(
        calcIconRound(ivQuotedAvatar.layoutParams),
        accessInfo.supplyBaseUrl(who.avatar_static),
        accessInfo.supplyBaseUrl(who.avatar),
    )

    quotedNameInvalidator.text = whoRef.decoded_display_name
    setAcct(tvQuotedAcct, accessInfo, who)
    tvQuotedTime.setTextColor(acctColor)
    showStatusTime(activity, tvQuotedTime, who, quoted)

    val spoiler = quoted.decoded_spoiler_text
    quotedContentInvalidator.text = when {
        spoiler.isNotEmpty() ->
            SpannableStringBuilder()
                .append(activity.getString(R.string.quoted_post_content_warning, spoiler))

        else -> quoted.decoded_content
    }
    tvQuotedContent.vg(spoiler.isNotEmpty() || quoted.decoded_content.isNotEmpty())

    showQuotedAttachments(quoted, hideMedia = spoiler.isNotEmpty())
}

private fun ItemViewHolder.showQuotedAttachments(quoted: TootStatus, hideMedia: Boolean) {
    val list = quoted.media_attachments
    val shown = when {
        hideMedia -> false
        list.isNullOrEmpty() -> false
        else -> {
            // メディアを隠す設定やNSFW指定を尊重する
            val defaultShown = when {
                column.hideMediaDefault -> false
                accessInfo.dontHideNsfw -> true
                else -> !quoted.sensitive
            }
            daoMediaShown.isShown(quoted, defaultShown)
        }
    }

    llQuotedMedia.vg(shown)

    val visibleCount = if (shown) {
        list.orEmpty().take(ItemViewHolder.QUOTED_MEDIA_VIEW_COUNT)
            .count { !it.urlForThumbnail().isNullOrEmpty() }
    } else {
        0
    }

    ivQuotedThumbnails.forEachIndexed { idx, iv ->
        val url = if (shown) list?.getOrNull(idx)?.urlForThumbnail() else null
        val lp = iv.layoutParams as android.widget.LinearLayout.LayoutParams
        if (visibleCount == 1) {
            lp.width = lp.height
            lp.weight = 0f
        } else {
            lp.width = 0
            lp.weight = 1f
        }
        iv.layoutParams = lp
        if (url.isNullOrEmpty()) {
            iv.setImageUrl(0f, null)
            iv.visibility = View.GONE
        } else {
            iv.visibility = View.VISIBLE
            iv.setImageUrl(0f, accessInfo.supplyBaseUrl(url), accessInfo.supplyBaseUrl(url))
        }
    }
}
