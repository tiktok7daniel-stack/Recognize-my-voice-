package com.daniel.assistant.data

import com.daniel.assistant.commands.CommandJson
import com.daniel.assistant.commands.CustomCommand
import com.daniel.assistant.commands.Step
import com.daniel.assistant.data.db.AliasDao
import com.daniel.assistant.data.db.AliasEntity
import com.daniel.assistant.data.db.CommandDao
import com.daniel.assistant.data.db.CommandEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class CommandRepository(private val dao: CommandDao, private val aliasDao: AliasDao) {

    private val stepsSer = ListSerializer(Step.serializer())
    private val stringsSer = ListSerializer(String.serializer())

    val commands: Flow<List<CustomCommand>> = dao.observeAll().map { list -> list.mapNotNull(::toDomain) }
    val aliases: Flow<List<AliasEntity>> = aliasDao.observeAll()

    suspend fun all(): List<CustomCommand> = dao.getAll().mapNotNull(::toDomain)
    suspend fun get(id: Long): CustomCommand? = dao.get(id)?.let(::toDomain)

    suspend fun save(c: CustomCommand): Long {
        val existing = if (c.id != 0L) dao.get(c.id) else null
        val entity = CommandEntity(
            id = c.id,
            name = c.name,
            enabled = c.enabled,
            triggersJson = CommandJson.json.encodeToString(stringsSer, c.triggers),
            stepsJson = CommandJson.json.encodeToString(stepsSer, c.steps),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            autoTime = c.autoTime,
            autoApp = c.autoApp,
            autoButton = c.autoButton,
        )
        val id = dao.upsert(entity)
        return if (c.id != 0L) c.id else id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun allAliases(): List<AliasEntity> = aliasDao.getAll()
    suspend fun addAlias(packageName: String, label: String, alias: String) =
        aliasDao.insert(AliasEntity(packageName = packageName, appLabel = label, alias = alias.trim()))
    suspend fun deleteAlias(a: AliasEntity) = aliasDao.delete(a)

    private fun toDomain(e: CommandEntity): CustomCommand? = runCatching {
        CustomCommand(
            id = e.id,
            name = e.name,
            enabled = e.enabled,
            triggers = CommandJson.json.decodeFromString(stringsSer, e.triggersJson),
            steps = CommandJson.json.decodeFromString(stepsSer, e.stepsJson),
            autoTime = e.autoTime,
            autoApp = e.autoApp,
            autoButton = e.autoButton,
        )
    }.getOrNull()
}
