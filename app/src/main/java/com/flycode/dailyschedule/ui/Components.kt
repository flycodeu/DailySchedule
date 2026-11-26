package com.flycode.dailyschedule.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onEditPlan: (String) -> Unit,
    onCreatePlan: () -> Unit,
    emptyTitle: String = "当天暂无安排",
    emptySubtitle: String = "点击下方按钮新建阶段计划"
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = emptySubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                onEditPlan = { onEditPlan(resolved.plan.id) }
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
                        text = "今日休整 / 停用阶段 (0)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    restPlans.forEach { resolved ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditPlan(resolved.plan.id) }
                                .padding(vertical = 2.dp),
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
                            Text(
                                text = "阶段 ${resolved.stageIndex + 1}/${resolved.totalStages} · 第${resolved.dayInStage}/${resolved.stageTotalDays}天",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanDayCard(
    resolved: ResolvedDayPlan,
    checkIns: Map<String, CheckInRecord>,
    onToggleSlot: (planId: String, date: LocalDate, slotId: String) -> Unit,
    onEditPlan: () -> Unit
) {
    val accent = planAccentColor(resolved.plan.colorIndex)
    val doneCount = resolved.activeSlots.count { slot ->
        checkIns.containsKey(checkInKey(resolved.plan.id, resolved.date, slot.id))
    }
    val allDone = doneCount == resolved.activeSlots.size && resolved.activeSlots.isNotEmpty()

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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = resolved.plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (resolved.plan.repeatCycle && resolved.cycleNumber > 1) {
                            StagePill(text = "第${resolved.cycleNumber}轮", color = accent)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    val stageName = resolved.stage.name.ifBlank { "阶段 ${resolved.stageIndex + 1}" }
                    val subtitle = buildString {
                        append("$stageName (${resolved.stageIndex + 1}/${resolved.totalStages})")
                        append(" · 第${resolved.dayInStage}/${resolved.stageTotalDays}天")
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
                Text(
                    text = "$doneCount/${resolved.activeSlots.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allDone) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onEditPlan, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "编辑",
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        onClick = { onToggleSlot(resolved.plan.id, resolved.date, slot.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotCheckRow(
    slot: SlotDose,
    unit: String,
    accent: Color,
    record: CheckInRecord?,
    onClick: () -> Unit
) {
    val checked = record != null
    val bgColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "slotBg"
    )
    val checkBg by animateColorAsState(
        targetValue = if (checked) accent else Color.Transparent,
        label = "checkBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
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
