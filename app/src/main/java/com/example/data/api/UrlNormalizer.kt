package com.example.data.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlNormalizer {

    fun normalize(rawUrl: String): String {
        var trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Panel URL cannot be empty.")
        }

        val schemeSeparatorIndex = trimmed.indexOf("://")
        if (schemeSeparatorIndex != -1) {
            val scheme = trimmed.substring(0, schemeSeparatorIndex).lowercase()
            if (scheme != "http" && scheme != "https") {
                throw IllegalArgumentException("Protocol must be HTTP or HTTPS.")
            }
        } else {
            trimmed = "https://$trimmed"
        }

        val httpUrl = trimmed.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid URL syntax: $rawUrl")

        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            throw IllegalArgumentException("Protocol must be HTTP or HTTPS.")
        }

        if (httpUrl.host.isBlank()) {
            throw IllegalArgumentException("Host name cannot be empty.")
        }

        val pathSegments = httpUrl.pathSegments.filter { it.isNotBlank() }.toMutableList()

        val authSegments = setOf("auth", "login", "checkpoint", "totp", "two-factor", "register")
        while (pathSegments.isNotEmpty() && pathSegments.last().lowercase() in authSegments) {
            pathSegments.removeAt(pathSegments.size - 1)
        }

        if (pathSegments.takeLast(2).map { it.lowercase() } == listOf("api", "client")) {
            pathSegments.removeAt(pathSegments.size - 1)
            pathSegments.removeAt(pathSegments.size - 1)
        } else if (pathSegments.lastOrNull()?.lowercase() == "client" && pathSegments.getOrNull(pathSegments.size - 2)?.lowercase() == "api") {
            pathSegments.removeAt(pathSegments.size - 1)
            pathSegments.removeAt(pathSegments.size - 1)
        }

        if (pathSegments.isNotEmpty() && (pathSegments.first().lowercase() == "server" || pathSegments.first().lowercase() == "admin")) {
            pathSegments.clear()
        }

        val builder = httpUrl.newBuilder()
            .query(null)
            .fragment(null)

        val currentSegmentsCount = httpUrl.pathSegments.size
        for (i in (currentSegmentsCount - 1) downTo 0) {
            builder.removePathSegment(i)
        }
        for (segment in pathSegments) {
            builder.addPathSegment(segment)
        }

        val result = builder.build().toString()
        return if (result.endsWith("/")) result else "$result/"
    }

    fun isValid(rawUrl: String): Boolean {
        return try {
            normalize(rawUrl)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isLocalOrLan(host: String): Boolean {
        val lower = host.lowercase().trim()
        if (lower == "localhost" || lower == "127.0.0.1" || lower == "::1") return true
        if (lower.startsWith("192.168.") || lower.startsWith("10.")) return true
        if (lower.startsWith("172.")) {
            val parts = lower.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 16..31) return true
            }
        }
        return false
    }

    fun requireHttpsForRemote(rawUrl: String): String {
        val normalized = normalize(rawUrl)
        val httpUrl = normalized.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid URL syntax: $rawUrl")

        if (httpUrl.scheme == "http" && !isLocalOrLan(httpUrl.host)) {
            throw IllegalArgumentException("HTTPS is required for remote production panels to protect your credentials. HTTP is only permitted for local LAN environments.")
        }
        return normalized
    }
}
