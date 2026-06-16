package com.darkbit.bypass

import android.util.Log
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

class LocalTlsProxy(private val port: Int, private val targetHost: String) {
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
            Log.i("LocalTlsProxy", "Started on 127.0.0.1:$boundPort -> TLS $targetHost:443")
        } catch (e: Exception) {
            Log.e("LocalTlsProxy", "Failed to start", e)
        }
    }

    private fun handleClient(client: Socket) {
        thread {
            var target: Socket? = null
            try {
                val sf = SSLSocketFactory.getDefault() as SSLSocketFactory
                target = sf.createSocket(targetHost, 443) as SSLSocket
                target.startHandshake()

                val input = client.getInputStream()
                val output = client.getOutputStream()
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
            } catch (e: Exception) {
                Log.e("LocalTlsProxy", "Handler error", e)
            } finally {
                try { target?.close() } catch (e: Exception) {}
                try { client.close() } catch (e: Exception) {}
            }
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) {}
    }
}
