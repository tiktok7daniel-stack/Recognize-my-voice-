package com.daniel.assistant.commands

import com.daniel.assistant.AppContainer

/** Creates the demo command "واحد" once: attach last 2 photos to Claude, wait, type "حلهم", send. */
object DemoSeeder {
    const val DEMO_TEXT = "حلهم بالترتيب بالإيطالي"
    private const val OLD_DEMO_TEXT = "حلهم"

    suspend fun seedIfNeeded(c: AppContainer) {
        migrateDemoText(c)
        if (c.settings.current().demoSeeded) return
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
                    Step.AttachPhotos(count = 2, packageName = pkg, label = label, text = DEMO_TEXT),
                    Step.Wait(1500),
                    Step.TypeText(DEMO_TEXT),
                    Step.PressSend,
                ),
            )
        )
        c.settings.setDemoSeeded(true)
        c.settings.setDemoTextV2(true)
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
