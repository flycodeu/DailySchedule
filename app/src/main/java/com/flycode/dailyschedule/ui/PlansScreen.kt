package com.flycode.dailyschedule.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onTogglePlanActive: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
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
            items(state.plans, key = { it.id }) { plan ->
                PlanSummaryCard(
                    plan = plan,
                    onClick = { onEditPlan(plan) },
                    onToggleActive = { onTogglePlanActive(plan.id) }
                )
            }
        }
    }
}

@Composable
private fun PlanSummaryCard(
    plan: PlanItem,
    onClick: () -> Unit,
    onToggleActive: () -> Unit
) {
    val accent = planAccentColor(plan.colorIndex)
    val endDate = remember(plan) { ScheduleEngine.planEndDate(plan) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
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
