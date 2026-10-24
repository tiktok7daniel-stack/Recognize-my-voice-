package com.daniel.assistant.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    /** JSON array of accepted trigger variants. */
    val triggersJson: String,
    /** JSON array of [com.daniel.assistant.commands.Step]. */
    val stepsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "aliases", indices = [Index("packageName")])
data class AliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val alias: String,
)

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    /** "info" | "ok" | "error" */
    val level: String,
    val message: String,
)
