package com.fms.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Core lifecycle state of any FMS ecosystem module.
 */
enum class FmsModuleState {
    REGISTERED,
    INITIALIZING,
    READY,
    DEGRADED,
    STOPPED,
    FAILED
}

/**
 * Metadata descriptor for an independent FMS module.
 */
data class FmsModuleDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val category: String,
    val dependencies: List<String> = emptyList(),
    val description: String = ""
)

/**
 * Contract implemented by every library module in the FMS Full Custom Ecosystem.
 */
interface FmsModule {
    val descriptor: FmsModuleDescriptor
    fun initialize(context: FmsRuntimeContext)
    fun shutdown() {}
    fun healthCheck(): Boolean = true
}

/**
 * Shared runtime context passed to FMS modules without tying FMS Core to product-specific classes.
 */
class FmsRuntimeContext(
    val configuration: FmsConfiguration,
    val eventBus: FmsEventBus,
    val logger: FmsLogger,
    val errorEngine: FmsErrorEngine
)

/**
 * Runtime snapshot exposed to diagnostics and product hubs.
 */
data class FmsModuleStatus(
    val descriptor: FmsModuleDescriptor,
    val state: FmsModuleState,
    val initializedAtMs: Long? = null,
    val lastError: String? = null
)

/**
 * FMSCore & FMSRuntime: Independent module registration, dependency ordering, and lifecycle engine.
 */
object FMSRuntime {
    private val modules = ConcurrentHashMap<String, FmsModule>()
    private val statuses = ConcurrentHashMap<String, FmsModuleStatus>()
    private val _statusFlow = MutableStateFlow<List<FmsModuleStatus>>(emptyList())
    val statusFlow: StateFlow<List<FmsModuleStatus>> = _statusFlow.asStateFlow()

    @Volatile
    private var runtimeContext: FmsRuntimeContext? = null

    fun ensureContext(configuration: FmsConfiguration = FmsConfiguration()): FmsRuntimeContext {
        return runtimeContext ?: synchronized(this) {
            runtimeContext ?: FmsRuntimeContext(
                configuration = configuration,
                eventBus = FmsEventBus(),
                logger = FmsLogger,
                errorEngine = FmsErrorEngine(FmsLogger)
            ).also { runtimeContext = it }
        }
    }

    fun register(module: FmsModule) {
        val desc = module.descriptor
        modules[desc.id] = module
        statuses[desc.id] = FmsModuleStatus(desc, FmsModuleState.REGISTERED)
        publishStatuses()
    }

    fun initializeAll(configuration: FmsConfiguration = FmsConfiguration()): List<FmsModuleStatus> {
        val ctx = ensureContext(configuration)
        val ordered = resolveDependencyOrder()
        for (module in ordered) {
            val id = module.descriptor.id
            val current = statuses[id]
            if (current?.state == FmsModuleState.READY) continue

            val missingDeps = module.descriptor.dependencies.filter { depId ->
                statuses[depId]?.state != FmsModuleState.READY
            }
            if (missingDeps.isNotEmpty()) {
                val msg = "Missing ready dependencies: ${missingDeps.joinToString()}"
                statuses[id] = FmsModuleStatus(module.descriptor, FmsModuleState.FAILED, lastError = msg)
                ctx.logger.warn("FMSRuntime", "Skipped $id: $msg")
                continue
            }

            statuses[id] = FmsModuleStatus(module.descriptor, FmsModuleState.INITIALIZING)
            try {
                module.initialize(ctx)
                val healthy = module.healthCheck()
                statuses[id] = FmsModuleStatus(
                    descriptor = module.descriptor,
                    state = if (healthy) FmsModuleState.READY else FmsModuleState.DEGRADED,
                    initializedAtMs = System.currentTimeMillis()
                )
            } catch (t: Throwable) {
                ctx.errorEngine.record("FMSRuntime.init.$id", t)
                statuses[id] = FmsModuleStatus(
                    descriptor = module.descriptor,
                    state = FmsModuleState.FAILED,
                    lastError = t.message ?: t::class.java.simpleName
                )
            }
        }
        publishStatuses()
        return _statusFlow.value
    }

    fun shutdownAll() {
        val ordered = resolveDependencyOrder().reversed()
        for (module in ordered) {
            runCatching { module.shutdown() }
            statuses[module.descriptor.id] = FmsModuleStatus(
                descriptor = module.descriptor,
                state = FmsModuleState.STOPPED
            )
        }
        publishStatuses()
    }

    fun isReady(moduleId: String): Boolean = statuses[moduleId]?.state == FmsModuleState.READY

    fun clearForTests() {
        modules.clear()
        statuses.clear()
        runtimeContext = null
        publishStatuses()
    }

    private fun resolveDependencyOrder(): List<FmsModule> {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val result = mutableListOf<FmsModule>()

        fun dfs(id: String) {
            if (visited.contains(id) || visiting.contains(id)) return
            val mod = modules[id] ?: return
            visiting.add(id)
            for (dep in mod.descriptor.dependencies) {
                dfs(dep)
            }
            visiting.remove(id)
            visited.add(id)
            result.add(mod)
        }

        modules.keys.sorted().forEach { dfs(it) }
        return result
    }

    private fun publishStatuses() {
        _statusFlow.value = statuses.values.sortedBy { it.descriptor.id }
    }
}

/**
 * FMSDependencyEngine & FMSLifecycle lightweight facades for modular discovery.
 */
object FMSCore {
    const val ECOSYSTEM_NAME = "FMS Engine Core V1"
    const val ECOSYSTEM_VERSION = "1.1.0"
    const val NAMESPACE = "com.fms"

    fun registerStandardCatalog() {
        val coreModules = listOf(
            SimpleFmsModule("fms.core", "FMSCore", "1.1.0", "Core", emptyList(), "FMS Engine Core V1 contracts & dependency graph"),
            SimpleFmsModule("fms.event", "FMSEventEngine", "1.1.0", "Core", listOf("fms.core"), "Asynchronous event bus"),
            SimpleFmsModule("fms.logging", "FMSLogging", "1.1.0", "Core", listOf("fms.core"), "Redacted structured logging"),
            SimpleFmsModule("fms.error", "FMSErrorEngine", "1.1.0", "Core", listOf("fms.logging"), "Unified FmsResult error recovery"),
            SimpleFmsModule("fms.security", "FMSSecurityEngine", "1.1.0", "Security", listOf("fms.core"), "Keystore, AES-GCM, PKCS#7 & v2 integrity"),
            SimpleFmsModule("fms.data", "FMSDatabaseEngine", "1.1.0", "Data", listOf("fms.core", "fms.security"), "Local-first SQLite WAL, Time Machine & backup"),
            SimpleFmsModule("fms.ui", "FMSUIEngine", "1.1.0", "UI", listOf("fms.core"), "Theme Studio, Design System & Layout"),
            SimpleFmsModule("fms.task", "FMSBackgroundEngine", "1.1.0", "Task", listOf("fms.core", "fms.event"), "Cancellable coroutine job queue"),
            SimpleFmsModule("fms.cloud", "FMSCloudEngine", "1.1.0", "Cloud", listOf("fms.core", "fms.security"), "Credential Manager Auth & Cloud Sync"),
            SimpleFmsModule("fms.device", "FMSDeviceManager", "1.1.0", "Product", listOf("fms.data", "fms.security"), "Install UUID registry & quota"),
            SimpleFmsModule("fms.clone", "FMSCloneEngine", "1.1.0", "Product", listOf("fms.data", "fms.security", "fms.task"), "APK Inspector, AXML, Canonical Repackager & Signer"),
            SimpleFmsModule("fms.graph", "FmsDependencyGraphAnalyzer", "1.1.0", "Analysis", listOf("fms.clone"), "AXML, multidex, <queries> & ELF DT_NEEDED graph analyzer"),
            SimpleFmsModule("fms.repro", "FmsReproducibleBuilder", "1.1.0", "Build", listOf("fms.clone", "fms.security"), "Canonical deterministic APK builder & provenance"),
            SimpleFmsModule("fms.diff", "FmsBinaryVersionDiff", "1.1.0", "Analysis", listOf("fms.graph"), "Binary APK permission, component, DEX & ELF comparator")
        )
        coreModules.forEach { FMSRuntime.register(it) }
    }
}

class SimpleFmsModule(
    id: String,
    name: String,
    version: String,
    category: String,
    dependencies: List<String>,
    description: String
) : FmsModule {
    override val descriptor = FmsModuleDescriptor(id, name, version, category, dependencies, description)
    private var initialized = false
    override fun initialize(context: FmsRuntimeContext) {
        initialized = true
        context.logger.info("FMSCore", "Initialized ${descriptor.name} (${descriptor.version})")
    }
    override fun healthCheck(): Boolean = initialized
}
