package com.daniel.assistant.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {
    @Query("SELECT * FROM commands ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CommandEntity>>

    @Query("SELECT * FROM commands ORDER BY createdAt ASC")
    suspend fun getAll(): List<CommandEntity>

    @Query("SELECT * FROM commands WHERE id = :id")
    suspend fun get(id: Long): CommandEntity?

    @Upsert
    suspend fun upsert(entity: CommandEntity): Long

    @Query("UPDATE commands SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM commands WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AliasDao {
    @Query("SELECT * FROM aliases ORDER BY appLabel ASC")
    fun observeAll(): Flow<List<AliasEntity>>

    @Query("SELECT * FROM aliases")
    suspend fun getAll(): List<AliasEntity>

    @Insert
    suspend fun insert(entity: AliasEntity): Long

    @Delete
    suspend fun delete(entity: AliasEntity)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY time DESC LIMIT 500")
    fun observeRecent(): Flow<List<LogEntity>>

    @Insert
    suspend fun insert(entity: LogEntity)

    @Query("DELETE FROM logs")
    suspend fun clear()

    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY time DESC LIMIT 500)")
    suspend fun trim()
}
