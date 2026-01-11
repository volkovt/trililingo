package com.trililingo.python

import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject

class PythonSrsBridge {

    private var ready = false

    private fun ensure() {
        if (ready) return
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(AppContextHolder.appContext))
        }
        ready = true
    }

    fun updateState(
        stateJson: String,
        isCorrect: Boolean,
        nowMs: Long,
        responseMs: Long
    ): String {
        ensure()
        val py = Python.getInstance()
        val mod = py.getModule("srs")
        return mod.callAttr("update_state", stateJson, isCorrect, nowMs, responseMs).toString()
    }

    fun pickDueItems(itemsJson: String, nowMs: Long, limit: Int): String {
        ensure()
        val py = Python.getInstance()
        val mod = py.getModule("srs")
        return mod.callAttr("pick_due_items", itemsJson, nowMs, limit).toString()
    }
}

/**
 * Pequeno holder pra evitar injetar Context no bridge (mantém chamada simples).
 * Inicialize em TrililingoApp (ver abaixo).
 */
object AppContextHolder {
    lateinit var appContext: android.content.Context
}
