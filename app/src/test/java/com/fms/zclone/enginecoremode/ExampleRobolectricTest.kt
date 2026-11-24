package com.fms.zclone.enginecoremode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fms.clone.FmsParityCatalog
import com.fms.clone.ParityStatus
import com.fms.data.FMSDatabaseEngine
import com.fms.data.LocalBuildHistoryRecord
import com.fms.data.LocalCloneRecord
import com.fms.data.LocalSecurityReportRecord
import com.fms.device.FMSDeviceManager
import com.fms.device.LicenseTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context returns ZCloner PRO`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ZCloner PRO", appName)
    }

    @Test
    fun `device manager generates persistent install uuid without hardware ids`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = FMSDeviceManager(context)
        val first = manager.getOrCreateInstallIdentity("1.0.0")
        val second = manager.getOrCreateInstallIdentity("1.0.0")

        assertTrue(first.installUuid.startsWith("fms-"))
        assertEquals(first.installUuid, second.installUuid)
        assertTrue(manager.checkQuota(2, LicenseTier.FREE).withinQuota)
        assertFalse(manager.checkQuota(5, LicenseTier.FREE).withinQuota)
    }

    @Test
    fun `database engine performs full local-first crud for clones, history, and reports`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FMSDatabaseEngine(context, "test_zclone_${System.nanoTime()}.db")

        val clone = LocalCloneRecord(
            id = "cfg_1",
            sourcePackage = "com.sample.src",
            targetPackage = "com.sample.src.zclone1",
            sourceAppName = "SourceApp",
            cloneName = "SourceApp Clone",
            iconPresetId = "neon_cyan",
            iconHueShift = 180,
            changePackageName = true,
            hideFromLauncher = false,
            isolateData = true,
            strippedPermissionsCsv = "android.permission.CAMERA",
            compatibilityStatus = "COMPATIBLE",
            buildStatus = "BUILT",
            outputApkPath = "/tmp/out.apk",
            updatedAtMs = 1000L
        )
        db.upsertCloneConfig(clone)
        val loaded = db.listCloneConfigs()
        assertEquals(1, loaded.size)
        assertEquals("com.sample.src.zclone1", loaded.first().targetPackage)

        db.insertBuildHistory(
            LocalBuildHistoryRecord(
                id = "bld_1",
                configId = "cfg_1",
                sourcePackage = "com.sample.src",
                targetPackage = "com.sample.src.zclone1",
                status = "VERIFIED",
                outputPath = "/tmp/out.apk",
                sha256 = "abc123",
                createdAtMs = 2000L
            )
        )
        assertEquals(1, db.listBuildHistory().size)

        db.upsertSecurityReport(
            LocalSecurityReportRecord(
                id = "rep_1",
                packageName = "com.sample.src",
                appName = "SourceApp",
                riskScore = 20,
                riskLevel = "LOW",
                exportedComponentsCount = 1,
                dangerousPermissionsCount = 1,
                cleartextAllowed = false,
                debuggableFlag = false,
                certSha256Prefix = "01234567",
                summary = "Low risk",
                scannedAtMs = 3000L
            )
        )
        assertEquals(1, db.listSecurityReports().size)

        db.deleteCloneConfig("cfg_1")
        assertTrue(db.listCloneConfigs().isEmpty())
        db.close()
    }

    @Test
    fun `parity catalog contains all 22 zclone exclusive items and honest unsupported markers`() {
        val records = FmsParityCatalog.allRecords
        val exclusiveCount = records.count { it.officialCategory == "ZClone Exclusive" }
        assertEquals(22, exclusiveCount)
        assertTrue(records.any { it.featureId == "AC-PREM-04" && it.status == ParityStatus.UNSUPPORTED })
        assertTrue(records.any { it.featureId == "AC-ULTRA-02" && it.status == ParityStatus.UNSUPPORTED })
        assertTrue(records.none { it.status == ParityStatus.NOT_AUDITED })
    }
}
