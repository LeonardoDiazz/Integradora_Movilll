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
import com.sgr.app.databinding.FragmentUsersBinding
import com.sgr.app.model.CreateUserRequest
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

        binding.btnAddUser.setOnClickListener { showCreateUserDialog() }

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
                val response = api.getUsers(currentPage, 4, filter, "")
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = UserAdapter(
                        items = page.content,
                        onView = { showViewUserDialog(it) },
                        onEdit = { showEditUserDialog(it) },
                        onToggle = { user ->
                            lifecycleScope.launch {
                                try { RetrofitClient.create(requireContext()).toggleUserStatus(user.id); load() }
                                catch (_: Exception) {}
                            }
                        }
                    )
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar usuarios", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showUserFormDialog(user: User? = null) {
        val isCreate = user == null
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_user_form, null)

        // Título
        dialogView.findViewById<TextView>(R.id.tvFormTitle).text =
            if (isCreate) "Nuevo Usuario" else "Editar Usuario"

        // Campos
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etName)
        val etLastName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etLastName)
        val etBirthDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBirthDate)
        val etIdentifier = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIdentifier)
        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail)
        val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPhone)
        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)

        // Pre-llenar si es edición
        user?.let {
            etName.setText(it.name)
            etLastName.setText(it.lastName)
            etBirthDate.setText(it.birthDate ?: "")
            etIdentifier.setText(it.identifier ?: "")
            etEmail.setText(it.email)
            etPhone.setText(it.phone ?: "")
        }

        // Dropdowns de Rol
        val roleValues = arrayOf("STUDENTS", "ADMIN")
        val roleLabels = arrayOf("Solicitante", "Administrador")
        val actvRole = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerRole)
        val roleAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, roleLabels)
        actvRole.setAdapter(roleAdapter)
        val roleIdx = roleValues.indexOf(user?.role ?: "STUDENTS").coerceAtLeast(0)
        actvRole.setText(roleLabels[roleIdx], false)

        // Dropdowns de Tipo
        val userTypeValues = arrayOf("ESTUDIANTE", "STAFF")
        val userTypeLabels = arrayOf("Estudiante", "Personal Académico")
        val actvUserType = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerUserType)
        val typeAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, userTypeLabels)
        actvUserType.setAdapter(typeAdapter)
        val typeIdx = userTypeValues.indexOf(user?.userType ?: "ESTUDIANTE").coerceAtLeast(0)
        actvUserType.setText(userTypeLabels[typeIdx], false)

        // Diálogo pantalla completa
        val dialog = AlertDialog.Builder(ctx, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(dialogView)
            .create()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        dialogView.findViewById<TextView>(R.id.btnFormClose).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFormCancel).setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFormSave).setOnClickListener {
            val name = etName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val selectedRole = roleValues[roleLabels.indexOf(actvRole.text.toString()).coerceAtLeast(0)]
            val selectedType = userTypeValues[userTypeLabels.indexOf(actvUserType.text.toString()).coerceAtLeast(0)]

            if (name.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
                Toast.makeText(ctx, "Nombre, apellidos y correo son requeridos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isCreate && password.isEmpty()) {
                Toast.makeText(ctx, "La contraseña es requerida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val req = CreateUserRequest(
                name = name, lastName = lastName, email = email,
                identifier = etIdentifier.text.toString().trim(),
                password = password.ifEmpty { "NO_CHANGE" },
                role = selectedRole, active = true,
                userType = selectedType,
                birthDate = etBirthDate.text.toString().trim().ifEmpty { null },
                phone = etPhone.text.toString().trim().ifEmpty { null }
            )

            lifecycleScope.launch {
                try {
                    val api = RetrofitClient.create(ctx)
                    val resp = if (isCreate) api.createUser(req) else api.updateUser(user!!.id, req)
                    if (resp.isSuccessful) {
                        Toast.makeText(ctx, if (isCreate) "Usuario creado" else "Usuario actualizado", Toast.LENGTH_SHORT).show()
                        dialog.dismiss(); load()
                    } else Toast.makeText(ctx, "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show() }
            }
        }

        dialog.show()
    }

    private fun showCreateUserDialog() = showUserFormDialog(null)
    private fun showEditUserDialog(user: User) = showUserFormDialog(user)

    private fun showViewUserDialog(user: User) {
        val ctx = requireContext()
        val rolLabel = if (user.role == "ADMIN") "Administrador" else "Solicitante"
        val tipoLabel = when (user.userType) {
            "ESTUDIANTE" -> "Estudiante"
            "STAFF" -> "Personal"
            else -> "—"
        }
        val activeLabel = if (user.active) "Activo" else "Inactivo"

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        fun field(label: String, value: String) {
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFFF8FAFC.toInt())
                    cornerRadius = 12f * ctx.resources.displayMetrics.density
                }
                setPadding(24, 14, 24, 14)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (8 * ctx.resources.displayMetrics.density).toInt()
                layoutParams = lp
            }
            val tvLabel = TextView(ctx).apply {
                text = label.uppercase()
                textSize = 10f
                setTextColor(0xFF6B7280.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val tvValue = TextView(ctx).apply {
                text = value
                textSize = 14f
                setTextColor(0xFF111827.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 4, 0, 0)
            }
            container.addView(tvLabel)
            container.addView(tvValue)
            root.addView(container)
        }

        // Fila doble (2 columnas)
        fun rowOf2(label1: String, val1: String, label2: String, val2: String) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (8 * ctx.resources.displayMetrics.density).toInt()
                layoutParams = lp
            }
            fun cell(label: String, value: String): LinearLayout {
                val container = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFFF8FAFC.toInt())
                        cornerRadius = 12f * ctx.resources.displayMetrics.density
                    }
                    setPadding(24, 14, 24, 14)
                    val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lp2
                }
                container.addView(TextView(ctx).apply {
                    text = label.uppercase(); textSize = 10f
                    setTextColor(0xFF6B7280.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
                container.addView(TextView(ctx).apply {
                    text = value; textSize = 14f
                    setTextColor(0xFF111827.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(0, 4, 0, 0)
                })
                return container
            }
            val cell1 = cell(label1, val1)
            val cell2 = cell(label2, val2)
            val gapLp = LinearLayout.LayoutParams((8 * ctx.resources.displayMetrics.density).toInt(), 1)
            row.addView(cell1)
            row.addView(android.view.View(ctx).apply { layoutParams = gapLp })
            row.addView(cell2)
            root.addView(row)
        }

        rowOf2("Nombre", user.name, "Apellidos", user.lastName)
        rowOf2("Fecha de nacimiento", user.birthDate ?: "—", "Rol", rolLabel)
        rowOf2("Tipo de usuario", tipoLabel, "Matrícula / ID", user.identifier ?: "—")
        rowOf2("Correo", user.email, "Teléfono", user.phone ?: "—")
        field("Estado", activeLabel)

        scroll.addView(root)

        AlertDialog.Builder(ctx)
            .setTitle("Detalle de Usuario")
            .setView(scroll)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class UserAdapter(
    private val items: List<User>,
    private val onView: (User) -> Unit,
    private val onEdit: (User) -> Unit,
    private val onToggle: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.view.apply {
            // Avatar con inicial
            val initial = u.name.firstOrNull()?.uppercase() ?: "?"
            findViewById<TextView>(R.id.tvAvatar).text = initial

            // Nombre y matrícula
            findViewById<TextView>(R.id.tvName).text = "${u.name} ${u.lastName}"
            findViewById<TextView>(R.id.tvIdentifier).text = "Matrícula: ${u.identifier ?: "—"}"

            // Correo
            findViewById<TextView>(R.id.tvEmail).text = u.email

            // Estado badge
            val tvStatus = findViewById<TextView>(R.id.tvStatus)
            tvStatus.text = if (u.active) "Activo" else "Inactivo"
            tvStatus.setBackgroundColor(if (u.active) 0xFF10B981.toInt() else 0xFFEF4444.toInt())

            // Rol badge
            val isAdmin = u.role == "ADMIN"
            val tvRole = findViewById<TextView>(R.id.tvRole)
            tvRole.text = if (isAdmin) "Administrador" else "Usuario solicitante"
            tvRole.setBackgroundColor(if (isAdmin) 0xFF7C3AED.toInt() else 0xFF2563EB.toInt())

            // Botones
            findViewById<android.widget.ImageButton>(R.id.btnView).setOnClickListener { onView(u) }
            findViewById<android.widget.ImageButton>(R.id.btnEdit).setOnClickListener { onEdit(u) }
            findViewById<android.widget.ImageButton>(R.id.btnToggle).setOnClickListener { onToggle(u) }
        }
    }
}
