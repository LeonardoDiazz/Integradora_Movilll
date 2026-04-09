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

    private val rolLabels = arrayOf("Todos los Roles", "Administrador", "Personal UTEZ", "Estudiante")
    private val rolValues = arrayOf("", "ADMIN", "STAFF", "STUDENT")

    private val tipoLabels = arrayOf("Todos los Tipos", "Administrativo", "Personal Académico", "Estudiante")
    private val tipoValues = arrayOf("", "Administrativo", "Personal Académico", "Estudiante")

    private val estadoLabels = arrayOf("Todos", "Activo", "Inactivo")
    private val estadoValues = arrayOf("", "ACTIVE", "INACTIVE")

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

        binding.btnAddUser.visibility = View.GONE

        setupSpinner(binding.spinnerRol, rolLabels) { pos ->
            if (!isInitializing) { filterRol = rolValues[pos]; filterTipo = ""; filterEstado = ""; currentPage = 0; load() }
        }
        setupSpinner(binding.spinnerTipo, tipoLabels) { pos ->
            if (!isInitializing) { filterTipo = tipoValues[pos]; filterRol = ""; filterEstado = ""; currentPage = 0; load() }
        }
        setupSpinner(binding.spinnerEstado, estadoLabels) { pos ->
            if (!isInitializing) { filterEstado = estadoValues[pos]; filterRol = ""; filterTipo = ""; currentPage = 0; load() }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterRol = ""; filterTipo = ""; filterEstado = ""; currentPage = 0
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
        val backendFilter = when {
            filterRol.isNotEmpty() -> filterRol
            filterEstado.isNotEmpty() -> filterEstado
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getUsers(currentPage, 10, backendFilter, "")
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
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    private fun formatRole(role: String): String = when (role) {
        "ADMIN" -> "Administrador"; "STAFF" -> "Personal UTEZ"; "STUDENT" -> "Estudiante"; else -> role
    }

    private fun showViewUserDialog(user: User) {
        val identifierLabel = if (user.role == "STUDENT") "Matrícula" else "No. Empleado"
        val msg = """
            Nombre: ${user.name} ${user.lastName}
            Correo: ${user.email}
            Teléfono: ${user.phone ?: "Sin registrar"}
            Rol: ${formatRole(user.role)}
            Tipo: ${user.userType ?: "—"}
            $identifierLabel: ${user.identifier ?: "—"}
            Fecha nacimiento: ${user.birthDate ?: "—"}
            Estado: ${if (user.active) "Activo" else "Inactivo"}
        """.trimIndent()
        AlertDialog.Builder(requireContext()).setTitle("Detalle del usuario").setMessage(msg).setPositiveButton("Cerrar", null).show()
    }

    private fun showEditUserDialog(user: User) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }
        fun label(text: String) = TextView(ctx).apply { this.text = text; textSize = 12f; setPadding(0, 12, 0, 2) }

        val etName = EditText(ctx).apply { hint = "Nombre"; setText(user.name) }
        val etLastName = EditText(ctx).apply { hint = "Apellidos"; setText(user.lastName) }
        val etBirthDate = EditText(ctx).apply { hint = "YYYY-MM-DD"; setText(user.birthDate ?: "") }
        val etIdentifier = EditText(ctx).apply { hint = if (user.role == "STUDENT") "Matrícula" else "No. Empleado"; setText(user.identifier ?: "") }
        val etEmail = EditText(ctx).apply { hint = "correo@utez.edu.mx"; setText(user.email); inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val etPhone = EditText(ctx).apply { hint = "10 dígitos"; setText(user.phone ?: ""); inputType = android.text.InputType.TYPE_CLASS_PHONE }

        val roleValues = arrayOf("ADMIN", "STAFF", "STUDENT")
        val roleLabels = arrayOf("Administrador", "Personal UTEZ", "Estudiante")
        val spinnerRole = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, roleLabels)
            setSelection(roleValues.indexOf(user.role).coerceAtLeast(0))
        }

        val typeValues = arrayOf("Administrativo", "Personal Académico", "Estudiante")
        val spinnerType = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, typeValues)
            setSelection(typeValues.indexOf(user.userType ?: "").coerceAtLeast(0))
        }

        val cbActive = CheckBox(ctx).apply { text = "Activo"; isChecked = user.active }

        layout.addView(label("Nombre *")); layout.addView(etName)
        layout.addView(label("Apellidos *")); layout.addView(etLastName)
        layout.addView(label("Fecha de nacimiento *")); layout.addView(etBirthDate)
        layout.addView(label("Rol *")); layout.addView(spinnerRole)
        layout.addView(label("Tipo de usuario *")); layout.addView(spinnerType)
        layout.addView(label(if (user.role == "STUDENT") "Matrícula *" else "No. Empleado *")); layout.addView(etIdentifier)
        layout.addView(label("Correo institucional *")); layout.addView(etEmail)
        layout.addView(label("Teléfono")); layout.addView(etPhone)
        layout.addView(cbActive)

        val sv = ScrollView(ctx).apply { addView(layout) }

        AlertDialog.Builder(ctx).setTitle("Editar usuario").setView(sv)
            .setPositiveButton("Guardar") { _, _ ->
                val name = etName.text.toString().trim()
                val lastName = etLastName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val identifier = etIdentifier.text.toString().trim()
                val birthDate = etBirthDate.text.toString().trim()

                if (name.isEmpty() || lastName.isEmpty() || email.isEmpty() || identifier.isEmpty()) {
                    Toast.makeText(ctx, "Completa todos los campos obligatorios", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (phone.isNotEmpty() && phone.length != 10) {
                    Toast.makeText(ctx, "El teléfono debe tener 10 dígitos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        val req = CreateUserRequest(
                            name = name, lastName = lastName, email = email, identifier = identifier,
                            password = "", role = roleValues[spinnerRole.selectedItemPosition],
                            active = cbActive.isChecked, userType = typeValues[spinnerType.selectedItemPosition],
                            birthDate = birthDate.ifBlank { null }, phone = phone.ifBlank { null }
                        )
                        val resp = RetrofitClient.create(ctx).updateUser(user.id, req)
                        if (resp.isSuccessful) { Toast.makeText(ctx, "Usuario actualizado", Toast.LENGTH_SHORT).show(); load() }
                        else Toast.makeText(ctx, "Error al actualizar", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }.setNegativeButton("Cancelar", null).show()
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
        val roleLabel = when (u.role) { "ADMIN" -> "Administrador"; "STAFF" -> "Personal UTEZ"; else -> "Estudiante" }
        holder.view.apply {
            findViewById<TextView>(R.id.tvTitle).text = "${u.name} ${u.lastName}"
            findViewById<TextView>(R.id.tvSubtitle).text = "${u.email} | ${u.identifier ?: "—"}"
            findViewById<TextView>(R.id.tvDetail).text = "$roleLabel | ${u.userType ?: "—"}"
            val badge = findViewById<TextView>(R.id.tvBadge)
            badge.text = if (u.active) "Activo" else "Inactivo"
            badge.setBackgroundColor(if (u.active) 0xFF10B981.toInt() else 0xFFEF4444.toInt())
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnView).setOnClickListener { onView(u) }
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEdit).setOnClickListener { onEdit(u) }
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHistory).visibility = View.GONE
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggle).apply {
                text = if (u.active) "Desactivar" else "Activar"
                setOnClickListener { onToggle(u) }
            }
        }
    }
}