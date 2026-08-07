package com.connectx.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "connectx_preferences")

data class AppConfig(
    val apiBaseUrl: String = "https://example.com/api/",
    val webSocketUrl: String = "wss://example.com/socket",
    val stunServerUrl: String = "stun:stun.l.google.com:19302",
    val turnServerUrl: String = "turn:turn.example.com:3478",
    val turnUsername: String = "connectx_user",
    val turnCredential: String = "connectx_pass"
)

data class AuthTokens(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userId: String? = null,
    val userEmail: String? = null,
    val userName: String? = null,
    val userPhone: String? = null,
    val profilePhotoUrl: String? = null,
    val isRememberMeEnabled: Boolean = true,
    val isBiometricEnabled: Boolean = false
)

@Singleton
class AppPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val WEBSOCKET_URL = stringPreferencesKey("websocket_url")
        val STUN_SERVER_URL = stringPreferencesKey("stun_server_url")
        val TURN_SERVER_URL = stringPreferencesKey("turn_server_url")
        val TURN_USERNAME = stringPreferencesKey("turn_username")
        val TURN_CREDENTIAL = stringPreferencesKey("turn_credential")

        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val PROFILE_PHOTO_URL = stringPreferencesKey("profile_photo_url")
        val REMEMBER_ME = booleanPreferencesKey("remember_me")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")

        val THEME_MODE = stringPreferencesKey("theme_mode") // "SYSTEM", "LIGHT", "DARK"
        val LANGUAGE = stringPreferencesKey("language")
    }

    val appConfigFlow: Flow<AppConfig> = context.dataStore.data.map { prefs ->
        AppConfig(
            apiBaseUrl = prefs[Keys.API_BASE_URL] ?: "https://example.com/api/",
            webSocketUrl = prefs[Keys.WEBSOCKET_URL] ?: "wss://example.com/socket",
            stunServerUrl = prefs[Keys.STUN_SERVER_URL] ?: "stun:stun.l.google.com:19302",
            turnServerUrl = prefs[Keys.TURN_SERVER_URL] ?: "turn:turn.example.com:3478",
            turnUsername = prefs[Keys.TURN_USERNAME] ?: "connectx_user",
            turnCredential = prefs[Keys.TURN_CREDENTIAL] ?: "connectx_pass"
        )
    }

    val authTokensFlow: Flow<AuthTokens> = context.dataStore.data.map { prefs ->
        AuthTokens(
            accessToken = prefs[Keys.ACCESS_TOKEN],
            refreshToken = prefs[Keys.REFRESH_TOKEN],
            userId = prefs[Keys.USER_ID],
            userEmail = prefs[Keys.USER_EMAIL],
            userName = prefs[Keys.USER_NAME],
            userPhone = prefs[Keys.USER_PHONE],
            profilePhotoUrl = prefs[Keys.PROFILE_PHOTO_URL],
            isRememberMeEnabled = prefs[Keys.REMEMBER_ME] ?: true,
            isBiometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false
        )
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "SYSTEM"
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LANGUAGE] ?: "English"
    }

    suspend fun saveAppConfig(config: AppConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.API_BASE_URL] = config.apiBaseUrl
            prefs[Keys.WEBSOCKET_URL] = config.webSocketUrl
            prefs[Keys.STUN_SERVER_URL] = config.stunServerUrl
            prefs[Keys.TURN_SERVER_URL] = config.turnServerUrl
            prefs[Keys.TURN_USERNAME] = config.turnUsername
            prefs[Keys.TURN_CREDENTIAL] = config.turnCredential
        }
    }

    suspend fun saveAuthTokens(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String,
        name: String,
        phone: String? = null,
        photoUrl: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USER_EMAIL] = email
            prefs[Keys.USER_NAME] = name
            phone?.let { prefs[Keys.USER_PHONE] = it }
            photoUrl?.let { prefs[Keys.PROFILE_PHOTO_URL] = it }
        }
    }

    suspend fun updateProfileInfo(name: String, phone: String, photoUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name
            prefs[Keys.USER_PHONE] = phone
            prefs[Keys.PROFILE_PHOTO_URL] = photoUrl
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.USER_NAME)
            prefs.remove(Keys.USER_PHONE)
            prefs.remove(Keys.PROFILE_PHOTO_URL)
        }
    }
}
