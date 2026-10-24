package com.daniel.assistant.commands

import kotlinx.serialization.Serializable

/** JSON file format for import / export of commands and app aliases. */
@Serializable
data class ExportBundle(
    val version: Int = 1,
    val app: String = "Nabra",
    val commands: List<ExportCommand> = emptyList(),
    val aliases: List<ExportAlias> = emptyList(),
)

@Serializable
data class ExportCommand(
    val name: String,
    val enabled: Boolean = true,
    val triggers: List<String>,
    val steps: List<Step>,
)

@Serializable
data class ExportAlias(val packageName: String, val appLabel: String = "", val alias: String)

object ImportExport {
    fun encode(commands: List<CustomCommand>, aliases: List<ExportAlias>): String =
        CommandJson.pretty.encodeToString(
            ExportBundle.serializer(),
            ExportBundle(
                commands = commands.map { ExportCommand(it.name, it.enabled, it.triggers, it.steps) },
                aliases = aliases,
            ),
        )

    fun decode(text: String): ExportBundle = CommandJson.json.decodeFromString(ExportBundle.serializer(), text)
}
