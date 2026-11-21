package com.studiomogli.ytcontrol

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {

    private val SAFE_HOST_SUFFIXES = listOf(
        "googlevideo.com",
        "accounts.google.com",
        "accounts.youtube.com",
        "myaccount.google.com",
        "studio.youtube.com",
        "gemini.google.com",
        "generativelanguage.googleapis.com",
        "gds.google.com",
        "ssl.gstatic.com"
    )

    private val BLOCKED_HOST_SUFFIXES = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adservice.google.co.in",
        "fundingchoicesmessages.google.com",
        "google-analytics.com",
        "googletagmanager.com"
    )

    private val BLOCKED_PATH_SUBSTRINGS = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/ptracking",
        "/get_midroll_info",
        "/ad_data_204",
        "/pcs/activeview",
        "/youtubei/v1/log_event",
        "adformat=",
        "ad_type="
    )

    fun isAdRequest(request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val host = uri.host?.lowercase() ?: return false

        if (SAFE_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) {
            return false
        }

        if (BLOCKED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) {
            return true
        }

        val fullUrl = uri.toString().lowercase()
        if (fullUrl.contains("/youtubei/v1/player") || fullUrl.contains("/youtubei/v1/next")) {
            return false
        }

        return BLOCKED_PATH_SUBSTRINGS.any { fullUrl.contains(it) }
    }

    fun intercept(request: WebResourceRequest?): WebResourceResponse? {
        if (!isAdRequest(request)) return null
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "No Content",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
