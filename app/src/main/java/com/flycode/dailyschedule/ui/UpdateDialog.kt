package com.flycode.dailyschedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.update.GitHubUpdater
import com.flycode.dailyschedule.update.ReleaseInfo
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    mirrorPrefix: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Column {
                Text("发现新版本 ${release.tagName}", fontWeight = FontWeight.Bold)
                Text(
                    text = "当前 v${GitHubUpdater.currentVersionName()} · 包大小 ${formatByteSize(release.apkSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = release.body,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (downloading) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "正在下载安装包 ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !downloading && release.apkUrl != null,
                onClick = {
                    if (!GitHubUpdater.canInstallPackages(context)) {
                        GitHubUpdater.openInstallPermissionSettings(context)
                        errorMessage = "请在系统设置中允许本应用安装未知来源应用，完成后再次点击下载安装"
                        return@Button
                    }
                    errorMessage = null
                    downloading = true
                    progress = 0f
                    scope.launch {
                        GitHubUpdater.downloadApk(
                            context = context,
                            release = release,
                            mirrorPrefix = mirrorPrefix,
                            onProgress = { p -> progress = p }
                        ).onSuccess { uri ->
                            downloading = false
                            GitHubUpdater.launchInstaller(context, uri)
                            onDismiss()
                        }.onFailure { err ->
                            downloading = false
                            errorMessage = err.message ?: "下载失败"
                        }
                    }
                }
            ) {
                Text(if (release.apkUrl == null) "无 APK 资源" else "下载并安装")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !downloading,
                onClick = onDismiss
            ) {
                Text("稍后")
            }
        }
    )
}
