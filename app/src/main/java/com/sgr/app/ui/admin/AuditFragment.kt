package com.sgr.app.ui.admin

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sgr.app.databinding.FragmentAuditBinding
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class AuditFragment : Fragment() {

    private var _binding: FragmentAuditBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private var totalPages = 1

    private val tipoLabels = arrayOf("Todos los Tipos", "Espacio", "Equipo")
    private val tipoValues = arrayOf("", "SPACE", "EQUIPMENT")

    private val estatusLabels = arrayOf("Todos los Estatus", "Aprobada", "Rechazada", "Cancelada")
    private val estatusValues = arrayOf("", "APROBADA", "RECHAZADA", "CANCELADA")

    private var filterTipo = ""
    private var filterEstatus = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAuditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerTipo, tipoLabels) { pos ->
            if (!isInitializing) {
                filterTipo = tipoValues[pos]
                filterEstatus = ""
                currentPage = 0
                load()
            }
        }
        setupSpinner(binding.spinnerEstatus, estatusLabels) { pos ->
            if (!isInitializing) {
                filterEstatus = estatusValues[pos]
                filterTipo = ""
                currentPage = 0
                load()
            }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterTipo = ""; filterEstatus = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerTipo.setSelection(0)
            binding.spinnerEstatus.setSelection(0)
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
            filterTipo.isNotEmpty() -> filterTipo
            filterEstatus.isNotEmpty() -> filterEstatus
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getAudit(currentPage, 10, backendFilter)
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = ReservationAdapter(page.content) { _, _ -> }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar historial", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
