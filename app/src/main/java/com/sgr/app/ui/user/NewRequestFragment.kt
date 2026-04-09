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

    private var spaces = listOf<Space>()
    private var equipments = listOf<Equipment>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""

        val session = SessionManager(requireContext())
        val isStudent = session.userRole == "STUDENT" || session.userRole == "STUDENTS"

        if (isStudent) binding.tvStudentNotice.visibility = View.VISIBLE

        binding.recyclerResources.layoutManager = LinearLayoutManager(requireContext())

        // Tabs
        binding.btnTabSpace.setOnClickListener { if (currentTab != "SPACE") switchTab("SPACE") }
        binding.btnTabEquipment.setOnClickListener { if (currentTab != "EQUIPMENT") switchTab("EQUIPMENT") }

        // Search
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { currentPage = 0; loadResources() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // Pagination
        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; loadResources() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; loadResources() } }

        // Continue
        binding.btnContinue.setOnClickListener { goToStep2() }

        // Back
        binding.btnBack.setOnClickListener { goToStep1() }

        // Change resource
        binding.btnChange.setOnClickListener { goToStep1() }

        // Submit
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
                    if (!resp.isSuccessful) {
                        Toast.makeText(requireContext(), "Error al cargar espacios: ${resp.code()}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val page = resp.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    spaces = page.content
                    val filtered = page.content
                        .filter { it.active != false }
                        .filter { it.availability?.uppercase() == "DISPONIBLE" }
                        .filter { !isStudent || it.allowStudents != false }
                    items = filtered.map {
                        ResourceItem(it.id, it.name, "SPACE", "📍 ${it.location} • 👥 ${it.capacity}", "🏢")
                    }
                } else {
                    val resp = api.getEquipments(currentPage, 10, "", search)
                    if (!resp.isSuccessful) {
                        Toast.makeText(requireContext(), "Error al cargar equipos: ${resp.code()}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val page = resp.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    equipments = page.content
                    val filtered = page.content
                        .filter { it.active != false }
                        .filter { it.equipmentCondition?.uppercase() == "DISPONIBLE" }
                        .filter { !isStudent || it.allowStudents != false }
                    items = filtered.map {
                        ResourceItem(it.id, it.name, "EQUIPMENT", "${it.category} • ${it.inventoryNumber}", "📦")
                    }
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
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
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

        if (res.resourceType == "SPACE") {
            binding.cardSpaceEquipment.visibility = View.VISIBLE
            loadSpaceEquipment(res.id)
        } else {
            binding.cardSpaceEquipment.visibility = View.GONE
        }
    }

    private fun loadSpaceEquipment(spaceId: Long) {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val resp = api.getEquipments(0, 50, "", "")
                if (resp.isSuccessful) {
                    val all = resp.body()?.content ?: emptyList()
                    val filtered = all.filter { it.active }
                    binding.rvSpaceEquipment.layoutManager = LinearLayoutManager(requireContext())
                    if (filtered.isEmpty()) {
                        binding.tvNoEquipment.visibility = View.VISIBLE
                        binding.rvSpaceEquipment.visibility = View.GONE
                    } else {
                        binding.tvNoEquipment.visibility = View.GONE
                        binding.rvSpaceEquipment.visibility = View.VISIBLE
                        binding.rvSpaceEquipment.adapter = SpaceEquipmentAdapter(filtered)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun goToStep1() {
        binding.layoutStep2.visibility = View.GONE
        binding.layoutStep1.visibility = View.VISIBLE
        binding.step2Indicator.setBackgroundResource(R.drawable.bg_step_inactive)
    }

    private fun submit() {
        val startDate = binding.etDate.text?.toString()?.trim() ?: ""
        val startTime = binding.etStartTime.text?.toString()?.trim() ?: ""
        val endTime = binding.etEndTime.text?.toString()?.trim() ?: ""
        val purpose = binding.etPurpose.text?.toString()?.trim() ?: ""
        val res = selectedResource ?: return

        if (startDate.isEmpty() || startTime.isEmpty() || endTime.isEmpty() || purpose.isEmpty()) {
            Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }
        if (purpose.length < 10) {
            Toast.makeText(requireContext(), "El propósito debe tener al menos 10 caracteres", Toast.LENGTH_SHORT).show()
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
                        spaceId = if (res.resourceType == "SPACE") res.id else null,
                        equipmentId = if (res.resourceType == "EQUIPMENT") res.id else null,
                        reservationDate = startDate,
                        startTime = startTime,
                        endTime = endTime,
                        purpose = purpose,
                        observations = null
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "¡Solicitud enviada exitosamente!", Toast.LENGTH_LONG).show()
                    selectedResource = null
                    binding.btnContinue.isEnabled = false
                    binding.etDate.text?.clear()
                    binding.etEndDate.text?.clear()
                    binding.etStartTime.text?.clear()
                    binding.etEndTime.text?.clear()
                    binding.etPurpose.text?.clear()
                    goToStep1()
                    loadResources()
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

class SpaceEquipmentAdapter(
    private val items: List<Equipment>
) : RecyclerView.Adapter<SpaceEquipmentAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_space_equipment, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val eq = items[position]
        holder.view.apply {
            findViewById<TextView>(R.id.tvEqName).text = eq.name
            findViewById<TextView>(R.id.tvEqInv).text = eq.inventoryNumber

            val tvCond = findViewById<TextView>(R.id.tvEqCondition)
            when (eq.equipmentCondition) {
                "DISPONIBLE" -> {
                    tvCond.text = "Disponible"
                    tvCond.setBackgroundResource(R.drawable.bg_badge_green)
                    tvCond.setTextColor(0xFF065F46.toInt())
                }
                "EN_USO" -> {
                    tvCond.text = "En uso"
                    tvCond.setBackgroundResource(R.drawable.bg_badge_yellow)
                    tvCond.setTextColor(0xFF92400E.toInt())
                }
                else -> {
                    tvCond.text = "Mantenimiento"
                    tvCond.setBackgroundResource(R.drawable.bg_badge_red)
                    tvCond.setTextColor(0xFF991B1B.toInt())
                }
            }
        }
    }
}
