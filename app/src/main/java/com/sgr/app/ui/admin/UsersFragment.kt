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
                val response = api.getUsers(currentPage, 10, filter, "")
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

    private fun buildUserForm(user: User? = null, isCreate: Boolean = true): Pair<ScrollView, () -> CreateUserRequest?> {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }

        fun label(text: String) = TextView(ctx).apply { this.text = text; textSize = 12f; setPadding(0, 12, 0, 2) }

        val etName = EditText(ctx).apply { hint = "Nombre"; setText(user?.name ?: "") }
        val etLastName = EditText(ctx).apply { hint = "Apellido"; setText(user?.lastName ?: "") }
        val etEmail = EditText(ctx).apply { hint = "Correo electrónico"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; setText(user?.email ?: "") }
        val etIdentifier = EditText(ctx).apply { hint = "Matrícula / ID"; setText(user?.identifier ?: "") }
        val etPhone = EditText(ctx).apply { hint = "Teléfono (opcional)"; inputType = android.text.InputType.TYPE_CLASS_PHONE; setText(user?.phone ?: "") }
        val etBirthDate = EditText(ctx).apply { hint = "Fecha nacimiento (YYYY-MM-DD)"; setText(user?.birthDate ?: "") }

        val roleValues = arrayOf("STUDENTS", "ADMIN")
        val roleLabels = arrayOf("Usuario solicitante", "Administrador")
        val spinnerRole = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, roleLabels)
            val idx = roleValues.indexOf(user?.role ?: "STUDENTS").coerceAtLeast(0)
            setSelection(idx)
        }

        val userTypeValues = arrayOf("ESTUDIANTE", "STAFF")
        val spinnerUserType = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, userTypeValues)
            val idx = userTypeValues.indexOf(user?.userType ?: "ESTUDIANTE").coerceAtLeast(0)
            setSelection(idx)
        }

        val cbActive = CheckBox(ctx).apply { text = "Activo"; isChecked = user?.active ?: true }

        layout.addView(label("Nombre *")); layout.addView(etName)
        layout.addView(label("Apellido *")); layout.addView(etLastName)
        layout.addView(label("Correo *")); layout.addView(etEmail)
        layout.addView(label("Matrícula / ID")); layout.addView(etIdentifier)
        layout.addView(label("Teléfono")); layout.addView(etPhone)
        layout.addView(label("Fecha de nacimiento")); layout.addView(etBirthDate)
        layout.addView(label("Rol")); layout.addView(spinnerRole)
        layout.addView(label("Tipo de usuario")); layout.addView(spinnerUserType)
        layout.addView(cbActive)

        // Password only shown for create, or optionally for edit
        val etPassword = EditText(ctx).apply {
            hint = if (isCreate) "Contraseña *" else "Nueva contraseña (dejar vacío para no cambiar)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(label(if (isCreate) "Contraseña" else "Contraseña")); layout.addView(etPassword)

        val sv = ScrollView(ctx).apply { addView(layout) }

        val builder: () -> CreateUserRequest? = {
            val name = etName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            if (name.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
                Toast.makeText(ctx, "Nombre, apellido y correo son requeridos", Toast.LENGTH_SHORT).show(); null
            } else if (isCreate && password.isEmpty()) {
                Toast.makeText(ctx, "La contraseña es requerida", Toast.LENGTH_SHORT).show(); null
            } else CreateUserRequest(
                name = name,
                lastName = lastName,
                email = email,
                identifier = etIdentifier.text.toString().trim(),
                password = password.ifEmpty { "NO_CHANGE" },
                role = roleValues[spinnerRole.selectedItemPosition],
                active = cbActive.isChecked,
                userType = userTypeValues[spinnerUserType.selectedItemPosition],
                birthDate = etBirthDate.text.toString().trim().ifEmpty { null },
                phone = etPhone.text.toString().trim().ifEmpty { null }
            )
        }
        return Pair(sv, builder)
    }

    private fun showCreateUserDialog() {
        val (view, buildRequest) = buildUserForm(isCreate = true)
        AlertDialog.Builder(requireContext())
            .setTitle("Crear usuario")
            .setView(view)
            .setPositiveButton("Crear") { _, _ ->
                val req = buildRequest() ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val resp = RetrofitClient.create(requireContext()).createUser(req)
                        if (resp.isSuccessful) {
                            Toast.makeText(requireContext(), "Usuario creado", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(requireContext(), "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showEditUserDialog(user: User) {
        val (view, buildRequest) = buildUserForm(user, isCreate = false)
        AlertDialog.Builder(requireContext())
            .setTitle("Editar usuario")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val req = buildRequest() ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val resp = RetrofitClient.create(requireContext()).updateUser(user.id, req)
                        if (resp.isSuccessful) {
                            Toast.makeText(requireContext(), "Usuario actualizado", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(requireContext(), "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showViewUserDialog(user: User) {
        val rolLabel = if (user.role == "ADMIN") "Administrador" else "Usuario solicitante"
        val active = if (user.active) "Activo" else "Inactivo"
        val msg = """
            Nombre: ${user.name} ${user.lastName}
            Correo: ${user.email}
            Matrícula/ID: ${user.identifier ?: "—"}
            Teléfono: ${user.phone ?: "—"}
            Rol: $rolLabel
            Tipo: ${user.userType ?: "—"}
            Fecha nacimiento: ${user.birthDate ?: "—"}
            Estado: $active
        """.trimIndent()
        AlertDialog.Builder(requireContext())
            .setTitle("Detalle del usuario")
            .setMessage(msg)
            .setPositiveButton("Cerrar", null).show()
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
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_generic, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.view.apply {
            val rolEs = if (u.role == "ADMIN") "Administrador" else "Usuario solicitante"
            findViewById<TextView>(R.id.tvTitle).text = "${u.name} ${u.lastName}"
            findViewById<TextView>(R.id.tvSubtitle).text = u.email
            findViewById<TextView>(R.id.tvDetail).text = "Rol: $rolEs | ID: ${u.identifier ?: "—"}"

            val badge = findViewById<TextView>(R.id.tvBadge)
            badge.text = if (u.active) "ACTIVO" else "INACTIVO"
            badge.setBackgroundColor(if (u.active) 0xFF10B981.toInt() else 0xFFEF4444.toInt())

            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnView).setOnClickListener { onView(u) }
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEdit).setOnClickListener { onEdit(u) }
            // Hide history for users
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHistory).visibility = View.GONE

            val btnToggle = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggle)
            btnToggle.text = if (u.active) "Desactivar" else "Activar"
            btnToggle.setOnClickListener { onToggle(u) }
        }
    }
}
