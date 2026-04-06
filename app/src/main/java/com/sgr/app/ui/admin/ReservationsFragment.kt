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
import com.sgr.app.databinding.FragmentListBinding
import com.sgr.app.model.ApproveRequest
import com.sgr.app.model.Reservation
import com.sgr.app.model.RejectRequest
import com.sgr.app.model.ReturnRequest
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class ReservationsFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var currentPage = 0
    private var totalPages = 1
    private var currentFilter = ""
    private val filters = arrayOf("Todas", "PENDIENTE", "APROBADA", "RECHAZADA", "CANCELADA")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filters)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilter.adapter = adapter
        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                currentFilter = if (pos == 0) "" else filters[pos]
                currentPage = 0
                load()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; load() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; load() } }

        load()
    }

    private fun load() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getReservations(currentPage, 10, currentFilter)
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = ReservationAdapter(page.content) { action, reservation ->
                        when (action) {
                            "approve" -> showApproveDialog(reservation)
                            "reject" -> showRejectDialog(reservation)
                            "return" -> showReturnDialog(reservation)
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
        }
    }
}
