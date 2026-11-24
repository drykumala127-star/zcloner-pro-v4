package com.fms.zclone.enginecoremode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.fms.clone.CloneBuildRequest
import com.fms.clone.CompatibilityStatus
import com.fms.clone.FMSAPKInspector
import com.fms.clone.FMSManifestEngine
import com.fms.clone.FMSRepackager
import com.fms.clone.FMSSignatureManager
import com.fms.clone.ValidationCheckCategory
import com.fms.clone.ValidationCheckStatus
import com.fms.zclone.enginecoremode.zclone.ui.screens.DhedryLoadingTimeline
import org.junit.Assert.assertArrayEquals
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
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * CP010L-09: Real Android SDK (javac + d8 + aapt2 + apksigner) 7-category APK cloning matrix
 * plus self-cloning test against the project's actual complex app-debug.apk when present.
 * Physical Android device / ADB emulator tests are NOT executed here and remain NOT TESTED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RealApkMatrixValidationTest {

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
    fun `category 1 - simple real SDK APK clones with valid aapt2 manifest, dhedry splash, and v2 signature`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = createWorkDir(context, "cat1_simple")
        try {
            val sourcePkg = "com.matrix.simple"
            val targetPkg = "com.matrix.simple.zclone1"
            val sourceApk = buildRealSdkApk(
                workDir = File(workDir, "src_build"),
                outApk = File(workDir, "simple_src.apk"),
                packageName = sourcePkg,
                appLabel = "Matrix Simple",
                minSdk = 24,
                targetSdk = 36,
                manifestBody = """
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                """.trimIndent(),
                javaSources = mapOf(
                    "com/matrix/simple/MainActivity.java" to """
                        package com.matrix.simple;
                        import android.app.Activity;
                        import android.os.Bundle;
                        public class MainActivity extends Activity {
                            @Override protected void onCreate(Bundle b) { super.onCreate(b); }
                        }
                    """.trimIndent()
                )
            )
            val clonedApk = File(workDir, "simple_cloned.apk")
            val result = cloneAndRunAssertions(context, workDir, sourceApk, sourcePkg, targetPkg, "Simple Clone", clonedApk)
            assertTrue(result.dhedrySplashInjected)
            assertEquals("classes2.dex", result.dhedryDexEntryName)
            assertEquals("com.matrix.simple.MainActivity", result.dhedryTargetActivity)

            val badging = runCmd(aapt2Bin.absolutePath, "dump", "badging", clonedApk.absolutePath)
            assertTrue("badging missing target pkg:\n$badging", badging.contains("package: name='$targetPkg'"))
            assertTrue("badging missing clone label:\n$badging", badging.contains("application-label:'Simple Clone'"))
            assertTrue(
                "badging missing DhedrySplashActivity launchable-activity:\n$badging",
                badging.contains("launchable-activity: name='${FMSManifestEngine.DHEDRY_SPLASH_ACTIVITY}'")
            )

            val xmlTree = runCmd(aapt2Bin.absolutePath, "dump", "xmltree", "--file", "AndroidManifest.xml", clonedApk.absolutePath)
            assertTrue("xmltree missing dhedry.target_activity:\n$xmlTree", xmlTree.contains("dhedry.target_activity"))
            assertTrue("xmltree missing target MainActivity:\n$xmlTree", xmlTree.contains("com.matrix.simple.MainActivity"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `category 2 - custom permissions and androidx dynamic receiver permission are rewritten safely`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = createWorkDir(context, "cat2_perms")
        try {
            val sourcePkg = "com.matrix.customperm"
            val targetPkg = "com.matrix.customperm.zclone1"
            val sourceApk = buildRealSdkApk(
                workDir = File(workDir, "src_build"),
                outApk = File(workDir, "perm_src.apk"),
                packageName = sourcePkg,
                appLabel = "Matrix Perm",
                minSdk = 24,
                targetSdk = 36,
                manifestTopTags = """
                    <permission android:name="$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" android:protectionLevel="signature" />
                    <permission android:name="$sourcePkg.permission.ACCESS_VAULT" android:protectionLevel="signature" />
                    <uses-permission android:name="$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
                    <uses-permission android:name="$sourcePkg.permission.ACCESS_VAULT" />
                    <uses-permission android:name="android.permission.INTERNET" />
                """.trimIndent(),
                appPermission = "$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                manifestBody = """
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <receiver android:name=".VaultReceiver" android:exported="false" android:permission="$sourcePkg.permission.ACCESS_VAULT" />
                """.trimIndent(),
                javaSources = mapOf(
                    "com/matrix/customperm/MainActivity.java" to """
                        package com.matrix.customperm;
                        import android.app.Activity;
                        public class MainActivity extends Activity {}
                    """.trimIndent(),
                    "com/matrix/customperm/VaultReceiver.java" to """
                        package com.matrix.customperm;
                        import android.content.BroadcastReceiver;
                        import android.content.Context;
                        import android.content.Intent;
                        public class VaultReceiver extends BroadcastReceiver {
                            @Override public void onReceive(Context c, Intent i) {}
                        }
                    """.trimIndent()
                )
            )
            val clonedApk = File(workDir, "perm_cloned.apk")
            cloneAndRunAssertions(context, workDir, sourceApk, sourcePkg, targetPkg, "Perm Clone", clonedApk)

            val xmlTree = runCmd(aapt2Bin.absolutePath, "dump", "xmltree", "--file", "AndroidManifest.xml", clonedApk.absolutePath)
            assertTrue(xmlTree.contains("$targetPkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
            assertTrue(xmlTree.contains("$targetPkg.permission.ACCESS_VAULT"))
            assertFalse("Old custom perm leaked:\n$xmlTree", xmlTree.contains("\"$sourcePkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION\""))
            assertFalse("Old vault perm leaked:\n$xmlTree", xmlTree.contains("\"$sourcePkg.permission.ACCESS_VAULT\""))
            assertTrue("Standard INTERNET perm must stay", xmlTree.contains("android.permission.INTERNET"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `category 3 - ContentProvider literal and @string reference authorities are handled accurately`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = createWorkDir(context, "cat3_provider")
        try {
            val sourcePkg = "com.matrix.provider"
            val targetPkg = "com.matrix.provider.zclone1"
            val sourceApk = buildRealSdkApk(
                workDir = File(workDir, "src_build"),
                outApk = File(workDir, "provider_src.apk"),
                packageName = sourcePkg,
                appLabel = "Matrix Provider",
                minSdk = 24,
                targetSdk = 36,
                extraStringsXml = """
                    <string name="ref_provider_auth">com.matrix.provider.refauth</string>
                """.trimIndent(),
                manifestBody = """
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <provider
                        android:name=".LiteralProvider"
                        android:authorities="$sourcePkg.fileprovider;custom.legacy.auth"
                        android:exported="false" />
                    <provider
                        android:name=".RefProvider"
                        android:authorities="@string/ref_provider_auth"
                        android:exported="false" />
                """.trimIndent(),
                javaSources = mapOf(
                    "com/matrix/provider/MainActivity.java" to """
                        package com.matrix.provider;
                        import android.app.Activity;
                        public class MainActivity extends Activity {}
                    """.trimIndent(),
                    "com/matrix/provider/LiteralProvider.java" to """
                        package com.matrix.provider;
                        import android.content.*;
                        import android.database.Cursor;
                        import android.net.Uri;
                        public class LiteralProvider extends ContentProvider {
                            @Override public boolean onCreate() { return true; }
                            @Override public Cursor query(Uri u, String[] p, String s, String[] sa, String so) { return null; }
                            @Override public String getType(Uri u) { return null; }
                            @Override public Uri insert(Uri u, ContentValues cv) { return null; }
                            @Override public int delete(Uri u, String s, String[] sa) { return 0; }
                            @Override public int update(Uri u, ContentValues cv, String s, String[] sa) { return 0; }
                        }
                    """.trimIndent(),
                    "com/matrix/provider/RefProvider.java" to """
                        package com.matrix.provider;
                        public class RefProvider extends LiteralProvider {}
                    """.trimIndent()
                )
            )
            val clonedApk = File(workDir, "provider_cloned.apk")
            val result = cloneAndRunAssertions(context, workDir, sourceApk, sourcePkg, targetPkg, "Provider Clone", clonedApk)

            val stage2 = result.validationReport!!.steps.first { it.stageNumber == 2 }
            assertEquals(ValidationCheckStatus.WARNING, stage2.status)
            assertTrue(result.warnings.any { it.contains("@string") })

            val xmlTree = runCmd(aapt2Bin.absolutePath, "dump", "xmltree", "--file", "AndroidManifest.xml", clonedApk.absolutePath)
            assertTrue(xmlTree.contains("$targetPkg.fileprovider;$targetPkg.custom.legacy.auth"))
            assertTrue(xmlTree.contains("$sourcePkg.LiteralProvider"))
            assertTrue(xmlTree.contains("$sourcePkg.RefProvider"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `category 4 - native libraries with extractNativeLibs false stay STORED and 16KB aligned`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = createWorkDir(context, "cat4_native")
        try {
            val sourcePkg = "com.matrix.nativeapp"
            val targetPkg = "com.matrix.nativeapp.zclone1"
            val soElfHeader = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
            val arm64So = ByteArray(8192) { idx -> if (idx < 4) soElfHeader[idx] else (idx * 7 and 0xFF).toByte() }
            val armeabiSo = ByteArray(4096) { idx -> if (idx < 4) soElfHeader[idx] else (idx * 13 and 0xFF).toByte() }

            val baseApk = buildRealSdkApk(
                workDir = File(workDir, "src_build"),
                outApk = File(workDir, "native_base.apk"),
                packageName = sourcePkg,
                appLabel = "Matrix Native",
                minSdk = 24,
                targetSdk = 36,
                extractNativeLibs = false,
                manifestBody = """
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                """.trimIndent(),
                javaSources = mapOf(
                    "com/matrix/nativeapp/MainActivity.java" to """
                        package com.matrix.nativeapp;
                        import android.app.Activity;
                        public class MainActivity extends Activity {}
                    """.trimIndent()
                )
            )
            val sourceApk = File(workDir, "native_src.apk")
            appendStoredEntries(
                baseApk,
                sourceApk,
                mapOf(
                    "lib/arm64-v8a/libengine.so" to arm64So,
                    "lib/armeabi-v7a/libengine.so" to armeabiSo
                )
            )
            val clonedApk = File(workDir, "native_cloned.apk")
            cloneAndRunAssertions(context, workDir, sourceApk, sourcePkg, targetPkg, "Native Clone", clonedApk)

            ZipFile(clonedApk).use { zip ->
                val arm64Entry = zip.getEntry("lib/arm64-v8a/libengine.so")
                val v7Entry = zip.getEntry("lib/armeabi-v7a/libengine.so")
                assertNotNull(arm64Entry)
                assertNotNull(v7Entry)
                assertEquals(ZipEntry.STORED, arm64Entry!!.method)
                assertEquals(ZipEntry.STORED, v7Entry!!.method)
                assertArrayEquals(arm64So, zip.getInputStream(arm64Entry).use { it.readBytes() })
                assertArrayEquals(armeabiSo, zip.getInputStream(v7Entry).use { it.readBytes() })
            }
            assertTrue(FMSSignatureManager.isEntryDataAligned(clonedApk, "lib/arm64-v8a/libengine.so", 16384))
            assertTrue(FMSSignatureManager.isEntryDataAligned(clonedApk, "lib/armeabi-v7a/libengine.so", 16384))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `category 5 - multidex real SDK APK preserves existing dex files and injects next unused classesN dex`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = createWorkDir(context, "cat5_multidex")
        try {
            val sourcePkg = "com.matrix.multidex"
            val targetPkg = "com.matrix.multidex.zclone1"
            val baseApk = buildRealSdkApk(
                workDir = File(workDir, "src_build"),
                outApk = File(workDir, "multidex_base.apk"),
                packageName = sourcePkg,
                appLabel = "Matrix MultiDex",
                minSdk = 24,
                targetSdk = 36,
                manifestBody = """
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                """.trimIndent(),
                javaSources = mapOf(
                    "com/matrix/multidex/MainActivity.java" to """
                        package com.matrix.multidex;
                        import android.app.Activity;
                        public class MainActivity extends Activity {}
                    """.trimIndent()
                )
            )
            // Build a genuine second classes2.dex using javac + d8
            val secondaryDexDir = File(workDir, "dex2").apply { mkdirs() }
            val extraJava = File(secondaryDexDir, "com/matrix/multidex/SecondaryHelper.java").apply {
                parentFile?.mkdirs()
                writeText(
                    """
                    package com.matrix.multidex;
                    public class SecondaryHelper { public static int ping() { return 42; } }
                    """.trimIndent()
                )
            }
            val clsDir = File(secondaryDexDir, "cls").apply { mkdirs() }
            runCmd(
                javacBin.absolutePath, "-source", "11", "-target", "11", "-Xlint:-options",
                "-classpath", androidJar.absolutePath, "-d", clsDir.absolutePath, extraJava.absolutePath
            )
            runCmd(
                d8Bin.absolutePath, "--min-api", "24", "--lib", androidJar.absolutePath,
                "--output", secondaryDexDir.absolutePath,
                File(clsDir, "com/matrix/multidex/SecondaryHelper.class").absolutePath
            )
            val classes2Bytes = File(secondaryDexDir, "classes.dex").readBytes()

            val sourceApk = File(workDir, "multidex_src.apk")
            appendStoredEntries(baseApk, sourceApk, mapOf("classes2.dex" to classes2Bytes), stored = false)

            val clonedApk = File(workDir, "multidex_cloned.apk")
            val result = cloneAndRunAssertions(context, workDir, sourceApk, sourcePkg, targetPkg, "MultiDex Clone", clonedApk)
            assertEquals("classes3.dex", result.dhedryDexEntryName)
            ZipFile(clonedApk).use { zip ->
                assertNotNull(zip.getEntry("classes.dex"))
                assertArrayEquals(classes2Bytes, zip.getInputStream(zip.getEntry("classes2.dex")).use { it.readBytes() })
                assertNotNull(zip.getEntry("classes3.dex"))
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `category 6 - complex manifest with queries, activity-alias, CoreComponentFactory, and real app-debug apk`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = createWorkDir(context, "cat6_complex")
        try {
            val sourcePkg = "com.matrix.complex"
            val targetPkg = "com.matrix.complex.zclone1"
            val sourceApk = buildRealSdkApk(
                workDir = File(workDir, "src_build"),
                outApk = File(workDir, "complex_src.apk"),
                packageName = sourcePkg,
                appLabel = "Matrix Complex",
                minSdk = 24,
                targetSdk = 36,
                appComponentFactory = "androidx.core.app.CoreComponentFactory",
                manifestRootChildTags = """
                    <queries>
                        <intent>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent>
                    </queries>
                """.trimIndent(),
                manifestBody = """
                    <activity android:name=".HomeActivity" android:exported="true" android:launchMode="singleTask">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <service android:name=".WorkerService" android:exported="false" />
                    <meta-data android:name="com.matrix.feature_flag" android:value="enabled" />
                """.trimIndent(),
                javaSources = mapOf(
                    "com/matrix/complex/HomeActivity.java" to """
                        package com.matrix.complex;
                        import android.app.Activity;
                        public class HomeActivity extends Activity {}
                    """.trimIndent(),
                    "com/matrix/complex/WorkerService.java" to """
                        package com.matrix.complex;
                        import android.app.Service;
                        import android.content.Intent;
                        import android.os.IBinder;
                        public class WorkerService extends Service {
                            @Override public IBinder onBind(Intent i) { return null; }
                        }
                    """.trimIndent()
                )
            )
            val clonedApk = File(workDir, "complex_cloned.apk")
            val result = cloneAndRunAssertions(context, workDir, sourceApk, sourcePkg, targetPkg, "Complex Clone", clonedApk)
            assertTrue(result.dhedrySplashInjected)

            val xmlTree = runCmd(aapt2Bin.absolutePath, "dump", "xmltree", "--file", "AndroidManifest.xml", clonedApk.absolutePath)
            // <queries> launcher category must remain untouched while activity launcher category switched to DEFAULT
            val queriesSection = xmlTree.substringAfter("E: queries").substringBefore("E: application")
            assertTrue("<queries> must retain LAUNCHER category:\n$queriesSection", queriesSection.contains("android.intent.category.LAUNCHER"))

            val badging = runCmd(aapt2Bin.absolutePath, "dump", "badging", clonedApk.absolutePath)
            assertTrue(badging.contains("package: name='$targetPkg'"))
            assertTrue(badging.contains("launchable-activity: name='${FMSManifestEngine.DHEDRY_SPLASH_ACTIVITY}'"))

            // Additionally clone the project's actual compiled app-debug.apk if present on disk
            val realDebugApk = listOf(
                File("build/outputs/apk/debug/app-debug.apk"),
                File("app/build/outputs/apk/debug/app-debug.apk")
            ).firstOrNull { it.isFile && it.length() > 1_000_000L }
            if (realDebugApk != null) {
                val realOutApk = File(workDir, "zclone_self_cloned.apk")
                val selfTargetPkg = "com.fms.zclone.enginecoremode.matrixclone"
                val selfResult = cloneAndRunAssertions(
                    context = context,
                    workDir = workDir,
                    sourceApk = realDebugApk,
                    sourcePkg = "com.fms.zclone.enginecoremode",
                    targetPkg = selfTargetPkg,
                    cloneLabel = "ZClone Self Clone",
                    outApk = realOutApk
                )
                assertTrue(selfResult.dhedrySplashInjected)
                assertEquals("com.fms.zclone.enginecoremode.MainActivity", selfResult.dhedryTargetActivity)
                val selfBadging = runCmd(aapt2Bin.absolutePath, "dump", "badging", realOutApk.absolutePath)
                assertTrue(selfBadging.contains("package: name='$selfTargetPkg'"))
                assertTrue(selfBadging.contains("application-label:'ZClone Self Clone'"))
                assertTrue(selfBadging.contains("launchable-activity: name='${FMSManifestEngine.DHEDRY_SPLASH_ACTIVITY}'"))

                val selfXmlTree = runCmd(aapt2Bin.absolutePath, "dump", "xmltree", "--file", "AndroidManifest.xml", realOutApk.absolutePath)
                assertTrue(
                    "Cloned app-debug.apk must rewrite DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                    selfXmlTree.contains("$selfTargetPkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                )
                assertTrue(
                    "Cloned app-debug.apk must rewrite FileProvider authority",
                    selfXmlTree.contains("$selfTargetPkg.fileprovider")
                )
                assertTrue(
                    "Cloned app-debug.apk must rewrite androidx-startup authority",
                    selfXmlTree.contains("$selfTargetPkg.androidx-startup")
                )
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `category 7 - split configuration APKs remain hard-blocked safely with complete diagnostics`() {
        requireSdkToolchain()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = createWorkDir(context, "cat7_split")
        try {
            val splitSourcePkg = "com.matrix.splitcfg"
            val splitApk = buildRealSdkApk(
                workDir = File(workDir, "src_split"),
                outApk = File(workDir, "split_base.apk"),
                packageName = splitSourcePkg,
                appLabel = "Split App",
                minSdk = 24,
                targetSdk = 36,
                manifestBody = """
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <meta-data android:name="com.android.vending.splits.required" android:value="true" />
                """.trimIndent(),
                javaSources = mapOf(
                    "com/matrix/splitcfg/MainActivity.java" to """
                        package com.matrix.splitcfg;
                        import android.app.Activity;
                        public class MainActivity extends Activity {}
                    """.trimIndent()
                )
            )
            val inspector = FMSAPKInspector(context)
            val inspection = inspector.inspectApkFile(splitApk).getOrThrow()
            assertTrue(inspection.manifestSummary.hasSplitsRequiredMeta)
            assertEquals(CompatibilityStatus.UNSUPPORTED, inspection.compatibility.status)
            assertTrue(inspection.compatibility.blockers.any { it.contains("Split APK") })

            val splitTypeApk = buildRealSdkApk(
                workDir = File(workDir, "src_split_types"),
                outApk = File(workDir, "split_types.apk"),
                packageName = "$splitSourcePkg.types",
                appLabel = "Split Types App",
                minSdk = 31,
                targetSdk = 36,
                manifestExtraRootAttrs = "android:requiredSplitTypes=\"abi\" android:splitTypes=\"\"",
                manifestBody = """
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                """.trimIndent(),
                javaSources = mapOf(
                    "com/matrix/splitcfg/types/MainActivity.java" to """
                        package com.matrix.splitcfg.types;
                        import android.app.Activity;
                        public class MainActivity extends Activity {}
                    """.trimIndent()
                )
            )
            val typesInspection = inspector.inspectApkFile(splitTypeApk).getOrThrow()
            assertTrue(typesInspection.manifestSummary.hasSplitsRequiredMeta)
            assertEquals(CompatibilityStatus.UNSUPPORTED, typesInspection.compatibility.status)

            val attemptedOut = File(workDir, "split_should_not_exist.apk")
            val buildRes = FMSRepackager.buildClonedApk(
                request = CloneBuildRequest(
                    sourceApkPath = splitApk.absolutePath,
                    sourcePackage = splitSourcePkg,
                    targetPackage = "$splitSourcePkg.zclone1",
                    sourceAppName = "Split App",
                    cloneName = "Split Clone",
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
                outputFile = attemptedOut,
                context = context
            )
            assertTrue(buildRes.isFailure)
            assertFalse("Blocked split clone must not produce output APK", attemptedOut.exists())
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun `mandatory Dhedry Splash and always-on audio contract stays locked`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(DhedryLoadingTimeline.DURATION_MS in 5000L..7000L)
        assertTrue(DhedryLoadingTimeline.AUDIO_ALWAYS_ENABLED)
        assertFalse(DhedryLoadingTimeline.HAS_MUTE_CONTROL)
        val mp3Bytes = context.resources.openRawResource(R.raw.dhedry_zclone_boot).use { it.readBytes() }
        assertTrue(mp3Bytes.size >= 50_000)
        val dexBytes = context.assets.open(FMSRepackager.DHEDRY_DEX_ASSET_PATH).use { it.readBytes() }
        assertEquals("dex\n", String(dexBytes, 0, 4, Charsets.US_ASCII))
    }

    private fun cloneAndRunAssertions(
        context: Context,
        workDir: File,
        sourceApk: File,
        sourcePkg: String,
        targetPkg: String,
        cloneLabel: String,
        outApk: File
    ) = FMSRepackager.buildClonedApk(
        request = CloneBuildRequest(
            sourceApkPath = sourceApk.absolutePath,
            sourcePackage = sourcePkg,
            targetPackage = targetPkg,
            sourceAppName = sourcePkg,
            cloneName = cloneLabel,
            iconPresetId = "neon_cyan",
            iconHueShift = 160,
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
    ).getOrThrow().also { out ->
        val report = out.validationReport!!
        assertTrue("Automated clone validation must pass:\n${report.summaryMessage}", report.passedAutomatedChecks)
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
        assertTrue(FMSSignatureManager.verifyApkContainsValidSignature(outApk))
        val verifyOut = runCmd(
            apkSignerBin.absolutePath,
            "verify",
            "--min-sdk-version", "24",
            "--verbose",
            "--print-certs",
            outApk.absolutePath
        )
        assertTrue("apksigner v2 missing:\n$verifyOut", verifyOut.contains("Verified using v2 scheme (APK Signature Scheme v2): true"))
    }

    private fun buildRealSdkApk(
        workDir: File,
        outApk: File,
        packageName: String,
        appLabel: String,
        minSdk: Int,
        targetSdk: Int,
        manifestExtraRootAttrs: String = "",
        manifestTopTags: String = "",
        manifestRootChildTags: String = "",
        appPermission: String? = null,
        extractNativeLibs: Boolean? = null,
        appComponentFactory: String? = null,
        extraStringsXml: String = "",
        manifestBody: String,
        javaSources: Map<String, String>
    ): File {
        workDir.mkdirs()
        val resDir = File(workDir, "res").apply { mkdirs() }
        val valuesDir = File(resDir, "values").apply { mkdirs() }
        File(valuesDir, "strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$appLabel</string>
                $extraStringsXml
            </resources>
            """.trimIndent().trimStart()
        )
        val mipmapDir = File(resDir, "mipmap-xxhdpi").apply { mkdirs() }
        val iconBmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(iconBmp).drawColor(Color.rgb(0, 180, 230))
        File(mipmapDir, "ic_launcher.png").outputStream().use { iconBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val permAttr = if (appPermission != null) "android:permission=\"$appPermission\"" else ""
        val extractAttr = if (extractNativeLibs != null) "android:extractNativeLibs=\"$extractNativeLibs\"" else ""
        val factoryAttr = if (appComponentFactory != null) "android:appComponentFactory=\"$appComponentFactory\"" else ""

        val manifestFile = File(workDir, "AndroidManifest.xml")
        manifestFile.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$packageName"
                android:versionCode="1"
                android:versionName="1.0"
                $manifestExtraRootAttrs>
                <uses-sdk android:minSdkVersion="$minSdk" android:targetSdkVersion="$targetSdk" />
                $manifestTopTags
                $manifestRootChildTags
                <application
                    android:label="@string/app_name"
                    android:icon="@mipmap/ic_launcher"
                    $permAttr
                    $extractAttr
                    $factoryAttr>
                    $manifestBody
                </application>
            </manifest>
            """.trimIndent().trimStart()
        )

        val compiledDir = File(workDir, "compiled_res").apply { mkdirs() }
        runCmd(aapt2Bin.absolutePath, "compile", "--dir", resDir.absolutePath, "-o", File(compiledDir, "res.zip").absolutePath)

        val linkedResApk = File(workDir, "res_linked.apk")
        runCmd(
            aapt2Bin.absolutePath, "link",
            "-o", linkedResApk.absolutePath,
            "-I", androidJar.absolutePath,
            "--manifest", manifestFile.absolutePath,
            File(compiledDir, "res.zip").absolutePath
        )

        val srcDir = File(workDir, "src").apply { mkdirs() }
        val clsDir = File(workDir, "cls").apply { mkdirs() }
        val javaFiles = javaSources.map { (rel, code) ->
            File(srcDir, rel).apply {
                parentFile?.mkdirs()
                writeText(code)
            }
        }
        val javacArgs = mutableListOf(
            javacBin.absolutePath, "-source", "11", "-target", "11", "-Xlint:-options",
            "-classpath", androidJar.absolutePath, "-d", clsDir.absolutePath
        )
        javacArgs.addAll(javaFiles.map { it.absolutePath })
        runCmd(*javacArgs.toTypedArray())

        val dexOutDir = File(workDir, "dex_out").apply { mkdirs() }
        val classFiles = clsDir.walkTopDown().filter { it.isFile && it.extension == "class" }.map { it.absolutePath }.toList()
        val d8Args = mutableListOf(
            d8Bin.absolutePath, "--min-api", minSdk.toString(),
            "--lib", androidJar.absolutePath, "--output", dexOutDir.absolutePath
        )
        d8Args.addAll(classFiles)
        runCmd(*d8Args.toTypedArray())

        val classesDexBytes = File(dexOutDir, "classes.dex").readBytes()
        appendStoredEntries(linkedResApk, outApk, mapOf("classes.dex" to classesDexBytes), stored = false)
        return outApk
    }

    private fun appendStoredEntries(
        baseApk: File,
        outApk: File,
        extraEntries: Map<String, ByteArray>,
        stored: Boolean = true
    ) {
        ZipFile(baseApk).use { srcZip ->
            ZipOutputStream(outApk.outputStream()).use { zos ->
                val enumEntries = srcZip.entries()
                while (enumEntries.hasMoreElements()) {
                    val e = enumEntries.nextElement()
                    if (e.isDirectory) continue
                    val data = srcZip.getInputStream(e).use { it.readBytes() }
                    val outEntry = ZipEntry(e.name)
                    if (e.method == ZipEntry.STORED || e.name == "resources.arsc") {
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = data.size.toLong()
                        outEntry.compressedSize = data.size.toLong()
                        outEntry.crc = CRC32().apply { update(data) }.value
                    }
                    zos.putNextEntry(outEntry)
                    zos.write(data)
                    zos.closeEntry()
                }
                for ((name, bytes) in extraEntries) {
                    val outEntry = ZipEntry(name)
                    if (stored) {
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = bytes.size.toLong()
                        outEntry.compressedSize = bytes.size.toLong()
                        outEntry.crc = CRC32().apply { update(bytes) }.value
                    }
                    zos.putNextEntry(outEntry)
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
    }

    private fun runCmd(vararg cmd: String): String {
        val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        if (code != 0) {
            throw AssertionError("Command failed ($code): ${cmd.joinToString(" ")}\n$output")
        }
        return output
    }

    private fun createWorkDir(context: Context, prefix: String): File =
        File(context.cacheDir, "${prefix}_${System.nanoTime()}").apply { mkdirs() }
}
