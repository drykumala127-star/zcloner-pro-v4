package com.fms.zclone.enginecoremode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.fms.clone.CloneBuildRequest
import com.fms.clone.FMSAPKInspector
import com.fms.clone.FMSManifestEngine
import com.fms.clone.FMSRepackager
import com.fms.clone.FMSSignatureManager
import com.fms.core.FmsLogger
import com.fms.task.FMSBackgroundEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
class StabilityRegressionTest {

    @Test
    fun `repackager preserves nine-patch and non-launcher drawables and streams multi-megabyte apksignerv2`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "stability_stream_${System.nanoTime()}").apply { mkdirs() }

        try {
            val sourceApk = File(workDir, "multi_mb_source.apk")
            val clonedApk = File(workDir, "multi_mb_cloned.apk")

            val ninePatchBytes = createSamplePng(24, 24, Color.MAGENTA) + byteArrayOf(0x6E, 0x70, 0x54, 0x63)
            val internalMenuBytes = createSamplePng(24, 24, Color.YELLOW)
            val launcherBgBytes = createSamplePng(48, 48, Color.DKGRAY)
            val launcherFgBytes = createSamplePng(48, 48, Color.BLUE)
            val resArsc = byteArrayOf(0x02, 0x00, 0x0C, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
            val axml = buildComplexManifestAxml(
                packageName = "com.stability.source",
                appLabel = "StabilitySample",
                disabledActivity = ".DisabledFirstMain",
                enabledActivity = ".RealLauncherActivity",
                authorities = "com.stability.source.prov1;com.stability.source.prov2"
            )
            // 2.6 MB STORED asset guarantees multi-chunk (> 2 x 1 MB) v2 hashing and alignment streaming
            val largeStoredBlob = ByteArray(2_600_000) { i -> ((i * 31 + 7) and 0xFF).toByte() }

            ZipOutputStream(sourceApk.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(axml)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("resources.arsc").apply {
                    method = ZipEntry.STORED
                    size = resArsc.size.toLong()
                    compressedSize = resArsc.size.toLong()
                    crc = CRC32().apply { update(resArsc) }.value
                })
                zos.write(resArsc)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("res/drawable-xhdpi-v4/btn_frame.9.png"))
                zos.write(ninePatchBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("res/drawable-xhdpi-v4/ic_menu_share.png"))
                zos.write(internalMenuBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("res/mipmap-xxhdpi-v4/ic_launcher_background.png"))
                zos.write(launcherBgBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("res/mipmap-xxhdpi-v4/ic_launcher.png"))
                zos.write(launcherFgBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("assets/large_blob.bin").apply {
                    method = ZipEntry.STORED
                    size = largeStoredBlob.size.toLong()
                    compressedSize = largeStoredBlob.size.toLong()
                    crc = CRC32().apply { update(largeStoredBlob) }.value
                })
                zos.write(largeStoredBlob)
                zos.closeEntry()
            }

            // Verify manifest engine prefers enabled MAIN/LAUNCHER and reads semicolon-separated providers
            val inspector = FMSAPKInspector(context)
            val preInspection = inspector.inspectApkFile(sourceApk).getOrThrow()
            assertEquals("com.stability.source.RealLauncherActivity", preInspection.manifestSummary.launcherActivityName)
            assertTrue("res/drawable-xhdpi-v4/btn_frame.9.png" !in preInspection.iconEntryPaths)

            val buildOut = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = "com.stability.source",
                    targetPackage = "com.stability.source.zclone1",
                    sourceAppName = "StabilitySample",
                    cloneName = "StabilitySample Clone",
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

            assertEquals(1, buildOut.patchedIconsCount)
            assertEquals("com.stability.source.RealLauncherActivity", buildOut.dhedryTargetActivity)
            assertTrue(FMSSignatureManager.verifyApkContainsValidSignature(clonedApk))
            assertTrue(FMSSignatureManager.isEntryDataAligned(clonedApk, "assets/large_blob.bin", 4))

            ZipFile(clonedApk).use { zip ->
                val outNine = zip.getInputStream(zip.getEntry("res/drawable-xhdpi-v4/btn_frame.9.png")).use { it.readBytes() }
                val outMenu = zip.getInputStream(zip.getEntry("res/drawable-xhdpi-v4/ic_menu_share.png")).use { it.readBytes() }
                val outBg = zip.getInputStream(zip.getEntry("res/mipmap-xxhdpi-v4/ic_launcher_background.png")).use { it.readBytes() }
                val outFg = zip.getInputStream(zip.getEntry("res/mipmap-xxhdpi-v4/ic_launcher.png")).use { it.readBytes() }
                val outBlob = zip.getInputStream(zip.getEntry("assets/large_blob.bin")).use { it.readBytes() }

                assertArrayEquals("NinePatch bytes must stay untouched", ninePatchBytes, outNine)
                assertArrayEquals("Non-launcher menu icon must stay untouched", internalMenuBytes, outMenu)
                assertArrayEquals("Adaptive background must stay untouched", launcherBgBytes, outBg)
                assertTrue("Launcher icon should be hue-shifted", !launcherFgBytes.contentEquals(outFg))
                assertArrayEquals("Large streamed STORED payload must remain intact", largeStoredBlob, outBlob)

                val outManifestBytes = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
                val outSummary = FMSManifestEngine.inspectManifest(outManifestBytes).getOrThrow()
                assertTrue(
                    outSummary.authorityStrings.contains("com.stability.source.zclone1.prov1;com.stability.source.zclone1.prov2")
                )
            }

            val apkSignerBin = File("/opt/android/sdk/build-tools/36.0.0/apksigner")
            if (apkSignerBin.isFile && apkSignerBin.canExecute()) {
                val proc = ProcessBuilder(
                    apkSignerBin.absolutePath,
                    "verify",
                    "--min-sdk-version",
                    "24",
                    "--verbose",
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
                val xmlTree = proc.inputStream.bufferedReader().readText()
                assertEquals(0, proc.waitFor())
                assertTrue(xmlTree.contains("launchMode") && xmlTree.contains("0x0"))
                assertTrue(xmlTree.contains("com.stability.source.RealLauncherActivity"))
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `logger and background engine remain consistent under concurrent writes and shutdown`() = runBlocking {
        FmsLogger.clear()
        val jobs = (1..4).map { worker ->
            async(Dispatchers.Default) {
                repeat(80) { i ->
                    FmsLogger.info("StabilityTest", "worker=$worker item=$i token=secret_$i")
                }
            }
        }
        jobs.awaitAll()
        val logs = FmsLogger.logsFlow.value
        assertEquals(200, logs.size)
        assertTrue(logs.all { it.message.contains("token=[REDACTED]") })

        val engine = FMSBackgroundEngine(maxParallelJobs = 2)
        engine.enqueue("Unit Job", { update ->
            update(0.5f, "Running")
            update(1f, "Done")
        })
        engine.shutdown()
    }

    private fun createSamplePng(w: Int, h: Int, color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun buildComplexManifestAxml(
        packageName: String,
        appLabel: String,
        disabledActivity: String,
        enabledActivity: String,
        authorities: String
    ): ByteArray {
        val strings = listOf(
            "package", "minSdkVersion", "label", "name", "exported", "enabled", "authorities",
            "http://schemas.android.com/apk/res/android", "android",
            "manifest", "uses-sdk", "application", "activity", "intent-filter", "action", "category", "provider",
            packageName, appLabel,
            disabledActivity, enabledActivity,
            "android.intent.action.MAIN", "android.intent.category.LAUNCHER",
            authorities
        )
        val resIds = listOf(
            0x00000000, // package
            0x0101020c, // minSdkVersion
            0x01010001, // label
            0x01010003, // name
            0x01010010, // exported
            0x0101000e, // enabled
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
        startTag("uses-sdk", listOf(Triple(nsAndroid, idx("minSdkVersion"), 0x10 to 24)))
        endTag("uses-sdk")
        startTag("application", listOf(Triple(nsAndroid, idx("label"), 0x03 to idx(appLabel))))

        // First launcher activity is disabled (must NOT win over real enabled activity)
        startTag(
            "activity",
            listOf(
                Triple(nsAndroid, idx("name"), 0x03 to idx(disabledActivity)),
                Triple(nsAndroid, idx("exported"), 0x12 to -1),
                Triple(nsAndroid, idx("enabled"), 0x12 to 0)
            )
        )
        startTag("intent-filter", emptyList())
        startTag("action", listOf(Triple(nsAndroid, idx("name"), 0x03 to idx("android.intent.action.MAIN"))))
        endTag("action")
        startTag("category", listOf(Triple(nsAndroid, idx("name"), 0x03 to idx("android.intent.category.LAUNCHER"))))
        endTag("category")
        endTag("intent-filter")
        endTag("activity")

        // Real enabled launcher activity
        startTag(
            "activity",
            listOf(
                Triple(nsAndroid, idx("name"), 0x03 to idx(enabledActivity)),
                Triple(nsAndroid, idx("exported"), 0x12 to -1),
                Triple(nsAndroid, idx("enabled"), 0x12 to -1)
            )
        )
        startTag("intent-filter", emptyList())
        startTag("action", listOf(Triple(nsAndroid, idx("name"), 0x03 to idx("android.intent.action.MAIN"))))
        endTag("action")
        startTag("category", listOf(Triple(nsAndroid, idx("name"), 0x03 to idx("android.intent.category.LAUNCHER"))))
        endTag("category")
        endTag("intent-filter")
        endTag("activity")

        startTag("provider", listOf(Triple(nsAndroid, idx("authorities"), 0x03 to idx(authorities))))
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
