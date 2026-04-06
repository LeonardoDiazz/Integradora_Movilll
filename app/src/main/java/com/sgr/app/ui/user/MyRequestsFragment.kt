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
import com.sgr.app.databinding.FragmentListBinding
import com.sgr.app.model.Reservation
import com.sgr.app.network.RetrofitClient
import com.sgr.app.utils.SessionManager
import kotlinx.coroutines.launch

class MyRequestsFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var currentPage = 0
    private var totalPages = 1
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
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { currentPage = 0; load(if (pos == 0) "" else filters[pos]) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; load() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; load() } }
        load()
    }

    private fun load(filter: String = "") {
        val session = SessionManager(requireContext())
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getMyReservations(session.userId, currentPage, 10, filter)
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = MyReservationAdapter(page.content) { reservation ->
                        AlertDialog.Builder(requireContext())
                            .setTitle("Cancelar solicitud")
                            .setMessage("¿Deseas cancelar esta solicitud?")
                            .setPositiveButton("Sí") { _, _ ->
                                lifecycleScope.launch {
                                    try {
                                        api.cancelMyReservation(reservation.id, session.userId)
                                        Toast.makeText(requireContext(), "Solicitud cancelada", Toast.LENGTH_SHORT).show()
                                        load(filter)
                                    } catch (_: Exception) {}
                                }
                            }
                            .setNegativeButton("No", null)
                            .show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar solicitudes", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class MyReservationAdapter(
    private val items: List<Reservation>,
    private val onCancel: (Reservation) -> Unit
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
            tvStatus.text = r.status
            tvStatus.setBackgroundColor(when (r.status) {
                "PENDIENTE" -> 0xFFF59E0B.toInt()
                "APROBADA" -> 0xFF10B981.toInt()
                "RECHAZADA" -> 0xFFEF4444.toInt()
                else -> 0xFF6B7280.toInt()
            })

            val layoutActions = findViewById<LinearLayout>(R.id.layoutActions)
            val btnApprove = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApprove)
            val btnReject = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReject)
            val btnReturn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReturn)

            if (r.status == "PENDIENTE") {
                layoutActions.visibility = View.VISIBLE
                btnReturn.visibility = View.GONE
                btnApprove.text = "Cancelar"
                btnApprove.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF6B7280.toInt())
                btnApprove.setOnClickListener { onCancel(r) }
                btnReject.visibility = View.GONE
            } else {
                layoutActions.visibility = View.GONE
                btnReturn.visibility = View.GONE
            }
        }
    }
}
