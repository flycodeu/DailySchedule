package com.flycode.dailyschedule

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flycode.dailyschedule.alarm.ReminderManager
import com.flycode.dailyschedule.data.AppStore
import com.flycode.dailyschedule.model.PlanItem
import com.flycode.dailyschedule.ui.CalendarScreen
import com.flycode.dailyschedule.ui.PlanEditorDialog
import com.flycode.dailyschedule.ui.PlansScreen
import com.flycode.dailyschedule.ui.TodayScreen
import com.flycode.dailyschedule.ui.UpdateDialog
import com.flycode.dailyschedule.ui.theme.DailyScheduleTheme
import com.flycode.dailyschedule.update.GitHubUpdater
import com.flycode.dailyschedule.update.ReleaseInfo
import com.flycode.dailyschedule.update.UpdateCheckResult
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class MainTab(val label: String) {
    TODAY("今日"),
    CALENDAR("日历"),
    PLANS("计划")
}

class MainActivity : ComponentActivity() {

    private val openDateState = mutableStateOf<LocalDate?>(null)
    private val openUpdateState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }
        runCatching { AppStore.init(this) }
        runCatching { ReminderManager.ensureChannels(this) }
        runCatching { ReminderManager.rescheduleAll(this) }
        runCatching { GitHubUpdater.schedulePeriodicCheck(this) }
        runCatching { handleIntentExtras(intent) }

        setContent {
            DailyScheduleTheme {
                DailyScheduleApp(
                    pendingOpenDate = openDateState.value,
                    onConsumedOpenDate = { openDateState.value = null },
                    pendingOpenUpdate = openUpdateState.value,
                    onConsumedOpenUpdate = { openUpdateState.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        val dateIso = intent?.getStringExtra(ReminderManager.EXTRA_DATE)
        if (!dateIso.isNullOrBlank()) {
            runCatching { LocalDate.parse(dateIso) }.getOrNull()?.let {
                openDateState.value = it
            }
        }
        if (intent?.getBooleanExtra(GitHubUpdater.EXTRA_OPEN_UPDATE, false) == true) {
            openUpdateState.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyScheduleApp(
    pendingOpenDate: LocalDate?,
    onConsumedOpenDate: () -> Unit,
    pendingOpenUpdate: Boolean,
    onConsumedOpenUpdate: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppStore.state.collectAsState()

    var currentTab by remember { mutableStateOf(MainTab.TODAY) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var editingPlan by remember { mutableStateOf<PlanItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    var latestRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }

    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermission(context))
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(ReminderManager.canScheduleExact(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    fun refreshPermissions() {
        hasNotificationPermission = checkNotificationPermission(context)
        hasExactAlarmPermission = ReminderManager.canScheduleExact(context)
    }

    fun triggerUpdateCheck(manual: Boolean) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        if (manual) updateStatusText = "正在检查 GitHub Release…"
        scope.launch {
            when (val result = GitHubUpdater.checkForUpdate(context, notifyIfNew = !manual)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    latestRelease = result.release
                    updateStatusText = "发现新版本 ${result.release.tagName}"
                    if (manual || pendingOpenUpdate) {
                        showUpdateDialog = true
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    latestRelease = null
                    updateStatusText = "已是最新版本 v${result.currentVersion}"
                }
                is UpdateCheckResult.Error -> {
                    if (manual) updateStatusText = result.message
                }
            }
            isCheckingUpdate = false
        }
    }

    LaunchedEffect(pendingOpenDate) {
        if (pendingOpenDate != null) {
            selectedDate = pendingOpenDate
            currentTab = MainTab.TODAY
            onConsumedOpenDate()
        }
    }

    LaunchedEffect(pendingOpenUpdate) {
        if (pendingOpenUpdate) {
            onConsumedOpenUpdate()
            if (latestRelease != null) {
                showUpdateDialog = true
            } else {
                triggerUpdateCheck(manual = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
        if (state.settings.autoCheckUpdates) {
            triggerUpdateCheck(manual = false)
        }
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val requestExactAlarmPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("日常计划", fontWeight = FontWeight.Bold)
                        if (latestRelease != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable { showUpdateDialog = true }
                            ) {
                                Text(
                                    text = "新版本 ${latestRelease!!.tagName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentTab != MainTab.PLANS) {
                FloatingActionButton(
                    onClick = {
                        editingPlan = null
                        showEditor = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "新建计划")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        MainTab.TODAY -> Icons.Rounded.Home
                        MainTab.CALENDAR -> Icons.Rounded.DateRange
                        MainTab.PLANS -> Icons.AutoMirrored.Rounded.List
                    }
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = {
                            refreshPermissions()
                            currentTab = tab
                        },
                        icon = { Icon(icon, contentDescription = tab.label, modifier = Modifier.size(22.dp)) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentTab) {
                MainTab.TODAY -> TodayScreen(
                    state = state,
                    selectedDate = selectedDate,
                    hasNotificationPermission = hasNotificationPermission,
                    hasExactAlarmPermission = hasExactAlarmPermission,
                    onSelectDate = { selectedDate = it },
                    onRequestNotificationPermission = requestNotificationPermission,
                    onRequestExactAlarmPermission = requestExactAlarmPermission,
                    onToggleSlot = { planId, date, slotId ->
                        AppStore.toggleCheckIn(context, planId, date, slotId)
                        ReminderManager.rescheduleAll(context)
                    },
                    onEditPlan = { planId ->
                        editingPlan = state.plans.firstOrNull { it.id == planId }
                        if (editingPlan != null) showEditor = true
                    },
                    onCreatePlan = {
                        editingPlan = null
                        showEditor = true
                    }
                )

                MainTab.CALENDAR -> CalendarScreen(
                    state = state,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it },
                    onToggleSlot = { planId, date, slotId ->
                        AppStore.toggleCheckIn(context, planId, date, slotId)
                        ReminderManager.rescheduleAll(context)
                    },
                    onEditPlan = { planId ->
                        editingPlan = state.plans.firstOrNull { it.id == planId }
                        if (editingPlan != null) showEditor = true
                    },
                    onCreatePlan = {
                        editingPlan = null
                        showEditor = true
                    }
                )

                MainTab.PLANS -> PlansScreen(
                    state = state,
                    latestRelease = latestRelease,
                    isCheckingUpdate = isCheckingUpdate,
                    updateStatusText = updateStatusText,
                    hasNotificationPermission = hasNotificationPermission,
                    hasExactAlarmPermission = hasExactAlarmPermission,
                    onCreatePlan = {
                        editingPlan = null
                        showEditor = true
                    },
                    onEditPlan = { plan ->
                        editingPlan = plan
                        showEditor = true
                    },
                    onTogglePlanActive = { planId ->
                        AppStore.togglePlanActive(context, planId)
                        ReminderManager.rescheduleAll(context)
                    },
                    onDeletePlan = { planId ->
                        AppStore.deletePlan(context, planId)
                        ReminderManager.rescheduleAll(context)
                    },
                    onCheckUpdate = { triggerUpdateCheck(manual = true) },
                    onOpenUpdateDialog = { showUpdateDialog = true },
                    onUpdateSettings = { transform ->
                        AppStore.updateSettings(context, transform)
                        GitHubUpdater.schedulePeriodicCheck(context)
                    },
                    onRequestNotificationPermission = requestNotificationPermission,
                    onRequestExactAlarmPermission = requestExactAlarmPermission,
                    onImportedData = {
                        ReminderManager.rescheduleAll(context)
                    }
                )
            }
        }
    }

    if (showEditor) {
        PlanEditorDialog(
            initialPlan = editingPlan,
            onDismiss = { showEditor = false },
            onSave = { plan ->
                AppStore.upsertPlan(context, plan)
                ReminderManager.rescheduleAll(context)
                showEditor = false
            }
        )
    }

    if (showUpdateDialog && latestRelease != null) {
        UpdateDialog(
            release = latestRelease!!,
            mirrorPrefix = state.settings.githubMirrorPrefix,
            onDismiss = { showUpdateDialog = false }
        )
    }
}

private fun checkNotificationPermission(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
