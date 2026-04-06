package com.sgr.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sgr.app.databinding.FragmentDashboardBinding
import com.sgr.app.network.RetrofitClient
import com.sgr.app.utils.SessionManager
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = SessionManager(requireContext())
        binding.tvWelcome.text = "Bienvenido, ${session.userName}"
        requireActivity().title = ""
        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getDashboardStats()
                if (response.isSuccessful) {
                    val stats = response.body() ?: return@launch
                    binding.tvActiveUsers.text = stats.activeUsers.toString()
                    binding.tvPendingReservations.text = stats.pendingReservations.toString()
                    binding.tvTotalSpaces.text = stats.totalSpaces.toString()
                    binding.tvTotalEquipments.text = stats.totalEquipments.toString()
                    binding.tvApproved.text = stats.approvedReservations.toString()
                    binding.tvRejected.text = stats.rejectedReservations.toString()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
