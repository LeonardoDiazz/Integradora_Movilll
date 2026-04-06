package com.sgr.app.ui.user

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sgr.app.databinding.FragmentNewRequestBinding
import com.sgr.app.model.CreateReservationRequest
import com.sgr.app.model.Equipment
import com.sgr.app.model.Space
import com.sgr.app.network.RetrofitClient
import com.sgr.app.utils.SessionManager
import kotlinx.coroutines.launch

class NewRequestFragment : Fragment() {

    private var _binding: FragmentNewRequestBinding? = null
    private val binding get() = _binding!!

    private var spaces = listOf<Space>()
    private var equipments = listOf<Equipment>()
    private var selectedType = "SPACE"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""

        val types = arrayOf("Espacio", "Equipo")
        binding.spinnerResourceType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerResourceType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedType = if (pos == 0) "SPACE" else "EQUIPMENT"
                updateResourceSpinner()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        loadResources()

        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun loadResources() {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                spaces = api.getSpaces(0, 100).body()?.content ?: emptyList()
                equipments = api.getEquipments(0, 100).body()?.content ?: emptyList()
                updateResourceSpinner()
            } catch (_: Exception) {}
        }
    }

    private fun updateResourceSpinner() {
        val items = if (selectedType == "SPACE") {
            spaces.filter { it.active && it.availability == "DISPONIBLE" }.map { it.name }
        } else {
            equipments.filter { it.active && it.equipmentCondition == "DISPONIBLE" }.map { it.name }
        }
        binding.spinnerResource.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun submit() {
        val date = binding.etDate.text?.toString()?.trim() ?: ""
        val startTime = binding.etStartTime.text?.toString()?.trim() ?: ""
        val endTime = binding.etEndTime.text?.toString()?.trim() ?: ""
        val purpose = binding.etPurpose.text?.toString()?.trim() ?: ""
        val selectedPos = binding.spinnerResource.selectedItemPosition

        if (date.isEmpty() || startTime.isEmpty() || endTime.isEmpty() || purpose.isEmpty()) {
            Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }
        if (purpose.length < 10) {
            Toast.makeText(requireContext(), "El propósito debe tener al menos 10 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        val session = SessionManager(requireContext())
        val spaceId: Long?
        val equipmentId: Long?

        if (selectedType == "SPACE") {
            val availableSpaces = spaces.filter { it.active && it.availability == "DISPONIBLE" }
            if (availableSpaces.isEmpty() || selectedPos >= availableSpaces.size) {
                Toast.makeText(requireContext(), "Selecciona un espacio", Toast.LENGTH_SHORT).show()
                return
            }
            spaceId = availableSpaces[selectedPos].id
            equipmentId = null
        } else {
            val availableEq = equipments.filter { it.active && it.equipmentCondition == "DISPONIBLE" }
            if (availableEq.isEmpty() || selectedPos >= availableEq.size) {
                Toast.makeText(requireContext(), "Selecciona un equipo", Toast.LENGTH_SHORT).show()
                return
            }
            spaceId = null
            equipmentId = availableEq[selectedPos].id
        }

        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.createReservation(
                    CreateReservationRequest(
                        requesterId = session.userId,
                        resourceType = selectedType,
                        spaceId = spaceId,
                        equipmentId = equipmentId,
                        reservationDate = date,
                        startTime = startTime,
                        endTime = endTime,
                        purpose = purpose,
                        observations = null
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "¡Solicitud enviada exitosamente!", Toast.LENGTH_LONG).show()
                    binding.etDate.text?.clear()
                    binding.etStartTime.text?.clear()
                    binding.etEndTime.text?.clear()
                    binding.etPurpose.text?.clear()
                } else {
                    Toast.makeText(requireContext(), "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSubmit.isEnabled = true
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
