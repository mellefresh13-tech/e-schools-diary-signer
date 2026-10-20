package by.eschools.diarysigner

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
 private lateinit var login: EditText; private lateinit var password: EditText; private lateinit var status: TextView; private lateinit var stampView: TextView; private lateinit var next: TextView; private lateinit var schedule: CheckBox; private lateinit var runButton: Button
 private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); makeUi(); load(); if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
 private fun makeUi() {
  val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(24)) }; val scroll = ScrollView(this); scroll.addView(content); setContentView(scroll)
  content.addView(TextView(this).apply { text = "Подпись школьного дневника"; textSize = 24f }, lp())
  content.addView(TextView(this).apply { text = "Логин и пароль сохраняются на устройстве в зашифрованном виде."; textSize = 14f }, top(8))
  login = EditText(this).apply { hint = "Логин"; setSingleLine(true) }; content.addView(login, top(12))
  password = EditText(this).apply { hint = "Пароль"; setSingleLine(true); inputType = 129 }; content.addView(password, top(8))
  schedule = CheckBox(this).apply { text = "Автоматическая проверка по пятницам в 19:00 (Минск)"; setOnCheckedChangeListener { _, enabled -> SecurePrefs.setScheduled(this@MainActivity, enabled); if (enabled) Scheduler.schedule(this@MainActivity) else Scheduler.cancel(this@MainActivity); refreshNext() } }; content.addView(schedule, top(12))
  content.addView(Button(this).apply { text = "Настроить точный будильник"; setOnClickListener { if (Build.VERSION.SDK_INT >= 31) { val a = getSystemService(ALARM_SERVICE) as AlarmManager; if (!a.canScheduleExactAlarms()) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) else Toast.makeText(this@MainActivity, "Разрешение уже выдано", Toast.LENGTH_SHORT).show() } } }, top(6))
  runButton = Button(this).apply { text = "Проверить / подписать сейчас"; setOnClickListener { runNow() } }; content.addView(runButton, top(14))
  content.addView(TextView(this).apply { text = "Последний результат"; textSize = 18f }, top(20)); status = TextView(this).apply { textSize = 16f }; content.addView(status, top(6)); stampView = TextView(this).apply { textSize = 13f }; content.addView(stampView, top(4)); next = TextView(this).apply { textSize = 13f }; content.addView(next, top(14))
  content.addView(TextView(this).apply { text = "Важно: Android может задерживать фоновые задачи. Автоматический режим нужно проверить на вашем аккаунте."; textSize = 12f }, top(18))
 }
 private fun runNow() {
  SecurePrefs.saveCredentials(this, login.text.toString(), password.text.toString()); if (SecurePrefs.login(this).isBlank() || SecurePrefs.password(this).isBlank()) { Toast.makeText(this, "Введите логин и пароль", Toast.LENGTH_SHORT).show(); return }
  runButton.isEnabled = false; status.text = "Проверка выполняется…"
  CoroutineScope(Dispatchers.IO).launch {
   val result = try { DiaryApi(SecurePrefs.login(this@MainActivity), SecurePrefs.password(this@MainActivity)).signOrCheck() } catch (e: Exception) { "Не удалось выполнить подпись: ${e.message ?: "ошибка"}".take(400) }
   val stamp = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale("ru")).format(Instant.now().atZone(ZoneId.of("Europe/Minsk"))); SecurePrefs.saveResult(this@MainActivity, result, stamp)
   withContext(Dispatchers.Main) { runButton.isEnabled = true; showResult(result, stamp); Notifier.show(this@MainActivity, result, stamp) }
  }
 }
 private fun load() { login.setText(SecurePrefs.login(this)); password.setText(SecurePrefs.password(this)); showResult(SecurePrefs.result(this), SecurePrefs.resultTime(this)); schedule.isChecked = SecurePrefs.scheduled(this); refreshNext() }
 private fun showResult(result: String, stamp: String) { status.text = result; stampView.text = stamp; status.setTextColor(if (result.contains("подписан", true) || result.contains("подпись уже есть", true)) 0xFF18794E.toInt() else 0xFF17243B.toInt()) }
 private fun refreshNext() { next.text = if (SecurePrefs.scheduled(this)) "Следующий запуск: " + Instant.ofEpochMilli(Scheduler.nextFridayMillis()).atZone(ZoneId.of("Europe/Minsk")).format(DateTimeFormatter.ofPattern("EEEE, dd MMMM, HH:mm", Locale("ru"))) + " (Минск)" else "Автоматическая проверка выключена" }
 override fun onResume() { super.onResume(); if (::status.isInitialized && ::schedule.isInitialized) load() }
 private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
 private fun lp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
 private fun top(n: Int) = lp().apply { topMargin = dp(n) }
}
