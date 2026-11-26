package com.flycode.dailyschedule.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.flycode.dailyschedule.BuildConfig
import com.flycode.dailyschedule.MainActivity
import com.flycode.dailyschedule.R
import com.flycode.dailyschedule.alarm.ReminderManager
import com.flycode.dailyschedule.data.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val body: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object GitHubUpdater {

    const val EXTRA_OPEN_UPDATE = "extra_open_update"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_NOTIFIED_TAG = "last_notified_tag"
    private const val JOB_ID = 4012
    private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
    private const val NOTIFICATION_ID_UPDATE = 9002

    fun currentVersionName(): String = BuildConfig.VERSION_NAME.substringBefore("-")

    fun schedulePeriodicCheck(context: Context) {
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(JobScheduler::class.java) ?: return
        val settings = AppStore.current(appContext).settings
        if (!settings.autoCheckUpdates) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return

        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(appContext, UpdateCheckJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(CHECK_INTERVAL_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    suspend fun checkForUpdate(
        context: Context,
        notifyIfNew: Boolean,
        respectNotifiedCache: Boolean = false
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val settings = AppStore.current(appContext).settings
        val repo = settings.githubRepo.trim().ifBlank { BuildConfig.GITHUB_REPO }
        if (!repo.contains("/")) {
            return@withContext UpdateCheckResult.Error("GitHub 仓库格式应为 owner/repo")
        }

        runCatching {
            val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "DailySchedule-Android/${currentVersionName()}")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            if (code == 404) {
                return@withContext UpdateCheckResult.Error("仓库尚未发布 Release")
            }
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("检查更新失败 (HTTP $code)")
            }

            val json = JSONObject(text)
            val tagName = json.optString("tag_name").trim()
            val remoteVersion = tagName.removePrefix("v").removePrefix("V")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val release = ReleaseInfo(
                tagName = tagName,
                versionName = remoteVersion,
                title = json.optString("name").ifBlank { tagName },
                body = json.optString("body").ifBlank { "常规更新与体验优化" },
                apkUrl = apkUrl,
                apkName = apkName,
                apkSizeBytes = apkSize,
                htmlUrl = json.optString("html_url")
            )

            val localVersion = currentVersionName()
            if (compareSemver(remoteVersion, localVersion) > 0) {
                if (notifyIfNew) {
                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val alreadyNotified = prefs.getString(KEY_NOTIFIED_TAG, null) == tagName
                    if (!respectNotifiedCache || !alreadyNotified) {
                        postUpdateNotification(appContext, release)
                        prefs.edit().putString(KEY_NOTIFIED_TAG, tagName).apply()
                    }
                }
                UpdateCheckResult.UpdateAvailable(release)
            } else {
                UpdateCheckResult.UpToDate(localVersion)
            }
        }.getOrElse { err ->
            UpdateCheckResult.Error(err.message ?: "网络请求失败")
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: ReleaseInfo,
        mirrorPrefix: String,
        onProgress: (Float) -> Unit
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val rawUrl = release.apkUrl ?: return@withContext Result.failure(
            IllegalStateException("该 Release 未附带 APK 安装包")
        )
        val cleanMirror = mirrorPrefix.trim()
        val finalUrl = if (cleanMirror.isNotBlank()) {
            val normalized = if (cleanMirror.endsWith("/")) cleanMirror else "$cleanMirror/"
            "$normalized$rawUrl"
        } else {
            rawUrl
        }

        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val safeName = (release.apkName ?: "DailySchedule-${release.tagName}.apk")
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val target = File(dir, safeName)

            val conn = openFollowingRedirects(finalUrl)
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSizeBytes
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            conn.disconnect()
            onProgress(1f)
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                target
            )
        }
    }

    private fun openFollowingRedirects(startUrl: String): HttpURLConnection {
        var currentUrl = startUrl
        repeat(6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "DailySchedule-Android/${currentVersionName()}")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) return conn
                currentUrl = location
            } else {
                if (code !in 200..299) {
                    conn.disconnect()
                    throw IllegalStateException("下载失败 (HTTP $code)")
                }
                return conn
            }
        }
        throw IllegalStateException("重定向次数过多")
    }

    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun launchInstaller(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun compareSemver(remote: String, local: String): Int {
        val rParts = remote.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val lParts = local.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    private fun postUpdateNotification(context: Context, release: ReleaseInfo) {
        ReminderManager.ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val openIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPDATE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_UPDATE, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("发现新版本 ${release.tagName}")
            .setContentText("点击查看更新说明并直接下载安装")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID_UPDATE, notification)
    }
}

class UpdateCheckJobService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = CoroutineScope(Dispatchers.IO).launch {
            AppStore.init(applicationContext)
            GitHubUpdater.checkForUpdate(
                context = applicationContext,
                notifyIfNew = true,
                respectNotifiedCache = true
            )
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }
}
