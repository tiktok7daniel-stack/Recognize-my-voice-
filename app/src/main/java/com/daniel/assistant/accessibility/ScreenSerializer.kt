package com.daniel.assistant.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Serializes the visible accessibility tree into a compact numbered list for the AI. */
object ScreenSerializer {

    data class Snapshot(val text: String, val nodes: List<AccessibilityNodeInfo>, val packageName: String?)

    fun snapshot(root: AccessibilityNodeInfo?, maxNodes: Int = 150): Snapshot {
        val kept = mutableListOf<AccessibilityNodeInfo>()
        NodeActions.walk(root) { n ->
            if (!n.isVisibleToUser) return@walk
            val hasLabel = !n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank()
            if (hasLabel || n.isClickable || n.isEditable || n.isScrollable) kept.add(n)
        }
        val nodes = kept.take(maxNodes)
        val sb = StringBuilder()
        sb.append("app: ").append(root?.packageName ?: "?").append('\n')
        val r = Rect()
        nodes.forEachIndexed { i, n ->
            n.getBoundsInScreen(r)
            val cls = n.className?.toString()?.substringAfterLast('.') ?: "View"
            val flags = buildString {
                if (n.isClickable) append('C')
                if (n.isEditable) append('E')
                if (n.isScrollable) append('S')
                if (n.isFocused) append('F')
            }
            sb.append(i).append('|').append(cls).append('|')
                .append(clean(n.text)).append('|').append(clean(n.contentDescription)).append('|')
                .append(flags).append("|[").append(r.left).append(',').append(r.top).append(',')
                .append(r.right).append(',').append(r.bottom).append("]\n")
        }
        return Snapshot(sb.toString(), nodes, root?.packageName?.toString())
    }

    private fun clean(s: CharSequence?): String =
        s?.toString()?.replace('\n', ' ')?.replace('|', '/')?.take(80) ?: ""
}
