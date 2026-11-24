package com.fms.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.io.File

/**
 * FMSPreferenceEngine: Platform-native user preferences engine (no DataStore dependency required).
 */
class FMSPreferenceEngine(context: Context, name: String = "fms_preferences") {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun getString(key: String, defaultValue: String): String = prefs.getString(key, defaultValue) ?: defaultValue
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)
    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
}

/**
 * FMSStorageEngine & FMSCacheEngine: Controlled workspace, SAF stream IO, and bounded cache cleanup.
 */
class FMSStorageEngine(private val context: Context) {
    val clonedApkOutputDir: File
        get() = File(context.filesDir, "zclone_builds").apply { mkdirs() }

    val cacheWorkDir: File
        get() = File(context.cacheDir, "fms_clone_work").apply { mkdirs() }

    fun readBytesFromUri(uri: Uri, maxBytes: Long = 150L * 1024L * 1024L): Result<ByteArray> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                require(total <= maxBytes) { "File exceeds safety size limit ($maxBytes bytes)." }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: error("Cannot open selected URI.")
    }

    fun copyUriToWorkFile(uri: Uri, destName: String): Result<File> = runCatching {
        val safeName = destName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val target = File(cacheWorkDir, safeName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot open input stream for $uri")
            require(target.length() > 0L) { "File yang dipilih kosong (0 byte)." }
            target
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun exportFileToUri(sourceFile: File, targetUri: Uri): Result<Unit> = runCatching {
        require(sourceFile.exists() && sourceFile.isFile) { "Source file does not exist." }
        val stream = runCatching { context.contentResolver.openOutputStream(targetUri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(targetUri)
            ?: error("Cannot open destination URI for writing.")
        stream.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
            output.flush()
        }
    }

    fun workCacheSizeBytes(): Long {
        fun folderBytes(f: File): Long {
            if (!f.exists()) return 0L
            if (f.isFile) return f.length()
            return f.listFiles()?.sumOf { folderBytes(it) } ?: 0L
        }
        return folderBytes(cacheWorkDir)
    }

    fun clearWorkCache(): Long {
        val freed = workCacheSizeBytes()
        cacheWorkDir.listFiles()?.forEach { it.deleteRecursively() }
        return freed
    }
}
