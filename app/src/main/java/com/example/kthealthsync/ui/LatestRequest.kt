package com.example.kthealthsync.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Main-thread request ownership: even an uncancellable old response cannot publish state. */
internal class LatestRequest {
    private var generation = 0L
    private var job: Job? = null

    fun cancel() {
        generation++
        job?.cancel()
        job = null
    }

    fun <T> launch(
        scope: CoroutineScope,
        load: suspend () -> T,
        onSuccess: suspend (T) -> Unit,
        onError: (Exception) -> Unit,
        onFinally: () -> Unit = {}
    ) {
        cancel()
        val requestGeneration = generation
        job = scope.launch {
            try {
                val result = load()
                currentCoroutineContext().ensureActive()
                if (generation == requestGeneration) onSuccess(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (generation == requestGeneration) onError(e)
            } finally {
                if (generation == requestGeneration) onFinally()
            }
        }
    }
}
