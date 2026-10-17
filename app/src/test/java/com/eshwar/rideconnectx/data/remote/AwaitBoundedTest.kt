package com.eshwar.rideconnectx.data.remote

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AwaitBoundedTest {
    @Test
    fun `a finished write has a null result and is not a timeout`() = runBlocking {
        assertNull(Tasks.forResult<Void?>(null).awaitBounded())
    }

    @Test
    fun `a missing document reads as null data, not a timeout`() = runBlocking {
        assertNull(Tasks.forResult<Map<String, Any>?>(null).awaitBounded())
    }

    @Test
    fun `a value passes through`() = runBlocking {
        assertEquals("x", Tasks.forResult("x").awaitBounded())
    }

    @Test
    fun `a task that never finishes is an IOException`() = runBlocking {
        try {
            TaskCompletionSource<Void>().task.awaitBounded(waitMs = 50)
            fail("expected IOException")
        } catch (e: IOException) {
            // offline
        }
    }
}
