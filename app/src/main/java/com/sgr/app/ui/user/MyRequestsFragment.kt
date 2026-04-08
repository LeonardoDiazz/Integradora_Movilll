package com.sgr.app.ui.user

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentMyRequestsBinding
import com.sgr.app.model.Reservation
import com.sgr.app.model.UpdateReservationRequest
import com.sgr.app.network.RetrofitClient
import com.sgr.app.utils.SessionManager
import kotlinx.coroutines.launch

class MyRequestsFragment : Fragment() {

    private var _binding: FragmentMyRequestsBinding? = null
    private val binding get() = _binding!!
    private var currentPage = 0
    private var totalPages = 1
    private var currentStatusFilter = ""
    private var currentTypeFilter = ""

    private val statusLabels = arrayOf("Todos los Estatus", "Pendiente", "Aprobada (En préstamo)", "Rechazada", "Devuelta", "Cancelada")
    private val statusValues = arrayOf("", "PENDIENTE", "APROBADA", "RECHAZADA", "DEVUELTA", "CANCELADA")

    private val typeLabels = arrayOf("Todos los Tipos", "Espacio", "Equipo")
    private val typeValues = arrayOf("", "SPACE", "EQUIPMENT")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statusLabels)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStatus.adapter = statusAdapter
        binding.spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentStatusFilter = statusValues[pos]; currentTypeFilter = ""
                binding.spinnerType.setSelection(0); currentPage = 0; load()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeLabels)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerType.adapter = typeAdapter
        binding.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentTypeFilter = typeValues[pos]; currentStatusFilter = ""
                binding.spinnerStatus.setSelection(0); currentPage = 0; load()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        binding.btnReset.setOnClickListener {
            currentStatusFilter = ""; currentTypeFilter = ""; currentPage = 0
            binding.spinnerStatus.setSelection(0); binding.spinnerType.setSelection(0); load()
        }

        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; load() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; load() } }

        load()
    }

    private fun load() {
        val session = SessionManager(requireContext())
        val filter = currentStatusFilter.ifEmpty { currentTypeFilter }
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getMyReservations(session.userId, currentPage, 10, filter)
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Página ${currentPage + 1} de $totalPages"
                    if (page.content.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.recyclerView.adapter = null
                    } else {
                        binding.recyclerView.adapter = MyReservationAdapter(
                            items = page.content,
                            onView = { showViewReservationDialog(it) },
                            onCancel = { reservation ->
                                AlertDialog.Builder(requireContext())
                                    .setTitle("Cancelar solicitud")
                                    .setMessage("¿Deseas cancelar esta solicitud?")
                                    .setPositiveButton("Sí") { _, _ ->
                                        lifecycleScope.launch {
                                            try {
                                                api.cancelMyReservation(reservation.id, session.userId)
                                                Toast.makeText(requireContext(), "Solicitud cancelada", Toast.LENGTH_SHORT).show()
                                                load()
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    .setNegativeButton("No", null).show()
                            },
                            onEdit = { showEditReservationDialog(it) },
                            onViewRejection = { showRejectionReasonDialog(it) }
                        )
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar solicitudes", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    private fun showViewReservationDialog(r: Reservation) {
        val resourceName = if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
        val resourceType = if (r.resourceType == "SPACE") "Espacio" else "Equipo"
        val statusLabel = mapOf(
            "PENDIENTE" to "Pendiente",
            "APROBADA" to "En préstamo",
            "RECHAZADA" to "Rechazada",
            "CANCELADA" to "Cancelada",
            "DEVUELTA" to "Devuelta"
        )
        val msg = """
            Tipo: $resourceType
            Recurso: ${resourceName ?: "—"}
            Fecha: ${r.reservationDate}
            Horario: ${r.startTime} - ${r.endTime}
            Estado: ${statusLabel[r.status] ?: r.status}
            Motivo: ${r.purpose}
            Observaciones: ${r.observations ?: "—"}
            Comentario admin: ${r.adminComment ?: "—"}
        """.trimIndent()
        AlertDialog.Builder(requireContext())
            .setTitle("Detalle de solicitud #${r.id}")
            .setMessage(msg)
            .setPositiveButton("Cerrar", null).show()
    }

    private fun showRejectionReasonDialog(r: Reservation) {
        val reason = r.adminComment?.takeIf { it.isNotBlank() } ?: "No se proporcionó motivo de rechazo."
        AlertDialog.Builder(requireContext())
            .setTitle("Motivo de rechazo")
            .setMessage(reason)
            .setPositiveButton("Cerrar", null).show()
    }

    private fun showEditReservationDialog(r: Reservation) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }

        fun label(text: String) = TextView(ctx).apply { this.text = text; textSize = 12f; setPadding(0, 12, 0, 2) }

        val etDate = EditText(ctx).apply { hint = "YYYY-MM-DD"; setText(r.reservationDate) }
        val etStart = EditText(ctx).apply { hint = "HH:MM"; setText(r.startTime) }
        val etEnd = EditText(ctx).apply { hint = "HH:MM"; setText(r.endTime) }
        val etPurpose = EditText(ctx).apply { hint = "Motivo de la reserva"; minLines = 3; setText(r.purpose) }
        val etObs = EditText(ctx).apply { hint = "Observaciones (opcional)"; setText(r.observations ?: "") }

        layout.addView(label("Fecha de reserva *")); layout.addView(etDate)
        layout.addView(label("Hora inicio *")); layout.addView(etStart)
        layout.addView(label("Hora fin *")); layout.addView(etEnd)
        layout.addView(label("Motivo *")); layout.addView(etPurpose)
        layout.addView(label("Observaciones")); layout.addView(etObs)

        val sv = ScrollView(ctx).apply { addView(layout) }
        val session = SessionManager(ctx)

        AlertDialog.Builder(ctx)
            .setTitle("Editar solicitud #${r.id}")
            .setView(sv)
            .setPositiveButton("Guardar") { _, _ ->
                val date = etDate.text.toString().trim()
                val start = etStart.text.toString().trim()
                val end = etEnd.text.toString().trim()
                val purpose = etPurpose.text.toString().trim()
                if (date.isEmpty() || start.isEmpty() || end.isEmpty() || purpose.isEmpty()) {
                    Toast.makeText(ctx, "Completa todos los campos requeridos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        val api = RetrofitClient.create(ctx)
                        val req = UpdateReservationRequest(
                            requesterId = session.userId,
                            resourceType = r.resourceType,
                            spaceId = r.spaceId,
                            equipmentId = r.equipmentId,
                            reservationDate = date,
                            startTime = start,
                            endTime = end,
                            purpose = purpose,
                            observations = etObs.text.toString().trim().ifEmpty { null }
                        )
                        val resp = api.updateMyReservation(r.id, session.userId, req)
                        if (resp.isSuccessful) {
                            Toast.makeText(ctx, "Solicitud actualizada", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(ctx, "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class MyReservationAdapter(
    private val items: List<Reservation>,
    private val onView: (Reservation) -> Unit,
    private val onCancel: (Reservation) -> Unit,
    private val onEdit: (Reservation) -> Unit,
    private val onViewRejection: (Reservation) -> Unit
) : RecyclerView.Adapter<MyReservationAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_reservation, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.view.apply {
            val resourceName = if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
            findViewById<TextView>(R.id.tvRequester).text = resourceName ?: "—"
            findViewById<TextView>(R.id.tvResource).text = r.purpose
            findViewById<TextView>(R.id.tvDate).text = "${r.reservationDate} | ${r.startTime} - ${r.endTime}"

            val tvStatus = findViewById<TextView>(R.id.tvStatus)
            val statusLabel = mapOf(
                "PENDIENTE" to "Pendiente",
                "APROBADA" to "En préstamo",
                "RECHAZADA" to "Rechazada",
                "CANCELADA" to "Cancelada",
                "DEVUELTA" to "Devuelta"
            )
            tvStatus.text = statusLabel[r.status] ?: r.status
            tvStatus.setBackgroundColor(when (r.status) {
                "PENDIENTE" -> 0xFFF59E0B.toInt()
                "APROBADA" -> 0xFF10B981.toInt()
                "RECHAZADA" -> 0xFFEF4444.toInt()
                "DEVUELTA" -> 0xFF2563EB.toInt()
                else -> 0xFF6B7280.toInt()
            })

            val layoutActions = findViewById<LinearLayout>(R.id.layoutActions)
            val btnApprove = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApprove)
            val btnReject = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReject)
            val btnReturn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReturn)
            val btnViewDetail = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewDetail)
            val btnEditReservation = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEditReservation)
            val btnViewRejection = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewRejection)

            // Always show view detail
            btnViewDetail.setOnClickListener { onView(r) }

            btnReturn.visibility = View.GONE

            if (r.status == "PENDIENTE") {
                layoutActions.visibility = View.VISIBLE
                btnReject.visibility = View.GONE
                btnApprove.text = "Cancelar"
                btnApprove.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFDC2626.toInt())
                btnApprove.setOnClickListener { onCancel(r) }
                btnEditReservation.visibility = View.VISIBLE
                btnEditReservation.setOnClickListener { onEdit(r) }
                btnViewRejection.visibility = View.GONE
            } else if (r.status == "RECHAZADA") {
                layoutActions.visibility = View.GONE
                btnEditReservation.visibility = View.GONE
                btnViewRejection.visibility = View.VISIBLE
                btnViewRejection.setOnClickListener { onViewRejection(r) }
            } else {
                layoutActions.visibility = View.GONE
                btnEditReservation.visibility = View.GONE
                btnViewRejection.visibility = View.GONE
            }
        }
    }
}
