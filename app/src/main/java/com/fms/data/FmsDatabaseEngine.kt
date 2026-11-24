package com.fms.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-first SQLite entities managed by FMSDatabaseEngine without requiring Room.
 */
data class LocalCloneRecord(
    val id: String,
    val sourcePackage: String,
    val targetPackage: String,
    val sourceAppName: String,
    val cloneName: String,
    val iconPresetId: String,
    val iconHueShift: Int,
    val changePackageName: Boolean,
    val hideFromLauncher: Boolean,
    val isolateData: Boolean,
    val strippedPermissionsCsv: String,
    val compatibilityStatus: String,
    val buildStatus: String,
    val outputApkPath: String?,
    val updatedAtMs: Long
)

data class LocalBuildHistoryRecord(
    val id: String,
    val configId: String,
    val sourcePackage: String,
    val targetPackage: String,
    val status: String,
    val outputPath: String,
    val sha256: String,
    val createdAtMs: Long,
    val configSnapshotJson: String = ""
)

data class LocalSecurityReportRecord(
    val id: String,
    val packageName: String,
    val appName: String,
    val riskScore: Int,
    val riskLevel: String,
    val exportedComponentsCount: Int,
    val dangerousPermissionsCount: Int,
    val cleartextAllowed: Boolean,
    val debuggableFlag: Boolean,
    val certSha256Prefix: String,
    val summary: String,
    val scannedAtMs: Long
)

/**
 * FMSDatabaseEngine & FMSMigrationEngine: Independent SQLite-backed local-first database engine.
 */
class FMSDatabaseEngine(context: Context, dbName: String = "fms_zclone_local.db") :
    SQLiteOpenHelper(context.applicationContext, dbName, null, DATABASE_VERSION) {

    private val writeLock = Any()

    companion object {
        const val DATABASE_VERSION = 2

        fun encodeCloneSnapshotJson(record: LocalCloneRecord): String = JSONObject().apply {
            put("id", record.id)
            put("sourcePackage", record.sourcePackage)
            put("targetPackage", record.targetPackage)
            put("sourceAppName", record.sourceAppName)
            put("cloneName", record.cloneName)
            put("iconPresetId", record.iconPresetId)
            put("iconHueShift", record.iconHueShift)
            put("changePackageName", record.changePackageName)
            put("hideFromLauncher", record.hideFromLauncher)
            put("isolateData", record.isolateData)
            put("strippedPermissionsCsv", record.strippedPermissionsCsv)
            put("compatibilityStatus", record.compatibilityStatus)
            put("buildStatus", record.buildStatus)
            put("updatedAtMs", record.updatedAtMs)
        }.toString()

        fun decodeCloneSnapshotJson(json: String, fallback: LocalBuildHistoryRecord): LocalCloneRecord? {
            if (json.isBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                LocalCloneRecord(
                    id = o.optString("id", fallback.configId).ifBlank { fallback.configId },
                    sourcePackage = o.optString("sourcePackage", fallback.sourcePackage).ifBlank { fallback.sourcePackage },
                    targetPackage = o.optString("targetPackage", fallback.targetPackage).ifBlank { fallback.targetPackage },
                    sourceAppName = o.optString("sourceAppName", fallback.sourcePackage).ifBlank { fallback.sourcePackage },
                    cloneName = o.optString("cloneName", fallback.targetPackage).ifBlank { fallback.targetPackage },
                    iconPresetId = o.optString("iconPresetId", "neon_cyan"),
                    iconHueShift = o.optInt("iconHueShift", 0).coerceIn(0, 360),
                    changePackageName = o.optBoolean("changePackageName", true),
                    hideFromLauncher = o.optBoolean("hideFromLauncher", false),
                    isolateData = o.optBoolean("isolateData", true),
                    strippedPermissionsCsv = o.optString("strippedPermissionsCsv", ""),
                    compatibilityStatus = o.optString("compatibilityStatus", "COMPATIBLE"),
                    buildStatus = "SNAPSHOT_RESTORED",
                    outputApkPath = fallback.outputPath.takeIf { it.isNotBlank() },
                    updatedAtMs = o.optLong("updatedAtMs", fallback.createdAtMs)
                )
            }.getOrNull()
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS clone_configs (
                id TEXT PRIMARY KEY,
                source_package TEXT NOT NULL,
                target_package TEXT NOT NULL,
                source_app_name TEXT NOT NULL,
                clone_name TEXT NOT NULL,
                icon_preset_id TEXT NOT NULL,
                icon_hue_shift INTEGER NOT NULL,
                change_package_name INTEGER NOT NULL,
                hide_from_launcher INTEGER NOT NULL,
                isolate_data INTEGER NOT NULL,
                stripped_permissions_csv TEXT NOT NULL,
                compatibility_status TEXT NOT NULL,
                build_status TEXT NOT NULL,
                output_apk_path TEXT,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS build_history (
                id TEXT PRIMARY KEY,
                config_id TEXT NOT NULL,
                source_package TEXT NOT NULL,
                target_package TEXT NOT NULL,
                status TEXT NOT NULL,
                output_path TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL,
                snapshot_json TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS security_reports (
                id TEXT PRIMARY KEY,
                package_name TEXT NOT NULL,
                app_name TEXT NOT NULL,
                risk_score INTEGER NOT NULL,
                risk_level TEXT NOT NULL,
                exported_components_count INTEGER NOT NULL,
                dangerous_permissions_count INTEGER NOT NULL,
                cleartext_allowed INTEGER NOT NULL,
                debuggable_flag INTEGER NOT NULL,
                cert_sha256_prefix TEXT NOT NULL,
                summary TEXT NOT NULL,
                scanned_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // FMSMigrationEngine deterministic schema evolution hook
        if (oldVersion < 2) {
            onCreate(db)
            runCatching {
                db.execSQL("ALTER TABLE build_history ADD COLUMN snapshot_json TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    fun upsertCloneConfig(record: LocalCloneRecord) {
        val cv = ContentValues().apply {
            put("id", record.id)
            put("source_package", record.sourcePackage)
            put("target_package", record.targetPackage)
            put("source_app_name", record.sourceAppName)
            put("clone_name", record.cloneName)
            put("icon_preset_id", record.iconPresetId)
            put("icon_hue_shift", record.iconHueShift)
            put("change_package_name", if (record.changePackageName) 1 else 0)
            put("hide_from_launcher", if (record.hideFromLauncher) 1 else 0)
            put("isolate_data", if (record.isolateData) 1 else 0)
            put("stripped_permissions_csv", record.strippedPermissionsCsv)
            put("compatibility_status", record.compatibilityStatus)
            put("build_status", record.buildStatus)
            put("output_apk_path", record.outputApkPath)
            put("updated_at_ms", record.updatedAtMs)
        }
        synchronized(writeLock) {
            writableDatabase.insertWithOnConflict("clone_configs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun listCloneConfigs(): List<LocalCloneRecord> {
        val result = mutableListOf<LocalCloneRecord>()
        readableDatabase.rawQuery("SELECT * FROM clone_configs ORDER BY updated_at_ms DESC", null).use { c ->
            while (c.moveToNext()) {
                result.add(
                    LocalCloneRecord(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        sourcePackage = c.getString(c.getColumnIndexOrThrow("source_package")),
                        targetPackage = c.getString(c.getColumnIndexOrThrow("target_package")),
                        sourceAppName = c.getString(c.getColumnIndexOrThrow("source_app_name")),
                        cloneName = c.getString(c.getColumnIndexOrThrow("clone_name")),
                        iconPresetId = c.getString(c.getColumnIndexOrThrow("icon_preset_id")),
                        iconHueShift = c.getInt(c.getColumnIndexOrThrow("icon_hue_shift")),
                        changePackageName = c.getInt(c.getColumnIndexOrThrow("change_package_name")) == 1,
                        hideFromLauncher = c.getInt(c.getColumnIndexOrThrow("hide_from_launcher")) == 1,
                        isolateData = c.getInt(c.getColumnIndexOrThrow("isolate_data")) == 1,
                        strippedPermissionsCsv = c.getString(c.getColumnIndexOrThrow("stripped_permissions_csv")),
                        compatibilityStatus = c.getString(c.getColumnIndexOrThrow("compatibility_status")),
                        buildStatus = c.getString(c.getColumnIndexOrThrow("build_status")),
                        outputApkPath = c.getString(c.getColumnIndexOrThrow("output_apk_path")),
                        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms"))
                    )
                )
            }
        }
        return result
    }

    fun deleteCloneConfig(id: String) {
        synchronized(writeLock) {
            writableDatabase.delete("clone_configs", "id = ?", arrayOf(id))
        }
    }

    fun insertBuildHistory(record: LocalBuildHistoryRecord) {
        val cv = ContentValues().apply {
            put("id", record.id)
            put("config_id", record.configId)
            put("source_package", record.sourcePackage)
            put("target_package", record.targetPackage)
            put("status", record.status)
            put("output_path", record.outputPath)
            put("sha256", record.sha256)
            put("created_at_ms", record.createdAtMs)
            put("snapshot_json", record.configSnapshotJson)
        }
        synchronized(writeLock) {
            writableDatabase.insertWithOnConflict("build_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun listBuildHistory(): List<LocalBuildHistoryRecord> {
        val result = mutableListOf<LocalBuildHistoryRecord>()
        readableDatabase.rawQuery("SELECT * FROM build_history ORDER BY created_at_ms DESC", null).use { c ->
            val snapIdx = c.getColumnIndex("snapshot_json")
            while (c.moveToNext()) {
                result.add(
                    LocalBuildHistoryRecord(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        configId = c.getString(c.getColumnIndexOrThrow("config_id")),
                        sourcePackage = c.getString(c.getColumnIndexOrThrow("source_package")),
                        targetPackage = c.getString(c.getColumnIndexOrThrow("target_package")),
                        status = c.getString(c.getColumnIndexOrThrow("status")),
                        outputPath = c.getString(c.getColumnIndexOrThrow("output_path")),
                        sha256 = c.getString(c.getColumnIndexOrThrow("sha256")),
                        createdAtMs = c.getLong(c.getColumnIndexOrThrow("created_at_ms")),
                        configSnapshotJson = if (snapIdx >= 0) c.getString(snapIdx).orEmpty() else ""
                    )
                )
            }
        }
        return result
    }

    fun upsertSecurityReport(record: LocalSecurityReportRecord) {
        val cv = ContentValues().apply {
            put("id", record.id)
            put("package_name", record.packageName)
            put("app_name", record.appName)
            put("risk_score", record.riskScore)
            put("risk_level", record.riskLevel)
            put("exported_components_count", record.exportedComponentsCount)
            put("dangerous_permissions_count", record.dangerousPermissionsCount)
            put("cleartext_allowed", if (record.cleartextAllowed) 1 else 0)
            put("debuggable_flag", if (record.debuggableFlag) 1 else 0)
            put("cert_sha256_prefix", record.certSha256Prefix)
            put("summary", record.summary)
            put("scanned_at_ms", record.scannedAtMs)
        }
        synchronized(writeLock) {
            writableDatabase.insertWithOnConflict("security_reports", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun listSecurityReports(): List<LocalSecurityReportRecord> {
        val result = mutableListOf<LocalSecurityReportRecord>()
        readableDatabase.rawQuery("SELECT * FROM security_reports ORDER BY scanned_at_ms DESC", null).use { c ->
            while (c.moveToNext()) {
                result.add(
                    LocalSecurityReportRecord(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        packageName = c.getString(c.getColumnIndexOrThrow("package_name")),
                        appName = c.getString(c.getColumnIndexOrThrow("app_name")),
                        riskScore = c.getInt(c.getColumnIndexOrThrow("risk_score")),
                        riskLevel = c.getString(c.getColumnIndexOrThrow("risk_level")),
                        exportedComponentsCount = c.getInt(c.getColumnIndexOrThrow("exported_components_count")),
                        dangerousPermissionsCount = c.getInt(c.getColumnIndexOrThrow("dangerous_permissions_count")),
                        cleartextAllowed = c.getInt(c.getColumnIndexOrThrow("cleartext_allowed")) == 1,
                        debuggableFlag = c.getInt(c.getColumnIndexOrThrow("debuggable_flag")) == 1,
                        certSha256Prefix = c.getString(c.getColumnIndexOrThrow("cert_sha256_prefix")),
                        summary = c.getString(c.getColumnIndexOrThrow("summary")),
                        scannedAtMs = c.getLong(c.getColumnIndexOrThrow("scanned_at_ms"))
                    )
                )
            }
        }
        return result
    }
}

/**
 * FMSBackupEngine & FMSSerialization: Schema-validated and size-bounded JSON backup/restore.
 */
object FMSBackupEngine {
    const val MAX_BACKUP_BYTES = 2 * 1024 * 1024 // 2 MB strict import cap
    private const val SCHEMA_VERSION = 1

    fun exportJson(
        configs: List<LocalCloneRecord>,
        activeThemeId: String,
        languageCode: String,
        fontChoice: String? = null,
        iconStyle: String? = null,
        customPrimaryArgb: Int? = null,
        customSecondaryArgb: Int? = null
    ): String {
        val arr = JSONArray()
        for (c in configs) {
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("sourcePackage", c.sourcePackage)
                    put("targetPackage", c.targetPackage)
                    put("sourceAppName", c.sourceAppName)
                    put("cloneName", c.cloneName)
                    put("iconPresetId", c.iconPresetId)
                    put("iconHueShift", c.iconHueShift)
                    put("changePackageName", c.changePackageName)
                    put("hideFromLauncher", c.hideFromLauncher)
                    put("isolateData", c.isolateData)
                    put("strippedPermissionsCsv", c.strippedPermissionsCsv)
                    put("compatibilityStatus", c.compatibilityStatus)
                    put("buildStatus", c.buildStatus)
                    put("updatedAtMs", c.updatedAtMs)
                }
            )
        }
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("exportedAtMs", System.currentTimeMillis())
            put("activeThemeId", activeThemeId)
            put("languageCode", languageCode)
            if (fontChoice != null) put("fontChoice", fontChoice)
            if (iconStyle != null) put("iconStyle", iconStyle)
            if (customPrimaryArgb != null) put("customPrimaryArgb", customPrimaryArgb)
            if (customSecondaryArgb != null) put("customSecondaryArgb", customSecondaryArgb)
            put("cloneConfigs", arr)
        }.toString(2)
    }

    data class ValidatedBackup(
        val activeThemeId: String,
        val languageCode: String,
        val cloneConfigs: List<LocalCloneRecord>,
        val fontChoice: String? = null,
        val iconStyle: String? = null,
        val customPrimaryArgb: Int? = null,
        val customSecondaryArgb: Int? = null
    )

    fun parseAndValidate(rawBytes: ByteArray): Result<ValidatedBackup> = runCatching {
        require(rawBytes.isNotEmpty()) { "Backup file is empty." }
        require(rawBytes.size <= MAX_BACKUP_BYTES) { "Backup exceeds 2 MB safety limit." }
        val root = JSONObject(String(rawBytes, Charsets.UTF_8))
        val version = root.optInt("schemaVersion", -1)
        require(version == SCHEMA_VERSION) { "Unsupported backup schema version: $version" }

        val themeId = root.optString("activeThemeId", "dark_neon").take(64)
        val lang = root.optString("languageCode", "id").let { if (it in setOf("id", "en")) it else "id" }
        val fontChoice = if (root.has("fontChoice")) root.optString("fontChoice").take(32) else null
        val iconStyle = if (root.has("iconStyle")) root.optString("iconStyle").take(32) else null
        val customPrimaryArgb = if (root.has("customPrimaryArgb")) root.optInt("customPrimaryArgb") else null
        val customSecondaryArgb = if (root.has("customSecondaryArgb")) root.optInt("customSecondaryArgb") else null
        val arr = root.optJSONArray("cloneConfigs") ?: JSONArray()
        require(arr.length() <= 500) { "Too many clone records in backup." }

        val configs = mutableListOf<LocalCloneRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.getString("id").trim().take(128)
            val src = obj.getString("sourcePackage").trim().take(160)
            val tgt = obj.getString("targetPackage").trim().take(160)
            val srcAppName = obj.getString("sourceAppName").trim().take(80)
            val cloneName = obj.getString("cloneName").trim().take(80)
            require(id.isNotBlank()) { "Blank config id in backup item $i" }
            require(src.length >= 3 && tgt.length >= 3) { "Invalid package name in backup item $i" }
            require(srcAppName.isNotBlank() && cloneName.isNotBlank()) { "Blank app/clone name in backup item $i" }
            configs.add(
                LocalCloneRecord(
                    id = id,
                    sourcePackage = src,
                    targetPackage = tgt,
                    sourceAppName = srcAppName,
                    cloneName = cloneName,
                    iconPresetId = obj.optString("iconPresetId", "neon_cyan").take(40),
                    iconHueShift = obj.optInt("iconHueShift", 0).coerceIn(0, 360),
                    changePackageName = obj.optBoolean("changePackageName", true),
                    hideFromLauncher = obj.optBoolean("hideFromLauncher", false),
                    isolateData = obj.optBoolean("isolateData", true),
                    strippedPermissionsCsv = obj.optString("strippedPermissionsCsv", "").take(2000),
                    compatibilityStatus = obj.optString("compatibilityStatus", "COMPATIBLE").take(24),
                    buildStatus = "SAVED",
                    outputApkPath = null,
                    updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis())
                )
            )
        }
        ValidatedBackup(
            activeThemeId = themeId,
            languageCode = lang,
            cloneConfigs = configs,
            fontChoice = fontChoice,
            iconStyle = iconStyle,
            customPrimaryArgb = customPrimaryArgb,
            customSecondaryArgb = customSecondaryArgb
        )
    }
}
