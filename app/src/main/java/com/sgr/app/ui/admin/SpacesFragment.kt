package com.sgr.app.ui.admin

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
                val response = api.getSpaces(currentPage, 4, backendFilter, "")
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
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_space_detail, null)
        view.findViewById<TextView>(R.id.tvDetailName).text = space.name
        view.findViewById<TextView>(R.id.tvDetailCategory).text = space.category.ifBlank { "—" }
        view.findViewById<TextView>(R.id.tvDetailLocation).text = space.location.ifBlank { "—" }
        view.findViewById<TextView>(R.id.tvDetailCapacity).text = "${space.capacity} personas"
        view.findViewById<TextView>(R.id.tvDetailDescription).text = space.description.ifBlank { "—" }
        view.findViewById<TextView>(R.id.tvDetailStudents).text = if (space.allowStudents == true) "Permitido" else "Restringido"
        view.findViewById<TextView>(R.id.tvDetailStatus).text = if (space.active == true) "Activo" else "Inactivo"
        view.findViewById<TextView>(R.id.tvDetailAvailability).text = when (space.availability) {
            "DISPONIBLE" -> "Disponible"
            "OCUPADO" -> "Ocupado"
            "MANTENIMIENTO" -> "Mantenimiento"
            else -> space.availability ?: "—"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Detalle de Espacio")
            .setView(view)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showSpaceHistory(space: Space) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_space_history, null)
        view.findViewById<TextView>(R.id.tvHistorySpaceName).text = "🏢 ${space.name.uppercase()}"
        // Mostrar estado vacío por defecto mientras carga
        view.findViewById<View>(R.id.layoutEmpty).visibility = View.VISIBLE

        AlertDialog.Builder(requireContext())
            .setTitle("Historial de Reservas")
            .setView(view)
            .setPositiveButton("Cerrar", null)
            .show()

        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.create(requireContext()).getSpaceHistory(space.id)
                if (resp.isSuccessful) {
                    val items = resp.body() ?: emptyList()
                    val layoutEmpty = view.findViewById<View>(R.id.layoutEmpty)
                    val rvHistory = view.findViewById<RecyclerView>(R.id.rvHistory)
                    if (items.isEmpty()) {
                        layoutEmpty.visibility = View.VISIBLE
                        rvHistory.visibility = View.GONE
                    } else {
                        layoutEmpty.visibility = View.GONE
                        rvHistory.visibility = View.VISIBLE
                        rvHistory.layoutManager = LinearLayoutManager(requireContext())
                        rvHistory.adapter = HistoryAdapter(items)
                    }
                } else {
                    Toast.makeText(requireContext(), "Error al cargar historial", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
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
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_space, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.view.apply {
            findViewById<TextView>(R.id.tvSpaceName).text = s.name
            findViewById<TextView>(R.id.tvCategory).text = s.category.ifBlank { "—" }
            findViewById<TextView>(R.id.tvLocation).text = s.location.ifBlank { "—" }
            findViewById<TextView>(R.id.tvCapacity).text = s.capacity.toString()

            // Badge ESTADO
            val badgeEstado = findViewById<TextView>(R.id.tvBadgeEstado)
            if (s.active == true) {
                badgeEstado.text = "Activo"
                badgeEstado.setBackgroundResource(R.drawable.bg_badge_green)
                badgeEstado.setTextColor(0xFF065F46.toInt())
            } else {
                badgeEstado.text = "Inactivo"
                badgeEstado.setBackgroundResource(R.drawable.bg_badge_red)
                badgeEstado.setTextColor(0xFF991B1B.toInt())
            }

            // Badge DISPONIBILIDAD
            val badgeDisp = findViewById<TextView>(R.id.tvBadgeDisp)
            when (s.availability ?: "") {
                "DISPONIBLE" -> {
                    badgeDisp.text = "Disponible"
                    badgeDisp.setBackgroundResource(R.drawable.bg_badge_green)
                    badgeDisp.setTextColor(0xFF065F46.toInt())
                }
                "OCUPADO" -> {
                    badgeDisp.text = "Ocupado"
                    badgeDisp.setBackgroundResource(R.drawable.bg_badge_red)
                    badgeDisp.setTextColor(0xFF991B1B.toInt())
                }
                else -> {
                    badgeDisp.text = "Mantenimiento"
                    badgeDisp.setBackgroundResource(R.drawable.bg_badge_yellow)
                    badgeDisp.setTextColor(0xFF92400E.toInt())
                }
            }

            findViewById<ImageButton>(R.id.btnView).setOnClickListener { onView(s) }
            findViewById<ImageButton>(R.id.btnEdit).setOnClickListener { onEdit(s) }
            findViewById<ImageButton>(R.id.btnHistory).setOnClickListener { onHistory(s) }
            findViewById<ImageButton>(R.id.btnToggle).setOnClickListener { onToggle(s) }
        }
    }
}

class HistoryAdapter(
    private val items: List<HistoryItem>
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val h = items[position]
        holder.view.apply {
            findViewById<TextView>(R.id.tvHistoryAction).text = h.action ?: "Acción"
            findViewById<TextView>(R.id.tvHistoryDate).text = h.changedAt?.take(10) ?: "—"
            findViewById<TextView>(R.id.tvHistoryBy).text = "Por: ${h.changedBy ?: "—"}"
            val tvDetails = findViewById<TextView>(R.id.tvHistoryDetails)
            if (h.details.isNullOrBlank()) {
                tvDetails.visibility = View.GONE
            } else {
                tvDetails.visibility = View.VISIBLE
                tvDetails.text = h.details
            }
        }
    }
}
