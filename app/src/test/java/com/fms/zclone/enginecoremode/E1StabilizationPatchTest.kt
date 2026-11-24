package com.fms.zclone.enginecoremode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.fms.clone.ApkInspectionResult
import com.fms.clone.CloneBuildRequest
import com.fms.clone.CompatibilityAnalysis
import com.fms.clone.CompatibilityStatus
import com.fms.clone.FMSRepackager
import com.fms.clone.FMSSignatureManager
import com.fms.clone.InstalledAppInfo
import com.fms.clone.ManifestSummary
import com.fms.core.FmsErrorEngine
import com.fms.core.FmsLogger
import com.fms.security.FMSKeyManager
import com.fms.security.SelfSignedCertGenerator
import com.fms.task.FMSBackgroundEngine
import com.fms.task.FmsJobSnapshot
import com.fms.task.FmsJobState
import com.fms.ui.FMSThemeEngine
import com.fms.zclone.enginecoremode.zclone.ui.ZCloneUiState
import com.fms.zclone.enginecoremode.zclone.ui.screens.AppConfigScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.HomeDashboardScreen
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class E1StabilizationPatchTest {

    @get:Rule
    val composeRule = createComposeRule()

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
    fun `P0A dialog build path and direct build path produce identical CloneBuildRequest with toggles`() {
        val capturedRequests = mutableListOf<CloneBuildRequest>()
        val sourcePkg = "com.example.parity"
        val app = InstalledAppInfo(
            packageName = sourcePkg,
            appName = "ParityApp",
            versionName = "1.0",
            versionCode = 1L,
            isSystemApp = false,
            sourceApkPath = "/tmp/fake_source.apk",
            hasSplitApks = false,
            apkSizeBytes = 2048L,
            minSdk = 24,
            targetSdk = 36,
            requestedPermissions = emptyList()
        )
        val inspection = ApkInspectionResult(
            packageName = sourcePkg,
            appName = "ParityApp",
            versionName = "1.0",
            versionCode = 1L,
            apkPath = "/tmp/fake_source.apk",
            apkSizeBytes = 2048L,
            entryCount = 12,
            hasSplitApks = false,
            hasDex = true,
            nativeAbis = emptyList(),
            iconEntryPaths = emptyList(),
            certSha256 = "AB12CD34",
            certSubject = "CN=Parity",
            manifestSummary = ManifestSummary(
                packageName = sourcePkg,
                applicationLabel = "ParityApp",
                versionCode = 1,
                versionName = "1.0",
                sharedUserId = null,
                debuggable = false,
                usesCleartextTraffic = false,
                allowBackup = true,
                permissions = emptyList(),
                exportedComponentsCount = 1,
                launcherCategoriesCount = 1,
                authorityStrings = emptyList(),
                minSdkVersion = 24,
                targetSdkVersion = 36,
                launcherActivityName = "$sourcePkg.MainActivity"
            ),
            compatibility = CompatibilityAnalysis(
                status = CompatibilityStatus.COMPATIBLE,
                score = 95,
                blockers = emptyList(),
                warnings = emptyList(),
                notes = "Ready"
            )
        )
        val uiState = ZCloneUiState(
            languageCode = "en",
            activeSelectedApp = app,
            activeInspection = inspection
        )

        composeRule.setContent {
            AppConfigScreen(
                state = uiState,
                theme = FMSThemeEngine.presets.first(),
                onBack = {},
                onBuildClone = { req, _ -> capturedRequests.add(req) },
                onExportApkUri = { _, _ -> },
                onInstallApk = {},
                onAttachSourceApkUri = {}
            )
        }

        // Expand Advanced Settings and turn OFF Reproducible Build
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("advanced_settings_toggle"))
        composeRule.onNodeWithTag("advanced_settings_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("toggle_reproducible_build"))
        composeRule.onNodeWithTag("toggle_reproducible_build").performClick()

        // First click opens confirmation dialog (ownership checkbox not confirmed yet)
        composeRule.onNodeWithTag("create_clone_button").performClick()
        composeRule.onNodeWithTag("build_ownership_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("build_ownership_checkbox").performClick()
        composeRule.onNodeWithTag("build_ownership_confirm_button").performClick()

        assertEquals(1, capturedRequests.size)
        val dialogReq = capturedRequests[0]
        assertFalse("Dialog path must forward reproducibleBuild=false", dialogReq.reproducibleBuild)
        assertTrue("Dialog path must forward embedDependencyGraph=true", dialogReq.embedDependencyGraph)

        // Second click uses direct path (ownerRightsConfirmed is now true) with identical config
        composeRule.onNodeWithTag("create_clone_button").performClick()
        assertEquals(2, capturedRequests.size)
        val directReq = capturedRequests[1]
        assertEquals(dialogReq, directReq)
    }

    @Test
    fun `P0B cooperative cancellation during v1 and v2 signing cleans temp files and keeps status CANCELLED`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "p0b_sign_cancel_${System.nanoTime()}").apply { mkdirs() }
        try {
            val unsignedApk = File(workDir, "unsigned.apk")
            val digests = mutableMapOf<String, String>()
            val md = MessageDigest.getInstance("SHA-256")
            ZipOutputStream(unsignedApk.outputStream()).use { zos ->
                listOf(
                    "AndroidManifest.xml" to byteArrayOf(1, 2, 3, 4),
                    "classes.dex" to byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00),
                    "res/drawable/sample.png" to ByteArray(4096) { 0x5A }
                ).forEach { (name, bytes) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                    digests[name] = android.util.Base64.encodeToString(md.digest(bytes), android.util.Base64.NO_WRAP)
                    md.reset()
                }
            }
            val identity = FMSKeyManager.getOrCreateSoftwareSigningIdentity(
                privateFilesDir = File(workDir, "ks").apply { mkdirs() },
                context = context
            )

            // 1) Cancel inside JAR v1 loop
            val outV1Cancel = File(workDir, "clone_v1_cancel.apk")
            var v1Calls = 0
            var caughtV1: Throwable? = null
            try {
                FMSSignatureManager.signJarApk(
                    unsignedApk = unsignedApk,
                    signedApk = outV1Cancel,
                    entryDigests = digests,
                    identity = identity,
                    deterministicMode = true,
                    shouldCancel = {
                        v1Calls++
                        v1Calls >= 3
                    }
                )
            } catch (t: Throwable) {
                caughtV1 = t
            }
            assertTrue("Expected CancellationException during v1 signing", caughtV1 is CancellationException)
            assertFalse("Target signed APK must not remain after v1 cancel", outV1Cancel.exists())
            assertFalse(
                "v1 temp file must be removed after v1 cancel",
                workDir.listFiles()?.any { it.name.contains("_v1_only") } == true
            )

            // 2) Cancel inside APK Signature Scheme v2 loop (after v1 succeeds)
            val preExistingTarget = File(workDir, "clone_v2_cancel.apk")
            val sentinelBytes = "ORIGINAL_VALID_BYTES".toByteArray()
            preExistingTarget.writeBytes(sentinelBytes)

            val outV2Temp = File(workDir, "clone_v2_cancel_signed_tmp.apk")
            val v2Calls = AtomicInteger(0)
            var caughtV2: Throwable? = null
            try {
                FMSSignatureManager.signJarApk(
                    unsignedApk = unsignedApk,
                    signedApk = outV2Temp,
                    entryDigests = digests,
                    identity = identity,
                    deterministicMode = true,
                    shouldCancel = {
                        val n = v2Calls.incrementAndGet()
                        // Entry digest + v1 loop calls occur before v2 (~13 calls); trigger inside v2 hashing/streaming
                        n >= 16
                    }
                )
            } catch (t: Throwable) {
                caughtV2 = t
            }
            assertTrue("Expected CancellationException during v2 signing", caughtV2 is CancellationException)
            assertFalse("Signed temp APK must not remain after v2 cancel", outV2Temp.exists())
            assertFalse(
                "No v1_only or signed_tmp temp files may leak",
                workDir.listFiles()?.any { it.name.contains("_v1_only") || it.name.contains("_signed_tmp") } == true
            )
            assertArrayEquals("Pre-existing final output must remain intact on cancel", sentinelBytes, preExistingTarget.readBytes())

            // 3) FMSBackgroundEngine preserves CANCELLED vs FAILED distinction
            val engine = FMSBackgroundEngine(maxParallelJobs = 1)
            val cancelDone = CountDownLatch(1)
            val cancelJobId = engine.enqueue(
                title = "Wrapped Cancel Task",
                block = {
                    try {
                        throw RuntimeException("signing wrapper", CancellationException("User cancelled v2"))
                    } finally {
                        cancelDone.countDown()
                    }
                }
            )
            assertTrue(cancelDone.await(3, TimeUnit.SECONDS))
            Thread.sleep(150L)
            assertEquals(FmsJobState.CANCELLED, engine.jobs.value.first { it.jobId == cancelJobId }.state)

            val failDone = CountDownLatch(1)
            val failJobId = engine.enqueue(
                title = "Real Fail Task",
                block = {
                    throw IllegalStateException("I/O checksum mismatch")
                },
                onComplete = { failDone.countDown() }
            )
            assertTrue(failDone.await(3, TimeUnit.SECONDS))
            Thread.sleep(100L)
            assertEquals(FmsJobState.FAILED, engine.jobs.value.first { it.jobId == failJobId }.state)
            engine.shutdown()
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `P0C logger and error engine redact secrets tokens passphrases JWTs and private keys`() {
        FmsLogger.clear()
        val errorEngine = FmsErrorEngine(FmsLogger)

        FmsLogger.warn("AuthTest", "Authorization: Bearer super_secret_token_987654 failed for user")
        FmsLogger.error(
            "KeyTest",
            """{"id_token":"abc123.def456","access_token":"xyz999","passphrase":"my-p12-pass","private_key":"secret-hex"}"""
        )
        FmsLogger.info(
            "JwtTest",
            "Rejected eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTYifQ.signature123 and -----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqh\n-----END PRIVATE KEY-----"
        )
        errorEngine.record(
            "SyncRepo",
            IllegalStateException("Upload rejected token=secret_value_abc password=hunter2")
        )

        val allMessages = FmsLogger.logsFlow.value.joinToString("\n") { it.message }
        assertFalse(allMessages.contains("super_secret_token_987654"))
        assertFalse(allMessages.contains("abc123.def456"))
        assertFalse(allMessages.contains("xyz999"))
        assertFalse(allMessages.contains("my-p12-pass"))
        assertFalse(allMessages.contains("secret-hex"))
        assertFalse(allMessages.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertFalse(allMessages.contains("MIIEvQIBADANBgkqh"))
        assertFalse(allMessages.contains("secret_value_abc"))
        assertFalse(allMessages.contains("hunter2"))
        assertTrue(allMessages.contains("[REDACTED]"))
        assertTrue(allMessages.contains("[REDACTED_JWT]"))
        assertTrue(allMessages.contains("[REDACTED_PRIVATE_KEY]"))

        val recordedError = errorEngine.errors.value.last().message
        assertFalse(recordedError.contains("secret_value_abc"))
        assertFalse(recordedError.contains("hunter2"))
    }

    @Test
    fun `P1A legacy pkcs12 keystore migrates non-destructively preserving RSA fingerprint and replaces hardcoded passphrase`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "p1a_keystore_${System.nanoTime()}").apply { mkdirs() }
        try {
            val legacyDir = File(workDir, "legacy").apply { mkdirs() }
            val legacyFile = File(legacyDir, "fms_local_signing.p12")
            val legacyPass = charArrayOf(
                'f', 'm', 's', '-', 'l', 'o', 'c', 'a', 'l', '-', 'u', 's', 'e', 'r', '-', 'k', 'e', 'y', 's', 't', 'o', 'r', 'e'
            )

            val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            val kp = kpg.generateKeyPair()
            val cert = SelfSignedCertGenerator.generateRsaSha256Cert(kp, "Legacy Local Signer", 3650)
            val originalCertBytes = cert.encoded
            val legacyKs = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry("zclone_local_key", kp.private, legacyPass, arrayOf(cert))
            }
            legacyFile.outputStream().use { legacyKs.store(it, legacyPass) }

            val migratedIdentity = FMSKeyManager.getOrCreateSoftwareSigningIdentity(
                privateFilesDir = legacyDir,
                context = context
            )
            assertArrayEquals(
                "Migrated PKCS#12 must preserve exact legacy X509 certificate & keypair",
                originalCertBytes,
                migratedIdentity.certificate.encoded
            )

            // Legacy passphrase must no longer unlock the migrated keystore
            val legacyUnlockAfter = runCatching {
                val testKs = KeyStore.getInstance("PKCS12")
                legacyFile.inputStream().use { testKs.load(it, legacyPass) }
            }
            assertTrue("Old hardcoded passphrase must fail after migration", legacyUnlockAfter.isFailure)

            val wrappedFile = File(legacyDir, ".fms_p12_pass_zclone_local_key.enc")
            assertTrue("Wrapped passphrase store must exist", wrappedFile.exists())
            assertFalse(
                "Wrapped payload must never contain plaintext legacy passphrase",
                wrappedFile.readText().contains(String(legacyPass))
            )
            assertFalse(File(legacyDir, "fms_local_signing.p12.bak").exists())
            assertFalse(File(legacyDir, "fms_local_signing.p12.tmp").exists())

            // Reload returns identical identity without recreating keypair
            val reloaded = FMSKeyManager.getOrCreateSoftwareSigningIdentity(legacyDir, context = context)
            assertEquals(migratedIdentity.sha256Fingerprint, reloaded.sha256Fingerprint)

            // Interrupted atomic write recovery from .bak
            val backupFile = File(legacyDir, "fms_local_signing.p12.bak")
            legacyFile.copyTo(backupFile, overwrite = true)
            assertTrue(legacyFile.delete())
            val recovered = FMSKeyManager.getOrCreateSoftwareSigningIdentity(legacyDir, context = context)
            assertEquals(migratedIdentity.sha256Fingerprint, recovered.sha256Fingerprint)
            assertTrue(legacyFile.exists())

            // Fresh directory does not accept legacy passphrase
            val freshDir = File(workDir, "fresh").apply { mkdirs() }
            val freshIdentity = FMSKeyManager.getOrCreateSoftwareSigningIdentity(freshDir, context = context)
            assertEquals(64, freshIdentity.sha256Fingerprint.length)
            val freshLegacyTry = runCatching {
                val testKs = KeyStore.getInstance("PKCS12")
                File(freshDir, "fms_local_signing.p12").inputStream().use { testKs.load(it, legacyPass) }
            }
            assertTrue("Fresh keystore must reject legacy hardcoded passphrase", freshLegacyTry.isFailure)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `P1C and P1D reproducible metadata carries ZCloner PRO attribution and Home card appears only for active jobs`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "p1cd_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sourceApk = buildMinimalTestApk(File(workDir, "src"), File(workDir, "src.apk"), "com.fms.p1brand", "BrandSrc")
            val outApk = File(workDir, "cloned.apk")
            FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = sourceApk.absolutePath,
                    sourcePackage = "com.fms.p1brand",
                    targetPackage = "com.fms.p1brand.zclone1",
                    sourceAppName = "BrandSrc",
                    cloneName = "BrandSrc Clone",
                    iconPresetId = "original",
                    iconHueShift = 0,
                    changePackageName = true,
                    hideFromLauncher = false,
                    isolateData = true,
                    strippedPermissions = emptySet(),
                    ownerConfirmedModificationRights = true,
                    injectDhedryLoading = true,
                    reproducibleBuild = true,
                    embedDependencyGraph = true
                ),
                privateFilesDir = File(workDir, "ks").apply { mkdirs() },
                outputFile = outApk,
                context = context
            ).getOrThrow()

            ZipFile(outApk).use { zip ->
                val repJson = JSONObject(
                    zip.getInputStream(zip.getEntry(FMSRepackager.DHEDRY_REPRODUCIBLE_BUILD_ENTRY))
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                )
                assertEquals("ZCloner PRO", repJson.getString("product"))
                assertEquals("FMS Engine Core V1", repJson.getString("engine"))
                assertEquals("Copyright FMS Clone Engine V1", repJson.getString("copyright"))

                val mfText = zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF")).use { it.readBytes().toString(Charsets.UTF_8) }
                assertTrue(mfText.contains("ZCloner PRO FMS Engine Core V1"))
            }
        } finally {
            workDir.deleteRecursively()
        }

        // Idle state: no active jobs card, ZCloner PRO branding present, no PRO Status Bar Manager
        val uiState = androidx.compose.runtime.mutableStateOf(ZCloneUiState(languageCode = "en"))
        var cancelBuildCalled = 0
        var cancelBatchCalled = 0
        composeRule.setContent {
            HomeDashboardScreen(
                state = uiState.value,
                theme = FMSThemeEngine.presets.first(),
                onNavigate = {},
                onToggleAutoTheme = {},
                onOpenAuthDialog = {},
                onOpenTierDialog = {},
                onCancelActiveBuild = { cancelBuildCalled++ },
                onCancelAllBatchJobs = { cancelBatchCalled++ }
            )
        }
        composeRule.onNodeWithText("ZCloner PRO").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("active_jobs_summary_card").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("PRO Status Bar Manager", substring = true).fetchSemanticsNodes().size)

        // Active state: card visible and cancel buttons invoke callbacks
        uiState.value = ZCloneUiState(
            languageCode = "en",
            isBuildingClone = true,
            buildProgress = 0.45f,
            buildProgressDetail = "Repacking APK entries...",
            backgroundJobs = listOf(
                FmsJobSnapshot(
                    jobId = "job-1",
                    title = "Batch App 1",
                    progress = 0.6f,
                    state = FmsJobState.RUNNING,
                    detail = "Signing..."
                )
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("active_jobs_summary_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_cancel_single_build_button").performClick()
        composeRule.onNodeWithTag("home_cancel_batch_jobs_button").performClick()
        assertEquals(1, cancelBuildCalled)
        assertEquals(1, cancelBatchCalled)
    }

    private fun buildMinimalTestApk(
        workDir: File,
        outApk: File,
        packageName: String,
        appName: String
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
                <application android:label="@string/app_name" android:icon="@mipmap/ic_launcher">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
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
            }
        }
        return outApk
    }

    private fun runCmd(vararg cmd: String): String {
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        if (code != 0) throw AssertionError("Command failed ($code): ${cmd.joinToString(" ")}\n$output")
        return output
    }
}
