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

    fun bindData(column: Column, status: TootStatus, attachmentIndex: Int) {
        val attachments = (status.reblog ?: status).media_attachments
        val attachment = attachments?.getOrNull(attachmentIndex)

        views.ivMedia.setImageUrl(
            0f,
            urlStatic = attachment?.urlForThumbnail(),
        )

        val rest = (attachments?.size ?: 1) - attachmentIndex - 1
        val showRest = !column.mediaSplitAttachments && rest > 0
        views.tvMediaCount.text = if (showRest) "+$rest" else null
        views.tvMediaCount.vg(showRest)

        itemView.setOnClickListener {
            activity.conversationLocal(
                pos = activity.nextPosition(column),
                accessInfo = column.accessInfo,
                statusId = status.id,
            )
        }
    }
}
