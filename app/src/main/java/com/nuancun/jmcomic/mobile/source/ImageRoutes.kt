package com.nuancun.jmcomic.mobile.source

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

internal class ImageRateLimited(val retryAfter: String?) : IOException("HTTP 429")
internal class ImageRouteCooling : IOException("HTTP 429")

/** Prefer observed throughput, and do not hammer a failing mirror on every page. */
internal class ImageRoutes(private val now: () -> Long = System::nanoTime) {
    private data class Health(
        var rate: Double = 0.0,
        var retryAt: Long = 0,
        var failures: Int = 0,
        var serialUntil: Long = 0,
        var notBefore: Long = 0,
        var active: Int = 0,
        val revision: MutableStateFlow<Long> = MutableStateFlow(0),
    )
    private val hosts = mutableMapOf<String, Health>()

    @Synchronized fun candidates(allowed: List<String>): List<String> {
        val time = now()
        val available = allowed.filter { host ->
            val health = hosts[host]
            (health?.retryAt ?: 0) <= time && (health?.notBefore ?: 0) - time <= MAX_LOCAL_WAIT
        }
        return (available.ifEmpty {
            allowed.minByOrNull { host -> hosts[host]?.let { maxOf(it.retryAt, it.notBefore) } ?: 0 }?.let(::listOf).orEmpty()
        })
            .sortedByDescending { hosts[it]?.rate ?: 0.0 }
    }

    suspend fun <T> transfer(host: String, action: suspend () -> T): T {
        val health = synchronized(this) { hosts.getOrPut(host) { Health() } }
        while (true) {
            val version = health.revision.value
            val wait = synchronized(this) {
                val time = now()
                val remaining = health.notBefore - time
                if (remaining > MAX_LOCAL_WAIT) throw ImageRouteCooling()
                when {
                    remaining > 0 -> (remaining + 999_999) / 1_000_000
                    health.serialUntil > time && health.active > 0 -> -1L
                    else -> { health.active++; 0L }
                }
            }
            if (wait == 0L) break
            if (wait > 0L) delay(wait) else health.revision.first { it != version }
        }
        // Includes requests admitted before a 429, so retries wait for their bodies to exit.
        try { return action() }
        finally { synchronized(this) { health.active--; health.revision.value++ } }
    }

    /** Short per-connection throttling is retried serially; long cooldowns use another mirror. */
    @Synchronized fun throttled(host: String, retryAfter: String?): Boolean {
        val health = hosts.getOrPut(host) { Health() }
        val delayMillis = retryDelayMillis(retryAfter)
        val time = now()
        health.notBefore = maxOf(health.notBefore, time + delayMillis * 1_000_000)
        health.serialUntil = maxOf(health.serialUntil, time + 60_000_000_000L, health.notBefore)
        health.revision.value++
        return delayMillis * 1_000_000 <= MAX_LOCAL_WAIT
    }

    @Synchronized fun succeeded(host: String, bytes: Long, nanos: Long) {
        val health = hosts.getOrPut(host) { Health() }
        val rate = bytes.toDouble() / nanos.coerceAtLeast(1)
        health.rate = if (health.rate == 0.0) rate else health.rate * 0.75 + rate * 0.25
        health.retryAt = 0
        health.failures = 0
    }

    @Synchronized fun failed(host: String) {
        val health = hosts.getOrPut(host) { Health() }
        health.failures = (health.failures + 1).coerceAtMost(4)
        health.retryAt = now() + (5_000_000_000L shl health.failures)
    }

    companion object {
        private const val MAX_LOCAL_WAIT = 250_000_000L

        internal fun retryDelayMillis(value: String?, epochMillis: Long = System.currentTimeMillis()): Long {
            val text = value?.trim() ?: return 100
            text.toLongOrNull()?.takeIf { it >= 0 }?.let { return it.coerceAtMost(86_400) * 1000 }
            return try {
                (ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - epochMillis)
                    .coerceIn(0, 86_400_000)
            } catch (_: java.time.format.DateTimeParseException) { 100 }
        }
    }
}
