package jp.juggler.subwaytooter.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.TextView
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.coroutine.cancellationException
import jp.juggler.util.data.notEmpty
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.createColoredDrawable
import jp.juggler.util.ui.dismissSafe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("MatchingDeclarationName")
class ActionsDialogInitializer(
    val title: CharSequence? = null,
    // タグメニューなど、詰めて表示したい場合に真
    val compact: Boolean = false,
) {
    class Action(var caption: CharSequence, val action: suspend () -> Unit)

    private sealed class Item {
        class Single(val action: Action) : Item()
        class Group(val caption: CharSequence, val actions: ArrayList<Action>) : Item()
    }

    private val items = ArrayList<Item>()

    // 表示後に表題を差し替えられるようにする (タグ情報の取得待ちなど)
    private var onChange: (() -> Unit)? = null

    fun refresh() {
        onChange?.invoke()
    }

    fun action(caption: CharSequence, action: suspend () -> Unit): Action {
        val item = Action(caption, action)
        items.add(Item.Single(item))
        return item
    }

    inner class GroupScope internal constructor(
        private val actions: MutableList<Action>,
    ) {
        fun action(caption: CharSequence, action: suspend () -> Unit) {
            actions.add(Action(caption, action))
        }
    }

    fun group(caption: CharSequence, init: GroupScope.() -> Unit) {
        val actions = ArrayList<Action>()
        GroupScope(actions).init()
        if (actions.isNotEmpty()) items.add(Item.Group(caption, actions))
    }

    private class Row(
        val action: Action?,
        val group: Item.Group?,
        val indented: Boolean,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun showSuspend(context: Context): Action =
        suspendCancellableCoroutine { cont ->
            // グループも詰めた表示も不要なダイアログは、これまで通りフレームワークに
            // リストを作らせる。フレームワークはテーマの listItemLayout を使うので、
            // 自前で android.R.layout.select_dialog_item を inflate した場合のように
            // 行の高さや文字サイズが他のメニューとずれることがない。
            if (!compact && items.none { it is Item.Group }) {
                val actions = items.map { (it as Item.Single).action }
                val dialog = android.app.AlertDialog.Builder(context).apply {
                    title?.notEmpty()?.let { setTitle(it) }
                    setNegativeButton(android.R.string.cancel, null)
                    setItems(actions.map { it.caption }.toTypedArray()) { d, i ->
                        if (cont.isActive) cont.resume(actions[i]) { _, _, _ -> }
                        d.dismissSafe()
                    }
                    setOnDismissListener {
                        if (cont.isActive) cont.resumeWithException(cancellationException())
                    }
                }.create()
                cont.invokeOnCancellation { dialog.dismissSafe() }
                dialog.show()
                return@suspendCancellableCoroutine
            }

            val expanded = HashSet<Item.Group>()
            val alwaysExpand = PrefB.bpAlwaysExpandContextMenuItems.value
            if (alwaysExpand) expanded.addAll(items.filterIsInstance<Item.Group>())

            fun flatten(): ArrayList<Row> {
                val rows = ArrayList<Row>()
                for (item in items) {
                    when (item) {
                        is Item.Single -> rows.add(Row(item.action, null, false))
                        is Item.Group -> {
                            rows.add(Row(null, item, false))
                            if (item in expanded) {
                                for (action in item.actions) {
                                    rows.add(Row(action, null, true))
                                }
                            }
                        }
                    }
                }
                return rows
            }

            val inflater = LayoutInflater.from(context)
            val density = context.resources.displayMetrics.density
            fun dp(value: Float) = (value * density + 0.5f).toInt()

            val adapter = object : BaseAdapter() {
                private var rows = flatten()

                fun refresh() {
                    rows = flatten()
                    notifyDataSetChanged()
                }

                override fun getCount() = rows.size
                override fun getItem(position: Int): Any = rows[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getViewTypeCount() = 2

                override fun getItemViewType(position: Int) =
                    if (rows[position].group != null) 0 else 1

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = rows[position]
                    val group = row.group
                    if (group != null) {
                        val tv = convertView as? TextView ?: (
                            inflater.inflate(R.layout.dlg_action_group_header, parent, false)
                                as TextView
                            )
                        tv.layoutParams = AbsListView.LayoutParams(
                            AbsListView.LayoutParams.MATCH_PARENT,
                            AbsListView.LayoutParams.WRAP_CONTENT
                        )
                        tv.text = group.caption
                        tv.setPaddingRelative(dp(16f), dp(4f), dp(16f), dp(4f))
                        val iconId = when (group in expanded) {
                            true -> R.drawable.ic_arrow_drop_up
                            else -> R.drawable.ic_arrow_drop_down
                        }
                        val color = context.attrColor(R.attr.colorTimeSmall)
                        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            createColoredDrawable(context, iconId, color, 1f),
                            null,
                            null,
                            null
                        )
                        tv.compoundDrawablePadding = dp(8f)
                        return tv
                    }

                    val tv = convertView as? TextView ?: (
                        inflater.inflate(
                            when {
                                compact -> R.layout.dlg_compact_action_row
                                else -> android.R.layout.select_dialog_item
                            },
                            parent,
                            false
                        ) as TextView
                        )
                    if (compact) {
                        tv.layoutParams = AbsListView.LayoutParams(
                            AbsListView.LayoutParams.MATCH_PARENT,
                            AbsListView.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val basePaddingStart =
                        (tv.tag as? Int) ?: tv.paddingStart.also { tv.tag = it }
                    tv.text = row.action?.caption
                    tv.setPaddingRelative(
                        basePaddingStart + if (row.indented) dp(24f) else 0,
                        tv.paddingTop,
                        tv.paddingEnd,
                        tv.paddingBottom
                    )
                    return tv
                }
            }

            val dialog = android.app.AlertDialog.Builder(context).apply {
                title?.notEmpty()?.let { setTitle(it) }
                setNegativeButton(android.R.string.cancel, null)
                setAdapter(adapter) { _, _ -> }
                setOnDismissListener {
                    onChange = null
                    if (cont.isActive) cont.resumeWithException(cancellationException())
                }
            }.create()

            cont.invokeOnCancellation { dialog.dismissSafe() }
            dialog.show()
            onChange = { dialog.window?.decorView?.post { adapter.refresh() } }

            dialog.listView?.setOnItemClickListener { _, _, position, _ ->
                val row = adapter.getItem(position) as Row
                val group = row.group
                if (group != null) {
                    if (!alwaysExpand) {
                        if (!expanded.add(group)) expanded.remove(group)
                        adapter.refresh()
                    }
                } else {
                    if (cont.isActive) row.action?.let { cont.resume(it) { _, _, _ -> } }
                    dialog.dismissSafe()
                }
            }
        }
}

suspend fun Context.actionsDialog(
    title: String? = null,
    compact: Boolean = false,
    init: suspend ActionsDialogInitializer.() -> Unit,
) {
    ActionsDialogInitializer(title, compact)
        .apply { init() }
        .showSuspend(this)
        .action.invoke()
}
