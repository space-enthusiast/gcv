package com.github.spaceenthusiast.clipboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClipboardJanitor(
    private val service: ClipboardService,
    private val sweepInterval: Duration,
) {
    private val log = LoggerFactory.getLogger(ClipboardJanitor::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(): Job = scope.launch {
        while (isActive) {
            try {
                val removed = service.deleteExpired()
                if (removed > 0) log.info("Reaped {} expired clipboard entries", removed)
            } catch (t: Throwable) {
                log.warn("Janitor sweep failed", t)
            }
            delay(sweepInterval)
        }
    }

    fun stop() {
        scope.cancel()
    }
}

fun janitor(service: ClipboardService, sweepSeconds: Long): ClipboardJanitor =
    ClipboardJanitor(service, sweepSeconds.seconds)
