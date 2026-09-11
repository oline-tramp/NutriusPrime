package com.example.kthealthsync

import com.example.kthealthsync.ui.LatestRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class LatestRequestTest {
    @Test fun slowOldDayCannotOverwriteNewDayOrFinishItsLoadingState() = runTest {
        val request = LatestRequest()
        val events = mutableListOf<String>()
        lateinit var oldResponse: Continuation<String>
        request.launch(this,
            load = { suspendCoroutine<String> { oldResponse = it } },
            onSuccess = { events += it },
            onError = { events += "old-error" },
            onFinally = { events += "old-finished" })
        runCurrent()
        request.launch(this,
            load = { delay(100); "new-day" },
            onSuccess = { events += it },
            onError = { events += "new-error" },
            onFinally = { events += "new-finished" })
        runCurrent()
        oldResponse.resume("old-day")
        runCurrent()
        assertTrue(events.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("new-day", "new-finished"), events)
    }

    @Test fun lateFailureFromOldRequestCannotReplaceSuccessfulData() = runTest {
        val request = LatestRequest()
        val events = mutableListOf<String>()
        lateinit var oldResponse: Continuation<String>
        request.launch(this,
            load = { suspendCoroutine<String> { oldResponse = it } },
            onSuccess = { events += it }, onError = { events += "old-error" })
        runCurrent()
        request.launch(this, load = { "new" },
            onSuccess = { events += it }, onError = { events += "new-error" })
        runCurrent()
        oldResponse.resumeWithException(IllegalStateException("old failure"))
        advanceUntilIdle()
        assertEquals(listOf("new"), events)
    }

    @Test fun changingQueryDiscardsAnAlreadyRunningPageIncludingItsFinallyCallback() = runTest {
        val page = LatestRequest()
        val results = mutableListOf("new-query-result")
        lateinit var oldPage: Continuation<List<String>>
        var finished = false
        page.launch(this,
            load = { suspendCoroutine<List<String>> { oldPage = it } },
            onSuccess = { results += it }, onError = { fail("Stale page error") },
            onFinally = { finished = true })
        runCurrent()
        page.cancel()
        oldPage.resume(listOf("old-query-result"))
        advanceUntilIdle()
        assertEquals(listOf("new-query-result"), results)
        assertFalse(finished)
    }

    @Test fun cancelActuallyStopsCooperativeNetworkWork() = runTest {
        val request = LatestRequest()
        var cancelled = false
        request.launch(this,
            load = { suspendCancellableCoroutine<String> { it.invokeOnCancellation { cancelled = true } } },
            onSuccess = { fail("Cancelled response") }, onError = { fail("Cancellation is not an error") })
        runCurrent()
        request.cancel()
        advanceUntilIdle()
        assertTrue(cancelled)
    }

    @Test fun currentFailureIsReportedAndLoadingFinishes() = runTest {
        val events = mutableListOf<String>()
        LatestRequest().launch(this, load = { throw IllegalStateException("offline") },
            onSuccess = { fail("Unexpected success") },
            onError = { events += it.message!! }, onFinally = { events += "finished" })
        advanceUntilIdle()
        assertEquals(listOf("offline", "finished"), events)
    }

    @Test fun destroyedScopeCannotPublishAnUncancellableResponse() = runTest {
        val lifecycle = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + lifecycle)
        lateinit var response: Continuation<String>
        LatestRequest().launch(scope,
            load = { suspendCoroutine<String> { response = it } },
            onSuccess = { fail("Destroyed screen must not receive data") },
            onError = { fail("Lifecycle cancellation is not an error") })
        runCurrent()
        lifecycle.cancel()
        response.resume("late data")
        advanceUntilIdle()
    }
}
