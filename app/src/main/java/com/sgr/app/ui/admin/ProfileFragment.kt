package com.sgr.app.ui.admin

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sgr.app.databinding.FragmentProfileBinding
import com.sgr.app.model.ChangePasswordRequest
import com.sgr.app.model.UpdateProfileRequest
import com.sgr.app.network.RetrofitClient
import com.sgr.app.utils.SessionManager
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        val session = SessionManager(requireContext())

        val fullName = "${session.userName} ${session.userLastName}"
        val initial = session.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        val roleLabel = when (session.userRole) {
            "ADMIN" -> "Administrador"
            "STAFF" -> "Personal"
            else -> "Estudiante"
        }

        // Header
        binding.tvAvatar.text = initial
        binding.tvName.text = fullName
        binding.tvRole.text = roleLabel
        binding.tvEmail.text = session.userEmail

        // Grid de datos
        binding.tvNameValue.text = session.userName ?: "—"
        binding.tvLastNameValue.text = session.userLastName ?: "—"
        binding.tvEmailValue.text = session.userEmail ?: "—"
        binding.tvRoleValue.text = roleLabel

        // Cargar teléfono desde la API (endpoint accesible a todos los autenticados)
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getProfile(session.userId)
                if (response.isSuccessful) {
                    val user = response.body()
                    val phone = user?.phone?.takeIf { it.isNotBlank() }
                    session.userPhone = phone
                    binding.tvPhoneValue.text = phone ?: "Sin teléfono registrado"
                }
            } catch (_: Exception) { }
        }

        // Mostrar teléfono guardado en sesión mientras carga
        binding.tvPhoneValue.text = session.userPhone?.takeIf { it.isNotBlank() } ?: "Sin teléfono registrado"

        // Teléfono
        binding.btnEditPhone.setOnClickListener {
            binding.layoutEditPhone.visibility = View.VISIBLE
            binding.btnEditPhone.visibility = View.GONE
            val current = session.userPhone?.takeIf { it.isNotBlank() }
            if (current != null) binding.etPhone.setText(current)
        }
        binding.btnCancelPhone.setOnClickListener {
            binding.layoutEditPhone.visibility = View.GONE
            binding.btnEditPhone.visibility = View.VISIBLE
            binding.etPhone.text?.clear()
        }
        binding.btnSaveProfile.setOnClickListener {
            val phone = binding.etPhone.text?.toString()?.trim() ?: ""
            if (phone.isNotEmpty() && phone.length != 10) {
                Toast.makeText(requireContext(), "El teléfono debe tener 10 dígitos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val api = RetrofitClient.create(requireContext())
                    val response = api.updateProfile(
                        session.userId,
                        UpdateProfileRequest(session.userName ?: "", session.userLastName ?: "", phone.ifBlank { null })
                    )
                    if (response.isSuccessful) {
                        session.userPhone = phone.ifBlank { null }
                        binding.tvPhoneValue.text = phone.ifBlank { "Sin teléfono registrado" }
                        binding.layoutEditPhone.visibility = View.GONE
                        binding.btnEditPhone.visibility = View.VISIBLE
                        binding.etPhone.text?.clear()
                        Toast.makeText(requireContext(), "Teléfono actualizado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Error al actualizar", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Contraseña
        binding.btnEditPass.setOnClickListener {
            binding.layoutEditPass.visibility = View.VISIBLE
            binding.btnEditPass.visibility = View.GONE
        }
        binding.btnCancelPass.setOnClickListener {
            binding.layoutEditPass.visibility = View.GONE
            binding.btnEditPass.visibility = View.VISIBLE
            binding.etCurrentPass.text?.clear()
            binding.etNewPass.text?.clear()
            binding.etConfirmPass.text?.clear()
        }
        binding.btnChangePass.setOnClickListener {
            val current = binding.etCurrentPass.text?.toString()?.trim() ?: ""
            val new = binding.etNewPass.text?.toString()?.trim() ?: ""
            val confirm = binding.etConfirmPass.text?.toString()?.trim() ?: ""

            if (current.isEmpty() || new.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (new.length < 8) {
                Toast.makeText(requireContext(), "Mínimo 8 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (new != confirm) {
                Toast.makeText(requireContext(), "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val api = RetrofitClient.create(requireContext())
                    val response = api.changePassword(session.userId, ChangePasswordRequest(current, new, confirm))
                    if (response.isSuccessful) {
                        binding.layoutEditPass.visibility = View.GONE
                        binding.btnEditPass.visibility = View.VISIBLE
                        binding.etCurrentPass.text?.clear()
                        binding.etNewPass.text?.clear()
                        binding.etConfirmPass.text?.clear()
                        Toast.makeText(requireContext(), "Contraseña actualizada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Error al cambiar contraseña", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
