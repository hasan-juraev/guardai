package com.guardai.metrics

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

object MetricsLogger {
    private const val TAG = "GUARDAI_METRIC"

    private val sequence = AtomicLong(0L)

    @Volatile
    private var sessionId: String = "none"

    fun beginSession(id: String) {
        sessionId = id
        logEvent(
            event = "session_begin",
            fields = mapOf("session_id" to id),
        )
    }

    fun endSession(reason: String) {
        logEvent(
            event = "session_end",
            fields = mapOf(
                "session_id" to sessionId,
                "reason" to reason,
            ),
        )
        sessionId = "none"
    }

    fun logEvent(
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        val orderedFields = linkedMapOf<String, Any?>(
            "seq" to sequence.incrementAndGet(),
            "t_ns" to SystemClock.elapsedRealtimeNanos(),
            "session_id" to sessionId,
            "event" to event,
        )
        orderedFields.putAll(fields)

        Log.i(
            TAG,
            orderedFields.entries.joinToString(",") { (key, value) ->
                "$key=${sanitize(value)}"
            },
        )
    }

    private fun sanitize(value: Any?): String {
        return value
            ?.toString()
            ?.replace(",", ";")
            ?.replace("\n", " ")
            ?.replace("\r", " ")
            ?.replace("=", ":")
            ?: "null"
    }
}
