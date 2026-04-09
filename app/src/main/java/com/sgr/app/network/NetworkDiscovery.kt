package com.sgr.app.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.Enumeration

object NetworkDiscovery {

    private const val BACKEND_PORT = 8080
    private const val CONNECT_TIMEOUT_MS = 400
    private const val VERIFY_TIMEOUT_MS = 1500
    private const val PREFS_NAME = "sgr_network"
    private const val KEY_BACKEND_HOST = "backend_host"

    /**
     * Devuelve la URL base del backend.
     * Primero verifica si el host en caché sigue siendo el backend SGR.
     * Si no, escanea la red local buscando el backend real.
     * Fallback a 10.0.2.2 (emulador) si no encuentra nada.
     */
    suspend fun resolveBaseUrl(context: Context): String = withContext(Dispatchers.IO) {
        val cached = getCachedHost(context)
        if (cached != null && isSgrBackend(cached)) {
            return@withContext "http://$cached:$BACKEND_PORT/"
        }

        val found = scanLocalNetwork()
        if (found != null) {
            cacheHost(context, found)
            return@withContext "http://$found:$BACKEND_PORT/"
        }

        // Fallback para emulador
        "http://10.0.2.2:$BACKEND_PORT/"
    }

    private suspend fun scanLocalNetwork(): String? = coroutineScope {
        val deviceIp = getDeviceIp() ?: return@coroutineScope null
        val parts = deviceIp.split(".")
        if (parts.size != 4) return@coroutineScope null
        val subnet = "${parts[0]}.${parts[1]}.${parts[2]}."
        val deviceLast = parts[3].toIntOrNull() ?: 0

        // Candidatos prioritarios: IPs cercanas al dispositivo (.1 gateway se salta porque
        // suele ser el router, no el backend)
        val priority = mutableListOf<Int>()
        for (delta in 1..15) {
            if (deviceLast - delta in 2..254) priority.add(deviceLast - delta)
            if (deviceLast + delta in 2..254) priority.add(deviceLast + delta)
        }
        // El gateway al final como última prioridad
        priority.add(1)

        // Paso 1: encontrar hosts con el puerto abierto (rápido, en paralelo)
        val portsOpen = mutableListOf<String>()
        for (chunk in priority.chunked(20)) {
            val results = chunk.map { last ->
                async { if (isPortOpen("$subnet$last")) "$subnet$last" else null }
            }.awaitAll().filterNotNull()
            portsOpen.addAll(results)
        }

        // Paso 2: de los hosts con puerto abierto, verificar cuál es el backend SGR
        for (host in portsOpen) {
            if (isSgrBackend(host)) return@coroutineScope host
        }

        // Paso 3: escaneo del resto del subnet
        val scanned = priority.toSet()
        val rest = (2..254).filter { it !in scanned && it != deviceLast }
        val restPortsOpen = mutableListOf<String>()
        for (chunk in rest.chunked(25)) {
            val results = chunk.map { last ->
                async { if (isPortOpen("$subnet$last")) "$subnet$last" else null }
            }.awaitAll().filterNotNull()
            restPortsOpen.addAll(results)
        }

        for (host in restPortsOpen) {
            if (isSgrBackend(host)) return@coroutineScope host
        }

        // Intentar también 10.0.2.2 explícitamente (emulador)
        if (isSgrBackend("10.0.2.2")) return@coroutineScope "10.0.2.2"

        null
    }

    /**
     * Verifica que el host sea realmente el backend SGR haciendo una petición
     * al endpoint de login. El backend SGR siempre responde JSON con Content-Type
     * application/json, mientras que un router u otro servicio no lo haría.
     */
    private fun isSgrBackend(host: String): Boolean {
        return try {
            val url = URL("http://$host:$BACKEND_PORT/api/auth/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = VERIFY_TIMEOUT_MS
            conn.readTimeout = VERIFY_TIMEOUT_MS
            // Enviar body vacío para que el backend responda (devolverá 400)
            conn.outputStream.write("{}".toByteArray())
            conn.outputStream.flush()

            val code = conn.responseCode
            val contentType = conn.getHeaderField("Content-Type") ?: ""
            conn.disconnect()

            // El backend SGR responde JSON ante cualquier error (400, 401, etc.)
            // Un router o servicio diferente devolvería HTML u otro content-type
            contentType.contains("application/json") && code in 400..499
        } catch (e: Exception) {
            false
        }
    }

    private fun isPortOpen(host: String): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, BACKEND_PORT), CONNECT_TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getDeviceIp(): String? {
        return try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    val host = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && host.contains('.') && !host.contains(':')) {
                        return host
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getCachedHost(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND_HOST, null)
    }

    private fun cacheHost(context: Context, host: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BACKEND_HOST, host).apply()
    }

    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_BACKEND_HOST).apply()
    }
}
