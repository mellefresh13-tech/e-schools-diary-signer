package by.eschools.diarysigner

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val FILE = "diary_signer_secure"
    private fun prefs(context: Context) = EncryptedSharedPreferences.create(context, FILE, MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun login(context: Context) = prefs(context).getString("login", "") ?: ""
    fun password(context: Context) = prefs(context).getString("password", "") ?: ""
    fun saveCredentials(context: Context, login: String, password: String) { prefs(context).edit().putString("login", login.trim()).putString("password", password).apply() }
    fun result(context: Context) = prefs(context).getString("last_result", "Подпись ещё не выполнялась") ?: ""
    fun resultTime(context: Context) = prefs(context).getString("last_time", "") ?: ""
    fun scheduled(context: Context) = prefs(context).getBoolean("scheduled", false)
    fun setScheduled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("scheduled", value).apply() }
    fun saveResult(context: Context, result: String, time: String) { prefs(context).edit().putString("last_result", result).putString("last_time", time).apply() }
}
