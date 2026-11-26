package com.flycode.dailyschedule.ui

import android.app.TimePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.model.CheckInRecord
import com.flycode.dailyschedule.model.ResolvedDayPlan
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.model.SlotDose
import com.flycode.dailyschedule.model.checkInKey
import com.flycode.dailyschedule.ui.theme.planAccentColor
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val timeStampFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun chineseWeekdayShort(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    val suffix = when (date) {
        today -> " · 今天"
        today.plusDays(1) -> " · 明天"
        today.minusDays(1) -> " · 昨天"
        else -> " · 周${chineseWeekdayShort(date.dayOfWeek)}"
    }
    return "${date.monthValue}月${date.dayOfMonth}日$suffix"
}

fun formatEpochTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(timeStampFormatter)

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format("%.1f MB", mb) else "${bytes / 1024} KB"
}

@Composable
fun DayScheduleSection(
    resolvedPlans: List<ResolvedDayPlan>,
    checkIns: Map<String, CheckInRecord>,
    onToggleSlot: (planId: String, date: LocalDate, slotId: String) -> Unit,
    onAdjustDay: (ResolvedDayPlan) -> Unit,
    onEditPlan: (String) -> Unit,
    onToggleHiddenPlan: (String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onCreatePlan: () -> Unit,
    emptyTitle: String = "暂无安排",
    emptySubtitle: String = ""
) {
    val activePlans = resolvedPlans.filter { !it.isRestDay }
    val restPlans = resolvedPlans.filter { it.isRestDay }

    if (resolvedPlans.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = emptyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (emptySubtitle.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = emptySubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = onCreatePlan) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建计划")
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        activePlans.forEach { resolved ->
            PlanDayCard(
                resolved = resolved,
                checkIns = checkIns,
                onToggleSlot = onToggleSlot,
                onAdjustDay = { onAdjustDay(resolved) },
                onEditPlan = { onEditPlan(resolved.plan.id) },
                onToggleHidden = { onToggleHiddenPlan(resolved.plan.id) },
                onDeletePlan = { onDeletePlan(resolved.plan.id) }
            )
        }

        if (restPlans.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "今日休息",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    restPlans.forEach { resolved ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdjustDay(resolved) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(planAccentColor(resolved.plan.colorIndex))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = resolved.plan.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (resolved.isOverridden) {
                                StagePill(text = "已调", color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = "调整",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlanDayCard(
    resolved: ResolvedDayPlan,
    checkIns: Map<String, CheckInRecord>,
    onToggleSlot: (planId: String, date: LocalDate, slotId: String) -> Unit,
    onAdjustDay: () -> Unit,
    onEditPlan: () -> Unit,
    onToggleHidden: () -> Unit,
    onDeletePlan: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val accent = planAccentColor(resolved.plan.colorIndex)
    val doneCount = resolved.activeSlots.count { slot ->
        checkIns.containsKey(checkInKey(resolved.plan.id, resolved.date, slot.id))
    }
    val allDone = doneCount == resolved.activeSlots.size && resolved.activeSlots.isNotEmpty()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (allDone) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onEditPlan,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMenu = true
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = resolved.plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val isForeverDaily = resolved.totalStages == 1 && resolved.stageTotalDays == 1 && resolved.plan.repeatCycle
                        if (resolved.plan.hidden) {
                            StagePill(text = "隐藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (resolved.isOverridden) {
                            StagePill(text = "已调", color = MaterialTheme.colorScheme.secondary)
                        } else if (!isForeverDaily && resolved.plan.repeatCycle && resolved.cycleNumber > 1) {
                            StagePill(text = "第${resolved.cycleNumber}轮", color = accent)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    val isForeverDaily = resolved.totalStages == 1 && resolved.stageTotalDays == 1 && resolved.plan.repeatCycle
                    val subtitle = buildString {
                        when {
                            isForeverDaily -> append("每日")
                            resolved.totalStages > 1 -> append("阶段 ${resolved.stageIndex + 1}/${resolved.totalStages} · 第${resolved.dayInStage}/${resolved.stageTotalDays}天")
                            else -> append("第${resolved.dayInStage}/${resolved.stageTotalDays}天")
                        }
                        if (resolved.plan.note.isNotBlank()) {
                            append(" · ${resolved.plan.note}")
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onAdjustDay,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("调本日", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    text = "$doneCount/${resolved.activeSlots.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allDone) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                resolved.activeSlots.forEach { slot ->
                    val record = checkIns[checkInKey(resolved.plan.id, resolved.date, slot.id)]
                    SlotCheckRow(
                        slot = slot,
                        unit = resolved.plan.unit,
                        accent = accent,
                        record = record,
                        onToggle = { onToggleSlot(resolved.plan.id, resolved.date, slot.id) },
                        onSwipeLeft = onAdjustDay,
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMenu = true
                        }
                    )
                }
            }
        }
    }

    if (showMenu) {
        ActionMenuDialog(
            title = resolved.plan.title,
            actions = listOf(
                ActionMenuItem("调整本日数量") { showMenu = false; onAdjustDay() },
                ActionMenuItem("编辑计划") { showMenu = false; onEditPlan() },
                ActionMenuItem(if (resolved.plan.hidden) "取消隐藏" else "设为隐藏") { showMenu = false; onToggleHidden() },
                ActionMenuItem("删除计划", destructive = true) { showMenu = false; showDeleteConfirm = true }
            ),
            onDismiss = { showMenu = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除计划") },
            text = { Text("确定删除「${resolved.plan.title}」？") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeletePlan() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

data class ActionMenuItem(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun ActionMenuDialog(
    title: String,
    actions: List<ActionMenuItem>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                actions.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (item.destructive) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                    if (idx < actions.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun DayOverrideDialog(
    resolved: ResolvedDayPlan,
    onDismiss: () -> Unit,
    onSaveOverride: (List<SlotDose>?) -> Unit,
    onEditFullPlan: () -> Unit
) {
    val context = LocalContext.current
    val baseTemplate = remember(resolved) {
        resolved.activeSlots.ifEmpty {
            resolved.stage.slots.ifEmpty {
                resolved.plan.stages.firstOrNull { it.slots.isNotEmpty() }?.slots
                    ?: listOf(
                        SlotDose(id = UUID.randomUUID().toString().take(8), time = "08:00", amount = 1.0),
                        SlotDose(id = UUID.randomUUID().toString().take(8), time = "13:00", amount = 1.0),
                        SlotDose(id = UUID.randomUUID().toString().take(8), time = "20:00", amount = 1.0)
                    )
            }
        }
    }

    var workingSlots by remember(resolved) {
        mutableStateOf(
            if (resolved.isRestDay) {
                baseTemplate.map { it.copy(amount = 0.0) }
            } else {
                resolved.activeSlots.map { it.copy() }
            }
        )
    }

    val uniformAmount = remember(workingSlots) {
        val positive = workingSlots.filter { it.amount > 0.0 }
        if (positive.isEmpty()) 0.0 else positive.maxOf { it.amount }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "调整 ${resolved.date.monthValue}月${resolved.date.dayOfMonth}日",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = resolved.plan.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("统一数量", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    AmountStepper(
                        amount = uniformAmount,
                        unit = resolved.plan.unit,
                        zeroLabel = "休息",
                        onChange = { next ->
                            workingSlots = workingSlots.map { it.copy(amount = next) }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                workingSlots.forEachIndexed { idx, slot ->
                    SlotStepperRow(
                        time = slot.time,
                        amount = slot.amount,
                        unit = resolved.plan.unit,
                        onPickTime = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    val newTime = String.format("%02d:%02d", h, m)
                                    workingSlots = workingSlots.mapIndexed { i, s ->
                                        if (i == idx) s.copy(time = newTime) else s
                                    }.sortedBy { it.parsedTime }
                                },
                                slot.parsedTime.hour,
                                slot.parsedTime.minute,
                                true
                            ).show()
                        },
                        onChangeAmount = { next ->
                            workingSlots = workingSlots.mapIndexed { i, s ->
                                if (i == idx) s.copy(amount = next) else s
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onEditFullPlan) { Text("编辑计划") }
                    if (resolved.isOverridden) {
                        TextButton(onClick = { onSaveOverride(null) }) { Text("恢复默认") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val active = workingSlots.filter { it.amount > 0.0 }
                onSaveOverride(active)
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SlotCheckRow(
    slot: SlotDose,
    unit: String,
    accent: Color,
    record: CheckInRecord?,
    onToggle: () -> Unit,
    onSwipeLeft: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val checked = record != null

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSwipeLeft()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    val bgColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "slotBg"
    )
    val checkBg by animateColorAsState(
        targetValue = if (checked) accent else Color.Transparent,
        label = "checkBg"
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)),
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val swipeBg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> accent.copy(alpha = 0.2f)
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(swipeBg)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (checked) "撤销" else "确认", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
                    }
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("调本日", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .background(bgColor)
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle()
                    },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(checkBg)
                    .border(
                        width = 1.8.dp,
                        color = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "已确认",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = slot.time,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )

            if (slot.label.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "${ScheduleEngine.formatAmount(slot.amount)} $unit",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) accent else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = if (record != null) formatEpochTime(record.checkedAtMillis) else "确认",
                style = MaterialTheme.typography.labelMedium,
                color = if (checked) accent else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StagePill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}
