package com.flycode.dailyschedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.model.AppState
import com.flycode.dailyschedule.model.ResolvedDayPlan
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.model.checkInKey
import java.time.LocalDate
import java.time.YearMonth

private enum class DayMarker {
    NONE,
    REST,
    PLANNED,
    PARTIAL,
    DONE
}

@Composable
fun CalendarScreen(
    state: AppState,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onToggleSlot: (planId: String, date: LocalDate, slotId: String) -> Unit,
    onAdjustDay: (ResolvedDayPlan) -> Unit,
    onEditPlan: (String) -> Unit,
    onCreatePlan: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        Text(
                            text = "${visibleMonth.year}年 ${visibleMonth.monthValue}月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        if (visibleMonth != YearMonth.from(today) || selectedDate != today) {
                            TextButton(onClick = {
                                visibleMonth = YearMonth.from(today)
                                onSelectDate(today)
                            }) {
                                Text("今天")
                            }
                        }
                        IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月")
                        }
                    }

                    Spacer(Modifier.height(6.dp))

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
                                        CalendarDayCell(
                                            date = date,
                                            isSelected = date == selectedDate,
                                            isToday = date == today,
                                            marker = dayMarkers[date] ?: DayMarker.NONE,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onSelectDate(date) }
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
                        LegendItem(color = Color(0xFF1B6B5C), label = "全部完成")
                        LegendItem(color = Color(0xFFD97706), label = "部分/待补")
                        LegendItem(color = Color(0xFF2E6FD8), label = "已排期")
                        LegendItem(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), label = "休息(0)")
                    }
                }
            }
        }

        item {
            Text(
                text = formatDateHeader(selectedDate),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item {
            DayScheduleSection(
                resolvedPlans = selectedResolved,
                checkIns = state.checkIns,
                onToggleSlot = onToggleSlot,
                onAdjustDay = onAdjustDay,
                onEditPlan = onEditPlan,
                onCreatePlan = onCreatePlan,
                emptyTitle = "暂无安排",
                emptySubtitle = ""
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    marker: DayMarker,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
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
