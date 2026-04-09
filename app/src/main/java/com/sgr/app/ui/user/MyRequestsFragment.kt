package com.sgr.app.ui.user

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import android.widget.ImageButton
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
                            onCancel = { showCancelConfirmDialog(it, session) },
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

    private fun statusLabel(status: String) = when (status) {
        "PENDIENTE" -> "Pendiente"
        "APROBADA" -> "En préstamo"
        "RECHAZADA" -> "Rechazada"
        "CANCELADA" -> "Cancelada"
        "DEVUELTA" -> "Devuelta"
        else -> status
    }

    private fun showViewReservationDialog(r: Reservation) {
        try {
            val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reservation_detail, null)
            val resourceName = if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
            val resourceType = if (r.resourceType == "SPACE") "Espacio" else "Equipo"

            view.findViewById<TextView>(R.id.tvDetailId).text = "#${r.id}"
            view.findViewById<TextView>(R.id.tvDetailStatus).text = statusLabel(r.status ?: "")
            view.findViewById<TextView>(R.id.tvDetailResource).text = resourceName ?: "—"
            view.findViewById<TextView>(R.id.tvDetailType).text = resourceType
            view.findViewById<TextView>(R.id.tvDetailDate).text = r.reservationDate ?: "—"
            view.findViewById<TextView>(R.id.tvDetailStartTime).text = r.startTime ?: "—"
            view.findViewById<TextView>(R.id.tvDetailEndDate).text = r.reservationDate ?: "—"
            view.findViewById<TextView>(R.id.tvDetailEndTime).text = r.endTime ?: "—"
            view.findViewById<TextView>(R.id.tvDetailPurpose).text = r.purpose?.ifBlank { "—" } ?: "—"

            val adminComment = r.adminComment?.takeIf { it.isNotBlank() }
            val layoutAdmin = view.findViewById<View>(R.id.layoutAdminComment)
            if (adminComment != null) {
                layoutAdmin.visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.tvDetailAdminComment).text = adminComment
            } else {
                layoutAdmin.visibility = View.GONE
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Detalles de la Solicitud")
                .setView(view)
                .setPositiveButton("Cerrar", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al mostrar detalle", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRejectionReasonDialog(r: Reservation) {
        val reason = r.adminComment?.takeIf { it.isNotBlank() } ?: "No se proporcionó motivo de rechazo."
        AlertDialog.Builder(requireContext())
            .setTitle("Motivo de rechazo")
            .setMessage(reason)
            .setPositiveButton("Cerrar", null).show()
    }

    private fun showCancelConfirmDialog(r: Reservation, session: SessionManager) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_cancel_confirm, null)
        val resourceName = if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
        view.findViewById<TextView>(R.id.tvCancelQuestion).text =
            "¿Estás seguro que deseas cancelar ${resourceName ?: "esta solicitud"}?"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnKeep)
            .setOnClickListener { dialog.dismiss() }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmCancel)
            .setOnClickListener {
                dialog.dismiss()
                lifecycleScope.launch {
                    try {
                        RetrofitClient.create(requireContext()).cancelMyReservation(r.id, session.userId)
                        Toast.makeText(requireContext(), "Solicitud cancelada", Toast.LENGTH_SHORT).show()
                        load()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        dialog.show()
    }

    private fun showEditReservationDialog(r: Reservation) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reservation_edit, null)
        val resourceName = if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
        val resourceType = if (r.resourceType == "SPACE") "Espacio" else "Equipo"

        view.findViewById<TextView>(R.id.tvEditResource).text = resourceName ?: "—"
        view.findViewById<TextView>(R.id.tvEditType).text = resourceType

        val etStartDate = view.findViewById<EditText>(R.id.etStartDate).apply { setText(r.reservationDate ?: "") }
        val etStartTime = view.findViewById<EditText>(R.id.etStartTime).apply { setText(r.startTime ?: "") }
        val etEndDate = view.findViewById<EditText>(R.id.etEndDate).apply { setText(r.reservationDate ?: "") }
        val etEndTime = view.findViewById<EditText>(R.id.etEndTime).apply { setText(r.endTime ?: "") }
        val etPurpose = view.findViewById<EditText>(R.id.etPurpose).apply { setText(r.purpose ?: "") }

        val session = SessionManager(requireContext())

        AlertDialog.Builder(requireContext())
            .setTitle("Modificar Solicitud")
            .setView(view)
            .setPositiveButton("Guardar Cambios") { _, _ ->
                val date = etStartDate.text.toString().trim()
                val start = etStartTime.text.toString().trim()
                val end = etEndTime.text.toString().trim()
                val purpose = etPurpose.text.toString().trim()
                if (date.isEmpty() || start.isEmpty() || end.isEmpty() || purpose.isEmpty()) {
                    Toast.makeText(requireContext(), "Completa todos los campos requeridos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        val req = UpdateReservationRequest(
                            requesterId = session.userId,
                            resourceType = r.resourceType,
                            spaceId = r.spaceId,
                            equipmentId = r.equipmentId,
                            reservationDate = date,
                            startTime = start,
                            endTime = end,
                            purpose = purpose,
                            observations = r.observations
                        )
                        val resp = RetrofitClient.create(requireContext()).updateMyReservation(r.id, session.userId, req)
                        if (resp.isSuccessful) {
                            Toast.makeText(requireContext(), "Solicitud actualizada", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(requireContext(), "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
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
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_my_reservation, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.view.apply {
            val resourceName = if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
            val resourceType = if (r.resourceType == "SPACE") "Espacio" else "Equipo"

            findViewById<TextView>(R.id.tvId).text = "#${r.id}"
            findViewById<TextView>(R.id.tvResourceName).text = resourceName ?: "—"
            findViewById<TextView>(R.id.tvType).text = resourceType
            findViewById<TextView>(R.id.tvDate).text = r.reservationDate ?: "—"
            findViewById<TextView>(R.id.tvSchedule).text = "${r.startTime ?: "—"} - ${r.endTime ?: "—"}"

            // Badge estado
            val tvStatus = findViewById<TextView>(R.id.tvStatus)
            tvStatus.text = when (r.status) {
                "PENDIENTE" -> "Pendiente"
                "APROBADA" -> "En préstamo"
                "RECHAZADA" -> "Rechazada"
                "CANCELADA" -> "Cancelada"
                "DEVUELTA" -> "Devuelta"
                else -> r.status
            }
            when (r.status) {
                "PENDIENTE" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_yellow); tvStatus.setTextColor(0xFF92400E.toInt()) }
                "APROBADA" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_green); tvStatus.setTextColor(0xFF065F46.toInt()) }
                "RECHAZADA" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_red); tvStatus.setTextColor(0xFF991B1B.toInt()) }
                "DEVUELTA" -> { tvStatus.setBackgroundResource(R.drawable.bg_icon_btn); tvStatus.setTextColor(0xFF1D4ED8.toInt()) }
                else -> { tvStatus.setBackgroundResource(R.drawable.bg_detail_card); tvStatus.setTextColor(0xFF6B7280.toInt()) }
            }

            // Botones
            val btnView = findViewById<ImageButton>(R.id.btnView)
            val btnEdit = findViewById<ImageButton>(R.id.btnEdit)
            val btnCancel = findViewById<ImageButton>(R.id.btnCancel)
            val btnRejection = findViewById<ImageButton>(R.id.btnRejection)

            btnView.setOnClickListener { onView(r) }

            if (r.status == "PENDIENTE") {
                btnEdit.alpha = 1f; btnEdit.isEnabled = true
                btnCancel.alpha = 1f; btnCancel.isEnabled = true
                btnEdit.setOnClickListener { onEdit(r) }
                btnCancel.setOnClickListener { onCancel(r) }
                btnRejection.visibility = View.GONE
            } else if (r.status == "RECHAZADA") {
                btnEdit.alpha = 0.3f; btnEdit.isEnabled = false
                btnCancel.alpha = 0.3f; btnCancel.isEnabled = false
                btnRejection.visibility = View.VISIBLE
                btnRejection.setOnClickListener { onViewRejection(r) }
            } else {
                btnEdit.alpha = 0.3f; btnEdit.isEnabled = false
                btnCancel.alpha = 0.3f; btnCancel.isEnabled = false
                btnRejection.visibility = View.GONE
            }
        }
    }
}
