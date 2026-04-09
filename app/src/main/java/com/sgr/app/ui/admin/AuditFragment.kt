package com.sgr.app.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentAuditBinding
import com.sgr.app.model.Reservation
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
                    binding.recyclerView.adapter = AuditAdapter(page.content) { r -> showAuditDetailDialog(r) }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar historial", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    private fun showAuditDetailDialog(r: Reservation) {
        val resourceName = if (r.resourceType == "SPACE") r.spaceName ?: "—" else r.equipmentName ?: "—"
        val resourceType = if (r.resourceType == "SPACE") "Espacio" else "Equipo"
        val schedule = if (r.startTime.isNotBlank() && r.startTime != "null" &&
            r.endTime.isNotBlank() && r.endTime != "null")
            "${r.startTime} - ${r.endTime}" else "—"
        val msg = """
            Solicitante: ${r.requesterName ?: "—"} (${r.requesterEmail ?: "—"})
            Tipo: $resourceType
            Recurso: $resourceName
            Fecha: ${r.reservationDate}
            Horario: $schedule
            Estado: ${r.status}
            Motivo: ${r.purpose}
            Observaciones: ${r.observations ?: "—"}
            Comentario admin: ${r.adminComment ?: "—"}
        """.trimIndent()
        AlertDialog.Builder(requireContext())
            .setTitle("Detalle de reservación #${r.id}")
            .setMessage(msg)
            .setPositiveButton("Cerrar", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class AuditAdapter(
    private val items: List<Reservation>,
    private val onViewDetail: (Reservation) -> Unit
) : RecyclerView.Adapter<AuditAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_audit, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.view.apply {
            val resourceName = if (r.resourceType == "SPACE") r.spaceName ?: "—" else r.equipmentName ?: "—"
            findViewById<TextView>(R.id.tvResourceName).text = resourceName

            findViewById<TextView>(R.id.tvRequesterName).text = r.requesterName ?: r.requesterEmail ?: "—"

            val categoryLabel = if (r.resourceType == "SPACE") "Espacio" else "Equipo"
            findViewById<TextView>(R.id.tvCategory).text = categoryLabel

            findViewById<TextView>(R.id.tvAuditDate).text = r.reservationDate

            val schedule = if (r.startTime.isNotBlank() && r.startTime != "null" &&
                r.endTime.isNotBlank() && r.endTime != "null")
                "${r.startTime} - ${r.endTime}" else "—"
            findViewById<TextView>(R.id.tvAuditSchedule).text = schedule

            val tvStatus = findViewById<TextView>(R.id.tvAuditStatus)
            val statusLabel = when (r.status) {
                "APROBADA" -> "Aprobada"
                "RECHAZADA" -> "Rechazada"
                "CANCELADA" -> "Cancelada"
                "PENDIENTE" -> "Pendiente"
                "DEVUELTA" -> "Devuelta"
                else -> r.status
            }
            tvStatus.text = statusLabel
            val statusColor = when (r.status) {
                "PENDIENTE" -> 0xFFF59E0B.toInt()
                "APROBADA" -> 0xFF10B981.toInt()
                "RECHAZADA" -> 0xFFEF4444.toInt()
                "CANCELADA" -> 0xFF6B7280.toInt()
                "DEVUELTA" -> 0xFF3B82F6.toInt()
                else -> 0xFF6B7280.toInt()
            }
            tvStatus.setBackgroundColor(statusColor)

            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAuditViewDetail)
                .setOnClickListener { onViewDetail(r) }
        }
    }
}
