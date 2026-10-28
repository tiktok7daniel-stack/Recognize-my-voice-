package com.daniel.assistant.commands

import com.daniel.assistant.AppContainer

/** Creates the demo command "واحد" once: attach last 2 photos to Claude, wait, type "حلهم", send. */
object DemoSeeder {
    const val DEMO_TEXT = "حلهم بالترتيب بالإيطالي"
    private const val OLD_DEMO_TEXT = "حلهم"
    /** The user's "رياضة" chat in Claude: "واحد" sends the photos there instead of a new chat. */
    const val MATH_CHAT_URL = "https://claude.ai/chat/33c15ca8-87ea-4824-aae8-c09a8c39cd0a"

    suspend fun seedIfNeeded(c: AppContainer) {
        migrateDemoText(c)
        if (c.settings.current().demoSeeded) {
            migrateDemoWait(c)
            migrateToMathChat(c)
            cleanUpOne(c)
            return
        }
        val apps = c.appEntries()
        // Resolve Claude at runtime instead of trusting a hardcoded package name.
        val claude = apps.firstOrNull { it.packageName == "com.anthropic.claude" }
            ?: CommandResolver().findApp("Claude", apps)?.app
        val pkg = claude?.packageName ?: ""
        val label = claude?.label ?: "Claude"
        c.repo.save(
            CustomCommand(
                name = "واحد — صور لكلود",
                triggers = listOf("واحد"),
                steps = listOf(
                    Step.AttachPhotos(count = 2, packageName = pkg.ifBlank { "com.anthropic.claude" }, label = label, chatUrl = MATH_CHAT_URL),
                    Step.Wait(300),
                    Step.TypeText(DEMO_TEXT),
                    Step.PressSend,
                ),
            )
        )
        c.settings.setDemoSeeded(true)
        c.settings.setDemoTextV2(true)
        c.settings.setDemoV3(true)
        c.settings.setDemoV4(true)
        c.settings.setDemoV5(true)
        c.settings.setDemoV6(true)
    }

    /**
     * "واحد" = open the رياضة chat + share the 2 photos (once), short pause, type, send.
     * Removes leftovers from earlier versions (extra "attach in chat", duplicate open steps).
     */
    private suspend fun cleanUpOne(c: AppContainer) {
        if (c.settings.current().demoV6) return
        c.repo.all().filter { "واحد" in it.triggers }.forEach { cmd ->
            val share = cmd.steps.filterIsInstance<Step.AttachPhotos>().firstOrNull() ?: return@forEach
            val url = share.chatUrl ?: return@forEach
            val fixed = share.copy(viaPicker = false, text = null)
            val rest = cmd.steps.filterNot { st ->
                st is Step.AttachPhotos || st is Step.AttachInChat ||
                    (st is Step.OpenUrl && st.url.trim() == url.trim()) ||
                    (st is Step.OpenApp && st.packageName == share.packageName)
            }
            val steps = mutableListOf<Step>(fixed)
            if (rest.firstOrNull() !is Step.Wait) steps.add(Step.Wait(300))
            steps.addAll(rest)
            c.repo.save(cmd.copy(steps = steps))
        }
        c.settings.setDemoV6(true)
    }

    /** "واحد" → the existing "رياضة" chat (no new chat), and skip the separate "open Claude" step. */
    private suspend fun migrateToMathChat(c: AppContainer) {
        if (c.settings.current().demoV5) return
        c.repo.all().filter { "واحد" in it.triggers }.forEach { cmd ->
            val share = cmd.steps.firstOrNull { it is Step.AttachPhotos && it.chatUrl.isNullOrBlank() && it.label.contains("Claude", true) }
                as? Step.AttachPhotos ?: return@forEach
            val steps = cmd.steps
                .filterNot { it is Step.OpenApp && it.packageName == share.packageName }
                .map { if (it == share) share.copy(chatUrl = MATH_CHAT_URL, text = null) else it }
            c.repo.save(cmd.copy(steps = steps))
        }
        c.settings.setDemoV5(true)
    }

    /**
     * Faster demo: typing waits for the text field and "send" waits until the button is
     * enabled (photos uploaded), so the fixed pause can be tiny: 1.5 s / 0.8 s → 0.3 s.
     */
    private suspend fun migrateDemoWait(c: AppContainer) {
        if (c.settings.current().demoV4) return
        val old = setOf(Step.Wait(1500), Step.Wait(800))
        c.repo.all().filter { "واحد" in it.triggers }.forEach { cmd ->
            if (cmd.steps.any { it in old }) {
                c.repo.save(cmd.copy(steps = cmd.steps.map { if (it in old) Step.Wait(300) else it }))
            }
        }
        c.settings.setDemoV3(true)
        c.settings.setDemoV4(true)
    }

    /** Updates the demo created by older versions ("حلهم") to the new text, unless the user edited it. */
    private suspend fun migrateDemoText(c: AppContainer) {
        val s = c.settings.current()
        if (!s.demoSeeded || s.demoTextV2) return
        c.repo.all().filter { "واحد" in it.triggers }.forEach { cmd ->
            if (cmd.steps.any { it == Step.TypeText(OLD_DEMO_TEXT) }) {
                c.repo.save(cmd.copy(steps = cmd.steps.map { if (it == Step.TypeText(OLD_DEMO_TEXT)) Step.TypeText(DEMO_TEXT) else it }))
            }
        }
        c.settings.setDemoTextV2(true)
    }
}
