package com.darkbit.bypass

import android.util.Log
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LocalTlsProxy(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false

    val assignedPort: Int
        get() = serverSocket?.localPort ?: port

    fun start() {
        try {
            serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
            val boundPort = serverSocket?.localPort ?: port
            isRunning = true
            thread {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: continue
                        handleClient(client)
                    } catch (e: Exception) {
                        if (isRunning) Log.e("LocalTlsProxy", "Accept error", e)
                    }
                }
            }
            Log.i("LocalTlsProxy", "HTTP CONNECT Proxy started on 127.0.0.1:$boundPort")
        } catch (e: Exception) {
            Log.e("LocalTlsProxy", "Failed to start", e)
        }
    }

    private fun handleClient(client: Socket) {
        thread {
            var target: Socket? = null
            try {
                val input = client.getInputStream()
                val output = client.getOutputStream()

                // Read HTTP headers byte-by-byte to avoid buffering body
                val headers = readHeaders(input)
                val firstLine = headers.lines().firstOrNull() ?: return@thread

                if (firstLine.startsWith("CONNECT")) {
                    val parts = firstLine.split(" ")
                    if (parts.size >= 2) {
                        val hostPort = parts[1].split(":")
                        val host = hostPort[0]
                        val targetPort = if (hostPort.size > 1) hostPort[1].toInt() else 443

                        // Connect to target
                        target = Socket(host, targetPort)
                        
                        // Send 200 OK
                        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                        output.flush()

                        // Forward traffic
                        val targetInput = target.getInputStream()
                        val targetOutput = target.getOutputStream()

                        val t1 = thread {
                            try { input.copyTo(targetOutput) } catch (e: Exception) {}
                            try { target.close() } catch (e: Exception) {}
                        }
                        val t2 = thread {
                            try { targetInput.copyTo(output) } catch (e: Exception) {}
                            try { client.close() } catch (e: Exception) {}
                        }
                        t1.join()
                        t2.join()
                        return@thread
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalTlsProxy", "Handler error", e)
            } finally {
                try { target?.close() } catch (e: Exception) {}
                try { client.close() } catch (e: Exception) {}
            }
        }
    }

    private fun readHeaders(input: InputStream): String {
        val sb = StringBuilder()
        var r: Int
        while (input.read().also { r = it } != -1) {
            sb.append(r.toChar())
            if (sb.endsWith("\r\n\r\n")) break
        }
        return sb.toString()
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) {}
    }
}
