package com.flycode.dailyschedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.model.AppState
import com.flycode.dailyschedule.model.PlanItem
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.ui.theme.planAccentColor

@Composable
fun PlansScreen(
    state: AppState,
    onCreatePlan: () -> Unit,
    onEditPlan: (PlanItem) -> Unit,
    onTogglePlanActive: (String) -> Unit,
    onTogglePlanHidden: (String) -> Unit,
    onMovePlan: (planId: String, moveUp: Boolean) -> Unit,
    onDeletePlan: (String) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<PlanItem?>(null) }
    var menuTarget by remember { mutableStateOf<Pair<PlanItem, Int>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.plans.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "暂无计划",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onCreatePlan) {
                            Text("新建计划")
                        }
                    }
                }
            }
        } else {
            itemsIndexed(state.plans, key = { _, p -> p.id }) { idx, plan ->
                PlanSummaryCard(
                    plan = plan,
                    onClick = { onEditPlan(plan) },
                    onLongClick = { menuTarget = plan to idx },
                    onSwipeRight = { onTogglePlanHidden(plan.id) },
                    onSwipeLeft = { pendingDelete = plan },
                    onToggleActive = { onTogglePlanActive(plan.id) }
                )
            }
        }
    }

    menuTarget?.let { (plan, idx) ->
        val actions = buildList {
            add(ActionMenuItem("编辑") { menuTarget = null; onEditPlan(plan) })
            if (idx > 0) {
                add(ActionMenuItem("上移") { menuTarget = null; onMovePlan(plan.id, true) })
            }
            if (idx < state.plans.lastIndex) {
                add(ActionMenuItem("下移") { menuTarget = null; onMovePlan(plan.id, false) })
            }
            add(ActionMenuItem(if (plan.hidden) "取消隐藏" else "设为隐藏") {
                menuTarget = null; onTogglePlanHidden(plan.id)
            })
            add(ActionMenuItem(if (plan.active) "暂停计划" else "恢复启用") {
                menuTarget = null; onTogglePlanActive(plan.id)
            })
            add(ActionMenuItem("删除计划", destructive = true) {
                menuTarget = null; pendingDelete = plan
            })
        }
        ActionMenuDialog(
            title = plan.title,
            actions = actions,
            onDismiss = { menuTarget = null }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除计划") },
            text = { Text("确定删除「${target.title}」？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlan(target.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlanSummaryCard(
    plan: PlanItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    onToggleActive: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val accent = planAccentColor(plan.colorIndex)
    val endDate = remember(plan) { ScheduleEngine.planEndDate(plan) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSwipeRight()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSwipeLeft()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)),
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Text(
                        text = if (plan.hidden) "取消隐藏" else "隐藏",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                ),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (plan.active) accent else MaterialTheme.colorScheme.outline)
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (plan.hidden) {
                            StagePill(text = "隐藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    val isForeverDaily = plan.stages.size == 1 && plan.repeatCycle && plan.stages[0].durationValue == 1
                    val periodText = when {
                        isForeverDaily -> "长期重复"
                        plan.repeatCycle -> "${plan.startDate} 起 · 循环"
                        else -> "${plan.startDate} ~ ${endDate ?: plan.startDate}"
                    }
                    val meta = if (plan.stages.size > 1) {
                        val isDaily = plan.stages.all { it.durationUnit.label == "天" && it.durationValue == 1 }
                        if (isDaily) "逐日 ${plan.stages.size} 天 · $periodText" else "${plan.stages.size} 阶段 · $periodText"
                    } else {
                        val slotsCount = plan.stages.firstOrNull()?.slots?.count { it.amount > 0 } ?: 0
                        "每日 $slotsCount 次 · $periodText"
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (plan.stages.size > 1) {
                        val preview = plan.stages.take(5).joinToString(" · ") { stage ->
                            val amt = stage.slots.maxOfOrNull { it.amount } ?: 0.0
                            val valStr = if (stage.isRestStage) "休" else ScheduleEngine.formatAmount(amt)
                            "${stage.durationValue}${stage.durationUnit.label}($valStr)"
                        } + if (plan.stages.size > 5) " …" else ""
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = plan.active,
                    onCheckedChange = { onToggleActive() }
                )
            }
        }
    }
}
