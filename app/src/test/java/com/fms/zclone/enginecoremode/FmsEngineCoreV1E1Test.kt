package com.fms.zclone.enginecoremode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.fms.clone.CloneBuildRequest
import com.fms.clone.ElfDependencyParser
import com.fms.clone.FMSRepackager
import com.fms.clone.FmsBinaryVersionDiff
import com.fms.clone.FmsDependencyGraphAnalyzer
import com.fms.core.FMSCore
import com.fms.core.FMSRuntime
import com.fms.data.FMSDatabaseEngine
import com.fms.data.LocalBuildHistoryRecord
import com.fms.data.LocalCloneRecord
import com.fms.task.FMSBackgroundEngine
import com.fms.task.FmsJobState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FmsEngineCoreV1E1Test {

    private val aapt2Bin = File("/opt/android/sdk/build-tools/36.0.0/aapt2")
    private val d8Bin = File("/opt/android/sdk/build-tools/36.0.0/d8")
    private val apkSignerBin = File("/opt/android/sdk/build-tools/36.0.0/apksigner")
    private val androidJar = File("/opt/android/sdk/platforms/android-36/android.jar")
    private val javacBin = File("/usr/bin/javac")

    private fun requireSdkToolchain() {
        assumeTrue("aapt2 not present", aapt2Bin.isFile && aapt2Bin.canExecute())
        assumeTrue("d8 not present", d8Bin.isFile && d8Bin.canExecute())
        assumeTrue("apksigner not present", apkSignerBin.isFile && apkSignerBin.canExecute())
        assumeTrue("android.jar not present", androidJar.isFile)
        assumeTrue("javac not present", javacBin.isFile && javacBin.canExecute())
    }

    @Test
    fun `fms core ecosystem exposes FMS Engine Core V1 and E1 modules`() {
        assertEquals("FMS Engine Core V1", FMSCore.ECOSYSTEM_NAME)
        FMSCore.registerStandardCatalog()
        val statuses = FMSRuntime.initializeAll()
        assertTrue(statuses.any { it.descriptor.id == "fms.graph" })
        assertTrue(statuses.any { it.descriptor.id == "fms.repro" })
        assertTrue(statuses.any { it.descriptor.id == "fms.diff" })
    }

    @Test
    fun `reproducible builder produces identical SHA-256 across two identical real SDK clone builds`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "e1_repro_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sourcePkg = "com.fms.e1repro"
            val targetPkg = "com.fms.e1repro.zclone1"
            val sourceApk = buildMinimalRealApk(
                workDir = File(workDir, "src"),
                outApk = File(workDir, "src.apk"),
                packageName = sourcePkg,
                appName = "E1 Repro"
            )
            val privateFilesDir = File(workDir, "keystore_files").apply { mkdirs() }
            val outApk1 = File(workDir, "cloned_1.apk")
            val outApk2 = File(workDir, "cloned_2.apk")

            val req = CloneBuildRequest(
                sourceApkPath = sourceApk.absolutePath,
                sourcePackage = sourcePkg,
                targetPackage = targetPkg,
                sourceAppName = "E1 Repro",
                cloneName = "E1 Repro Clone",
                iconPresetId = "neon_cyan",
                iconHueShift = 180,
                changePackageName = true,
                hideFromLauncher = false,
                isolateData = true,
                strippedPermissions = emptySet(),
                ownerConfirmedModificationRights = true,
                injectDhedryLoading = true,
                reproducibleBuild = true,
                embedDependencyGraph = true
            )

            val res1 = FMSRepackager.buildClonedApk(req, privateFilesDir, outApk1, context).getOrThrow()
            Thread.sleep(1100L) // Ensure wall-clock differs while canonical ZIP timestamp stays fixed
            val res2 = FMSRepackager.buildClonedApk(req, privateFilesDir, outApk2, context).getOrThrow()

            assertTrue(res1.reproducibleBuildApplied)
            assertTrue(res1.dependencyGraphEmbedded)
            assertTrue(res1.signatureVerified)
            assertEquals("Two reproducible builds with same inputs & keystore must produce identical SHA-256", res1.outputSha256, res2.outputSha256)
            assertEquals(outApk1.readBytes().size, outApk2.readBytes().size)

            ZipFile(outApk1).use { zip ->
                assertNotNull(zip.getEntry(FMSRepackager.DHEDRY_DEPENDENCY_GRAPH_ENTRY))
                assertNotNull(zip.getEntry(FMSRepackager.DHEDRY_REPRODUCIBLE_BUILD_ENTRY))
                val repJson = JSONObject(zip.getInputStream(zip.getEntry(FMSRepackager.DHEDRY_REPRODUCIBLE_BUILD_ENTRY)).use { it.readBytes().toString(Charsets.UTF_8) })
                assertEquals("fms.reproducible_build.v1", repJson.getString("schema"))
                assertEquals(targetPkg, repJson.getString("targetPackage"))
            }

            val verifyOut = runCmd(apkSignerBin.absolutePath, "verify", "--verbose", "--print-certs", outApk1.absolutePath)
            assertTrue(verifyOut.contains("Verifies"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `dependency graph parses AXML components, queries, multidex, and real ELF64 DT_NEEDED libraries`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "e1_graph_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sourcePkg = "com.fms.e1graph"
            val baseApk = buildMinimalRealApk(
                workDir = File(workDir, "src"),
                outApk = File(workDir, "base.apk"),
                packageName = sourcePkg,
                appName = "E1 Graph",
                extraManifestChildren = """
                    <queries>
                        <package android:name="com.whatsapp" />
                        <intent>
                            <action android:name="android.intent.action.VIEW" />
                        </intent>
                    </queries>
                """.trimIndent(),
                extraAppChildren = """
                    <service android:name=".SyncService" android:exported="false" />
                    <provider android:name=".VaultProvider" android:authorities="$sourcePkg.vault" android:exported="false" />
                """.trimIndent(),
                extraEntries = mapOf(
                    "lib/arm64-v8a/libfms_native.so" to buildMinimalElf64SharedLib(listOf("libc.so", "liblog.so"))
                )
            )
            // Verify pure-Kotlin ELF parser directly and inside full APK analyzer
            val elfResult = ElfDependencyParser.parseNeededLibraries(buildMinimalElf64SharedLib(listOf("libc.so", "liblog.so")))
            assertTrue(elfResult.isElf)
            assertTrue(elfResult.isElf64)
            assertEquals(listOf("libc.so", "liblog.so"), elfResult.neededLibraries)

            val graph = FmsDependencyGraphAnalyzer.analyze(baseApk).getOrThrow()
            assertEquals(sourcePkg, graph.packageName)
            assertEquals(listOf("classes.dex"), graph.dexFiles)
            assertTrue(graph.queriedPackages.contains("com.whatsapp"))
            assertTrue(graph.queriedActions.contains("android.intent.action.VIEW"))
            assertTrue(graph.providerAuthorities.contains("$sourcePkg.vault"))
            assertTrue(graph.components.any { it.kind == "activity" && it.name.contains("MainActivity") && it.exported })
            assertTrue(graph.components.any { it.kind == "service" && it.name.contains("SyncService") && !it.exported })
            assertTrue(graph.components.any { it.kind == "provider" && it.name.contains("VaultProvider") })

            val nativeLib = graph.nativeLibs.firstOrNull { it.fileName == "libfms_native.so" }
            assertNotNull(nativeLib)
            assertEquals("arm64-v8a", nativeLib?.abi)
            assertEquals(listOf("libc.so", "liblog.so"), nativeLib?.neededLibraries)

            val graphJson = JSONObject(FmsDependencyGraphAnalyzer.toJsonString(graph))
            assertEquals("fms.dependency_graph.v1", graphJson.getString("schema"))
            assertTrue(graphJson.getJSONArray("nativeLibraries").length() == 1)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `binary version diff compares source APK vs cloned APK and exports valid JSON`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "e1_diff_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sourcePkg = "com.fms.e1diff"
            val targetPkg = "com.fms.e1diff.zclone1"
            val sourceApk = buildMinimalRealApk(
                workDir = File(workDir, "src"),
                outApk = File(workDir, "src.apk"),
                packageName = sourcePkg,
                appName = "E1 Diff"
            )
            val clonedApk = File(workDir, "cloned.apk")
            val req = CloneBuildRequest(
                sourceApkPath = sourceApk.absolutePath,
                sourcePackage = sourcePkg,
                targetPackage = targetPkg,
                sourceAppName = "E1 Diff",
                cloneName = "E1 Diff Clone",
                iconPresetId = "neon_cyan",
                iconHueShift = 180,
                changePackageName = true,
                hideFromLauncher = false,
                isolateData = true,
                strippedPermissions = emptySet(),
                ownerConfirmedModificationRights = true,
                injectDhedryLoading = true
            )
            FMSRepackager.buildClonedApk(req, File(workDir, "ks").apply { mkdirs() }, clonedApk, context).getOrThrow()

            val diff = FmsBinaryVersionDiff.compareApks(sourceApk, clonedApk).getOrThrow()
            assertEquals(sourcePkg, diff.packageA)
            assertEquals(targetPkg, diff.packageB)
            assertTrue(diff.addedDexFiles.contains("classes2.dex"))
            assertTrue(diff.addedDhedryModules.contains(FMSRepackager.DHEDRY_DEPENDENCY_GRAPH_ENTRY))
            assertTrue(diff.addedDhedryModules.contains(FMSRepackager.DHEDRY_REPRODUCIBLE_BUILD_ENTRY))
            assertTrue(diff.sizeDeltaBytes > 0L)

            val jsonObj = JSONObject(FmsBinaryVersionDiff.toJsonString(diff))
            assertEquals("fms.version_diff.v1", jsonObj.getString("schema"))
            assertEquals(targetPkg, jsonObj.getString("packageB"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `background engine cancelJob and repackager shouldCancel abort cleanly without corrupting files`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = FMSBackgroundEngine(maxParallelJobs = 1)
        val startedLatch = CountDownLatch(1)
        val jobId = engine.enqueue(
            title = "Cancelable Task",
            block = { update ->
                update(0.25f, "Working...")
                startedLatch.countDown()
                repeat(40) {
                    delay(50L)
                    update(0.5f, "Tick $it")
                }
            }
        )
        assertTrue(startedLatch.await(3, TimeUnit.SECONDS))
        assertTrue(engine.cancelJob(jobId))
        Thread.sleep(150L)
        val snap = engine.jobs.value.first { it.jobId == jobId }
        assertEquals(FmsJobState.CANCELLED, snap.state)
        engine.shutdown()

        val workDir = File(context.cacheDir, "e1_cancel_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sourceApk = buildMinimalRealApk(
                workDir = File(workDir, "src"),
                outApk = File(workDir, "src.apk"),
                packageName = "com.fms.e1cancel",
                appName = "E1 Cancel"
            )
            val outApk = File(workDir, "never_written.apk")
            val res = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = "com.fms.e1cancel",
                    targetPackage = "com.fms.e1cancel.zclone1",
                    sourceAppName = "E1 Cancel",
                    cloneName = "E1 Cancel Clone",
                    iconPresetId = "original",
                    iconHueShift = 0,
                    changePackageName = true,
                    hideFromLauncher = false,
                    isolateData = true,
                    strippedPermissions = setOf("android.permission.CAMERA"),
                    ownerConfirmedModificationRights = true,
                    injectDhedryLoading = true
                ),
                privateFilesDir = File(workDir, "ks").apply { mkdirs() },
                outputFile = outApk,
                context = context,
                shouldCancel = { true }
            )
            // Build with shouldCancel={true} throws CancellationException or returns failure
            assertTrue("Expected cancellation failure", res.isFailure && res.exceptionOrNull() is CancellationException)
            assertFalse("Target APK must not exist after cancelled build", outApk.exists())
        } catch (_: CancellationException) {
            // Expected cancellation path
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `time machine sqlite v2 persists snapshot_json and restores full clone recipe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FMSDatabaseEngine(context, "test_e1_tm_${System.nanoTime()}.db")
        try {
            val rec = LocalCloneRecord(
                id = "cfg_tm_1",
                sourcePackage = "com.example.tm",
                targetPackage = "com.example.tm.zclone2",
                sourceAppName = "TM App",
                cloneName = "TM Violet Clone",
                iconPresetId = "violet_glow",
                iconHueShift = 270,
                changePackageName = true,
                hideFromLauncher = true,
                isolateData = true,
                strippedPermissionsCsv = "android.permission.CAMERA,android.permission.RECORD_AUDIO",
                compatibilityStatus = "COMPATIBLE",
                buildStatus = "BUILT",
                outputApkPath = "/tmp/tm.apk",
                updatedAtMs = 9999L
            )
            val snapJson = FMSDatabaseEngine.encodeCloneSnapshotJson(rec)
            val hist = LocalBuildHistoryRecord(
                id = "bld_tm_1",
                configId = rec.id,
                sourcePackage = rec.sourcePackage,
                targetPackage = rec.targetPackage,
                status = "VERIFIED",
                outputPath = "/tmp/tm.apk",
                sha256 = "DEADBEAF",
                createdAtMs = 123456L,
                configSnapshotJson = snapJson
            )
            db.insertBuildHistory(hist)
            val loaded = db.listBuildHistory().first { it.id == "bld_tm_1" }
            assertEquals(snapJson, loaded.configSnapshotJson)

            val restored = FMSDatabaseEngine.decodeCloneSnapshotJson(loaded.configSnapshotJson, loaded)
            assertNotNull(restored)
            assertEquals("TM Violet Clone", restored?.cloneName)
            assertEquals(270, restored?.iconHueShift)
            assertEquals(true, restored?.hideFromLauncher)
            assertEquals("android.permission.CAMERA,android.permission.RECORD_AUDIO", restored?.strippedPermissionsCsv)
        } finally {
            db.close()
        }
    }

    private fun buildMinimalRealApk(
        workDir: File,
        outApk: File,
        packageName: String,
        appName: String,
        extraManifestChildren: String = "",
        extraAppChildren: String = "",
        extraEntries: Map<String, ByteArray> = emptyMap()
    ): File {
        workDir.mkdirs()
        val resDir = File(workDir, "res").apply { mkdirs() }
        val valuesDir = File(resDir, "values").apply { mkdirs() }
        File(valuesDir, "strings.xml").writeText("<resources><string name=\"app_name\">$appName</string></resources>")
        val mipmapDir = File(resDir, "mipmap-xxhdpi").apply { mkdirs() }
        val iconBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(iconBmp).drawColor(Color.rgb(0, 200, 255))
        File(mipmapDir, "ic_launcher.png").outputStream().use { iconBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val manifestFile = File(workDir, "AndroidManifest.xml")
        manifestFile.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$packageName"
                android:versionCode="1"
                android:versionName="1.0">
                <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36" />
                <uses-permission android:name="android.permission.INTERNET" />
                $extraManifestChildren
                <application android:label="@string/app_name" android:icon="@mipmap/ic_launcher">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    $extraAppChildren
                </application>
            </manifest>
            """.trimIndent().trimStart()
        )

        val compiledDir = File(workDir, "compiled").apply { mkdirs() }
        runCmd(aapt2Bin.absolutePath, "compile", "--dir", resDir.absolutePath, "-o", File(compiledDir, "res.zip").absolutePath)
        val linkedApk = File(workDir, "linked.apk")
        runCmd(
            aapt2Bin.absolutePath, "link",
            "-o", linkedApk.absolutePath,
            "-I", androidJar.absolutePath,
            "--manifest", manifestFile.absolutePath,
            File(compiledDir, "res.zip").absolutePath
        )

        val srcDir = File(workDir, "src").apply { mkdirs() }
        val clsDir = File(workDir, "cls").apply { mkdirs() }
        val pkgRel = packageName.replace('.', '/')
        val actFile = File(srcDir, "$pkgRel/MainActivity.java").apply { parentFile?.mkdirs() }
        actFile.writeText(
            """
            package $packageName;
            import android.app.Activity;
            import android.os.Bundle;
            public class MainActivity extends Activity {
                @Override protected void onCreate(Bundle b) { super.onCreate(b); }
            }
            """.trimIndent()
        )
        runCmd(javacBin.absolutePath, "-source", "11", "-target", "11", "-Xlint:-options", "-classpath", androidJar.absolutePath, "-d", clsDir.absolutePath, actFile.absolutePath)
        val dexOutDir = File(workDir, "dex").apply { mkdirs() }
        val classFiles = clsDir.walkTopDown().filter { it.isFile && it.extension == "class" }.map { it.absolutePath }.toList()
        runCmd(d8Bin.absolutePath, "--min-api", "24", "--lib", androidJar.absolutePath, "--output", dexOutDir.absolutePath, *classFiles.toTypedArray())

        val dexBytes = File(dexOutDir, "classes.dex").readBytes()
        ZipFile(linkedApk).use { srcZip ->
            ZipOutputStream(outApk.outputStream()).use { zos ->
                val en = srcZip.entries()
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    if (e.isDirectory) continue
                    val data = srcZip.getInputStream(e).use { it.readBytes() }
                    val copy = ZipEntry(e.name)
                    if (e.method == ZipEntry.STORED || e.name == "resources.arsc") {
                        copy.method = ZipEntry.STORED
                        copy.size = data.size.toLong()
                        copy.compressedSize = data.size.toLong()
                        copy.crc = CRC32().apply { update(data) }.value
                    }
                    zos.putNextEntry(copy)
                    zos.write(data)
                    zos.closeEntry()
                }
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(dexBytes)
                zos.closeEntry()
                for ((name, data) in extraEntries) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(data)
                    zos.closeEntry()
                }
            }
        }
        return outApk
    }

    /**
     * Synthesizes a valid minimal ELF64 little-endian shared library containing SHT_DYNAMIC + SHT_STRTAB
     * with real DT_NEEDED entries pointing to the specified shared library strings.
     */
    private fun buildMinimalElf64SharedLib(neededLibs: List<String>): ByteArray {
        val strTabBytes = ByteArrayOutputStream().apply {
            write(0)
            neededLibs.forEach { lib ->
                write(lib.toByteArray(Charsets.UTF_8))
                write(0)
            }
        }.toByteArray()
        val libOffsets = mutableListOf<Long>()
        var cursor = 1L
        neededLibs.forEach { lib ->
            libOffsets.add(cursor)
            cursor += lib.toByteArray(Charsets.UTF_8).size + 1
        }

        // Layout: [64B Elf64_Ehdr] [Dyn entries (neededLibs.size + 3)*16] [strTab] [3 * 64B Elf64_Shdr]
        val dynOff = 64L
        val dynCount = neededLibs.size + 3 // DT_STRTAB, DT_STRSZ, N*DT_NEEDED, DT_NULL
        val dynSize = dynCount * 16L
        val strOff = dynOff + dynSize
        val shOff = strOff + strTabBytes.size
        val totalSize = (shOff + 3 * 64L).toInt()

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        // Elf64_Ehdr
        buf.put(0, 0x7F.toByte())
        buf.put(1, 'E'.code.toByte())
        buf.put(2, 'L'.code.toByte())
        buf.put(3, 'F'.code.toByte())
        buf.put(4, 2) // ELFCLASS64
        buf.put(5, 1) // ELFDATA2LSB
        buf.put(6, 1) // EV_CURRENT
        buf.putShort(16, 3) // ET_DYN
        buf.putShort(18, 183.toShort()) // EM_AARCH64
        buf.putInt(20, 1)
        buf.putLong(40, shOff) // e_shoff
        buf.putShort(52, 64) // e_ehsize
        buf.putShort(58, 64) // e_shentsize
        buf.putShort(60, 3) // e_shnum

        // .dynamic entries at dynOff
        var p = dynOff.toInt()
        buf.putLong(p, 5L); buf.putLong(p + 8, strOff); p += 16 // DT_STRTAB
        buf.putLong(p, 10L); buf.putLong(p + 8, strTabBytes.size.toLong()); p += 16 // DT_STRSZ
        libOffsets.forEach { strIdx ->
            buf.putLong(p, 1L) // DT_NEEDED
            buf.putLong(p + 8, strIdx)
            p += 16
        }
        buf.putLong(p, 0L); buf.putLong(p + 8, 0L) // DT_NULL

        // .dynstr at strOff
        strTabBytes.forEachIndexed { idx, b -> buf.put(strOff.toInt() + idx, b) }

        // Section 0: SHT_NULL
        // Section 1: SHT_STRTAB (index = 1)
        val sh1 = (shOff + 64L).toInt()
        buf.putInt(sh1 + 4, 3) // SHT_STRTAB
        buf.putLong(sh1 + 16, strOff) // sh_addr
        buf.putLong(sh1 + 24, strOff) // sh_offset
        buf.putLong(sh1 + 32, strTabBytes.size.toLong()) // sh_size

        // Section 2: SHT_DYNAMIC (index = 2, sh_link = 1)
        val sh2 = (shOff + 128L).toInt()
        buf.putInt(sh2 + 4, 6) // SHT_DYNAMIC
        buf.putLong(sh2 + 16, dynOff) // sh_addr
        buf.putLong(sh2 + 24, dynOff) // sh_offset
        buf.putLong(sh2 + 32, dynSize) // sh_size
        buf.putInt(sh2 + 40, 1) // sh_link -> Section 1 (.dynstr)

        return buf.array()
    }

    private fun runCmd(vararg cmd: String): String {
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        if (code != 0) throw AssertionError("Command failed ($code): ${cmd.joinToString(" ")}\n$output")
        return output
    }
}
