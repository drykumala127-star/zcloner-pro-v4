package com.fms.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Base contract for typed events dispatched across FMSEventEngine.
 */
interface FmsEvent {
    val topic: String
    val timestampMs: Long
}

data class FmsSystemEvent(
    override val topic: String,
    val payload: Map<String, String> = emptyMap(),
    override val timestampMs: Long = System.currentTimeMillis()
) : FmsEvent

/**
 * FMSEventEngine: Non-blocking pub/sub event bus for decoupled FMS modules.
 */
class FmsEventBus {
    private val _events = MutableSharedFlow<FmsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<FmsEvent> = _events.asSharedFlow()

    fun emit(event: FmsEvent): Boolean = _events.tryEmit(event)

    fun emitTopic(topic: String, payload: Map<String, String> = emptyMap()): Boolean {
        return emit(FmsSystemEvent(topic = topic, payload = payload))
    }
}

/**
 * FMSConfiguration: Runtime configuration parameters for FMS ecosystem modules.
 */
data class FmsConfiguration(
    val debugLogging: Boolean = false,
    val localFirstMode: Boolean = true,
    val maxConcurrentJobs: Int = 2,
    val maxBackupSizeBytes: Long = 2L * 1024L * 1024L
)

/**
 * FMSPluginEngine: Safe in-process extension registry without dynamic code loading (DCL).
 */
interface FmsPlugin {
    val pluginId: String
    val displayName: String
    val category: String
    val version: String
}

object FmsPluginEngine {
    private val plugins = ConcurrentHashMap<String, FmsPlugin>()

    fun register(plugin: FmsPlugin) {
        plugins[plugin.pluginId] = plugin
    }

    fun listPlugins(): List<FmsPlugin> = plugins.values.sortedBy { it.pluginId }
}

/**
 * FMSLogging: Structured logger with automatic secret/token redaction.
 */
enum class FmsLogLevel { DEBUG, INFO, WARN, ERROR }

data class FmsLogEntry(
    val level: FmsLogLevel,
    val tag: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)

object FmsLogger {
    private val keyValueSecretPattern = Regex(
        """(["']?(?:token|id_?token|access_?token|refresh_?token|secret|password|passphrase|keystore_?pass|apikey|api_key|bearer|privatekey|private_?key|credential)["']?\s*[:=]\s*["']?)([^\s,;}"']+)""",
        RegexOption.IGNORE_CASE
    )
    private val bearerHeaderPattern = Regex("""\bBearer\s+[A-Za-z0-9\-._~+/=]{8,}""", RegexOption.IGNORE_CASE)
    private val jwtPattern = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}(?:\.[A-Za-z0-9_-]*)?\b""")
    private val pemPrivateKeyPattern = Regex(
        """-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----""",
        RegexOption.IGNORE_CASE
    )
    private val logLock = Any()
    private val buffer = CopyOnWriteArrayList<FmsLogEntry>()
    private val _logsFlow = MutableStateFlow<List<FmsLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<FmsLogEntry>> = _logsFlow.asStateFlow()

    fun sanitizeMessage(message: String): String {
        var sanitized = pemPrivateKeyPattern.replace(message, "[REDACTED_PRIVATE_KEY]")
        sanitized = bearerHeaderPattern.replace(sanitized, "Bearer [REDACTED]")
        sanitized = jwtPattern.replace(sanitized, "[REDACTED_JWT]")
        sanitized = keyValueSecretPattern.replace(sanitized) { "${it.groupValues[1]}[REDACTED]" }
        return sanitized
    }

    fun info(tag: String, message: String) = record(FmsLogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = record(FmsLogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = record(FmsLogLevel.ERROR, tag, message)
    fun debug(tag: String, message: String) = record(FmsLogLevel.DEBUG, tag, message)

    private fun record(level: FmsLogLevel, tag: String, message: String) {
        val redacted = sanitizeMessage(message)
        val snapshot = synchronized(logLock) {
            if (buffer.size >= 200) {
                buffer.removeAt(0)
            }
            buffer.add(FmsLogEntry(level, tag, redacted))
            buffer.toList()
        }
        _logsFlow.value = snapshot
    }

    fun clear() {
        synchronized(logLock) {
            buffer.clear()
        }
        _logsFlow.value = emptyList()
    }
}

/**
 * FMSErrorEngine: Typed error handling & FmsResult wrapper.
 */
sealed class FmsResult<out T> {
    data class Success<T>(val value: T) : FmsResult<T>()
    data class Failure(val code: String, val message: String, val cause: Throwable? = null) : FmsResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): FmsResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.value
}

data class FmsErrorRecord(
    val source: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class FmsErrorEngine(private val logger: FmsLogger) {
    private val _errors = MutableStateFlow<List<FmsErrorRecord>>(emptyList())
    val errors: StateFlow<List<FmsErrorRecord>> = _errors.asStateFlow()

    fun record(source: String, throwable: Throwable) {
        val rawMsg = throwable.message ?: throwable::class.java.simpleName
        val safeMsg = FmsLogger.sanitizeMessage(rawMsg)
        logger.error(source, safeMsg)
        _errors.value = (_errors.value + FmsErrorRecord(source, safeMsg)).takeLast(50)
    }
}
