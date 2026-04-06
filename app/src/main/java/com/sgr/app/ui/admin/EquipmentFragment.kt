package com.sgr.app.ui.admin

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentEquipmentBinding
import com.sgr.app.model.Equipment
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class EquipmentFragment : Fragment() {

    private var _binding: FragmentEquipmentBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private var totalPages = 1

    private val condicionLabels = arrayOf("Todos", "Disponible", "En uso", "Mantenimiento")
    private val condicionValues = arrayOf("", "DISPONIBLE", "EN_USO", "MANTENIMIENTO")

    private val categoriaLabels = arrayOf("Todos", "Audiovisual", "Cómputo", "Laboratorio")
    private val categoriaValues = arrayOf("", "AUDIOVISUAL", "COMPUTO", "LABORATORIO")

    private val accesoLabels = arrayOf("Todos", "Permite alumnos", "Restringido")
    private val accesoValues = arrayOf("", "ALUMNOS", "RESTRINGIDO")

    private var filterCondicion = ""
    private var filterCategoria = ""
    private var filterAcceso = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEquipmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerCondicion, condicionLabels) { pos ->
            if (!isInitializing) {
                filterCondicion = condicionValues[pos]
                filterCategoria = ""
                filterAcceso = ""
                currentPage = 0
                load()
            }
        }
        setupSpinner(binding.spinnerCategoria, categoriaLabels) { pos ->
            if (!isInitializing) {
                filterCategoria = categoriaValues[pos]
                filterCondicion = ""
                filterAcceso = ""
                currentPage = 0
                load()
            }
        }
        setupSpinner(binding.spinnerAcceso, accesoLabels) { pos ->
            if (!isInitializing) {
                filterAcceso = accesoValues[pos]
                filterCondicion = ""
                filterCategoria = ""
                currentPage = 0
                load()
            }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterCondicion = ""; filterCategoria = ""; filterAcceso = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerCondicion.setSelection(0)
            binding.spinnerCategoria.setSelection(0)
            binding.spinnerAcceso.setSelection(0)
            isInitializing = false
            load()
        }

        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; load() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; load() } }

        load()
    }

    private fun setupSpinner(spinner: Spinner, labels: Array<String>, onSelect: (Int) -> Unit) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { onSelect(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun load() {
        val backendFilter = when {
            filterCondicion.isNotEmpty() -> filterCondicion
            filterCategoria.isNotEmpty() -> filterCategoria
            filterAcceso.isNotEmpty() -> filterAcceso
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getEquipments(currentPage, 10, backendFilter, "")
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = EquipmentAdapter(page.content) { eq ->
                        lifecycleScope.launch {
                            try { RetrofitClient.create(requireContext()).toggleEquipmentStatus(eq.id); load() }
                            catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar equipos", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class EquipmentAdapter(
    private val items: List<Equipment>,
    private val onToggle: (Equipment) -> Unit
) : RecyclerView.Adapter<EquipmentAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_generic, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.view.apply {
            findViewById<TextView>(R.id.tvTitle).text = e.name
            findViewById<TextView>(R.id.tvSubtitle).text = "${e.category} | No. ${e.inventoryNumber}"
            findViewById<TextView>(R.id.tvDetail).text = e.description
            val badge = findViewById<TextView>(R.id.tvBadge)
            badge.text = e.equipmentCondition
            badge.setBackgroundColor(when (e.equipmentCondition) {
                "DISPONIBLE" -> 0xFF10B981.toInt()
                "EN_USO" -> 0xFFF59E0B.toInt()
                else -> 0xFFEF4444.toInt()
            })
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggle).apply {
                text = if (e.active) "Desactivar" else "Activar"
                setOnClickListener { onToggle(e) }
            }
        }
    }
}
