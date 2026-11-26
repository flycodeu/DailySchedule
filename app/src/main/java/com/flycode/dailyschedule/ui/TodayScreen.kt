package com.flycode.dailyschedule.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.model.AppState
import com.flycode.dailyschedule.model.ResolvedDayPlan
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.model.checkInKey
import java.time.LocalDate

@Composable
fun TodayScreen(
    state: AppState,
    selectedDate: LocalDate,
    hasNotificationPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onDismissPermissionBanner: () -> Unit,
    onToggleSlot: (planId: String, date: LocalDate, slotId: String) -> Unit,
    onAdjustDay: (ResolvedDayPlan) -> Unit,
    onEditPlan: (String) -> Unit,
    onCreatePlan: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val resolvedPlans = remember(state.plans, selectedDate) {
        ScheduleEngine.resolveAllForDate(state.plans, selectedDate)
    }
    val totalSlots = resolvedPlans.sumOf { it.activeSlots.size }
    val doneSlots = resolvedPlans.sumOf { resolved ->
        resolved.activeSlots.count { slot ->
            state.checkIns.containsKey(checkInKey(resolved.plan.id, selectedDate, slot.id))
        }
    }
    val progressTarget = if (totalSlots > 0) doneSlots.toFloat() / totalSlots.toFloat() else 0f
    val progress by animateFloatAsState(targetValue = progressTarget, label = "dailyProgress")

    val showPermissionPrompts = state.settings.remindersEnabled && !state.settings.hidePermissionPrompts

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            WeekStrip(
                selectedDate = selectedDate,
                today = today,
                onSelectDate = onSelectDate
            )
        }

        if (showPermissionPrompts && !hasNotificationPermission) {
            item {
                PermissionBanner(
                    icon = Icons.Rounded.Notifications,
                    title = "开启通知权限",
                    subtitle = "用于按时提醒，不用提醒可点右侧关闭",
                    actionText = "授权",
                    onAction = onRequestNotificationPermission,
                    onDismiss = onDismissPermissionBanner
                )
            }
        } else if (showPermissionPrompts && !hasExactAlarmPermission) {
            item {
                PermissionBanner(
                    icon = Icons.Rounded.Warning,
                    title = "开启精确闹钟",
                    subtitle = "减少提醒延迟，不用闹钟可点右侧隐藏",
                    actionText = "开启",
                    onAction = onRequestExactAlarmPermission,
                    onDismiss = onDismissPermissionBanner
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatDateHeader(selectedDate),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            val statusText = when {
                                resolvedPlans.isEmpty() -> "暂无执行中的计划"
                                totalSlots == 0 -> "今天全为休息日"
                                doneSlots == totalSlots -> "全部 $totalSlots 项已完成"
                                else -> "已完成 $doneSlots / $totalSlots 项"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        if (selectedDate != today) {
                            TextButton(onClick = { onSelectDate(today) }) {
                                Text("回今天", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (totalSlots > 0) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        item {
            DayScheduleSection(
                resolvedPlans = resolvedPlans,
                checkIns = state.checkIns,
                onToggleSlot = onToggleSlot,
                onAdjustDay = onAdjustDay,
                onEditPlan = onEditPlan,
                onCreatePlan = onCreatePlan,
                emptyTitle = if (state.plans.isEmpty()) "暂无计划" else "当天暂无安排",
                emptySubtitle = ""
            )
        }
    }
}

@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    val startOfWindow = selectedDate.minusDays(3)
    val days = remember(selectedDate) {
        (0L..6L).map { startOfWindow.plusDays(it) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { day ->
            val selected = day == selectedDate
            val isToday = day == today
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectDate(day) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = chineseWeekdayShort(day.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isToday && selected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(actionText, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "隐藏提示",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
