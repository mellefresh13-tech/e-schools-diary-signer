package by.eschools.diarysigner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId
import java.time.ZonedDateTime

object Scheduler {
    private val zone = ZoneId.of("Europe/Minsk")
    fun nextFridayMillis(): Long {
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(19).withMinute(0).withSecond(0).withNano(0)
        while (next.dayOfWeek.value != 5 || !next.isAfter(now)) next = next.plusDays(1).withHour(19).withMinute(0).withSecond(0).withNano(0)
        return next.toInstant().toEpochMilli()
    }
    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 701, Intent(context, ScheduleReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context: Context) {
        if (!SecurePrefs.scheduled(context)) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFridayMillis(), pi)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFridayMillis(), pi)
    }
    fun cancel(context: Context) { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context)) }
}
