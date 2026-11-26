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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorDialog(
    initialPlan: PlanItem?,
    onDismiss: () -> Unit,
    onSave: (PlanItem) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialPlan?.title ?: "") }
    var unit by remember { mutableStateOf(initialPlan?.unit ?: "片") }
    var note by remember { mutableStateOf(initialPlan?.note ?: "") }
    var colorIndex by remember { mutableIntStateOf(initialPlan?.colorIndex ?: 0) }
    var startDate by remember { mutableStateOf(initialPlan?.startDate ?: LocalDate.now()) }
    var repeatCycle by remember { mutableStateOf(initialPlan?.repeatCycle ?: false) }

    var stages by remember {
        mutableStateOf(
            initialPlan?.stages?.ifEmpty { null } ?: listOf(
                PlanStage(
                    name = "阶段 1",
                    durationValue = 7,
                    durationUnit = DurationUnit.DAY,
                    slots = defaultThreeSlots()
                )
            )
        )
    }

    // Quick sequence builder state
    var showPatternBuilder by remember { mutableStateOf(initialPlan == null) }
    var patternText by remember { mutableStateOf("2, 2, 2, 2, 0") }
    var patternMode by remember { mutableStateOf(PatternMode.PER_STEP_DOSE) }
    var stepDuration by remember { mutableStateOf("1") }
    var stepUnit by remember { mutableStateOf(DurationUnit.DAY) }
    var templateSlots by remember { mutableStateOf(defaultThreeSlots()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (initialPlan == null) "新建计划" else "编辑计划") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        val canSave = title.isNotBlank() && stages.isNotEmpty()
                        TextButton(
                            enabled = canSave,
                            onClick = {
                                val saved = PlanItem(
                                    id = initialPlan?.id ?: UUID.randomUUID().toString().take(8),
                                    title = title.trim(),
                                    unit = unit.trim().ifBlank { "次" },
                                    note = note.trim(),
                                    colorIndex = colorIndex,
                                    startDate = startDate,
                                    repeatCycle = repeatCycle,
                                    active = initialPlan?.active ?: true,
                                    stages = stages
                                )
                                onSave(saved)
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("计划名称（如 维生素D、日常方案A）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = { unit = it },
                                    label = { Text("单位") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.38f)
                                )
                                OutlinedTextField(
                                    value = note,
                                    onValueChange = { note = it },
                                    label = { Text("备注（如 饭后、温水）") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.62f)
                                )
                            }

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("片", "粒", "次", "ml", "分钟").forEach { preset ->
                                    FilterChip(
                                        selected = unit == preset,
                                        onClick = { unit = preset },
                                        label = { Text(preset) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PlanAccentColors.forEachIndexed { idx, color ->
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
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

                                OutlinedButton(
                                    onClick = {
                                        DatePickerDialog(
                                            context,
                                            { _, y, m, d -> startDate = LocalDate.of(y, m + 1, d) },
                                            startDate.year,
                                            startDate.monthValue - 1,
                                            startDate.dayOfMonth
                                        ).show()
                                    }
                                ) {
                                    Text("开始: $startDate")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("循环执行全部阶段", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "开启后到达最后阶段会自动回到第 1 阶段循环",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = repeatCycle,
                                    onCheckedChange = { repeatCycle = it }
                                )
                            }
                        }
                    }
                }

                // Quick Pattern Generator Card
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPatternBuilder = !showPatternBuilder },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "序列快填（如 2,2,2,2,0 / 2,1,1,1,0 / 2,0,2,0）",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "一键按天、按周、按月或单日多时段生成阶段",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    if (showPatternBuilder) "收起" else "展开",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (showPatternBuilder) {
                                OutlinedTextField(
                                    value = patternText,
                                    onValueChange = { patternText = it },
                                    label = { Text("输入阶段数值序列（逗号或空格分隔）") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("2,2,2,2,0", "2,1,1,1,0", "2,0,2,0", "3,2,1,0").forEach { ex ->
                                        FilterChip(
                                            selected = patternText.replace(" ", "") == ex,
                                            onClick = { patternText = ex },
                                            label = { Text(ex) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PatternMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = patternMode == mode,
                                            onClick = { patternMode = mode },
                                            label = { Text(mode.label) }
                                        )
                                    }
                                }
                                Text(
                                    text = patternMode.hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = stepDuration,
                                        onValueChange = { s -> stepDuration = s.filter { it.isDigit() } },
                                        label = { Text("每步时长") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width(96.dp)
                                    )
                                    DurationUnit.entries.forEach { du ->
                                        FilterChip(
                                            selected = stepUnit == du,
                                            onClick = { stepUnit = du },
                                            label = { Text(du.label) }
                                        )
                                    }
                                }

                                Text(
                                    "每日默认提醒时段（点击修改时间）：",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    templateSlots.forEachIndexed { idx, slot ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .clickable {
                                                        pickTime(context, slot.parsedTime) { newTime ->
                                                            templateSlots = templateSlots.mapIndexed { i, s ->
                                                                if (i == idx) s.copy(time = newTime) else s
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${slot.time} ${slot.label}", style = MaterialTheme.typography.labelMedium)
                                                if (templateSlots.size > 1) {
                                                    Spacer(Modifier.width(4.dp))
                                                    Icon(
                                                        Icons.Rounded.Close,
                                                        contentDescription = "删除时段",
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clickable {
                                                                templateSlots = templateSlots.filterIndexed { i, _ -> i != idx }
                                                            }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val nextHour = (8 + templateSlots.size * 4).coerceAtMost(23)
                                            templateSlots = templateSlots + SlotDose(
                                                time = String.format("%02d:00", nextHour),
                                                label = "时段${templateSlots.size + 1}",
                                                amount = 1.0
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("+ 时段")
                                    }
                                }

                                Button(
                                    onClick = {
                                        val generated = PatternParser.generateStages(
                                            rawPattern = patternText,
                                            mode = patternMode,
                                            stepDurationValue = stepDuration.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                            stepDurationUnit = stepUnit,
                                            templateTimes = templateSlots
                                        )
                                        if (generated.isNotEmpty()) {
                                            stages = generated
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("一键生成阶段列表")
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "阶段明细 (${stages.size} 个阶段)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            val lastSlots = stages.lastOrNull()?.slots?.takeIf { it.isNotEmpty() } ?: templateSlots
                            stages = stages + PlanStage(
                                name = "阶段 ${stages.size + 1}",
                                durationValue = 7,
                                durationUnit = DurationUnit.DAY,
                                slots = lastSlots.map { it.copy(id = UUID.randomUUID().toString().take(8)) }
                            )
                        }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("添加阶段")
                        }
                        TextButton(onClick = {
                            stages = stages + PlanStage(
                                name = "休息阶段",
                                durationValue = 1,
                                durationUnit = DurationUnit.DAY,
                                slots = emptyList()
                            )
                        }) {
                            Text("+ 休息(0)")
                        }
                    }
                }

                itemsIndexed(stages, key = { idx, s -> "${s.id}_$idx" }) { idx, stage ->
                    StageEditorCard(
                        index = idx,
                        stage = stage,
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
        }
    }
}

@Composable
private fun StageEditorCard(
    index: Int,
    stage: PlanStage,
    unit: String,
    canDelete: Boolean,
    onUpdate: (PlanStage) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var durationText by remember(stage.id, stage.durationValue) {
        mutableStateOf(stage.durationValue.toString())
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "阶段 ${index + 1}" + if (stage.isRestStage) " · 休息/停用 (0)" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除阶段",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        durationText = digits
                        digits.toIntOrNull()?.coerceAtLeast(1)?.let { v ->
                            onUpdate(stage.copy(durationValue = v))
                        }
                    },
                    label = { Text("持续") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(84.dp)
                )
                DurationUnit.entries.forEach { du ->
                    FilterChip(
                        selected = stage.durationUnit == du,
                        onClick = { onUpdate(stage.copy(durationUnit = du)) },
                        label = { Text(du.label) }
                    )
                }
            }

            if (stage.slots.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "该阶段为休息日（不触发提醒）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        onUpdate(stage.copy(slots = defaultThreeSlots()))
                    }) {
                        Text("恢复提醒时段")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stage.slots.forEachIndexed { slotIdx, slot ->
                        SlotRowEditor(
                            slot = slot,
                            unit = unit,
                            onPickTime = {
                                pickTime(context, slot.parsedTime) { newTime ->
                                    val next = stage.slots.mapIndexed { i, s ->
                                        if (i == slotIdx) s.copy(time = newTime) else s
                                    }
                                    onUpdate(stage.copy(slots = next))
                                }
                            },
                            onChangeAmount = { newAmount ->
                                val next = stage.slots.mapIndexed { i, s ->
                                    if (i == slotIdx) s.copy(amount = newAmount) else s
                                }
                                onUpdate(stage.copy(slots = next))
                            },
                            onDelete = {
                                val next = stage.slots.filterIndexed { i, _ -> i != slotIdx }
                                onUpdate(stage.copy(slots = next))
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        val nextHour = (8 + stage.slots.size * 4).coerceAtMost(23)
                        val added = SlotDose(
                            time = String.format("%02d:00", nextHour),
                            label = "时段${stage.slots.size + 1}",
                            amount = stage.slots.lastOrNull()?.amount ?: 1.0
                        )
                        onUpdate(stage.copy(slots = stage.slots + added))
                    }) {
                        Text("+ 添加时段")
                    }
                    TextButton(onClick = { onUpdate(stage.copy(slots = emptyList())) }) {
                        Text("设为休息(0)")
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotRowEditor(
    slot: SlotDose,
    unit: String,
    onPickTime: () -> Unit,
    onChangeAmount: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var amountText by remember(slot.id, slot.amount) {
        mutableStateOf(ScheduleEngine.formatAmount(slot.amount))
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
            Text("${slot.time} ${slot.label}".trim())
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() || it == '.' }
                amountText = filtered
                filtered.toDoubleOrNull()?.takeIf { it >= 0.0 }?.let(onChangeAmount)
            },
            label = { Text("数量 ($unit)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "移除时段", modifier = Modifier.size(16.dp))
        }
    }
}

private fun defaultThreeSlots(): List<SlotDose> = listOf(
    SlotDose(id = UUID.randomUUID().toString().take(8), time = "08:00", label = "早", amount = 1.0),
    SlotDose(id = UUID.randomUUID().toString().take(8), time = "13:00", label = "中", amount = 1.0),
    SlotDose(id = UUID.randomUUID().toString().take(8), time = "20:00", label = "晚", amount = 1.0)
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
