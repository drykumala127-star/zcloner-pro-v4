package com.fms.clone

import android.os.Build
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class FmsCloneValidationException(
    val failureInfo: CloneBuildFailureInfo,
    val partialReport: ApkValidationReport? = null
) : IllegalStateException(failureInfo.userMessage)

/**
 * FMSCloneValidator:
 * Performs the 7 mandatory automated pre-release APK validation checks before any cloned APK
 * is handed to the user, and initializes Stages 8..10 honestly as PENDING_DEVICE_TEST until
 * tested on physical Android hardware.
 */
object FMSCloneValidator {
    private val DEX_MAGIC = byteArrayOf(0x64, 0x65, 0x78, 0x0A) // "dex\n"

    fun validateBuiltCloneApk(
        apkFile: File,
        expectedTargetPackage: String,
        sourceManifest: ManifestSummary,
        sourceHasResArsc: Boolean,
        request: CloneBuildRequest,
        expectDhedryInjected: Boolean,
        expectedDhedryDexName: String?,
        deviceSdkInt: Int = Build.VERSION.SDK_INT,
        deviceSupportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()
    ): ApkValidationReport {
        val steps = mutableListOf<ApkValidationStep>()
        val ioBuf = ByteArray(64 * 1024)

        // Stage 1: Validasi Struktur APK (ZIP, EOCD, Manifest presence, Zip-Slip, duplicates)
        var manifestBytes: ByteArray? = null
        val entriesSnapshot = mutableListOf<ZipEntry>()
        val stage1Result = runCatching {
            require(apkFile.exists() && apkFile.isFile && apkFile.length() > 64L) {
                "File APK hasil build kosong atau tidak ditemukan."
            }
            ZipFile(apkFile).use { zip ->
                val seen = hashSetOf<String>()
                var manifestCount = 0
                val enumEntries = zip.entries()
                while (enumEntries.hasMoreElements()) {
                    val e = enumEntries.nextElement()
                    require(!e.name.contains("..") && !e.name.startsWith("/")) {
                        "Terdeteksi path entri tidak aman: ${e.name}"
                    }
                    require(seen.add(e.name)) {
                        "Terdeteksi entri duplikat di dalam arsip APK: ${e.name}"
                    }
                    if (e.name == "AndroidManifest.xml") {
                        manifestCount++
                        manifestBytes = zip.getInputStream(e).use { it.readBytes() }
                    }
                    entriesSnapshot.add(e)
                }
                require(manifestCount == 1 && manifestBytes != null) {
                    "AndroidManifest.xml hilang atau duplikat (jumlah=$manifestCount)."
                }
                require(entriesSnapshot.isNotEmpty()) { "Arsip APK tidak memiliki entri." }
            }
        }
        steps += if (stage1Result.isSuccess) {
            ApkValidationStep(
                stageNumber = 1,
                titleId = "Validasi Struktur APK",
                titleEn = "APK Structure Validation",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.PASS,
                detail = "Arsip ZIP valid (${entriesSnapshot.size} entri), AndroidManifest.xml tunggal & bebas duplikasi."
            )
        } else {
            ApkValidationStep(
                stageNumber = 1,
                titleId = "Validasi Struktur APK",
                titleEn = "APK Structure Validation",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.FAILED,
                detail = stage1Result.exceptionOrNull()?.message ?: "Struktur ZIP APK rusak."
            )
        }

        // Stage 2: Pemeriksaan Manifest (AXML parseability, package match, custom permissions, providers)
        var parsedManifest: ManifestSummary? = null
        var stage2Warning = false
        val stage2Result = runCatching {
            val rawAxml = requireNotNull(manifestBytes) { "AndroidManifest.xml tidak terbaca." }
            val summary = FMSManifestEngine.validateBinaryAxml(rawAxml).getOrThrow()
            parsedManifest = summary
            if (expectedTargetPackage.isNotBlank()) {
                require(summary.packageName == expectedTargetPackage) {
                    "Package manifest (${summary.packageName}) tidak sesuai target ($expectedTargetPackage)."
                }
            }
            if (request.disableDebuggable) {
                require(!summary.debuggable) {
                    "Flag android:debuggable masih aktif setelah mutasi."
                }
            }
            if (request.changePackageName && request.sourcePackage.isNotBlank() && request.sourcePackage != expectedTargetPackage) {
                val leakedDynamicPerm = "${request.sourcePackage}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                require(!summary.customPermissions.contains(leakedDynamicPerm) && !summary.permissions.contains(leakedDynamicPerm)) {
                    "Izin kustom $leakedDynamicPerm belum ditulis ulang ke $expectedTargetPackage (berisiko duplicate permission / force close)."
                }
            }
            if (expectDhedryInjected) {
                require(summary.launcherActivityName == FMSManifestEngine.DHEDRY_SPLASH_ACTIVITY) {
                    "DhedrySplashActivity belum terpasang sebagai MAIN/LAUNCHER."
                }
            }
            if (summary.referenceAuthoritiesCount > 0 && request.changePackageName) {
                stage2Warning = true
            }
        }
        steps += when {
            stage2Result.isFailure -> ApkValidationStep(
                stageNumber = 2,
                titleId = "Pemeriksaan AndroidManifest",
                titleEn = "Manifest Verification",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.FAILED,
                detail = stage2Result.exceptionOrNull()?.message ?: "AndroidManifest.xml gagal diurai (Parse Error)."
            )
            stage2Warning -> ApkValidationStep(
                stageNumber = 2,
                titleId = "Pemeriksaan AndroidManifest",
                titleEn = "Manifest Verification",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.WARNING,
                detail = "Manifest AXML valid (${parsedManifest?.packageName}); ada ${parsedManifest?.referenceAuthoritiesCount} provider @string yang berisiko bentrok jika dipasang berdampingan dengan app asli."
            )
            else -> ApkValidationStep(
                stageNumber = 2,
                titleId = "Pemeriksaan AndroidManifest",
                titleEn = "Manifest Verification",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.PASS,
                detail = "AXML terurai bersih (${parsedManifest?.packageName}), izin kustom & otoritas literal sinkron."
            )
        }

        // Stage 3: Pemeriksaan DEX dan Resource
        val dexEntries = entriesSnapshot.filter { !it.isDirectory && it.name.endsWith(".dex", ignoreCase = true) }
        val stage3Result = runCatching {
            require(dexEntries.isNotEmpty()) { "Tidak ada file classes*.dex di dalam APK." }
            ZipFile(apkFile).use { zip ->
                for (dex in dexEntries) {
                    zip.getInputStream(dex).use { inp ->
                        val magic = ByteArray(4)
                        val read = inp.read(magic)
                        require(read == 4 && magic.contentEquals(DEX_MAGIC)) {
                            "Header file ${dex.name} bukan magic DEX yang valid (risiko force close / parse error)."
                        }
                    }
                }
                if (sourceHasResArsc || entriesSnapshot.any { it.name == "resources.arsc" }) {
                    val arsc = zip.getEntry("resources.arsc")
                        ?: error("resources.arsc hilang dari hasil pengemasan ulang.")
                    require(arsc.method == ZipEntry.STORED) {
                        "resources.arsc terkompresi (DEFLATED); Android 11+ mewajibkan STORED (tanpa kompresi)."
                    }
                    require(arsc.size > 0L) { "resources.arsc kosong (0 byte)." }
                    require(FMSSignatureManager.isEntryDataAligned(apkFile, "resources.arsc", 4)) {
                        "resources.arsc tidak sejajar 4-byte pada arsip ZIP."
                    }
                }
                if (expectDhedryInjected) {
                    val dexName = requireNotNull(expectedDhedryDexName) { "Nama entri DEX Dhedry tidak tercatat." }
                    require(zip.getEntry(dexName) != null) { "Runtime Dhedry DEX ($dexName) tidak ditemukan." }
                    val audio = zip.getEntry(FMSRepackager.DHEDRY_APK_AUDIO_ENTRY)
                        ?: error("Audio boot Dhedry tidak ditemukan di dalam APK.")
                    require(audio.method == ZipEntry.STORED && audio.size > 0L) {
                        "Audio boot Dhedry wajib STORED dan tidak boleh kosong."
                    }
                    require(FMSSignatureManager.isEntryDataAligned(apkFile, FMSRepackager.DHEDRY_APK_AUDIO_ENTRY, 4)) {
                        "Audio boot Dhedry tidak sejajar 4-byte."
                    }
                }
                zip.getEntry(FMSRepackager.DHEDRY_DEPENDENCY_GRAPH_ENTRY)?.let { dgEntry ->
                    val raw = zip.getInputStream(dgEntry).use { it.readBytes().toString(Charsets.UTF_8) }
                    val obj = JSONObject(raw)
                    require(obj.optString("schema") == "fms.dependency_graph.v1") {
                        "Skema dependency_graph.json di APK tidak valid."
                    }
                }
                zip.getEntry(FMSRepackager.DHEDRY_REPRODUCIBLE_BUILD_ENTRY)?.let { repEntry ->
                    val raw = zip.getInputStream(repEntry).use { it.readBytes().toString(Charsets.UTF_8) }
                    val obj = JSONObject(raw)
                    require(obj.optString("schema") == "fms.reproducible_build.v1") {
                        "Skema reproducible_build.json di APK tidak valid."
                    }
                }
            }
        }
        steps += if (stage3Result.isSuccess) {
            ApkValidationStep(
                stageNumber = 3,
                titleId = "Pemeriksaan DEX & Resource",
                titleEn = "DEX & Resource Check",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.PASS,
                detail = "${dexEntries.size} DEX valid ('dex\\n') & resources.arsc STORED sejajar 4-byte."
            )
        } else {
            ApkValidationStep(
                stageNumber = 3,
                titleId = "Pemeriksaan DEX & Resource",
                titleEn = "DEX & Resource Check",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.FAILED,
                detail = stage3Result.exceptionOrNull()?.message ?: "Verifikasi DEX / resources.arsc gagal."
            )
        }

        // Stage 4: Pemeriksaan Dependensi & ABI (.so alignment & ABI match)
        val soEntries = entriesSnapshot.filter { !it.isDirectory && it.name.startsWith("lib/") && it.name.endsWith(".so") }
        val nativeAbis = soEntries.mapNotNull { it.name.split("/").getOrNull(1) }.distinct().sorted()
        var stage4Warning = false
        val stage4Result = runCatching {
            val extractNativeLibs = parsedManifest?.extractNativeLibs ?: sourceManifest.extractNativeLibs
            if (extractNativeLibs == false && soEntries.isNotEmpty()) {
                for (so in soEntries) {
                    require(so.method == ZipEntry.STORED) {
                        "Manifest memakai extractNativeLibs=false namun ${so.name} masih terkompresi."
                    }
                    require(FMSSignatureManager.isEntryDataAligned(apkFile, so.name, 4096)) {
                        "Library native ${so.name} tidak sejajar halaman 4096-byte."
                    }
                }
            }
            if (nativeAbis.isNotEmpty() && deviceSupportedAbis.isNotEmpty()) {
                require(nativeAbis.any { it in deviceSupportedAbis }) {
                    "ABI APK [${nativeAbis.joinToString()}] tidak kompatibel dengan perangkat [${deviceSupportedAbis.joinToString()}]."
                }
            }
            if ((parsedManifest?.requiredUsesLibraries ?: sourceManifest.requiredUsesLibraries).isNotEmpty()) {
                stage4Warning = true
            }
        }
        steps += when {
            stage4Result.isFailure -> ApkValidationStep(
                stageNumber = 4,
                titleId = "Dependensi & Native ABI",
                titleEn = "Dependencies & Native ABI",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.FAILED,
                detail = stage4Result.exceptionOrNull()?.message ?: "Ketidakcocokan ABI atau alignment library native."
            )
            stage4Warning -> ApkValidationStep(
                stageNumber = 4,
                titleId = "Dependensi & Native ABI",
                titleEn = "Dependencies & Native ABI",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.WARNING,
                detail = "ABI OK (${if (nativeAbis.isEmpty()) "Pure DEX" else nativeAbis.joinToString()}); manifest mewajibkan uses-library: ${sourceManifest.requiredUsesLibraries.joinToString()}."
            )
            else -> ApkValidationStep(
                stageNumber = 4,
                titleId = "Dependensi & Native ABI",
                titleEn = "Dependencies & Native ABI",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.PASS,
                detail = if (nativeAbis.isEmpty()) {
                    "APK murni DEX/resource (kompatibel lintas arsitektur CPU)."
                } else {
                    "ABI native valid & sejajar (${nativeAbis.joinToString()})."
                }
            )
        }

        // Stage 5: Pemeriksaan Kompatibilitas Perangkat (minSdk, split, sharedUserId)
        val stage5Result = runCatching {
            val minSdk = parsedManifest?.minSdkVersion ?: sourceManifest.minSdkVersion
            if (minSdk != null && deviceSdkInt > 0) {
                require(minSdk <= deviceSdkInt) {
                    "APK memerlukan minimal Android API $minSdk (perangkat aktif: API $deviceSdkInt)."
                }
            }
            require(sourceManifest.sharedUserId.isNullOrBlank()) {
                "APK memakai android:sharedUserId yang terikat kunci asli."
            }
            require(!sourceManifest.isFeatureSplit && sourceManifest.splitName.isNullOrBlank() && !sourceManifest.hasSplitsRequiredMeta) {
                "Split APK tidak dapat dikemas ulang menjadi APK tunggal."
            }
        }
        steps += if (stage5Result.isSuccess) {
            val minSdkText = parsedManifest?.minSdkVersion ?: sourceManifest.minSdkVersion
            val targetSdkText = parsedManifest?.targetSdkVersion ?: sourceManifest.targetSdkVersion
            ApkValidationStep(
                stageNumber = 5,
                titleId = "Kompatibilitas Perangkat",
                titleEn = "Device Compatibility",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.PASS,
                detail = "minSdk=${minSdkText ?: "?"}, targetSdk=${targetSdkText ?: "?"} cocok dengan API perangkat ($deviceSdkInt)."
            )
        } else {
            ApkValidationStep(
                stageNumber = 5,
                titleId = "Kompatibilitas Perangkat",
                titleEn = "Device Compatibility",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.FAILED,
                detail = stage5Result.exceptionOrNull()?.message ?: "Versi SDK atau struktur tidak kompatibel dengan perangkat."
            )
        }

        // Stage 6: Verifikasi Signature (JAR v1 manifest/SF/RSA + semua entry SHA-256 + APK Sig Block v2)
        val stage6Result = runCatching {
            require(FMSSignatureManager.verifyApkContainsValidSignature(apkFile)) {
                "Tanda tangan PKCS#7 v1 atau APK Signature Scheme v2 tidak valid."
            }
            verifyAllJarEntryDigests(apkFile)
        }
        steps += if (stage6Result.isSuccess) {
            ApkValidationStep(
                stageNumber = 6,
                titleId = "Verifikasi Signature v1 + v2",
                titleEn = "Signature Verification (v1 + v2)",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.PASS,
                detail = "RSA PKCS#7 v1, seluruh digest entri MANIFEST.MF, dan APK Sig Block 42 v2 terverifikasi."
            )
        } else {
            ApkValidationStep(
                stageNumber = 6,
                titleId = "Verifikasi Signature v1 + v2",
                titleEn = "Signature Verification (v1 + v2)",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.FAILED,
                detail = stage6Result.exceptionOrNull()?.message ?: "Verifikasi tanda tangan digital APK gagal."
            )
        }

        // Stage 7: Pemeriksaan Integritas File Akhir (full stream read CRC + SHA-256)
        var verifiedEntriesCount = 0
        var apkShaPrefix = ""
        val stage7Result = runCatching {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    zip.getInputStream(e).use { inp ->
                        while (inp.read(ioBuf) > 0) {
                            // Full read ensures ZIP CRC32 verification on every entry
                        }
                    }
                    verifiedEntriesCount++
                }
            }
            val md = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().buffered().use { inp ->
                while (true) {
                    val r = inp.read(ioBuf)
                    if (r <= 0) break
                    md.update(ioBuf, 0, r)
                }
            }
            apkShaPrefix = md.digest().take(8).joinToString("") { "%02X".format(it) }
        }
        steps += if (stage7Result.isSuccess) {
            ApkValidationStep(
                stageNumber = 7,
                titleId = "Pemeriksaan Integritas File",
                titleEn = "File Integrity Check",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.PASS,
                detail = "$verifiedEntriesCount entri lulus baca penuh & CRC32 (${apkFile.length()} B, SHA256:$apkShaPrefix...)."
            )
        } else {
            ApkValidationStep(
                stageNumber = 7,
                titleId = "Pemeriksaan Integritas File",
                titleEn = "File Integrity Check",
                category = ValidationCheckCategory.AUTOMATED_BUILD,
                status = ValidationCheckStatus.FAILED,
                detail = stage7Result.exceptionOrNull()?.message ?: "Deteksi korupsi ZIP/CRC saat membaca APK final."
            )
        }

        // Stages 8..10: Honest On-Device Hardware Verification Placeholders
        steps += ApkValidationStep(
            stageNumber = 8,
            titleId = "Pengujian Instalasi Perangkat",
            titleEn = "On-Device Installation Test",
            category = ValidationCheckCategory.DEVICE_RUNTIME,
            status = ValidationCheckStatus.PENDING_DEVICE_TEST,
            detail = "Belum diuji — tekan 'Pasang APK' lalu 'Cek Status Terpasang'."
        )
        steps += ApkValidationStep(
            stageNumber = 9,
            titleId = "Pengujian Peluncuran Pertama",
            titleEn = "First Launch Test",
            category = ValidationCheckCategory.DEVICE_RUNTIME,
            status = ValidationCheckStatus.PENDING_DEVICE_TEST,
            detail = "Belum diuji — jalankan klon setelah pemasangan untuk memverifikasi peluncuran awal."
        )
        steps += ApkValidationStep(
            stageNumber = 10,
            titleId = "Uji Fungsi Utama Pasca-Instalasi",
            titleEn = "Post-Install Core Function Check",
            category = ValidationCheckCategory.DEVICE_RUNTIME,
            status = ValidationCheckStatus.PENDING_DEVICE_TEST,
            detail = "Belum diuji — konfirmasikan stabilitas layar utama pada perangkat Android fisik."
        )

        val automatedSteps = steps.filter { it.category == ValidationCheckCategory.AUTOMATED_BUILD }
        val passedAutomated = automatedSteps.none { it.status == ValidationCheckStatus.FAILED }
        val warningCount = automatedSteps.count { it.status == ValidationCheckStatus.WARNING }
        val summary = when {
            !passedAutomated -> {
                val firstFail = automatedSteps.first { it.status == ValidationCheckStatus.FAILED }
                "DITOLAK di Tahap ${firstFail.stageNumber} (${firstFail.titleId}): ${firstFail.detail}"
            }
            warningCount > 0 ->
                "7/7 pemeriksaan otomatis lulus dengan $warningCount peringatan kompatibilitas; uji perangkat fisik (Tahap 8–10) menunggu konfirmasi."
            else ->
                "7/7 pemeriksaan struktur, manifest, DEX/resource, ABI, signature & integritas LULUS; siap diuji pasang di perangkat (Tahap 8–10)."
        }

        return ApkValidationReport(
            passedAutomatedChecks = passedAutomated,
            steps = steps,
            summaryMessage = summary,
            canInstallOnDevice = passedAutomated
        )
    }

    private fun verifyAllJarEntryDigests(apkFile: File) {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        ZipFile(apkFile).use { zip ->
            val mfEntry = zip.getEntry("META-INF/MANIFEST.MF")
                ?: error("META-INF/MANIFEST.MF tidak ditemukan.")
            val manifest = zip.getInputStream(mfEntry).use { Manifest(it) }
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val upper = e.name.uppercase()
                if (upper == "META-INF/MANIFEST.MF" ||
                    (upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")))
                ) {
                    continue
                }
                val attrs = manifest.getAttributes(e.name)
                    ?: error("Entri ${e.name} tidak terdaftar di META-INF/MANIFEST.MF.")
                val expectedDigest = attrs.getValue("SHA-256-Digest")
                    ?: error("Entri ${e.name} tidak memiliki SHA-256-Digest di MANIFEST.MF.")
                md.reset()
                zip.getInputStream(e).use { inp ->
                    while (true) {
                        val r = inp.read(buf)
                        if (r <= 0) break
                        md.update(buf, 0, r)
                    }
                }
                val actualDigest = android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP)
                require(actualDigest == expectedDigest) {
                    "Checksum SHA-256 tidak cocok untuk entri ${e.name}."
                }
            }
        }
    }

    fun buildFailureInfoFromReport(report: ApkValidationReport): CloneBuildFailureInfo {
        val failStep = report.steps.firstOrNull { it.status == ValidationCheckStatus.FAILED }
        val stageNum = failStep?.stageNumber
        val title = failStep?.titleId ?: "Validasi Pra-Rilis"
        val rawReason = failStep?.detail ?: report.summaryMessage
        val action = when (stageNum) {
            2 -> "Periksa nama package target atau nonaktifkan opsi yang bertentangan dengan manifest APK sumber."
            3 -> "APK sumber mungkin rusak atau memakai perlindungan resource; coba salinan APK tunggal yang bersih."
            4 -> "APK ini tidak membawa library native untuk arsitektur CPU perangkat Anda."
            5 -> "Periksa versi Android minimum atau apakah aplikasi ini Split APK/App Bundle."
            6, 7 -> "Bersihkan cache kerja sementara di Pengaturan lalu ulangi proses build."
            else -> "Periksa kompatibilitas APK sumber sebelum membangun ulang."
        }
        return CloneBuildFailureInfo(
            failedStageNumber = stageNum,
            failedStageTitle = title,
            userMessage = "Proses build dibatalkan agar tidak menghasilkan APK rusak: $rawReason",
            technicalCause = rawReason,
            sourcePreservedNotice = "APK sumber asli Anda dan seluruh backup resep klon tetap aman dan tidak diubah.",
            suggestedAction = action
        )
    }

    fun toFailureInfo(error: Throwable): CloneBuildFailureInfo {
        if (error is FmsCloneValidationException) return error.failureInfo
        val raw = error.message?.takeIf { it.isNotBlank() } ?: "Kesalahan pemrosesan APK tidak diketahui."
        val simpleExplanation = when {
            raw.contains("Split APK", ignoreCase = true) || raw.contains("splits", ignoreCase = true) ->
                "Aplikasi ini berbentuk Split APK / App Bundle sehingga satu berkas APK saja tidak lengkap untuk dikloning."
            raw.contains("sharedUserId", ignoreCase = true) ->
                "Aplikasi mengunci android:sharedUserId yang terikat tanda tangan pengembang asli."
            raw.contains("classes.dex", ignoreCase = true) ->
                "Berkas APK ini tidak memuat kode DEX aplikasi (kemungkinan paket resource/split config)."
            raw.contains("hak modifikasi", ignoreCase = true) ->
                "Konfirmasi kepemilikan atau izin modifikasi APK diperlukan sebelum build."
            else -> raw
        }
        return CloneBuildFailureInfo(
            failedStageNumber = null,
            failedStageTitle = "Persiapan / Pengemasan APK",
            userMessage = simpleExplanation,
            technicalCause = raw,
            sourcePreservedNotice = "APK sumber asli Anda dan seluruh backup konfigurasi tetap utuh serta tidak dihapus.",
            suggestedAction = "Periksa status kompatibilitas APK sumber atau sesuaikan opsi sebelum mencoba lagi."
        )
    }
}
