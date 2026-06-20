package com.platinum.vip.hasiba

import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.UUID

object FirebaseHistoryHelper {
    private var database: FirebaseDatabase? = null
    private const val PREFS_NAME = "AppPrefs"
    private const val KEY_FIREBASE_USER = "firebase_user_id"

    private fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var uid = prefs.getString(KEY_FIREBASE_USER, null)
        if (uid == null) {
            uid = UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_FIREBASE_USER, uid).apply()
        }
        return uid
    }

    fun addHistory(context: Context, expression: String, result: String) {
        try {
            if (database == null) {
                database = Firebase.database
                database?.setPersistenceEnabled(false)
            }
            val userId = getUserId(context)
            val ref = database?.getReference("history")?.child(userId)
            val item = mapOf(
                "expression" to expression,
                "result" to result,
                "timestamp" to System.currentTimeMillis()
            )
            ref?.push()?.setValue(item)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}