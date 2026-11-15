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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.example.ui.FocusGardenViewModel
import com.example.ui.GardenDataState
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.DiaryScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MirrorScreen
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
    GardenNavDestination(
        NavRoutes.DIARY,
        R.string.nav_diary,
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook,
        "nav_diary"
    )
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mandatory: Configure Firebase App Check before UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            FocusGardenTheme {
                AppNavigation()
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
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FocusGardenViewModel(
                    repository = FocusGardenRepository(db),
                    currentUserId = currentUserId,
                    notifier = FocusNotificationHelper(app)
                )
            }
        }
    )

    val dataState by viewModel.dataState.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()
    val isEditingStudyPlan by viewModel.isEditingStudyPlan.collectAsStateWithLifecycle()
    val clockTick by viewModel.clockTick.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val message = statusBanner
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissStatusBanner()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val showOnboardingOrEditor = (dataState as? GardenDataState.Ready)?.let {
        !it.profile.onboardingCompleted || isEditingStudyPlan
    } ?: false

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
                                text = "🌱 Focus Garden",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = userDisplayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                signOut(credentialManager = credentialManager, scope = scope)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.sign_out)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen && !showOnboardingOrEditor && dataState is GardenDataState.Ready) {
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
                                label = { Text(stringResource(dest.labelRes)) },
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
                if (isWideScreen && !showOnboardingOrEditor && dataState is GardenDataState.Ready) {
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
                                label = { Text(stringResource(dest.labelRes)) },
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
                        GardenDataState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is GardenDataState.Error -> {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 480.dp)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(
                                    onClick = { navigateTo(NavRoutes.HOME) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Retry")
                                }
                            }
                        }

                        is GardenDataState.Ready -> {
                            val profile = state.profile
                            val sessions = state.sessions

                            if (!profile.onboardingCompleted || isEditingStudyPlan) {
                                StudySetupScreen(
                                    userDisplayName = userDisplayName,
                                    existingProfile = profile,
                                    isEditingExisting = profile.onboardingCompleted && isEditingStudyPlan,
                                    setupState = setupState,
                                    onCancelEdit = viewModel::closeStudySetupEditor,
                                    onSubmitWithAi = viewModel::analyzeAndSaveStudyPlan,
                                    onSubmitManual = viewModel::saveManualStudyPlanWithoutAi,
                                    onClearError = viewModel::clearSetupError,
                                    modifier = Modifier
                                        .widthIn(max = 600.dp)
                                        .fillMaxWidth()
                                )
                            } else {
                                val alarmStatus = remember(profile.alarmTime, sessions, clockTick) {
                                    viewModel.computeReverseAlarm(profile.alarmTime, sessions)
                                }
                                val streakDays = remember(sessions, clockTick) {
                                    viewModel.computeStreakDays(sessions)
                                }
                                val todayMinutes = remember(sessions, clockTick) {
                                    viewModel.computeTodayFocusMinutes(sessions)
                                }
                                val subjectBreakdown = remember(sessions) {
                                    viewModel.computeSubjectBreakdown(sessions)
                                }
                                val soundStats = remember(sessions) {
                                    viewModel.computeSoundStats(sessions)
                                }
                                val bestSound = remember(soundStats) {
                                    viewModel.computeBestSound(soundStats)
                                }
                                val mirrorEntries = remember(sessions) {
                                    viewModel.computeMirrorStats(sessions)
                                }
                                val diaryStory = remember(sessions, timerState.diaryStoryNonce) {
                                    viewModel.computeTodayDiaryStory(sessions)
                                }
                                val userSubjects = remember(profile.subjectsText) {
                                    FocusConstants.parseSubjects(profile.subjectsText)
                                }

                                NavHost(
                                    navController = navController,
                                    startDestination = NavRoutes.HOME,
                                    modifier = Modifier
                                        .widthIn(max = 600.dp)
                                        .fillMaxWidth()
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
                                            onSaveAlarmTime = { time ->
                                                viewModel.saveAlarmTime(time, profile.currentStudy)
                                            },
                                            onTestNotification = {
                                                viewModel.sendTestNotification(profile.currentStudy)
                                            },
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
