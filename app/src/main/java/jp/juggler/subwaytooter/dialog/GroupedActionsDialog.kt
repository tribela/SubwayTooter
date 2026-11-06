package jp.juggler.subwaytooter.dialog

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.coroutine.cancellationException
import jp.juggler.util.data.notEmpty
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.createColoredDrawable
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.vg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GroupedActionsDialog(
    private val context: Context,
    private val title: CharSequence? = null,
) {
    class Action(val caption: CharSequence, val action: suspend () -> Unit)

    private sealed class Item {
        class Single(val action: Action) : Item()
        class Group(val caption: CharSequence, val actions: ArrayList<Action>) : Item()
    }

    private val items = ArrayList<Item>()

    fun action(caption: CharSequence, action: suspend () -> Unit) {
        items.add(Item.Single(Action(caption, action)))
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

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun show(): Action = suspendCancellableCoroutine { cont ->
        val inflater = (context as? Activity)?.layoutInflater
            ?: android.view.LayoutInflater.from(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        val scroll = ScrollView(context)

        lateinit var dialog: android.app.AlertDialog

        fun select(action: Action) {
            if (cont.isActive) cont.resume(action) { _, _, _ -> }
            dialog.dismissSafe()
        }

        fun addAction(container: LinearLayout, action: Action) {
            val button = inflater.inflate(
                R.layout.dlg_grouped_action_row,
                container,
                false
            ) as Button
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            button.text = action.caption
            button.setOnClickListener { select(action) }
            container.addView(button)
        }

        for (item in items) {
            when (item) {
                is Item.Single -> addAction(root, item.action)

                is Item.Group -> {
                    val groupView = inflater.inflate(
                        R.layout.dlg_grouped_action_group,
                        root,
                        false
                    )
                    val header = groupView.findViewById<Button>(R.id.btnGroupHeader)
                    val children = groupView.findViewById<LinearLayout>(R.id.llGroupChildren)
                    header.text = item.caption
                    for (action in item.actions) addAction(children, action)
                    setupGroup(header, children)
                    root.addView(groupView)
                }
            }
        }

        scroll.addView(
            root,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        dialog = android.app.AlertDialog.Builder(context)
            .setTitle(title?.notEmpty())
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnDismissListener {
            if (cont.isActive) cont.resumeWithException(cancellationException())
        }
        cont.invokeOnCancellation { dialog.dismissSafe() }
        dialog.show()
    }

    private fun setupGroup(header: Button, group: View) {
        if (PrefB.bpAlwaysExpandContextMenuItems.value) {
            group.vg(true)
            header.background = null
        } else {
            group.vg(false)
            header.setOnClickListener { updateGroup(header, group, toggle = true) }
        }
        updateGroupArrow(header, group)
    }

    private fun updateGroup(header: Button, group: View, toggle: Boolean = false) {
        if (toggle) group.vg(group.visibility != View.VISIBLE)
        updateGroupArrow(header, group)
    }

    private fun updateGroupArrow(header: Button, group: View) {
        val iconId = when (group.visibility) {
            View.VISIBLE -> R.drawable.ic_arrow_drop_up
            else -> R.drawable.ic_arrow_drop_down
        }
        val color = context.attrColor(R.attr.colorTimeSmall)
        val drawable = createColoredDrawable(context, iconId, color, 1f)
        header.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
    }

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}

suspend fun Context.groupedActionsDialog(
    title: CharSequence? = null,
    init: suspend GroupedActionsDialog.() -> Unit,
) {
    val dialog = GroupedActionsDialog(this, title)
    dialog.init()
    dialog.show().action.invoke()
}
