package com.sentinel.laptopguard

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Firebase Realtime Database REST client (no Firebase SDK, no google-services.json).
 * Every call is blocking: always call from a background dispatcher.
 */
object Bridge {

    private fun buildUrl(ctx: Context, path: String, query: String): String {
        val base = Prefs.dbUrl(ctx)
        val secret = Prefs.secret(ctx)
        val sb = StringBuilder(base).append(path).append(".json")
        val params = ArrayList<String>()
        if (secret.isNotEmpty()) params.add("auth=" + URLEncoder.encode(secret, "UTF-8"))
        if (query.isNotEmpty()) params.add(query)
        if (params.isNotEmpty()) sb.append("?").append(params.joinToString("&"))
        return sb.toString()
    }

    /** Returns the response body on HTTP 2xx, or null on any failure. */
    private fun request(method: String, url: String, body: String?): String? {
        var conn: HttpURLConnection? = null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.useCaches = false
            if (method == "PATCH") {
                c.requestMethod = "POST"
                c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                c.requestMethod = method
            }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { out -> out.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code in 200..299) {
                c.inputStream.bufferedReader(Charsets.UTF_8).use { r -> r.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun get(ctx: Context, path: String, query: String = ""): String? =
        request("GET", buildUrl(ctx, path, query), null)

    fun put(ctx: Context, path: String, json: String): String? =
        request("PUT", buildUrl(ctx, path, ""), json)

    fun post(ctx: Context, path: String, json: String): String? =
        request("POST", buildUrl(ctx, path, ""), json)

    fun patch(ctx: Context, path: String, json: String): String? =
        request("PATCH", buildUrl(ctx, path, ""), json)

    fun delete(ctx: Context, path: String): String? =
        request("DELETE", buildUrl(ctx, path, ""), null)

    /** null = request failed; empty JSONObject = node empty. */
    fun getObject(ctx: Context, path: String, query: String = ""): JSONObject? {
        val text = get(ctx, path, query) ?: return null
        return try {
            if (text.trim() == "null" || text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: Exception) {
            JSONObject()
        }
    }
}
