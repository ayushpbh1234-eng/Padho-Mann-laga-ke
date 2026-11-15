package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbientSoundPlayer
import com.example.data.ai.SyllabusAiRepository
import com.example.data.model.ChildDeviceState
import com.example.data.model.FamilyLink
import com.example.data.model.FocusConstants
import com.example.data.model.MirrorEntry
import com.example.data.model.PairingCode
import com.example.data.model.ParentControlRules
import com.example.data.model.ReverseAlarmStatus
import com.example.data.model.SoundStats
import com.example.data.model.StudySession
import com.example.data.model.SubjectBreakdown
import com.example.data.model.UserProfile
import com.example.data.repository.FocusGardenRepository
import com.example.notifications.FocusNotifier
import com.example.supervision.ChildSupervisionService
import com.example.supervision.DeviceUsageSnapshot
import com.example.supervision.ParentControlActions
import com.example.supervision.ScreenShareService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class)
class FocusGardenViewModel(
    private val repository: FocusGardenRepository,
    private val currentUserId: String,
    private val syllabusAiRepository: SyllabusAiRepository = SyllabusAiRepository(),
    private val soundPlayer: AmbientSoundPlayer = AmbientSoundPlayer(),
    private val notifier: FocusNotifier? = null
) : ViewModel() {

    private val parentControlActions = ParentControlActions(repository, currentUserId)

    private val _timerState = MutableStateFlow(FocusTimerUiState())
    val timerState: StateFlow<FocusTimerUiState> = _timerState.asStateFlow()

    private val _setupState = MutableStateFlow<StudySetupUiState>(StudySetupUiState.Idle)
    val setupState: StateFlow<StudySetupUiState> = _setupState.asStateFlow()

    private val _isEditingStudyPlan = MutableStateFlow(false)
    val isEditingStudyPlan: StateFlow<Boolean> = _isEditingStudyPlan.asStateFlow()

    private val _clockTick = MutableStateFlow(System.currentTimeMillis())
    val clockTick: StateFlow<Long> = _clockTick.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _selectedChildUid = MutableStateFlow<String?>(null)
    private val _localDeviceSnapshot = MutableStateFlow<DeviceUsageSnapshot?>(null)
    private val _temporarilyUnlockedUntilMs = MutableStateFlow(0L)

    private var timerJob: Job? = null
    private var lastObservedNudgeNonce: Long = -1L

    val dataState: StateFlow<GardenDataState> = combine(
        repository.observeProfile(currentUserId),
        repository.observeSessions(currentUserId)
    ) { profile, sessions ->
        val resolvedProfile = profile ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            onboardingCompleted = false,
            subjectsText = ""
        )
        val subjects = FocusConstants.parseSubjects(resolvedProfile.subjectsText)
        if (subjects.isNotEmpty() && _timerState.value.selectedSubject !in subjects) {
            _timerState.update { it.copy(selectedSubject = subjects.first()) }
        }
        GardenDataState.Ready(
            profile = resolvedProfile,
            sessions = sessions.sortedByDescending { it.createdAt?.seconds ?: 0L }
        ) as GardenDataState
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing Focus Garden data", rootError)
            emit(GardenDataState.Error(rootError.localizedMessage ?: "Failed to sync Focus Garden data"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = GardenDataState.Loading
        )

    private val childLinksFlow: StateFlow<List<FamilyLink>> = repository.observeFamilyLinksForChild(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val parentLinksFlow: StateFlow<List<FamilyLink>> = repository.observeFamilyLinksForParent(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val activePairingCodesFlow: StateFlow<List<PairingCode>> =
        repository.observeActivePairingCodesForChild(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val childParentControlsFlow: StateFlow<ParentControlRules?> =
        repository.observeParentControls(currentUserId)
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val childSupervisionState: StateFlow<ChildSupervisionState> = combine(
        combine(childLinksFlow, activePairingCodesFlow, childParentControlsFlow) { links, codes, controls ->
            Triple(links, codes, controls)
        },
        _localDeviceSnapshot,
        ChildSupervisionService.isRunning,
        ScreenShareService.isSharing
    ) { (links, codes, controls), snapshot, serviceRunning, screenShareRunning ->
        if (controls != null && controls.parentNudgeNonce > 0L && controls.parentNudgeNonce != lastObservedNudgeNonce) {
            if (lastObservedNudgeNonce != -1L && controls.parentNudgeText.isNotBlank()) {
                val parentName = links.firstOrNull()?.parentName ?: "Parent"
                notifier?.notifyParentNudge(parentName, controls.parentNudgeText)
            }
            lastObservedNudgeNonce = controls.parentNudgeNonce
        }
        ChildSupervisionState(
            familyLinks = links,
            activePairingCode = codes.maxByOrNull { it.createdAt?.seconds ?: 0L },
            parentControls = controls,
            localSnapshot = snapshot,
            serviceRunning = serviceRunning,
            screenShareRunning = screenShareRunning,
            temporarilyUnlockedUntilMs = _temporarilyUnlockedUntilMs.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ChildSupervisionState())

    val parentDashboardState: StateFlow<ParentDashboardState> = combine(
        parentLinksFlow,
        _selectedChildUid
    ) { links, selectedUid ->
        val resolvedUid = when {
            links.isEmpty() -> null
            selectedUid != null && links.any { it.childUid == selectedUid } -> selectedUid
            else -> links.first().childUid
        }
        links to resolvedUid
    }.flatMapLatest { (links, childUid) ->
        if (childUid == null) {
            flowOf(ParentDashboardState(linkedChildren = links))
        } else {
            combine(
                repository.observeProfile(childUid).catch { emit(null) },
                repository.observeChildDeviceState(childUid).catch { emit(null) },
                repository.observeParentControls(childUid).catch { emit(null) },
                repository.observeChildScreenShare(childUid).catch { emit(null) },
                repository.observeLinkedChildSessions(childUid, currentUserId).catch { emit(emptyList()) }
            ) { profile, deviceState, controls, screenShare, sessions ->
                ParentDashboardState(
                    linkedChildren = links,
                    selectedChildUid = childUid,
                    childProfile = profile,
                    childDeviceState = deviceState,
                    childControls = controls,
                    childScreenShare = screenShare,
                    childSessions = sessions.sortedByDescending { it.createdAt?.seconds ?: 0L },
                    isLoadingChild = false
                )
            }
        }
    }.catch { error ->
        Log.w(TAG, "Parent dashboard sync warning", error)
        emit(
            ParentDashboardState(
                linkedChildren = parentLinksFlow.value,
                selectedChildUid = _selectedChildUid.value,
                errorMessage = error.localizedMessage ?: "Child data sync nahi ho paya."
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ParentDashboardState())

    init {
        notifier?.ensureChannels()
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                _clockTick.value = System.currentTimeMillis()
            }
        }
    }

    fun dismissStatusBanner() { _statusBanner.value = null }
    fun openStudySetupEditor() { _setupState.value = StudySetupUiState.Idle; _isEditingStudyPlan.value = true }
    fun closeStudySetupEditor() { _setupState.value = StudySetupUiState.Idle; _isEditingStudyPlan.value = false }
    fun clearSetupError() { if (_setupState.value is StudySetupUiState.Error) _setupState.value = StudySetupUiState.Idle }

    fun selectRole(role: String, displayName: String, enteredPin: String = "") {
        val currentRole = (dataState.value as? GardenDataState.Ready)?.profile?.role
        val requiredPin = childSupervisionState.value.parentControls?.parentPin.orEmpty()
        if (currentRole == "child" && role != "child" && requiredPin.isNotEmpty() && enteredPin.trim() != requiredPin) {
            _statusBanner.value = "Role badalne ke liye Parent PIN daalo 🔒"
            return
        }
        viewModelScope.launch {
            repository.saveUserRoleAndName(role, displayName).onSuccess {
                _statusBanner.value = if (role == "parent") {
                    "Parent Mode chalu! 🛡️"
                } else {
                    "Child Mode chalu! 🌱"
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Role save nahi ho paya."
            }
        }
    }

    fun showStatusMessage(message: String) {
        _statusBanner.value = message
    }

    fun verifyParentPinAndUnlockTemporarily(enteredPin: String): Boolean {
        val requiredPin = childSupervisionState.value.parentControls?.parentPin.orEmpty()
        return if (requiredPin.isNotEmpty() && enteredPin.trim() == requiredPin) {
            _temporarilyUnlockedUntilMs.value = System.currentTimeMillis() + 10 * 60_000L
            _clockTick.value = System.currentTimeMillis()
            _statusBanner.value = "Parent PIN verified! 10 minute ke liye unlock 🔓"
            true
        } else {
            _statusBanner.value = "Galat Parent PIN ❌"
            false
        }
    }

    fun refreshLocalDeviceTelemetry(context: Context) {
        val ready = dataState.value as? GardenDataState.Ready ?: return
        if (ready.profile.role != "child") return
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                parentControlActions.syncChildDeviceState(
                    context = context.applicationContext,
                    profile = ready.profile,
                    sessions = ready.sessions,
                    timerState = _timerState.value
                )
            }
            _localDeviceSnapshot.value = snapshot
        }
    }

    fun generatePairingCode(childName: String) {
        viewModelScope.launch {
            repository.generatePairingCode(childName).onSuccess { code ->
                _statusBanner.value = "Naya Family Code ban gaya: $code 🔗"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Code generate nahi ho paya."
            }
        }
    }

    fun disconnectParentLink(linkId: String, enteredPin: String = "") {
        val requiredPin = childSupervisionState.value.parentControls?.parentPin.orEmpty()
        if (requiredPin.isNotEmpty() && enteredPin.trim() != requiredPin) {
            _statusBanner.value = "Disconnect karne ke liye Parent PIN daalo 🔒"
            return
        }
        viewModelScope.launch {
            repository.revokeFamilyLink(linkId).onSuccess {
                _statusBanner.value = "Family link hata diya gaya."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Link remove nahi ho paya."
            }
        }
    }

    fun selectManagedChild(childUid: String) { _selectedChildUid.value = childUid }

    fun linkChildByCode(code: String, parentName: String, onLinked: () -> Unit = {}) {
        viewModelScope.launch {
            repository.linkChildWithCode(code, parentName).onSuccess { link ->
                _selectedChildUid.value = link.childUid
                _statusBanner.value = "${link.childName} ka phone link ho gaya! 🛡️"
                onLinked()
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Child link nahi ho paya."
            }
        }
    }

    fun unlinkChild(linkId: String) {
        viewModelScope.launch {
            repository.revokeFamilyLink(linkId).onSuccess {
                _statusBanner.value = "Child device unlink ho gaya."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Unlink nahi ho paya."
            }
        }
    }

    fun toggleChildPhoneLock(locked: Boolean, lockMessage: String? = null) {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.toggleChildPhoneLock(childUid, locked, lockMessage).onSuccess {
                _statusBanner.value = if (locked) "Child ka phone lock kar diya gaya 🔒" else "Child ka phone unlock ho gaya 🔓"
            }.onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Lock update fail." }
        }
    }

    fun toggleStudyOnlyMode(enabled: Boolean) {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.toggleStudyOnlyMode(childUid, enabled).onSuccess {
                _statusBanner.value = if (enabled) "Study-Only Mode chalu 📚" else "Study-Only Mode band"
            }.onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Update fail." }
        }
    }

    fun setDailyScreenLimit(minutes: Long) {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.setDailyScreenLimit(childUid, minutes).onSuccess {
                _statusBanner.value = if (minutes <= 0L) "Daily limit hata di gayi" else "Daily limit $minutes min set ho gayi ⏳"
            }.onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Limit save fail." }
        }
    }

    fun grantBonusMinutes(extraMinutes: Long = 15L) {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.grantBonusMinutes(childUid, extraMinutes).onSuccess {
                _statusBanner.value = "+$extraMinutes min bonus time de diya gaya 🎁"
            }.onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Bonus grant fail." }
        }
    }

    fun toggleBedtimeLock(enabled: Boolean, start: String = "22:00", end: String = "06:00") {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.toggleBedtimeLock(childUid, enabled, start, end).onSuccess {
                _statusBanner.value = if (enabled) "Bedtime lock ($start-$end) chalu 🌙" else "Bedtime lock band"
            }.onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Bedtime update fail." }
        }
    }

    fun toggleBlockedPackage(packageName: String, blocked: Boolean) {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.toggleBlockedPackage(childUid, packageName, blocked).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "App block update fail."
            }
        }
    }

    fun setParentPin(pin: String) {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.setParentPin(childUid, pin).onSuccess {
                _statusBanner.value = if (pin.isBlank()) "Parent PIN hata diya gaya" else "4-digit Parent PIN set ho gaya 🔐"
            }.onFailure { err -> _statusBanner.value = err.localizedMessage ?: "PIN save fail." }
        }
    }

    fun sendParentNudge(message: String) {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.sendParentNudge(childUid, message).onSuccess {
                _statusBanner.value = "Child ke phone par instant message bhej diya gaya 📢"
            }.onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Nudge bhej nahi paaye." }
        }
    }

    fun requestChildScreenShare() {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.sendParentNudge(
                childUid,
                "Parent ne Live Screen Share ki permission maangi hai — Family tab mein jakar Allow & Start Screen Share dabayein 📺"
            ).onSuccess {
                _statusBanner.value = "Child ko Screen Share request bhej di gayi 📺"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Request bhej nahi paaye."
            }
        }
    }

    fun assignStudyTask(subject: String, durationMinutes: Long, alarmTime: String) {
        val childUid = parentDashboardState.value.selectedChildUid ?: return
        viewModelScope.launch {
            parentControlActions.assignStudyTask(childUid, subject, durationMinutes, alarmTime).onSuccess {
                _statusBanner.value = "Study task child ko assign ho gaya 📝"
            }.onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Assign fail." }
        }
    }

    fun analyzeAndSaveStudyPlan(
        currentStudy: String,
        enteredSubjects: String,
        typedSyllabus: String,
        syllabusBitmap: Bitmap?
    ) {
        val studyTrimmed = currentStudy.trim()
        val subjectsTrimmed = enteredSubjects.trim()
        val syllabusTrimmed = typedSyllabus.trim()
        if (studyTrimmed.isEmpty() && subjectsTrimmed.isEmpty() && syllabusTrimmed.isEmpty() && syllabusBitmap == null) {
            _setupState.value = StudySetupUiState.Error("Kam se kam apni current study, subjects ya syllabus (photo/text) zaroor dein.")
            return
        }
        viewModelScope.launch {
            _setupState.value = StudySetupUiState.Analyzing
            syllabusAiRepository.analyzeStudyAndSyllabus(studyTrimmed, subjectsTrimmed, syllabusTrimmed, syllabusBitmap)
                .onSuccess { plan ->
                    val mergedSubjects = (FocusConstants.parseSubjects(subjectsTrimmed) + plan.subjects).distinct()
                    if (mergedSubjects.isEmpty()) {
                        _setupState.value = StudySetupUiState.Error("Syllabus se subjects detect nahi ho paye. Kripya subjects likh kar try karein.")
                        return@onSuccess
                    }
                    repository.saveStudyPlan(
                        currentStudy = studyTrimmed.ifBlank { "My Study Plan" },
                        subjectsText = mergedSubjects.joinToString(", ").take(500),
                        syllabusText = syllabusTrimmed.ifBlank { if (syllabusBitmap != null) "Syllabus photo se extract kiya gaya." else "" },
                        studySummary = plan.studySummary,
                        syllabusTopics = plan.syllabusTopics,
                        customChallenges = plan.customChallenges
                    ).onSuccess {
                        _timerState.update { it.copy(selectedSubject = mergedSubjects.first(), selectedTopic = null) }
                        _setupState.value = StudySetupUiState.Idle
                        _isEditingStudyPlan.value = false
                    }.onFailure { err ->
                        _setupState.value = StudySetupUiState.Error(err.localizedMessage ?: "Study plan save karne mein error aaya.")
                    }
                }.onFailure { err ->
                    _setupState.value = StudySetupUiState.Error(err.localizedMessage ?: "Syllabus analyze nahi ho paya.")
                }
        }
    }

    fun saveManualStudyPlanWithoutAi(currentStudy: String, enteredSubjects: String, typedSyllabus: String) {
        val parsedSubjects = FocusConstants.parseSubjects(enteredSubjects)
        if (parsedSubjects.isEmpty()) {
            _setupState.value = StudySetupUiState.Error("Kam se kam ek subject zaroor likhein.")
            return
        }
        val studyLabel = currentStudy.trim().ifBlank { "My Study Plan" }
        val syllabusLines = typedSyllabus.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(40)
        val tailoredChallenges = parsedSubjects.take(5).map { "$it ka ek chhota topic ya ek page abhi padho." }
        viewModelScope.launch {
            _setupState.value = StudySetupUiState.Analyzing
            repository.saveStudyPlan(
                currentStudy = studyLabel,
                subjectsText = parsedSubjects.joinToString(", ").take(500),
                syllabusText = typedSyllabus.trim().take(4000),
                studySummary = "$studyLabel ke ${parsedSubjects.size} subjects ready hain!",
                syllabusTopics = syllabusLines,
                customChallenges = tailoredChallenges
            ).onSuccess {
                _timerState.update { it.copy(selectedSubject = parsedSubjects.first(), selectedTopic = null) }
                _setupState.value = StudySetupUiState.Idle
                _isEditingStudyPlan.value = false
            }.onFailure { err ->
                _setupState.value = StudySetupUiState.Error(err.localizedMessage ?: "Study plan save nahi ho paya.")
            }
        }
    }

    fun setMood(moodKey: String) {
        viewModelScope.launch { repository.saveMood(moodKey).onFailure { _statusBanner.value = it.localizedMessage ?: "Mood save nahi ho paya." } }
    }

    fun nextChallenge() {
        viewModelScope.launch { repository.advanceChallenge().onFailure { _statusBanner.value = it.localizedMessage ?: "Challenge update nahi ho paya." } }
    }

    fun saveAlarmTime(alarmTime: String, currentStudy: String = "Padhai") {
        viewModelScope.launch {
            repository.saveAlarmTime(alarmTime).onSuccess {
                notifier?.scheduleReverseAlarm(alarmTime, currentStudy)
                _statusBanner.value = "Reverse Alarm $alarmTime ke liye set ho gaya 🔔"
            }.onFailure { _statusBanner.value = it.localizedMessage ?: "Alarm save nahi ho paya." }
        }
    }

    fun sendTestNotification(currentStudy: String) {
        notifier?.sendTestReminderNotification(currentStudy)
        _statusBanner.value = "Reminder notification bhej diya gaya 🔔"
    }

    fun saveSubjectsText(subjectsText: String) {
        val cleaned = subjectsText.take(500)
        val parsed = FocusConstants.parseSubjects(cleaned)
        if (parsed.isNotEmpty()) {
            _timerState.update { state -> if (state.selectedSubject in parsed) state else state.copy(selectedSubject = parsed.first()) }
        }
        viewModelScope.launch { repository.saveSubjectsText(cleaned).onFailure { _statusBanner.value = it.localizedMessage ?: "Subjects save nahi ho paye." } }
    }

    fun focusOnSyllabusTopic(topic: String, allSubjects: List<String>) {
        if (_timerState.value.isRunning) return
        val prefixSubject = topic.substringBefore(":").trim()
        val matchedSubject = allSubjects.firstOrNull { it.equals(prefixSubject, ignoreCase = true) }
            ?: allSubjects.firstOrNull() ?: prefixSubject
        _timerState.update {
            val mins = if (it.durationMinutes == 5) 25 else it.durationMinutes
            it.copy(
                selectedSubject = matchedSubject, selectedTopic = topic, isBreakMode = false,
                durationMinutes = mins, totalSeconds = mins * 60, remainingSeconds = mins * 60,
                petMessage = "Topic ready: $topic"
            )
        }
    }

    fun startAssignedTask(subject: String, durationMinutes: Int) {
        if (_timerState.value.isRunning) return
        val mins = durationMinutes.takeIf { it > 0 } ?: 25
        _timerState.update {
            it.copy(
                selectedSubject = subject.ifBlank { it.selectedSubject }, selectedTopic = null,
                isBreakMode = false, durationMinutes = mins, totalSeconds = mins * 60, remainingSeconds = mins * 60,
                petMessage = "Parent ka task shuru: $subject ($mins min)"
            )
        }
        startSession()
    }

    fun spinRoulette(subjectsText: String, syllabusTopics: List<String> = emptyList()) {
        val list = FocusConstants.parseSubjects(subjectsText)
        if (list.isEmpty()) { _timerState.update { it.copy(rouletteMessage = "Pehle subjects likho.") }; return }
        val picked = list.random()
        val matchingTopic = syllabusTopics.filter { it.contains(picked, ignoreCase = true) }.randomOrNull()
        val studyDuration = if (_timerState.value.durationMinutes == 5) 25 else _timerState.value.durationMinutes
        val msg = if (matchingTopic != null) "\"$picked\" ($matchingTopic) ko $studyDuration minute padho!" else "\"$picked\" ko $studyDuration minute padho!"
        _timerState.update { it.copy(selectedSubject = picked, selectedTopic = matchingTopic, rouletteMessage = msg) }
        viewModelScope.launch { repository.saveSubjectsText(subjectsText.take(500)) }
    }

    fun selectDuration(minutes: Int) {
        if (_timerState.value.isRunning) return
        val breakMode = (minutes == 5)
        _timerState.update {
            it.copy(
                durationMinutes = minutes, totalSeconds = minutes * 60, remainingSeconds = minutes * 60,
                isBreakMode = breakMode, isPaused = false, isWilted = false,
                plantEmoji = if (breakMode) "☕" else FocusConstants.STAGES.first(),
                petMessage = if (breakMode) "5 minute ka mindful break lo, phir fresh start!" else "Padhai shuru karo, plant ko paani do."
            )
        }
    }

    fun selectSubject(subject: String) { if (!_timerState.value.isRunning) _timerState.update { it.copy(selectedSubject = subject, selectedTopic = null) } }
    fun selectTopic(topic: String?) { if (!_timerState.value.isRunning) _timerState.update { it.copy(selectedTopic = topic) } }
    fun setSessionGoal(goal: String) { if (!_timerState.value.isRunning) _timerState.update { it.copy(sessionGoal = goal.take(80)) } }

    fun selectSound(sound: String) {
        _timerState.update { it.copy(selectedSound = sound) }
        val state = _timerState.value
        if (state.isRunning && !state.isPaused) soundPlayer.start(sound, state.isAudioMuted)
    }

    fun toggleAudioMute() {
        val nextMuted = !_timerState.value.isAudioMuted
        _timerState.update { it.copy(isAudioMuted = nextMuted) }
        val state = _timerState.value
        soundPlayer.setMuted(nextMuted, state.selectedSound, state.isRunning && !state.isPaused)
    }

    fun startQuickBreak() { if (!_timerState.value.isRunning) { selectDuration(5); startSession() } }

    fun startSession() {
        val current = _timerState.value
        if (current.isRunning) return
        val total = current.durationMinutes * 60
        _timerState.update {
            it.copy(
                totalSeconds = total, remainingSeconds = total, isRunning = true, isPaused = false, isWilted = false,
                showDistractionDialog = false, plantEmoji = if (it.isBreakMode) "☕" else FocusConstants.STAGES.first(),
                petMessage = if (it.isBreakMode) "Thoda aaraam karo, paani piyo 💧" else FocusConstants.PET_MESSAGES.first()
            )
        }
        soundPlayer.start(current.selectedSound, current.isAudioMuted)
        launchTickerLoop()
    }

    fun pauseOrResumeSession() {
        val state = _timerState.value
        if (!state.isRunning) return
        if (!state.isPaused) {
            timerJob?.cancel(); soundPlayer.stop()
            _timerState.update { it.copy(isPaused = true, petMessage = "Session ruka hua hai ⏸️ Wapas resume karo!") }
        } else {
            _timerState.update { it.copy(isPaused = false, petMessage = if (it.isBreakMode) "Break chal raha hai ☕" else "Wapas track par! Chal raha hai...") }
            soundPlayer.start(state.selectedSound, state.isAudioMuted)
            launchTickerLoop()
        }
    }

    private fun launchTickerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val state = _timerState.value
                if (!state.isRunning || state.isPaused) break
                val nextLeft = (state.remainingSeconds - 1).coerceAtLeast(0)
                val prog = if (state.totalSeconds <= 0) 1f else 1f - nextLeft.toFloat() / state.totalSeconds.toFloat()
                val stageIdx = min(FocusConstants.STAGES.lastIndex, (prog * FocusConstants.STAGES.size).toInt())
                val msgIdx = min(FocusConstants.PET_MESSAGES.lastIndex, (prog * FocusConstants.PET_MESSAGES.size).toInt())
                _timerState.update {
                    it.copy(
                        remainingSeconds = nextLeft,
                        plantEmoji = if (it.isBreakMode) "☕" else FocusConstants.STAGES[stageIdx],
                        petMessage = if (it.isBreakMode) "Aaraam karo, agla session aur strong hoga 🌿" else FocusConstants.PET_MESSAGES[msgIdx]
                    )
                }
                if (nextLeft <= 0) { finishSession(); break }
            }
        }
    }

    private fun finishSession() {
        timerJob?.cancel(); soundPlayer.stop()
        val state = _timerState.value
        val label = state.selectedTopic?.takeIf { it.isNotBlank() } ?: state.selectedSubject.ifBlank { "Study" }
        notifier?.notifySessionComplete(label, state.durationMinutes, state.isBreakMode)
        if (state.isBreakMode) {
            _timerState.update { it.copy(isRunning = false, isPaused = false, isWilted = false, remainingSeconds = it.totalSeconds, plantEmoji = "🌿", petMessage = "Break poora hua!", rewardMessage = "☕ Mindful break complete!") }
            return
        }
        val sticker = FocusConstants.STICKERS.random(); val quote = FocusConstants.QUOTES.random()
        val reward = if (state.sessionGoal.trim().isNotEmpty()) "$sticker Target \"${state.sessionGoal.trim()}\" poora! $quote" else "$sticker $quote"
        _timerState.update { it.copy(isRunning = false, isPaused = false, isWilted = false, remainingSeconds = it.totalSeconds, plantEmoji = "🌳", petMessage = "Shabash! Plant poora khil gaya.", rewardMessage = reward) }
        persistSession(label, state.selectedSound, state.durationMinutes.toLong(), true, reward, emptyList())
    }

    fun giveUp() {
        val state = _timerState.value
        if (!state.isRunning) return
        timerJob?.cancel(); soundPlayer.stop()
        if (state.isBreakMode) {
            _timerState.update { it.copy(isRunning = false, isPaused = false, isWilted = false, remainingSeconds = it.totalSeconds, plantEmoji = "☕", petMessage = "Break band kiya.") }
            return
        }
        _timerState.update { it.copy(isRunning = false, isPaused = false, isWilted = true, remainingSeconds = it.totalSeconds, plantEmoji = "🥀", petMessage = "Plant murjha gaya 🥀 Phir se try karo.") }
        val label = state.selectedTopic?.takeIf { it.isNotBlank() } ?: state.selectedSubject.ifBlank { "Study" }
        persistSession(label, state.selectedSound, state.durationMinutes.toLong(), false, null, emptyList())
    }

    fun onAppBackgrounded() {
        val state = _timerState.value
        if (!state.isRunning || state.isPaused || state.isBreakMode) return
        timerJob?.cancel(); soundPlayer.stop()
        val label = state.selectedTopic?.takeIf { it.isNotBlank() } ?: state.selectedSubject.ifBlank { "Study" }
        notifier?.notifyBackgroundDistraction(label)
        _timerState.update { it.copy(isRunning = false, isPaused = false, isWilted = true, plantEmoji = "🥀", petMessage = "App chhoda, plant udaas ho gaya 🥀", showDistractionDialog = true) }
    }

    fun onAppForegrounded() { notifier?.clearBackgroundDistraction() }

    fun pickDistractionReason(reason: String) {
        val state = _timerState.value
        _timerState.update { it.copy(isRunning = false, isPaused = false, isWilted = true, showDistractionDialog = false, remainingSeconds = it.totalSeconds, petMessage = "Koi baat nahi, wapas aao aur phir se shuru karo!") }
        val label = state.selectedTopic?.takeIf { it.isNotBlank() } ?: state.selectedSubject.ifBlank { "Study" }
        persistSession(label, state.selectedSound, state.durationMinutes.toLong(), false, null, listOf(reason))
    }

    fun refreshDiaryStory() { _timerState.update { it.copy(diaryStoryNonce = it.diaryStoryNonce + 1) } }

    private fun persistSession(
        subject: String, sound: String, durationMinutes: Long, done: Boolean, rewardText: String?, distractionReasons: List<String>
    ) {
        val parentUids = childLinksFlow.value.map { it.parentUid }.distinct()
        viewModelScope.launch {
            repository.recordSession(
                day = FocusConstants.todayIsoDate(), subject = subject, sound = sound, durationMinutes = durationMinutes,
                done = done, rewardText = rewardText, distractionReasons = distractionReasons, authorizedParentUids = parentUids
            ).onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Session save nahi ho paya." }
            _clockTick.value = System.currentTimeMillis()
        }
    }

    fun computeReverseAlarm(alarmTime: String?, sessions: List<StudySession>): ReverseAlarmStatus {
        val today = FocusConstants.todayIsoDate()
        return FocusConstants.evaluateReverseAlarm(alarmTime, sessions.count { it.day == today }, Calendar.getInstance())
    }
    fun computeSoundStats(sessions: List<StudySession>): List<SoundStats> = FocusConstants.computeSoundStats(sessions)
    fun computeBestSound(stats: List<SoundStats>): String? = FocusConstants.computeBestSound(stats)
    fun computeMirrorStats(sessions: List<StudySession>): List<MirrorEntry> = FocusConstants.computeMirrorStats(sessions)
    fun computeStreakDays(sessions: List<StudySession>): Int = FocusConstants.computeStreakDays(sessions)
    fun computeTodayFocusMinutes(sessions: List<StudySession>): Long = FocusConstants.computeTotalFocusMinutes(sessions, onlyDay = FocusConstants.todayIsoDate())
    fun computeTotalFocusMinutes(sessions: List<StudySession>): Long = FocusConstants.computeTotalFocusMinutes(sessions)
    fun computeSubjectBreakdown(sessions: List<StudySession>): List<SubjectBreakdown> = FocusConstants.computeSubjectBreakdown(sessions)
    fun computeTodayDiaryStory(sessions: List<StudySession>): String = FocusConstants.buildDiaryStory(sessions.filter { it.day == FocusConstants.todayIsoDate() })

    override fun onCleared() { super.onCleared(); timerJob?.cancel(); soundPlayer.stop() }
    private companion object { const val TAG = "FocusGardenVM" }
}
