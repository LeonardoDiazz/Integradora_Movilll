package com.sgr.app.ui.admin

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentUsersBinding
import com.sgr.app.model.User
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class UsersFragment : Fragment() {

    private var _binding: FragmentUsersBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private var totalPages = 1

    private val rolesLabels = arrayOf("Todos", "Administrador", "Usuario solicitante")
    private val rolesValues = arrayOf("", "ADMIN", "STUDENTS")

    private val tiposLabels = arrayOf("Todos", "Estudiante", "Personal")
    private val tiposValues = arrayOf("", "ESTUDIANTE", "STAFF")

    private val estadosLabels = arrayOf("Todos", "Activo", "Inactivo")
    private val estadosValues = arrayOf("", "ACTIVE", "INACTIVE")

    private var filterRol = ""
    private var filterTipo = ""
    private var filterEstado = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        // El título detallado está en el layout
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerRol, rolesLabels) { pos ->
            if (!isInitializing) { filterRol = rolesValues[pos]; filterTipo = ""; filterEstado = ""; currentPage = 0; load() }
        }
        setupSpinner(binding.spinnerTipo, tiposLabels) { pos ->
            if (!isInitializing) { filterTipo = tiposValues[pos]; filterRol = ""; filterEstado = ""; currentPage = 0; load() }
        }
        setupSpinner(binding.spinnerEstado, estadosLabels) { pos ->
            if (!isInitializing) { filterEstado = estadosValues[pos]; filterRol = ""; filterTipo = ""; currentPage = 0; load() }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterRol = ""; filterTipo = ""; filterEstado = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerRol.setSelection(0)
            binding.spinnerTipo.setSelection(0)
            binding.spinnerEstado.setSelection(0)
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
        val filter = when {
            filterEstado.isNotEmpty() -> filterEstado
            filterRol.isNotEmpty() -> filterRol
            filterTipo.isNotEmpty() -> filterTipo
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getUsers(currentPage, 10, filter, "")
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = UserAdapter(page.content) { user ->
                        lifecycleScope.launch {
                            try { RetrofitClient.create(requireContext()).toggleUserStatus(user.id); load() }
                            catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar usuarios", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class UserAdapter(
    private val items: List<User>,
    private val onToggle: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_generic, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.view.apply {
            val rolEs = when (u.role) {
                "ADMIN" -> "Administrador"
                else -> "Usuario solicitante"
            }
            findViewById<TextView>(R.id.tvTitle).text = "${u.name} ${u.lastName}"
            findViewById<TextView>(R.id.tvSubtitle).text = u.email
            findViewById<TextView>(R.id.tvDetail).text = "Rol: $rolEs\nID: ${u.identifier ?: "—"}"

            val badge = findViewById<TextView>(R.id.tvBadge)
            badge.text = if (u.active) "ACTIVO" else "INACTIVO"
            badge.setBackgroundColor(if (u.active) 0xFF10B981.toInt() else 0xFFEF4444.toInt())

            val btnToggle = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggle)
            btnToggle.text = if (u.active) "Desactivar" else "Activar"
            btnToggle.setOnClickListener {
                onToggle(u)
                val nowActive = !u.active
                badge.text = if (nowActive) "ACTIVO" else "INACTIVO"
                badge.setBackgroundColor(if (nowActive) 0xFF10B981.toInt() else 0xFFEF4444.toInt())
                btnToggle.text = if (nowActive) "Desactivar" else "Activar"
            }
        }
    }
}
