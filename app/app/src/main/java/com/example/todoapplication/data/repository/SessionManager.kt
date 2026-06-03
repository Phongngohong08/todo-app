package com.example.todoapplication.data.repository

import android.content.Context
import android.content.SharedPreferences

import androidx.core.content.edit

class SessionManager  (
     context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "todo_app_prefs"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
    }

    fun saveAuthToken(token: String) {
        prefs.edit { putString(KEY_TOKEN, token) }
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveUser(id: String, email: String, name: String) {
        prefs.edit().apply {
            putString(KEY_USER_ID, id)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    fun getUserId(): String {
        return prefs.getString(KEY_USER_ID, "") ?: ""
    }

    fun getUserEmail(): String {
        return prefs.getString(KEY_USER_EMAIL, "") ?: ""
    }

    fun logout() {
        prefs.edit { clear() }
    }

    fun isLoggedIn(): Boolean {
        return getAuthToken() != null
    }
}
