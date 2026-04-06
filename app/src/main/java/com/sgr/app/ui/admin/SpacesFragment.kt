package com.sgr.app.ui.admin

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentSpacesBinding
import com.sgr.app.model.Space
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class SpacesFragment : Fragment() {

    private var _binding: FragmentSpacesBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private var totalPages = 1

    private val disponibilidadLabels = arrayOf("Todos", "Disponible", "Ocupado", "Mantenimiento")
    private val disponibilidadValues = arrayOf("", "DISPONIBLE", "OCUPADO", "MANTENIMIENTO")

    private val accesoLabels = arrayOf("Todos", "Permite alumnos", "Restringido")
    private val accesoValues = arrayOf("", "ALUMNOS", "RESTRINGIDO")

    private val capacidadLabels = arrayOf("Todos", "1 - 30", "31 - 100", "101+")
    private val capacidadValues = arrayOf("", "CAP_SMALL", "CAP_MEDIUM", "CAP_LARGE")

    private var filterDisponibilidad = ""
    private var filterAcceso = ""
    private var filterCapacidad = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSpacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerDisponibilidad, disponibilidadLabels) { pos ->
            if (!isInitializing) {
                filterDisponibilidad = disponibilidadValues[pos]
                filterAcceso = ""
                filterCapacidad = ""
                currentPage = 0
                load()
            }
        }
        setupSpinner(binding.spinnerAcceso, accesoLabels) { pos ->
            if (!isInitializing) {
                filterAcceso = accesoValues[pos]
                filterDisponibilidad = ""
                filterCapacidad = ""
                currentPage = 0
                load()
            }
        }
        setupSpinner(binding.spinnerCapacidad, capacidadLabels) { pos ->
            if (!isInitializing) {
                filterCapacidad = capacidadValues[pos]
                filterDisponibilidad = ""
                filterAcceso = ""
                currentPage = 0
                load()
            }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterDisponibilidad = ""; filterAcceso = ""; filterCapacidad = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerDisponibilidad.setSelection(0)
            binding.spinnerAcceso.setSelection(0)
            binding.spinnerCapacidad.setSelection(0)
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
            filterDisponibilidad.isNotEmpty() -> filterDisponibilidad
            filterCapacidad.isNotEmpty() -> filterCapacidad
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getSpaces(currentPage, 10, backendFilter, "")
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = SpaceAdapter(page.content) { space ->
                        lifecycleScope.launch {
                            try { RetrofitClient.create(requireContext()).toggleSpaceStatus(space.id); load() }
                            catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar espacios", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class SpaceAdapter(
    private val items: List<Space>,
    private val onToggle: (Space) -> Unit
) : RecyclerView.Adapter<SpaceAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_generic, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.view.apply {
            findViewById<TextView>(R.id.tvTitle).text = s.name
            findViewById<TextView>(R.id.tvSubtitle).text = "${s.category} | ${s.location}"
            findViewById<TextView>(R.id.tvDetail).text = "Cap: ${s.capacity} | ${if (s.allowStudents) "Estudiantes permitidos" else "Sin acceso estudiantes"}"
            val badge = findViewById<TextView>(R.id.tvBadge)
            badge.text = s.availability
            badge.setBackgroundColor(when (s.availability) {
                "DISPONIBLE" -> 0xFF10B981.toInt()
                "OCUPADO" -> 0xFFEF4444.toInt()
                else -> 0xFFF59E0B.toInt()
            })
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggle).apply {
                text = if (s.active) "Desactivar" else "Activar"
                setOnClickListener { onToggle(s) }
            }
        }
    }
}
