package com.sgr.app.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentReservationsBinding
import com.sgr.app.model.ApproveRequest
import com.sgr.app.model.CreateReservationRequest
import com.sgr.app.model.Reservation
import com.sgr.app.model.RejectRequest
import com.sgr.app.model.ReturnRequest
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class ReservationsFragment : Fragment() {

    private var _binding: FragmentReservationsBinding? = null
    private val binding get() = _binding!!
    private var currentPage = 0
    private var totalPages = 1

    private val estatusLabels = arrayOf("Todos los Estatus", "Pendiente", "Aprobada (En préstamo)", "Rechazada", "Devuelta", "Cancelada")
    private val estatusValues = arrayOf("", "PENDIENTE", "APROBADA", "RECHAZADA", "DEVUELTA", "CANCELADA")

    private val tipoLabels = arrayOf("Todos los Tipos", "Espacio", "Equipo")
    private val tipoValues = arrayOf("", "SPACE", "EQUIPMENT")

    private var filterEstatus = ""
    private var filterTipo = ""
    private var requesterSearch = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReservationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerFilter, estatusLabels) { pos ->
            if (!isInitializing) { filterEstatus = estatusValues[pos]; filterTipo = ""; currentPage = 0; load() }
        }
        setupSpinner(binding.spinnerTipo, tipoLabels) { pos ->
            if (!isInitializing) { filterTipo = tipoValues[pos]; filterEstatus = ""; currentPage = 0; load() }
        }

        isInitializing = false

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { requesterSearch = s?.toString()?.trim() ?: ""; currentPage = 0; load() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnClearFilters.setOnClickListener {
            filterEstatus = ""; filterTipo = ""; requesterSearch = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerFilter.setSelection(0)
            binding.spinnerTipo.setSelection(0)
            isInitializing = false
            binding.etSearch.text?.clear()
            load()
        }

        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; load() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; load() } }
        binding.btnAddReservation.setOnClickListener { showCreateReservationDialog() }

        load()
    }

    private fun showCreateReservationDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }

        fun label(text: String) = TextView(ctx).apply { this.text = text; textSize = 12f; setPadding(0, 12, 0, 2) }

        val etRequesterId = EditText(ctx).apply { hint = "ID del solicitante (número)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etDate = EditText(ctx).apply { hint = "YYYY-MM-DD" }
        val etStart = EditText(ctx).apply { hint = "HH:MM" }
        val etEnd = EditText(ctx).apply { hint = "HH:MM" }
        val etPurpose = EditText(ctx).apply { hint = "Motivo de la reserva"; minLines = 3 }
        val etObs = EditText(ctx).apply { hint = "Observaciones (opcional)" }

        val typeValues = arrayOf("SPACE", "EQUIPMENT")
        val typeLabels = arrayOf("Espacio", "Equipo")
        val spinnerType = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, typeLabels)
        }
        val etResourceId = EditText(ctx).apply { hint = "ID del recurso (número)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }

        layout.addView(label("ID Solicitante *")); layout.addView(etRequesterId)
        layout.addView(label("Tipo de recurso")); layout.addView(spinnerType)
        layout.addView(label("ID del recurso *")); layout.addView(etResourceId)
        layout.addView(label("Fecha *")); layout.addView(etDate)
        layout.addView(label("Hora inicio *")); layout.addView(etStart)
        layout.addView(label("Hora fin *")); layout.addView(etEnd)
        layout.addView(label("Motivo *")); layout.addView(etPurpose)
        layout.addView(label("Observaciones")); layout.addView(etObs)

        val sv = ScrollView(ctx).apply { addView(layout) }

        AlertDialog.Builder(ctx)
            .setTitle("Crear reservación")
            .setView(sv)
            .setPositiveButton("Crear") { _, _ ->
                val requesterId = etRequesterId.text.toString().toLongOrNull()
                val resourceId = etResourceId.text.toString().toLongOrNull()
                val date = etDate.text.toString().trim()
                val start = etStart.text.toString().trim()
                val end = etEnd.text.toString().trim()
                val purpose = etPurpose.text.toString().trim()

                if (requesterId == null || resourceId == null || date.isEmpty() || start.isEmpty() || end.isEmpty() || purpose.isEmpty()) {
                    Toast.makeText(ctx, "Completa todos los campos requeridos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedType = typeValues[spinnerType.selectedItemPosition]
                lifecycleScope.launch {
                    try {
                        val api = RetrofitClient.create(ctx)
                        val req = CreateReservationRequest(
                            requesterId = requesterId,
                            resourceType = selectedType,
                            spaceId = if (selectedType == "SPACE") resourceId else null,
                            equipmentId = if (selectedType == "EQUIPMENT") resourceId else null,
                            reservationDate = date,
                            startTime = start,
                            endTime = end,
                            purpose = purpose,
                            observations = etObs.text.toString().trim().ifEmpty { null }
                        )
                        val resp = api.createReservation(req)
                        if (resp.isSuccessful) {
                            Toast.makeText(ctx, "Reservación creada", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(ctx, "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showViewReservationDialog(r: Reservation) {
        val resourceName = if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
        val resourceType = if (r.resourceType == "SPACE") "Espacio" else "Equipo"
        val msg = """
            Solicitante: ${r.requesterName ?: "—"} (${r.requesterEmail ?: "—"})
            Tipo: $resourceType
            Recurso: ${resourceName ?: "—"}
            Fecha: ${r.reservationDate}
            Horario: ${r.startTime} - ${r.endTime}
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
            filterEstatus.isNotEmpty() -> filterEstatus
            filterTipo.isNotEmpty() -> filterTipo
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getReservations(currentPage, 10, backendFilter)
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    val filtered = if (requesterSearch.isEmpty()) page.content
                        else page.content.filter { (it.requesterName ?: "").contains(requesterSearch, ignoreCase = true) }
                    binding.recyclerView.adapter = ReservationAdapter(filtered) { action, reservation ->
                        when (action) {
                            "approve" -> showApproveDialog(reservation)
                            "reject" -> showRejectDialog(reservation)
                            "return" -> showReturnDialog(reservation)
                            "view" -> showViewReservationDialog(reservation)
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar reservaciones", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showApproveDialog(r: Reservation) {
        val input = EditText(requireContext()).apply { hint = "Comentario (opcional)" }
        AlertDialog.Builder(requireContext())
            .setTitle("Aprobar reservación")
            .setMessage("¿Aprobar la reserva de ${r.requesterName}?")
            .setView(input)
            .setPositiveButton("Aprobar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        RetrofitClient.create(requireContext()).approveReservation(r.id, ApproveRequest(input.text.toString().ifBlank { null }))
                        Toast.makeText(requireContext(), "Reservación aprobada", Toast.LENGTH_SHORT).show()
                        load()
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRejectDialog(r: Reservation) {
        val input = EditText(requireContext()).apply { hint = "Motivo de rechazo (requerido)" }
        AlertDialog.Builder(requireContext())
            .setTitle("Rechazar reservación")
            .setView(input)
            .setPositiveButton("Rechazar") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.length < 10) {
                    Toast.makeText(requireContext(), "El motivo debe tener al menos 10 caracteres", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        RetrofitClient.create(requireContext()).rejectReservation(r.id, RejectRequest(reason))
                        Toast.makeText(requireContext(), "Reservación rechazada", Toast.LENGTH_SHORT).show()
                        load()
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showReturnDialog(r: Reservation) {
        val conditions = arrayOf("BUEN_ESTADO", "DAÑADO")
        var selectedCondition = "BUEN_ESTADO"
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val spinnerCondition = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, conditions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { selectedCondition = conditions[pos] }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        val inputDesc = EditText(requireContext()).apply { hint = "Descripción (opcional)" }
        layout.addView(spinnerCondition)
        layout.addView(inputDesc)

        AlertDialog.Builder(requireContext())
            .setTitle("Registrar devolución")
            .setView(layout)
            .setPositiveButton("Registrar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        RetrofitClient.create(requireContext()).returnReservation(
                            r.id, ReturnRequest(selectedCondition, inputDesc.text.toString().ifBlank { null })
                        )
                        Toast.makeText(requireContext(), "Devolución registrada", Toast.LENGTH_SHORT).show()
                        load()
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ReservationAdapter(
    private val items: List<Reservation>,
    private val onAction: (String, Reservation) -> Unit
) : RecyclerView.Adapter<ReservationAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_reservation, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.view.apply {
            findViewById<TextView>(R.id.tvRequester).text = r.requesterName ?: r.requesterEmail ?: "—"
            val resourceName = if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
            findViewById<TextView>(R.id.tvResource).text = "${r.resourceType}: ${resourceName ?: "—"}"
            findViewById<TextView>(R.id.tvDate).text = "${r.reservationDate} | ${r.startTime} - ${r.endTime}"

            val tvStatus = findViewById<TextView>(R.id.tvStatus)
            tvStatus.text = r.status
            val color = when (r.status) {
                "PENDIENTE" -> 0xFFF59E0B.toInt()
                "APROBADA" -> 0xFF10B981.toInt()
                "RECHAZADA" -> 0xFFEF4444.toInt()
                "CANCELADA" -> 0xFF6B7280.toInt()
                else -> 0xFF6B7280.toInt()
            }
            tvStatus.setBackgroundColor(color)

            val layoutActions = findViewById<LinearLayout>(R.id.layoutActions)
            val btnApprove = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApprove)
            val btnReject = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReject)
            val btnReturn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReturn)

            when (r.status) {
                "PENDIENTE" -> {
                    layoutActions.visibility = View.VISIBLE
                    btnReturn.visibility = View.GONE
                    btnApprove.setOnClickListener { onAction("approve", r) }
                    btnReject.setOnClickListener { onAction("reject", r) }
                }
                "APROBADA" -> {
                    layoutActions.visibility = View.GONE
                    btnReturn.visibility = View.VISIBLE
                    btnReturn.setOnClickListener { onAction("return", r) }
                }
                else -> {
                    layoutActions.visibility = View.GONE
                    btnReturn.visibility = View.GONE
                }
            }

            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewDetail)
                .setOnClickListener { onAction("view", r) }
            // Hide student-only buttons in admin view
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEditReservation).visibility = View.GONE
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewRejection).visibility = View.GONE
        }
    }
}
