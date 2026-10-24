package com.daniel.assistant.data

import android.util.Log
import com.daniel.assistant.data.db.LogDao
import com.daniel.assistant.data.db.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Simple persistent log of executed commands and errors (shown in the Logs screen). */
class ExecLog(private val dao: LogDao, private val scope: CoroutineScope) {
    val recent = dao.observeRecent()

    fun info(msg: String) = write("info", msg)
    fun ok(msg: String) = write("ok", msg)
    fun error(msg: String) = write("error", msg)

    private fun write(level: String, msg: String) {
        Log.d("Nabra", "[$level] $msg")
        scope.launch {
            dao.insert(LogEntity(level = level, message = msg))
            dao.trim()
        }
    }

    fun clear() = scope.launch { dao.clear() }
}
