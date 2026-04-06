package com.sgr.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sgr.app.databinding.ActivityLoginBinding
import com.sgr.app.model.LoginRequest
import com.sgr.app.network.RetrofitClient
import com.sgr.app.ui.admin.AdminActivity
import com.sgr.app.ui.user.UserActivity
import com.sgr.app.utils.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        if (session.isLoggedIn) {
            navigateByRole(session.userRole)
            return
        }

        binding.btnLogin.setOnClickListener { doLogin() }
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
                    showError("Credenciales incorrectas. Verifica tu correo y contraseña.")
                }
            } catch (e: Exception) {
                showError("Error de conexión. Verifica tu red.")
            } finally {
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Iniciar Sesión"
            }
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
