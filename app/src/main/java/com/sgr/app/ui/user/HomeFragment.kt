package com.sgr.app.ui.user

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sgr.app.databinding.FragmentListBinding
import com.sgr.app.network.RetrofitClient
import com.sgr.app.ui.admin.SpaceAdapter
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.spinnerFilter.visibility = View.GONE
        binding.btnPrev.visibility = View.GONE
        binding.btnNext.visibility = View.GONE
        binding.tvPage.visibility = View.GONE
        loadSpaces()
    }

    private fun loadSpaces() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getSpaces(0, 50, "DISPONIBLE", binding.etSearch.text?.toString() ?: "")
                if (response.isSuccessful) {
                    val spaces = response.body()?.content ?: emptyList()
                    binding.recyclerView.adapter = SpaceAdapter(spaces) {}
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar recursos", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
