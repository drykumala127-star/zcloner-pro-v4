package com.fms.clone

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import com.fms.security.FMSCryptoAdapter
import java.io.File
import java.util.zip.ZipFile

/**
 * FMSAPKInspector, FMSCompatibilityEngine & FMSSecurityScanner:
 * Inspects installed launcher apps, system packages (where visible), and external .apk files.
 */
class FMSAPKInspector(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val iconCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    fun getCachedIcon(packageName: String): Bitmap? =
        iconCache.get(packageName)?.takeUnless { it.isRecycled }

    fun loadAppIcon(packageName: String): Bitmap? {
        getCachedIcon(packageName)?.let { return it }
        val appInfo = getApplicationInfoSafe(packageName) ?: return null
        val bmp = runCatching { drawableToBitmap(pm.getApplicationIcon(appInfo), 96) }.getOrNull()
            ?: return null
        iconCache.put(packageName, bmp)
        return bmp
    }

    fun loadVisibleApps(includeSystem: Boolean, loadIcons: Boolean = false): List<InstalledAppInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val uniquePackages = resolved.mapNotNull { it.activityInfo?.packageName }.distinct()
        val results = mutableListOf<InstalledAppInfo>()
        for (pkg in uniquePackages) {
            if (pkg == context.packageName) continue
            val pkgInfo = getPackageInfoSafe(pkg) ?: continue
            val appInfo = pkgInfo.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem != includeSystem) continue

            val label = pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
            val sourcePath = appInfo.sourceDir
            val size = sourcePath?.let { File(it).takeIf(File::exists)?.length() } ?: 0L
            val splitCount = appInfo.splitSourceDirs?.size ?: 0
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            val iconBmp = if (loadIcons) {
                loadAppIcon(pkg)
            } else {
                getCachedIcon(pkg)
            }
            results.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    versionName = pkgInfo.versionName ?: "1.0",
                    versionCode = versionCode,
                    isSystemApp = isSystem,
                    sourceApkPath = sourcePath,
                    hasSplitApks = splitCount > 0,
                    apkSizeBytes = size,
                    minSdk = appInfo.minSdkVersion,
                    targetSdk = appInfo.targetSdkVersion,
                    requestedPermissions = pkgInfo.requestedPermissions?.toList().orEmpty(),
                    iconBitmap = iconBmp
                )
            )
        }
        return results.sortedBy { it.appName.lowercase() }
    }

    fun inspectApkFile(apkFile: File, hasSplitApksHint: Boolean = false): Result<ApkInspectionResult> = runCatching {
        require(apkFile.exists() && apkFile.isFile) { "APK file not found: ${apkFile.name}" }
        ZipFile(apkFile).use { zip ->
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
                ?: error("Invalid APK: AndroidManifest.xml missing.")
            val manifestBytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
            val manifestSummary = FMSManifestEngine.inspectManifest(manifestBytes).getOrThrow()

            var entryCount = 0
            var hasDex = false
            val abis = mutableSetOf<String>()
            val iconPaths = mutableListOf<String>()
            var certBytes: ByteArray? = null

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                entryCount++
                val name = e.name
                if (name.endsWith(".dex")) hasDex = true
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    val parts = name.split("/")
                    if (parts.size >= 3) abis.add(parts[1])
                }
                if (!name.endsWith(".9.png", ignoreCase = true) &&
                    (name.contains("ic_launcher") || name.endsWith("/icon.png", true) || name.endsWith("/icon.webp", true)) &&
                    (name.endsWith(".png", ignoreCase = true) || name.endsWith(".webp", ignoreCase = true))
                ) {
                    iconPaths.add(name)
                }
                if (certBytes == null && name.startsWith("META-INF/") &&
                    (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))
                ) {
                    certBytes = zip.getInputStream(e).use { it.readBytes() }
                }
            }

            val hasApkSigBlockV2Plus = detectApkSigBlockV2Plus(apkFile)
            val effectiveSplit = hasSplitApksHint ||
                !manifestSummary.splitName.isNullOrBlank() ||
                !manifestSummary.configForSplit.isNullOrBlank() ||
                manifestSummary.isFeatureSplit ||
                manifestSummary.hasSplitsRequiredMeta

            val pkgName = manifestSummary.packageName.ifBlank { apkFile.nameWithoutExtension }
            val appInfoFromPm = getPackageInfoSafe(pkgName)
            val appName = manifestSummary.applicationLabel
                ?: appInfoFromPm?.applicationInfo?.let { pm.getApplicationLabel(it).toString() }
                ?: pkgName

            val certSha256 = certBytes?.let { raw ->
                extractCertSha256FromPkcs7(raw) ?: FMSCryptoAdapter.sha256Hex(raw)
            } ?: if (hasApkSigBlockV2Plus) {
                "V2/V3 Signing Block (Tanpa Sertifikat v1)"
            } else {
                "Tidak Terdeteksi"
            }

            val certSubject = when {
                certBytes != null && hasApkSigBlockV2Plus -> "APK Signature v1 (PKCS#7) + v2/v3 Block"
                certBytes != null -> "APK Signature v1 (PKCS#7 / JAR)"
                hasApkSigBlockV2Plus -> "APK Signature Scheme v2/v3 Block"
                else -> "Unsigned / Tidak Bertanda Tangan"
            }

            val compatibility = FMSCompatibilityEngine.analyze(
                manifest = manifestSummary,
                hasSplitApks = effectiveSplit,
                hasDex = hasDex,
                nativeAbis = abis.toList(),
                hasApkSigBlockV2Plus = hasApkSigBlockV2Plus
            )

            ApkInspectionResult(
                packageName = pkgName,
                appName = appName,
                versionName = manifestSummary.versionName ?: appInfoFromPm?.versionName ?: "1.0",
                versionCode = manifestSummary.versionCode?.toLong() ?: 1L,
                apkPath = apkFile.absolutePath,
                apkSizeBytes = apkFile.length(),
                entryCount = entryCount,
                hasSplitApks = effectiveSplit,
                hasDex = hasDex,
                nativeAbis = abis.sorted(),
                iconEntryPaths = iconPaths.sorted(),
                certSha256 = certSha256,
                certSubject = certSubject,
                manifestSummary = manifestSummary,
                compatibility = compatibility,
                hasApkSigBlockV2Plus = hasApkSigBlockV2Plus
            )
        }
    }

    internal fun detectApkSigBlockV2Plus(apkFile: File): Boolean = runCatching {
        val len = apkFile.length()
        if (len < 64L) return@runCatching false
        java.io.RandomAccessFile(apkFile, "r").use { raf ->
            val tailLen = minOf(len, 65558L).toInt()
            val tailStart = len - tailLen
            val tail = ByteArray(tailLen)
            raf.seek(tailStart)
            raf.readFully(tail)
            var eocdOffset = -1
            for (i in tail.size - 22 downTo 0) {
                if (tail[i] == 0x50.toByte() &&
                    tail[i + 1] == 0x4B.toByte() &&
                    tail[i + 2] == 0x05.toByte() &&
                    tail[i + 3] == 0x06.toByte()
                ) {
                    val commentLen = (tail[i + 20].toInt() and 0xFF) or ((tail[i + 21].toInt() and 0xFF) shl 8)
                    if (i + 22 + commentLen == tail.size) {
                        eocdOffset = i
                        break
                    }
                }
            }
            if (eocdOffset < 0) return@runCatching false
            val cdOffset = (tail[eocdOffset + 16].toLong() and 0xFFL) or
                ((tail[eocdOffset + 17].toLong() and 0xFFL) shl 8) or
                ((tail[eocdOffset + 18].toLong() and 0xFFL) shl 16) or
                ((tail[eocdOffset + 19].toLong() and 0xFFL) shl 24)
            if (cdOffset < 24L || cdOffset > len) return@runCatching false
            val magic = ByteArray(16)
            raf.seek(cdOffset - 16L)
            raf.readFully(magic)
            String(magic, Charsets.US_ASCII) == "APK Sig Block 42"
        }
    }.getOrDefault(false)

    private fun extractCertSha256FromPkcs7(derBytes: ByteArray): String? = runCatching {
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        val certs = cf.generateCertificates(derBytes.inputStream())
        val first = certs.firstOrNull() ?: return@runCatching null
        FMSCryptoAdapter.sha256Hex(first.encoded)
    }.getOrNull()

    private fun getPackageInfoSafe(pkg: String): PackageInfo? = runCatching {
        val flags = PackageManager.GET_PERMISSIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, flags)
        }
    }.getOrNull()

    private fun getApplicationInfoSafe(pkg: String): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, 0)
        }
    }.getOrNull()

    fun extractIconBitmapFromApk(apkFile: File, iconEntryPaths: List<String>): Bitmap? = runCatching {
        val preferred = iconEntryPaths
            .sortedByDescending { path ->
                when {
                    path.contains("xxxhdpi") -> 5
                    path.contains("xxhdpi") -> 4
                    path.contains("xhdpi") -> 3
                    path.contains("hdpi") -> 2
                    else -> 1
                }
            }
            .firstOrNull() ?: return@runCatching null
        ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry(preferred) ?: return@use null
            zip.getInputStream(entry).use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        }
    }.getOrNull()

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }
}

/**
 * FMSCompatibilityEngine: Honest non-root Android compatibility analyzer.
 */
object FMSCompatibilityEngine {
    fun analyze(
        manifest: ManifestSummary,
        hasSplitApks: Boolean,
        hasDex: Boolean,
        nativeAbis: List<String>,
        hasApkSigBlockV2Plus: Boolean = false,
        deviceSdkInt: Int = Build.VERSION.SDK_INT,
        deviceSupportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()
    ): CompatibilityAnalysis {
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var score = 100

        if (hasSplitApks || !manifest.splitName.isNullOrBlank() || !manifest.configForSplit.isNullOrBlank() || manifest.isFeatureSplit || manifest.hasSplitsRequiredMeta) {
            blockers.add("Split APK / App Bundle terdeteksi: satu berkas base/config APK tidak memuat seluruh resource & modul split sehingga hasil repackage tidak dapat dipasang sebagai APK tunggal.")
            score -= 55
        }
        if (!manifest.sharedUserId.isNullOrBlank()) {
            blockers.add("Menggunakan android:sharedUserId (${manifest.sharedUserId}) yang bergantung pada signature kunci asli.")
            score -= 50
        }
        if (!hasDex) {
            blockers.add("APK tidak memuat classes.dex (kemungkinan resource-only / split config) dan tidak dapat dikloning sebagai aplikasi mandiri.")
            score -= 55
        }
        if (manifest.minSdkVersion != null && deviceSdkInt > 0 && manifest.minSdkVersion > deviceSdkInt) {
            blockers.add("APK membutuhkan minimal Android API ${manifest.minSdkVersion}, sedangkan perangkat/lingkungan aktif berada pada API $deviceSdkInt.")
            score -= 55
        }
        if (nativeAbis.isNotEmpty() && deviceSupportedAbis.isNotEmpty() && nativeAbis.none { it in deviceSupportedAbis }) {
            blockers.add("Ketidakcocokan ABI native: APK hanya memuat [${nativeAbis.joinToString()}], sedangkan perangkat mendukung [${deviceSupportedAbis.joinToString()}].")
            score -= 50
        }
        if (manifest.usesPlayLicensing) {
            warnings.add("Izin com.android.vending.CHECK_LICENSE terdeteksi: validasi lisensi Play Store dapat gagal setelah re-signing lokal.")
            score -= 20
        }
        if (nativeAbis.isNotEmpty()) {
            warnings.add("Memuat native libraries (${nativeAbis.joinToString()}): perubahan package name dapat memicu validasi JNI.")
            score -= 10
        }
        if (manifest.extractNativeLibs == false && nativeAbis.isNotEmpty()) {
            warnings.add("extractNativeLibs=false terdeteksi: file .so akan dijaga STORED dan disejajarkan halaman (4KB/16KB) agar tidak gagal instal.")
        }
        if (hasApkSigBlockV2Plus) {
            warnings.add("Sumber bertanda tangan v2/v3; APK klon ditandatangani ulang memakai skema v1 (JAR PKCS#7) + v2 (APK Sig Block) lokal.")
        }
        if (manifest.minSdkVersion != null && manifest.minSdkVersion < 21) {
            warnings.add("minSdkVersion=${manifest.minSdkVersion}: injeksi DEX tambahan Dhedry otomatis dilewati pada target pra-Android 5.0 (Dalvik) untuk mencegah force close saat startup.")
        }
        if (manifest.launcherActivityName.isNullOrBlank()) {
            warnings.add("Aktivitas MAIN/LAUNCHER tidak terdeteksi pada manifest; Dhedry Animated Loading tidak dapat disisipkan sebagai launcher.")
        }
        if (manifest.authorityStrings.isNotEmpty()) {
            warnings.add("${manifest.authorityStrings.size} ContentProvider authority akan ditulis ulang agar tidak bentrok dengan aplikasi asli.")
            score -= 5
        }
        if (manifest.referenceAuthoritiesCount > 0) {
            warnings.add("${manifest.referenceAuthoritiesCount} ContentProvider authority menggunakan referensi @string/... di resources.arsc sehingga berisiko bentrok (CONFLICTING_PROVIDER) jika dipasang berdampingan dengan aplikasi asli.")
            score -= 15
        }
        if (manifest.customPermissions.isNotEmpty()) {
            warnings.add("${manifest.customPermissions.size} izin kustom paket asal (termasuk DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION jika ada) akan ditulis ulang ke package target.")
        }
        if (manifest.requiredUsesLibraries.isNotEmpty()) {
            warnings.add("Membutuhkan shared library wajib: ${manifest.requiredUsesLibraries.joinToString()}.")
            score -= 5
        }
        warnings.add("Penandatanganan ulang dengan keystore lokal membatalkan update langsung Play Store dan dapat memicu pemeriksaan Play Integrity/certificate pinning.")

        val status = when {
            blockers.isNotEmpty() -> CompatibilityStatus.UNSUPPORTED
            score >= 80 -> CompatibilityStatus.COMPATIBLE
            else -> CompatibilityStatus.LIMITED
        }

        val notes = if (blockers.isEmpty()) {
            "Struktur APK tunggal siap dikemas ulang melalui FMSManifestEngine & FMSSignatureManager serta diuji 10 tahap."
        } else {
            "Proses kloning diblokir demi stabilitas karena ditemukan penghalang kompatibilitas; APK asli Anda tetap aman."
        }
        return CompatibilityAnalysis(
            status = status,
            score = score.coerceIn(0, 100),
            blockers = blockers,
            warnings = warnings,
            notes = notes
        )
    }
}

/**
 * FMSSecurityScanner: Evidence-based static manifest & APK risk scanner with explicit limitations.
 */
object FMSSecurityScanner {
    private val highRiskPermissions = setOf(
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_CONTACTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.QUERY_ALL_PACKAGES"
    )

    fun scan(inspection: ApkInspectionResult): SecurityScanResult {
        val m = inspection.manifestSummary
        val findings = mutableListOf<SecurityScanFinding>()
        var score = 0

        if (m.debuggable) {
            score += 35
            findings.add(
                SecurityScanFinding(
                    id = "SEC-DEBUG",
                    title = "Flag android:debuggable aktif",
                    severity = RiskLevel.HIGH,
                    detail = "Aplikasi mengizinkan lampiran debugger di build APK ini.",
                    remediation = "Aktifkan opsi matikan debuggable pada konfigurasi ZClone sebelum build."
                )
            )
        }
        if (m.usesCleartextTraffic) {
            score += 20
            findings.add(
                SecurityScanFinding(
                    id = "SEC-CLEARTEXT",
                    title = "Lalu lintas HTTP non-terenkripsi diizinkan",
                    severity = RiskLevel.MEDIUM,
                    detail = "android:usesCleartextTraffic=true dapat mengekspos permintaan HTTP tanpa TLS.",
                    remediation = "Gunakan jaringan tepercaya atau tinjau kebijakan Network Security Config."
                )
            )
        }
        if (m.allowBackup) {
            score += 8
            findings.add(
                SecurityScanFinding(
                    id = "SEC-BACKUP",
                    title = "allowBackup aktif",
                    severity = RiskLevel.LOW,
                    detail = "Data aplikasi dapat disertakan pada cadangan ADB jika diizinkan sistem.",
                    remediation = "Pastikan data sensitif tidak tersimpan dalam plaintext."
                )
            )
        }
        val dangerousPerms = m.permissions.filter { it in highRiskPermissions }
        if (dangerousPerms.isNotEmpty()) {
            val pts = (dangerousPerms.size * 7).coerceAtMost(30)
            score += pts
            findings.add(
                SecurityScanFinding(
                    id = "SEC-PERMS",
                    title = "${dangerousPerms.size} izin berisiko tinggi terdeteksi",
                    severity = if (dangerousPerms.size >= 4) RiskLevel.HIGH else RiskLevel.MEDIUM,
                    detail = dangerousPerms.take(5).joinToString(", "),
                    remediation = "Pertimbangkan mencabut izin yang tidak diperlukan lewat Izin Kustom."
                )
            )
        }
        if (m.exportedComponentsCount > 0) {
            score += (m.exportedComponentsCount * 3).coerceAtMost(15)
            findings.add(
                SecurityScanFinding(
                    id = "SEC-EXPORTED",
                    title = "${m.exportedComponentsCount} komponen android:exported=true",
                    severity = RiskLevel.LOW,
                    detail = "Komponen yang diekspor dapat dipanggil oleh aplikasi lain bila tidak dilindungi permission.",
                    remediation = "Periksa komponen penerima Intent pada manifest."
                )
            )
        }
        if (inspection.certSha256 == "Tidak Terdeteksi" && !inspection.hasApkSigBlockV2Plus) {
            score += 20
            findings.add(
                SecurityScanFinding(
                    id = "SEC-UNSIGNED",
                    title = "APK tidak bertanda tangan (Unsigned)",
                    severity = RiskLevel.MEDIUM,
                    detail = "Tidak ditemukan sertifikat PKCS#7 v1 maupun APK Signing Block v2/v3.",
                    remediation = "Bangun dan tandatangani ulang APK sebelum pemasangan di perangkat Android."
                )
            )
        }

        val clampedScore = score.coerceIn(0, 100)
        val level = when {
            clampedScore >= 55 -> RiskLevel.HIGH
            clampedScore >= 25 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val summary = "Skor risiko statis $clampedScore/100 (${level.name}): ${dangerousPerms.size} izin sensitif, ${m.exportedComponentsCount} komponen exported."

        return SecurityScanResult(
            packageName = inspection.packageName,
            appName = inspection.appName,
            riskScore = clampedScore,
            riskLevel = level,
            exportedComponentsCount = m.exportedComponentsCount,
            dangerousPermissionsCount = dangerousPerms.size,
            cleartextAllowed = m.usesCleartextTraffic,
            debuggableFlag = m.debuggable,
            certSha256Prefix = inspection.certSha256.take(16),
            findings = findings,
            summary = summary,
            limitationNotice = "Pemindai statis ini menganalisis manifest, izin, dan sertifikat APK; tidak menggantikan analisis perilaku runtime atau jaminan bebas malware."
        )
    }
}
