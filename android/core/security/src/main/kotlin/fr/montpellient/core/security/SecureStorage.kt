package fr.montpellient.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Gestionnaire de stockage chiffré matériel via Android Keystore et EncryptedSharedPreferences (AES-256-GCM).
 *
 * Garanties RGPD & Sécurité :
 * - Les identifiants (login, mot de passe CAS) ne sont JAMAIS transmis à notre backend.
 * - Le chiffrement s'appuie sur le Keystore de l'appareil avec clé AES256-GCM hardware-backed si disponible.
 */
interface SecureStorage {
    fun saveCredentials(login: String, password: String)
    fun getCredentials(): Pair<String, String>?
    fun clearCredentials()
    fun saveSecret(key: String, value: String)
    fun getSecret(key: String): String?
    fun removeSecret(key: String)
    fun clearAll()
}

class AndroidSecureStorage(context: Context) : SecureStorage {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveCredentials(login: String, password: String) {
        sharedPreferences.edit()
            .putString(KEY_LOGIN, login)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    override fun getCredentials(): Pair<String, String>? {
        val login = sharedPreferences.getString(KEY_LOGIN, null) ?: return null
        val password = sharedPreferences.getString(KEY_PASSWORD, null) ?: return null
        return Pair(login, password)
    }

    override fun clearCredentials() {
        sharedPreferences.edit()
            .remove(KEY_LOGIN)
            .remove(KEY_PASSWORD)
            .apply()
    }

    override fun saveSecret(key: String, value: String) {
        sharedPreferences.edit()
            .putString(key, value)
            .apply()
    }

    override fun getSecret(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    override fun removeSecret(key: String) {
        sharedPreferences.edit()
            .remove(key)
            .apply()
    }

    override fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val SECURE_PREFS_FILE = "montpellient_secure_vault"
        private const val KEY_LOGIN = "cas_login"
        private const val KEY_PASSWORD = "cas_password"
    }
}
