package com.sbtracker

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

/**
 * Lightweight WebSocket server that broadcasts telemetry to local clients (WebView).
 */
class VaporizerWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    interface CommandListener {
        fun onCommand(json: String)
    }

    var commandListener: CommandListener? = null

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.d("VaporizerWS", "New connection: ${conn?.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        Log.d("VaporizerWS", "Closed connection: ${conn?.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        Log.d("VaporizerWS", "Message from client: $message")
        message?.let { commandListener?.onCommand(it) }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        Log.e("VaporizerWS", "WebSocket Error", ex)
    }

    override fun onStart() {
        Log.d("VaporizerWS", "WebSocket Server started on port $port")
    }

    fun broadcastTelemetry(json: String) {
        if (connections.isNotEmpty()) {
            broadcast(json)
        }
    }
}
