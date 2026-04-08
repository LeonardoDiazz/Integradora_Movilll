package com.sgr.app.ui.user

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.sgr.app.R
import com.sgr.app.databinding.FragmentHomeBinding
import com.sgr.app.utils.SessionManager

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""

        val session = SessionManager(requireContext())
        binding.tvWelcome.text = "Bienvenido, ${session.userName ?: "Usuario"}"

        binding.cardNewRequest.setOnClickListener {
            (requireActivity() as UserActivity).navigateTo(NewRequestFragment(), R.id.nav_new_request)
        }

        binding.cardMyRequests.setOnClickListener {
            (requireActivity() as UserActivity).navigateTo(MyRequestsFragment(), R.id.nav_my_requests)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
