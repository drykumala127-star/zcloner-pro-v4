package com.fms.clone

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.IdentityHashMap

/**
 * FMSManifestEngine:
 * Pure-Kotlin Android Binary XML (AXML) parser, inspector, controlled mutator, and serializer.
 * Preserves existing StringPool resource-ID mappings while appending mutated strings safely.
 */
object FMSManifestEngine {
    private const val CHUNK_AXML_FILE = 0x0003
    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_RESOURCE_IDS = 0x0180
    private const val CHUNK_START_NAMESPACE = 0x0100
    private const val CHUNK_END_NAMESPACE = 0x0101
    private const val CHUNK_START_TAG = 0x0102
    private const val CHUNK_END_TAG = 0x0103
    private const val CHUNK_TEXT = 0x0104

    private const val FLAG_UTF8 = 0x00000100
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOL = 0x12

    private const val ATTR_RES_THEME = 0x01010000
    private const val ATTR_RES_NAME = 0x01010003
    private const val ATTR_RES_EXPORTED = 0x01010010
    private const val ATTR_RES_LAUNCH_MODE = 0x0101001d
    private const val ATTR_RES_VALUE = 0x01010024
    private const val STYLE_THEME_NO_TITLE_BAR_FULLSCREEN = 0x01030007

    private const val ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"
    const val DHEDRY_SPLASH_ACTIVITY = "com.dhedry.loading.DhedrySplashActivity"
    const val DHEDRY_META_TARGET_ACTIVITY = "dhedry.target_activity"

    private const val ATTR_LABEL = "label"
    private const val ATTR_NAME = "name"
    private const val ATTR_TARGET_ACTIVITY = "targetActivity"
    private const val ATTR_PACKAGE = "package"
    private const val ATTR_VERSION_CODE = "versionCode"
    private const val ATTR_VERSION_NAME = "versionName"
    private const val ATTR_MIN_SDK_VERSION = "minSdkVersion"
    private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
    private const val ATTR_SHARED_USER_ID = "sharedUserId"
    private const val ATTR_DEBUGGABLE = "debuggable"
    private const val ATTR_USES_CLEARTEXT = "usesCleartextTraffic"
    private const val ATTR_ALLOW_BACKUP = "allowBackup"
    private const val ATTR_EXTRACT_NATIVE_LIBS = "extractNativeLibs"
    private const val ATTR_APP_COMPONENT_FACTORY = "appComponentFactory"
    private const val ATTR_EXPORTED = "exported"
    private const val ATTR_AUTHORITIES = "authorities"
    private const val ATTR_PERMISSION = "permission"
    private const val ATTR_READ_PERMISSION = "readPermission"
    private const val ATTR_WRITE_PERMISSION = "writePermission"
    private const val ATTR_REQUIRED = "required"
    private const val ATTR_SPLIT = "split"
    private const val ATTR_CONFIG_FOR_SPLIT = "configForSplit"
    private const val ATTR_IS_FEATURE_SPLIT = "isFeatureSplit"
    private const val ATTR_REQUIRED_SPLIT_TYPES = "requiredSplitTypes"
    private const val ATTR_SPLIT_TYPES = "splitTypes"
    private const val ATTR_VALUE = "value"

    data class AxmlMutationOptions(
        val oldPackageName: String,
        val newPackageName: String?,
        val newApplicationLabel: String?,
        val strippedPermissions: Set<String> = emptySet(),
        val hideFromLauncher: Boolean = false,
        val rewriteAuthorities: Boolean = true,
        val disableDebuggable: Boolean = true,
        val injectDhedrySplash: Boolean = false,
        val dhedryTargetActivity: String? = null
    )

    private class StringPool(
        var flags: Int,
        val strings: MutableList<String>,
        val styleCount: Int,
        val rawStyleOffsets: ByteArray,
        val rawStylesData: ByteArray
    ) {
        val isUtf8: Boolean get() = (flags and FLAG_UTF8) != 0

        fun getOrNull(index: Int): String? = if (index in strings.indices) strings[index] else null

        fun markUnsorted() {
            // Clear SORTED_FLAG (0x00000001) whenever strings are mutated or appended
            flags = flags and 0x00000001.inv()
        }

        fun appendString(value: String): Int {
            val existing = strings.indexOf(value)
            if (existing >= 0) return existing
            markUnsorted()
            strings.add(value)
            return strings.lastIndex
        }

        fun serialize(): ByteArray {
            val encodedList = strings.map { s ->
                if (isUtf8) encodeUtf8Entry(s) else encodeUtf16Entry(s)
            }
            val headerSize = 28
            val offsetsSize = encodedList.size * 4
            val styleOffsetsSize = if (rawStyleOffsets.isNotEmpty()) rawStyleOffsets.size else styleCount * 4
            val stringDataStart = headerSize + offsetsSize + styleOffsetsSize

            val strBytes = ByteArrayOutputStream()
            val offsets = IntArray(encodedList.size)
            for (i in encodedList.indices) {
                offsets[i] = strBytes.size()
                strBytes.write(encodedList[i])
            }
            while (strBytes.size() % 4 != 0) {
                strBytes.write(0)
            }

            val stringsPayload = strBytes.toByteArray()
            val totalSize = stringDataStart + stringsPayload.size + rawStylesData.size
            val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(CHUNK_STRING_POOL.toShort())
            buf.putShort(headerSize.toShort())
            buf.putInt(totalSize)
            buf.putInt(encodedList.size)
            buf.putInt(styleCount)
            buf.putInt(flags)
            buf.putInt(stringDataStart)
            buf.putInt(if (styleCount > 0) stringDataStart + stringsPayload.size else 0)
            for (off in offsets) buf.putInt(off)
            if (rawStyleOffsets.isNotEmpty()) {
                buf.put(rawStyleOffsets)
            } else if (styleOffsetsSize > 0) {
                buf.put(ByteArray(styleOffsetsSize))
            }
            buf.put(stringsPayload)
            if (rawStylesData.isNotEmpty()) buf.put(rawStylesData)
            return buf.array()
        }

        private fun encodeUtf8Entry(s: String): ByteArray {
            val utf8 = s.toByteArray(Charsets.UTF_8)
            val out = ByteArrayOutputStream()
            writeLen8(out, s.length)
            writeLen8(out, utf8.size)
            out.write(utf8)
            out.write(0)
            return out.toByteArray()
        }

        private fun writeLen8(out: ByteArrayOutputStream, len: Int) {
            if (len < 0x80) {
                out.write(len)
            } else {
                out.write(((len ushr 8) and 0x7F) or 0x80)
                out.write(len and 0xFF)
            }
        }

        private fun encodeUtf16Entry(s: String): ByteArray {
            val out = ByteArrayOutputStream()
            if (s.length < 0x8000) {
                out.write(s.length and 0xFF)
                out.write((s.length ushr 8) and 0xFF)
            } else {
                val hi = ((s.length ushr 16) and 0x7FFF) or 0x8000
                val lo = s.length and 0xFFFF
                out.write(hi and 0xFF)
                out.write((hi ushr 8) and 0xFF)
                out.write(lo and 0xFF)
                out.write((lo ushr 8) and 0xFF)
            }
            val utf16 = s.toByteArray(Charsets.UTF_16LE)
            out.write(utf16)
            out.write(0)
            out.write(0)
            return out.toByteArray()
        }
    }

    private data class AxmlAttr(
        var nsIdx: Int,
        var nameIdx: Int,
        var rawValueIdx: Int,
        var size: Short,
        var res0: Byte,
        var dataType: Byte,
        var data: Int
    )

    private sealed class XmlChunk {
        data class NamespaceChunk(
            val type: Int,
            val headerSize: Short,
            val lineNumber: Int,
            val commentIdx: Int,
            var prefixIdx: Int,
            var uriIdx: Int
        ) : XmlChunk()
        data class TextChunk(
            val headerSize: Short,
            val lineNumber: Int,
            val commentIdx: Int,
            var nameIdx: Int,
            val extraBytes: ByteArray
        ) : XmlChunk()
        data class RawChunk(val type: Int, val rawBytes: ByteArray) : XmlChunk()
        data class StartTag(
            val headerSize: Short,
            val lineNumber: Int,
            var commentIdx: Int,
            var nsIdx: Int,
            var nameIdx: Int,
            val attrStart: Short,
            val attrSize: Short,
            val idIndex: Short,
            val classIndex: Short,
            val styleIndex: Short,
            val attrs: MutableList<AxmlAttr>
        ) : XmlChunk()
        data class EndTag(
            val headerSize: Short,
            val lineNumber: Int,
            var commentIdx: Int,
            var nsIdx: Int,
            var nameIdx: Int
        ) : XmlChunk()
    }

    fun resolveComponentClassName(packageName: String, rawName: String?): String? {
        val clean = rawName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            packageName.isBlank() -> clean
            clean.startsWith(".") -> "$packageName$clean"
            !clean.contains(".") -> "$packageName.$clean"
            else -> clean
        }
    }

    fun validateBinaryAxml(axmlBytes: ByteArray): Result<ManifestSummary> = runCatching {
        val parsed = parse(axmlBytes)
        var startTags = 0
        var endTags = 0
        var hasManifestRoot = false
        for (chunk in parsed.chunks) {
            when (chunk) {
                is XmlChunk.StartTag -> {
                    startTags++
                    if (parsed.stringPool.getOrNull(chunk.nameIdx) == "manifest") {
                        hasManifestRoot = true
                    }
                }
                is XmlChunk.EndTag -> endTags++
                else -> Unit
            }
        }
        require(hasManifestRoot) { "Binary AXML tidak memiliki tag <manifest>." }
        require(startTags > 0 && startTags == endTags) {
            "Struktur tag AXML tidak seimbang (start=$startTags, end=$endTags) — berisiko Parse Error."
        }
        inspectManifest(axmlBytes).getOrThrow()
    }

    fun inspectManifest(axmlBytes: ByteArray): Result<ManifestSummary> = runCatching {
        val parsed = parse(axmlBytes)
        var packageName = ""
        var appLabel: String? = null
        var versionCode: Int? = null
        var versionName: String? = null
        var minSdkVersion: Int? = null
        var targetSdkVersion: Int? = null
        var sharedUserId: String? = null
        var debuggable = false
        var cleartext = false
        var allowBackup = true
        var extractNativeLibs: Boolean? = null
        var usesAppComponentFactory: String? = null
        val permissions = mutableListOf<String>()
        val customPermissions = mutableListOf<String>()
        val requiredUsesLibraries = mutableListOf<String>()
        var exportedCount = 0
        var launcherCount = 0
        val authorities = mutableListOf<String>()
        var referenceAuthoritiesCount = 0
        var splitName: String? = null
        var configForSplit: String? = null
        var isFeatureSplit = false
        var hasSplitsRequiredMeta = false
        var usesPlayLicensing = false

        var currentComponentClass: String? = null
        var currentComponentEnabled = true
        var firstDisabledLauncherFallback: String? = null
        var queriesDepth = 0
        var inIntentFilter = false
        var filterHasMain = false
        var filterHasLauncher = false
        var launcherActivityName: String? = null

        for (chunk in parsed.chunks) {
            if (chunk is XmlChunk.EndTag) {
                val endName = parsed.stringPool.getOrNull(chunk.nameIdx)
                if (endName == "queries" && queriesDepth > 0) {
                    queriesDepth--
                } else if (endName == "intent-filter") {
                    if (queriesDepth == 0 && inIntentFilter && filterHasMain && filterHasLauncher && currentComponentClass != null) {
                        if (currentComponentEnabled && launcherActivityName == null) {
                            launcherActivityName = currentComponentClass
                        } else if (!currentComponentEnabled && firstDisabledLauncherFallback == null) {
                            firstDisabledLauncherFallback = currentComponentClass
                        }
                    }
                    inIntentFilter = false
                    filterHasMain = false
                    filterHasLauncher = false
                } else if (endName == "activity" || endName == "activity-alias") {
                    currentComponentClass = null
                    currentComponentEnabled = true
                }
                continue
            }
            if (chunk !is XmlChunk.StartTag) continue
            val tagName = parsed.stringPool.getOrNull(chunk.nameIdx) ?: continue
            fun attrStr(name: String): String? {
                val a = chunk.attrs.firstOrNull { parsed.stringPool.getOrNull(it.nameIdx) == name } ?: return null
                return readChunkStringAttr(parsed.stringPool, a)
            }
            fun attrBool(name: String): Boolean? {
                val a = chunk.attrs.firstOrNull { parsed.stringPool.getOrNull(it.nameIdx) == name } ?: return null
                return if (a.dataType.toInt() == TYPE_INT_BOOL) a.data != 0 else null
            }
            fun attrInt(name: String): Int? {
                val a = chunk.attrs.firstOrNull { parsed.stringPool.getOrNull(it.nameIdx) == name } ?: return null
                return when (a.dataType.toInt()) {
                    TYPE_INT_DEC -> a.data
                    TYPE_STRING -> parsed.stringPool.getOrNull(a.data)?.toIntOrNull()
                    else -> null
                }
            }

            when (tagName) {
                "manifest" -> {
                    packageName = attrStr(ATTR_PACKAGE).orEmpty()
                    versionCode = attrInt(ATTR_VERSION_CODE)
                    versionName = attrStr(ATTR_VERSION_NAME)
                    sharedUserId = attrStr(ATTR_SHARED_USER_ID)
                    splitName = attrStr(ATTR_SPLIT)?.takeIf { it.isNotBlank() }
                    configForSplit = attrStr(ATTR_CONFIG_FOR_SPLIT)?.takeIf { it.isNotBlank() }
                    isFeatureSplit = attrBool(ATTR_IS_FEATURE_SPLIT) ?: false
                    if (!attrStr(ATTR_REQUIRED_SPLIT_TYPES).isNullOrBlank() || !attrStr(ATTR_SPLIT_TYPES).isNullOrBlank()) {
                        hasSplitsRequiredMeta = true
                    }
                }
                "queries" -> {
                    queriesDepth++
                }
                "uses-sdk" -> {
                    minSdkVersion = attrInt(ATTR_MIN_SDK_VERSION) ?: minSdkVersion
                    targetSdkVersion = attrInt(ATTR_TARGET_SDK_VERSION) ?: targetSdkVersion
                }
                "application" -> {
                    appLabel = attrStr(ATTR_LABEL)
                    debuggable = attrBool(ATTR_DEBUGGABLE) ?: false
                    cleartext = attrBool(ATTR_USES_CLEARTEXT) ?: false
                    allowBackup = attrBool(ATTR_ALLOW_BACKUP) ?: true
                    extractNativeLibs = attrBool(ATTR_EXTRACT_NATIVE_LIBS)
                    usesAppComponentFactory = attrStr(ATTR_APP_COMPONENT_FACTORY)
                }
                "permission", "permission-group", "permission-tree" -> {
                    attrStr(ATTR_NAME)?.let { customPermissions.add(it) }
                }
                "uses-library" -> {
                    if (attrBool(ATTR_REQUIRED) != false) {
                        attrStr(ATTR_NAME)?.let { requiredUsesLibraries.add(it) }
                    }
                }
                "uses-permission", "uses-permission-sdk-23" -> {
                    attrStr(ATTR_NAME)?.let { perm ->
                        permissions.add(perm)
                        if (perm == "com.android.vending.CHECK_LICENSE") {
                            usesPlayLicensing = true
                        }
                    }
                }
                "meta-data" -> {
                    val metaName = attrStr(ATTR_NAME)
                    if (metaName == "com.android.vending.splits.required" && attrBool(ATTR_VALUE) == true) {
                        hasSplitsRequiredMeta = true
                    } else if (metaName == "com.android.dynamic.apk.fused.modules" && !attrStr(ATTR_VALUE).isNullOrBlank()) {
                        hasSplitsRequiredMeta = true
                    }
                }
                "activity" -> {
                    currentComponentClass = resolveComponentClassName(packageName, attrStr(ATTR_NAME))
                    currentComponentEnabled = attrBool("enabled") ?: true
                    if (attrBool(ATTR_EXPORTED) == true) exportedCount++
                }
                "activity-alias" -> {
                    val rawTarget = attrStr(ATTR_TARGET_ACTIVITY) ?: attrStr(ATTR_NAME)
                    currentComponentClass = resolveComponentClassName(packageName, rawTarget)
                    currentComponentEnabled = attrBool("enabled") ?: true
                    if (attrBool(ATTR_EXPORTED) == true) exportedCount++
                }
                "service", "receiver", "provider" -> {
                    if (attrBool(ATTR_EXPORTED) == true) exportedCount++
                    if (tagName == "provider") {
                        val authAttr = chunk.attrs.firstOrNull { parsed.stringPool.getOrNull(it.nameIdx) == ATTR_AUTHORITIES }
                        if (authAttr != null) {
                            if (authAttr.dataType.toInt() == TYPE_REFERENCE) {
                                referenceAuthoritiesCount++
                            } else {
                                readChunkStringAttr(parsed.stringPool, authAttr)?.let { authorities.add(it) }
                            }
                        }
                    }
                }
                "intent-filter" -> {
                    if (queriesDepth == 0) {
                        inIntentFilter = true
                        filterHasMain = false
                        filterHasLauncher = false
                    }
                }
                "action" -> {
                    if (queriesDepth == 0 && inIntentFilter && attrStr(ATTR_NAME) == "android.intent.action.MAIN") {
                        filterHasMain = true
                    }
                }
                "category" -> {
                    if (queriesDepth == 0 && attrStr(ATTR_NAME) == "android.intent.category.LAUNCHER") {
                        launcherCount++
                        if (inIntentFilter) filterHasLauncher = true
                    }
                }
            }
        }

        ManifestSummary(
            packageName = packageName,
            applicationLabel = appLabel,
            versionCode = versionCode,
            versionName = versionName,
            sharedUserId = sharedUserId,
            debuggable = debuggable,
            usesCleartextTraffic = cleartext,
            allowBackup = allowBackup,
            permissions = permissions.distinct(),
            exportedComponentsCount = exportedCount,
            launcherCategoriesCount = launcherCount,
            authorityStrings = authorities,
            splitName = splitName,
            configForSplit = configForSplit,
            isFeatureSplit = isFeatureSplit,
            hasSplitsRequiredMeta = hasSplitsRequiredMeta,
            usesPlayLicensing = usesPlayLicensing,
            minSdkVersion = minSdkVersion,
            launcherActivityName = launcherActivityName ?: firstDisabledLauncherFallback,
            targetSdkVersion = targetSdkVersion,
            extractNativeLibs = extractNativeLibs,
            customPermissions = customPermissions.distinct(),
            referenceAuthoritiesCount = referenceAuthoritiesCount,
            requiredUsesLibraries = requiredUsesLibraries.distinct(),
            usesAppComponentFactory = usesAppComponentFactory
        )
    }

    data class ManifestDependencySummary(
        val components: List<ComponentNode>,
        val queriedPackages: List<String>,
        val queriedActions: List<String>
    )

    fun inspectDependencyComponents(axmlBytes: ByteArray): Result<ManifestDependencySummary> = runCatching {
        val parsed = parse(axmlBytes)
        var packageName = ""
        val components = mutableListOf<ComponentNode>()
        val queriedPackages = mutableListOf<String>()
        val queriedActions = mutableListOf<String>()
        var queriesDepth = 0

        for (chunk in parsed.chunks) {
            if (chunk is XmlChunk.EndTag) {
                val endName = parsed.stringPool.getOrNull(chunk.nameIdx)
                if (endName == "queries" && queriesDepth > 0) queriesDepth--
                continue
            }
            if (chunk !is XmlChunk.StartTag) continue
            val tagName = parsed.stringPool.getOrNull(chunk.nameIdx) ?: continue
            fun attrStr(name: String): String? {
                val a = chunk.attrs.firstOrNull { parsed.stringPool.getOrNull(it.nameIdx) == name } ?: return null
                return readChunkStringAttr(parsed.stringPool, a)
            }
            fun attrBool(name: String): Boolean? {
                val a = chunk.attrs.firstOrNull { parsed.stringPool.getOrNull(it.nameIdx) == name } ?: return null
                return if (a.dataType.toInt() == TYPE_INT_BOOL) a.data != 0 else null
            }

            when (tagName) {
                "manifest" -> packageName = attrStr(ATTR_PACKAGE).orEmpty()
                "queries" -> queriesDepth++
                "package" -> if (queriesDepth > 0) {
                    attrStr(ATTR_NAME)?.takeIf { it.isNotBlank() }?.let { queriedPackages.add(it) }
                }
                "action" -> if (queriesDepth > 0) {
                    attrStr(ATTR_NAME)?.takeIf { it.isNotBlank() }?.let { queriedActions.add(it) }
                }
                "activity", "activity-alias", "service", "receiver", "provider" -> {
                    val rawName = if (tagName == "activity-alias") attrStr(ATTR_TARGET_ACTIVITY) ?: attrStr(ATTR_NAME) else attrStr(ATTR_NAME)
                    val resolved = resolveComponentClassName(packageName, rawName) ?: continue
                    val exported = attrBool(ATTR_EXPORTED) ?: false
                    val meta = if (tagName == "provider") attrStr(ATTR_AUTHORITIES) else attrStr(ATTR_PERMISSION)
                    components.add(
                        ComponentNode(
                            kind = tagName,
                            name = resolved,
                            exported = exported,
                            authorityOrPerm = meta
                        )
                    )
                }
            }
        }
        ManifestDependencySummary(
            components = components,
            queriedPackages = queriedPackages.distinct(),
            queriedActions = queriedActions.distinct()
        )
    }

    private fun readChunkStringAttr(pool: StringPool, attr: AxmlAttr): String? {
        return when {
            attr.dataType.toInt() == TYPE_STRING -> pool.getOrNull(attr.data)
            attr.rawValueIdx >= 0 -> pool.getOrNull(attr.rawValueIdx)
            else -> null
        }
    }

    fun transformManifest(axmlBytes: ByteArray, options: AxmlMutationOptions): Result<ByteArray> = runCatching {
        val parsed = parse(axmlBytes)
        val pool = parsed.stringPool
        val resourceIds = parsed.resourceIds
        val outputChunks = mutableListOf<XmlChunk>()
        var skipUntilEndTagName: String? = null
        var skipDepth = 0
        val shouldInjectDhedry = options.injectDhedrySplash &&
            !options.hideFromLauncher &&
            !options.dhedryTargetActivity.isNullOrBlank()
        var alreadyHasDhedryActivity = false
        val newPkgClean = options.newPackageName?.trim()?.takeIf { it.isNotBlank() && it != options.oldPackageName }
        var queriesDepth = 0
        var activityDepth = 0
        var inActivityIntentFilter = false

        for (chunk in parsed.chunks) {
            if (skipUntilEndTagName != null) {
                if (chunk is XmlChunk.StartTag && pool.getOrNull(chunk.nameIdx) == skipUntilEndTagName) {
                    skipDepth++
                } else if (chunk is XmlChunk.EndTag && pool.getOrNull(chunk.nameIdx) == skipUntilEndTagName) {
                    if (skipDepth == 0) {
                        skipUntilEndTagName = null
                        continue
                    } else {
                        skipDepth--
                    }
                }
                continue
            }

            if (chunk is XmlChunk.EndTag) {
                val endTagName = pool.getOrNull(chunk.nameIdx)
                if (endTagName == "queries" && queriesDepth > 0) {
                    queriesDepth--
                } else if (endTagName == "intent-filter") {
                    inActivityIntentFilter = false
                } else if ((endTagName == "activity" || endTagName == "activity-alias") && activityDepth > 0) {
                    activityDepth--
                }
                if (endTagName == "application" && shouldInjectDhedry && !alreadyHasDhedryActivity) {
                    val targetCls = resolveComponentClassName(options.oldPackageName, options.dhedryTargetActivity)!!
                    val distinctChunks = Collections.newSetFromMap(IdentityHashMap<XmlChunk, Boolean>()).apply {
                        addAll(outputChunks)
                        addAll(parsed.chunks)
                    }.toList()
                    outputChunks.addAll(buildDhedrySplashChunks(pool, resourceIds, distinctChunks, targetCls))
                }
                outputChunks.add(chunk)
                continue
            }

            if (chunk is XmlChunk.StartTag) {
                val tagName = pool.getOrNull(chunk.nameIdx)

                // Strip selected permissions cleanly
                if ((tagName == "uses-permission" || tagName == "uses-permission-sdk-23") && options.strippedPermissions.isNotEmpty()) {
                    val permAttr = chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_NAME }
                    val permVal = permAttr?.let { readChunkStringAttr(pool, it) }
                    if (permVal != null && options.strippedPermissions.contains(permVal)) {
                        skipUntilEndTagName = tagName
                        skipDepth = 0
                        continue
                    }
                }

                // Rewrite package-scoped permissions (incl. AndroidX DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) when package changes
                if (newPkgClean != null && options.oldPackageName.isNotBlank()) {
                    if (tagName in setOf("permission", "permission-group", "permission-tree", "uses-permission", "uses-permission-sdk-23")) {
                        chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_NAME }?.let { attr ->
                            val rawVal = readChunkStringAttr(pool, attr)
                            val updated = rawVal?.let { rewriteScopedIdentifier(it, options.oldPackageName, newPkgClean) }
                            if (updated != null && updated != rawVal) {
                                val idx = pool.appendString(updated)
                                attr.rawValueIdx = idx
                                attr.dataType = TYPE_STRING.toByte()
                                attr.data = idx
                            }
                        }
                    }
                    if (tagName in setOf("application", "activity", "activity-alias", "service", "receiver", "provider")) {
                        for (permAttrName in listOf(ATTR_PERMISSION, ATTR_READ_PERMISSION, ATTR_WRITE_PERMISSION)) {
                            chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == permAttrName }?.let { attr ->
                                val rawVal = readChunkStringAttr(pool, attr)
                                val updated = rawVal?.let { rewriteScopedIdentifier(it, options.oldPackageName, newPkgClean) }
                                if (updated != null && updated != rawVal) {
                                    val idx = pool.appendString(updated)
                                    attr.rawValueIdx = idx
                                    attr.dataType = TYPE_STRING.toByte()
                                    attr.data = idx
                                }
                            }
                        }
                    }
                }

                if (tagName == "queries") {
                    queriesDepth++
                } else if (tagName == "activity" || tagName == "activity-alias") {
                    activityDepth++
                } else if (tagName == "intent-filter" && queriesDepth == 0 && activityDepth > 0) {
                    inActivityIntentFilter = true
                }

                // Switch existing LAUNCHER category to DEFAULT only inside activity/activity-alias intent-filters (never inside <queries>)
                if ((options.hideFromLauncher || shouldInjectDhedry) &&
                    queriesDepth == 0 &&
                    activityDepth > 0 &&
                    inActivityIntentFilter &&
                    tagName == "category"
                ) {
                    val catAttr = chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_NAME }
                    val catVal = catAttr?.let { readChunkStringAttr(pool, it) }
                    if (catVal == "android.intent.category.LAUNCHER") {
                        val defaultIdx = pool.appendString("android.intent.category.DEFAULT")
                        catAttr.rawValueIdx = defaultIdx
                        catAttr.dataType = TYPE_STRING.toByte()
                        catAttr.data = defaultIdx
                    }
                }

                if (tagName == "activity") {
                    val actAttr = chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_NAME }
                    val actName = actAttr?.let { readChunkStringAttr(pool, it) }
                    if (actName == DHEDRY_SPLASH_ACTIVITY) {
                        alreadyHasDhedryActivity = true
                    }
                }

                // Normalize relative component class names (.MainActivity -> com.old.pkg.MainActivity) when package changes
                if (newPkgClean != null && options.oldPackageName.isNotBlank() &&
                    tagName in setOf("application", "activity", "activity-alias", "service", "receiver", "provider")
                ) {
                    chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_NAME }?.let { attr ->
                        val rawName = readChunkStringAttr(pool, attr)
                        if (rawName != null && (rawName.startsWith(".") || !rawName.contains("."))) {
                            val resolved = resolveComponentClassName(options.oldPackageName, rawName)
                            if (resolved != null && resolved != rawName) {
                                val idx = pool.appendString(resolved)
                                attr.rawValueIdx = idx
                                attr.dataType = TYPE_STRING.toByte()
                                attr.data = idx
                            }
                        }
                    }
                    if (tagName == "activity-alias") {
                        chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_TARGET_ACTIVITY }?.let { attr ->
                            val rawTarget = readChunkStringAttr(pool, attr)
                            if (rawTarget != null && (rawTarget.startsWith(".") || !rawTarget.contains("."))) {
                                val resolved = resolveComponentClassName(options.oldPackageName, rawTarget)
                                if (resolved != null && resolved != rawTarget) {
                                    val idx = pool.appendString(resolved)
                                    attr.rawValueIdx = idx
                                    attr.dataType = TYPE_STRING.toByte()
                                    attr.data = idx
                                }
                            }
                        }
                    }
                }

                when (tagName) {
                    "manifest" -> {
                        if (newPkgClean != null) {
                            chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_PACKAGE }?.let { attr ->
                                val idx = pool.appendString(newPkgClean)
                                attr.rawValueIdx = idx
                                attr.dataType = TYPE_STRING.toByte()
                                attr.data = idx
                            }
                        }
                    }
                    "application" -> {
                        val newLabel = options.newApplicationLabel?.trim()
                        if (!newLabel.isNullOrBlank()) {
                            chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_LABEL }?.let { attr ->
                                val idx = pool.appendString(newLabel)
                                attr.rawValueIdx = idx
                                attr.dataType = TYPE_STRING.toByte()
                                attr.data = idx
                            }
                        }
                        if (options.disableDebuggable) {
                            chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_DEBUGGABLE }?.let { attr ->
                                if (attr.dataType.toInt() == TYPE_INT_BOOL) {
                                    attr.data = 0
                                }
                            }
                        }
                    }
                    "provider" -> {
                        if (options.rewriteAuthorities && newPkgClean != null && options.oldPackageName.isNotBlank()) {
                            chunk.attrs.firstOrNull { pool.getOrNull(it.nameIdx) == ATTR_AUTHORITIES }?.let { attr ->
                                val oldAuth = readChunkStringAttr(pool, attr)
                                if (!oldAuth.isNullOrBlank()) {
                                    val updated = oldAuth.split(";").joinToString(";") { part ->
                                        val trimmed = part.trim()
                                        when {
                                            trimmed.isEmpty() -> trimmed
                                            trimmed.contains(options.oldPackageName) -> trimmed.replace(options.oldPackageName, newPkgClean)
                                            else -> "$newPkgClean.$trimmed"
                                        }
                                    }
                                    val idx = pool.appendString(updated)
                                    attr.rawValueIdx = idx
                                    attr.dataType = TYPE_STRING.toByte()
                                    attr.data = idx
                                }
                            }
                        }
                    }
                }
            }
            outputChunks.add(chunk)
        }

        val serialized = serialize(pool, resourceIds, outputChunks)
        val roundTripSummary = validateBinaryAxml(serialized).getOrThrow()
        val expectedPkg = newPkgClean ?: options.oldPackageName
        if (expectedPkg.isNotBlank()) {
            require(roundTripSummary.packageName == expectedPkg) {
                "Validasi AXML pasca-mutasi gagal: package=${roundTripSummary.packageName}, diharapkan=$expectedPkg"
            }
        }
        if (shouldInjectDhedry) {
            require(roundTripSummary.launcherActivityName == DHEDRY_SPLASH_ACTIVITY) {
                "Validasi AXML pasca-mutasi gagal: DhedrySplashActivity tidak aktif sebagai MAIN/LAUNCHER."
            }
        }
        serialized
    }

    private fun rewriteScopedIdentifier(value: String, oldPkg: String, newPkg: String): String {
        return when {
            value == oldPkg -> newPkg
            value.startsWith("$oldPkg.") -> newPkg + value.removePrefix(oldPkg)
            else -> value
        }
    }

    private fun ensureAndroidFrameworkAttr(
        pool: StringPool,
        resourceIds: MutableList<Int>,
        allChunks: List<XmlChunk>,
        attrName: String,
        resId: Int
    ): Int {
        val existingByResId = resourceIds.indexOf(resId)
        if (existingByResId >= 0) return existingByResId

        val insertIdx = resourceIds.size
        pool.markUnsorted()
        pool.strings.add(insertIdx, attrName)
        resourceIds.add(insertIdx, resId)
        shiftStringIndices(allChunks, insertIdx)
        return insertIdx
    }

    private fun shiftStringIndices(chunks: List<XmlChunk>, insertedAt: Int) {
        fun shift(v: Int): Int = if (v >= insertedAt) v + 1 else v
        for (c in chunks) {
            when (c) {
                is XmlChunk.NamespaceChunk -> {
                    c.prefixIdx = shift(c.prefixIdx)
                    c.uriIdx = shift(c.uriIdx)
                }
                is XmlChunk.TextChunk -> {
                    c.nameIdx = shift(c.nameIdx)
                }
                is XmlChunk.StartTag -> {
                    c.commentIdx = shift(c.commentIdx)
                    c.nsIdx = shift(c.nsIdx)
                    c.nameIdx = shift(c.nameIdx)
                    for (a in c.attrs) {
                        a.nsIdx = shift(a.nsIdx)
                        a.nameIdx = shift(a.nameIdx)
                        a.rawValueIdx = shift(a.rawValueIdx)
                        if (a.dataType.toInt() == TYPE_STRING) {
                            a.data = shift(a.data)
                        }
                    }
                }
                is XmlChunk.EndTag -> {
                    c.commentIdx = shift(c.commentIdx)
                    c.nsIdx = shift(c.nsIdx)
                    c.nameIdx = shift(c.nameIdx)
                }
                is XmlChunk.RawChunk -> Unit
            }
        }
    }

    private fun buildDhedrySplashChunks(
        pool: StringPool,
        resourceIds: MutableList<Int>,
        allChunks: List<XmlChunk>,
        targetActivityClass: String
    ): List<XmlChunk> {
        val themeAttrIdx = ensureAndroidFrameworkAttr(pool, resourceIds, allChunks, "theme", ATTR_RES_THEME)
        val nameAttrIdx = ensureAndroidFrameworkAttr(pool, resourceIds, allChunks, ATTR_NAME, ATTR_RES_NAME)
        val exportedAttrIdx = ensureAndroidFrameworkAttr(pool, resourceIds, allChunks, ATTR_EXPORTED, ATTR_RES_EXPORTED)
        val launchModeAttrIdx = ensureAndroidFrameworkAttr(pool, resourceIds, allChunks, "launchMode", ATTR_RES_LAUNCH_MODE)
        val valueAttrIdx = ensureAndroidFrameworkAttr(pool, resourceIds, allChunks, ATTR_VALUE, ATTR_RES_VALUE)

        val androidNsIdx = pool.strings.indexOf(ANDROID_NS_URI).let {
            if (it >= 0) it else pool.appendString(ANDROID_NS_URI)
        }

        val tagActivityIdx = pool.appendString("activity")
        val tagMetaDataIdx = pool.appendString("meta-data")
        val tagIntentFilterIdx = pool.appendString("intent-filter")
        val tagActionIdx = pool.appendString("action")
        val tagCategoryIdx = pool.appendString("category")

        val splashClassIdx = pool.appendString(DHEDRY_SPLASH_ACTIVITY)
        val metaTargetKeyIdx = pool.appendString(DHEDRY_META_TARGET_ACTIVITY)
        val metaTargetValIdx = pool.appendString(targetActivityClass)
        val actionMainIdx = pool.appendString("android.intent.action.MAIN")
        val categoryLauncherIdx = pool.appendString("android.intent.category.LAUNCHER")

        fun startTag(nameIdx: Int, attrs: MutableList<AxmlAttr>) = XmlChunk.StartTag(
            headerSize = 16,
            lineNumber = 1,
            commentIdx = -1,
            nsIdx = -1,
            nameIdx = nameIdx,
            attrStart = 20,
            attrSize = 20,
            idIndex = 0,
            classIndex = 0,
            styleIndex = 0,
            attrs = attrs
        )

        fun endTag(nameIdx: Int) = XmlChunk.EndTag(
            headerSize = 16,
            lineNumber = 1,
            commentIdx = -1,
            nsIdx = -1,
            nameIdx = nameIdx
        )

        fun strAttr(attrNameIdx: Int, strValIdx: Int) = AxmlAttr(
            nsIdx = androidNsIdx,
            nameIdx = attrNameIdx,
            rawValueIdx = strValIdx,
            size = 8,
            res0 = 0,
            dataType = TYPE_STRING.toByte(),
            data = strValIdx
        )

        val activityAttrs = mutableListOf(
            AxmlAttr(
                nsIdx = androidNsIdx,
                nameIdx = themeAttrIdx,
                rawValueIdx = -1,
                size = 8,
                res0 = 0,
                dataType = TYPE_REFERENCE.toByte(),
                data = STYLE_THEME_NO_TITLE_BAR_FULLSCREEN
            ),
            strAttr(nameAttrIdx, splashClassIdx),
            AxmlAttr(
                nsIdx = androidNsIdx,
                nameIdx = exportedAttrIdx,
                rawValueIdx = -1,
                size = 8,
                res0 = 0,
                dataType = TYPE_INT_BOOL.toByte(),
                data = -1
            ),
            AxmlAttr(
                nsIdx = androidNsIdx,
                nameIdx = launchModeAttrIdx,
                rawValueIdx = -1,
                size = 8,
                res0 = 0,
                dataType = TYPE_INT_DEC.toByte(),
                data = 0 // standard same-task handoff
            )
        )

        return listOf(
            startTag(tagActivityIdx, activityAttrs),
            startTag(
                tagMetaDataIdx,
                mutableListOf(
                    strAttr(nameAttrIdx, metaTargetKeyIdx),
                    strAttr(valueAttrIdx, metaTargetValIdx)
                )
            ),
            endTag(tagMetaDataIdx),
            startTag(tagIntentFilterIdx, mutableListOf()),
            startTag(tagActionIdx, mutableListOf(strAttr(nameAttrIdx, actionMainIdx))),
            endTag(tagActionIdx),
            startTag(tagCategoryIdx, mutableListOf(strAttr(nameAttrIdx, categoryLauncherIdx))),
            endTag(tagCategoryIdx),
            endTag(tagIntentFilterIdx),
            endTag(tagActivityIdx)
        )
    }

    private data class ParsedAxml(
        val stringPool: StringPool,
        val resourceIds: MutableList<Int>,
        val chunks: List<XmlChunk>
    )

    private fun parse(bytes: ByteArray): ParsedAxml {
        require(bytes.size >= 8) { "Binary AXML buffer too small." }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buf.short.toInt() and 0xFFFF
        require(fileType == CHUNK_AXML_FILE) { "Not an Android binary XML file (type=0x${fileType.toString(16)})." }
        val fileHeaderSize = buf.short.toInt() and 0xFFFF
        val fileSize = buf.int
        require(fileSize <= bytes.size) { "Truncated AXML chunk." }

        buf.position(fileHeaderSize)
        var stringPool: StringPool? = null
        val resourceIds = mutableListOf<Int>()
        val chunks = mutableListOf<XmlChunk>()

        while (buf.position() + 8 <= fileSize) {
            val chunkStart = buf.position()
            val type = buf.short.toInt() and 0xFFFF
            val headerSize = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.int
            require(chunkSize >= 8 && chunkStart + chunkSize <= fileSize) { "Invalid AXML chunk at $chunkStart" }

            when (type) {
                CHUNK_STRING_POOL -> {
                    stringPool = parseStringPool(bytes, chunkStart, chunkSize)
                }
                CHUNK_RESOURCE_IDS -> {
                    val count = (chunkSize - headerSize) / 4
                    buf.position(chunkStart + headerSize)
                    repeat(count) {
                        resourceIds.add(buf.int)
                    }
                }
                CHUNK_START_TAG -> {
                    val lineNumber = buf.int
                    val commentIdx = buf.int
                    val nsIdx = buf.int
                    val nameIdx = buf.int
                    val attrStart = buf.short
                    val attrSize = buf.short
                    val attrCount = buf.short.toInt() and 0xFFFF
                    val idIndex = buf.short
                    val classIndex = buf.short
                    val styleIndex = buf.short
                    val attrs = mutableListOf<AxmlAttr>()
                    buf.position(chunkStart + headerSize + (attrStart.toInt() and 0xFFFF))
                    repeat(attrCount) {
                        val aNs = buf.int
                        val aName = buf.int
                        val aRaw = buf.int
                        val aSize = buf.short
                        val aRes0 = buf.get()
                        val aType = buf.get()
                        val aData = buf.int
                        attrs.add(AxmlAttr(aNs, aName, aRaw, aSize, aRes0, aType, aData))
                    }
                    chunks.add(
                        XmlChunk.StartTag(
                            headerSize.toShort(), lineNumber, commentIdx, nsIdx, nameIdx,
                            attrStart, attrSize, idIndex, classIndex, styleIndex, attrs
                        )
                    )
                }
                CHUNK_END_TAG -> {
                    val lineNumber = buf.int
                    val commentIdx = buf.int
                    val nsIdx = buf.int
                    val nameIdx = buf.int
                    chunks.add(XmlChunk.EndTag(headerSize.toShort(), lineNumber, commentIdx, nsIdx, nameIdx))
                }
                CHUNK_START_NAMESPACE, CHUNK_END_NAMESPACE -> {
                    val lineNumber = buf.int
                    val commentIdx = buf.int
                    val prefixIdx = buf.int
                    val uriIdx = buf.int
                    chunks.add(XmlChunk.NamespaceChunk(type, headerSize.toShort(), lineNumber, commentIdx, prefixIdx, uriIdx))
                }
                CHUNK_TEXT -> {
                    val lineNumber = buf.int
                    val commentIdx = buf.int
                    val nameIdx = buf.int
                    val extra = bytes.copyOfRange(buf.position(), chunkStart + chunkSize)
                    chunks.add(XmlChunk.TextChunk(headerSize.toShort(), lineNumber, commentIdx, nameIdx, extra))
                }
                else -> {
                    chunks.add(XmlChunk.RawChunk(type, bytes.copyOfRange(chunkStart, chunkStart + chunkSize)))
                }
            }
            buf.position(chunkStart + chunkSize)
        }

        return ParsedAxml(
            stringPool = stringPool ?: error("AXML missing StringPool chunk."),
            resourceIds = resourceIds,
            chunks = chunks
        )
    }

    private fun parseStringPool(bytes: ByteArray, start: Int, size: Int): StringPool {
        val buf = ByteBuffer.wrap(bytes, start, size).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(start + 8)
        val stringCount = buf.int
        val styleCount = buf.int
        val flags = buf.int
        val stringsStart = buf.int
        val stylesStart = buf.int
        val isUtf8 = (flags and FLAG_UTF8) != 0

        val offsets = IntArray(stringCount) { buf.int }
        val rawStyleOffsets = if (styleCount > 0) {
            ByteArray(styleCount * 4).also { buf.get(it) }
        } else {
            ByteArray(0)
        }
        val baseDataPos = start + stringsStart
        val strings = MutableList(stringCount) { i ->
            val pos = baseDataPos + offsets[i]
            if (isUtf8) readUtf8String(bytes, pos) else readUtf16String(bytes, pos)
        }

        val rawStyles = if (styleCount > 0 && stylesStart > 0 && start + stylesStart < start + size) {
            bytes.copyOfRange(start + stylesStart, start + size)
        } else {
            ByteArray(0)
        }
        return StringPool(flags, strings, styleCount, rawStyleOffsets, rawStyles)
    }

    private fun readUtf8String(bytes: ByteArray, startPos: Int): String {
        var pos = startPos
        // char length
        val c1 = bytes[pos++].toInt() and 0xFF
        if ((c1 and 0x80) != 0) pos++
        // byte length
        val b1 = bytes[pos++].toInt() and 0xFF
        val byteLen = if ((b1 and 0x80) != 0) {
            val b2 = bytes[pos++].toInt() and 0xFF
            ((b1 and 0x7F) shl 8) or b2
        } else {
            b1
        }
        return String(bytes, pos, byteLen.coerceAtMost(bytes.size - pos), Charsets.UTF_8)
    }

    private fun readUtf16String(bytes: ByteArray, startPos: Int): String {
        var pos = startPos
        val u1 = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        val charLen = if ((u1 and 0x8000) != 0) {
            val u2 = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
            pos += 2
            ((u1 and 0x7FFF) shl 16) or u2
        } else {
            u1
        }
        val byteLen = (charLen * 2).coerceAtMost(bytes.size - pos)
        return String(bytes, pos, byteLen, Charsets.UTF_16LE)
    }

    private fun serialize(pool: StringPool, resourceIds: List<Int>, chunks: List<XmlChunk>): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(pool.serialize())
        if (resourceIds.isNotEmpty()) {
            val resSize = 8 + resourceIds.size * 4
            val resBuf = ByteBuffer.allocate(resSize).order(ByteOrder.LITTLE_ENDIAN)
            resBuf.putShort(CHUNK_RESOURCE_IDS.toShort())
            resBuf.putShort(8)
            resBuf.putInt(resSize)
            for (id in resourceIds) resBuf.putInt(id)
            body.write(resBuf.array())
        }

        for (chunk in chunks) {
            when (chunk) {
                is XmlChunk.NamespaceChunk -> {
                    val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                    buf.putShort(chunk.type.toShort())
                    buf.putShort(chunk.headerSize)
                    buf.putInt(24)
                    buf.putInt(chunk.lineNumber)
                    buf.putInt(chunk.commentIdx)
                    buf.putInt(chunk.prefixIdx)
                    buf.putInt(chunk.uriIdx)
                    body.write(buf.array())
                }
                is XmlChunk.TextChunk -> {
                    val size = 20 + chunk.extraBytes.size
                    val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
                    buf.putShort(CHUNK_TEXT.toShort())
                    buf.putShort(chunk.headerSize)
                    buf.putInt(size)
                    buf.putInt(chunk.lineNumber)
                    buf.putInt(chunk.commentIdx)
                    buf.putInt(chunk.nameIdx)
                    buf.put(chunk.extraBytes)
                    body.write(buf.array())
                }
                is XmlChunk.RawChunk -> body.write(chunk.rawBytes)
                is XmlChunk.StartTag -> {
                    val chunkSize = 36 + chunk.attrs.size * 20
                    val buf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                    buf.putShort(CHUNK_START_TAG.toShort())
                    buf.putShort(chunk.headerSize)
                    buf.putInt(chunkSize)
                    buf.putInt(chunk.lineNumber)
                    buf.putInt(chunk.commentIdx)
                    buf.putInt(chunk.nsIdx)
                    buf.putInt(chunk.nameIdx)
                    buf.putShort(20)
                    buf.putShort(20)
                    buf.putShort(chunk.attrs.size.toShort())
                    buf.putShort(chunk.idIndex)
                    buf.putShort(chunk.classIndex)
                    buf.putShort(chunk.styleIndex)
                    for (a in chunk.attrs) {
                        buf.putInt(a.nsIdx)
                        buf.putInt(a.nameIdx)
                        buf.putInt(a.rawValueIdx)
                        buf.putShort(a.size)
                        buf.put(a.res0)
                        buf.put(a.dataType)
                        buf.putInt(a.data)
                    }
                    body.write(buf.array())
                }
                is XmlChunk.EndTag -> {
                    val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                    buf.putShort(CHUNK_END_TAG.toShort())
                    buf.putShort(chunk.headerSize)
                    buf.putInt(24)
                    buf.putInt(chunk.lineNumber)
                    buf.putInt(chunk.commentIdx)
                    buf.putInt(chunk.nsIdx)
                    buf.putInt(chunk.nameIdx)
                    body.write(buf.array())
                }
            }
        }

        val payload = body.toByteArray()
        val totalSize = 8 + payload.size
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(CHUNK_AXML_FILE.toShort())
        header.putShort(8)
        header.putInt(totalSize)
        return header.array() + payload
    }
}
