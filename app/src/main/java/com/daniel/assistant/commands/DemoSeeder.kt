package com.daniel.assistant.commands

import com.daniel.assistant.AppContainer

/** Creates the demo command "واحد" once: attach last 2 photos to Claude, wait, type "حلهم", send. */
object DemoSeeder {
    suspend fun seedIfNeeded(c: AppContainer) {
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
                    Step.AttachPhotos(count = 2, packageName = pkg, label = label),
                    Step.Wait(1500),
                    Step.TypeText("حلهم"),
                    Step.PressSend,
                ),
            )
        )
        c.settings.setDemoSeeded(true)
    }
}
