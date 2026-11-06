package jp.juggler.subwaytooter.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    private val activity: Activity,
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
        lateinit var dialog: Dialog
        val density = activity.resources.displayMetrics.density
        fun dp(value: Float) = (value * density + 0.5f).toInt()

        fun select(action: Action) {
            if (cont.isActive) cont.resume(action) { _, _, _ -> }
            dialog.dismissSafe()
        }

        fun addAction(container: LinearLayout, action: Action) {
            val button = activity.layoutInflater.inflate(
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

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        }

        title?.notEmpty()?.let {
            val tv = TextView(activity).apply {
                text = it
                textSize = 12f
                setTextColor(activity.attrColor(R.attr.colorTimeSmall))
                setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            }
            list.addView(tv)
        }

        for (item in items) {
            when (item) {
                is Item.Single -> addAction(list, item.action)

                is Item.Group -> {
                    val groupView = activity.layoutInflater.inflate(
                        R.layout.dlg_grouped_action_group,
                        list,
                        false
                    )
                    val header = groupView.findViewById<Button>(R.id.btnGroupHeader)
                    val children = groupView.findViewById<LinearLayout>(R.id.llGroupChildren)
                    header.text = item.caption
                    for (action in item.actions) addAction(children, action)
                    setupGroup(header, children)
                    list.addView(groupView)
                }
            }
        }

        val scroll = ScrollView(activity).apply {
            addView(list)
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(View(activity).apply {
                setBackgroundColor(activity.attrColor(R.attr.colorTimeSmall))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1f)))
            val cancel = activity.layoutInflater.inflate(
                R.layout.dlg_grouped_action_row,
                this,
                false
            ) as Button
            cancel.text = activity.getString(android.R.string.cancel)
            cancel.setOnClickListener { dialog.dismissSafe() }
            addView(cancel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        dialog = Dialog(activity)
        dialog.setContentView(content)
        dialog.setOnDismissListener {
            if (cont.isActive) cont.resumeWithException(cancellationException())
        }
        cont.invokeOnCancellation { dialog.dismissSafe() }
        dialog.show()
        dialog.window?.setLayout(
            dp(280f),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupGroup(header: Button, group: View) {
        if (PrefB.bpAlwaysExpandContextMenuItems.value) {
            group.vg(true)
            header.background = null
        } else {
            group.vg(false)
            header.setOnClickListener {
                group.vg(group.visibility != View.VISIBLE)
                updateArrow(header, group)
            }
        }
        updateArrow(header, group)
    }

    private fun updateArrow(header: Button, group: View) {
        val iconId = when (group.visibility) {
            View.VISIBLE -> R.drawable.ic_arrow_drop_up
            else -> R.drawable.ic_arrow_drop_down
        }
        val color = activity.attrColor(R.attr.colorTimeSmall)
        val drawable = createColoredDrawable(activity, iconId, color, 1f)
        header.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
    }
}

suspend fun Context.groupedActionsDialog(
    title: CharSequence? = null,
    init: suspend GroupedActionsDialog.() -> Unit,
) {
    val activity = this as? Activity ?: return
    val dialog = GroupedActionsDialog(activity, title)
    dialog.init()
    dialog.show().action.invoke()
}
