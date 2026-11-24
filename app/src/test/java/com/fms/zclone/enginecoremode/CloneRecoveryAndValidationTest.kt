package com.fms.zclone.enginecoremode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.fms.clone.ApkValidationStep
import com.fms.clone.CloneBuildRequest
import com.fms.clone.CompatibilityStatus
import com.fms.clone.FMSAPKInspector
import com.fms.clone.FMSCloneValidator
import com.fms.clone.FMSCompatibilityEngine
import com.fms.clone.FMSManifestEngine
import com.fms.clone.FMSRepackager
import com.fms.clone.FMSSignatureManager
import com.fms.clone.FmsCloneValidationException
import com.fms.clone.ValidationCheckCategory
import com.fms.clone.ValidationCheckStatus
import com.fms.data.FMSDatabaseEngine
import com.fms.data.FMSPreferenceEngine
import com.fms.data.FMSStorageEngine
import com.fms.device.FMSDeviceManager
import com.fms.task.FMSBackgroundEngine
import com.fms.zclone.enginecoremode.zclone.data.ZCloneCloudRepository
import com.fms.zclone.enginecoremode.zclone.ui.ZCloneViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class CloneRecoveryAndValidationTest {

    @Test
    fun `rewrites custom and androidx dynamic permissions to avoid duplicate permission install failure and startup crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "recovery_perm_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sourcePkg = "com.perm.origin"
            val targetPkg = "com.perm.origin.zclone1"
            val sourceApk = File(workDir, "perm_source.apk")
            val clonedApk = File(workDir, "perm_cloned.apk")

            val axml = buildRecoveryAxml(
                packageName = sourcePkg,
                appLabel = "PermApp",
                minSdk = 24,
                targetSdk = 35,
                extractNativeLibs = true,
                customPermName = "$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                usesCustomPerm = "$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                componentPerm = "$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                authorityIsReference = false,
                literalAuthority = "$sourcePkg.fileprovider"
            )
            writeSampleApk(
                outFile = sourceApk,
                axml = axml,
                nativeEntries = emptyMap()
            )

            val inspector = FMSAPKInspector(context)
            val pre = inspector.inspectApkFile(sourceApk).getOrThrow()
            assertTrue(
                pre.manifestSummary.customPermissions.contains("$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
            )

            val out = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = sourcePkg,
                    targetPackage = targetPkg,
                    sourceAppName = "PermApp",
                    cloneName = "PermApp Clone",
                    iconPresetId = "neon_cyan",
                    iconHueShift = 120,
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

            val report = out.validationReport
            assertNotNull(report)
            assertTrue(report!!.passedAutomatedChecks)
            assertTrue(report.canInstallOnDevice)
            assertEquals(10, report.steps.size)
            assertTrue(
                report.steps.filter { it.category == ValidationCheckCategory.AUTOMATED_BUILD }
                    .all { it.status == ValidationCheckStatus.PASS || it.status == ValidationCheckStatus.WARNING }
            )
            assertTrue(
                report.steps.filter { it.category == ValidationCheckCategory.DEVICE_RUNTIME }
                    .all { it.status == ValidationCheckStatus.PENDING_DEVICE_TEST }
            )

            ZipFile(clonedApk).use { zip ->
                val outAxml = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
                val summary = FMSManifestEngine.validateBinaryAxml(outAxml).getOrThrow()
                assertEquals(targetPkg, summary.packageName)
                assertTrue(summary.customPermissions.contains("$targetPkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
                assertFalse(summary.customPermissions.contains("$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
                assertTrue(summary.permissions.contains("$targetPkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
                assertFalse(summary.permissions.contains("$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
                assertTrue(summary.authorityStrings.contains("$targetPkg.fileprovider"))
            }

            val apkSignerBin = File("/opt/android/sdk/build-tools/36.0.0/apksigner")
            if (apkSignerBin.isFile && apkSignerBin.canExecute()) {
                val proc = ProcessBuilder(
                    apkSignerBin.absolutePath,
                    "verify",
                    "--min-sdk-version",
                    "24",
                    clonedApk.absolutePath
                ).redirectErrorStream(true).start()
                val verifyOut = proc.inputStream.bufferedReader().readText()
                assertEquals("apksigner verify failed:\n$verifyOut", 0, proc.waitFor())
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `detects reference provider authority warning across inspector and validator`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "recovery_ref_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sourcePkg = "com.refprov.src"
            val sourceApk = File(workDir, "ref_source.apk")
            val clonedApk = File(workDir, "ref_cloned.apk")

            val axml = buildRecoveryAxml(
                packageName = sourcePkg,
                appLabel = "RefProv",
                minSdk = 24,
                targetSdk = 35,
                extractNativeLibs = true,
                customPermName = null,
                usesCustomPerm = null,
                componentPerm = null,
                authorityIsReference = true,
                literalAuthority = null
            )
            writeSampleApk(outFile = sourceApk, axml = axml, nativeEntries = emptyMap())

            val inspector = FMSAPKInspector(context)
            val pre = inspector.inspectApkFile(sourceApk).getOrThrow()
            assertEquals(1, pre.manifestSummary.referenceAuthoritiesCount)
            assertTrue(pre.compatibility.warnings.any { it.contains("@string") || it.contains("CONFLICTING_PROVIDER") })

            val out = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = sourcePkg,
                    targetPackage = "$sourcePkg.zclone1",
                    sourceAppName = "RefProv",
                    cloneName = "RefProv Clone",
                    iconPresetId = "neon_cyan",
                    iconHueShift = 90,
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

            assertTrue(out.warnings.any { it.contains("@string") })
            val stage2 = out.validationReport!!.steps.first { it.stageNumber == 2 }
            assertEquals(ValidationCheckStatus.WARNING, stage2.status)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `extractNativeLibs false keeps native so STORED page-aligned and rejects ABI mismatch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "recovery_abi_${System.nanoTime()}").apply { mkdirs() }
        try {
            val runtimeAbi = Build.SUPPORTED_ABIS?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "armeabi-v7a"
            val sourcePkg = "com.native.sample"
            val sourceApk = File(workDir, "native_source.apk")
            val clonedApk = File(workDir, "native_cloned.apk")

            val axml = buildRecoveryAxml(
                packageName = sourcePkg,
                appLabel = "NativeApp",
                minSdk = 24,
                targetSdk = 35,
                extractNativeLibs = false,
                customPermName = null,
                usesCustomPerm = null,
                componentPerm = null,
                authorityIsReference = false,
                literalAuthority = "$sourcePkg.provider"
            )
            val fakeSo = ByteArray(4096) { (it and 0xFF).toByte() }
            val soEntryPath = "lib/$runtimeAbi/libsample.so"
            writeSampleApk(
                outFile = sourceApk,
                axml = axml,
                nativeEntries = mapOf(soEntryPath to fakeSo)
            )

            val out = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = sourcePkg,
                    targetPackage = "$sourcePkg.zclone1",
                    sourceAppName = "NativeApp",
                    cloneName = "NativeApp Clone",
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

            ZipFile(clonedApk).use { zip ->
                val so = zip.getEntry(soEntryPath)
                assertNotNull(so)
                assertEquals(ZipEntry.STORED, so!!.method)
                assertArrayEquals(fakeSo, zip.getInputStream(so).use { it.readBytes() })
            }
            assertTrue(FMSSignatureManager.isEntryDataAligned(clonedApk, soEntryPath, 4096))

            // Verify explicit ABI mismatch rejection against incompatible CPU architecture list
            val compatMismatch = FMSCompatibilityEngine.analyze(
                manifest = FMSManifestEngine.inspectManifest(axml).getOrThrow(),
                hasSplitApks = false,
                hasDex = true,
                nativeAbis = listOf("arm64-v8a"),
                hasApkSigBlockV2Plus = true,
                deviceSdkInt = 35,
                deviceSupportedAbis = listOf("x86_64")
            )
            assertEquals(CompatibilityStatus.UNSUPPORTED, compatMismatch.status)
            assertTrue(compatMismatch.blockers.any { it.contains("ABI", ignoreCase = true) })

            val mismatchReport = FMSCloneValidator.validateBuiltCloneApk(
                apkFile = clonedApk,
                expectedTargetPackage = out.targetPackage,
                sourceManifest = FMSManifestEngine.inspectManifest(axml).getOrThrow(),
                sourceHasResArsc = true,
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = sourcePkg,
                    targetPackage = out.targetPackage,
                    sourceAppName = "NativeApp",
                    cloneName = "NativeApp Clone",
                    iconPresetId = "neon_cyan",
                    iconHueShift = 180,
                    changePackageName = true,
                    hideFromLauncher = false,
                    isolateData = true,
                    strippedPermissions = emptySet(),
                    ownerConfirmedModificationRights = true,
                    injectDhedryLoading = true
                ),
                expectDhedryInjected = true,
                expectedDhedryDexName = out.dhedryDexEntryName,
                deviceSdkInt = 35,
                deviceSupportedAbis = listOf("mismatch-abi")
            )
            assertFalse(mismatchReport.passedAutomatedChecks)
            assertEquals(ValidationCheckStatus.FAILED, mismatchReport.steps.first { it.stageNumber == 4 }.status)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `skips Dhedry multidex injection automatically on pre-Lollipop minSdk under 21`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "recovery_api19_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sourcePkg = "com.legacy.api19"
            val sourceApk = File(workDir, "api19_source.apk")
            val clonedApk = File(workDir, "api19_cloned.apk")

            val axml = buildRecoveryAxml(
                packageName = sourcePkg,
                appLabel = "Legacy19",
                minSdk = 19,
                targetSdk = 33,
                extractNativeLibs = true,
                customPermName = null,
                usesCustomPerm = null,
                componentPerm = null,
                authorityIsReference = false,
                literalAuthority = null
            )
            writeSampleApk(outFile = sourceApk, axml = axml, nativeEntries = emptyMap())

            val out = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = sourcePkg,
                    targetPackage = "$sourcePkg.zclone1",
                    sourceAppName = "Legacy19",
                    cloneName = "Legacy19 Clone",
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

            assertFalse(out.dhedrySplashInjected)
            assertTrue(out.warnings.any { it.contains("<21") || it.contains("minSdk < 21") })
            assertTrue(out.validationReport!!.passedAutomatedChecks)
            ZipFile(clonedApk).use { zip ->
                val outManifest = FMSManifestEngine.inspectManifest(
                    zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
                ).getOrThrow()
                assertEquals("com.legacy.api19.MainActivity", outManifest.launcherActivityName)
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `corrupt DEX is blocked before overwriting existing output or source APK and ViewModel records runtime stages`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "recovery_fail_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sourcePkg = "com.corrupt.dexapp"
            val sourceApk = File(workDir, "corrupt_source.apk")
            val existingOutput = File(workDir, "existing_output.apk")
            val sentinel = "ORIGINAL_VALID_OUTPUT_BACKUP".toByteArray()
            existingOutput.writeBytes(sentinel)

            val axml = buildRecoveryAxml(
                packageName = sourcePkg,
                appLabel = "BadDexApp",
                minSdk = 24,
                targetSdk = 35,
                extractNativeLibs = true,
                customPermName = null,
                usesCustomPerm = null,
                componentPerm = null,
                authorityIsReference = false,
                literalAuthority = null
            )
            writeSampleApk(
                outFile = sourceApk,
                axml = axml,
                nativeEntries = emptyMap(),
                dexBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04) // Invalid DEX header
            )
            val originalSourceHash = sourceApk.readBytes().contentHashCode()

            val res = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = sourcePkg,
                    targetPackage = "$sourcePkg.zclone1",
                    sourceAppName = "BadDexApp",
                    cloneName = "BadDexApp Clone",
                    iconPresetId = "neon_cyan",
                    iconHueShift = 180,
                    changePackageName = true,
                    hideFromLauncher = false,
                    isolateData = true,
                    strippedPermissions = emptySet(),
                    ownerConfirmedModificationRights = true,
                    injectDhedryLoading = false
                ),
                privateFilesDir = workDir,
                outputFile = existingOutput,
                context = context
            )
            assertTrue("Corrupt DEX build must fail validation", res.isFailure)
            val err = res.exceptionOrNull()
            assertTrue(err is FmsCloneValidationException)
            val validationEx = err as FmsCloneValidationException
            assertEquals(3, validationEx.failureInfo.failedStageNumber)
            assertTrue(validationEx.failureInfo.sourcePreservedNotice.contains("tetap aman"))
            assertTrue("Source APK must remain untouched", sourceApk.exists() && sourceApk.readBytes().contentHashCode() == originalSourceHash)
            assertArrayEquals("Existing output file must not be overwritten on validation failure", sentinel, existingOutput.readBytes())

            // Verify ViewModel device verification tracking (Stages 8..10)
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApplicationId("1:759195378420:android:0000000000000000")
                        .setApiKey("AIzaSyTestPlaceholderForJvmUnitOnly")
                        .setProjectId("zclone-fms")
                        .build()
                )
            }
            val vm = ZCloneViewModel(
                appContext = context,
                database = FMSDatabaseEngine(context),
                prefs = FMSPreferenceEngine(context),
                storage = FMSStorageEngine(context),
                inspector = FMSAPKInspector(context),
                deviceManager = FMSDeviceManager(context),
                backgroundEngine = FMSBackgroundEngine(),
                cloudRepository = ZCloneCloudRepository(context)
            )
            vm.verifyClonedAppInstalled("com.nonexistent.cloned.app")
            assertEquals(ValidationCheckStatus.FAILED, vm.uiState.value.deviceVerification?.stage8Installed)
            vm.verifyClonedAppInstalled(context.packageName)
            assertEquals(ValidationCheckStatus.PASS, vm.uiState.value.deviceVerification?.stage8Installed)
            vm.recordPostInstallFunctionalCheck(context.packageName, passed = true, note = "UI lancar")
            assertEquals(ValidationCheckStatus.PASS, vm.uiState.value.deviceVerification?.stage10MainFunction)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun writeSampleApk(
        outFile: File,
        axml: ByteArray,
        nativeEntries: Map<String, ByteArray>,
        dexBytes: ByteArray = byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00)
    ) {
        val resArsc = byteArrayOf(0x02, 0x00, 0x0C, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.CYAN)
        val iconPng = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        ZipOutputStream(outFile.outputStream()).use { zos ->
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

            zos.putNextEntry(ZipEntry("res/mipmap-xxhdpi-v4/ic_launcher.png"))
            zos.write(iconPng)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(dexBytes)
            zos.closeEntry()

            nativeEntries.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                })
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    private fun buildRecoveryAxml(
        packageName: String,
        appLabel: String,
        minSdk: Int,
        targetSdk: Int,
        extractNativeLibs: Boolean,
        customPermName: String?,
        usesCustomPerm: String?,
        componentPerm: String?,
        authorityIsReference: Boolean,
        literalAuthority: String?
    ): ByteArray {
        val baseStrings = mutableListOf(
            "package", "minSdkVersion", "targetSdkVersion", "label", "name",
            "exported", "extractNativeLibs", "permission", "authorities",
            "http://schemas.android.com/apk/res/android", "android",
            "manifest", "uses-sdk", "uses-permission", "permission-group",
            "application", "activity", "intent-filter", "action", "category", "provider",
            packageName, appLabel,
            ".MainActivity",
            "android.intent.action.MAIN", "android.intent.category.LAUNCHER"
        )
        listOfNotNull(customPermName, usesCustomPerm, componentPerm, literalAuthority).forEach { s ->
            if (s !in baseStrings) baseStrings.add(s)
        }
        val resIds = listOf(
            0x00000000, // package
            0x0101020c, // minSdkVersion
            0x01010270, // targetSdkVersion
            0x01010001, // label
            0x01010003, // name
            0x01010010, // exported
            0x010104ea, // extractNativeLibs
            0x01010006, // permission
            0x01010018  // authorities
        )
        fun idx(s: String) = baseStrings.indexOf(s)
        val nsAndroid = idx("http://schemas.android.com/apk/res/android")
        val prefixAndroid = idx("android")

        val poolChunk = buildUtf8StringPool(baseStrings)
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
        startTag(
            "uses-sdk",
            listOf(
                Triple(nsAndroid, idx("minSdkVersion"), 0x10 to minSdk),
                Triple(nsAndroid, idx("targetSdkVersion"), 0x10 to targetSdk)
            )
        )
        endTag("uses-sdk")

        if (customPermName != null) {
            startTag("permission", listOf(Triple(nsAndroid, idx("name"), 0x03 to idx(customPermName))))
            endTag("permission")
        }
        if (usesCustomPerm != null) {
            startTag("uses-permission", listOf(Triple(nsAndroid, idx("name"), 0x03 to idx(usesCustomPerm))))
            endTag("uses-permission")
        }

        val appAttrs = mutableListOf(
            Triple(nsAndroid, idx("label"), 0x03 to idx(appLabel)),
            Triple(nsAndroid, idx("extractNativeLibs"), 0x12 to if (extractNativeLibs) -1 else 0)
        )
        if (componentPerm != null) {
            appAttrs += Triple(nsAndroid, idx("permission"), 0x03 to idx(componentPerm))
        }
        startTag("application", appAttrs)

        startTag(
            "activity",
            listOf(
                Triple(nsAndroid, idx("name"), 0x03 to idx(".MainActivity")),
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

        if (authorityIsReference) {
            startTag(
                "provider",
                listOf(Triple(nsAndroid, idx("authorities"), 0x01 to 0x7F0A0001))
            )
            endTag("provider")
        } else if (literalAuthority != null) {
            startTag(
                "provider",
                listOf(Triple(nsAndroid, idx("authorities"), 0x03 to idx(literalAuthority)))
            )
            endTag("provider")
        }

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
