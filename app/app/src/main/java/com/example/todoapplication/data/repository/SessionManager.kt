package com.example.todoapplication.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Token JWT + refresh token là dữ liệu nhạy cảm nên lưu qua EncryptedSharedPreferences (mã hóa bằng Android Keystore). */
class SessionManager(context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    companion object {
        internal const val PREF_NAME = "todo_app_prefs"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
    }

    fun saveAuthToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /** Lưu cả access token và refresh token trong một lần ghi. */
    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString(KEY_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
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

    fun getUserEmail(): String {
        return prefs.getString(KEY_USER_EMAIL, "") ?: ""
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return getAuthToken() != null
    }
}

/**
 * Tạo EncryptedSharedPreferences; nếu file mã hóa bị hỏng (ví dụ khóa Keystore mất sau khi
 * restore backup sang máy khác, hoặc OS thay đổi), xóa file rồi tạo lại thay vì để app crash.
 * Hệ quả: phiên đăng nhập cũ mất -> người dùng đăng nhập lại (an toàn hơn crash vòng lặp).
 */
private fun createEncryptedPrefs(context: Context): SharedPreferences {
    fun build(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SessionManager.PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    return try {
        build()
    } catch (e: Exception) {
        context.deleteSharedPreferences(SessionManager.PREF_NAME)
        build()
    }
}
