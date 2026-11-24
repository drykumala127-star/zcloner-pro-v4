package com.fms.zclone.enginecoremode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.fms.clone.ApkInspectionResult
import com.fms.clone.CloneBuildRequest
import com.fms.clone.FMSAPKInspector
import com.fms.clone.FMSIconEngine
import com.fms.clone.FMSManifestEngine
import com.fms.clone.FMSRepackager
import com.fms.clone.FMSSecurityScanner
import com.fms.clone.FMSSignatureManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FmsCloneEngineTest {

    @Test
    fun `full apk repackaging pipeline mutates manifest icons strips permissions and verifies pkcs7 signature`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "clone_test_${System.nanoTime()}").apply { mkdirs() }
        val sourceApk = File(workDir, "sample_source.apk")
        val clonedApk = File(workDir, "sample_cloned.apk")

        val manifestAxml = buildMinimalTestAxml(
            packageName = "com.sample.source",
            label = "SampleApp",
            authority = "com.sample.source.fileprovider",
            permission = "android.permission.CAMERA"
        )
        val iconPng = createSamplePng(48, 48, Color.GREEN)

        ZipOutputStream(sourceApk.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(manifestAxml)
            zos.closeEntry()

            val resArscPayload = byteArrayOf(0x02, 0x00, 0x0C, 0x00, 0x11, 0x22, 0x33, 0x44)
            val resEntry = ZipEntry("resources.arsc").apply {
                method = ZipEntry.STORED
                size = resArscPayload.size.toLong()
                compressedSize = resArscPayload.size.toLong()
                crc = java.util.zip.CRC32().apply { update(resArscPayload) }.value
            }
            zos.putNextEntry(resEntry)
            zos.write(resArscPayload)
            zos.closeEntry()

            val longIconPath = "res/drawable-xxhdpi-v4/ic_launcher_very_long_entry_name_for_jar_line_wrapping_check.png"
            zos.putNextEntry(ZipEntry(longIconPath))
            zos.write(iconPng)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00))
            zos.closeEntry()
        }

        // 1. Inspect original APK
        val inspector = FMSAPKInspector(context)
        val inspection = inspector.inspectApkFile(sourceApk, hasSplitApksHint = false).getOrThrow()
        assertEquals("com.sample.source", inspection.packageName)
        assertEquals("SampleApp", inspection.appName)
        assertTrue(inspection.manifestSummary.permissions.contains("android.permission.CAMERA"))
        assertEquals(1, inspection.manifestSummary.launcherCategoriesCount)
        assertTrue(inspection.manifestSummary.debuggable)
        val extractedIcon = inspector.extractIconBitmapFromApk(sourceApk, inspection.iconEntryPaths)
        assertTrue(extractedIcon != null && extractedIcon.width > 0)

        // 2. Security Scanner test
        val scan = FMSSecurityScanner.scan(inspection)
        assertTrue(scan.debuggableFlag)
        assertTrue(scan.riskScore >= 25)
        assertTrue(scan.findings.isNotEmpty())

        // 3. Build Cloned APK
        val buildOutput = FMSRepackager.buildClonedApk(
            request = CloneBuildRequest(
                sourceApkPath = sourceApk.absolutePath,
                sourcePackage = "com.sample.source",
                targetPackage = "com.sample.source.zclone1",
                sourceAppName = "SampleApp",
                cloneName = "SampleApp Clone",
                iconPresetId = "neon_cyan",
                iconHueShift = 180,
                changePackageName = true,
                hideFromLauncher = true,
                isolateData = true,
                strippedPermissions = setOf("android.permission.CAMERA"),
                disableDebuggable = true,
                rewriteProviderAuthorities = true
            ),
            privateFilesDir = workDir,
            outputFile = clonedApk
        ).getOrThrow()

        assertTrue(clonedApk.exists())
        assertTrue(buildOutput.modifiedManifest)
        assertEquals(1, buildOutput.patchedIconsCount)
        assertEquals(1, buildOutput.strippedPermissionsCount)
        assertTrue(buildOutput.signatureVerified)
        assertTrue(FMSSignatureManager.verifyApkContainsValidSignature(clonedApk))
        assertTrue(FMSSignatureManager.isEntryDataAligned(clonedApk, "resources.arsc", 4))

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
            val verifyOutput = proc.inputStream.bufferedReader().readText()
            assertEquals("apksigner verify failed:\n$verifyOutput", 0, proc.waitFor())
            assertTrue(
                "Expected APK Signature Scheme v2 verification to pass:\n$verifyOutput",
                verifyOutput.contains("Verified using v2 scheme (APK Signature Scheme v2): true")
            )
        }

        // 4. Verify signature fails if MANIFEST.MF or ZCLONE.SF is tampered with
        val tamperedApk = File(workDir, "tampered.apk")
        ZipFile(clonedApk).use { inZip ->
            ZipOutputStream(tamperedApk.outputStream()).use { outZip ->
                val entries = inZip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    var bytes = inZip.getInputStream(e).use { it.readBytes() }
                    if (e.name == "META-INF/MANIFEST.MF") {
                        bytes = (String(bytes, Charsets.UTF_8) + "\r\n").toByteArray(Charsets.UTF_8)
                    }
                    outZip.putNextEntry(ZipEntry(e.name))
                    outZip.write(bytes)
                    outZip.closeEntry()
                }
            }
        }
        assertFalse(FMSSignatureManager.verifyApkContainsValidSignature(tamperedApk))

        // 5. Re-inspect cloned AndroidManifest.xml inside output APK
        val clonedSummary = ZipFile(clonedApk).use { zip ->
            val manifestBytes = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
            FMSManifestEngine.inspectManifest(manifestBytes).getOrThrow()
        }
        assertEquals("com.sample.source.zclone1", clonedSummary.packageName)
        assertEquals("SampleApp Clone", clonedSummary.applicationLabel)
        assertFalse(clonedSummary.debuggable)
        assertFalse(clonedSummary.permissions.contains("android.permission.CAMERA"))
        assertEquals(0, clonedSummary.launcherCategoriesCount)
        assertTrue(clonedSummary.authorityStrings.contains("com.sample.source.zclone1.fileprovider"))

        workDir.deleteRecursively()
    }

    @Test
    fun `icon engine transforms png bytes cleanly`() {
        val raw = createSamplePng(48, 48, Color.BLUE)
        val transformed = FMSIconEngine.transformIconPng(raw, hueShiftDegrees = 180, roundedBadge = true)
        assertTrue(transformed.isNotEmpty())
        assertFalse(raw.contentEquals(transformed))
    }

    private fun createSamplePng(w: Int, h: Int, color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /**
     * Builds a valid minimal Android Binary XML (AXML) file matching AXML chunk format.
     */
    private fun buildMinimalTestAxml(
        packageName: String,
        label: String,
        authority: String,
        permission: String
    ): ByteArray {
        val strings = listOf(
            "manifest", "package", packageName,
            "uses-permission", "name", permission,
            "application", "label", label, "debuggable",
            "activity", "intent-filter", "category", "android.intent.category.LAUNCHER",
            "provider", "authorities", authority
        )
        fun idx(s: String) = strings.indexOf(s)

        val strPoolChunk = buildUtf8StringPool(strings)
        val chunks = ByteArrayOutputStream()

        fun startTag(tag: String, attrs: List<Triple<Int, Int, Int>>) {
            // Triple(nameIdx, dataType, dataValue)
            val size = 36 + attrs.size * 20
            val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(0x0102)
            buf.putShort(16)
            buf.putInt(size)
            buf.putInt(1)
            buf.putInt(-1)
            buf.putInt(-1)
            buf.putInt(idx(tag))
            buf.putShort(20)
            buf.putShort(20)
            buf.putShort(attrs.size.toShort())
            buf.putShort(0)
            buf.putShort(0)
            buf.putShort(0)
            for ((nameIdx, type, data) in attrs) {
                buf.putInt(-1)
                buf.putInt(nameIdx)
                buf.putInt(if (type == 0x03) data else -1)
                buf.putShort(8)
                buf.put(0)
                buf.put(type.toByte())
                buf.putInt(data)
            }
            chunks.write(buf.array())
        }

        fun endTag(tag: String) {
            val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(0x0103)
            buf.putShort(16)
            buf.putInt(24)
            buf.putInt(1)
            buf.putInt(-1)
            buf.putInt(-1)
            buf.putInt(idx(tag))
            chunks.write(buf.array())
        }

        startTag("manifest", listOf(Triple(idx("package"), 0x03, idx(packageName))))
        startTag("uses-permission", listOf(Triple(idx("name"), 0x03, idx(permission))))
        endTag("uses-permission")
        startTag(
            "application",
            listOf(
                Triple(idx("label"), 0x03, idx(label)),
                Triple(idx("debuggable"), 0x12, -1) // true
            )
        )
        startTag("activity", emptyList())
        startTag("intent-filter", emptyList())
        startTag("category", listOf(Triple(idx("name"), 0x03, idx("android.intent.category.LAUNCHER"))))
        endTag("category")
        endTag("intent-filter")
        endTag("activity")
        startTag("provider", listOf(Triple(idx("authorities"), 0x03, idx(authority))))
        endTag("provider")
        endTag("application")
        endTag("manifest")

        val body = strPoolChunk + chunks.toByteArray()
        val totalSize = 8 + body.size
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0003)
            putShort(8)
            putInt(totalSize)
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
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0001)
        buf.putShort(headerSize.toShort())
        buf.putInt(totalSize)
        buf.putInt(strings.size)
        buf.putInt(0)
        buf.putInt(0x00000100) // UTF-8
        buf.putInt(stringsStart)
        buf.putInt(0)
        offsets.forEach { buf.putInt(it) }
        buf.put(payload)
        return buf.array()
    }
}
