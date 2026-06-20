package com.platinum.vip.hasiba

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val UPDATE_URL = "https://raw.githubusercontent.com/obadaok/hassaba/main/update.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val fileSize: Long,
        val changelog: String,
        val mandatory: Boolean,
        val releaseDate: String = ""
    )

    suspend fun checkForUpdate(context: Context): UpdateInfo? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = URL(UPDATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)

            val serverVersionCode = json.getInt("versionCode")
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }

            if (serverVersionCode > currentVersionCode) {
                UpdateInfo(
                    versionCode = serverVersionCode,
                    versionName = json.getString("versionName"),
                    downloadUrl = json.getString("downloadUrl"),
                    fileSize = json.getLong("fileSize"),
                    changelog = json.getString("changelog"),
                    mandatory = json.optBoolean("mandatory", false),
                    releaseDate = json.optString("releaseDate", "")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun openOnGooglePlay(context: Context) {
        val packageName = context.packageName
        try {
            // محاولة فتح تطبيق Google Play
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // إذا لم يكن تطبيق Play موجوداً، افتح المتصفح
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.ENGLISH, "%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.ENGLISH, "%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(java.util.Locale.ENGLISH, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}