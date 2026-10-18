package com.nuancun.jmcomic.mobile.work

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Idle workers immediately take another page; a failed page does not discard its siblings. */
internal suspend fun downloadChapterPages(count: Int, action: suspend (Int) -> Unit): List<Exception> {
    val next = AtomicInteger()
    val failures = ConcurrentLinkedQueue<Exception>()
    coroutineScope {
        repeat(minOf(8, count)) {
            launch {
                while (true) {
                    val index = next.getAndIncrement()
                    if (index >= count) break
                    try { action(index) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { failures.add(error) }
                }
            }
        }
    }
    return failures.toList()
}
