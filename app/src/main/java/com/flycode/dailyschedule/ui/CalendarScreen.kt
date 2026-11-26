package com.flycode.dailyschedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.model.AppState
import com.flycode.dailyschedule.model.PlanItem
import com.flycode.dailyschedule.model.ResolvedDayPlan
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.model.checkInKey
import com.flycode.dailyschedule.ui.theme.planAccentColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private enum class DayMarker {
    NONE,
    REST,
    PLANNED,
    PARTIAL,
    DONE
}

private data class RangePlanStat(
    val plan: PlanItem,
    val activeDays: Int,
    val restDays: Int,
    val totalSlots: Int,
    val doneSlots: Int
)

@Composable
fun CalendarScreen(
    state: AppState,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onToggleSlot: (planId: String, date: LocalDate, slotId: String) -> Unit,
    onAdjustDay: (ResolvedDayPlan) -> Unit,
    onEditPlan: (String) -> Unit,
    onToggleHiddenPlan: (String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onCreatePlan: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // Range selection state
    var rangeMode by remember { mutableStateOf(false) }
    var rangeStart by remember { mutableStateOf(today.minusDays(6)) }
    var rangeEnd by remember { mutableStateOf<LocalDate?>(today) }

    val effectiveRangeStart: LocalDate
    val effectiveRangeEnd: LocalDate
    if (rangeEnd == null) {
        effectiveRangeStart = rangeStart
        effectiveRangeEnd = rangeStart
    } else if (rangeEnd!!.isBefore(rangeStart)) {
        effectiveRangeStart = rangeEnd!!
        effectiveRangeEnd = rangeStart
    } else {
        effectiveRangeStart = rangeStart
        effectiveRangeEnd = rangeEnd!!
    }

    val dayMarkers = remember(state.plans, state.checkIns, visibleMonth, today) {
        buildMap<LocalDate, DayMarker> {
            val daysInMonth = visibleMonth.lengthOfMonth()
            for (d in 1..daysInMonth) {
                val date = visibleMonth.atDay(d)
                val resolved = ScheduleEngine.resolveAllForDate(state.plans, date)
                if (resolved.isEmpty()) {
                    put(date, DayMarker.NONE)
                    continue
                }
                val slots = resolved.flatMap { r -> r.activeSlots.map { r.plan.id to it.id } }
                if (slots.isEmpty()) {
                    put(date, DayMarker.REST)
                    continue
                }
                val done = slots.count { (pId, sId) ->
                    state.checkIns.containsKey(checkInKey(pId, date, sId))
                }
                val marker = when {
                    done == slots.size -> DayMarker.DONE
                    done > 0 -> DayMarker.PARTIAL
                    date.isBefore(today) -> DayMarker.PARTIAL
                    else -> DayMarker.PLANNED
                }
                put(date, marker)
            }
        }
    }

    val selectedResolved = remember(state.plans, selectedDate) {
        ScheduleEngine.resolveAllForDate(state.plans, selectedDate)
    }

    val rangeStats = remember(rangeMode, effectiveRangeStart, effectiveRangeEnd, state.plans, state.checkIns) {
        if (!rangeMode) emptyList()
        else {
            val spanDays = ChronoUnit.DAYS.between(effectiveRangeStart, effectiveRangeEnd).coerceIn(0L, 366L)
            state.plans.mapNotNull { plan ->
                var activeDays = 0
                var restDays = 0
                var totalSlots = 0
                var doneSlots = 0
                for (offset in 0L..spanDays) {
                    val date = effectiveRangeStart.plusDays(offset)
                    val resolved = ScheduleEngine.resolveDay(plan, date) ?: continue
                    if (resolved.isRestDay) {
                        restDays++
                    } else {
                        activeDays++
                        totalSlots += resolved.activeSlots.size
                        doneSlots += resolved.activeSlots.count { slot ->
                            state.checkIns.containsKey(checkInKey(plan.id, date, slot.id))
                        }
                    }
                }
                if (activeDays == 0 && restDays == 0) null
                else RangePlanStat(plan, activeDays, restDays, totalSlots, doneSlots)
            }
        }
    }

    var dragAccum by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccum = 0f },
                            onHorizontalDrag = { _, dragAmount -> dragAccum += dragAmount },
                            onDragEnd = {
                                if (dragAccum < -90f) {
                                    visibleMonth = visibleMonth.plusMonths(1)
                                } else if (dragAccum > 90f) {
                                    visibleMonth = visibleMonth.minusMonths(1)
                                }
                                dragAccum = 0f
                            }
                        )
                    },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月")
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showMonthPicker = true }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${visibleMonth.year}年 ${visibleMonth.monthValue}月 ▾",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilterChip(
                            selected = rangeMode,
                            onClick = {
                                rangeMode = !rangeMode
                                if (rangeMode) {
                                    rangeStart = selectedDate
                                    rangeEnd = selectedDate.plusDays(6)
                                }
                            },
                            label = { Text(if (rangeMode) "范围" else "单日") }
                        )
                        IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月")
                        }
                    }

                    if (rangeMode) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = effectiveRangeStart == today.minusDays(6) && effectiveRangeEnd == today,
                                onClick = {
                                    rangeStart = today.minusDays(6)
                                    rangeEnd = today
                                    visibleMonth = YearMonth.from(today)
                                },
                                label = { Text("近7天") }
                            )
                            FilterChip(
                                selected = effectiveRangeStart == visibleMonth.atDay(1) && effectiveRangeEnd == visibleMonth.atEndOfMonth(),
                                onClick = {
                                    rangeStart = visibleMonth.atDay(1)
                                    rangeEnd = visibleMonth.atEndOfMonth()
                                },
                                label = { Text("本月") }
                            )
                            FilterChip(
                                selected = effectiveRangeStart == today && effectiveRangeEnd == today.plusDays(29),
                                onClick = {
                                    rangeStart = today
                                    rangeEnd = today.plusDays(29)
                                    visibleMonth = YearMonth.from(today)
                                },
                                label = { Text("未来30天") }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val firstDay = visibleMonth.atDay(1)
                    val leadingBlanks = firstDay.dayOfWeek.value - 1
                    val totalDays = visibleMonth.lengthOfMonth()
                    val totalCells = ((leadingBlanks + totalDays + 6) / 7) * 7

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (rowStart in 0 until totalCells step 7) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val cellIndex = rowStart + col
                                    val dayNumber = cellIndex - leadingBlanks + 1
                                    if (dayNumber in 1..totalDays) {
                                        val date = visibleMonth.atDay(dayNumber)
                                        val inRange = rangeMode &&
                                            !date.isBefore(effectiveRangeStart) &&
                                            !date.isAfter(effectiveRangeEnd)
                                        val isRangeBound = rangeMode &&
                                            (date == effectiveRangeStart || date == effectiveRangeEnd)
                                        CalendarDayCell(
                                            date = date,
                                            isSelected = if (rangeMode) isRangeBound else date == selectedDate,
                                            inRange = inRange && !isRangeBound,
                                            isToday = date == today,
                                            marker = dayMarkers[date] ?: DayMarker.NONE,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                if (rangeMode) {
                                                    if (rangeEnd != null) {
                                                        rangeStart = date
                                                        rangeEnd = null
                                                    } else {
                                                        rangeEnd = date
                                                    }
                                                } else {
                                                    onSelectDate(date)
                                                }
                                            }
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LegendItem(color = Color(0xFF1B6B5C), label = "完成")
                        LegendItem(color = Color(0xFFD97706), label = "未满")
                        LegendItem(color = Color(0xFF2E6FD8), label = "排期")
                        LegendItem(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), label = "休息")
                    }
                }
            }
        }

        if (rangeMode) {
            val totalSpanDays = ChronoUnit.DAYS.between(effectiveRangeStart, effectiveRangeEnd).toInt() + 1
            val allTotalSlots = rangeStats.sumOf { it.totalSlots }
            val allDoneSlots = rangeStats.sumOf { it.doneSlots }
            val overallRatio = if (allTotalSlots > 0) allDoneSlots.toFloat() / allTotalSlots else 0f

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${effectiveRangeStart.monthValue}/${effectiveRangeStart.dayOfMonth} ~ ${effectiveRangeEnd.monthValue}/${effectiveRangeEnd.dayOfMonth}（共 $totalSpanDays 天）",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (allTotalSlots > 0) {
                                        "总计完成 $allDoneSlots / $allTotalSlots 次（${(overallRatio * 100).toInt()}%）"
                                    } else {
                                        "该范围内无待执行时段"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (rangeEnd == null) {
                                StagePill(text = "再点结束日", color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (allTotalSlots > 0) {
                            LinearProgressIndicator(
                                progress = { overallRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                strokeCap = StrokeCap.Round
                            )
                        }

                        if (rangeStats.isEmpty()) {
                            Text(
                                text = "所选范围暂无排期计划",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            rangeStats.forEach { stat ->
                                val accent = planAccentColor(stat.plan.colorIndex)
                                val ratio = if (stat.totalSlots > 0) stat.doneSlots.toFloat() / stat.totalSlots else 0f
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onEditPlan(stat.plan.id) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stat.plan.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = buildString {
                                                append("执行 ${stat.activeDays} 天")
                                                if (stat.restDays > 0) append(" · 休息 ${stat.restDays} 天")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${stat.doneSlots}/${stat.totalSlots}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accent
                                        )
                                        Text(
                                            text = "${(ratio * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDateHeader(selectedDate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (visibleMonth != YearMonth.from(today) || selectedDate != today) {
                        TextButton(onClick = {
                            visibleMonth = YearMonth.from(today)
                            onSelectDate(today)
                        }) {
                            Text("回今天")
                        }
                    }
                }
            }

            item {
                DayScheduleSection(
                    resolvedPlans = selectedResolved,
                    checkIns = state.checkIns,
                    onToggleSlot = onToggleSlot,
                    onAdjustDay = onAdjustDay,
                    onEditPlan = onEditPlan,
                    onToggleHiddenPlan = onToggleHiddenPlan,
                    onDeletePlan = onDeletePlan,
                    onCreatePlan = onCreatePlan,
                    emptyTitle = "暂无安排",
                    emptySubtitle = ""
                )
            }
        }
    }

    if (showMonthPicker) {
        YearMonthPickerDialog(
            initial = visibleMonth,
            onDismiss = { showMonthPicker = false },
            onSelect = { chosen ->
                visibleMonth = chosen
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun YearMonthPickerDialog(
    initial: YearMonth,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit
) {
    var year by remember { mutableIntStateOf(initial.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { year-- }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一年")
                }
                Text("${year}年", fontWeight = FontWeight.Bold)
                IconButton(onClick = { year++ }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一年")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 1..3) {
                            val month = row * 3 + col
                            val selected = year == initial.year && month == initial.monthValue
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelect(YearMonth.of(year, month)) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = "${month}月",
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
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
private fun CalendarDayCell(
    date: LocalDate,
    isSelected: Boolean,
    inRange: Boolean,
    isToday: Boolean,
    marker: DayMarker,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        inRange -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(
                        width = 1.4.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            Spacer(Modifier.height(3.dp))
            when (marker) {
                DayMarker.NONE -> Spacer(Modifier.height(5.dp))
                DayMarker.REST -> Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                )
                else -> {
                    val dotColor = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        marker == DayMarker.DONE -> Color(0xFF1B6B5C)
                        marker == DayMarker.PARTIAL -> Color(0xFFD97706)
                        else -> Color(0xFF2E6FD8)
                    }
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
