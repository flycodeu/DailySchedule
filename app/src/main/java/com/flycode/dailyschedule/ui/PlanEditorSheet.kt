package com.flycode.dailyschedule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.model.DurationUnit
import com.flycode.dailyschedule.model.PlanItem
import com.flycode.dailyschedule.model.PlanStage
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.model.SlotDose
import com.flycode.dailyschedule.ui.theme.PlanAccentColors
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private val PresetUnits = listOf("片", "粒", "次", "ml", "分钟")

private enum class ScheduleMode(val label: String, val desc: String) {
    FIXED("每日固定", "每天按相同时间与数量执行"),
    DAILY("逐日安排", "每天单独设数量，如今天 1、明天 1、后天 2"),
    STAGED("多阶段", "按周或按月分阶段调整数量与频次")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(
    initialPlan: PlanItem?,
    onDismiss: () -> Unit,
    onSave: (PlanItem) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    var title by remember { mutableStateOf(initialPlan?.title ?: "") }
    var unit by remember { mutableStateOf(initialPlan?.unit ?: "片") }
    var customUnitMode by remember { mutableStateOf(initialPlan != null && initialPlan.unit !in PresetUnits) }
    var note by remember { mutableStateOf(initialPlan?.note ?: "") }
    var colorIndex by remember { mutableIntStateOf(initialPlan?.colorIndex ?: 0) }
    var startDate by remember { mutableStateOf(initialPlan?.startDate ?: LocalDate.now()) }
    var repeatCycle by remember { mutableStateOf(initialPlan?.repeatCycle ?: true) }

    val detectedMode = remember(initialPlan) {
        val list = initialPlan?.stages.orEmpty()
        when {
            list.size <= 1 -> ScheduleMode.FIXED
            list.all { it.durationUnit == DurationUnit.DAY && it.durationValue == 1 } -> ScheduleMode.DAILY
            else -> ScheduleMode.STAGED
        }
    }
    var mode by remember { mutableStateOf(detectedMode) }

    // Shared default times used by DAILY and STAGED modes
    var sharedTimes by remember {
        mutableStateOf(
            initialPlan?.stages?.firstOrNull { it.slots.isNotEmpty() }?.slots?.map { it.copy() }
                ?: defaultThreeSlots()
        )
    }

    // FIXED mode state
    val firstStage = initialPlan?.stages?.firstOrNull()
    var fixedDurationText by remember {
        val defaultVal = if (firstStage != null && !(initialPlan.repeatCycle && firstStage.durationValue == 1 && firstStage.durationUnit == DurationUnit.DAY)) {
            firstStage.durationValue
        } else {
            30
        }
        mutableStateOf(defaultVal.toString())
    }
    var fixedDurationUnit by remember {
        mutableStateOf(
            if (firstStage != null && !(initialPlan.repeatCycle && firstStage.durationValue == 1 && firstStage.durationUnit == DurationUnit.DAY)) {
                firstStage.durationUnit
            } else {
                DurationUnit.DAY
            }
        )
    }
    var fixedSlots by remember {
        mutableStateOf(firstStage?.slots?.ifEmpty { null } ?: defaultThreeSlots())
    }

    // DAILY mode state (1-day stages)
    var dailyStages by remember {
        mutableStateOf(
            if (detectedMode == ScheduleMode.DAILY && !initialPlan?.stages.isNullOrEmpty()) {
                initialPlan!!.stages
            } else {
                listOf(
                    PlanStage(durationValue = 1, durationUnit = DurationUnit.DAY, slots = cloneSlots(sharedTimes, 1.0)),
                    PlanStage(durationValue = 1, durationUnit = DurationUnit.DAY, slots = cloneSlots(sharedTimes, 1.0)),
                    PlanStage(durationValue = 1, durationUnit = DurationUnit.DAY, slots = cloneSlots(sharedTimes, 1.0))
                )
            }
        )
    }

    // STAGED mode state (multi-week/month phases)
    var phasedStages by remember {
        mutableStateOf(
            if (detectedMode == ScheduleMode.STAGED && !initialPlan?.stages.isNullOrEmpty()) {
                initialPlan!!.stages
            } else {
                listOf(
                    PlanStage(durationValue = 1, durationUnit = DurationUnit.MONTH, slots = cloneSlots(sharedTimes, 2.0)),
                    PlanStage(durationValue = 1, durationUnit = DurationUnit.MONTH, slots = cloneSlots(sharedTimes, 1.0))
                )
            }
        )
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun updateSharedTimes(newTimes: List<SlotDose>) {
        sharedTimes = newTimes
        dailyStages = dailyStages.map { st ->
            if (st.isRestStage) st
            else {
                val amt = st.slots.firstOrNull { it.amount > 0 }?.amount ?: 1.0
                st.copy(slots = cloneSlots(newTimes, amt))
            }
        }
    }

    fun buildStagesForSave(): List<PlanStage> = when (mode) {
        ScheduleMode.FIXED -> listOf(
            PlanStage(
                id = firstStage?.id ?: UUID.randomUUID().toString().take(8),
                durationValue = if (repeatCycle) 1 else (fixedDurationText.toIntOrNull()?.coerceAtLeast(1) ?: 30),
                durationUnit = if (repeatCycle) DurationUnit.DAY else fixedDurationUnit,
                slots = fixedSlots
            )
        )
        ScheduleMode.DAILY -> dailyStages
        ScheduleMode.STAGED -> phasedStages
    }

    val safeUnit = unit.trim().ifBlank { "次" }
    val stageDateRanges = remember(phasedStages, startDate) {
        var cursor = startDate
        phasedStages.map { st ->
            val end = st.durationUnit.addTo(cursor, st.durationValue).minusDays(1)
            val pair = cursor to end
            cursor = end.plusDays(1)
            pair
        }
    }
    val previewSummary = remember(mode, startDate, repeatCycle, fixedDurationText, fixedDurationUnit, fixedSlots, dailyStages, phasedStages, safeUnit) {
        val dateStr = "${startDate.monthValue}/${startDate.dayOfMonth}"
        when (mode) {
            ScheduleMode.FIXED -> {
                val activeCount = fixedSlots.count { it.amount > 0.0 }
                val durPart = if (repeatCycle) "长期每天" else "持续 ${fixedDurationText.ifBlank { "30" }}${fixedDurationUnit.label}"
                "$dateStr 起 · 每日 $activeCount 次 · $durPart"
            }
            ScheduleMode.DAILY -> {
                val seq = dailyStages.take(5).joinToString(" → ") { st ->
                    if (st.isRestStage) "休" else ScheduleEngine.formatAmount(st.slots.maxOfOrNull { it.amount } ?: 0.0)
                } + if (dailyStages.size > 5) "…" else ""
                val loopPart = if (repeatCycle) "循环" else "单轮"
                "$dateStr 起 · ${dailyStages.size} 天$loopPart（$seq $safeUnit）"
            }
            ScheduleMode.STAGED -> {
                val phases = phasedStages.take(4).joinToString(" → ") { st ->
                    "${st.durationValue}${st.durationUnit.label}"
                } + if (phasedStages.size > 4) "…" else ""
                val loopPart = if (repeatCycle) " · 循环" else ""
                "$dateStr 起 · ${phasedStages.size} 个阶段（$phases）$loopPart"
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(if (initialPlan == null) "新建计划" else "编辑计划", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    val stagesToSave = buildStagesForSave()
                    Button(
                        enabled = title.isNotBlank() && stagesToSave.isNotEmpty(),
                        onClick = {
                            onSave(
                                PlanItem(
                                    id = initialPlan?.id ?: UUID.randomUUID().toString().take(8),
                                    title = title.trim(),
                                    unit = safeUnit,
                                    note = note.trim(),
                                    colorIndex = colorIndex,
                                    startDate = startDate,
                                    repeatCycle = repeatCycle,
                                    active = initialPlan?.active ?: true,
                                    stages = stagesToSave,
                                    dayOverrides = initialPlan?.dayOverrides ?: emptyMap()
                                )
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Plain-Language Preview Banner
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(PlanAccentColors[colorIndex.mod(PlanAccentColors.size)])
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = previewSummary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Card 1: Basic Identity Only
            item {
                EditorSectionHeader("1. 计划信息")
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("名称") },
                            placeholder = { Text("例如：维生素、每日饮水") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "单位",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PresetUnits.forEach { preset ->
                                    FilterChip(
                                        selected = !customUnitMode && unit == preset,
                                        onClick = {
                                            customUnitMode = false
                                            unit = preset
                                        },
                                        label = { Text(preset) }
                                    )
                                }
                                FilterChip(
                                    selected = customUnitMode,
                                    onClick = { customUnitMode = true },
                                    label = { Text("自定义") }
                                )
                            }
                            if (customUnitMode) {
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = { unit = it },
                                    placeholder = { Text("输入单位") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("备注（选填）") },
                            placeholder = { Text("如：饭后半小时") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "颜色标记",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PlanAccentColors.forEachIndexed { idx, color ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .then(
                                                if (idx == colorIndex) {
                                                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                } else Modifier
                                            )
                                            .clickable { colorIndex = idx }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Card 2: Schedule Mode + Date + Times (Unified, No Duplication)
            item {
                EditorSectionHeader("2. 执行安排")
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ScheduleMode.entries.forEachIndexed { idx, item ->
                                SegmentedButton(
                                    selected = mode == item,
                                    onClick = {
                                        if (mode != item) {
                                            if (item == ScheduleMode.DAILY && initialPlan == null) {
                                                repeatCycle = false
                                            }
                                            if (item == ScheduleMode.STAGED && initialPlan == null) {
                                                repeatCycle = false
                                            }
                                            mode = item
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = ScheduleMode.entries.size)
                                ) {
                                    Text(item.label)
                                }
                            }
                        }

                        Text(
                            text = mode.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d -> startDate = LocalDate.of(y, m + 1, d) },
                                        startDate.year,
                                        startDate.monthValue - 1,
                                        startDate.dayOfMonth
                                    ).show()
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("开始日期", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                text = startDate.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (mode) {
                                    ScheduleMode.FIXED -> "长期每天重复"
                                    ScheduleMode.DAILY -> "按以上天数循环"
                                    ScheduleMode.STAGED -> "全部阶段结束后循环"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = repeatCycle, onCheckedChange = { repeatCycle = it })
                        }

                        if (mode == ScheduleMode.FIXED) {
                            if (!repeatCycle) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                DurationRow(
                                    durationText = fixedDurationText,
                                    durationUnit = fixedDurationUnit,
                                    onDurationTextChange = { fixedDurationText = it },
                                    onUnitChange = { fixedDurationUnit = it }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "每日时间与数量",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    val nextHour = (8 + fixedSlots.size * 4).coerceAtMost(23)
                                    val added = SlotDose(time = String.format("%02d:00", nextHour), amount = 1.0)
                                    val next = (fixedSlots + added).sortedBy { it.parsedTime }
                                    fixedSlots = next
                                    sharedTimes = next
                                }) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("添加时间")
                                }
                            }

                            fixedSlots.forEachIndexed { idx, slot ->
                                SlotStepperRow(
                                    time = slot.time,
                                    amount = slot.amount,
                                    unit = safeUnit,
                                    onPickTime = {
                                        pickTime(context, slot.parsedTime) { newTime ->
                                            val updated = fixedSlots.mapIndexed { i, s ->
                                                if (i == idx) s.copy(time = newTime) else s
                                            }.sortedBy { it.parsedTime }
                                            fixedSlots = updated
                                            sharedTimes = updated
                                        }
                                    },
                                    onChangeAmount = { newAmt ->
                                        fixedSlots = fixedSlots.mapIndexed { i, s ->
                                            if (i == idx) s.copy(amount = newAmt) else s
                                        }
                                    },
                                    onDelete = if (fixedSlots.size > 1) {
                                        {
                                            val next = fixedSlots.filterIndexed { i, _ -> i != idx }
                                            fixedSlots = next
                                            sharedTimes = next
                                        }
                                    } else null
                                )
                            }
                        } else {
                            // Compact shared time pills for DAILY / STAGED
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "默认每日时间",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    val nextHour = (8 + sharedTimes.size * 4).coerceAtMost(23)
                                    val added = SlotDose(time = String.format("%02d:00", nextHour), amount = 1.0)
                                    updateSharedTimes((sharedTimes + added).sortedBy { it.parsedTime })
                                }) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("加时间")
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sharedTimes.forEachIndexed { idx, slot ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .clickable {
                                                    pickTime(context, slot.parsedTime) { newTime ->
                                                        val updated = sharedTimes.mapIndexed { i, s ->
                                                            if (i == idx) s.copy(time = newTime) else s
                                                        }.sortedBy { it.parsedTime }
                                                        updateSharedTimes(updated)
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(slot.time, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                            if (sharedTimes.size > 1) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    contentDescription = "删除时间",
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable {
                                                            updateSharedTimes(sharedTimes.filterIndexed { i, _ -> i != idx })
                                                        },
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (mode == ScheduleMode.DAILY) {
                itemsIndexed(dailyStages, key = { idx, s -> "${s.id}_$idx" }) { idx, stage ->
                    val previewDate = startDate.plusDays(idx.toLong())
                    DayPlanCard(
                        dayNumber = idx + 1,
                        date = previewDate,
                        stage = stage,
                        defaultSlots = sharedTimes,
                        unit = safeUnit,
                        canDelete = dailyStages.size > 1,
                        onUpdate = { updated ->
                            dailyStages = dailyStages.mapIndexed { i, item -> if (i == idx) updated else item }
                        },
                        onDelete = {
                            dailyStages = dailyStages.filterIndexed { i, _ -> i != idx }
                        }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = {
                            val lastAmt = dailyStages.lastOrNull()?.slots?.firstOrNull { it.amount > 0 }?.amount ?: 1.0
                            dailyStages = dailyStages + PlanStage(
                                durationValue = 1,
                                durationUnit = DurationUnit.DAY,
                                slots = cloneSlots(sharedTimes, lastAmt)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加第 ${dailyStages.size + 1} 天")
                    }
                }
            }

            if (mode == ScheduleMode.STAGED) {
                itemsIndexed(phasedStages, key = { idx, s -> "${s.id}_$idx" }) { idx, stage ->
                    val range = stageDateRanges.getOrNull(idx)
                    val rangeLabel = range?.let { (s, e) ->
                        "${s.monthValue}/${s.dayOfMonth} ~ ${e.monthValue}/${e.dayOfMonth}"
                    }.orEmpty()
                    StageEditorCard(
                        index = idx,
                        rangeLabel = rangeLabel,
                        stage = stage,
                        defaultSlots = sharedTimes,
                        unit = safeUnit,
                        canDelete = phasedStages.size > 1,
                        onUpdate = { updated ->
                            phasedStages = phasedStages.mapIndexed { i, item -> if (i == idx) updated else item }
                        },
                        onDelete = {
                            phasedStages = phasedStages.filterIndexed { i, _ -> i != idx }
                        }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = {
                            phasedStages = phasedStages + PlanStage(
                                durationValue = 1,
                                durationUnit = DurationUnit.MONTH,
                                slots = cloneSlots(sharedTimes, 1.0)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加阶段 ${phasedStages.size + 1}")
                    }
                }
            }

            if (initialPlan != null && onDelete != null) {
                item {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Text("删除此计划", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && initialPlan != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除计划") },
            text = { Text("确定删除「${initialPlan.title}」及历史记录？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(initialPlan.id)
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun DayPlanCard(
    dayNumber: Int,
    date: LocalDate,
    stage: PlanStage,
    defaultSlots: List<SlotDose>,
    unit: String,
    canDelete: Boolean,
    onUpdate: (PlanStage) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var expandSlots by remember(stage.id) { mutableStateOf(false) }
    val currentAmount = if (stage.isRestStage) 0.0 else (stage.slots.maxOfOrNull { it.amount } ?: 1.0)
    val today = remember { LocalDate.now() }
    val relativeHint = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.plusDays(2) -> "后天"
        else -> "周${chineseWeekdayShort(date.dayOfWeek)}"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "第 $dayNumber 天",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${date.monthValue}/${date.dayOfMonth} $relativeHint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AmountStepper(
                    amount = currentAmount,
                    unit = unit,
                    zeroLabel = "休息",
                    onChange = { nextAmt ->
                        if (nextAmt <= 0.0) {
                            onUpdate(stage.copy(slots = emptyList()))
                            expandSlots = false
                        } else {
                            val base = stage.slots.ifEmpty { defaultSlots }
                            onUpdate(stage.copy(slots = cloneSlots(base, nextAmt)))
                        }
                    }
                )

                if (!stage.isRestStage) {
                    TextButton(
                        onClick = { expandSlots = !expandSlots },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(if (expandSlots) "收起" else "时段", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "删除此天",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (expandSlots && stage.slots.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                stage.slots.forEachIndexed { slotIdx, slot ->
                    SlotStepperRow(
                        time = slot.time,
                        amount = slot.amount,
                        unit = unit,
                        onPickTime = {
                            pickTime(context, slot.parsedTime) { newTime ->
                                val next = stage.slots.mapIndexed { i, s ->
                                    if (i == slotIdx) s.copy(time = newTime) else s
                                }.sortedBy { it.parsedTime }
                                onUpdate(stage.copy(slots = next))
                            }
                        },
                        onChangeAmount = { newAmt ->
                            val next = stage.slots.mapIndexed { i, s ->
                                if (i == slotIdx) s.copy(amount = newAmt) else s
                            }
                            onUpdate(stage.copy(slots = next))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StageEditorCard(
    index: Int,
    rangeLabel: String,
    stage: PlanStage,
    defaultSlots: List<SlotDose>,
    unit: String,
    canDelete: Boolean,
    onUpdate: (PlanStage) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var durationText by remember(stage.id, stage.durationValue) {
        mutableStateOf(stage.durationValue.toString())
    }
    var expandSlots by remember(stage.id) { mutableStateOf(false) }
    val uniformAmount = if (stage.isRestStage) 0.0 else (stage.slots.maxOfOrNull { it.amount } ?: 1.0)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "阶段 ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (rangeLabel.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = rangeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除阶段",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            DurationRow(
                durationText = durationText,
                durationUnit = stage.durationUnit,
                onDurationTextChange = { digits ->
                    durationText = digits
                    digits.toIntOrNull()?.coerceAtLeast(1)?.let { v ->
                        onUpdate(stage.copy(durationValue = v))
                    }
                },
                onUnitChange = { onUpdate(stage.copy(durationUnit = it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (stage.isRestStage) "本阶段状态" else "每次数量",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                AmountStepper(
                    amount = uniformAmount,
                    unit = unit,
                    zeroLabel = "休息",
                    onChange = { nextAmt ->
                        if (nextAmt <= 0.0) {
                            onUpdate(stage.copy(slots = emptyList()))
                            expandSlots = false
                        } else {
                            val base = stage.slots.ifEmpty { defaultSlots }
                            onUpdate(stage.copy(slots = cloneSlots(base, nextAmt)))
                        }
                    }
                )
                if (!stage.isRestStage) {
                    TextButton(
                        onClick = { expandSlots = !expandSlots },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(if (expandSlots) "收起" else "改时段")
                    }
                }
            }

            if (expandSlots && stage.slots.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                stage.slots.forEachIndexed { slotIdx, slot ->
                    SlotStepperRow(
                        time = slot.time,
                        amount = slot.amount,
                        unit = unit,
                        onPickTime = {
                            pickTime(context, slot.parsedTime) { newTime ->
                                val next = stage.slots.mapIndexed { i, s ->
                                    if (i == slotIdx) s.copy(time = newTime) else s
                                }.sortedBy { it.parsedTime }
                                onUpdate(stage.copy(slots = next))
                            }
                        },
                        onChangeAmount = { newAmt ->
                            val next = stage.slots.mapIndexed { i, s ->
                                if (i == slotIdx) s.copy(amount = newAmt) else s
                            }
                            onUpdate(stage.copy(slots = next))
                        },
                        onDelete = if (stage.slots.size > 1) {
                            { onUpdate(stage.copy(slots = stage.slots.filterIndexed { i, _ -> i != slotIdx })) }
                        } else null
                    )
                }
                TextButton(onClick = {
                    val nextHour = (8 + stage.slots.size * 4).coerceAtMost(23)
                    val added = SlotDose(
                        time = String.format("%02d:00", nextHour),
                        amount = stage.slots.lastOrNull()?.amount ?: 1.0
                    )
                    onUpdate(stage.copy(slots = (stage.slots + added).sortedBy { it.parsedTime }))
                }) {
                    Text("+ 添加时段")
                }
            }
        }
    }
}

@Composable
fun AmountStepper(
    amount: Double,
    unit: String,
    zeroLabel: String = "跳过",
    onChange: (Double) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showDirectInput by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(enabled = amount > 0.0) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange((amount - 1.0).coerceAtLeast(0.0))
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (amount > 0.0) 0.9f else 0.35f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "−",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (amount > 0.0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = if (amount <= 0.0) zeroLabel else "${ScheduleEngine.formatAmount(amount)} $unit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (amount <= 0.0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDirectInput = true }
                .padding(vertical = 4.dp)
        )

        Surface(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(amount + 1.0)
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showDirectInput) {
        var draft by remember(amount) { mutableStateOf(ScheduleEngine.formatAmount(amount)) }
        AlertDialog(
            onDismissRequest = { showDirectInput = false },
            title = { Text("输入数量 ($unit)") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { raw -> draft = raw.filter { it.isDigit() || it == '.' } },
                    placeholder = { Text("支持小数，0 表示跳过/休息") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    draft.toDoubleOrNull()?.takeIf { it >= 0.0 }?.let(onChange)
                    showDirectInput = false
                }) {
                    Text("确定", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectInput = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun SlotStepperRow(
    time: String,
    amount: Double,
    unit: String,
    onPickTime: () -> Unit,
    onChangeAmount: (Double) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPickTime,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(time, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        AmountStepper(
            amount = amount,
            unit = unit,
            zeroLabel = "跳过",
            onChange = onChangeAmount
        )
        if (onDelete != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EditorSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun DurationRow(
    durationText: String,
    durationUnit: DurationUnit,
    onDurationTextChange: (String) -> Unit,
    onUnitChange: (DurationUnit) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("持续", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = durationText,
            onValueChange = { raw -> onDurationTextChange(raw.filter { it.isDigit() }) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(72.dp)
        )
        DurationUnit.entries.forEach { du ->
            FilterChip(
                selected = durationUnit == du,
                onClick = { onUnitChange(du) },
                label = { Text(du.label) }
            )
        }
    }
}

private fun defaultThreeSlots(): List<SlotDose> = listOf(
    SlotDose(id = UUID.randomUUID().toString().take(8), time = "08:00", amount = 1.0),
    SlotDose(id = UUID.randomUUID().toString().take(8), time = "13:00", amount = 1.0),
    SlotDose(id = UUID.randomUUID().toString().take(8), time = "20:00", amount = 1.0)
)

private fun cloneSlots(base: List<SlotDose>, amount: Double): List<SlotDose> =
    base.map {
        it.copy(id = UUID.randomUUID().toString().take(8), amount = amount)
    }

private fun pickTime(
    context: android.content.Context,
    initial: LocalTime,
    onPicked: (String) -> Unit
) {
    TimePickerDialog(
        context,
        { _, hour, minute ->
            onPicked(String.format("%02d:%02d", hour, minute))
        },
        initial.hour,
        initial.minute,
        true
    ).show()
}
