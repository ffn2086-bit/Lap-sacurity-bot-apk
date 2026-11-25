package com.sentinel.laptopguard

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("sentinel_prefs", Context.MODE_PRIVATE)

    fun dbUrl(c: Context): String = sp(c).getString("db_url", "") ?: ""

    fun setDbUrl(c: Context, value: String) {
        var v = value.trim().trimEnd('/')
        if (v.isNotEmpty() && !v.startsWith("http://") && !v.startsWith("https://")) {
            v = "https://$v"
        }
        if (v != dbUrl(c)) {
            sp(c).edit().putBoolean("seeded", false).putString("last_alert_key", "").apply()
        }
        sp(c).edit().putString("db_url", v).apply()
    }

    fun secret(c: Context): String = sp(c).getString("db_secret", "") ?: ""

    fun setSecret(c: Context, value: String) {
        sp(c).edit().putString("db_secret", value.trim()).apply()
    }

    fun monitoring(c: Context): Boolean = sp(c).getBoolean("monitoring", true)

    fun setMonitoring(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("monitoring", on).apply()
    }

    fun lastAlertKey(c: Context): String = sp(c).getString("last_alert_key", "") ?: ""

    fun setLastAlertKey(c: Context, key: String) {
        sp(c).edit().putString("last_alert_key", key).apply()
    }

    fun seeded(c: Context): Boolean = sp(c).getBoolean("seeded", false)

    fun setSeeded(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("seeded", v).apply()
    }

    fun lang(c: Context): AppLang =
        if (sp(c).getString("lang", "en") == "ml") AppLang.ML else AppLang.EN

    fun setLang(c: Context, lang: AppLang) {
        sp(c).edit().putString("lang", if (lang == AppLang.ML) "ml" else "en").apply()
    }
}
