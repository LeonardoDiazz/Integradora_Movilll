package com.sgr.app.utils

import android.content.Context

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("sgr_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var userId: Long
        get() = prefs.getLong("userId", -1)
        set(value) = prefs.edit().putLong("userId", value).apply()

    var userName: String?
        get() = prefs.getString("userName", null)
        set(value) = prefs.edit().putString("userName", value).apply()

    var userLastName: String?
        get() = prefs.getString("userLastName", null)
        set(value) = prefs.edit().putString("userLastName", value).apply()

    var userEmail: String?
        get() = prefs.getString("userEmail", null)
        set(value) = prefs.edit().putString("userEmail", value).apply()

    var userRole: String?
        get() = prefs.getString("userRole", null)
        set(value) = prefs.edit().putString("userRole", value).apply()

    val isLoggedIn: Boolean get() = token != null

    fun save(token: String, userId: Long, name: String, lastName: String, email: String, role: String) {
        this.token = token
        this.userId = userId
        this.userName = name
        this.userLastName = lastName
        this.userEmail = email
        this.userRole = role
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
