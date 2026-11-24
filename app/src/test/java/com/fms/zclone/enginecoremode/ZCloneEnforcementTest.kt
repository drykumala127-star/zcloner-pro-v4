package com.fms.zclone.enginecoremode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fms.clone.CloneBuildRequest
import com.fms.clone.CompatibilityStatus
import com.fms.clone.FMSAPKInspector
import com.fms.clone.FMSCompatibilityEngine
import com.fms.clone.FMSManifestEngine
import com.fms.clone.FMSRepackager
import com.fms.clone.FMSSecurityScanner
import com.fms.clone.ManifestSummary
import com.fms.data.FMSBackupEngine
import com.fms.data.FMSStorageEngine
import com.fms.device.FMSDeviceManager
import com.fms.device.LicenseTier
import com.fms.security.FMSIntegrityEngine
import com.fms.ui.FMSNavigationEngine
import com.fms.zclone.enginecoremode.zclone.ui.ZCloneViewModel
import com.fms.zclone.enginecoremode.zclone.ui.screens.ZCloneRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZCloneEnforcementTest {

    @Test
    fun `split apk and resource-only apk are marked UNSUPPORTED and rejected by repackager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "enforce_split_${System.nanoTime()}").apply { mkdirs() }

        try {
            val splitAxml = buildSplitTestAxml(
                packageName = "com.example.splitapp",
                splitName = "config.arm64_v8a"
            )
            val summary = FMSManifestEngine.inspectManifest(splitAxml).getOrThrow()
            assertEquals("config.arm64_v8a", summary.splitName)

            val splitApk = File(workDir, "split_arm64.apk")
            ZipOutputStream(splitApk.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(splitAxml)
                zos.closeEntry()
            }

            val inspector = FMSAPKInspector(context)
            val inspection = inspector.inspectApkFile(splitApk).getOrThrow()
            assertTrue(inspection.hasSplitApks)
            assertEquals(CompatibilityStatus.UNSUPPORTED, inspection.compatibility.status)
            assertTrue(inspection.compatibility.blockers.isNotEmpty())

            // Security scanner also flags unsigned test APK honestly
            val scan = FMSSecurityScanner.scan(inspection)
            assertTrue(scan.findings.any { it.id == "SEC-UNSIGNED" })

            // Repackager refuses to produce broken split clone APK
            val buildAttempt = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = splitApk.absolutePath,
                    sourcePackage = "com.example.splitapp",
                    targetPackage = "com.example.splitapp.zclone1",
                    sourceAppName = "SplitApp",
                    cloneName = "SplitApp Clone",
                    iconPresetId = "original",
                    iconHueShift = 0,
                    changePackageName = true,
                    hideFromLauncher = false,
                    isolateData = true,
                    strippedPermissions = emptySet()
                ),
                privateFilesDir = workDir,
                outputFile = File(workDir, "out.apk")
            )
            assertTrue(buildAttempt.isFailure)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `missing dex apk is blocked as UNSUPPORTED even without split flag`() {
        val noDexSummary = ManifestSummary(
            packageName = "com.example.nodex",
            applicationLabel = "NoDex",
            versionCode = 1,
            versionName = "1.0",
            sharedUserId = null,
            debuggable = false,
            usesCleartextTraffic = false,
            allowBackup = true,
            permissions = listOf("com.android.vending.CHECK_LICENSE"),
            exportedComponentsCount = 0,
            launcherCategoriesCount = 1,
            authorityStrings = emptyList(),
            usesPlayLicensing = true
        )
        val compat = FMSCompatibilityEngine.analyze(
            manifest = noDexSummary,
            hasSplitApks = false,
            hasDex = false,
            nativeAbis = emptyList(),
            hasApkSigBlockV2Plus = true
        )
        assertEquals(CompatibilityStatus.UNSUPPORTED, compat.status)
        assertTrue(compat.blockers.any { it.contains("classes.dex") })
        assertTrue(compat.warnings.any { it.contains("CHECK_LICENSE") })
        assertTrue(compat.warnings.any { it.contains("v2/v3") })
    }

    @Test
    fun `inspector detects APK Signature Scheme v2 signing block magic before central directory`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "enforce_v2_${System.nanoTime()}").apply { mkdirs() }
        try {
            val baseZip = File(workDir, "base.apk")
            val manifestAxml = buildSplitTestAxml(packageName = "com.example.v2signed", splitName = null)
            ZipOutputStream(baseZip.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(manifestAxml)
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
                zos.closeEntry()
            }

            // Splice a valid 40-byte APK Signing Block ("APK Sig Block 42") right before the Central Directory
            val raw = baseZip.readBytes()
            val cdMagic = byteArrayOf(0x50, 0x4B, 0x01, 0x02)
            val eocdMagic = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
            val cdIdx = indexOfMagic(raw, cdMagic)
            assertTrue(cdIdx > 0)

            val sigBlock = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN).apply {
                putLong(32L) // size-of-block (excluding first 8-byte uint64)
                putLong(0L)  // pair length dummy
                putLong(32L) // size-of-block repeated
                put("APK Sig Block 42".toByteArray(Charsets.US_ASCII))
            }.array()

            val spliced = ByteArrayOutputStream().apply {
                write(raw, 0, cdIdx)
                write(sigBlock)
                write(raw, cdIdx, raw.size - cdIdx)
            }.toByteArray()

            // Patch EOCD Central Directory offset by +40 bytes
            val newEocdIdx = indexOfMagic(spliced, eocdMagic)
            assertTrue(newEocdIdx > cdIdx)
            val buf = ByteBuffer.wrap(spliced).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.putInt(newEocdIdx + 16, cdIdx + sigBlock.size)

            val v2Apk = File(workDir, "v2_signed.apk").apply { writeBytes(spliced) }
            val inspector = FMSAPKInspector(context)
            val inspection = inspector.inspectApkFile(v2Apk).getOrThrow()
            assertTrue(inspection.hasApkSigBlockV2Plus)
            assertTrue(inspection.certSubject.contains("v2/v3"))
            assertEquals(CompatibilityStatus.COMPATIBLE, inspection.compatibility.status)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `batch target package builder guarantees valid syntax even for long package names`() {
        val longPkg = "com." + ("verylongsegment.".repeat(10)) + "app"
        val built = ZCloneViewModel.buildValidBatchTargetPackage(longPkg, 2)
        assertTrue(built.length <= 150)
        assertTrue(FMSIntegrityEngine.isValidPackageName(built))

        val normal = ZCloneViewModel.buildValidBatchTargetPackage("org.mozilla.firefox", 1)
        assertEquals("org.mozilla.firefox.zclone1", normal)
        assertTrue(FMSIntegrityEngine.isValidPackageName(normal))
    }

    @Test
    fun `storage engine measures and cleans work cache and device manager honors custom quota limit`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FMSStorageEngine(context)
        storage.clearWorkCache()
        val sample = File(storage.cacheWorkDir, "temp_import.apk")
        sample.writeBytes(ByteArray(2048) { 0x5A })
        assertTrue(storage.workCacheSizeBytes() >= 2048L)

        val freed = storage.clearWorkCache()
        assertTrue(freed >= 2048L)
        assertEquals(0L, storage.workCacheSizeBytes())
        assertFalse(sample.exists())

        val deviceMgr = FMSDeviceManager(context)
        val okQuota = deviceMgr.checkQuota(activeDevicesCount = 2, tier = LicenseTier.FREE, overrideLimit = 3)
        assertTrue(okQuota.withinQuota)
        val fullQuota = deviceMgr.checkQuota(activeDevicesCount = 4, tier = LicenseTier.FREE, overrideLimit = 3)
        assertFalse(fullQuota.withinQuota)
    }

    @Test
    fun `backup parser rejects blank clone id and blank clone name`() {
        val badIdJson = """
            {
              "schemaVersion": 1,
              "activeThemeId": "dark_neon",
              "languageCode": "id",
              "cloneConfigs": [
                {
                  "id": "   ",
                  "sourcePackage": "com.sample.app",
                  "targetPackage": "com.sample.app.zclone1",
                  "sourceAppName": "Sample",
                  "cloneName": "Sample Clone"
                }
              ]
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)
        assertTrue(FMSBackupEngine.parseAndValidate(badIdJson).isFailure)

        val badNameJson = """
            {
              "schemaVersion": 1,
              "activeThemeId": "dark_neon",
              "languageCode": "en",
              "cloneConfigs": [
                {
                  "id": "cfg-1",
                  "sourcePackage": "com.sample.app",
                  "targetPackage": "com.sample.app.zclone1",
                  "sourceAppName": "Sample",
                  "cloneName": " "
                }
              ]
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)
        assertTrue(FMSBackupEngine.parseAndValidate(badNameJson).isFailure)
    }

    @Test
    fun `repackager preserves existing output apk when manifest transformation fails`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "enforce_atomic_${System.nanoTime()}").apply { mkdirs() }
        try {
            val corruptSourceApk = File(workDir, "corrupt_manifest.apk")
            ZipOutputStream(corruptSourceApk.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(byteArrayOf(0x01, 0x02, 0x03, 0x04)) // invalid AXML
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A))
                zos.closeEntry()
            }
            val sentinelOutput = File(workDir, "existing_clone.apk").apply {
                writeText("PREVIOUS_VALID_APK_SENTINEL")
            }

            val res = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = corruptSourceApk.absolutePath,
                    sourcePackage = "com.sample.broken",
                    targetPackage = "com.sample.broken.zclone1",
                    sourceAppName = "Broken",
                    cloneName = "Broken Clone",
                    iconPresetId = "original",
                    iconHueShift = 0,
                    changePackageName = true,
                    hideFromLauncher = false,
                    isolateData = true,
                    strippedPermissions = emptySet()
                ),
                privateFilesDir = workDir,
                outputFile = sentinelOutput
            )
            assertTrue(res.isFailure)
            assertEquals("PREVIOUS_VALID_APK_SENTINEL", sentinelOutput.readText())
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `navigation engine snapshot and restore preserve multi-step backstack`() {
        val engine = FMSNavigationEngine(ZCloneRoute.SPLASH)
        engine.replaceRoot(ZCloneRoute.HOME)
        engine.navigate(ZCloneRoute.CLONE_APPS)
        engine.navigate(ZCloneRoute.APP_CONFIG)

        val saved = engine.snapshot()
        val restored = FMSNavigationEngine(ZCloneRoute.SPLASH)
        restored.restore(saved)

        assertEquals(ZCloneRoute.APP_CONFIG, restored.currentRoute)
        assertTrue(restored.pop())
        assertEquals(ZCloneRoute.CLONE_APPS, restored.currentRoute)
        assertTrue(restored.pop())
        assertEquals(ZCloneRoute.HOME, restored.currentRoute)
    }

    private fun indexOfMagic(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun buildSplitTestAxml(packageName: String, splitName: String?): ByteArray {
        val strings = mutableListOf("manifest", "package", packageName, "application")
        if (splitName != null) {
            strings.add("split")
            strings.add(splitName)
        }
        fun idx(s: String) = strings.indexOf(s)

        val dataOut = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { i, s ->
            offsets[i] = dataOut.size()
            val utf8 = s.toByteArray(Charsets.UTF_8)
            dataOut.write(s.length)
            dataOut.write(utf8.size)
            dataOut.write(utf8)
            dataOut.write(0)
        }
        while (dataOut.size() % 4 != 0) dataOut.write(0)
        val payload = dataOut.toByteArray()
        val headerSize = 28
        val stringsStart = headerSize + strings.size * 4
        val poolSize = stringsStart + payload.size
        val poolBuf = ByteBuffer.allocate(poolSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0001)
            putShort(headerSize.toShort())
            putInt(poolSize)
            putInt(strings.size)
            putInt(0)
            putInt(0x00000100)
            putInt(stringsStart)
            putInt(0)
            offsets.forEach { putInt(it) }
            put(payload)
        }.array()

        val chunks = ByteArrayOutputStream()
        fun startTag(tag: String, attrs: List<Pair<Int, Int>>) {
            val size = 36 + attrs.size * 20
            val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(0x0102); putShort(16); putInt(size); putInt(1); putInt(-1); putInt(-1); putInt(idx(tag))
                putShort(20); putShort(20); putShort(attrs.size.toShort()); putShort(0); putShort(0); putShort(0)
                for ((nIdx, valIdx) in attrs) {
                    putInt(-1); putInt(nIdx); putInt(valIdx); putShort(8); put(0); put(0x03.toByte()); putInt(valIdx)
                }
            }
            chunks.write(buf.array())
        }
        fun endTag(tag: String) {
            val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(0x0103); putShort(16); putInt(24); putInt(1); putInt(-1); putInt(-1); putInt(idx(tag))
            }
            chunks.write(buf.array())
        }

        val manifestAttrs = mutableListOf(idx("package") to idx(packageName))
        if (splitName != null) {
            manifestAttrs.add(idx("split") to idx(splitName))
        }
        startTag("manifest", manifestAttrs)
        startTag("application", emptyList())
        endTag("application")
        endTag("manifest")

        val body = poolBuf + chunks.toByteArray()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0003); putShort(8); putInt(8 + body.size)
        }.array()
        return header + body
    }
}
