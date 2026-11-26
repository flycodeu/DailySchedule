package com.flycode.dailyschedule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flycode.dailyschedule.model.DurationUnit
import com.flycode.dailyschedule.model.PatternMode
import com.flycode.dailyschedule.model.PatternParser
import com.flycode.dailyschedule.model.PlanItem
import com.flycode.dailyschedule.model.PlanStage
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.model.SlotDose
import com.flycode.dailyschedule.ui.theme.PlanAccentColors
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private val PresetUnits = listOf("片", "粒", "次", "ml", "分钟")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorDialog(
    initialPlan: PlanItem?,
    onDismiss: () -> Unit,
    onSave: (PlanItem) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialPlan?.title ?: "") }
    var unit by remember { mutableStateOf(initialPlan?.unit ?: "片") }
    var customUnitMode by remember { mutableStateOf(initialPlan != null && initialPlan.unit !in PresetUnits) }
    var note by remember { mutableStateOf(initialPlan?.note ?: "") }
    var colorIndex by remember { mutableIntStateOf(initialPlan?.colorIndex ?: 0) }
    var startDate by remember { mutableStateOf(initialPlan?.startDate ?: LocalDate.now()) }
    var repeatCycle by remember { mutableStateOf(initialPlan?.repeatCycle ?: false) }

    val initialIsMultiStage = (initialPlan?.stages?.size ?: 1) > 1
    var isMultiStage by remember { mutableStateOf(initialIsMultiStage) }

    // Shared daily reminder times for quick sync across fixed or newly added stages
    var dailyTimes by remember {
        mutableStateOf(
            initialPlan?.stages?.firstOrNull { it.slots.isNotEmpty() }?.slots?.map { it.copy() }
                ?: defaultThreeSlots()
        )
    }

    // Fixed plan state (single stage)
    val firstStage = initialPlan?.stages?.firstOrNull()
    var fixedDurationText by remember { mutableStateOf((firstStage?.durationValue ?: 30).toString()) }
    var fixedDurationUnit by remember { mutableStateOf(firstStage?.durationUnit ?: DurationUnit.DAY) }
    var fixedSlots by remember {
        mutableStateOf(firstStage?.slots?.ifEmpty { null } ?: defaultThreeSlots())
    }

    // Multi-stage list state
    var stages by remember {
        mutableStateOf(
            initialPlan?.stages?.ifEmpty { null } ?: listOf(
                PlanStage(
                    durationValue = 7,
                    durationUnit = DurationUnit.DAY,
                    slots = defaultThreeSlots()
                )
            )
        )
    }

    var showSequenceDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun syncDailyTimesToFixed(newTimes: List<SlotDose>) {
        dailyTimes = newTimes
        fixedSlots = newTimes.mapIndexed { idx, slot ->
            val oldAmount = fixedSlots.getOrNull(idx)?.amount ?: slot.amount
            slot.copy(amount = oldAmount)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(if (initialPlan == null) "新建计划" else "编辑计划", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        val validStages = if (isMultiStage) {
                            stages
                        } else {
                            listOf(
                                PlanStage(
                                    id = firstStage?.id ?: UUID.randomUUID().toString().take(8),
                                    durationValue = fixedDurationText.toIntOrNull()?.coerceAtLeast(1) ?: 30,
                                    durationUnit = fixedDurationUnit,
                                    slots = fixedSlots
                                )
                            )
                        }
                        val canSave = title.isNotBlank() && validStages.isNotEmpty()
                        TextButton(
                            enabled = canSave,
                            onClick = {
                                onSave(
                                    PlanItem(
                                        id = initialPlan?.id ?: UUID.randomUUID().toString().take(8),
                                        title = title.trim(),
                                        unit = unit.trim().ifBlank { "次" },
                                        note = note.trim(),
                                        colorIndex = colorIndex,
                                        startDate = startDate,
                                        repeatCycle = repeatCycle,
                                        active = initialPlan?.active ?: true,
                                        stages = validStages
                                    )
                                )
                            }
                        ) {
                            Text("保存", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Basic Info
                item {
                    SectionTitle("基本信息")
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
                                placeholder = { Text("输入计划名称") },
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
                                        placeholder = { Text("自定义单位，如 袋、滴") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

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
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "开始日期",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "标识颜色",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PlanAccentColors.forEachIndexed { idx, color ->
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .then(
                                                    if (idx == colorIndex) {
                                                        Modifier.border(
                                                            2.5.dp,
                                                            MaterialTheme.colorScheme.onSurface,
                                                            CircleShape
                                                        )
                                                    } else Modifier
                                                )
                                                .clickable { colorIndex = idx }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "循环重复",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = repeatCycle,
                                    onCheckedChange = { repeatCycle = it }
                                )
                            }

                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("备注") },
                                placeholder = { Text("选填，如 饭后") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Section 2: Daily Reminder Times
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle("提醒时间", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val nextHour = (8 + dailyTimes.size * 4).coerceAtMost(23)
                            val added = SlotDose(
                                time = String.format("%02d:00", nextHour),
                                label = "",
                                amount = 1.0
                            )
                            syncDailyTimesToFixed(dailyTimes + added)
                        }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("添加时间")
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dailyTimes.forEachIndexed { idx, slot ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clickable {
                                                pickTime(context, slot.parsedTime) { newTime ->
                                                    val updated = dailyTimes.mapIndexed { i, s ->
                                                        if (i == idx) s.copy(time = newTime) else s
                                                    }.sortedBy { it.parsedTime }
                                                    syncDailyTimesToFixed(updated)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = slot.time,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (dailyTimes.size > 1) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "删除时间",
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .clickable {
                                                        syncDailyTimesToFixed(dailyTimes.filterIndexed { i, _ -> i != idx })
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

                // Section 3: Schedule Mode (Fixed vs Multi-Stage)
                item {
                    SectionTitle("计划安排")
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !isMultiStage,
                            onClick = { isMultiStage = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("固定计划")
                        }
                        SegmentedButton(
                            selected = isMultiStage,
                            onClick = {
                                if (!isMultiStage && stages.size == 1) {
                                    stages = listOf(
                                        stages[0].copy(
                                            durationValue = fixedDurationText.toIntOrNull()?.coerceAtLeast(1) ?: 7,
                                            durationUnit = fixedDurationUnit,
                                            slots = fixedSlots
                                        )
                                    )
                                }
                                isMultiStage = true
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("多阶段计划")
                        }
                    }
                }

                if (!isMultiStage) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DurationRow(
                                    durationText = fixedDurationText,
                                    durationUnit = fixedDurationUnit,
                                    onDurationTextChange = { fixedDurationText = it },
                                    onUnitChange = { fixedDurationUnit = it }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                                Text(
                                    text = "各时段数量（设为 0 则跳过该时段）",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                fixedSlots.forEachIndexed { idx, slot ->
                                    SlotAmountRow(
                                        time = slot.time,
                                        amount = slot.amount,
                                        unit = unit.ifBlank { "次" },
                                        onPickTime = {
                                            pickTime(context, slot.parsedTime) { newTime ->
                                                val updated = fixedSlots.mapIndexed { i, s ->
                                                    if (i == idx) s.copy(time = newTime) else s
                                                }.sortedBy { it.parsedTime }
                                                fixedSlots = updated
                                                dailyTimes = updated
                                            }
                                        },
                                        onChangeAmount = { newAmt ->
                                            fixedSlots = fixedSlots.mapIndexed { i, s ->
                                                if (i == idx) s.copy(amount = newAmt) else s
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "共 ${stages.size} 个阶段",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showSequenceDialog = true }) {
                                Text("按序列填充")
                            }
                            TextButton(onClick = {
                                val base = stages.lastOrNull()?.slots?.takeIf { it.isNotEmpty() } ?: dailyTimes
                                stages = stages + PlanStage(
                                    durationValue = 7,
                                    durationUnit = DurationUnit.DAY,
                                    slots = base.map { it.copy(id = UUID.randomUUID().toString().take(8)) }
                                )
                            }) {
                                Text("+ 添加阶段")
                            }
                        }
                    }

                    itemsIndexed(stages, key = { idx, s -> "${s.id}_$idx" }) { idx, stage ->
                        StageEditorCard(
                            index = idx,
                            stage = stage,
                            defaultSlots = dailyTimes,
                            unit = unit.ifBlank { "次" },
                            canDelete = stages.size > 1,
                            onUpdate = { updated ->
                                stages = stages.mapIndexed { i, item -> if (i == idx) updated else item }
                            },
                            onDelete = {
                                stages = stages.filterIndexed { i, _ -> i != idx }
                            }
                        )
                    }
                }

                if (initialPlan != null && onDelete != null) {
                    item {
                        Spacer(Modifier.height(8.dp))
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
    }

    if (showSequenceDialog) {
        SequenceBatchDialog(
            templateSlots = dailyTimes,
            onDismiss = { showSequenceDialog = false },
            onApply = { generated ->
                stages = generated
                showSequenceDialog = false
            }
        )
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
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
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

@Composable
private fun StageEditorCard(
    index: Int,
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
    var expandedCustomSlots by remember(stage.id) { mutableStateOf(false) }

    val distinctAmounts = stage.slots.map { it.amount }.distinct()
    val uniformAmount = if (distinctAmounts.size == 1) distinctAmounts.first() else null
    var uniformAmountText by remember(stage.id, uniformAmount) {
        mutableStateOf(ScheduleEngine.formatAmount(uniformAmount ?: 1.0))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "阶段 ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = stage.isRestStage,
                    onClick = {
                        if (stage.isRestStage) {
                            onUpdate(stage.copy(slots = defaultSlots.map { it.copy(id = UUID.randomUUID().toString().take(8)) }))
                        } else {
                            onUpdate(stage.copy(slots = emptyList()))
                            expandedCustomSlots = false
                        }
                    },
                    label = { Text("休息(0)") }
                )
                if (canDelete) {
                    Spacer(Modifier.width(4.dp))
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

            if (!stage.isRestStage) {
                if (!expandedCustomSlots && uniformAmount != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("每次", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = uniformAmountText,
                            onValueChange = { raw ->
                                val filtered = raw.filter { it.isDigit() || it == '.' }
                                uniformAmountText = filtered
                                filtered.toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { v ->
                                    onUpdate(stage.copy(slots = stage.slots.map { it.copy(amount = v) }))
                                }
                            },
                            suffix = { Text(unit) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.width(104.dp)
                        )
                        Text(
                            text = "每日 ${stage.slots.count { it.amount > 0 }} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { expandedCustomSlots = true }) {
                            Text("改时段")
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stage.slots.forEachIndexed { slotIdx, slot ->
                            SlotAmountRow(
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
                                    {
                                        onUpdate(stage.copy(slots = stage.slots.filterIndexed { i, _ -> i != slotIdx }))
                                    }
                                } else null
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = {
                                val nextHour = (8 + stage.slots.size * 4).coerceAtMost(23)
                                val added = SlotDose(
                                    time = String.format("%02d:00", nextHour),
                                    amount = stage.slots.lastOrNull()?.amount ?: 1.0
                                )
                                onUpdate(stage.copy(slots = stage.slots + added))
                            }) {
                                Text("+ 添加时段")
                            }
                            if (uniformAmount != null) {
                                TextButton(onClick = { expandedCustomSlots = false }) {
                                    Text("收起")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotAmountRow(
    time: String,
    amount: Double,
    unit: String,
    onPickTime: () -> Unit,
    onChangeAmount: (Double) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var text by remember(time, amount) {
        mutableStateOf(ScheduleEngine.formatAmount(amount))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onPickTime,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(time)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() || it == '.' }
                text = filtered
                filtered.toDoubleOrNull()?.takeIf { it >= 0.0 }?.let(onChangeAmount)
            },
            suffix = { Text(unit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SequenceBatchDialog(
    templateSlots: List<SlotDose>,
    onDismiss: () -> Unit,
    onApply: (List<PlanStage>) -> Unit
) {
    var patternText by remember { mutableStateOf("2, 2, 2, 2, 0") }
    var stepDuration by remember { mutableStateOf("1") }
    var stepUnit by remember { mutableStateOf(DurationUnit.DAY) }
    var mode by remember { mutableStateOf(PatternMode.PER_STEP_DOSE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按序列填充阶段", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = patternText,
                    onValueChange = { patternText = it },
                    label = { Text("阶段序列（0 为休息）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("2,2,2,2,0", "2,1,1,1,0", "2,0,2,0").forEach { preset ->
                        FilterChip(
                            selected = patternText.replace(" ", "") == preset,
                            onClick = { patternText = preset },
                            label = { Text(preset) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("每步", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = stepDuration,
                        onValueChange = { raw -> stepDuration = raw.filter { it.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(64.dp)
                    )
                    DurationUnit.entries.forEach { du ->
                        FilterChip(
                            selected = stepUnit == du,
                            onClick = { stepUnit = du },
                            label = { Text(du.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PatternMode.PER_STEP_DOSE, PatternMode.PER_STEP_FREQUENCY).forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val generated = PatternParser.generateStages(
                        rawPattern = patternText,
                        mode = mode,
                        stepDurationValue = stepDuration.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        stepDurationUnit = stepUnit,
                        templateTimes = templateSlots
                    )
                    if (generated.isNotEmpty()) {
                        onApply(generated)
                    }
                }
            ) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun defaultThreeSlots(): List<SlotDose> = listOf(
    SlotDose(id = UUID.randomUUID().toString().take(8), time = "08:00", label = "", amount = 1.0),
    SlotDose(id = UUID.randomUUID().toString().take(8), time = "13:00", label = "", amount = 1.0),
    SlotDose(id = UUID.randomUUID().toString().take(8), time = "20:00", label = "", amount = 1.0)
)

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
