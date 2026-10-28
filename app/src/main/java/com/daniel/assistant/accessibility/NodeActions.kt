package com.daniel.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.daniel.assistant.voice.ArabicNormalizer
import kotlin.math.hypot

/** Helpers for finding and acting on accessibility nodes. */
object NodeActions {

    private val SEND_WORDS = listOf(
        "send", "إرسال", "ارسال", "أرسل", "ارسل", "invia", "envoyer", "enviar", "senden", "submit",
        "send message", "إرسال رسالة", "arrow_up", "btn_send", "send_button",
    )

    fun walk(root: AccessibilityNodeInfo?, visit: (AccessibilityNodeInfo) -> Unit) {
        root ?: return
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var guard = 0
        while (stack.isNotEmpty() && guard++ < 3000) {
            val n = stack.removeLast()
            visit(n)
            for (i in n.childCount - 1 downTo 0) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
    }

    fun visibleNodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        walk(root) { if (it.isVisibleToUser) out.add(it) }
        return out
    }

    private fun matches(value: CharSequence?, query: String, exact: Boolean): Boolean {
        if (value.isNullOrBlank()) return false
        val v = ArabicNormalizer.normalize(value.toString())
        val q = ArabicNormalizer.normalize(query)
        if (q.isEmpty()) return false
        return if (exact) v == q else v.contains(q)
    }

    fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        val nodes = visibleNodes(root)
        return nodes.firstOrNull { matches(it.text, text, true) }
            ?: nodes.firstOrNull { matches(it.text, text, false) }
            ?: findNodeByDescription(root, text)
    }

    fun findNodeByDescription(root: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        val nodes = visibleNodes(root)
        return nodes.firstOrNull { matches(it.contentDescription, desc, true) }
            ?: nodes.firstOrNull { matches(it.contentDescription, desc, false) }
            ?: nodes.firstOrNull { matches(it.hintText, desc, false) }
    }

    fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (it.isEditable) return it }
        return visibleNodes(root).firstOrNull { it.isEditable }
    }

    /** Finds a send button by common labels, falling back to the clickable node nearest the input field. */
    fun findSendButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val nodes = visibleNodes(root)
        fun isSend(n: AccessibilityNodeInfo): Boolean {
            val labels = listOfNotNull(n.contentDescription, n.text, n.viewIdResourceName?.substringAfterLast('/'))
            return labels.any { l ->
                val s = ArabicNormalizer.normalize(l.toString())
                SEND_WORDS.any { w -> s.contains(ArabicNormalizer.normalize(w)) }
            }
        }
        nodes.firstOrNull { !it.isEditable && isSend(it) && (it.isClickable || clickableParent(it) != null) }
            ?.let { return it }

        val edit = findEditableNode(root) ?: return null
        val er = Rect().also { edit.getBoundsInScreen(it) }
        return nodes
            .filter { it.isClickable && !it.isEditable && it != edit }
            .map { n -> n to Rect().also { n.getBoundsInScreen(it) } }
            .filter { (_, r) -> r.width() > 0 && r.centerX() >= er.centerX() && r.top >= er.top - er.height() * 2 }
            .minByOrNull { (_, r) ->
                hypot((r.centerX() - er.right).toDouble(), (r.centerY() - er.centerY()).toDouble())
            }?.first
    }

    fun clickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth++ < 8) {
            if (cur.isClickable) return cur
            cur = cur.parent
        }
        return null
    }

    /** Clicks the node or its nearest clickable parent; falls back to a tap gesture. */
    fun clickNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        clickableParent(node)?.let { if (it.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true }
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r.isEmpty) return false
        return tap(service, r.exactCenterX(), r.exactCenterY())
    }

    fun tap(service: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return service.dispatchGesture(g, null, null)
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private val SEARCH_WORDS = listOf("search", "بحث", "ابحث", "cerca", "recherche", "buscar", "suche")

    /** A search icon / box in a chat app. */
    fun findSearch(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val nodes = visibleNodes(root)
        return nodes.firstOrNull { n ->
            val labels = listOfNotNull(n.contentDescription, n.text, n.hintText, n.viewIdResourceName?.substringAfterLast('/'))
            labels.any { l -> val s = l.toString().lowercase(); SEARCH_WORDS.any { s.contains(it) } } &&
                (n.isClickable || n.isEditable || clickableParent(n) != null)
        }
    }

    /** The visible (non-editable) node whose text best matches [name] — e.g. a contact in search results. */
    fun findBestTextMatch(root: AccessibilityNodeInfo?, name: String, minScore: Double = 0.6): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestScore = minScore
        visibleNodes(root).forEach { n ->
            if (n.isEditable) return@forEach
            val t = n.text?.toString()?.takeIf { it.isNotBlank() } ?: n.contentDescription?.toString() ?: return@forEach
            if (t.length > 60) return@forEach
            val sc = maxOf(
                com.daniel.assistant.voice.FuzzyMatcher.score(name, t),
                com.daniel.assistant.voice.FuzzyMatcher.score(name, com.daniel.assistant.voice.Transliterator.toArabic(t)),
            )
            if (sc > bestScore) { bestScore = sc; best = n }
        }
        return best
    }

    private val ATTACH_WORDS = listOf(
        "attach", "add", "plus", "upload", "media", "photo", "image", "gallery", "file",
        "ارفاق", "مرفق", "اضافه", "اضف", "صور", "صوره", "allega", "aggiungi", "carica",
    )
    private val ATTACH_EXCLUDE = listOf("send", "ارسال", "camera", "كاميرا", "voice", "mic", "صوت", "invia", "fotocamera")

    private fun labels(n: AccessibilityNodeInfo): String = listOfNotNull(
        n.contentDescription, n.text, n.viewIdResourceName?.substringAfterLast('/'),
    ).joinToString(" ") { ArabicNormalizer.normalize(it.toString()) }

    /** The "+" / attach button of a chat composer (closest to the text field). */
    fun findAttachButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val nodes = visibleNodes(root)
        val edit = findEditableNode(root)
        val er = edit?.let { Rect().also { r -> it.getBoundsInScreen(r) } }
        return nodes.filter { n ->
            val l = labels(n)
            !n.isEditable && (n.isClickable || clickableParent(n) != null) &&
                ATTACH_WORDS.any { l.contains(it) } && ATTACH_EXCLUDE.none { l.contains(it) }
        }.minByOrNull { n ->
            if (er == null) 0.0 else {
                val r = Rect(); n.getBoundsInScreen(r)
                hypot((r.centerX() - er.centerX()).toDouble(), (r.centerY() - er.centerY()).toDouble())
            }
        }
    }

    /** First clickable node whose label contains one of [words] (normalized). */
    fun findByWords(root: AccessibilityNodeInfo?, words: List<String>, exclude: List<String> = emptyList()): AccessibilityNodeInfo? {
        val w = words.map { ArabicNormalizer.normalize(it) }
        val ex = exclude.map { ArabicNormalizer.normalize(it) }
        return visibleNodes(root).firstOrNull { n ->
            val l = labels(n)
            l.isNotBlank() && w.any { l == it || l.split(' ').contains(it) || (it.length > 4 && l.contains(it)) } &&
                ex.none { l.contains(it) } && (n.isClickable || clickableParent(n) != null)
        }
    }

    /**
     * Photo thumbnails in a picker grid, newest first (accessibility order follows the
     * picker's own order, which is newest-first in RTL and LTR alike).
     */
    fun findThumbnails(root: AccessibilityNodeInfo?, screenWidth: Int): List<AccessibilityNodeInfo> {
        val photoWords = listOf("photo", "image", "picture", "صوره", "صور", "foto", "immagine", "taken", "التقطت", "video")
        val out = mutableListOf<AccessibilityNodeInfo>()
        val r = Rect()
        walk(root) { n ->
            if (!n.isVisibleToUser) return@walk
            n.getBoundsInScreen(r)
            val w = r.width(); val h = r.height()
            if (w < screenWidth * 0.14 || w > screenWidth * 0.6 || h == 0) return@walk
            val ratio = w.toFloat() / h
            if (ratio < 0.7f || ratio > 1.45f) return@walk
            val l = labels(n)
            val looksPhoto = photoWords.any { l.contains(it) } ||
                (n.className?.toString()?.contains("Image") == true && n.text.isNullOrBlank())
            if (looksPhoto && (n.isClickable || n.isCheckable || clickableParent(n) != null)) {
                val target = if (n.isClickable || n.isCheckable) n else clickableParent(n)!!
                if (out.none { it == target }) out.add(target)
            }
        }
        return out
    }

    /** First on/off switch on screen (Wi-Fi / Bluetooth panels). */
    fun findSwitch(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        visibleNodes(root).firstOrNull { n ->
            val cls = n.className?.toString().orEmpty()
            n.isCheckable && (cls.contains("Switch") || cls.contains("Toggle") || cls.contains("CheckBox"))
        } ?: visibleNodes(root).firstOrNull { it.isCheckable }

    /** Puts [text] on the clipboard and pastes it into [node]. */
    fun pasteText(context: android.content.Context, node: AccessibilityNodeInfo, text: String): Boolean {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("nabra", text))
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    /** Triggers the keyboard's action (send / enter) on a text field — Android 11+. */
    fun imeEnter(node: AccessibilityNodeInfo): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 30) return false
        return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    /** Horizontal swipe across the middle of the screen (stories, pages, tabs). */
    fun swipeHorizontal(service: AccessibilityService, toLeft: Boolean): Boolean {
        val dm = service.resources.displayMetrics
        val y = dm.heightPixels * 0.5f
        val (x1, x2) = if (toLeft) dm.widthPixels * 0.85f to dm.widthPixels * 0.15f else dm.widthPixels * 0.15f to dm.widthPixels * 0.85f
        val path = Path().apply { moveTo(x1, y); lineTo(x2, y) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 250)).build()
        return service.dispatchGesture(g, null, null)
    }

    /** Scrolls the largest scrollable container; falls back to a swipe. */
    fun scroll(service: AccessibilityService, down: Boolean): Boolean {
        val root = runCatching { service.rootInActiveWindow }.getOrNull()
        val scrollables = visibleNodes(root).filter { it.isScrollable }
        val target = scrollables.maxByOrNull { n ->
            val r = Rect(); n.getBoundsInScreen(r); r.width() * r.height()
        }
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (target != null && target.performAction(action)) return true
        val dm = service.resources.displayMetrics
        val x = dm.widthPixels / 2f
        val (y1, y2) = if (down) dm.heightPixels * 0.75f to dm.heightPixels * 0.3f
        else dm.heightPixels * 0.3f to dm.heightPixels * 0.75f
        val path = Path().apply { moveTo(x, y1); lineTo(x, y2) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return service.dispatchGesture(g, null, null)
    }
}
