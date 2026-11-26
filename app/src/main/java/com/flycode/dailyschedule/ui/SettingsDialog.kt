package com.flycode.dailyschedule.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.alarm.ReminderManager
import com.flycode.dailyschedule.data.AppStore
import com.flycode.dailyschedule.model.AppSettings
import com.flycode.dailyschedule.model.AppState
import com.flycode.dailyschedule.update.GitHubUpdater
import com.flycode.dailyschedule.update.ReleaseInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    latestRelease: ReleaseInfo?,
    recentReleases: List<ReleaseInfo>,
    isCheckingUpdate: Boolean,
    updateStatusText: String?,
    hasNotificationPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    onDismiss: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onOpenChangelog: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onImportedData: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    var showAdvancedRepo by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var repoDraft by remember(state.settings.githubRepo) { mutableStateOf(state.settings.githubRepo) }
    var mirrorDraft by remember(state.settings.githubMirrorPrefix) { mutableStateOf(state.settings.githubMirrorPrefix) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "提醒与闹钟",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("开启定时提醒与闹钟", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (state.settings.remindersEnabled) "已启用，按计划时段推送提醒" else "已关闭，仅作为打卡记录使用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.settings.remindersEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings {
                                        it.copy(
                                            remindersEnabled = enabled,
                                            hidePermissionPrompts = if (!enabled) true else it.hidePermissionPrompts
                                        )
                                    }
                                }
                            )
                        }

                        if (state.settings.remindersEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "隐藏首页权限提示条",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = state.settings.hidePermissionPrompts,
                                    onCheckedChange = { hidden ->
                                        onUpdateSettings { it.copy(hidePermissionPrompts = hidden) }
                                    }
                                )
                            }

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
                                        Text("授权闹钟")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "软件更新",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "当前版本 v${GitHubUpdater.currentVersionName()}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (latestRelease != null || !updateStatusText.isNullOrBlank()) {
                                    Text(
                                        text = latestRelease?.let { "发现新版本 ${it.tagName}" }
                                            ?: updateStatusText.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (latestRelease != null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                            if (latestRelease != null) {
                                Button(onClick = onOpenUpdateDialog) {
                                    Text("查看更新")
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

                        if (recentReleases.isNotEmpty() && latestRelease == null) {
                            TextButton(
                                onClick = onOpenChangelog,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("查看近期版本更新内容")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "自动检查更新通知",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = state.settings.autoCheckUpdates,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { it.copy(autoCheckUpdates = enabled) }
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedRepo = !showAdvancedRepo }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "更新源配置",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (showAdvancedRepo) "收起" else "展开",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (showAdvancedRepo) {
                            OutlinedTextField(
                                value = repoDraft,
                                onValueChange = { repoDraft = it },
                                label = { Text("GitHub 仓库") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mirrorDraft,
                                onValueChange = { mirrorDraft = it },
                                label = { Text("加速前缀（选填）") },
                                placeholder = { Text("如 https://ghfast.top/") },
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
                                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("保存")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "数据备份",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val json = AppStore.exportJson(context)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("DailyScheduleBackup", json))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
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

    if (showImportDialog) {
        var jsonInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入备份") },
            text = {
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    placeholder = { Text("粘贴 JSON 备份内容") },
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
                            Toast.makeText(context, "格式无效", Toast.LENGTH_SHORT).show()
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
