package com.sgr.app.ui.user

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentNewRequestBinding
import com.sgr.app.model.CreateReservationRequest
import com.sgr.app.model.Equipment
import com.sgr.app.model.Space
import com.sgr.app.network.RetrofitClient
import com.sgr.app.utils.SessionManager
import kotlinx.coroutines.launch

data class ResourceItem(
    val id: Long,
    val name: String,
    val resourceType: String,
    val meta: String,
    val icon: String
)

class NewRequestFragment : Fragment() {

    private var _binding: FragmentNewRequestBinding? = null
    private val binding get() = _binding!!

    private var currentTab = "SPACE"
    private var currentPage = 0
    private var totalPages = 1
    private var selectedResource: ResourceItem? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""

        val session = SessionManager(requireContext())
        val isStudent = session.userRole == "STUDENT"

        if (isStudent) binding.tvStudentNotice.visibility = View.VISIBLE

        binding.recyclerResources.layoutManager = LinearLayoutManager(requireContext())

        binding.btnTabSpace.setOnClickListener { if (currentTab != "SPACE") switchTab("SPACE") }
        binding.btnTabEquipment.setOnClickListener { if (currentTab != "EQUIPMENT") switchTab("EQUIPMENT") }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { currentPage = 0; loadResources() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; loadResources() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; loadResources() } }
        binding.btnContinue.setOnClickListener { goToStep2() }
        binding.btnBack.setOnClickListener { goToStep1() }
        binding.btnChange.setOnClickListener { goToStep1() }
        binding.btnSubmit.setOnClickListener { submit() }

        loadResources()
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        currentPage = 0
        selectedResource = null
        binding.btnContinue.isEnabled = false

        val green = android.content.res.ColorStateList.valueOf(0xFF00843D.toInt())
        val inactive = android.content.res.ColorStateList.valueOf(0xFFF1F5F9.toInt())
        if (tab == "SPACE") {
            binding.btnTabSpace.backgroundTintList = green
            binding.btnTabSpace.setTextColor(0xFFFFFFFF.toInt())
            binding.btnTabEquipment.backgroundTintList = inactive
            binding.btnTabEquipment.setTextColor(0xFF6B7280.toInt())
            binding.etSearch.setText("")
            binding.etSearch.hint = "Buscar espacio..."
        } else {
            binding.btnTabEquipment.backgroundTintList = green
            binding.btnTabEquipment.setTextColor(0xFFFFFFFF.toInt())
            binding.btnTabSpace.backgroundTintList = inactive
            binding.btnTabSpace.setTextColor(0xFF6B7280.toInt())
            binding.etSearch.setText("")
            binding.etSearch.hint = "Buscar equipo..."
        }
        loadResources()
    }

    private fun loadResources() {
        val search = binding.etSearch.text?.toString()?.trim() ?: ""
        val session = SessionManager(requireContext())
        val isStudent = session.userRole == "STUDENT"

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val items: List<ResourceItem>

                if (currentTab == "SPACE") {
                    val resp = api.getSpaces(currentPage, 10, "", search)
                    if (resp.isSuccessful) {
                        val page = resp.body() ?: return@launch
                        totalPages = page.totalPages.coerceAtLeast(1)
                        val filtered = page.content.filter { it.active }.filter { !isStudent || it.allowStudents }
                        items = filtered.map { ResourceItem(it.id, it.name, "SPACE", "${it.location} | Cap: ${it.capacity}", "🏢") }
                    } else return@launch
                } else {
                    val resp = api.getEquipments(currentPage, 10, "", search)
                    if (resp.isSuccessful) {
                        val page = resp.body() ?: return@launch
                        totalPages = page.totalPages.coerceAtLeast(1)
                        val filtered = page.content.filter { it.active && it.condition == "DISPONIBLE" }.filter { !isStudent || it.allowStudents }
                        items = filtered.map { ResourceItem(it.id, it.name, "EQUIPMENT", "${it.category} | ${it.inventoryNumber}", "📦") }
                    } else return@launch
                }

                binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"

                if (items.isEmpty()) {
                    binding.recyclerResources.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerResources.visibility = View.VISIBLE
                    binding.recyclerResources.adapter = ResourceAdapter(items, selectedResource?.id) { res ->
                        selectedResource = res
                        binding.btnContinue.isEnabled = true
                        (binding.recyclerResources.adapter as ResourceAdapter).setSelected(res.id)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun goToStep2() {
        val res = selectedResource ?: return
        binding.layoutStep1.visibility = View.GONE
        binding.layoutStep2.visibility = View.VISIBLE
        binding.step2Indicator.setBackgroundResource(R.drawable.bg_step_active)

        binding.tvBannerIcon.text = res.icon
        binding.tvBannerTitle.text = res.name
        binding.tvBannerSub.text = "${res.meta} • ${if (res.resourceType == "SPACE") "Espacio" else "Equipo"}"
    }

    private fun goToStep1() {
        binding.layoutStep2.visibility = View.GONE
        binding.layoutStep1.visibility = View.VISIBLE
        binding.step2Indicator.setBackgroundResource(R.drawable.bg_step_inactive)
    }

    private fun submit() {
        val startDate = binding.etDate.text?.toString()?.trim() ?: ""
        val startTime = binding.etStartTime.text?.toString()?.trim() ?: ""
        val endDate = binding.etEndDate.text?.toString()?.trim() ?: ""
        val endTime = binding.etEndTime.text?.toString()?.trim() ?: ""
        val purpose = binding.etPurpose.text?.toString()?.trim() ?: ""
        val res = selectedResource ?: return

        if (startDate.isEmpty() || startTime.isEmpty() || endTime.isEmpty() || purpose.isEmpty()) {
            Toast.makeText(requireContext(), "Completa todos los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }
        if (purpose.length < 10) {
            Toast.makeText(requireContext(), "El motivo debe tener al menos 10 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        val session = SessionManager(requireContext())
        binding.btnSubmit.isEnabled = false

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.createReservation(
                    CreateReservationRequest(
                        requesterId = session.userId,
                        resourceType = res.resourceType,
                        resourceId = res.id,
                        reservationDate = startDate,
                        startTime = startTime,
                        endDate = endDate.ifBlank { startDate },
                        endTime = endTime,
                        purpose = purpose,
                        observations = null
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Solicitud enviada exitosamente", Toast.LENGTH_LONG).show()
                    selectedResource = null
                    binding.btnContinue.isEnabled = false
                    binding.etDate.text?.clear()
                    binding.etStartTime.text?.clear()
                    binding.etEndDate.text?.clear()
                    binding.etEndTime.text?.clear()
                    binding.etPurpose.text?.clear()
                    goToStep1()
                    loadResources()
                } else {
                    Toast.makeText(requireContext(), "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            } finally { binding.btnSubmit.isEnabled = true }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ResourceAdapter(
    private val items: List<ResourceItem>,
    private var selectedId: Long?,
    private val onSelect: (ResourceItem) -> Unit
) : RecyclerView.Adapter<ResourceAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    fun setSelected(id: Long) { selectedId = id; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_resource_card, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val isSelected = item.id == selectedId
        holder.view.apply {
            setBackgroundResource(if (isSelected) R.drawable.bg_card_selected else R.drawable.bg_card_selectable)
            findViewById<TextView>(R.id.tvResourceIcon).text = item.icon
            findViewById<TextView>(R.id.tvResourceName).text = item.name
            findViewById<TextView>(R.id.tvResourceLocation).text = item.meta
            findViewById<TextView>(R.id.tvResourceMeta).text = item.meta
            val check = findViewById<TextView>(R.id.tvCheckMark)
            check.visibility = if (isSelected) View.VISIBLE else View.GONE
            setOnClickListener { onSelect(item) }
        }
    }
}