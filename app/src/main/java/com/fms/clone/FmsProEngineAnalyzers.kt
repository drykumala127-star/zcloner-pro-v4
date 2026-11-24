package com.fms.clone

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * ElfDependencyParser:
 * Pure-Kotlin ELF32 / ELF64 parser extracting DT_NEEDED shared library dependencies from APK .so files.
 */
object ElfDependencyParser {
    private const val ELF_MAGIC = 0x464C457F // "\u007FELF" little-endian
    private const val ELFCLASS32 = 1
    private const val ELFCLASS64 = 2
    private const val SHT_DYNAMIC = 6L
    private const val DT_NULL = 0L
    private const val DT_NEEDED = 1L
    private const val DT_STRTAB = 5L
    private const val DT_STRSZ = 10L
    private const val MAX_ELF_PARSE_BYTES = 16 * 1024 * 1024

    data class ElfParseResult(
        val isElf: Boolean,
        val isElf64: Boolean,
        val neededLibraries: List<String>
    )

    fun parseNeededLibraries(input: InputStream): ElfParseResult {
        val bytes = readBoundedBytes(input, MAX_ELF_PARSE_BYTES) ?: return ElfParseResult(false, false, emptyList())
        return parseNeededLibraries(bytes)
    }

    fun parseNeededLibraries(bytes: ByteArray): ElfParseResult = runCatching {
        if (bytes.size < 64) return@runCatching ElfParseResult(false, false, emptyList())
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.getInt(0) != ELF_MAGIC) {
            return@runCatching ElfParseResult(false, false, emptyList())
        }
        val elfClass = bytes[4].toInt() and 0xFF
        val dataEnc = bytes[5].toInt() and 0xFF
        if (dataEnc != 1) {
            // Only little-endian ELF supported safely
            return@runCatching ElfParseResult(true, elfClass == ELFCLASS64, emptyList())
        }

        val is64 = elfClass == ELFCLASS64
        if (!is64 && elfClass != ELFCLASS32) {
            return@runCatching ElfParseResult(false, false, emptyList())
        }

        val shoff: Long
        val shentsize: Int
        val shnum: Int
        if (is64) {
            shoff = buf.getLong(40)
            shentsize = buf.getShort(58).toInt() and 0xFFFF
            shnum = buf.getShort(60).toInt() and 0xFFFF
        } else {
            shoff = buf.getInt(32).toLong() and 0xFFFFFFFFL
            shentsize = buf.getShort(46).toInt() and 0xFFFF
            shnum = buf.getShort(48).toInt() and 0xFFFF
        }

        if (shoff <= 0L || shentsize < (if (is64) 64 else 40) || shnum <= 0 || shnum > 4096) {
            return@runCatching ElfParseResult(true, is64, emptyList())
        }
        val shTableEnd = shoff + shentsize.toLong() * shnum.toLong()
        if (shTableEnd > bytes.size.toLong()) {
            return@runCatching ElfParseResult(true, is64, emptyList())
        }

        var dynOffset = -1L
        var dynSize = 0L
        var dynLink = -1
        data class SecInfo(val type: Long, val addr: Long, val offset: Long, val size: Long, val link: Int)
        val sections = ArrayList<SecInfo>(shnum)
        for (i in 0 until shnum) {
            val base = (shoff + i.toLong() * shentsize.toLong()).toInt()
            val sType: Long
            val sAddr: Long
            val sOff: Long
            val sSize: Long
            val sLink: Int
            if (is64) {
                sType = buf.getInt(base + 4).toLong() and 0xFFFFFFFFL
                sAddr = buf.getLong(base + 16)
                sOff = buf.getLong(base + 24)
                sSize = buf.getLong(base + 32)
                sLink = buf.getInt(base + 40)
            } else {
                sType = buf.getInt(base + 4).toLong() and 0xFFFFFFFFL
                sAddr = buf.getInt(base + 12).toLong() and 0xFFFFFFFFL
                sOff = buf.getInt(base + 16).toLong() and 0xFFFFFFFFL
                sSize = buf.getInt(base + 20).toLong() and 0xFFFFFFFFL
                sLink = buf.getInt(base + 24)
            }
            sections.add(SecInfo(sType, sAddr, sOff, sSize, sLink))
            if (sType == SHT_DYNAMIC && sOff > 0L && sOff + sSize <= bytes.size.toLong()) {
                dynOffset = sOff
                dynSize = sSize
                dynLink = sLink
            }
        }

        if (dynOffset < 0L || dynSize <= 0L) {
            return@runCatching ElfParseResult(true, is64, emptyList())
        }

        val entryStride = if (is64) 16 else 8
        val entryCount = (dynSize / entryStride.toLong()).toInt().coerceAtMost(4096)
        val neededOffsets = mutableListOf<Long>()
        var strTabAddr = -1L
        var strTabSize = -1L

        for (i in 0 until entryCount) {
            val pos = (dynOffset + i.toLong() * entryStride.toLong()).toInt()
            val tag = if (is64) buf.getLong(pos) else buf.getInt(pos).toLong()
            val valOrPtr = if (is64) buf.getLong(pos + 8) else (buf.getInt(pos + 4).toLong() and 0xFFFFFFFFL)
            if (tag == DT_NULL) break
            when (tag) {
                DT_NEEDED -> neededOffsets.add(valOrPtr)
                DT_STRTAB -> strTabAddr = valOrPtr
                DT_STRSZ -> strTabSize = valOrPtr
            }
        }

        val strSec = if (dynLink in sections.indices) {
            sections[dynLink]
        } else {
            sections.firstOrNull { sec ->
                strTabAddr >= 0L && strTabAddr >= sec.addr && strTabAddr < sec.addr + sec.size
            }
        }
        if (strSec == null || strSec.offset < 0L || strSec.offset + strSec.size > bytes.size.toLong()) {
            return@runCatching ElfParseResult(true, is64, emptyList())
        }
        val baseStrOff = if (strTabAddr >= strSec.addr && strTabAddr < strSec.addr + strSec.size) {
            strSec.offset + (strTabAddr - strSec.addr)
        } else {
            strSec.offset
        }
        val maxStrEnd = minOf(
            bytes.size.toLong(),
            if (strTabSize > 0L) baseStrOff + strTabSize else strSec.offset + strSec.size
        ).toInt()

        val libs = neededOffsets.mapNotNull { strOff ->
            val start = (baseStrOff + strOff).toInt()
            if (start < 0 || start >= maxStrEnd) return@mapNotNull null
            var end = start
            while (end < maxStrEnd && bytes[end] != 0.toByte()) {
                end++
            }
            if (end > start) String(bytes, start, end - start, Charsets.UTF_8) else null
        }.distinct()

        ElfParseResult(true, is64, libs)
    }.getOrDefault(ElfParseResult(false, false, emptyList()))

    private fun readBoundedBytes(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val r = input.read(buf)
            if (r <= 0) break
            total += r
            if (total > limit) return null
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }
}

/**
 * FmsDependencyGraphAnalyzer:
 * Static APK component, <queries>, multidex, uses-library, and ELF DT_NEEDED graph extractor.
 */
object FmsDependencyGraphAnalyzer {
    fun analyze(apkFile: File): Result<ApkDependencyGraph> = runCatching {
        require(apkFile.exists() && apkFile.isFile) { "APK file not found: ${apkFile.name}" }
        val sha256 = sha256FileHex(apkFile)
        ZipFile(apkFile).use { zip ->
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
                ?: error("AndroidManifest.xml missing.")
            val manifestBytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
            val manifestSummary = FMSManifestEngine.inspectManifest(manifestBytes).getOrThrow()
            val depSummary = FMSManifestEngine.inspectDependencyComponents(manifestBytes).getOrThrow()

            val dexFiles = mutableListOf<String>()
            val nativeLibs = mutableListOf<NativeLibDependencyNode>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val name = e.name
                if (name.endsWith(".dex", ignoreCase = true) && !name.contains("/")) {
                    dexFiles.add(name)
                } else if (name.startsWith("lib/") && name.endsWith(".so", ignoreCase = true)) {
                    val parts = name.split("/")
                    val abi = parts.getOrNull(1) ?: "unknown"
                    val fileName = parts.lastOrNull() ?: name
                    val elf = zip.getInputStream(e).use { ElfDependencyParser.parseNeededLibraries(it) }
                    nativeLibs.add(
                        NativeLibDependencyNode(
                            abi = abi,
                            path = name,
                            fileName = fileName,
                            neededLibraries = elf.neededLibraries,
                            isElf64 = elf.isElf64
                        )
                    )
                }
            }

            ApkDependencyGraph(
                packageName = manifestSummary.packageName,
                versionCode = manifestSummary.versionCode?.toLong() ?: 1L,
                dexFiles = dexFiles.sorted(),
                components = depSummary.components.sortedWith(compareBy({ it.kind }, { it.name })),
                queriedPackages = depSummary.queriedPackages.distinct().sorted(),
                queriedActions = depSummary.queriedActions.distinct().sorted(),
                providerAuthorities = manifestSummary.authorityStrings.distinct().sorted(),
                requiredUsesLibraries = manifestSummary.requiredUsesLibraries.distinct().sorted(),
                nativeLibs = nativeLibs.sortedWith(compareBy({ it.abi }, { it.path })),
                sha256Source = sha256
            )
        }
    }

    fun toJsonString(graph: ApkDependencyGraph): String {
        val compsArr = JSONArray()
        graph.components.forEach { c ->
            compsArr.put(
                JSONObject().apply {
                    put("kind", c.kind)
                    put("name", c.name)
                    put("exported", c.exported)
                    if (c.authorityOrPerm != null) put("meta", c.authorityOrPerm)
                }
            )
        }
        val nativeArr = JSONArray()
        graph.nativeLibs.forEach { n ->
            nativeArr.put(
                JSONObject().apply {
                    put("abi", n.abi)
                    put("path", n.path)
                    put("fileName", n.fileName)
                    put("isElf64", n.isElf64)
                    put("dtNeeded", JSONArray(n.neededLibraries))
                }
            )
        }
        return JSONObject().apply {
            put("engine", "FMS Engine Core V1")
            put("schema", "fms.dependency_graph.v1")
            put("packageName", graph.packageName)
            put("versionCode", graph.versionCode)
            put("sourceSha256", graph.sha256Source)
            put("dexFiles", JSONArray(graph.dexFiles))
            put("components", compsArr)
            put("queriedPackages", JSONArray(graph.queriedPackages))
            put("queriedActions", JSONArray(graph.queriedActions))
            put("providerAuthorities", JSONArray(graph.providerAuthorities))
            put("requiredUsesLibraries", JSONArray(graph.requiredUsesLibraries))
            put("nativeLibraries", nativeArr)
        }.toString(2)
    }

    private fun sha256FileHex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        file.inputStream().buffered().use { inp ->
            while (true) {
                val r = inp.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02X".format(it) }
    }
}

/**
 * FmsBinaryVersionDiff:
 * Compares two actual APK binaries (permissions, exported components, SDK, DEX files, native .so, PRO modules).
 */
object FmsBinaryVersionDiff {
    fun compareApks(apkA: File, apkB: File): Result<BinaryVersionDiffReport> = runCatching {
        val graphA = FmsDependencyGraphAnalyzer.analyze(apkA).getOrThrow()
        val graphB = FmsDependencyGraphAnalyzer.analyze(apkB).getOrThrow()
        val manifestA = readManifestSummary(apkA)
        val manifestB = readManifestSummary(apkB)
        val dhedryA = listDhedryEntries(apkA)
        val dhedryB = listDhedryEntries(apkB)

        val permsA = manifestA.permissions.toSet()
        val permsB = manifestB.permissions.toSet()
        val compKeysA = graphA.components.map { "${it.kind}:${it.name}${if (it.exported) " [exported]" else ""}" }.toSet()
        val compKeysB = graphB.components.map { "${it.kind}:${it.name}${if (it.exported) " [exported]" else ""}" }.toSet()
        val dexA = graphA.dexFiles.toSet()
        val dexB = graphB.dexFiles.toSet()
        val soA = graphA.nativeLibs.map { it.path }.toSet()
        val soB = graphB.nativeLibs.map { it.path }.toSet()

        BinaryVersionDiffReport(
            apkAPath = apkA.absolutePath,
            apkBPath = apkB.absolutePath,
            packageA = manifestA.packageName,
            packageB = manifestB.packageName,
            versionCodeA = manifestA.versionCode?.toLong() ?: 1L,
            versionCodeB = manifestB.versionCode?.toLong() ?: 1L,
            versionNameA = manifestA.versionName ?: "1.0",
            versionNameB = manifestB.versionName ?: "1.0",
            minSdkA = manifestA.minSdkVersion,
            minSdkB = manifestB.minSdkVersion,
            targetSdkA = manifestA.targetSdkVersion,
            targetSdkB = manifestB.targetSdkVersion,
            sizeDeltaBytes = apkB.length() - apkA.length(),
            addedPermissions = (permsB - permsA).sorted(),
            removedPermissions = (permsA - permsB).sorted(),
            addedComponents = (compKeysB - compKeysA).sorted(),
            removedComponents = (compKeysA - compKeysB).sorted(),
            addedDexFiles = (dexB - dexA).sorted(),
            removedDexFiles = (dexA - dexB).sorted(),
            addedNativeLibs = (soB - soA).sorted(),
            removedNativeLibs = (soA - soB).sorted(),
            addedDhedryModules = (dhedryB - dhedryA).sorted(),
            removedDhedryModules = (dhedryA - dhedryB).sorted(),
            sha256A = graphA.sha256Source,
            sha256B = graphB.sha256Source
        )
    }

    fun toJsonString(diff: BinaryVersionDiffReport): String = JSONObject().apply {
        put("engine", "FMS Engine Core V1")
        put("schema", "fms.version_diff.v1")
        put("packageA", diff.packageA)
        put("packageB", diff.packageB)
        put("versionCodeA", diff.versionCodeA)
        put("versionCodeB", diff.versionCodeB)
        put("versionNameA", diff.versionNameA)
        put("versionNameB", diff.versionNameB)
        put("sizeDeltaBytes", diff.sizeDeltaBytes)
        put("sha256A", diff.sha256A)
        put("sha256B", diff.sha256B)
        put("addedPermissions", JSONArray(diff.addedPermissions))
        put("removedPermissions", JSONArray(diff.removedPermissions))
        put("addedComponents", JSONArray(diff.addedComponents))
        put("removedComponents", JSONArray(diff.removedComponents))
        put("addedDexFiles", JSONArray(diff.addedDexFiles))
        put("removedDexFiles", JSONArray(diff.removedDexFiles))
        put("addedNativeLibs", JSONArray(diff.addedNativeLibs))
        put("removedNativeLibs", JSONArray(diff.removedNativeLibs))
        put("addedDhedryModules", JSONArray(diff.addedDhedryModules))
        put("removedDhedryModules", JSONArray(diff.removedDhedryModules))
    }.toString(2)

    private fun readManifestSummary(apk: File): ManifestSummary = ZipFile(apk).use { zip ->
        val entry = zip.getEntry("AndroidManifest.xml") ?: error("AndroidManifest.xml missing in ${apk.name}")
        FMSManifestEngine.inspectManifest(zip.getInputStream(entry).use { it.readBytes() }).getOrThrow()
    }

    private fun listDhedryEntries(apk: File): Set<String> = ZipFile(apk).use { zip ->
        val res = mutableSetOf<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val name = entries.nextElement().name
            if (name.startsWith("assets/dhedry/")) res.add(name)
        }
        res
    }
}
