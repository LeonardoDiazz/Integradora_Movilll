package com.sgr.app.ui.user

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sgr.app.R
import com.sgr.app.databinding.ActivityUserBinding
import com.sgr.app.ui.auth.LoginActivity
import com.sgr.app.ui.admin.ProfileFragment
import com.sgr.app.utils.SessionManager

class UserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        setSupportActionBar(binding.toolbar)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { showFragment(HomeFragment()); true }
                R.id.nav_new_request -> { showFragment(NewRequestFragment()); true }
                R.id.nav_my_requests -> { showFragment(MyRequestsFragment()); true }
                R.id.nav_profile_user -> { showFragment(UserProfileFragment()); true }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            showFragment(HomeFragment())
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
