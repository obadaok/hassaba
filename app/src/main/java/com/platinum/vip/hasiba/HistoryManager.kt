package com.platinum.vip.hasiba

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class HistoryItem(val expression: String, val result: String, val timestamp: Long)

object HistoryManager {
    private const val PREFS_NAME = "HistoryPrefs"
    private const val KEY_HISTORY = "calc_history"

    // استخدمنا null كدليل لمعرفة ما إذا تم تحميل السجل أم لا بعد فتح التطبيق
    private var historyList: MutableList<HistoryItem>? = null

    // هذه الدالة السحرية تضمن عدم مسح السجل القديم أبداً
    private fun ensureLoaded(context: Context) {
        if (historyList == null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_HISTORY, null)
            if (json != null) {
                val type = object : TypeToken<MutableList<HistoryItem>>() {}.type
                historyList = Gson().fromJson(json, type) ?: mutableListOf()
            } else {
                historyList = mutableListOf()
            }
        }
    }

    fun add(context: Context, expr: String, res: String) {
        ensureLoaded(context)
        historyList?.add(0, HistoryItem(expr, res, System.currentTimeMillis()))
        saveToPrefs(context)
        FirebaseHistoryHelper.addHistory(context, expr, res)
    }

    fun getAll(context: Context): List<HistoryItem> {
        ensureLoaded(context) // تأكد من التحميل قبل العرض
        return historyList ?: emptyList()
    }

    fun remove(context: Context, item: HistoryItem) {
        ensureLoaded(context)
        historyList?.remove(item)
        saveToPrefs(context)
    }

    fun clear(context: Context) {
        ensureLoaded(context)
        historyList?.clear()
        saveToPrefs(context)
    }

    private fun saveToPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(historyList)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }
}