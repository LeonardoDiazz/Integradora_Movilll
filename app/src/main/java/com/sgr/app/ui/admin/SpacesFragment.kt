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
import com.sgr.app.databinding.FragmentSpacesBinding
import com.sgr.app.model.CreateSpaceRequest
import com.sgr.app.model.HistoryItem
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

        binding.btnAddSpace.setOnClickListener { showCreateSpaceDialog() }

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
                    binding.recyclerView.adapter = SpaceAdapter(
                        items = page.content,
                        onView = { showViewSpaceDialog(it) },
                        onEdit = { showEditSpaceDialog(it) },
                        onHistory = { showSpaceHistory(it) },
                        onToggle = { space ->
                            lifecycleScope.launch {
                                try { RetrofitClient.create(requireContext()).toggleSpaceStatus(space.id); load() }
                                catch (_: Exception) {}
                            }
                        }
                    )
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar espacios", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    private fun buildSpaceForm(space: Space? = null): Pair<ScrollView, () -> CreateSpaceRequest?> {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }

        fun label(text: String) = TextView(ctx).apply { this.text = text; textSize = 12f; setPadding(0, 12, 0, 2) }

        val etName = EditText(ctx).apply { hint = "Nombre del espacio"; setText(space?.name ?: "") }
        val etCategory = EditText(ctx).apply { hint = "Categoría (Aula, Laboratorio, etc.)"; setText(space?.category ?: "") }
        val etLocation = EditText(ctx).apply { hint = "Ubicación"; setText(space?.location ?: "") }
        val etCapacity = EditText(ctx).apply { hint = "Capacidad"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(space?.capacity?.toString() ?: "") }
        val etDescription = EditText(ctx).apply { hint = "Descripción"; minLines = 2; setText(space?.description ?: "") }

        val availValues = arrayOf("DISPONIBLE", "OCUPADO", "MANTENIMIENTO")
        val spinnerAvail = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, availValues)
            val idx = availValues.indexOf(space?.availability ?: "DISPONIBLE").coerceAtLeast(0)
            setSelection(idx)
        }
        val cbStudents = CheckBox(ctx).apply { text = "Permite estudiantes"; isChecked = space?.allowStudents ?: true }
        val cbActive = CheckBox(ctx).apply { text = "Activo"; isChecked = space?.active ?: true }

        layout.addView(label("Nombre *")); layout.addView(etName)
        layout.addView(label("Categoría")); layout.addView(etCategory)
        layout.addView(label("Ubicación")); layout.addView(etLocation)
        layout.addView(label("Capacidad")); layout.addView(etCapacity)
        layout.addView(label("Descripción")); layout.addView(etDescription)
        layout.addView(label("Disponibilidad")); layout.addView(spinnerAvail)
        layout.addView(cbStudents); layout.addView(cbActive)

        val sv = ScrollView(ctx).apply { addView(layout) }

        val builder: () -> CreateSpaceRequest? = {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) { Toast.makeText(ctx, "El nombre es requerido", Toast.LENGTH_SHORT).show(); null }
            else CreateSpaceRequest(
                name = name,
                category = etCategory.text.toString().trim(),
                location = etLocation.text.toString().trim(),
                capacity = etCapacity.text.toString().toIntOrNull() ?: 0,
                description = etDescription.text.toString().trim(),
                allowStudents = cbStudents.isChecked,
                availability = availValues[spinnerAvail.selectedItemPosition],
                active = cbActive.isChecked
            )
        }
        return Pair(sv, builder)
    }

    private fun showCreateSpaceDialog() {
        val (view, buildRequest) = buildSpaceForm()
        AlertDialog.Builder(requireContext())
            .setTitle("Crear espacio")
            .setView(view)
            .setPositiveButton("Crear") { _, _ ->
                val req = buildRequest() ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val resp = RetrofitClient.create(requireContext()).createSpace(req)
                        if (resp.isSuccessful) {
                            Toast.makeText(requireContext(), "Espacio creado", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(requireContext(), "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showEditSpaceDialog(space: Space) {
        val (view, buildRequest) = buildSpaceForm(space)
        AlertDialog.Builder(requireContext())
            .setTitle("Editar espacio")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val req = buildRequest() ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val resp = RetrofitClient.create(requireContext()).updateSpace(space.id, req)
                        if (resp.isSuccessful) {
                            Toast.makeText(requireContext(), "Espacio actualizado", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(requireContext(), "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showViewSpaceDialog(space: Space) {
        val allowStudents = if (space.allowStudents) "Sí" else "No"
        val active = if (space.active) "Activo" else "Inactivo"
        val msg = """
            Nombre: ${space.name}
            Categoría: ${space.category}
            Ubicación: ${space.location}
            Capacidad: ${space.capacity}
            Disponibilidad: ${space.availability}
            Permite estudiantes: $allowStudents
            Estado: $active
            Descripción: ${space.description}
        """.trimIndent()
        AlertDialog.Builder(requireContext())
            .setTitle("Detalle del espacio")
            .setMessage(msg)
            .setPositiveButton("Cerrar", null).show()
    }

    private fun showSpaceHistory(space: Space) {
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.create(requireContext()).getSpaceHistory(space.id)
                if (resp.isSuccessful) {
                    val items = resp.body() ?: emptyList()
                    showHistoryDialog("Historial: ${space.name}", items)
                } else Toast.makeText(requireContext(), "Error al cargar historial", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showHistoryDialog(title: String, items: List<HistoryItem>) {
        if (items.isEmpty()) {
            AlertDialog.Builder(requireContext()).setTitle(title).setMessage("Sin historial registrado.").setPositiveButton("Cerrar", null).show()
            return
        }
        val msg = items.joinToString("\n\n") { h ->
            "• ${h.action ?: "Acción"}\n  Por: ${h.changedBy ?: "—"}\n  ${h.changedAt ?: ""}\n  ${h.details ?: ""}"
        }
        val tv = TextView(requireContext()).apply {
            text = msg; setPadding(48, 24, 48, 0); textSize = 13f
        }
        val sv = ScrollView(requireContext()).apply { addView(tv) }
        AlertDialog.Builder(requireContext()).setTitle(title).setView(sv).setPositiveButton("Cerrar", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class SpaceAdapter(
    private val items: List<Space>,
    private val onView: (Space) -> Unit,
    private val onEdit: (Space) -> Unit,
    private val onHistory: (Space) -> Unit,
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
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnView).setOnClickListener { onView(s) }
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEdit).setOnClickListener { onEdit(s) }
            val btnHistory = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHistory)
            btnHistory.visibility = View.VISIBLE
            btnHistory.setOnClickListener { onHistory(s) }
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggle).apply {
                text = if (s.active) "Desactivar" else "Activar"
                setOnClickListener { onToggle(s) }
            }
        }
    }
}
