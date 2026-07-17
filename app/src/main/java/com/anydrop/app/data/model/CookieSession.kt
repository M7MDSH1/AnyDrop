package com.anydrop.app.data.model

data class CookieSession(
    val domain: String,
    val cookies: List<CookieEntry>
)

data class CookieEntry(
    val domain: String,
    val includeSubdomains: Boolean,
    val path: String,
    val secure: Boolean,
    val expiry: Long,
    val name: String,
    val value: String
)
