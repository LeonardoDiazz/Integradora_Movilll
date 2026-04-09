package com.sgr.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sgr.app.databinding.ActivityLoginBinding
import com.sgr.app.model.LoginRequest
import com.sgr.app.network.NetworkDiscovery
import com.sgr.app.network.RetrofitClient
import com.sgr.app.ui.admin.AdminActivity
import com.sgr.app.ui.user.UserActivity
import com.sgr.app.utils.SessionManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        binding.btnLogin.isEnabled = false
        binding.btnLogin.setOnClickListener { doLogin() }

        discoverBackend()
    }

    private fun discoverBackend() {
        showNetworkStatus(searching = true, message = "Buscando servidor en la red...")

        lifecycleScope.launch {
            try {
                val url = NetworkDiscovery.resolveBaseUrl(this@LoginActivity)
                val host = url.removePrefix("http://").removeSuffix(":8080/")
                if (host == "10.0.2.2") {
                    showNetworkStatus(searching = false, message = "Conectado: emulador (10.0.2.2)")
                } else {
                    showNetworkStatus(searching = false, message = "Servidor encontrado: $host")
                }
                binding.btnLogin.isEnabled = true
            } catch (e: Exception) {
                showNetworkStatus(searching = false, message = "Sin conexion al servidor. Verifica la red.")
                binding.btnLogin.isEnabled = true
            }
        }
    }

    private fun showNetworkStatus(searching: Boolean, message: String) {
        binding.progressNetwork.visibility = if (searching) View.VISIBLE else View.GONE
        binding.tvNetworkStatus.text = message
    }

    private fun doLogin() {
        val email = binding.etEmail.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""

        if (email.isEmpty() || password.isEmpty()) {
            showError("Completa todos los campos")
            return
        }

        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Verificando..."
        binding.layoutError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(this@LoginActivity)
                val response = api.login(LoginRequest(email, password))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    session.save(body.token, body.userId, body.name, body.lastName, body.email, body.role)
                    navigateByRole(body.role)
                } else {
                    // Leer el mensaje real del backend
                    val errorMsg = parseErrorBody(response.errorBody()?.string())
                    showError(errorMsg)
                }
            } catch (e: Exception) {
                // Si falla la conexion, intentar redescubrir el backend
                showError("Error de conexion. Reintentando busqueda de servidor...")
                binding.btnLogin.isEnabled = false
                try {
                    val api = RetrofitClient.createWithRediscovery(this@LoginActivity)
                    val url = NetworkDiscovery.resolveBaseUrl(this@LoginActivity)
                    val host = url.removePrefix("http://").removeSuffix(":8080/")
                    showNetworkStatus(searching = false, message = "Servidor encontrado: $host")
                    val response = api.login(LoginRequest(email, password))
                    if (response.isSuccessful) {
                        val body = response.body()!!
                        session.save(body.token, body.userId, body.name, body.lastName, body.email, body.role)
                        navigateByRole(body.role)
                        return@launch
                    } else {
                        val errorMsg = parseErrorBody(response.errorBody()?.string())
                        showError(errorMsg)
                    }
                } catch (e2: Exception) {
                    showError("Sin conexion al servidor. Verifica que el backend este activo en la red.")
                }
            } finally {
                if (binding.btnLogin.text == "Verificando...") {
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "Iniciar Sesion"
                }
            }
        }
    }

    /**
     * Extrae el campo "message" del JSON de error que devuelve el backend.
     * Ejemplo: {"message": "Credenciales incorrectas"} → "Credenciales incorrectas"
     */
    private fun parseErrorBody(errorJson: String?): String {
        if (errorJson.isNullOrBlank()) return "Error desconocido. Intenta de nuevo."
        return try {
            JSONObject(errorJson).getString("message")
        } catch (e: Exception) {
            "Error al iniciar sesion. Verifica tus datos."
        }
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.layoutError.visibility = View.VISIBLE
    }

    private fun navigateByRole(role: String?) {
        val intent = if (role == "ADMIN") {
            Intent(this, AdminActivity::class.java)
        } else {
            Intent(this, UserActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
