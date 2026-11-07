package jp.juggler.subwaytooter.columnviewholder

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.action.conversationLocal
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.databinding.ItemMediaCellBinding
import jp.juggler.util.ui.vg

class ViewHolderMediaCell(
    private val activity: ActMain,
    parent: ViewGroup,
) : RecyclerView.ViewHolder(
    ItemMediaCellBinding.inflate(activity.layoutInflater, parent, false).root
) {
    private val views = ItemMediaCellBinding.bind(itemView)

    fun bindData(column: Column, status: TootStatus) {
        val attachments = status.media_attachments
        val attachment = attachments?.firstOrNull()

        views.ivMedia.setImageUrl(
            0f,
            urlStatic = attachment?.urlForThumbnail(),
        )

        val rest = (attachments?.size ?: 1) - 1
        views.tvMediaCount.text = if (rest > 0) "+$rest" else null
        views.tvMediaCount.vg(rest > 0)

        itemView.setOnClickListener {
            activity.conversationLocal(
                pos = activity.nextPosition(column),
                accessInfo = column.accessInfo,
                statusId = status.id,
            )
        }
    }
}
