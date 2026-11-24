package com.fms.clone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Base64
import com.fms.security.FMSCryptoAdapter
import com.fms.security.FMSIntegrityEngine
import com.fms.security.FMSKeyManager
import com.fms.security.LocalSigningIdentity
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * FMSIconEngine: Real Bitmap hue-rotation / tinting / badge overlay for APK icon patching.
 */
object FMSIconEngine {
    fun transformIconPng(pngBytes: ByteArray, hueShiftDegrees: Int, roundedBadge: Boolean = true): ByteArray {
        return transformIconRaster(pngBytes, isWebp = false, hueShiftDegrees = hueShiftDegrees, roundedBadge = roundedBadge)
    }

    fun transformIconRaster(
        bytes: ByteArray,
        isWebp: Boolean,
        hueShiftDegrees: Int,
        roundedBadge: Boolean = true
    ): ByteArray {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        var outBmp: Bitmap? = null
        try {
            val width = original.width.coerceAtLeast(48)
            val height = original.height.coerceAtLeast(48)
            val targetBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            outBmp = targetBmp
            val canvas = Canvas(targetBmp)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (hueShiftDegrees != 0) {
                paint.colorFilter = ColorMatrixColorFilter(createHueRotateMatrix(hueShiftDegrees.toFloat()))
            }
            canvas.drawBitmap(original, 0f, 0f, paint)

            if (roundedBadge) {
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#00D2FF")
                }
                val r = (width * 0.14f).coerceAtLeast(6f)
                canvas.drawCircle(width - r * 1.3f, height - r * 1.3f, r, badgePaint)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (r * 0.25f).coerceAtLeast(1.5f)
                    color = android.graphics.Color.WHITE
                }
                canvas.drawCircle(width - r * 1.3f, height - r * 1.3f, r, borderPaint)
            }

            val outStream = ByteArrayOutputStream()
            val format = if (isWebp) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            } else {
                Bitmap.CompressFormat.PNG
            }
            targetBmp.compress(format, 95, outStream)
            return outStream.toByteArray()
        } finally {
            original.recycle()
            outBmp?.recycle()
        }
    }

    fun applyPreviewHue(original: Bitmap, hueShiftDegrees: Int): Bitmap {
        val outBmp = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (hueShiftDegrees != 0) {
            paint.colorFilter = ColorMatrixColorFilter(createHueRotateMatrix(hueShiftDegrees.toFloat()))
        }
        canvas.drawBitmap(original, 0f, 0f, paint)
        return outBmp
    }

    private fun createHueRotateMatrix(degrees: Float): ColorMatrix {
        val rad = Math.toRadians(degrees.toDouble())
        val cosVal = kotlin.math.cos(rad).toFloat()
        val sinVal = kotlin.math.sin(rad).toFloat()
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f
        return ColorMatrix(
            floatArrayOf(
                lumR + cosVal * (1 - lumR) + sinVal * (-lumR),
                lumG + cosVal * (-lumG) + sinVal * (-lumG),
                lumB + cosVal * (-lumB) + sinVal * (1 - lumB), 0f, 0f,
                lumR + cosVal * (-lumR) + sinVal * 0.143f,
                lumG + cosVal * (1 - lumG) + sinVal * 0.140f,
                lumB + cosVal * (-lumB) + sinVal * (-0.283f), 0f, 0f,
                lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)),
                lumG + cosVal * (-lumG) + sinVal * lumG,
                lumB + cosVal * (1 - lumB) + sinVal * lumB, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
}

/**
 * FMSRepackager & FMSSignatureManager & FMSCloneEngine:
 * Repackages an APK cleanly with Zip-Slip protection, AXML manifest transformation,
 * PNG icon patching, uncompressed resources.arsc preservation, and real JAR/PKCS#7 v1 local signing.
 */
object FMSRepackager {
    private val DEX_ENTRY_REGEX = Regex("""^classes(\d*)\.dex$""")
    const val DHEDRY_DEX_ASSET_PATH = "dhedry/classes_dhedry.dex"
    const val DHEDRY_APK_AUDIO_ENTRY = "assets/dhedry/zclone_boot_audio.mp3"
    const val DHEDRY_DEPENDENCY_GRAPH_ENTRY = "assets/dhedry/dependency_graph.json"
    const val DHEDRY_REPRODUCIBLE_BUILD_ENTRY = "assets/dhedry/reproducible_build.json"
    private const val FIXED_DOS_TIME_UTC_MS = 347198400000L // 1981-01-01T12:00:00Z (safely >= 1980 in all timezones)

    internal fun applyCanonicalTimestamp(entry: ZipEntry) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            entry.timeLocal = java.time.LocalDateTime.of(1981, 1, 1, 0, 0, 0)
        } else {
            entry.time = FIXED_DOS_TIME_UTC_MS
        }
    }

    fun buildClonedApk(
        request: CloneBuildRequest,
        privateFilesDir: File,
        outputFile: File,
        context: Context? = null,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<CloneBuildOutput> {
        val outcome = runCatching {
            if (shouldCancel()) throw kotlinx.coroutines.CancellationException("Build APK dibatalkan sebelum dimulai.")
            require(request.ownerConfirmedModificationRights) {
                "Konfirmasi kepemilikan atau hak modifikasi APK wajib disetujui sebelum membangun klon."
            }
            val sourceFile = File(request.sourceApkPath)
            require(sourceFile.exists() && sourceFile.isFile) { "File APK sumber tidak tersedia." }
            if (request.changePackageName) {
                require(FMSIntegrityEngine.isValidPackageName(request.targetPackage)) {
                    "Format target package name tidak valid: ${request.targetPackage}"
                }
            }

            onProgress(0.15f, "Membaca struktur APK & AndroidManifest.xml...")
            outputFile.parentFile?.mkdirs()
            val tempUnsigned = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}_unsigned.apk")
            val entryDigests = if (request.reproducibleBuild) {
                java.util.TreeMap<String, String>()
            } else {
                linkedMapOf()
            }
            var patchedIcons = 0
            var modifiedManifest = false
            val warnings = mutableListOf<String>()
            val identity = FMSKeyManager.getOrCreateSoftwareSigningIdentity(
                privateFilesDir = privateFilesDir,
                context = context
            )
            var dhedrySplashInjected = false
            var dhedryDexEntryName: String? = null
            var dhedryTargetActivity: String? = null
            var dependencyGraphEmbedded = false

            try {
                val depGraphJsonBytes: ByteArray? = if (request.embedDependencyGraph) {
                    FmsDependencyGraphAnalyzer.analyze(sourceFile)
                        .map { FmsDependencyGraphAnalyzer.toJsonString(it).toByteArray(Charsets.UTF_8) }
                        .getOrNull()
                } else null
                if (shouldCancel()) throw kotlinx.coroutines.CancellationException("Build APK dibatalkan oleh pengguna.")

                ZipFile(sourceFile).use { zip ->
                    val manifestEntry = zip.getEntry("AndroidManifest.xml")
                        ?: error("APK tidak valid: AndroidManifest.xml tidak ditemukan.")
                    val parsedManifestSummary = FMSManifestEngine.inspectManifest(
                        zip.getInputStream(manifestEntry).use { it.readBytes() }
                    ).getOrThrow()
                    require(
                        parsedManifestSummary.splitName.isNullOrBlank() &&
                            parsedManifestSummary.configForSplit.isNullOrBlank() &&
                            !parsedManifestSummary.isFeatureSplit &&
                            !parsedManifestSummary.hasSplitsRequiredMeta
                    ) {
                        "Split APK / dynamic feature tidak didukung untuk kloning APK tunggal non-root."
                    }
                    require(parsedManifestSummary.sharedUserId.isNullOrBlank()) {
                        "APK menggunakan android:sharedUserId (${parsedManifestSummary.sharedUserId}) yang tidak dapat dipisahkan tanpa signature asli."
                    }

                    if (parsedManifestSummary.referenceAuthoritiesCount > 0 && request.changePackageName) {
                        warnings.add("${parsedManifestSummary.referenceAuthoritiesCount} ContentProvider memakai referensi @string/... pada resources.arsc sehingga berisiko bentrok jika dipasang berdampingan dengan app asli.")
                    }

                    val targetLauncher = parsedManifestSummary.launcherActivityName
                    val minSdkTooLowForMultiDex = parsedManifestSummary.minSdkVersion != null && parsedManifestSummary.minSdkVersion < 21
                    if (request.injectDhedryLoading && !request.hideFromLauncher) {
                        if (targetLauncher.isNullOrBlank()) {
                            warnings.add("Aktivitas MAIN/LAUNCHER tidak ditemukan pada manifest; injeksi Dhedry Animated Loading dilewati.")
                        } else if (minSdkTooLowForMultiDex) {
                            warnings.add("minSdkVersion=${parsedManifestSummary.minSdkVersion} (<21): injeksi DEX Dhedry dilewati otomatis untuk mencegah startup force close pada perangkat Dalvik.")
                        }
                    }
                    val wantsDhedrySplash = request.injectDhedryLoading && !request.hideFromLauncher && !minSdkTooLowForMultiDex
                    val dexPayload = if (wantsDhedrySplash && !targetLauncher.isNullOrBlank()) {
                        request.dhedryDexBytes ?: context?.let { loadAssetBytesSafe(it, DHEDRY_DEX_ASSET_PATH) }
                    } else null
                    val audioPayload = if (wantsDhedrySplash && !targetLauncher.isNullOrBlank()) {
                        request.dhedryAudioMp3Bytes ?: context?.let { loadRawAudioBytesSafe(it) }
                    } else null

                    if (wantsDhedrySplash && !targetLauncher.isNullOrBlank() && (dexPayload == null || audioPayload == null)) {
                        warnings.add("Aset Dhedry Animated Loading (DEX/MP3) tidak tersedia pada konteks build; injeksi splash dilewati.")
                    }

                    val shouldInjectDhedry = wantsDhedrySplash &&
                        !targetLauncher.isNullOrBlank() &&
                        dexPayload != null &&
                        audioPayload != null

                    var sourceHasResArsc = false

                    ZipOutputStream(tempUnsigned.outputStream().buffered()).use { zos ->
                        val entries = zip.entries()
                        val rawEntries = mutableListOf<ZipEntry>()
                        while (entries.hasMoreElements()) {
                            rawEntries.add(entries.nextElement())
                        }
                        val allEntries = if (request.reproducibleBuild) {
                            rawEntries.sortedBy { it.name }
                        } else {
                            rawEntries
                        }
                        require(allEntries.any { it.name.endsWith(".dex") }) {
                            "APK tidak memuat classes.dex (kemungkinan resource-only / split config)."
                        }

                        val existingDexNumbers = allEntries.mapNotNull { e ->
                            DEX_ENTRY_REGEX.matchEntire(e.name)?.let { match ->
                                val suffix = match.groupValues[1]
                                if (suffix.isEmpty()) 1 else suffix.toIntOrNull()
                            }
                        }.toSet()
                        var nextDexIndex = 2
                        while (nextDexIndex in existingDexNumbers) {
                            nextDexIndex++
                        }
                        val chosenDexName = "classes$nextDexIndex.dex"

                        val total = allEntries.size.coerceAtLeast(1)
                        val ioBuffer = ByteArray(64 * 1024)
                        val streamMd = MessageDigest.getInstance("SHA-256")
                        allEntries.forEachIndexed { idx, entry ->
                            if (shouldCancel()) {
                                throw kotlinx.coroutines.CancellationException("Build APK dibatalkan oleh pengguna.")
                            }
                            val name = entry.name
                            // Zip-Slip path safety validation
                            require(!name.contains("..") && !name.startsWith("/")) {
                                "Entri ZIP tidak aman ditolak: $name"
                            }
                            // Strip existing signature artifacts so re-signing is clean
                            if (name.equals("META-INF/MANIFEST.MF", ignoreCase = true) ||
                                (name.startsWith("META-INF/", ignoreCase = true) && (
                                    name.endsWith(".SF", ignoreCase = true) ||
                                        name.endsWith(".RSA", ignoreCase = true) ||
                                        name.endsWith(".DSA", ignoreCase = true) ||
                                        name.endsWith(".EC", ignoreCase = true)
                                    ))
                            ) {
                                return@forEachIndexed
                            }
                            if (entry.isDirectory) return@forEachIndexed
                            if (shouldInjectDhedry && name == DHEDRY_APK_AUDIO_ENTRY) {
                                return@forEachIndexed
                            }
                            if (depGraphJsonBytes != null && name == DHEDRY_DEPENDENCY_GRAPH_ENTRY) {
                                return@forEachIndexed
                            }
                            if (request.reproducibleBuild && name == DHEDRY_REPRODUCIBLE_BUILD_ENTRY) {
                                return@forEachIndexed
                            }
                            if (name == "resources.arsc") {
                                sourceHasResArsc = true
                            }

                            val isManifest = name == "AndroidManifest.xml"
                            val isPatchableIcon = request.iconHueShift != 0 && isPatchableLauncherIconEntry(name)

                            if (isManifest || isPatchableIcon) {
                                var payload = zip.getInputStream(entry).use { it.readBytes() }
                                if (isManifest) {
                                    onProgress(0.35f, "Memodifikasi binary AndroidManifest.xml...")
                                    val mutated = FMSManifestEngine.transformManifest(
                                        payload,
                                        FMSManifestEngine.AxmlMutationOptions(
                                            oldPackageName = request.sourcePackage,
                                            newPackageName = if (request.changePackageName) request.targetPackage else null,
                                            newApplicationLabel = request.cloneName,
                                            strippedPermissions = request.strippedPermissions,
                                            hideFromLauncher = request.hideFromLauncher,
                                            rewriteAuthorities = request.rewriteProviderAuthorities,
                                            disableDebuggable = request.disableDebuggable,
                                            injectDhedrySplash = shouldInjectDhedry,
                                            dhedryTargetActivity = targetLauncher
                                        )
                                    )
                                    if (mutated.isSuccess) {
                                        payload = mutated.getOrThrow()
                                        modifiedManifest = true
                                    } else {
                                        error("Gagal memodifikasi AndroidManifest.xml: ${mutated.exceptionOrNull()?.message}")
                                    }
                                } else {
                                    val isWebp = name.endsWith(".webp", ignoreCase = true)
                                    val useBadge = !name.contains("adaptive", ignoreCase = true) &&
                                        !name.contains("background", ignoreCase = true)
                                    payload = runCatching {
                                        FMSIconEngine.transformIconRaster(
                                            bytes = payload,
                                            isWebp = isWebp,
                                            hueShiftDegrees = request.iconHueShift,
                                            roundedBadge = useBadge
                                        )
                                    }.onSuccess { patchedIcons++ }.getOrDefault(payload)
                                }

                                val outEntry = ZipEntry(name)
                                if (request.reproducibleBuild) applyCanonicalTimestamp(outEntry)
                                if (entry.method == ZipEntry.STORED) {
                                    outEntry.method = ZipEntry.STORED
                                    outEntry.size = payload.size.toLong()
                                    outEntry.compressedSize = payload.size.toLong()
                                    outEntry.crc = CRC32().apply { update(payload) }.value
                                }
                                zos.putNextEntry(outEntry)
                                zos.write(payload)
                                zos.closeEntry()
                                entryDigests[name] = sha256Base64(payload)
                            } else {
                                // Stream unmodified entries (DEX, native .so, resources.arsc, assets, .9.png) without loading whole entry into RAM
                                val needsStored = name == "resources.arsc" ||
                                    entry.method == ZipEntry.STORED ||
                                    (name.endsWith(".so", ignoreCase = true) && parsedManifestSummary.extractNativeLibs == false)
                                var knownSize = entry.size
                                var knownCrc = entry.crc
                                if (needsStored && (knownSize < 0L || knownCrc < 0L)) {
                                    val crc32 = CRC32()
                                    var counted = 0L
                                    zip.getInputStream(entry).use { inp ->
                                        while (true) {
                                            val read = inp.read(ioBuffer)
                                            if (read <= 0) break
                                            crc32.update(ioBuffer, 0, read)
                                            counted += read
                                        }
                                    }
                                    knownSize = counted
                                    knownCrc = crc32.value
                                }

                                val outEntry = ZipEntry(name)
                                if (request.reproducibleBuild) applyCanonicalTimestamp(outEntry)
                                if (needsStored) {
                                    outEntry.method = ZipEntry.STORED
                                    outEntry.size = knownSize
                                    outEntry.compressedSize = knownSize
                                    outEntry.crc = knownCrc
                                }
                                zos.putNextEntry(outEntry)
                                streamMd.reset()
                                zip.getInputStream(entry).use { inp ->
                                    while (true) {
                                        val read = inp.read(ioBuffer)
                                        if (read <= 0) break
                                        zos.write(ioBuffer, 0, read)
                                        streamMd.update(ioBuffer, 0, read)
                                    }
                                }
                                zos.closeEntry()
                                entryDigests[name] = Base64.encodeToString(streamMd.digest(), Base64.NO_WRAP)
                            }

                            if (idx % 20 == 0) {
                                onProgress(0.4f + 0.35f * (idx.toFloat() / total), "Mengemas ulang entri APK (${idx + 1}/$total)...")
                            }
                        }

                        if (shouldInjectDhedry) {
                            val safeDex = dexPayload
                            val safeAudio = audioPayload
                            onProgress(0.76f, "Menyuntikkan runtime Dhedry Animated Loading ($chosenDexName + audio)...")
                            val dexEntry = ZipEntry(chosenDexName)
                            if (request.reproducibleBuild) applyCanonicalTimestamp(dexEntry)
                            zos.putNextEntry(dexEntry)
                            zos.write(safeDex)
                            zos.closeEntry()
                            entryDigests[chosenDexName] = sha256Base64(safeDex)

                            val audioEntry = ZipEntry(DHEDRY_APK_AUDIO_ENTRY).apply {
                                if (request.reproducibleBuild) applyCanonicalTimestamp(this)
                                method = ZipEntry.STORED
                                size = safeAudio.size.toLong()
                                compressedSize = safeAudio.size.toLong()
                                crc = CRC32().apply { update(safeAudio) }.value
                            }
                            zos.putNextEntry(audioEntry)
                            zos.write(safeAudio)
                            zos.closeEntry()
                            entryDigests[DHEDRY_APK_AUDIO_ENTRY] = sha256Base64(safeAudio)

                            dhedrySplashInjected = true
                            dhedryDexEntryName = chosenDexName
                            dhedryTargetActivity = FMSManifestEngine.resolveComponentClassName(
                                request.sourcePackage,
                                targetLauncher
                            )
                        }

                        if (depGraphJsonBytes != null) {
                            val graphEntry = ZipEntry(DHEDRY_DEPENDENCY_GRAPH_ENTRY)
                            if (request.reproducibleBuild) applyCanonicalTimestamp(graphEntry)
                            zos.putNextEntry(graphEntry)
                            zos.write(depGraphJsonBytes)
                            zos.closeEntry()
                            entryDigests[DHEDRY_DEPENDENCY_GRAPH_ENTRY] = sha256Base64(depGraphJsonBytes)
                            dependencyGraphEmbedded = true
                        }

                        if (request.reproducibleBuild) {
                            val reproJsonBytes = org.json.JSONObject().apply {
                                put("product", "ZCloner PRO")
                                put("engine", "FMS Engine Core V1")
                                put("copyright", "Copyright FMS Clone Engine V1")
                                put("schema", "fms.reproducible_build.v1")
                                put("canonicalMode", true)
                                put("fixedTimestampUtc", "1981-01-01T00:00:00Z")
                                put("entryOrder", "LEXICOGRAPHICAL_ASC")
                                put("sourceApkSha256", sha256FileHex(sourceFile))
                                put("targetPackage", if (request.changePackageName) request.targetPackage else request.sourcePackage)
                                put("cloneName", request.cloneName)
                                put("iconHueShift", request.iconHueShift)
                                put("dhedrySplashInjected", dhedrySplashInjected)
                                put("signingCertSha256", identity.sha256Fingerprint)
                            }.toString(2).toByteArray(Charsets.UTF_8)

                            val reproEntry = ZipEntry(DHEDRY_REPRODUCIBLE_BUILD_ENTRY)
                            applyCanonicalTimestamp(reproEntry)
                            zos.putNextEntry(reproEntry)
                            zos.write(reproJsonBytes)
                            zos.closeEntry()
                            entryDigests[DHEDRY_REPRODUCIBLE_BUILD_ENTRY] = sha256Base64(reproJsonBytes)
                        }
                    }

                    if (shouldCancel()) throw kotlinx.coroutines.CancellationException("Build APK dibatalkan sebelum penandatanganan.")

                    onProgress(0.82f, "Menandatangani APK (v1 + v2) secara deterministik...")
                    val tempSigned = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}_signed_tmp.apk")
                    val validationReport: ApkValidationReport
                    try {
                        FMSSignatureManager.signJarApk(
                            unsignedApk = tempUnsigned,
                            signedApk = tempSigned,
                            entryDigests = entryDigests,
                            identity = identity,
                            deterministicMode = request.reproducibleBuild,
                            shouldCancel = shouldCancel
                        )
                        if (shouldCancel()) throw kotlinx.coroutines.CancellationException("Build APK dibatalkan sebelum finalisasi.")
                        onProgress(0.92f, "Menjalankan validasi 7-tahap APK sebelum rilis...")
                        val expectedPkg = if (request.changePackageName) request.targetPackage else request.sourcePackage
                        validationReport = FMSCloneValidator.validateBuiltCloneApk(
                            apkFile = tempSigned,
                            expectedTargetPackage = expectedPkg,
                            sourceManifest = parsedManifestSummary,
                            sourceHasResArsc = sourceHasResArsc,
                            request = request,
                            expectDhedryInjected = dhedrySplashInjected,
                            expectedDhedryDexName = dhedryDexEntryName
                        )
                        if (!validationReport.passedAutomatedChecks) {
                            val failureInfo = FMSCloneValidator.buildFailureInfoFromReport(validationReport)
                            throw FmsCloneValidationException(failureInfo, validationReport)
                        }
                        if (shouldCancel()) throw kotlinx.coroutines.CancellationException("Build APK dibatalkan sebelum penyimpanan akhir.")
                        outputFile.parentFile?.mkdirs()
                        if (outputFile.exists()) outputFile.delete()
                        if (!tempSigned.renameTo(outputFile)) {
                            tempSigned.copyTo(outputFile, overwrite = true)
                            tempSigned.delete()
                        }
                    } catch (t: Throwable) {
                        tempSigned.delete()
                        throw t
                    }

                    val outSha256 = sha256FileHex(outputFile)

                    return@runCatching CloneBuildOutput(
                        outputApkPath = outputFile.absolutePath,
                        targetPackage = if (request.changePackageName) request.targetPackage else request.sourcePackage,
                        cloneName = request.cloneName,
                        outputSizeBytes = outputFile.length(),
                        outputSha256 = outSha256,
                        signingSha256 = identity.sha256Fingerprint,
                        modifiedManifest = modifiedManifest,
                        patchedIconsCount = patchedIcons,
                        strippedPermissionsCount = request.strippedPermissions.size,
                        signatureVerified = true,
                        warnings = warnings,
                        dhedrySplashInjected = dhedrySplashInjected,
                        dhedryDexEntryName = dhedryDexEntryName,
                        dhedryTargetActivity = dhedryTargetActivity,
                        reproducibleBuildApplied = request.reproducibleBuild,
                        dependencyGraphEmbedded = dependencyGraphEmbedded,
                        validationReport = validationReport
                    )
                }
            } finally {
                tempUnsigned.delete()
            }
        }
        outcome.exceptionOrNull()?.let { err ->
            if (err is kotlinx.coroutines.CancellationException) throw err
        }
        return outcome
    }

    fun isPatchableLauncherIconEntry(entryName: String): Boolean {
        if (entryName.endsWith(".9.png", ignoreCase = true)) return false
        val isRaster = entryName.endsWith(".png", ignoreCase = true) || entryName.endsWith(".webp", ignoreCase = true)
        if (!isRaster) return false
        if (entryName.contains("ic_launcher_background", ignoreCase = true)) return false
        return entryName.contains("ic_launcher", ignoreCase = true) ||
            entryName.endsWith("/icon.png", ignoreCase = true) ||
            entryName.endsWith("/icon.webp", ignoreCase = true)
    }

    private fun loadAssetBytesSafe(context: Context, assetPath: String): ByteArray? = runCatching {
        context.assets.open(assetPath).use { it.readBytes() }
    }.getOrNull()

    private fun loadRawAudioBytesSafe(context: Context): ByteArray? = runCatching {
        val resId = context.resources.getIdentifier("dhedry_zclone_boot", "raw", context.packageName)
        if (resId == 0) return@runCatching null
        context.resources.openRawResource(resId).use { it.readBytes() }
    }.getOrNull()

    private fun sha256Base64(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun sha256FileHex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        file.inputStream().buffered().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02X".format(it) }
    }
}

private class CountingOutputStream(private val delegate: java.io.OutputStream) : java.io.OutputStream() {
    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        delegate.write(b)
        bytesWritten += 1L
    }

    override fun write(b: ByteArray) {
        delegate.write(b)
        bytesWritten += b.size.toLong()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        bytesWritten += len.toLong()
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}

/**
 * FMSSignatureManager: Pure-Java JAR/APK v1 SHA-256 + PKCS#7 SignedData generator and verifier
 * with 4-byte / 4096-byte ZIP alignment for STORED APK entries (Android R+ compliant).
 */
object FMSSignatureManager {
    private fun checkCancel(shouldCancel: () -> Boolean, stage: String) {
        if (shouldCancel()) {
            throw kotlinx.coroutines.CancellationException("Penandatanganan APK dibatalkan saat $stage.")
        }
    }

    fun signJarApk(
        unsignedApk: File,
        signedApk: File,
        entryDigests: Map<String, String>,
        identity: LocalSigningIdentity,
        deterministicMode: Boolean = true,
        shouldCancel: () -> Boolean = { false }
    ) {
        checkCancel(shouldCancel, "inisialisasi v1")
        val orderedDigests = if (deterministicMode) {
            java.util.TreeMap(entryDigests)
        } else {
            entryDigests
        }

        val mainManifest = Manifest()
        mainManifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        mainManifest.mainAttributes[Attributes.Name("Created-By")] = "ZCloner PRO FMS Engine Core V1"
        val mainSectionBytes = ByteArrayOutputStream().also { mainManifest.write(it) }.toByteArray()
        val mainSectionDigest = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(mainSectionBytes),
            Base64.NO_WRAP
        )

        val fullManifestOut = ByteArrayOutputStream()
        fullManifestOut.write(mainSectionBytes)
        val sfSections = StringBuilder()
        val mdSha256 = MessageDigest.getInstance("SHA-256")
        for ((name, digest) in orderedDigests) {
            checkCancel(shouldCancel, "pembuatan MANIFEST.MF")
            val sectionManifest = Manifest()
            sectionManifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            val attrs = Attributes()
            attrs[Attributes.Name("SHA-256-Digest")] = digest
            sectionManifest.entries[name] = attrs
            // Render exact per-entry section as written into MANIFEST.MF
            val tmp = ByteArrayOutputStream().also { sectionManifest.write(it) }.toByteArray()
            val tmpText = String(tmp, Charsets.UTF_8)
            val marker = "\r\n\r\n"
            val startIdx = tmpText.indexOf(marker)
            val entrySectionBytes = if (startIdx >= 0) {
                tmp.copyOfRange(startIdx + marker.length, tmp.size)
            } else {
                tmp
            }
            fullManifestOut.write(entrySectionBytes)

            val sectionDigest = Base64.encodeToString(mdSha256.digest(entrySectionBytes), Base64.NO_WRAP)
            mdSha256.reset()
            appendJarLine(sfSections, "Name: $name")
            appendJarLine(sfSections, "SHA-256-Digest: $sectionDigest")
            sfSections.append("\r\n")
        }

        checkCancel(shouldCancel, "pembuatan PKCS#7 v1")
        val manifestBytes = fullManifestOut.toByteArray()
        val manifestDigest = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(manifestBytes),
            Base64.NO_WRAP
        )

        val sfBuilder = StringBuilder()
        appendJarLine(sfBuilder, "Signature-Version: 1.0")
        appendJarLine(sfBuilder, "Created-By: ZCloner PRO FMS Engine Core V1")
        appendJarLine(sfBuilder, "SHA-256-Digest-Manifest: $manifestDigest")
        appendJarLine(sfBuilder, "SHA-256-Digest-Manifest-Main-Attributes: $mainSectionDigest")
        sfBuilder.append("\r\n")
        sfBuilder.append(sfSections)
        val sfBytes = sfBuilder.toString().toByteArray(Charsets.UTF_8)

        val pkcs7Bytes = buildPkcs7SignedData(sfBytes, identity)

        val v1Temp = File(signedApk.parentFile, "${signedApk.nameWithoutExtension}_v1_only.apk")
        try {
            ZipFile(unsignedApk).use { srcZip ->
                val countingOut = CountingOutputStream(v1Temp.outputStream().buffered())
                ZipOutputStream(countingOut).use { zos ->
                    fun putMeta(metaName: String, bytes: ByteArray) {
                        val me = ZipEntry(metaName)
                        if (deterministicMode) FMSRepackager.applyCanonicalTimestamp(me)
                        zos.putNextEntry(me)
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                    putMeta("META-INF/MANIFEST.MF", manifestBytes)
                    putMeta("META-INF/ZCLONE.SF", sfBytes)
                    putMeta("META-INF/ZCLONE.RSA", pkcs7Bytes)

                    val copyBuf = ByteArray(64 * 1024)
                    val rawList = mutableListOf<ZipEntry>()
                    val entries = srcZip.entries()
                    while (entries.hasMoreElements()) {
                        rawList.add(entries.nextElement())
                    }
                    val sortedEntries = if (deterministicMode) rawList.sortedBy { it.name } else rawList
                    for (e in sortedEntries) {
                        checkCancel(shouldCancel, "pengemasan tanda tangan v1")
                        val copy = ZipEntry(e.name)
                        if (deterministicMode) FMSRepackager.applyCanonicalTimestamp(copy)
                        if (e.method == ZipEntry.STORED) {
                            var knownSize = e.size
                            var knownCrc = e.crc
                            if (knownSize < 0L || knownCrc < 0L) {
                                val crc32 = CRC32()
                                var counted = 0L
                                srcZip.getInputStream(e).use { inp ->
                                    while (true) {
                                        checkCancel(shouldCancel, "perhitungan CRC v1")
                                        val read = inp.read(copyBuf)
                                        if (read <= 0) break
                                        crc32.update(copyBuf, 0, read)
                                        counted += read
                                    }
                                }
                                knownSize = counted
                                knownCrc = crc32.value
                            }
                            zos.flush()
                            val align = if (e.name.endsWith(".so", ignoreCase = true)) 16384L else 4L
                            val nameLen = e.name.toByteArray(Charsets.UTF_8).size.toLong()
                            val dataOffsetWithoutExtra = countingOut.bytesWritten + 30L + nameLen
                            val rem = dataOffsetWithoutExtra % align
                            val pad = if (rem == 0L) 0 else (align - rem).toInt()
                            if (pad > 0) {
                                copy.extra = ByteArray(pad)
                            }
                            copy.method = ZipEntry.STORED
                            copy.size = knownSize
                            copy.compressedSize = knownSize
                            copy.crc = knownCrc
                        }
                        zos.putNextEntry(copy)
                        srcZip.getInputStream(e).use { inp ->
                            while (true) {
                                checkCancel(shouldCancel, "penulisan entri v1")
                                val read = inp.read(copyBuf)
                                if (read <= 0) break
                                zos.write(copyBuf, 0, read)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
            checkCancel(shouldCancel, "sebelum tanda tangan v2")
            appendApkSignatureSchemeV2(v1Temp, signedApk, identity, shouldCancel)
        } catch (t: Throwable) {
            signedApk.delete()
            throw t
        } finally {
            v1Temp.delete()
        }
    }

    private fun appendApkSignatureSchemeV2(
        v1Apk: File,
        outApk: File,
        identity: LocalSigningIdentity,
        shouldCancel: () -> Boolean = { false }
    ) {
        try {
            java.io.RandomAccessFile(v1Apk, "r").use { raf ->
                checkCancel(shouldCancel, "inisialisasi APK v2")
                val fileLen = raf.length()
                require(fileLen >= 22L) { "File APK terlalu kecil untuk tanda tangan v2." }
                val tailLen = minOf(fileLen, 65558L).toInt()
                val tailStart = fileLen - tailLen.toLong()
                val tail = ByteArray(tailLen)
                raf.seek(tailStart)
                raf.readFully(tail)

                var eocdLocal = -1
                for (i in tail.size - 22 downTo 0) {
                    if (tail[i] == 0x50.toByte() &&
                        tail[i + 1] == 0x4B.toByte() &&
                        tail[i + 2] == 0x05.toByte() &&
                        tail[i + 3] == 0x06.toByte()
                    ) {
                        val commentLen = (tail[i + 20].toInt() and 0xFF) or ((tail[i + 21].toInt() and 0xFF) shl 8)
                        if (i + 22 + commentLen == tail.size) {
                            eocdLocal = i
                            break
                        }
                    }
                }
                require(eocdLocal >= 0) { "EOCD ZIP tidak ditemukan saat menambahkan tanda tangan v2." }
                val eocdOffset = tailStart + eocdLocal.toLong()
                require(eocdOffset > 0L) { "Offset EOCD ZIP tidak valid." }

                val cdOffset = ((tail[eocdLocal + 16].toLong() and 0xFFL) or
                    ((tail[eocdLocal + 17].toLong() and 0xFFL) shl 8) or
                    ((tail[eocdLocal + 18].toLong() and 0xFFL) shl 16) or
                    ((tail[eocdLocal + 19].toLong() and 0xFFL) shl 24))
                require(cdOffset in 1L until eocdOffset) { "Offset Central Directory ZIP tidak valid." }

                // Compute 1 MB chunked SHA-256 digest (0x0103) by streaming without loading entire APK into heap
                val chunkBuf = ByteArray(1024 * 1024)
                val chunkDigests = mutableListOf<ByteArray>()
                val md = MessageDigest.getInstance("SHA-256")
                fun hashSectionInChunks(start: Long, end: Long) {
                    raf.seek(start)
                    var pos = start
                    while (pos < end) {
                        checkCancel(shouldCancel, "hashing chunk APK v2")
                        val chunkLen = minOf(chunkBuf.size.toLong(), end - pos).toInt()
                        raf.readFully(chunkBuf, 0, chunkLen)
                        md.reset()
                        md.update(0xA5.toByte())
                        md.update(leInt(chunkLen))
                        md.update(chunkBuf, 0, chunkLen)
                        chunkDigests.add(md.digest())
                        pos += chunkLen.toLong()
                    }
                }
                hashSectionInChunks(0L, cdOffset)
                hashSectionInChunks(cdOffset, eocdOffset)
                hashSectionInChunks(eocdOffset, fileLen)

                checkCancel(shouldCancel, "penandatanganan blok APK v2")
                val topMd = MessageDigest.getInstance("SHA-256")
                topMd.update(0x5A.toByte())
                topMd.update(leInt(chunkDigests.size))
                chunkDigests.forEach { topMd.update(it) }
                val contentDigest = topMd.digest()

                val sigAlgId = 0x0103 // RSASSA-PKCS1-v1_5 with SHA-256
                val digestsSeq = v2LenPrefixed(
                    v2LenPrefixed(leInt(sigAlgId) + v2LenPrefixed(contentDigest))
                )
                val certsSeq = v2LenPrefixed(
                    v2LenPrefixed((identity.certificate as X509Certificate).encoded)
                )
                val additionalAttrs = v2LenPrefixed(ByteArray(0))
                val signedData = digestsSeq + certsSeq + additionalAttrs

                val signatureBytes = Signature.getInstance("SHA256withRSA").apply {
                    initSign(identity.privateKey)
                    update(signedData)
                }.sign()
                val signaturesSeq = v2LenPrefixed(
                    v2LenPrefixed(leInt(sigAlgId) + v2LenPrefixed(signatureBytes))
                )
                val pubKeyBytes = identity.certificate.publicKey.encoded
                val signer = v2LenPrefixed(signedData) + signaturesSeq + v2LenPrefixed(pubKeyBytes)
                val v2SchemeValue = v2LenPrefixed(v2LenPrefixed(signer))

                val v2PairPayload = leInt(0x7109871A) + v2SchemeValue
                val v2Pair = leLong(v2PairPayload.size.toLong()) + v2PairPayload
                val paddingNeeded = run {
                    val baseTotal = 8 + v2Pair.size + 8 + 16
                    val rem = baseTotal % 4096
                    if (rem == 0) 0 else {
                        val p = 4096 - rem
                        if (p >= 12) p else (p + 4096)
                    }
                }
                val verityPadPair = if (paddingNeeded >= 12) {
                    val padDataLen = paddingNeeded - 12
                    leLong((4 + padDataLen).toLong()) + leInt(0x42726577) + ByteArray(padDataLen)
                } else {
                    ByteArray(0)
                }

                val blockSizeAfterHeader = v2Pair.size.toLong() + verityPadPair.size.toLong() + 8L + 16L
                val signingBlock = ByteArrayOutputStream().apply {
                    write(leLong(blockSizeAfterHeader))
                    write(v2Pair)
                    write(verityPadPair)
                    write(leLong(blockSizeAfterHeader))
                    write("APK Sig Block 42".toByteArray(Charsets.US_ASCII))
                }.toByteArray()

                val patchedEocd = tail.copyOfRange(eocdLocal, tail.size)
                val newCdOffset = cdOffset + signingBlock.size.toLong()
                require(newCdOffset <= 0xFFFFFFFFL) { "Ukuran APK melebihi batas format ZIP32 (4 GB)." }
                val cdOffLe = leInt((newCdOffset and 0xFFFFFFFFL).toInt())
                patchedEocd[16] = cdOffLe[0]
                patchedEocd[17] = cdOffLe[1]
                patchedEocd[18] = cdOffLe[2]
                patchedEocd[19] = cdOffLe[3]

                val streamBuf = ByteArray(64 * 1024)
                fun streamFileRange(start: Long, length: Long, out: java.io.OutputStream) {
                    raf.seek(start)
                    var remaining = length
                    while (remaining > 0L) {
                        checkCancel(shouldCancel, "streaming output APK v2")
                        val toRead = minOf(streamBuf.size.toLong(), remaining).toInt()
                        raf.readFully(streamBuf, 0, toRead)
                        out.write(streamBuf, 0, toRead)
                        remaining -= toRead.toLong()
                    }
                }

                outApk.outputStream().buffered().use { out ->
                    streamFileRange(0L, cdOffset, out)
                    out.write(signingBlock)
                    streamFileRange(cdOffset, eocdOffset - cdOffset, out)
                    out.write(patchedEocd)
                    out.flush()
                }
                checkCancel(shouldCancel, "finalisasi output APK v2")
            }
        } catch (t: Throwable) {
            outApk.delete()
            throw t
        }
    }

    private fun leInt(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 24) and 0xFF).toByte()
    )

    private fun leLong(v: Long): ByteArray = byteArrayOf(
        (v and 0xFFL).toByte(),
        ((v ushr 8) and 0xFFL).toByte(),
        ((v ushr 16) and 0xFFL).toByte(),
        ((v ushr 24) and 0xFFL).toByte(),
        ((v ushr 32) and 0xFFL).toByte(),
        ((v ushr 40) and 0xFFL).toByte(),
        ((v ushr 48) and 0xFFL).toByte(),
        ((v ushr 56) and 0xFFL).toByte()
    )

    private fun v2LenPrefixed(bytes: ByteArray): ByteArray = leInt(bytes.size) + bytes

    fun verifyApkContainsValidSignature(apkFile: File): Boolean = runCatching {
        ZipFile(apkFile).use { zip ->
            val mf = zip.getEntry("META-INF/MANIFEST.MF") ?: return false
            val sf = zip.getEntry("META-INF/ZCLONE.SF") ?: return false
            val rsa = zip.getEntry("META-INF/ZCLONE.RSA") ?: return false
            val mfBytes = zip.getInputStream(mf).use { it.readBytes() }
            val sfBytes = zip.getInputStream(sf).use { it.readBytes() }
            val rsaBytes = zip.getInputStream(rsa).use { it.readBytes() }
            if (mfBytes.isEmpty() || sfBytes.isEmpty() || rsaBytes.isEmpty()) return false

            val sfText = String(sfBytes, Charsets.UTF_8)
            val expectedManifestDigest = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(mfBytes),
                Base64.NO_WRAP
            )
            if (!sfText.contains("SHA-256-Digest-Manifest: $expectedManifestDigest")) return false

            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificates(rsaBytes.inputStream()).firstOrNull() as? X509Certificate
                ?: return false
            val rawSig = extractPkcs7SignatureOctets(rsaBytes) ?: return false
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(cert.publicKey)
            verifier.update(sfBytes)
            if (!verifier.verify(rawSig)) return false
        }
        hasValidApkSigBlockHeaderAndFooter(apkFile)
    }.getOrDefault(false)

    fun hasValidApkSigBlockHeaderAndFooter(apkFile: File): Boolean = runCatching {
        java.io.RandomAccessFile(apkFile, "r").use { raf ->
            val len = raf.length()
            if (len < 64L) return false
            val searchStart = maxOf(0L, len - 65558L)
            val tail = ByteArray((len - searchStart).toInt())
            raf.seek(searchStart)
            raf.readFully(tail)
            var eocdLocal = -1
            for (i in tail.size - 22 downTo 0) {
                if (tail[i] == 0x50.toByte() &&
                    tail[i + 1] == 0x4B.toByte() &&
                    tail[i + 2] == 0x05.toByte() &&
                    tail[i + 3] == 0x06.toByte()
                ) {
                    val commentLen = (tail[i + 20].toInt() and 0xFF) or ((tail[i + 21].toInt() and 0xFF) shl 8)
                    if (i + 22 + commentLen == tail.size) {
                        eocdLocal = i
                        break
                    }
                }
            }
            if (eocdLocal < 0) return false
            val cdOffset = ((tail[eocdLocal + 16].toLong() and 0xFFL) or
                ((tail[eocdLocal + 17].toLong() and 0xFFL) shl 8) or
                ((tail[eocdLocal + 18].toLong() and 0xFFL) shl 16) or
                ((tail[eocdLocal + 19].toLong() and 0xFFL) shl 24))
            if (cdOffset < 32L) return false
            val footer = ByteArray(24)
            raf.seek(cdOffset - 24L)
            raf.readFully(footer)
            val magic = String(footer, 8, 16, Charsets.US_ASCII)
            if (magic != "APK Sig Block 42") return false
            val bottomSize = java.nio.ByteBuffer.wrap(footer, 0, 8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .long
            val topPos = cdOffset - bottomSize - 8L
            if (topPos < 0L) return false
            val topBuf = ByteArray(8)
            raf.seek(topPos)
            raf.readFully(topBuf)
            val topSize = java.nio.ByteBuffer.wrap(topBuf)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .long
            topSize == bottomSize
        }
    }.getOrDefault(false)

    fun isEntryDataAligned(apkFile: File, entryName: String, alignment: Int = 4): Boolean = runCatching {
        ZipFile(apkFile).use { zip ->
            val target = zip.getEntry(entryName) ?: return@use true
            if (target.method != ZipEntry.STORED) return@use true

            java.io.RandomAccessFile(apkFile, "r").use { file ->
                var pos = 0L
                val len = file.length()
                val header = ByteArray(30)
                val descSig = ByteArray(4)
                while (pos + 30 <= len) {
                    file.seek(pos)
                    file.readFully(header)
                    val sig = (header[0].toInt() and 0xFF) or
                        ((header[1].toInt() and 0xFF) shl 8) or
                        ((header[2].toInt() and 0xFF) shl 16) or
                        ((header[3].toInt() and 0xFF) shl 24)
                    if (sig != 0x04034B50) break
                    val flags = (header[6].toInt() and 0xFF) or ((header[7].toInt() and 0xFF) shl 8)
                    val compMethod = (header[8].toInt() and 0xFF) or ((header[9].toInt() and 0xFF) shl 8)
                    val hdrCompSize = (header[18].toLong() and 0xFFL) or
                        ((header[19].toLong() and 0xFFL) shl 8) or
                        ((header[20].toLong() and 0xFFL) shl 16) or
                        ((header[21].toLong() and 0xFFL) shl 24)
                    val nameLen = (header[26].toInt() and 0xFF) or ((header[27].toInt() and 0xFF) shl 8)
                    val extraLen = (header[28].toInt() and 0xFF) or ((header[29].toInt() and 0xFF) shl 8)
                    val nameBytes = ByteArray(nameLen)
                    file.readFully(nameBytes)
                    val name = String(nameBytes, Charsets.UTF_8)
                    val dataOffset = pos + 30L + nameLen + extraLen
                    if (name == entryName) {
                        return@use compMethod != ZipEntry.STORED || (dataOffset % alignment == 0L)
                    }
                    val hasDataDesc = (flags and 0x08) != 0
                    val actualCompSize = if (hasDataDesc) {
                        zip.getEntry(name)?.compressedSize?.takeIf { it >= 0L } ?: hdrCompSize
                    } else {
                        hdrCompSize
                    }
                    var nextPos = dataOffset + actualCompSize
                    if (hasDataDesc && nextPos + 4 <= len) {
                        file.seek(nextPos)
                        file.readFully(descSig)
                        val ds = (descSig[0].toInt() and 0xFF) or
                            ((descSig[1].toInt() and 0xFF) shl 8) or
                            ((descSig[2].toInt() and 0xFF) shl 16) or
                            ((descSig[3].toInt() and 0xFF) shl 24)
                        nextPos += if (ds == 0x08074B50) 16L else 12L
                    }
                    pos = nextPos
                }
                false
            }
        }
    }.getOrDefault(false)

    private fun extractPkcs7SignatureOctets(derBytes: ByteArray): ByteArray? = runCatching {
        // Walk SignedData -> signerInfos SET -> SignerInfo SEQ -> last primitive OCTET STRING (0x04)
        var offset = 0
        fun readTagLen(pos: Int): Triple<Int, Int, Int> {
            val tag = derBytes[pos].toInt() and 0xFF
            val b = derBytes[pos + 1].toInt() and 0xFF
            return if ((b and 0x80) == 0) {
                Triple(tag, b, pos + 2)
            } else {
                val count = b and 0x7F
                var length = 0
                for (i in 0 until count) {
                    length = (length shl 8) or (derBytes[pos + 2 + i].toInt() and 0xFF)
                }
                Triple(tag, length, pos + 2 + count)
            }
        }
        // Scan for the SignerInfo trailing OCTET STRING inside our DER SignedData
        val (_, rootLen, rootStart) = readTagLen(0)
        val rootEnd = rootStart + rootLen
        var lastOctetString: ByteArray? = null
        // Dive into outer SEQ -> [0] -> SignedData SEQ
        var p = rootStart
        val (oidTag, oidLen, oidStart) = readTagLen(p)
        if (oidTag != 0x06) return null
        p = oidStart + oidLen
        val (ctxTag, _, ctxStart) = readTagLen(p)
        if (ctxTag != 0xA0) return null
        val (sdTag, sdLen, sdStart) = readTagLen(ctxStart)
        if (sdTag != 0x30) return null
        val sdEnd = (sdStart + sdLen).coerceAtMost(rootEnd)
        p = sdStart
        while (p < sdEnd) {
            val (t, l, s) = readTagLen(p)
            if (t == 0x31 && s + l >= sdEnd) {
                // signerInfos SET at tail of SignedData
                val (siTag, siLen, siStart) = readTagLen(s)
                if (siTag == 0x30) {
                    var q = siStart
                    val siEnd = siStart + siLen
                    while (q < siEnd) {
                        val (ft, fl, fs) = readTagLen(q)
                        if (ft == 0x04) {
                            lastOctetString = derBytes.copyOfRange(fs, fs + fl)
                        }
                        q = fs + fl
                    }
                }
            }
            p = s + l
        }
        lastOctetString
    }.getOrNull()

    private fun appendJarLine(sb: StringBuilder, line: String) {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 70) {
            sb.append(line).append("\r\n")
            return
        }
        var offset = 0
        var first = true
        while (offset < line.length) {
            val chunkLen = if (first) 70 else 69
            val end = (offset + chunkLen).coerceAtMost(line.length)
            if (!first) sb.append(' ')
            sb.append(line, offset, end).append("\r\n")
            offset = end
            first = false
        }
    }

    private fun buildPkcs7SignedData(contentBytes: ByteArray, identity: LocalSigningIdentity): ByteArray {
        val x509 = identity.certificate as X509Certificate
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(identity.privateKey)
            update(contentBytes)
        }.sign()

        val oidData = derOid(intArrayOf(1, 2, 840, 113549, 1, 7, 1))
        val oidSignedData = derOid(intArrayOf(1, 2, 840, 113549, 1, 7, 2))
        val oidSha256 = derSeq(derOid(intArrayOf(2, 16, 840, 1, 101, 3, 4, 2, 1)), byteArrayOf(0x05, 0x00))
        val oidRsa = derSeq(derOid(intArrayOf(1, 2, 840, 113549, 1, 1, 1)), byteArrayOf(0x05, 0x00))

        val issuerAndSerial = derSeq(
            x509.issuerX500Principal.encoded,
            derInteger(x509.serialNumber)
        )

        val signerInfo = derSeq(
            derInteger(java.math.BigInteger.ONE),
            issuerAndSerial,
            oidSha256,
            oidRsa,
            derOctetString(sig)
        )

        val signedData = derSeq(
            derInteger(java.math.BigInteger.ONE), // version
            derSet(oidSha256), // digestAlgorithms
            derSeq(oidData), // contentInfo (detached)
            derTag(0xA0, x509.encoded), // certificates [0] IMPLICIT
            derSet(signerInfo) // signerInfos
        )

        return derSeq(
            oidSignedData,
            derTag(0xA0, signedData)
        )
    }

    private fun derTag(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + encodeLen(content.size) + content

    private fun derSeq(vararg parts: ByteArray): ByteArray = derTag(0x30, concat(*parts))
    private fun derSet(vararg parts: ByteArray): ByteArray = derTag(0x31, concat(*parts))
    private fun derOctetString(bytes: ByteArray): ByteArray = derTag(0x04, bytes)
    private fun derInteger(v: java.math.BigInteger): ByteArray = derTag(0x02, v.toByteArray())

    private fun derOid(parts: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(parts[0] * 40 + parts[1])
        for (i in 2 until parts.size) {
            var v = parts[i]
            val stack = ArrayDeque<Int>()
            stack.addFirst(v and 0x7F)
            v = v ushr 7
            while (v > 0) {
                stack.addFirst((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            stack.forEach { out.write(it) }
        }
        return derTag(0x06, out.toByteArray())
    }

    private fun encodeLen(len: Int): ByteArray {
        if (len < 0x80) return byteArrayOf(len.toByte())
        val list = mutableListOf<Byte>()
        var rem = len
        while (rem > 0) {
            list.add(0, (rem and 0xFF).toByte())
            rem = rem ushr 8
        }
        return byteArrayOf((0x80 or list.size).toByte()) + list.toByteArray()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        arrays.forEach { out.write(it) }
        return out.toByteArray()
    }
}
