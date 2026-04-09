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
import com.sgr.app.databinding.FragmentEquipmentBinding
import com.sgr.app.model.CreateEquipmentRequest
import com.sgr.app.model.Equipment
import com.sgr.app.model.HistoryItem
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class EquipmentFragment : Fragment() {

    private var _binding: FragmentEquipmentBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private var totalPages = 1

    private val condicionLabels = arrayOf("Todos", "Disponible", "En uso", "Mantenimiento")
    private val condicionValues = arrayOf("", "DISPONIBLE", "EN_USO", "MANTENIMIENTO")

    private val categoriaLabels = arrayOf("Todos", "Audiovisual", "Cómputo", "Laboratorio")
    private val categoriaValues = arrayOf("", "AUDIOVISUAL", "COMPUTO", "LABORATORIO")

    private val accesoLabels = arrayOf("Todos", "Permite alumnos", "Restringido")
    private val accesoValues = arrayOf("", "ALUMNOS", "RESTRINGIDO")

    private var filterCondicion = ""
    private var filterCategoria = ""
    private var filterAcceso = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEquipmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerCondicion, condicionLabels) { pos ->
            if (!isInitializing) {
                filterCondicion = condicionValues[pos]; filterCategoria = ""; filterAcceso = ""; currentPage = 0; load()
            }
        }
        setupSpinner(binding.spinnerCategoria, categoriaLabels) { pos ->
            if (!isInitializing) {
                filterCategoria = categoriaValues[pos]; filterCondicion = ""; filterAcceso = ""; currentPage = 0; load()
            }
        }
        setupSpinner(binding.spinnerAcceso, accesoLabels) { pos ->
            if (!isInitializing) {
                filterAcceso = accesoValues[pos]; filterCondicion = ""; filterCategoria = ""; currentPage = 0; load()
            }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterCondicion = ""; filterCategoria = ""; filterAcceso = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerCondicion.setSelection(0)
            binding.spinnerCategoria.setSelection(0)
            binding.spinnerAcceso.setSelection(0)
            isInitializing = false
            load()
        }

        binding.btnAddEquipment.setOnClickListener { showCreateEquipmentDialog() }

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
            filterCondicion.isNotEmpty() -> filterCondicion
            filterCategoria.isNotEmpty() -> filterCategoria
            filterAcceso.isNotEmpty() -> filterAcceso
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getEquipments(currentPage, 4, backendFilter, "")
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = EquipmentAdapter(
                        items = page.content,
                        onView = { showViewEquipmentDialog(it) },
                        onEdit = { showEditEquipmentDialog(it) },
                        onHistory = { showEquipmentHistory(it) },
                        onToggle = { eq ->
                            lifecycleScope.launch {
                                try { RetrofitClient.create(requireContext()).toggleEquipmentStatus(eq.id); load() }
                                catch (_: Exception) {}
                            }
                        }
                    )
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar equipos", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    private fun buildEquipmentForm(eq: Equipment? = null): Pair<ScrollView, () -> CreateEquipmentRequest?> {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }

        fun label(text: String) = TextView(ctx).apply { this.text = text; textSize = 12f; setPadding(0, 12, 0, 2) }

        val etInvNumber = EditText(ctx).apply { hint = "Número de inventario"; setText(eq?.inventoryNumber ?: "") }
        val etName = EditText(ctx).apply { hint = "Nombre del equipo"; setText(eq?.name ?: "") }
        val etCategory = EditText(ctx).apply { hint = "Categoría (Audiovisual, Cómputo, etc.)"; setText(eq?.category ?: "") }
        val etDescription = EditText(ctx).apply { hint = "Descripción"; minLines = 2; setText(eq?.description ?: "") }

        val condValues = arrayOf("DISPONIBLE", "EN_USO", "MANTENIMIENTO")
        val spinnerCond = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, condValues)
            val idx = condValues.indexOf(eq?.equipmentCondition ?: "DISPONIBLE").coerceAtLeast(0)
            setSelection(idx)
        }
        val cbStudents = CheckBox(ctx).apply { text = "Permite estudiantes"; isChecked = eq?.allowStudents ?: true }
        val cbActive = CheckBox(ctx).apply { text = "Activo"; isChecked = eq?.active ?: true }

        layout.addView(label("Número de inventario *")); layout.addView(etInvNumber)
        layout.addView(label("Nombre *")); layout.addView(etName)
        layout.addView(label("Categoría")); layout.addView(etCategory)
        layout.addView(label("Descripción")); layout.addView(etDescription)
        layout.addView(label("Condición")); layout.addView(spinnerCond)
        layout.addView(cbStudents); layout.addView(cbActive)

        val sv = ScrollView(ctx).apply { addView(layout) }

        val builder: () -> CreateEquipmentRequest? = {
            val inv = etInvNumber.text.toString().trim()
            val name = etName.text.toString().trim()
            if (inv.isEmpty() || name.isEmpty()) {
                Toast.makeText(ctx, "Número de inventario y nombre son requeridos", Toast.LENGTH_SHORT).show(); null
            } else CreateEquipmentRequest(
                inventoryNumber = inv,
                name = name,
                category = etCategory.text.toString().trim(),
                description = etDescription.text.toString().trim(),
                allowStudents = cbStudents.isChecked,
                equipmentCondition = condValues[spinnerCond.selectedItemPosition],
                active = cbActive.isChecked
            )
        }
        return Pair(sv, builder)
    }

    private fun showCreateEquipmentDialog() {
        val (view, buildRequest) = buildEquipmentForm()
        AlertDialog.Builder(requireContext())
            .setTitle("Agregar equipo")
            .setView(view)
            .setPositiveButton("Crear") { _, _ ->
                val req = buildRequest() ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val resp = RetrofitClient.create(requireContext()).createEquipment(req)
                        if (resp.isSuccessful) {
                            Toast.makeText(requireContext(), "Equipo creado", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(requireContext(), "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showEditEquipmentDialog(eq: Equipment) {
        val (view, buildRequest) = buildEquipmentForm(eq)
        AlertDialog.Builder(requireContext())
            .setTitle("Editar equipo")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val req = buildRequest() ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val resp = RetrofitClient.create(requireContext()).updateEquipment(eq.id, req)
                        if (resp.isSuccessful) {
                            Toast.makeText(requireContext(), "Equipo actualizado", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(requireContext(), "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showViewEquipmentDialog(eq: Equipment) {
        val allowStudents = if (eq.allowStudents) "Sí" else "No"
        val active = if (eq.active) "Activo" else "Inactivo"
        val msg = """
            Nombre: ${eq.name}
            No. Inventario: ${eq.inventoryNumber}
            Categoría: ${eq.category}
            Condición: ${eq.equipmentCondition}
            Permite estudiantes: $allowStudents
            Estado: $active
            Descripción: ${eq.description}
        """.trimIndent()
        AlertDialog.Builder(requireContext())
            .setTitle("Detalle del equipo")
            .setMessage(msg)
            .setPositiveButton("Cerrar", null).show()
    }

    private fun showEquipmentHistory(eq: Equipment) {
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.create(requireContext()).getEquipmentHistory(eq.id)
                if (resp.isSuccessful) {
                    val items = resp.body() ?: emptyList()
                    showHistoryDialog("Historial: ${eq.name}", items)
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
        val tv = TextView(requireContext()).apply { text = msg; setPadding(48, 24, 48, 0); textSize = 13f }
        val sv = ScrollView(requireContext()).apply { addView(tv) }
        AlertDialog.Builder(requireContext()).setTitle(title).setView(sv).setPositiveButton("Cerrar", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class EquipmentAdapter(
    private val items: List<Equipment>,
    private val onView: (Equipment) -> Unit,
    private val onEdit: (Equipment) -> Unit,
    private val onHistory: (Equipment) -> Unit,
    private val onToggle: (Equipment) -> Unit
) : RecyclerView.Adapter<EquipmentAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_generic, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.view.apply {
            findViewById<TextView>(R.id.tvTitle).text = e.name
            findViewById<TextView>(R.id.tvSubtitle).text = "${e.category} | No. ${e.inventoryNumber}"
            findViewById<TextView>(R.id.tvDetail).text = e.description
            val badge = findViewById<TextView>(R.id.tvBadge)
            badge.text = e.equipmentCondition
            badge.setBackgroundColor(when (e.equipmentCondition) {
                "DISPONIBLE" -> 0xFF10B981.toInt()
                "EN_USO" -> 0xFFF59E0B.toInt()
                else -> 0xFFEF4444.toInt()
            })
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnView).setOnClickListener { onView(e) }
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEdit).setOnClickListener { onEdit(e) }
            val btnHistory = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHistory)
            btnHistory.visibility = View.VISIBLE
            btnHistory.setOnClickListener { onHistory(e) }
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggle).apply {
                text = if (e.active) "Desactivar" else "Activar"
                setOnClickListener { onToggle(e) }
            }
        }
    }
}
