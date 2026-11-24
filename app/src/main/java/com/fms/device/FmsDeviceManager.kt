package com.fms.device

import android.content.Context
import android.os.Build
import com.fms.security.FMSSecurityStorage
import java.util.UUID

enum class LicenseTier(val displayName: String, val maxDevices: Int, val maxParallelClones: Int) {
    FREE("Free", 3, 2),
    PREMIUM("Premium", 5, 10),
    ULTRA("Ultra++", 10, 50)
}

data class LocalDeviceIdentity(
    val installUuid: String,
    val deviceLabel: String,
    val modelName: String,
    val androidVersion: String,
    val appVersion: String
)

/**
 * FMSDeviceManager, FMSDeviceRegistry, FMSDeviceLimit & DeviceLicenseManager:
 * Uses a cryptographically random installation UUID persisted in FMSSecurityStorage.
 * Never collects or spoofs protected hardware identifiers (IMEI, Serial, MAC).
 */
class FMSDeviceManager(context: Context) {
    private val secureStorage = FMSSecurityStorage(context, "fms_device_registry")
    private val appContext = context.applicationContext

    fun getOrCreateInstallIdentity(appVersion: String = "1.0.0"): LocalDeviceIdentity {
        val existingUuid = secureStorage.getString(KEY_INSTALL_UUID)
        val uuid = if (existingUuid != null && existingUuid.matches(Regex("^[a-zA-Z0-9_\\-]{8,64}$"))) {
            existingUuid
        } else {
            ("fms-" + UUID.randomUUID().toString().replace("-", "").take(20)).also {
                secureStorage.putString(KEY_INSTALL_UUID, it)
            }
        }
        val model = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android Device" }
            .take(80)
        val label = secureStorage.getString(KEY_DEVICE_LABEL)?.take(60) ?: model.take(60)
        val androidVer = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}".take(32)
        return LocalDeviceIdentity(
            installUuid = uuid,
            deviceLabel = label,
            modelName = model,
            androidVersion = androidVer,
            appVersion = appVersion.take(32)
        )
    }

    fun updateDeviceLabel(newLabel: String) {
        val clean = newLabel.trim().take(60)
        if (clean.isNotEmpty()) {
            secureStorage.putString(KEY_DEVICE_LABEL, clean)
        }
    }

    fun checkQuota(activeDevicesCount: Int, tier: LicenseTier, overrideLimit: Int? = null): QuotaStatus {
        val effectiveLimit = (overrideLimit ?: tier.maxDevices).coerceIn(1, 20)
        val withinQuota = activeDevicesCount <= effectiveLimit
        return QuotaStatus(
            activeCount = activeDevicesCount,
            limit = effectiveLimit,
            withinQuota = withinQuota
        )
    }

    private companion object {
        const val KEY_INSTALL_UUID = "install_uuid"
        const val KEY_DEVICE_LABEL = "device_label"
    }
}

data class QuotaStatus(
    val activeCount: Int,
    val limit: Int,
    val withinQuota: Boolean
)
