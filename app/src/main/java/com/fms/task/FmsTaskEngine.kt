package com.fms.task

import com.fms.core.FmsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

enum class FmsJobState { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

data class FmsJobSnapshot(
    val jobId: String,
    val title: String,
    val progress: Float,
    val state: FmsJobState,
    val detail: String = ""
)

/**
 * FMSBackgroundEngine, FMSTaskScheduler, FMSJobQueue & FMSProcessMonitor:
 * Independent concurrency-bounded coroutine job queue with real cancellation support.
 */
class FMSBackgroundEngine(maxParallelJobs: Int = 2) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(maxParallelJobs)
    private val activeCoroutines = ConcurrentHashMap<String, Job>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val _jobs = MutableStateFlow<List<FmsJobSnapshot>>(emptyList())
    val jobs: StateFlow<List<FmsJobSnapshot>> = _jobs.asStateFlow()

    fun enqueue(
        title: String,
        block: suspend (updateProgress: (Float, String) -> Unit) -> Unit,
        onComplete: (Result<Unit>) -> Unit = {}
    ): String {
        val id = "job-" + UUID.randomUUID().toString().take(8)
        updateJob(FmsJobSnapshot(id, title, 0f, FmsJobState.QUEUED, "Menunggu antrean"))
        val launched = scope.launch {
            try {
                semaphore.withPermit {
                    coroutineContext[Job]?.ensureActive()
                    if (id in cancelledIds) throw CancellationException("Job cancelled before execution")
                    updateJob(FmsJobSnapshot(id, title, 0.05f, FmsJobState.RUNNING, "Memulai..."))
                    val res = try {
                        block { p, msg ->
                            if (id in cancelledIds) throw CancellationException("Job cancelled by user")
                            updateJob(FmsJobSnapshot(id, title, p.coerceIn(0f, 1f), FmsJobState.RUNNING, msg))
                        }
                        if (id in cancelledIds) throw CancellationException("Job cancelled before completion")
                        updateJob(FmsJobSnapshot(id, title, 1f, FmsJobState.COMPLETED, "Selesai"))
                        Result.success(Unit)
                    } catch (c: CancellationException) {
                        updateJob(FmsJobSnapshot(id, title, 0f, FmsJobState.CANCELLED, "Dibatalkan oleh pengguna"))
                        throw c
                    } catch (t: Throwable) {
                        val isCancelCause = id in cancelledIds ||
                            generateSequence(t) { it.cause }.any { it is CancellationException }
                        if (isCancelCause) {
                            updateJob(FmsJobSnapshot(id, title, 0f, FmsJobState.CANCELLED, "Dibatalkan oleh pengguna"))
                            throw CancellationException("Job cancelled during execution", t)
                        }
                        FmsLogger.error("FMSJobQueue", "Job $id failed: ${t.message}")
                        updateJob(FmsJobSnapshot(id, title, 0f, FmsJobState.FAILED, FmsLogger.sanitizeMessage(t.message ?: "Gagal")))
                        Result.failure(t)
                    }
                    onComplete(res)
                }
            } catch (c: CancellationException) {
                updateJob(FmsJobSnapshot(id, title, 0f, FmsJobState.CANCELLED, "Dibatalkan oleh pengguna"))
                throw c
            } finally {
                activeCoroutines.remove(id)
                cancelledIds.remove(id)
            }
        }
        activeCoroutines[id] = launched
        return id
    }

    fun cancelJob(jobId: String): Boolean {
        val current = _jobs.value.firstOrNull { it.jobId == jobId } ?: return false
        if (current.state == FmsJobState.COMPLETED || current.state == FmsJobState.FAILED || current.state == FmsJobState.CANCELLED) {
            return false
        }
        cancelledIds.add(jobId)
        activeCoroutines[jobId]?.cancel(CancellationException("Dibatalkan oleh pengguna"))
        updateJob(current.copy(progress = 0f, state = FmsJobState.CANCELLED, detail = "Membatalkan..."))
        return true
    }

    fun cancelAll(): Int {
        var count = 0
        _jobs.value.filter { it.state == FmsJobState.QUEUED || it.state == FmsJobState.RUNNING }
            .forEach { if (cancelJob(it.jobId)) count++ }
        return count
    }

    private fun updateJob(snapshot: FmsJobSnapshot) {
        _jobs.update { existing ->
            val current = existing.toMutableList()
            val idx = current.indexOfFirst { it.jobId == snapshot.jobId }
            if (idx >= 0) current[idx] = snapshot else current.add(0, snapshot)
            current.take(25)
        }
    }

    fun shutdown() {
        cancelAll()
        scope.cancel()
    }
}
