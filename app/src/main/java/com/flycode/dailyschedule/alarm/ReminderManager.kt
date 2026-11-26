package com.flycode.dailyschedule.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.flycode.dailyschedule.MainActivity
import com.flycode.dailyschedule.R
import com.flycode.dailyschedule.data.AppStore
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.model.checkInKey
import com.flycode.dailyschedule.update.GitHubUpdater
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderManager {

    const val CHANNEL_REMINDER = "daily_schedule_reminders"
    const val CHANNEL_UPDATE = "daily_schedule_updates"

    const val ACTION_FIRE_REMINDER = "com.flycode.dailyschedule.ACTION_FIRE_REMINDER"
    const val ACTION_CONFIRM_SLOT = "com.flycode.dailyschedule.ACTION_CONFIRM_SLOT"
    const val ACTION_SNOOZE_SLOT = "com.flycode.dailyschedule.ACTION_SNOOZE_SLOT"

    const val EXTRA_PLAN_ID = "extra_plan_id"
    const val EXTRA_DATE = "extra_date"
    const val EXTRA_SLOT_ID = "extra_slot_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    private const val PREFS_NAME = "reminder_prefs"
    private const val KEY_CODES = "scheduled_codes"
    private const val LOOKAHEAD_DAYS = 90L
    private const val MAX_ALARMS = 32

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_REMINDER) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDER,
                    "日程阶段提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "按计划阶段与每日时段发送提醒通知"
                    enableVibration(true)
                }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_UPDATE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_UPDATE,
                    "版本更新通知",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "检测到 GitHub Release 新版本时提醒更新"
                }
            )
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun rescheduleAll(context: Context) {
        runCatching {
            rescheduleAllInternal(context)
        }
    }

    private fun rescheduleAllInternal(context: Context) {
        ensureChannels(context)
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Cancel previously scheduled alarms
        val oldCodes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        oldCodes.forEach { codeStr ->
            codeStr.toIntOrNull()?.let { code ->
                val pi = PendingIntent.getBroadcast(
                    appContext,
                    code,
                    Intent(appContext, ReminderReceiver::class.java).setAction(ACTION_FIRE_REMINDER),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pi != null) {
                    alarmManager.cancel(pi)
                    pi.cancel()
                }
            }
        }

        val state = AppStore.current(appContext)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val leadMinutes = state.settings.reminderLeadMinutes.toLong().coerceAtLeast(0L)
        val zone = ZoneId.systemDefault()

        data class UpcomingTrigger(
            val triggerAt: LocalDateTime,
            val planId: String,
            val date: LocalDate,
            val slotId: String
        )

        val upcoming = mutableListOf<UpcomingTrigger>()
        for (dayOffset in 0L..LOOKAHEAD_DAYS) {
            val date = today.plusDays(dayOffset)
            val resolvedList = ScheduleEngine.resolveAllForDate(state.plans, date)
            for (resolved in resolvedList) {
                for (slot in resolved.activeSlots) {
                    val key = checkInKey(resolved.plan.id, date, slot.id)
                    if (state.checkIns.containsKey(key)) continue
                    val triggerAt = date.atTime(slot.parsedTime).minusMinutes(leadMinutes)
                    if (triggerAt.isAfter(now)) {
                        upcoming += UpcomingTrigger(triggerAt, resolved.plan.id, date, slot.id)
                    }
                }
            }
            if (upcoming.size >= MAX_ALARMS * 2) break
        }

        val selected = upcoming.sortedBy { it.triggerAt }.take(MAX_ALARMS)
        val newCodes = mutableSetOf<String>()

        selected.forEach { item ->
            val requestCode = requestCodeFor(item.planId, item.date.toString(), item.slotId)
            val intent = Intent(appContext, ReminderReceiver::class.java).apply {
                action = ACTION_FIRE_REMINDER
                putExtra(EXTRA_PLAN_ID, item.planId)
                putExtra(EXTRA_DATE, item.date.toString())
                putExtra(EXTRA_SLOT_ID, item.slotId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerMillis = item.triggerAt.atZone(zone).toInstant().toEpochMilli()
            scheduleAlarm(alarmManager, triggerMillis, pendingIntent, appContext)
            newCodes += requestCode.toString()
        }

        prefs.edit().putStringSet(KEY_CODES, newCodes).apply()
    }

    fun scheduleSnooze(
        context: Context,
        planId: String,
        dateIso: String,
        slotId: String,
        minutes: Long = 15L
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val code = requestCodeFor(planId, dateIso, "$slotId#snooze")
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            putExtra(EXTRA_PLAN_ID, planId)
            putExtra(EXTRA_DATE, dateIso)
            putExtra(EXTRA_SLOT_ID, slotId)
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerMillis = System.currentTimeMillis() + minutes * 60_000L
        scheduleAlarm(alarmManager, triggerMillis, pi, appContext)
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerMillis: Long,
        pendingIntent: PendingIntent,
        context: Context
    ) {
        val scheduledExact = if (canScheduleExact(context)) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }.isSuccess
        } else {
            false
        }
        if (!scheduledExact) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        }
    }

    fun showTestNotification(context: Context) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val openIntent = PendingIntent.getActivity(
            context,
            9001,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("提醒通道测试正常")
            .setContentText("日常计划通知与闹钟触发权限已就绪")
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(9001, notification)
    }

    fun requestCodeFor(planId: String, dateIso: String, slotId: String): Int =
        ("$planId|$dateIso|$slotId").hashCode() and 0x7FFFFFFF
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val planId = intent.getStringExtra(ReminderManager.EXTRA_PLAN_ID) ?: return
        val dateIso = intent.getStringExtra(ReminderManager.EXTRA_DATE) ?: return
        val slotId = intent.getStringExtra(ReminderManager.EXTRA_SLOT_ID) ?: return
        val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        val notificationId = intent.getIntExtra(
            ReminderManager.EXTRA_NOTIFICATION_ID,
            ReminderManager.requestCodeFor(planId, dateIso, slotId)
        )

        when (action) {
            ReminderManager.ACTION_CONFIRM_SLOT -> {
                AppStore.setCheckIn(context, planId, date, slotId, checked = true)
                nm?.cancel(notificationId)
                ReminderManager.rescheduleAll(context)
            }

            ReminderManager.ACTION_SNOOZE_SLOT -> {
                nm?.cancel(notificationId)
                ReminderManager.scheduleSnooze(context, planId, dateIso, slotId, 15L)
            }

            ReminderManager.ACTION_FIRE_REMINDER -> {
                ReminderManager.ensureChannels(context)
                val state = AppStore.current(context)
                val alreadyDone = state.checkIns.containsKey(checkInKey(planId, dateIso, slotId))
                if (!alreadyDone) {
                    val plan = state.plans.firstOrNull { it.id == planId }
                    val resolved = plan?.let { ScheduleEngine.resolveDay(it, date) }
                    val slot = resolved?.activeSlots?.firstOrNull { it.id == slotId }
                    if (plan != null && resolved != null && slot != null && nm != null) {
                        val amountText = ScheduleEngine.formatAmount(slot.amount)
                        val title = "${plan.title} · $amountText${plan.unit}"
                        val slotPrefix = if (slot.label.isNotBlank()) "${slot.time} ${slot.label}" else slot.time
                        val stageLabel = "阶段 ${resolved.stageIndex + 1}/${resolved.totalStages} · 第${resolved.dayInStage}/${resolved.stageTotalDays}天"
                        val body = buildString {
                            append(slotPrefix)
                            append(" · ")
                            append(stageLabel)
                            if (plan.note.isNotBlank()) {
                                append("（").append(plan.note).append("）")
                            }
                        }

                        val openAppIntent = PendingIntent.getActivity(
                            context,
                            notificationId,
                            Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(ReminderManager.EXTRA_DATE, dateIso)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val confirmIntent = PendingIntent.getBroadcast(
                            context,
                            notificationId + 1,
                            Intent(context, ReminderReceiver::class.java).apply {
                                this.action = ReminderManager.ACTION_CONFIRM_SLOT
                                putExtra(ReminderManager.EXTRA_PLAN_ID, planId)
                                putExtra(ReminderManager.EXTRA_DATE, dateIso)
                                putExtra(ReminderManager.EXTRA_SLOT_ID, slotId)
                                putExtra(ReminderManager.EXTRA_NOTIFICATION_ID, notificationId)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val snoozeIntent = PendingIntent.getBroadcast(
                            context,
                            notificationId + 2,
                            Intent(context, ReminderReceiver::class.java).apply {
                                this.action = ReminderManager.ACTION_SNOOZE_SLOT
                                putExtra(ReminderManager.EXTRA_PLAN_ID, planId)
                                putExtra(ReminderManager.EXTRA_DATE, dateIso)
                                putExtra(ReminderManager.EXTRA_SLOT_ID, slotId)
                                putExtra(ReminderManager.EXTRA_NOTIFICATION_ID, notificationId)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_REMINDER)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(title)
                            .setContentText(body)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                            .setContentIntent(openAppIntent)
                            .setAutoCancel(true)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .addAction(0, "已确认打卡", confirmIntent)
                            .addAction(0, "延后15分", snoozeIntent)
                            .build()

                        nm.notify(notificationId, notification)
                    }
                }
                ReminderManager.rescheduleAll(context)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppStore.init(context)
        ReminderManager.rescheduleAll(context)
        GitHubUpdater.schedulePeriodicCheck(context)
    }
}
