package com.fms.zclone.enginecoremode.zclone.data

import android.content.Context
import androidx.annotation.Keep
import com.fms.core.FmsLogger
import com.fms.zclone.enginecoremode.R
import com.fms.data.LocalCloneRecord
import com.fms.data.LocalSecurityReportRecord
import com.fms.device.LicenseTier
import com.fms.device.LocalDeviceIdentity
import com.fms.ui.FmsFontChoice
import com.fms.ui.FmsIconStyle
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("hasEmail", !provider.email.isNullOrBlank())
        }
    } ?: emptyList()

    val uidPrefix = currentUser?.uid?.takeIf { it.isNotBlank() }?.let { "${it.take(6)}…" } ?: "none"
    val authInfoJson = JSONObject().apply {
        put("userIdTag", uidPrefix)
        put("signedIn", currentUser != null)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val safePath = path?.replace(Regex("(^|/)users/[^/]+"), "$1users/$uidPrefix")
    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", safePath)
        put("authInfo", authInfoJson)
    }

    val jsonString = FmsLogger.sanitizeMessage(errorInfoJson.toString())
    FmsLogger.error("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    val userId: String = "",
    val displayName: String = "",
    val tier: String = "FREE",
    val deviceLimit: Long = 3L,
    val activeThemeId: String = "dark_neon",
    val languageCode: String = "id",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toTier(): LicenseTier = LicenseTier.entries.firstOrNull { it.name == tier } ?: LicenseTier.FREE
}

@Keep
@IgnoreExtraProperties
data class CloudDeviceRecord(
    val userId: String = "",
    val installUuid: String = "",
    val deviceLabel: String = "",
    val modelName: String = "",
    val androidVersion: String = "",
    val appVersion: String = "",
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val lastSyncAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudCloneConfig(
    val userId: String = "",
    val sourcePackage: String = "",
    val targetPackage: String = "",
    val sourceAppName: String = "",
    val cloneName: String = "",
    val iconPresetId: String = "neon_cyan",
    val iconHueShift: Long = 0L,
    val changePackageName: Boolean = true,
    val hideFromLauncher: Boolean = false,
    val isolateData: Boolean = true,
    val strippedPermissionsCount: Long = 0L,
    val compatibilityStatus: String = "COMPATIBLE",
    val buildStatus: String = "SAVED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudThemePreset(
    val userId: String = "",
    val name: String = "",
    val primaryHex: String = "#00D2FF",
    val secondaryHex: String = "#8B5CF6",
    val surfaceHex: String = "#11182E",
    val fontFamily: String = "SPACE_GROTESK",
    val iconStyle: String = "SQUIRCLE",
    val customTheme: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudSecurityReport(
    val userId: String = "",
    val packageName: String = "",
    val appName: String = "",
    val riskScore: Long = 0L,
    val riskLevel: String = "LOW",
    val exportedComponentsCount: Long = 0L,
    val dangerousPermissionsCount: Long = 0L,
    val cleartextAllowed: Boolean = false,
    val debuggableFlag: Boolean = false,
    val certSha256Prefix: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Zero-trust Firestore Cloud Sync repository bound strictly to R.string.firestore_database_id
 * and isolated under /users/{uid}/... matching firestore.rules 1:1.
 */
class ZCloneCloudRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        context.applicationContext.getString(R.string.firestore_database_id).let { dbId ->
            if (dbId.isBlank() || dbId == "(default)") {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        }
    )

    private val auth = Firebase.auth

    private fun requireActiveUid(expectedUid: String): String {
        val current = auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google untuk sinkronisasi cloud.")
        check(current == expectedUid) { "Sesi pengguna berubah; menolak operasi lintas akun." }
        return current
    }

    fun observeDevices(userId: String): Flow<List<Pair<String, CloudDeviceRecord>>> =
        db.collection("users").document(userId).collection("devices")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(CloudDeviceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.let { doc.id to it }
                }
            }

    fun observeCloneConfigs(userId: String): Flow<List<Pair<String, CloudCloneConfig>>> =
        db.collection("users").document(userId).collection("cloneConfigs")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(CloudCloneConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.let { doc.id to it }
                }
            }

    fun observeThemePresets(userId: String): Flow<List<Pair<String, CloudThemePreset>>> =
        db.collection("users").document(userId).collection("themePresets")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(CloudThemePreset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.let { doc.id to it }
                }
            }

    fun observeSecurityReports(userId: String): Flow<List<Pair<String, CloudSecurityReport>>> =
        db.collection("users").document(userId).collection("securityReports")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(CloudSecurityReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.let { doc.id to it }
                }
            }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        activeThemeId: String,
        languageCode: String
    ): Result<CloudUserProfile> {
        val path = "users/$userId"
        return try {
            val uid = requireActiveUid(userId)
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: CloudUserProfile(userId = uid, displayName = displayName.take(80).ifBlank { "ZClone User" })
                Result.success(existing)
            } else {
                val safeName = displayName.trim().take(80).ifBlank { "ZClone User" }
                val safeTheme = activeThemeId.take(64).ifBlank { "dark_neon" }
                val safeLang = if (languageCode in setOf("id", "en")) languageCode else "id"
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "tier" to "FREE",
                    "deviceLimit" to 3L,
                    "activeThemeId" to safeTheme,
                    "languageCode" to safeLang,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(
                    CloudUserProfile(
                        userId = uid,
                        displayName = safeName,
                        tier = "FREE",
                        deviceLimit = 3L,
                        activeThemeId = safeTheme,
                        languageCode = safeLang
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(userId: String, activeThemeId: String, languageCode: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireActiveUid(userId)
            val safeLang = if (languageCode in setOf("id", "en")) languageCode else "id"
            db.collection("users").document(uid).update(
                mapOf(
                    "activeThemeId" to activeThemeId.take(64).ifBlank { "dark_neon" },
                    "languageCode" to safeLang,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun registerOrSyncDevice(
        userId: String,
        identity: LocalDeviceIdentity,
        deviceLimit: Int = 3
    ): Result<String> {
        val docId = identity.installUuid.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val path = "users/$userId/devices/$docId"
        return try {
            val uid = requireActiveUid(userId)
            val devicesColl = db.collection("users").document(uid).collection("devices")
            val ref = devicesColl.document(docId)
            val snap = ref.get().await()
            val safeLabel = identity.deviceLabel.trim().take(60).ifBlank { "Android Device" }
            val safeModel = identity.modelName.trim().take(80).ifBlank { "Android" }
            val safeAndroidVer = identity.androidVersion.trim().take(32).ifBlank { "Android" }
            val safeAppVer = identity.appVersion.trim().take(32).ifBlank { "1.0.0" }
            val finalStatus = if (snap.exists()) {
                val existingStatus = snap.getString("status")
                if (existingStatus == "REVOKED") {
                    // Never write metadata/lastSyncAt updates from a REVOKED device.
                    return Result.success("REVOKED")
                }
                val resolved = if (existingStatus in setOf("ACTIVE", "INACTIVE")) {
                    existingStatus!!
                } else {
                    "ACTIVE"
                }
                ref.update(
                    mapOf(
                        "deviceLabel" to safeLabel,
                        "modelName" to safeModel,
                        "androidVersion" to safeAndroidVer,
                        "appVersion" to safeAppVer,
                        "status" to resolved,
                        "lastSyncAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                resolved
            } else {
                val activeCount = devicesColl.whereEqualTo("status", "ACTIVE").get().await().size()
                val assignedStatus = if (activeCount < deviceLimit.coerceIn(1, 20)) "ACTIVE" else "INACTIVE"
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "installUuid" to identity.installUuid.take(64),
                        "deviceLabel" to safeLabel,
                        "modelName" to safeModel,
                        "androidVersion" to safeAndroidVer,
                        "appVersion" to safeAppVer,
                        "status" to assignedStatus,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastSyncAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                assignedStatus
            }
            Result.success(finalStatus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun setDeviceStatus(
        userId: String,
        deviceId: String,
        status: String,
        callerDeviceId: String? = null
    ): Result<Unit> {
        val path = "users/$userId/devices/$deviceId"
        return try {
            val uid = requireActiveUid(userId)
            require(status in setOf("ACTIVE", "INACTIVE", "REVOKED")) { "Invalid status" }
            if (callerDeviceId != null && callerDeviceId == deviceId && status == "REVOKED") {
                return Result.failure(
                    IllegalStateException("Perangkat aktif ini tidak dapat mencabut dirinya sendiri; gunakan perangkat lain atau keluar akun.")
                )
            }
            val ref = db.collection("users").document(uid).collection("devices").document(deviceId)
            val snap = ref.get().await()
            val currentStatus = snap.getString("status")
            if (currentStatus == "REVOKED") {
                return Result.failure(
                    IllegalStateException("Perangkat berstatus REVOKED terkunci oleh aturan keamanan dan tidak dapat diubah statusnya oleh klien.")
                )
            }
            ref.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncCloneConfig(userId: String, record: LocalCloneRecord): Result<Unit> {
        val docId = record.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val path = "users/$userId/cloneConfigs/$docId"
        return try {
            val uid = requireActiveUid(userId)
            val ref = db.collection("users").document(uid).collection("cloneConfigs").document(docId)
            val snap = ref.get().await()
            val strippedCount = record.strippedPermissionsCsv
                .split(",")
                .count { it.isNotBlank() }
                .toLong()
                .coerceIn(0L, 500L)
            val compat = if (record.compatibilityStatus in setOf("VERIFIED", "COMPATIBLE", "LIMITED", "UNSUPPORTED")) {
                record.compatibilityStatus
            } else {
                "COMPATIBLE"
            }
            val bStatus = if (record.buildStatus in setOf("SAVED", "BUILT", "EXPORTED")) {
                record.buildStatus
            } else {
                "SAVED"
            }
            val safeSourceAppName = record.sourceAppName.trim().take(80).ifBlank { record.sourcePackage.take(80) }
            val safeCloneName = record.cloneName.trim().take(80).ifBlank { "Clone" }
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "targetPackage" to record.targetPackage.take(160),
                        "cloneName" to safeCloneName,
                        "iconPresetId" to record.iconPresetId.take(40).ifBlank { "original" },
                        "iconHueShift" to record.iconHueShift.toLong().coerceIn(0L, 360L),
                        "changePackageName" to record.changePackageName,
                        "hideFromLauncher" to record.hideFromLauncher,
                        "isolateData" to record.isolateData,
                        "strippedPermissionsCount" to strippedCount,
                        "compatibilityStatus" to compat,
                        "buildStatus" to bStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "sourcePackage" to record.sourcePackage.take(160),
                        "targetPackage" to record.targetPackage.take(160),
                        "sourceAppName" to safeSourceAppName,
                        "cloneName" to safeCloneName,
                        "iconPresetId" to record.iconPresetId.take(40).ifBlank { "original" },
                        "iconHueShift" to record.iconHueShift.toLong().coerceIn(0L, 360L),
                        "changePackageName" to record.changePackageName,
                        "hideFromLauncher" to record.hideFromLauncher,
                        "isolateData" to record.isolateData,
                        "strippedPermissionsCount" to strippedCount,
                        "compatibilityStatus" to compat,
                        "buildStatus" to bStatus,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCloneConfig(userId: String, configId: String): Result<Unit> {
        val path = "users/$userId/cloneConfigs/$configId"
        return try {
            val uid = requireActiveUid(userId)
            db.collection("users").document(uid).collection("cloneConfigs").document(configId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveCustomThemePreset(
        userId: String,
        themeId: String,
        name: String,
        primaryHex: String,
        secondaryHex: String,
        surfaceHex: String,
        fontChoice: FmsFontChoice,
        iconStyle: FmsIconStyle
    ): Result<Unit> {
        val docId = themeId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val path = "users/$userId/themePresets/$docId"
        return try {
            val uid = requireActiveUid(userId)
            val ref = db.collection("users").document(uid).collection("themePresets").document(docId)
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "name" to name.take(50).ifBlank { "Custom" },
                        "primaryHex" to primaryHex.take(9),
                        "secondaryHex" to secondaryHex.take(9),
                        "surfaceHex" to surfaceHex.take(9),
                        "fontFamily" to fontChoice.name,
                        "iconStyle" to iconStyle.name,
                        "customTheme" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "name" to name.take(50).ifBlank { "Custom" },
                        "primaryHex" to primaryHex.take(9),
                        "secondaryHex" to secondaryHex.take(9),
                        "surfaceHex" to surfaceHex.take(9),
                        "fontFamily" to fontChoice.name,
                        "iconStyle" to iconStyle.name,
                        "customTheme" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncSecurityReport(userId: String, report: LocalSecurityReportRecord): Result<Unit> {
        val docId = report.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val path = "users/$userId/securityReports/$docId"
        return try {
            val uid = requireActiveUid(userId)
            val ref = db.collection("users").document(uid).collection("securityReports").document(docId)
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "riskScore" to report.riskScore.toLong().coerceIn(0L, 100L),
                        "riskLevel" to report.riskLevel,
                        "exportedComponentsCount" to report.exportedComponentsCount.toLong().coerceIn(0L, 500L),
                        "dangerousPermissionsCount" to report.dangerousPermissionsCount.toLong().coerceIn(0L, 200L),
                        "cleartextAllowed" to report.cleartextAllowed,
                        "debuggableFlag" to report.debuggableFlag,
                        "certSha256Prefix" to report.certSha256Prefix.take(64),
                        "summary" to report.summary.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "packageName" to report.packageName.take(160),
                        "appName" to report.appName.take(80),
                        "riskScore" to report.riskScore.toLong().coerceIn(0L, 100L),
                        "riskLevel" to report.riskLevel,
                        "exportedComponentsCount" to report.exportedComponentsCount.toLong().coerceIn(0L, 500L),
                        "dangerousPermissionsCount" to report.dangerousPermissionsCount.toLong().coerceIn(0L, 200L),
                        "cleartextAllowed" to report.cleartextAllowed,
                        "debuggableFlag" to report.debuggableFlag,
                        "certSha256Prefix" to report.certSha256Prefix.take(64),
                        "summary" to report.summary.take(300),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
