package com.fms.zclone.enginecoremode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.fms.clone.CloneBuildRequest
import com.fms.clone.CompatibilityStatus
import com.fms.clone.FMSAPKInspector
import com.fms.clone.FMSCompatibilityEngine
import com.fms.clone.FMSManifestEngine
import com.fms.clone.FMSRepackager
import com.fms.clone.FMSSignatureManager
import com.fms.clone.ManifestSummary
import com.fms.zclone.enginecoremode.zclone.ui.screens.DhedryLoadingTimeline
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DhedryLoadingIntegrationTest {

    @Test
    fun `dhedry timeline spans 5 to 7 seconds with all 10 stages and always-on audio`() {
        assertTrue(DhedryLoadingTimeline.DURATION_MS in 5000L..7000L)
        assertEquals(10, DhedryLoadingTimeline.STAGE_COUNT)
        assertTrue(DhedryLoadingTimeline.AUDIO_ALWAYS_ENABLED)
        assertFalse(DhedryLoadingTimeline.HAS_MUTE_CONTROL)

        val sampleMs = listOf(100L, 800L, 1600L, 2500L, 3400L, 4200L, 4700L, 5200L, 5700L, 6300L)
        val stages = sampleMs.map { DhedryLoadingTimeline.stageForElapsedMs(it) }
        assertEquals((1..10).toList(), stages.map { it.stageNumber })
        assertTrue(stages[7].labelEn.contains("Welcome to ZClone Engine"))
        assertTrue(stages[8].labelEn.contains("Powered by Dhedry Library"))
    }

    @Test
    fun `dhedry offline mp3 raw resource and prebuilt dex asset exist and are valid binaries`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val mp3Bytes = context.resources.openRawResource(R.raw.dhedry_zclone_boot).use { it.readBytes() }
        assertTrue("Expected offline MP3 size >= 50KB, got ${mp3Bytes.size}", mp3Bytes.size >= 50_000)
        val hasId3Header = mp3Bytes.size >= 3 &&
            mp3Bytes[0] == 'I'.code.toByte() &&
            mp3Bytes[1] == 'D'.code.toByte() &&
            mp3Bytes[2] == '3'.code.toByte()
        val hasMpegFrameSync = mp3Bytes.size >= 2 &&
            (mp3Bytes[0].toInt() and 0xFF) == 0xFF &&
            (mp3Bytes[1].toInt() and 0xE0) == 0xE0
        assertTrue("Expected valid MP3 header or MPEG frame sync", hasId3Header || hasMpegFrameSync)

        val dexBytes = context.assets.open(FMSRepackager.DHEDRY_DEX_ASSET_PATH).use { it.readBytes() }
        assertTrue("Expected compiled DEX size >= 4KB, got ${dexBytes.size}", dexBytes.size >= 4_000)
        assertEquals("dex\n", String(dexBytes, 0, 4, Charsets.US_ASCII))
    }

    @Test
    fun `repackager injects DhedrySplashActivity dex and STORED mp3 cleanly without touching resources arsc`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "dhedry_inject_${System.nanoTime()}").apply { mkdirs() }

        try {
            val sourceApk = File(workDir, "target_app.apk")
            val clonedApk = File(workDir, "target_app_cloned.apk")
            // Valid 12-byte Android ResTable_header (type=0x0002, headerSize=12, totalSize=12, packageCount=0)
            val originalResArsc = byteArrayOf(
                0x02, 0x00, 0x0C, 0x00,
                0x0C, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
            )
            val axml = buildLauncherAxml(
                packageName = "com.sample.source",
                appLabel = "Sample Launcher App",
                mainActivityRawName = ".MainActivity",
                authority = "com.sample.source.provider",
                minSdk = 24
            )
            val iconPng = createSamplePng(48, 48, Color.CYAN)

            ZipOutputStream(sourceApk.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(axml)
                zos.closeEntry()

                val resEntry = ZipEntry("resources.arsc").apply {
                    method = ZipEntry.STORED
                    size = originalResArsc.size.toLong()
                    compressedSize = originalResArsc.size.toLong()
                    crc = CRC32().apply { update(originalResArsc) }.value
                }
                zos.putNextEntry(resEntry)
                zos.write(originalResArsc)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("res/drawable-xhdpi-v4/ic_launcher.png"))
                zos.write(iconPng)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("classes2.dex"))
                zos.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00))
                zos.closeEntry()
            }

            val inspector = FMSAPKInspector(context)
            val sourceInspection = inspector.inspectApkFile(sourceApk).getOrThrow()
            assertEquals("com.sample.source.MainActivity", sourceInspection.manifestSummary.launcherActivityName)
            assertEquals(24, sourceInspection.manifestSummary.minSdkVersion)

            val buildOut = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = "com.sample.source",
                    targetPackage = "com.sample.source.zclone1",
                    sourceAppName = "Sample Launcher App",
                    cloneName = "Sample Clone",
                    iconPresetId = "neon_cyan",
                    iconHueShift = 180,
                    changePackageName = true,
                    hideFromLauncher = false,
                    isolateData = true,
                    strippedPermissions = emptySet(),
                    ownerConfirmedModificationRights = true,
                    injectDhedryLoading = true
                ),
                privateFilesDir = workDir,
                outputFile = clonedApk,
                context = context
            ).getOrThrow()

            assertTrue(buildOut.dhedrySplashInjected)
            assertEquals("classes3.dex", buildOut.dhedryDexEntryName)
            assertEquals("com.sample.source.MainActivity", buildOut.dhedryTargetActivity)
            assertTrue(buildOut.signatureVerified)
            assertTrue(FMSSignatureManager.verifyApkContainsValidSignature(clonedApk))
            assertTrue(FMSSignatureManager.isEntryDataAligned(clonedApk, "resources.arsc", 4))
            assertTrue(FMSSignatureManager.isEntryDataAligned(clonedApk, FMSRepackager.DHEDRY_APK_AUDIO_ENTRY, 4))

            ZipFile(clonedApk).use { zip ->
                val resBytes = zip.getInputStream(zip.getEntry("resources.arsc")).use { it.readBytes() }
                assertArrayEquals("resources.arsc must remain byte-for-byte unmodified", originalResArsc, resBytes)

                val injectedDexEntry = zip.getEntry("classes3.dex")
                assertTrue("Expected classes3.dex entry", injectedDexEntry != null)
                val expectedDex = context.assets.open(FMSRepackager.DHEDRY_DEX_ASSET_PATH).use { it.readBytes() }
                val actualDex = zip.getInputStream(injectedDexEntry).use { it.readBytes() }
                assertArrayEquals(expectedDex, actualDex)

                val injectedMp3Entry = zip.getEntry(FMSRepackager.DHEDRY_APK_AUDIO_ENTRY)
                assertTrue("Expected stored MP3 asset entry", injectedMp3Entry != null)
                assertEquals(ZipEntry.STORED, injectedMp3Entry!!.method)
                val expectedMp3 = context.resources.openRawResource(R.raw.dhedry_zclone_boot).use { it.readBytes() }
                val actualMp3 = zip.getInputStream(injectedMp3Entry).use { it.readBytes() }
                assertArrayEquals(expectedMp3, actualMp3)

                val clonedManifestBytes = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
                val clonedSummary = FMSManifestEngine.inspectManifest(clonedManifestBytes).getOrThrow()
                assertEquals("com.sample.source.zclone1", clonedSummary.packageName)
                assertEquals(FMSManifestEngine.DHEDRY_SPLASH_ACTIVITY, clonedSummary.launcherActivityName)
                assertEquals(1, clonedSummary.launcherCategoriesCount)
                assertTrue(clonedSummary.authorityStrings.contains("com.sample.source.zclone1.provider"))
            }

            // Verify with official Android SDK apksigner & aapt2 if installed
            val apkSignerBin = File("/opt/android/sdk/build-tools/36.0.0/apksigner")
            if (apkSignerBin.isFile && apkSignerBin.canExecute()) {
                val proc = ProcessBuilder(
                    apkSignerBin.absolutePath,
                    "verify",
                    "--min-sdk-version",
                    "24",
                    "--verbose",
                    "--print-certs",
                    clonedApk.absolutePath
                ).redirectErrorStream(true).start()
                val verifyOut = proc.inputStream.bufferedReader().readText()
                assertEquals("apksigner verify failed:\n$verifyOut", 0, proc.waitFor())
                assertTrue(verifyOut.contains("Verified using v2 scheme (APK Signature Scheme v2): true"))
            }

            val aapt2Bin = File("/opt/android/sdk/build-tools/36.0.0/aapt2")
            if (aapt2Bin.isFile && aapt2Bin.canExecute()) {
                val proc = ProcessBuilder(
                    aapt2Bin.absolutePath,
                    "dump",
                    "xmltree",
                    "--file",
                    "AndroidManifest.xml",
                    clonedApk.absolutePath
                ).redirectErrorStream(true).start()
                val xmlTreeOut = proc.inputStream.bufferedReader().readText()
                assertEquals("aapt2 dump xmltree failed:\n$xmlTreeOut", 0, proc.waitFor())
                assertTrue(xmlTreeOut.contains("com.dhedry.loading.DhedrySplashActivity"))
                assertTrue(xmlTreeOut.contains("dhedry.target_activity"))
                assertTrue(xmlTreeOut.contains("com.sample.source.MainActivity"))
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `repackager rejects unconfirmed modification rights and skips dhedry splash when no launcher exists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "dhedry_rights_${System.nanoTime()}").apply { mkdirs() }

        try {
            val nonLauncherApk = File(workDir, "service_only.apk")
            val outApk = File(workDir, "service_only_cloned.apk")
            val axml = buildLauncherAxml(
                packageName = "com.sample.serviceonly",
                appLabel = "ServiceOnly",
                mainActivityRawName = null,
                authority = "com.sample.serviceonly.provider",
                minSdk = 19
            )
            ZipOutputStream(nonLauncherApk.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(axml)
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00))
                zos.closeEntry()
            }

            val unconfirmedResult = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = nonLauncherApk.absolutePath,
                    sourcePackage = "com.sample.serviceonly",
                    targetPackage = "com.sample.serviceonly.zclone1",
                    sourceAppName = "ServiceOnly",
                    cloneName = "ServiceOnly Clone",
                    iconPresetId = "original",
                    iconHueShift = 0,
                    changePackageName = true,
                    hideFromLauncher = false,
                    isolateData = true,
                    strippedPermissions = emptySet(),
                    ownerConfirmedModificationRights = false
                ),
                privateFilesDir = workDir,
                outputFile = outApk,
                context = context
            )
            assertTrue(unconfirmedResult.isFailure)

            val confirmedResult = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = nonLauncherApk.absolutePath,
                    sourcePackage = "com.sample.serviceonly",
                    targetPackage = "com.sample.serviceonly.zclone1",
                    sourceAppName = "ServiceOnly",
                    cloneName = "ServiceOnly Clone",
                    iconPresetId = "original",
                    iconHueShift = 0,
                    changePackageName = true,
                    hideFromLauncher = false,
                    isolateData = true,
                    strippedPermissions = emptySet(),
                    ownerConfirmedModificationRights = true,
                    injectDhedryLoading = true
                ),
                privateFilesDir = workDir,
                outputFile = outApk,
                context = context
            ).getOrThrow()

            assertFalse(confirmedResult.dhedrySplashInjected)
            assertNull(confirmedResult.dhedryDexEntryName)
            assertTrue(confirmedResult.warnings.any { it.contains("MAIN/LAUNCHER") })

            val legacyCompat = FMSCompatibilityEngine.analyze(
                manifest = ManifestSummary(
                    packageName = "com.sample.legacy",
                    applicationLabel = "Legacy",
                    versionCode = 1,
                    versionName = "1.0",
                    sharedUserId = null,
                    debuggable = false,
                    usesCleartextTraffic = false,
                    allowBackup = true,
                    permissions = emptyList(),
                    exportedComponentsCount = 0,
                    launcherCategoriesCount = 1,
                    authorityStrings = emptyList(),
                    minSdkVersion = 19,
                    launcherActivityName = "com.sample.legacy.MainActivity"
                ),
                hasSplitApks = false,
                hasDex = true,
                nativeAbis = emptyList()
            )
            assertEquals(CompatibilityStatus.COMPATIBLE, legacyCompat.status)
            assertTrue(legacyCompat.warnings.any { it.contains("minSdkVersion=19") })
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun createSamplePng(w: Int, h: Int, color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun buildLauncherAxml(
        packageName: String,
        appLabel: String,
        mainActivityRawName: String?,
        authority: String,
        minSdk: Int
    ): ByteArray {
        val strings = mutableListOf(
            "package", "minSdkVersion", "label", "name", "exported", "authorities",
            "http://schemas.android.com/apk/res/android", "android",
            "manifest", "uses-sdk", "application", "provider", authority,
            packageName, appLabel
        )
        if (mainActivityRawName != null) {
            strings.addAll(
                listOf(
                    "activity", "intent-filter", "action", "category",
                    mainActivityRawName,
                    "android.intent.action.MAIN",
                    "android.intent.category.LAUNCHER"
                )
            )
        }
        val resIds = listOf(
            0x00000000, // package (non-res attr index 0)
            0x0101020c, // minSdkVersion
            0x01010001, // label
            0x01010003, // name
            0x01010010, // exported
            0x01010018  // authorities
        )
        fun idx(s: String) = strings.indexOf(s)
        val nsAndroid = idx("http://schemas.android.com/apk/res/android")
        val prefixAndroid = idx("android")

        val poolChunk = buildUtf8StringPool(strings)
        val resIdChunk = ByteBuffer.allocate(8 + resIds.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0180)
            putShort(8)
            putInt(8 + resIds.size * 4)
            resIds.forEach { putInt(it) }
        }.array()

        val chunks = ByteArrayOutputStream()

        fun nsChunk(type: Int) {
            val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(type.toShort()); putShort(16); putInt(24); putInt(1); putInt(-1)
                putInt(prefixAndroid); putInt(nsAndroid)
            }
            chunks.write(buf.array())
        }

        // Triple(ns, nameIdx, Pair(type, data))
        fun startTag(tag: String, attrs: List<Triple<Int, Int, Pair<Int, Int>>>) {
            val size = 36 + attrs.size * 20
            val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(0x0102); putShort(16); putInt(size); putInt(1); putInt(-1); putInt(-1); putInt(idx(tag))
                putShort(20); putShort(20); putShort(attrs.size.toShort()); putShort(0); putShort(0); putShort(0)
                for ((ns, nameIdx, typeData) in attrs) {
                    val (t, d) = typeData
                    putInt(ns)
                    putInt(nameIdx)
                    putInt(if (t == 0x03) d else -1)
                    putShort(8)
                    put(0)
                    put(t.toByte())
                    putInt(d)
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

        nsChunk(0x0100)
        startTag("manifest", listOf(Triple(-1, idx("package"), 0x03 to idx(packageName))))
        startTag("uses-sdk", listOf(Triple(nsAndroid, idx("minSdkVersion"), 0x10 to minSdk)))
        endTag("uses-sdk")
        startTag("application", listOf(Triple(nsAndroid, idx("label"), 0x03 to idx(appLabel))))

        if (mainActivityRawName != null) {
            startTag(
                "activity",
                listOf(
                    Triple(nsAndroid, idx("name"), 0x03 to idx(mainActivityRawName)),
                    Triple(nsAndroid, idx("exported"), 0x12 to -1)
                )
            )
            startTag("intent-filter", emptyList())
            startTag("action", listOf(Triple(nsAndroid, idx("name"), 0x03 to idx("android.intent.action.MAIN"))))
            endTag("action")
            startTag("category", listOf(Triple(nsAndroid, idx("name"), 0x03 to idx("android.intent.category.LAUNCHER"))))
            endTag("category")
            endTag("intent-filter")
            endTag("activity")
        }

        startTag("provider", listOf(Triple(nsAndroid, idx("authorities"), 0x03 to idx(authority))))
        endTag("provider")
        endTag("application")
        endTag("manifest")
        nsChunk(0x0101)

        val body = poolChunk + resIdChunk + chunks.toByteArray()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0003); putShort(8); putInt(8 + body.size)
        }.array()
        return header + body
    }

    private fun buildUtf8StringPool(strings: List<String>): ByteArray {
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
        val totalSize = stringsStart + payload.size
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0001)
            putShort(headerSize.toShort())
            putInt(totalSize)
            putInt(strings.size)
            putInt(0)
            putInt(0x00000100)
            putInt(stringsStart)
            putInt(0)
            offsets.forEach { putInt(it) }
            put(payload)
        }
        return buf.array()
    }
}
