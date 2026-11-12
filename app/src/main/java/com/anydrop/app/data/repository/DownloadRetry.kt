package com.anydrop.app.data.repository

/**
 * Which failed downloads are worth trying again by themselves: the ones a dropped or slow
 * connection caused. A removed video, a private one or a full phone won't get better by waiting.
 */
object DownloadRetry {

    /** Automatic tries after the first failure. */
    const val MAX_ATTEMPTS = 3

    /** The wait before automatic try [attempt] (1-based): 5 s, 15 s, 45 s. */
    fun delayMs(attempt: Int): Long {
        var wait = 5_000L
        repeat((attempt - 1).coerceAtLeast(0)) { wait *= 3 }
        return wait
    }

    private val PERMANENT = listOf(
        "http error 401", "http error 403", "http error 404", "http error 410", "no space left",
        "unsupported url", "private video", "sign in", "log in", "login required", "not available", "copyright"
    )

    private val TRANSIENT = listOf(
        "timed out", "timeout", "connection reset", "connection aborted", "connection refused", "connection closed",
        "network is unreachable", "temporary failure", "failed to resolve", "unable to resolve", "unknownhost",
        "name or service not known", "no address associated", "broken pipe", "incompleteread", "incomplete read",
        "eof occurred", "unexpected end of stream", "software caused connection abort", "remote end closed",
        "stream was reset", "socketexception", "connectexception", "sslexception", "transporterror",
        "http error 408", "http error 429", "http error 5", "unable to download"
    )

    /** [error]: the message shown plus the tool's own words. */
    fun isTransient(error: String): Boolean {
        val e = error.lowercase()
        return PERMANENT.none { it in e } && TRANSIENT.any { it in e }
    }
}
