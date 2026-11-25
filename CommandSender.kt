package com.sentinel.laptopguard

import android.content.Context
import org.json.JSONObject

/** Sends remote-control commands to the laptop agent through the Realtime Database. */
object CommandSender {

    fun send(ctx: Context, cmd: String, arg: String = ""): Boolean {
        val body = JSONObject()
            .put("cmd", cmd)
            .put("arg", arg)
            .put("ts", JSONObject().put(".sv", "timestamp"))
            .toString()
        return Bridge.post(ctx, "/commands", body) != null
    }

    fun setArmed(ctx: Context, armed: Boolean): Boolean =
        Bridge.put(ctx, "/config/armed", if (armed) "true" else "false") != null

    /** Pushes a fake alert so the full-screen red popup can be tested end to end. */
    fun sendTestAlert(ctx: Context): Boolean {
        val body = JSONObject()
            .put("title", "TEST")
            .put("time", System.currentTimeMillis())
            .put("laptop", "TEST-LAPTOP")
            .put("ip_local", "192.168.0.10")
            .put("ip_public", "203.0.113.7")
            .put("reason", "Test alert from phone (Lid Opened)")
            .toString()
        return Bridge.post(ctx, "/alerts", body) != null
    }
}
