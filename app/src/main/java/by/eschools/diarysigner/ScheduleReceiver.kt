package by.eschools.diarysigner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent?) {
  val pending = goAsync(); val app = context.applicationContext
  CoroutineScope(Dispatchers.IO).launch {
   val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager; val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DiarySigner:Sign")
   try { lock.acquire(180000L); perform(app) }
   catch (e: Exception) { val stamp = now(); val result = "Ошибка фонового запуска: ${e.message ?: "неизвестная ошибка"}".take(350); SecurePrefs.saveResult(app, result, stamp); Notifier.show(app, result, stamp); Scheduler.schedule(app) }
   finally { if (lock.isHeld) lock.release(); pending.finish() }
  }
 }
 companion object {
  private fun now() = ZonedDateTime.now(ZoneId.of("Europe/Minsk")).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale("ru")))
  fun perform(context: Context) {
   val stamp = now(); val result = try { val login = SecurePrefs.login(context); val password = SecurePrefs.password(context); if (login.isBlank() || password.isBlank()) error("Сначала введите логин и пароль в приложении."); DiaryApi(login, password).signOrCheck() } catch (e: Exception) { "Не удалось выполнить подпись: ${e.message ?: "неизвестная ошибка"}".take(400) }
   SecurePrefs.saveResult(context, result, stamp); Notifier.show(context, result, stamp); Scheduler.schedule(context)
  }
 }
}
