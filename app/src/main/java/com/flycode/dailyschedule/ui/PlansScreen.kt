package com.flycode.dailyschedule.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.alarm.ReminderManager
import com.flycode.dailyschedule.data.AppStore
import com.flycode.dailyschedule.model.AppSettings
import com.flycode.dailyschedule.model.AppState
import com.flycode.dailyschedule.model.PlanItem
import com.flycode.dailyschedule.model.ScheduleEngine
import com.flycode.dailyschedule.ui.theme.planAccentColor
import com.flycode.dailyschedule.update.GitHubUpdater
import com.flycode.dailyschedule.update.ReleaseInfo

@Composable
fun PlansScreen(
    state: AppState,
    latestRelease: ReleaseInfo?,
    isCheckingUpdate: Boolean,
    updateStatusText: String?,
    hasNotificationPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    onCreatePlan: () -> Unit,
    onEditPlan: (PlanItem) -> Unit,
    onTogglePlanActive: (String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onImportedData: () -> Unit
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<PlanItem?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var repoDraft by remember(state.settings.githubRepo) { mutableStateOf(state.settings.githubRepo) }
    var mirrorDraft by remember(state.settings.githubMirrorPrefix) { mutableStateOf(state.settings.githubMirrorPrefix) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部计划 (${state.plans.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onCreatePlan) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建计划")
                }
            }
        }

        if (state.plans.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("暂无计划项", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "支持 2,2,2,2,0 / 2,1,1,1,0 / 2,0,2,0 等任意阶段与长周期安排",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.plans, key = { it.id }) { plan ->
                PlanSummaryCard(
                    plan = plan,
                    onToggleActive = { onTogglePlanActive(plan.id) },
                    onEdit = { onEditPlan(plan) },
                    onDelete = { pendingDelete = plan }
                )
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "GitHub Release 自动更新与系统设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "当前版本 v${GitHubUpdater.currentVersionName()}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    latestRelease != null -> "发现新版本 ${latestRelease.tagName}"
                                    !updateStatusText.isNullOrBlank() -> updateStatusText
                                    else -> "托管于 GitHub Release，新版本可通知并直装"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (latestRelease != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (latestRelease != null) {
                            Button(onClick = onOpenUpdateDialog) {
                                Text("去更新")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onCheckUpdate,
                                enabled = !isCheckingUpdate
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isCheckingUpdate) "检查中" else "检查更新")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动检查更新通知", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "后台定期检查 GitHub Release 并在通知栏提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.autoCheckUpdates,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(autoCheckUpdates = enabled) }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = repoDraft,
                        onValueChange = { repoDraft = it },
                        label = { Text("GitHub 仓库 (owner/repo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = mirrorDraft,
                        onValueChange = { mirrorDraft = it },
                        label = { Text("下载加速前缀 (可选，留空直连 GitHub)") },
                        placeholder = { Text("例如 https://ghfast.top/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (repoDraft.trim() != state.settings.githubRepo ||
                        mirrorDraft.trim() != state.settings.githubMirrorPrefix
                    ) {
                        Button(
                            onClick = {
                                onUpdateSettings {
                                    it.copy(
                                        githubRepo = repoDraft.trim(),
                                        githubMirrorPrefix = mirrorDraft.trim()
                                    )
                                }
                                Toast.makeText(context, "更新配置已保存", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("保存仓库配置")
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "提醒与数据备份",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!hasNotificationPermission) {
                                    onRequestNotificationPermission()
                                } else {
                                    ReminderManager.showTestNotification(context)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (hasNotificationPermission) "测试通知" else "开启通知")
                        }
                        if (!hasExactAlarmPermission) {
                            OutlinedButton(
                                onClick = onRequestExactAlarmPermission,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("授权精确闹钟")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val json = AppStore.exportJson(context)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("DailyScheduleBackup", json))
                                Toast.makeText(context, "已复制全部计划与打卡记录到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导出备份")
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入恢复")
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除计划") },
            text = { Text("确定要删除「${target.title}」及其历史确认记录吗？如果只想暂停，关闭启用开关即可。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlan(target.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showImportDialog) {
        var jsonInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入 JSON 备份") },
            text = {
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    label = { Text("粘贴之前导出的 JSON") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    AppStore.importJson(context, jsonInput)
                        .onSuccess { count ->
                            Toast.makeText(context, "已恢复 $count 个计划", Toast.LENGTH_SHORT).show()
                            onImportedData()
                            showImportDialog = false
                        }
                        .onFailure {
                            Toast.makeText(context, "JSON 格式无效", Toast.LENGTH_SHORT).show()
                        }
                }) {
                    Text("导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun PlanSummaryCard(
    plan: PlanItem,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = planAccentColor(plan.colorIndex)
    val endDate = remember(plan) { ScheduleEngine.planEndDate(plan) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (plan.active) accent else MaterialTheme.colorScheme.outline)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val spanText = if (plan.repeatCycle) {
                        "自 ${plan.startDate} 起 · 循环执行"
                    } else {
                        "${plan.startDate} ~ ${endDate ?: plan.startDate}"
                    }
                    Text(
                        text = "${plan.stages.size} 个阶段 · $spanText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = plan.active,
                    onCheckedChange = { onToggleActive() }
                )
            }

            if (plan.stages.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val preview = plan.stages.take(5).joinToString(" → ") { stage ->
                    val maxAmt = stage.slots.maxOfOrNull { it.amount } ?: 0.0
                    val amtText = if (stage.isRestStage) "0(休)" else "${ScheduleEngine.formatAmount(maxAmt)}×${stage.slots.count { it.amount > 0 }}"
                    "${stage.durationValue}${stage.durationUnit.label}:$amtText"
                } + if (plan.stages.size > 5) " …" else ""
                Text(
                    text = preview,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "编辑", modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
