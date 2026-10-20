package by.eschools.diarysigner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent?) { Scheduler.schedule(context.applicationContext) } }
