package com.flycode.dailyschedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flycode.dailyschedule.update.GitHubUpdater
import com.flycode.dailyschedule.update.ReleaseInfo
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    releasesToShow: List<ReleaseInfo>,
    mirrorPrefix: String,
    isAlreadyLatest: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(release.apkSizeBytes) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val progress = if (totalBytes > 0L) {
        (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val versionList = releasesToShow.ifEmpty { listOf(release) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isAlreadyLatest) "版本更新记录" else "发现新版本",
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isAlreadyLatest) {
                                "当前 v${GitHubUpdater.currentVersionName()}"
                            } else {
                                "v${GitHubUpdater.currentVersionName()} → ${release.tagName}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (!isAlreadyLatest && release.apkSizeBytes > 0L) {
                        Text(
                            text = formatByteSize(release.apkSizeBytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                versionList.forEach { item ->
                    ReleaseVersionBlock(item)
                }

                if (downloading) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                strokeCap = StrokeCap.Round
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "正在下载安装包 ${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (totalBytes > 0L) {
                                    Text(
                                        text = "${formatByteSize(downloadedBytes)} / ${formatByteSize(totalBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
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
            if (isAlreadyLatest) {
                Button(onClick = onDismiss) {
                    Text("知道了")
                }
            } else {
                Button(
                    enabled = !downloading && release.apkUrl != null,
                    onClick = {
                        if (!GitHubUpdater.canInstallPackages(context)) {
                            GitHubUpdater.openInstallPermissionSettings(context)
                            errorMessage = "请先允许安装未知来源应用，授权后再点击立即更新"
                            return@Button
                        }
                        errorMessage = null
                        downloading = true
                        downloadedBytes = 0L
                        totalBytes = release.apkSizeBytes
                        scope.launch {
                            GitHubUpdater.downloadApk(
                                context = context,
                                release = release,
                                mirrorPrefix = mirrorPrefix,
                                onProgress = { done, total ->
                                    downloadedBytes = done
                                    if (total > 0L) totalBytes = total
                                }
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
                    Text(
                        when {
                            downloading -> "下载中…"
                            release.apkUrl == null -> "暂无安装包"
                            else -> "立即更新"
                        }
                    )
                }
            }
        },
        dismissButton = {
            if (!isAlreadyLatest) {
                TextButton(
                    enabled = !downloading,
                    onClick = onDismiss
                ) {
                    Text("稍后")
                }
            }
        }
    )
}

@Composable
private fun ReleaseVersionBlock(release: ReleaseInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (release.publishedDate.isNotBlank()) {
                    Text(
                        text = release.publishedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            release.changes.forEach { change ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = change,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
