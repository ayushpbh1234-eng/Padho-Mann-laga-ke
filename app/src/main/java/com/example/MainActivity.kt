package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.configureAppCheck
import com.example.data.model.FocusConstants
import com.example.data.repository.FocusGardenRepository
import com.example.notifications.FocusNotificationHelper
import com.example.supervision.ChildSupervisionService
import com.example.supervision.DeviceUsageCollector
import com.example.ui.FocusGardenViewModel
import com.example.ui.GardenDataState
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.PhoneLockOverlay
import com.example.ui.components.SafeAlertDialog
import com.example.ui.components.SafeTextToolbar
import com.example.ui.components.clearActiveTextInput
import com.example.ui.screens.ChildSupervisionScreen
import com.example.ui.screens.DiaryScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MirrorScreen
import com.example.ui.screens.ParentDashboardScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.SoundScreen
import com.example.ui.screens.StudySetupScreen
import com.example.ui.theme.FocusGardenTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object NavRoutes {
    const val HOME = "home"
    const val FOCUS = "focus"
    const val SOUND = "sound"
    const val MIRROR = "mirror"
    const val DIARY = "diary"
    const val FAMILY = "family"
}

private data class GardenNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val GARDEN_DESTINATIONS = listOf(
    GardenNavDestination(NavRoutes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    GardenNavDestination(NavRoutes.FOCUS, R.string.nav_focus, Icons.Filled.Eco, Icons.Outlined.Eco, "nav_focus"),
    GardenNavDestination(NavRoutes.SOUND, R.string.nav_sound, Icons.Filled.Headphones, Icons.Outlined.Headphones, "nav_sound"),
    GardenNavDestination(NavRoutes.MIRROR, R.string.nav_mirror, Icons.Filled.Visibility, Icons.Outlined.Visibility, "nav_mirror"),
    GardenNavDestination(NavRoutes.DIARY, R.string.nav_diary, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook, "nav_diary"),
    GardenNavDestination(NavRoutes.FAMILY, R.string.nav_family, Icons.Filled.Shield, Icons.Outlined.Shield, "nav_family")
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth -> trySend(firebaseAuth.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            FocusGardenTheme {
                val baseToolbar = LocalTextToolbar.current
                val safeToolbar = remember(baseToolbar) { SafeTextToolbar(baseToolbar) }
                CompositionLocalProvider(LocalTextToolbar provides safeToolbar) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            FocusGardenMainShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email ?: "Gardener"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusGardenMainShell(
    currentUserId: String,
    userDisplayName: String
) {
    val viewModel: FocusGardenViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                FocusGardenViewModel(FocusGardenRepository(db), currentUserId, notifier = FocusNotificationHelper(app))
            }
        }
    )

    val dataState by viewModel.dataState.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()
    val isEditingStudyPlan by viewModel.isEditingStudyPlan.collectAsStateWithLifecycle()
    val clockTick by viewModel.clockTick.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val childSupervision by viewModel.childSupervisionState.collectAsStateWithLifecycle()
    val parentDashboard by viewModel.parentDashboardState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, focusManager, textToolbar, keyboardController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    clearActiveTextInput(focusManager, textToolbar, keyboardController)
                }
                Lifecycle.Event.ON_STOP -> {
                    clearActiveTextInput(focusManager, textToolbar, keyboardController)
                    viewModel.onAppBackgrounded()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onAppForegrounded()
                    viewModel.refreshLocalDeviceTelemetry(context)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val readyState = dataState as? GardenDataState.Ready
    val activeProfile = readyState?.profile
    LaunchedEffect(activeProfile?.role, childSupervision.familyLinks.size) {
        if (activeProfile?.role == "child") {
            viewModel.refreshLocalDeviceTelemetry(context)
            if (childSupervision.familyLinks.isNotEmpty() && !childSupervision.serviceRunning) {
                runCatching { ChildSupervisionService.start(context) }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissStatusBanner()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val navigateTo: (String) -> Unit = { route ->
        clearActiveTextInput(focusManager, textToolbar, keyboardController)
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val showChildNav = activeProfile != null &&
        activeProfile.role == "child" &&
        activeProfile.onboardingCompleted &&
        !isEditingStudyPlan

    var showRoleSwitchPinDialog by remember { mutableStateOf(false) }
    var roleSwitchPinInput by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (activeProfile?.role) {
                                    "parent" -> "🛡️ Focus Garden • Parent"
                                    "child" -> "🌱 Focus Garden • Child"
                                    else -> "🌱 Focus Garden"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (activeProfile?.role != null) {
                            IconButton(
                                onClick = {
                                    clearActiveTextInput(focusManager, textToolbar, keyboardController)
                                    if (activeProfile.role == "parent") {
                                        viewModel.selectRole("child", activeProfile.displayName.ifBlank { userDisplayName })
                                    } else {
                                        val pin = childSupervision.parentControls?.parentPin.orEmpty()
                                        if (pin.isNotEmpty()) {
                                            roleSwitchPinInput = ""
                                            showRoleSwitchPinDialog = true
                                        } else {
                                            viewModel.selectRole("parent", activeProfile.displayName.ifBlank { userDisplayName })
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("switch_role_button")
                            ) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch Child or Parent mode")
                            }
                        }
                        IconButton(
                            onClick = {
                                clearActiveTextInput(focusManager, textToolbar, keyboardController)
                                signOut(credentialManager = credentialManager, scope = scope)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.sign_out))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                if (!isWideScreen && showChildNav) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        GARDEN_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Clip
                                    )
                                },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        )
                    )
                    .padding(innerPadding)
            ) {
                if (isWideScreen && showChildNav) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        GARDEN_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (val state = dataState) {
                        GardenDataState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                        is GardenDataState.Error -> Column(
                            modifier = Modifier.widthIn(max = 480.dp).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { navigateTo(NavRoutes.HOME) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Text("Retry")
                            }
                        }

                        is GardenDataState.Ready -> {
                            val profile = state.profile
                            val sessions = state.sessions
                            when {
                                profile.role == null -> RoleSelectionScreen(
                                    initialDisplayName = profile.displayName.ifBlank { userDisplayName },
                                    onSelectRole = { role, name -> viewModel.selectRole(role, name) },
                                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                                )

                                profile.role == "parent" -> ParentDashboardScreen(
                                    parentName = profile.displayName.ifBlank { userDisplayName },
                                    dashboardState = parentDashboard,
                                    onSelectChild = viewModel::selectManagedChild,
                                    onLinkChildByCode = viewModel::linkChildByCode,
                                    onUnlinkChild = viewModel::unlinkChild,
                                    onTogglePhoneLock = viewModel::toggleChildPhoneLock,
                                    onToggleStudyOnly = viewModel::toggleStudyOnlyMode,
                                    onSetDailyLimit = viewModel::setDailyScreenLimit,
                                    onGrantBonus = viewModel::grantBonusMinutes,
                                    onToggleBedtime = { enabled -> viewModel.toggleBedtimeLock(enabled) },
                                    onSetParentPin = viewModel::setParentPin,
                                    onToggleBlockedApp = viewModel::toggleBlockedPackage,
                                    onSendNudge = viewModel::sendParentNudge,
                                    onRequestScreenShare = viewModel::requestChildScreenShare,
                                    onAssignStudyTask = viewModel::assignStudyTask,
                                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                                )

                                !profile.onboardingCompleted || isEditingStudyPlan -> StudySetupScreen(
                                    userDisplayName = profile.displayName.ifBlank { userDisplayName },
                                    existingProfile = profile,
                                    isEditingExisting = profile.onboardingCompleted && isEditingStudyPlan,
                                    setupState = setupState,
                                    onCancelEdit = viewModel::closeStudySetupEditor,
                                    onSubmitWithAi = viewModel::analyzeAndSaveStudyPlan,
                                    onSubmitManual = viewModel::saveManualStudyPlanWithoutAi,
                                    onClearError = viewModel::clearSetupError,
                                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                                )

                                else -> {
                                    val controls = childSupervision.parentControls
                                    val today = FocusConstants.todayIsoDate()
                                    val bonusToday = if (controls?.bonusDay == today) controls.bonusMinutesToday else 0L
                                    val usedMins = childSupervision.localSnapshot?.totalPhoneScreenMinutesToday ?: 0L
                                    val limitReached = (controls?.dailyScreenLimitMinutes ?: 0L) > 0L &&
                                        usedMins >= ((controls?.dailyScreenLimitMinutes ?: 0L) + bonusToday)
                                    val bedtimeLocked = controls?.bedtimeEnabled == true &&
                                        DeviceUsageCollector.isWithinBedtime(controls.bedtimeStart, controls.bedtimeEnd)
                                    val pinUnlocked = clockTick < childSupervision.temporarilyUnlockedUntilMs
                                    val isLockedOut = !pinUnlocked && (controls?.isPhoneLocked == true || bedtimeLocked || limitReached)

                                    val alarmStatus = remember(profile.alarmTime, sessions, clockTick) {
                                        viewModel.computeReverseAlarm(profile.alarmTime, sessions)
                                    }
                                    val streakDays = remember(sessions, clockTick) { viewModel.computeStreakDays(sessions) }
                                    val todayMinutes = remember(sessions, clockTick) { viewModel.computeTodayFocusMinutes(sessions) }
                                    val subjectBreakdown = remember(sessions) { viewModel.computeSubjectBreakdown(sessions) }
                                    val soundStats = remember(sessions) { viewModel.computeSoundStats(sessions) }
                                    val bestSound = remember(soundStats) { viewModel.computeBestSound(soundStats) }
                                    val mirrorEntries = remember(sessions) { viewModel.computeMirrorStats(sessions) }
                                    val diaryStory = remember(sessions, timerState.diaryStoryNonce) {
                                        viewModel.computeTodayDiaryStory(sessions)
                                    }
                                    val userSubjects = remember(profile.subjectsText) {
                                        FocusConstants.parseSubjects(profile.subjectsText)
                                    }

                                    NavHost(
                                        navController = navController,
                                        startDestination = NavRoutes.HOME,
                                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                                    ) {
                                        composable(NavRoutes.HOME) {
                                            HomeScreen(
                                                profile = profile,
                                                sessions = sessions,
                                                alarmStatus = alarmStatus,
                                                streakDays = streakDays,
                                                todayMinutes = todayMinutes,
                                                onSelectMood = viewModel::setMood,
                                                onNextChallenge = viewModel::nextChallenge,
                                                onSaveAlarmTime = { viewModel.saveAlarmTime(it, profile.currentStudy) },
                                                onTestNotification = { viewModel.sendTestNotification(profile.currentStudy) },
                                                onEditStudyPlan = viewModel::openStudySetupEditor,
                                                onStartTopicFocus = { topic ->
                                                    viewModel.focusOnSyllabusTopic(topic, userSubjects)
                                                    navigateTo(NavRoutes.FOCUS)
                                                },
                                                onGoToFocus = { navigateTo(NavRoutes.FOCUS) }
                                            )
                                        }
                                        composable(NavRoutes.FOCUS) {
                                            FocusScreen(
                                                profile = profile,
                                                timerState = timerState,
                                                onSelectDuration = viewModel::selectDuration,
                                                onSelectSubject = viewModel::selectSubject,
                                                onSelectTopic = viewModel::selectTopic,
                                                onSessionGoalChanged = viewModel::setSessionGoal,
                                                onSelectSound = viewModel::selectSound,
                                                onToggleMute = viewModel::toggleAudioMute,
                                                onStartSession = viewModel::startSession,
                                                onPauseOrResume = viewModel::pauseOrResumeSession,
                                                onStartQuickBreak = viewModel::startQuickBreak,
                                                onGiveUp = viewModel::giveUp,
                                                onSpinRoulette = viewModel::spinRoulette,
                                                onSaveSubjectsText = viewModel::saveSubjectsText,
                                                onPickDistractionReason = viewModel::pickDistractionReason
                                            )
                                        }
                                        composable(NavRoutes.SOUND) {
                                            SoundScreen(
                                                soundStats = soundStats,
                                                bestSound = bestSound,
                                                activeSound = timerState.selectedSound,
                                                onSelectAndUseSound = { sound ->
                                                    viewModel.selectSound(sound)
                                                    navigateTo(NavRoutes.FOCUS)
                                                }
                                            )
                                        }
                                        composable(NavRoutes.MIRROR) {
                                            MirrorScreen(entries = mirrorEntries)
                                        }
                                        composable(NavRoutes.DIARY) {
                                            DiaryScreen(
                                                diaryStory = diaryStory,
                                                sessions = sessions,
                                                subjectBreakdown = subjectBreakdown,
                                                onRefreshStory = viewModel::refreshDiaryStory
                                            )
                                        }
                                        composable(NavRoutes.FAMILY) {
                                            ChildSupervisionScreen(
                                                profile = profile,
                                                userDisplayName = userDisplayName,
                                                supervisionState = childSupervision,
                                                onGeneratePairingCode = viewModel::generatePairingCode,
                                                onDisconnectParent = viewModel::disconnectParentLink,
                                                onRefreshTelemetry = { viewModel.refreshLocalDeviceTelemetry(context) },
                                                onStartAssignedTask = { subject, mins ->
                                                    viewModel.startAssignedTask(subject, mins)
                                                    navigateTo(NavRoutes.FOCUS)
                                                },
                                                onScreenShareStatusMessage = viewModel::showStatusMessage
                                            )
                                        }
                                    }

                                    if (isLockedOut) {
                                        val lockTitle = when {
                                            controls?.isPhoneLocked == true -> "🔒 Parent ne Phone Lock kiya hai"
                                            bedtimeLocked -> "🌙 Bedtime Lock Active"
                                            else -> "⏳ Daily Phone Limit Poori Hui"
                                        }
                                        PhoneLockOverlay(
                                            reasonTitle = lockTitle,
                                            lockMessage = controls?.lockMessage.orEmpty(),
                                            hasParentPin = !controls?.parentPin.isNullOrBlank(),
                                            onUnlockWithPin = viewModel::verifyParentPinAndUnlockTemporarily
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRoleSwitchPinDialog && activeProfile != null) {
        SafeAlertDialog(
            onDismissRequest = {
                showRoleSwitchPinDialog = false
            },
            title = { Text("Parent PIN Daalein 🔒") },
            text = {
                OutlinedTextField(
                    value = roleSwitchPinInput,
                    onValueChange = { if (it.length <= 4) roleSwitchPinInput = it.filter(Char::isDigit) },
                    label = { Text("Parent Mode kholne ke liye 4-digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { clearDialogInput ->
                Button(
                    onClick = {
                        clearDialogInput()
                        viewModel.selectRole("parent", activeProfile.displayName.ifBlank { userDisplayName }, roleSwitchPinInput)
                        showRoleSwitchPinDialog = false
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) { Text("Verify") }
            },
            dismissButton = { clearDialogInput ->
                TextButton(
                    onClick = {
                        clearDialogInput()
                        showRoleSwitchPinDialog = false
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) { Text("Cancel") }
            }
        )
    }
}
