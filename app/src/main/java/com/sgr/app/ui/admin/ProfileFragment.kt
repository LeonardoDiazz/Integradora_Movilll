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

        binding.tvName.text = "Nombre: ${session.userName} ${session.userLastName}"
        binding.tvEmail.text = "Correo: ${session.userEmail}"
        binding.tvRole.text = "Rol: ${session.userRole}"

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
                        Toast.makeText(requireContext(), "Perfil actualizado", Toast.LENGTH_SHORT).show()
                    } else {
                        val error = response.errorBody()?.string() ?: "Error desconocido"
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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
                Toast.makeText(requireContext(), "La nueva contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(requireContext(), "Contraseña actualizada", Toast.LENGTH_SHORT).show()
                        binding.etCurrentPass.text?.clear()
                        binding.etNewPass.text?.clear()
                        binding.etConfirmPass.text?.clear()
                    } else {
                        val error = response.errorBody()?.string() ?: "Error desconocido"
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
