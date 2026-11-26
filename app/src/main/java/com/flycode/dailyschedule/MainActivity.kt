package com.flycode.dailyschedule

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flycode.dailyschedule.alarm.ReminderManager
import com.flycode.dailyschedule.data.AppStore
import com.flycode.dailyschedule.model.AppSettings
import com.flycode.dailyschedule.model.PlanItem
import com.flycode.dailyschedule.model.ResolvedDayPlan
import com.flycode.dailyschedule.ui.AppLockScreen
import com.flycode.dailyschedule.ui.CalendarScreen
import com.flycode.dailyschedule.ui.DayOverrideDialog
import com.flycode.dailyschedule.ui.PinVerifyDialog
import com.flycode.dailyschedule.ui.PlanEditorScreen
import com.flycode.dailyschedule.ui.PlansScreen
import com.flycode.dailyschedule.ui.SettingsScreen
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

private enum class ScreenRoute {
    HOME,
    EDITOR,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val openDateState = mutableStateOf<LocalDate?>(null)
    private val openUpdateState = mutableStateOf(false)
    private val showHiddenState = mutableStateOf(false)
    private val appLockedState = mutableStateOf(false)
    private val backgroundObscuredState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }
        val initialState = runCatching { AppStore.init(this) }.getOrNull()
        if (initialState != null) {
            applyWindowPrivacy(initialState.settings)
            if (initialState.settings.appLockEnabled && initialState.settings.hasPrivacyPin) {
                appLockedState.value = true
            }
        }
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
                    onConsumedOpenUpdate = { openUpdateState.value = false },
                    showHiddenPlans = showHiddenState.value,
                    onChangeShowHidden = { showHiddenState.value = it },
                    isAppLocked = appLockedState.value,
                    onUnlockApp = { appLockedState.value = false },
                    isBackgroundObscured = backgroundObscuredState.value,
                    onPrivacySettingsChanged = { applyWindowPrivacy(it) }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val settings = AppStore.state.value.settings
        if (settings.autoRelockOnBackground) {
            backgroundObscuredState.value = true
            showHiddenState.value = false
        }
    }

    override fun onResume() {
        super.onResume()
        backgroundObscuredState.value = false
    }

    override fun onStop() {
        super.onStop()
        val settings = AppStore.state.value.settings
        if (settings.autoRelockOnBackground) {
            showHiddenState.value = false
            if (settings.appLockEnabled && settings.hasPrivacyPin) {
                appLockedState.value = true
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    private fun applyWindowPrivacy(settings: AppSettings) {
        runCatching {
            if (settings.blockScreenshots) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setRecentsScreenshotEnabled(!settings.autoRelockOnBackground && !settings.blockScreenshots)
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DailyScheduleApp(
    pendingOpenDate: LocalDate?,
    onConsumedOpenDate: () -> Unit,
    pendingOpenUpdate: Boolean,
    onConsumedOpenUpdate: () -> Unit,
    showHiddenPlans: Boolean,
    onChangeShowHidden: (Boolean) -> Unit,
    isAppLocked: Boolean,
    onUnlockApp: () -> Unit,
    isBackgroundObscured: Boolean,
    onPrivacySettingsChanged: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state by AppStore.state.collectAsState()

    LaunchedEffect(state.settings.blockScreenshots, state.settings.autoRelockOnBackground) {
        onPrivacySettingsChanged(state.settings)
    }

    val blurModifier = if (isBackgroundObscured && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(24.dp)
    } else {
        Modifier
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().then(blurModifier)) {
            if (isAppLocked && state.settings.appLockEnabled && state.settings.hasPrivacyPin) {
                AppLockScreen(
                    pinHash = state.settings.privacyPinHash,
                    onUnlocked = onUnlockApp
                )
            } else {
                DailyScheduleMainContent(
                    state = state,
                    pendingOpenDate = pendingOpenDate,
                    onConsumedOpenDate = onConsumedOpenDate,
                    pendingOpenUpdate = pendingOpenUpdate,
                    onConsumedOpenUpdate = onConsumedOpenUpdate,
                    showHiddenPlans = showHiddenPlans,
                    onChangeShowHidden = onChangeShowHidden,
                    context = context,
                    haptic = haptic,
                    scope = scope
                )
            }
        }

        if (isBackgroundObscured) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.86f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "日常计划",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "隐私保护中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DailyScheduleMainContent(
    state: com.flycode.dailyschedule.model.AppState,
    pendingOpenDate: LocalDate?,
    onConsumedOpenDate: () -> Unit,
    pendingOpenUpdate: Boolean,
    onConsumedOpenUpdate: () -> Unit,
    showHiddenPlans: Boolean,
    onChangeShowHidden: (Boolean) -> Unit,
    context: android.content.Context,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var currentTab by remember { mutableStateOf(MainTab.TODAY) }
    var currentRoute by remember { mutableStateOf(ScreenRoute.HOME) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var editingPlan by remember { mutableStateOf<PlanItem?>(null) }
    var adjustingDay by remember { mutableStateOf<ResolvedDayPlan?>(null) }
    var showPinVerifyForHidden by remember { mutableStateOf(false) }

    var latestRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var pendingReleases by remember { mutableStateOf<List<ReleaseInfo>>(emptyList()) }
    var recentReleases by remember { mutableStateOf<List<ReleaseInfo>>(emptyList()) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showChangelogOnly by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }

    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermission(context))
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(ReminderManager.canScheduleExact(context))
    }

    val visibleState = remember(state, showHiddenPlans) {
        if (showHiddenPlans) state
        else state.copy(plans = state.plans.filterNot { it.hidden })
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

    fun requestToggleHidden(enable: Boolean) {
        if (!enable) {
            onChangeShowHidden(false)
            return
        }
        if (state.settings.hasPrivacyPin) {
            showPinVerifyForHidden = true
        } else {
            onChangeShowHidden(true)
        }
    }

    fun toggleSinglePlanHidden(planId: String) {
        val nowHidden = AppStore.togglePlanHidden(context, planId)
        ReminderManager.rescheduleAll(context)
        if (nowHidden && !showHiddenPlans) {
            Toast.makeText(context, "已隐藏，长按首页标题可查看", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerUpdateCheck(manual: Boolean) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        if (manual) updateStatusText = "正在检查更新…"
        scope.launch {
            when (val result = GitHubUpdater.checkForUpdate(context, notifyIfNew = !manual)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    latestRelease = result.release
                    pendingReleases = result.pendingReleases
                    recentReleases = result.pendingReleases
                    updateStatusText = "发现新版本 ${result.release.tagName}"
                    if (manual || pendingOpenUpdate) {
                        showChangelogOnly = false
                        showUpdateDialog = true
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    latestRelease = null
                    pendingReleases = emptyList()
                    recentReleases = result.recentReleases
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
            currentRoute = ScreenRoute.HOME
            onConsumedOpenDate()
        }
    }

    LaunchedEffect(pendingOpenUpdate) {
        if (pendingOpenUpdate) {
            onConsumedOpenUpdate()
            if (latestRelease != null) {
                showChangelogOnly = false
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

    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            fadeIn(animationSpec = tween(160)) togetherWith fadeOut(animationSpec = tween(130))
        },
        label = "screenRoute"
    ) { route ->
        when (route) {
            ScreenRoute.EDITOR -> PlanEditorScreen(
                initialPlan = editingPlan,
                onDismiss = { currentRoute = ScreenRoute.HOME },
                onSave = { plan ->
                    AppStore.upsertPlan(context, plan)
                    ReminderManager.rescheduleAll(context)
                    if (plan.hidden && !showHiddenPlans) {
                        Toast.makeText(context, "已隐藏，长按首页标题可查看", Toast.LENGTH_SHORT).show()
                    }
                    currentRoute = ScreenRoute.HOME
                },
                onDelete = { planId ->
                    AppStore.deletePlan(context, planId)
                    ReminderManager.rescheduleAll(context)
                    currentRoute = ScreenRoute.HOME
                }
            )

            ScreenRoute.SETTINGS -> SettingsScreen(
                state = state,
                showHiddenPlans = showHiddenPlans,
                latestRelease = latestRelease,
                recentReleases = recentReleases,
                isCheckingUpdate = isCheckingUpdate,
                updateStatusText = updateStatusText,
                hasNotificationPermission = hasNotificationPermission,
                hasExactAlarmPermission = hasExactAlarmPermission,
                onDismiss = {
                    refreshPermissions()
                    currentRoute = ScreenRoute.HOME
                },
                onToggleShowHidden = { enable -> requestToggleHidden(enable) },
                onCheckUpdate = { triggerUpdateCheck(manual = true) },
                onOpenUpdateDialog = {
                    showChangelogOnly = false
                    showUpdateDialog = true
                },
                onOpenChangelog = {
                    showChangelogOnly = true
                    showUpdateDialog = true
                },
                onUpdateSettings = { transform ->
                    AppStore.updateSettings(context, transform)
                    ReminderManager.rescheduleAll(context)
                    GitHubUpdater.schedulePeriodicCheck(context)
                },
                onRequestNotificationPermission = requestNotificationPermission,
                onRequestExactAlarmPermission = requestExactAlarmPermission,
                onImportedData = {
                    ReminderManager.rescheduleAll(context)
                }
            )

            ScreenRoute.HOME -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (currentTab) {
                                        MainTab.TODAY -> "日常计划"
                                        MainTab.CALENDAR -> "日历"
                                        MainTab.PLANS -> "全部计划"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (showHiddenPlans) {
                                                onChangeShowHidden(false)
                                                Toast.makeText(context, "已隐藏隐私计划", Toast.LENGTH_SHORT).show()
                                            } else {
                                                requestToggleHidden(true)
                                                if (!state.settings.hasPrivacyPin) {
                                                    Toast.makeText(context, "已显示隐藏计划", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                )
                                if (showHiddenPlans) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.clickable {
                                            onChangeShowHidden(false)
                                        }
                                    ) {
                                        Text(
                                            text = "隐私可见 · 点击收起",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                } else if (latestRelease != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.clickable {
                                            showChangelogOnly = false
                                            showUpdateDialog = true
                                        }
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
                        },
                        actions = {
                            IconButton(onClick = {
                                refreshPermissions()
                                currentRoute = ScreenRoute.SETTINGS
                            }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "设置")
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            editingPlan = null
                            currentRoute = ScreenRoute.EDITOR
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建计划")
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
                    Crossfade(
                        targetState = currentTab,
                        animationSpec = tween(150),
                        label = "mainTab"
                    ) { tab ->
                        when (tab) {
                            MainTab.TODAY -> TodayScreen(
                                state = visibleState,
                                selectedDate = selectedDate,
                                hasNotificationPermission = hasNotificationPermission,
                                hasExactAlarmPermission = hasExactAlarmPermission,
                                onSelectDate = { selectedDate = it },
                                onRequestNotificationPermission = requestNotificationPermission,
                                onRequestExactAlarmPermission = requestExactAlarmPermission,
                                onDismissPermissionBanner = {
                                    AppStore.updateSettings(context) { it.copy(hidePermissionPrompts = true) }
                                },
                                onToggleSlot = { planId, date, slotId ->
                                    AppStore.toggleCheckIn(context, planId, date, slotId)
                                    ReminderManager.rescheduleAll(context)
                                },
                                onAdjustDay = { adjustingDay = it },
                                onEditPlan = { planId ->
                                    editingPlan = state.plans.firstOrNull { it.id == planId }
                                    if (editingPlan != null) currentRoute = ScreenRoute.EDITOR
                                },
                                onToggleHiddenPlan = { toggleSinglePlanHidden(it) },
                                onDeletePlan = { planId ->
                                    AppStore.deletePlan(context, planId)
                                    ReminderManager.rescheduleAll(context)
                                },
                                onCreatePlan = {
                                    editingPlan = null
                                    currentRoute = ScreenRoute.EDITOR
                                }
                            )

                            MainTab.CALENDAR -> CalendarScreen(
                                state = visibleState,
                                selectedDate = selectedDate,
                                onSelectDate = { selectedDate = it },
                                onToggleSlot = { planId, date, slotId ->
                                    AppStore.toggleCheckIn(context, planId, date, slotId)
                                    ReminderManager.rescheduleAll(context)
                                },
                                onAdjustDay = { adjustingDay = it },
                                onEditPlan = { planId ->
                                    editingPlan = state.plans.firstOrNull { it.id == planId }
                                    if (editingPlan != null) currentRoute = ScreenRoute.EDITOR
                                },
                                onToggleHiddenPlan = { toggleSinglePlanHidden(it) },
                                onDeletePlan = { planId ->
                                    AppStore.deletePlan(context, planId)
                                    ReminderManager.rescheduleAll(context)
                                },
                                onCreatePlan = {
                                    editingPlan = null
                                    currentRoute = ScreenRoute.EDITOR
                                }
                            )

                            MainTab.PLANS -> PlansScreen(
                                state = visibleState,
                                onCreatePlan = {
                                    editingPlan = null
                                    currentRoute = ScreenRoute.EDITOR
                                },
                                onEditPlan = { plan ->
                                    editingPlan = plan
                                    currentRoute = ScreenRoute.EDITOR
                                },
                                onTogglePlanActive = { planId ->
                                    AppStore.togglePlanActive(context, planId)
                                    ReminderManager.rescheduleAll(context)
                                },
                                onTogglePlanHidden = { toggleSinglePlanHidden(it) },
                                onMovePlan = { planId, moveUp ->
                                    AppStore.movePlan(context, planId, moveUp)
                                },
                                onDeletePlan = { planId ->
                                    AppStore.deletePlan(context, planId)
                                    ReminderManager.rescheduleAll(context)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPinVerifyForHidden) {
        PinVerifyDialog(
            title = "验证隐私密码",
            pinHash = state.settings.privacyPinHash,
            onDismiss = { showPinVerifyForHidden = false },
            onVerified = {
                showPinVerifyForHidden = false
                onChangeShowHidden(true)
            }
        )
    }

    adjustingDay?.let { target ->
        DayOverrideDialog(
            resolved = target,
            onDismiss = { adjustingDay = null },
            onSaveOverride = { slots ->
                AppStore.setDayOverride(context, target.plan.id, target.date, slots)
                ReminderManager.rescheduleAll(context)
                adjustingDay = null
            },
            onEditFullPlan = {
                editingPlan = state.plans.firstOrNull { it.id == target.plan.id }
                adjustingDay = null
                if (editingPlan != null) currentRoute = ScreenRoute.EDITOR
            }
        )
    }

    val dialogRelease = latestRelease ?: recentReleases.firstOrNull()
    if (showUpdateDialog && dialogRelease != null) {
        UpdateDialog(
            release = dialogRelease,
            releasesToShow = if (showChangelogOnly) recentReleases else pendingReleases,
            mirrorPrefix = state.settings.githubMirrorPrefix,
            isAlreadyLatest = showChangelogOnly || latestRelease == null,
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
