package com.fms.zclone.enginecoremode

import com.fms.core.FmsErrorEngine
import com.fms.core.FmsLogger
import com.fms.core.FmsModule
import com.fms.core.FmsModuleDescriptor
import com.fms.core.FmsModuleState
import com.fms.core.FmsRuntimeContext
import com.fms.core.FMSRuntime
import com.fms.data.FMSBackupEngine
import com.fms.data.LocalCloneRecord
import com.fms.security.FMSIntegrityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FmsCoreAndDataTest {

    @Before
    fun setUp() {
        FMSRuntime.clearForTests()
        FmsLogger.clear()
    }

    @Test
    fun `fms runtime resolves dependencies in topological order and isolates missing dependencies`() {
        val initOrder = mutableListOf<String>()

        val modA = object : FmsModule {
            override val descriptor = FmsModuleDescriptor("mod.a", "Mod A", "1.0", "Core", emptyList())
            override fun initialize(context: FmsRuntimeContext) { initOrder.add("mod.a") }
        }
        val modB = object : FmsModule {
            override val descriptor = FmsModuleDescriptor("mod.b", "Mod B", "1.0", "Core", listOf("mod.a"))
            override fun initialize(context: FmsRuntimeContext) { initOrder.add("mod.b") }
        }
        val modBroken = object : FmsModule {
            override val descriptor = FmsModuleDescriptor("mod.broken", "Broken", "1.0", "Core", listOf("mod.missing"))
            override fun initialize(context: FmsRuntimeContext) { initOrder.add("mod.broken") }
        }

        FMSRuntime.register(modB)
        FMSRuntime.register(modA)
        FMSRuntime.register(modBroken)

        val statuses = FMSRuntime.initializeAll()
        assertEquals(listOf("mod.a", "mod.b"), initOrder)
        assertEquals(FmsModuleState.READY, statuses.first { it.descriptor.id == "mod.a" }.state)
        assertEquals(FmsModuleState.READY, statuses.first { it.descriptor.id == "mod.b" }.state)
        assertEquals(FmsModuleState.FAILED, statuses.first { it.descriptor.id == "mod.broken" }.state)
    }

    @Test
    fun `fms logger redacts sensitive tokens and passwords`() {
        FmsLogger.info("AuthTest", "Login completed token=secret_abc_123 password=my_pass")
        val latest = FmsLogger.logsFlow.value.last()
        assertFalse(latest.message.contains("secret_abc_123"))
        assertFalse(latest.message.contains("my_pass"))
        assertTrue(latest.message.contains("token=[REDACTED]"))
        assertTrue(latest.message.contains("password=[REDACTED]"))
    }

    @Test
    fun `backup engine exports validates and rejects corrupt or oversized payloads`() {
        val sample = LocalCloneRecord(
            id = "c1",
            sourcePackage = "com.demo.app",
            targetPackage = "com.demo.app.zclone1",
            sourceAppName = "Demo",
            cloneName = "Demo Clone",
            iconPresetId = "neon_cyan",
            iconHueShift = 180,
            changePackageName = true,
            hideFromLauncher = false,
            isolateData = true,
            strippedPermissionsCsv = "",
            compatibilityStatus = "COMPATIBLE",
            buildStatus = "BUILT",
            outputApkPath = null,
            updatedAtMs = 12345L
        )
        val json = FMSBackupEngine.exportJson(
            configs = listOf(sample),
            activeThemeId = "custom",
            languageCode = "id",
            fontChoice = "JETBRAINS_MONO",
            iconStyle = "NEON_RING",
            customPrimaryArgb = 0xFF00D2FF.toInt(),
            customSecondaryArgb = 0xFF8B5CF6.toInt()
        )
        val parsed = FMSBackupEngine.parseAndValidate(json.toByteArray())
        assertTrue(parsed.isSuccess)
        val validated = parsed.getOrThrow()
        assertEquals(1, validated.cloneConfigs.size)
        assertEquals("com.demo.app.zclone1", validated.cloneConfigs.first().targetPackage)
        assertEquals("JETBRAINS_MONO", validated.fontChoice)
        assertEquals("NEON_RING", validated.iconStyle)
        assertEquals(0xFF00D2FF.toInt(), validated.customPrimaryArgb)
        assertEquals(0xFF8B5CF6.toInt(), validated.customSecondaryArgb)

        // Corrupt JSON
        assertTrue(FMSBackupEngine.parseAndValidate("{invalid".toByteArray()).isFailure)
        // Oversized > 2 MB
        val huge = ByteArray(FMSBackupEngine.MAX_BACKUP_BYTES + 128) { 'a'.code.toByte() }
        assertTrue(FMSBackupEngine.parseAndValidate(huge).isFailure)
    }

    @Test
    fun `integrity engine prevents zip-slip traversal and validates package syntax`() {
        val targetDir = File("/tmp/zclone_zip_test")
        assertTrue(FMSIntegrityEngine.resolveZipEntrySafe(targetDir, "res/drawable/icon.png").isSuccess)
        assertTrue(FMSIntegrityEngine.resolveZipEntrySafe(targetDir, "../../etc/passwd").isFailure)
        assertTrue(FMSIntegrityEngine.resolveZipEntrySafe(targetDir, "/abs/path").isFailure)

        assertTrue(FMSIntegrityEngine.isValidPackageName("com.demo.zclone1"))
        assertFalse(FMSIntegrityEngine.isValidPackageName("invalid"))
        assertFalse(FMSIntegrityEngine.isValidPackageName("com.123bad.app"))
    }
}
