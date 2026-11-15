package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbientSoundPlayer
import com.example.data.ai.SyllabusAiRepository
import com.example.data.model.FocusConstants
import com.example.data.model.MirrorEntry
import com.example.data.model.ReverseAlarmStatus
import com.example.data.model.SoundStats
import com.example.data.model.StudySession
import com.example.data.model.SubjectBreakdown
import com.example.data.model.UserProfile
import com.example.data.repository.FocusGardenRepository
import com.example.notifications.FocusNotifier
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.min

sealed interface GardenDataState {
    data object Loading : GardenDataState
    data class Ready(
        val profile: UserProfile,
        val sessions: List<StudySession>
    ) : GardenDataState
    data class Error(val message: String) : GardenDataState
}

sealed interface StudySetupUiState {
    data object Idle : StudySetupUiState
    data object Analyzing : StudySetupUiState
    data class Error(val message: String) : StudySetupUiState
}

data class FocusTimerUiState(
    val durationMinutes: Int = 25,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isBreakMode: Boolean = false,
    val isWilted: Boolean = false,
    val selectedSubject: String = "",
    val selectedTopic: String? = null,
    val sessionGoal: String = "",
    val selectedSound: String = FocusConstants.SOUNDS.first(),
    val isAudioMuted: Boolean = false,
    val plantEmoji: String = "🌰",
    val petMessage: String = "Padhai shuru karo, plant ko paani do.",
    val rewardMessage: String = "Session poora karoge toh random reward milega.",
    val rouletteMessage: String? = null,
    val showDistractionDialog: Boolean = false,
    val diaryStoryNonce: Int = 0
) {
    val progress: Float
        get() = if (totalSeconds <= 0) 0f else (1f - remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

    val formattedTime: String
        get() {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            return "%02d:%02d".format(mins, secs)
        }
}

class FocusGardenViewModel(
    private val repository: FocusGardenRepository,
    private val currentUserId: String,
    private val syllabusAiRepository: SyllabusAiRepository = SyllabusAiRepository(),
    private val soundPlayer: AmbientSoundPlayer = AmbientSoundPlayer(),
    private val notifier: FocusNotifier? = null
) : ViewModel() {

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

    private var timerJob: Job? = null

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
        val sortedSessions = sessions.sortedByDescending { it.createdAt?.seconds ?: 0L }
        GardenDataState.Ready(
            profile = resolvedProfile,
            sessions = sortedSessions
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

    init {
        notifier?.ensureChannels()
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                _clockTick.value = System.currentTimeMillis()
            }
        }
    }

    fun dismissStatusBanner() {
        _statusBanner.value = null
    }

    fun openStudySetupEditor() {
        _setupState.value = StudySetupUiState.Idle
        _isEditingStudyPlan.value = true
    }

    fun closeStudySetupEditor() {
        _setupState.value = StudySetupUiState.Idle
        _isEditingStudyPlan.value = false
    }

    fun clearSetupError() {
        if (_setupState.value is StudySetupUiState.Error) {
            _setupState.value = StudySetupUiState.Idle
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
            _setupState.value = StudySetupUiState.Error(
                "Kam se kam apni current study, subjects ya syllabus (photo/text) zaroor dein."
            )
            return
        }

        viewModelScope.launch {
            _setupState.value = StudySetupUiState.Analyzing
            val aiResult = syllabusAiRepository.analyzeStudyAndSyllabus(
                currentStudy = studyTrimmed,
                enteredSubjects = subjectsTrimmed,
                typedSyllabus = syllabusTrimmed,
                syllabusBitmap = syllabusBitmap
            )

            aiResult.onSuccess { plan ->
                val userSubjects = FocusConstants.parseSubjects(subjectsTrimmed)
                val mergedSubjects = (userSubjects + plan.subjects).distinct()
                val finalSubjectsText = mergedSubjects.joinToString(", ").take(500)
                if (mergedSubjects.isEmpty()) {
                    _setupState.value = StudySetupUiState.Error(
                        "Syllabus se subjects detect nahi ho paye. Kripya apne subjects likh kar dobara try karein."
                    )
                    return@onSuccess
                }

                val savedSyllabusText = syllabusTrimmed.ifBlank {
                    if (syllabusBitmap != null) "Syllabus photo se extract kiya gaya." else ""
                }

                repository.saveStudyPlan(
                    currentStudy = studyTrimmed.ifBlank { "My Study Plan" },
                    subjectsText = finalSubjectsText,
                    syllabusText = savedSyllabusText,
                    studySummary = plan.studySummary,
                    syllabusTopics = plan.syllabusTopics,
                    customChallenges = plan.customChallenges
                ).onSuccess {
                    _timerState.update { it.copy(selectedSubject = mergedSubjects.first(), selectedTopic = null) }
                    _setupState.value = StudySetupUiState.Idle
                    _isEditingStudyPlan.value = false
                }.onFailure { err ->
                    _setupState.value = StudySetupUiState.Error(
                        err.localizedMessage ?: "Study plan save karne mein error aaya."
                    )
                }
            }.onFailure { err ->
                _setupState.value = StudySetupUiState.Error(
                    err.localizedMessage ?: "Syllabus analyze nahi ho paya. Kripya dobara try karein."
                )
            }
        }
    }

    fun saveManualStudyPlanWithoutAi(
        currentStudy: String,
        enteredSubjects: String,
        typedSyllabus: String
    ) {
        val parsedSubjects = FocusConstants.parseSubjects(enteredSubjects)
        if (parsedSubjects.isEmpty()) {
            _setupState.value = StudySetupUiState.Error("Kam se kam ek subject zaroor likhein.")
            return
        }
        val studyLabel = currentStudy.trim().ifBlank { "My Study Plan" }
        val syllabusLines = typedSyllabus.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(40)
        val tailoredChallenges = parsedSubjects.take(5).map { subject ->
            "$subject ka ek chhota topic ya ek page abhi padho."
        }
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
                _setupState.value = StudySetupUiState.Error(
                    err.localizedMessage ?: "Study plan save nahi ho paya."
                )
            }
        }
    }

    fun setMood(moodKey: String) {
        viewModelScope.launch {
            repository.saveMood(moodKey).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Mood save nahi ho paya."
            }
        }
    }

    fun nextChallenge() {
        viewModelScope.launch {
            repository.advanceChallenge().onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Challenge update nahi ho paya."
            }
        }
    }

    fun saveAlarmTime(alarmTime: String, currentStudy: String = "Padhai") {
        viewModelScope.launch {
            repository.saveAlarmTime(alarmTime).onSuccess {
                notifier?.scheduleReverseAlarm(alarmTime, currentStudy)
                _statusBanner.value = "Reverse Alarm $alarmTime ke liye set ho gaya 🔔"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Alarm save nahi ho paya."
            }
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
            _timerState.update { state ->
                if (state.selectedSubject in parsed) state else state.copy(selectedSubject = parsed.first())
            }
        }
        viewModelScope.launch {
            repository.saveSubjectsText(cleaned).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Subjects save nahi ho paye."
            }
        }
    }

    fun focusOnSyllabusTopic(topic: String, allSubjects: List<String>) {
        if (_timerState.value.isRunning) return
        val prefixSubject = topic.substringBefore(":").trim()
        val matchedSubject = allSubjects.firstOrNull {
            it.equals(prefixSubject, ignoreCase = true)
        } ?: allSubjects.firstOrNull() ?: prefixSubject
        _timerState.update {
            it.copy(
                selectedSubject = matchedSubject,
                selectedTopic = topic,
                isBreakMode = false,
                durationMinutes = if (it.durationMinutes == 5) 25 else it.durationMinutes,
                totalSeconds = (if (it.durationMinutes == 5) 25 else it.durationMinutes) * 60,
                remainingSeconds = (if (it.durationMinutes == 5) 25 else it.durationMinutes) * 60,
                petMessage = "Topic ready: $topic"
            )
        }
    }

    fun spinRoulette(subjectsText: String, syllabusTopics: List<String> = emptyList()) {
        val list = FocusConstants.parseSubjects(subjectsText)
        if (list.isEmpty()) {
            _timerState.update { it.copy(rouletteMessage = "Pehle subjects likho.") }
            return
        }
        val picked = list.random()
        val matchingTopic = syllabusTopics
            .filter { it.contains(picked, ignoreCase = true) }
            .randomOrNull()
        val studyDuration = if (_timerState.value.durationMinutes == 5) 25 else _timerState.value.durationMinutes
        val msg = if (matchingTopic != null) {
            "\"$picked\" ($matchingTopic) ko $studyDuration minute padho!"
        } else {
            "\"$picked\" ko $studyDuration minute padho!"
        }
        _timerState.update { state ->
            state.copy(
                selectedSubject = picked,
                selectedTopic = matchingTopic,
                rouletteMessage = msg
            )
        }
        viewModelScope.launch {
            repository.saveSubjectsText(subjectsText.take(500))
        }
    }

    fun selectDuration(minutes: Int) {
        if (_timerState.value.isRunning) return
        val secs = minutes * 60
        val breakMode = (minutes == 5)
        _timerState.update {
            it.copy(
                durationMinutes = minutes,
                totalSeconds = secs,
                remainingSeconds = secs,
                isBreakMode = breakMode,
                isPaused = false,
                isWilted = false,
                plantEmoji = if (breakMode) "☕" else FocusConstants.STAGES.first(),
                petMessage = if (breakMode) {
                    "5 minute ka mindful break lo, phir fresh start!"
                } else {
                    "Padhai shuru karo, plant ko paani do."
                }
            )
        }
    }

    fun selectSubject(subject: String) {
        if (_timerState.value.isRunning) return
        _timerState.update { it.copy(selectedSubject = subject, selectedTopic = null) }
    }

    fun selectTopic(topic: String?) {
        if (_timerState.value.isRunning) return
        _timerState.update { it.copy(selectedTopic = topic) }
    }

    fun setSessionGoal(goal: String) {
        if (_timerState.value.isRunning) return
        _timerState.update { it.copy(sessionGoal = goal.take(80)) }
    }

    fun selectSound(sound: String) {
        _timerState.update { it.copy(selectedSound = sound) }
        val state = _timerState.value
        if (state.isRunning && !state.isPaused) {
            soundPlayer.start(sound, state.isAudioMuted)
        }
    }

    fun toggleAudioMute() {
        val nextMuted = !_timerState.value.isAudioMuted
        _timerState.update { it.copy(isAudioMuted = nextMuted) }
        val state = _timerState.value
        soundPlayer.setMuted(nextMuted, state.selectedSound, state.isRunning && !state.isPaused)
    }

    fun startQuickBreak() {
        if (_timerState.value.isRunning) return
        selectDuration(5)
        startSession()
    }

    fun startSession() {
        val current = _timerState.value
        if (current.isRunning) return
        val total = current.durationMinutes * 60
        _timerState.update {
            it.copy(
                totalSeconds = total,
                remainingSeconds = total,
                isRunning = true,
                isPaused = false,
                isWilted = false,
                showDistractionDialog = false,
                plantEmoji = if (it.isBreakMode) "☕" else FocusConstants.STAGES.first(),
                petMessage = if (it.isBreakMode) {
                    "Thoda aaraam karo, paani piyo 💧"
                } else {
                    FocusConstants.PET_MESSAGES.first()
                }
            )
        }
        soundPlayer.start(current.selectedSound, current.isAudioMuted)
        launchTickerLoop()
    }

    fun pauseOrResumeSession() {
        val state = _timerState.value
        if (!state.isRunning) return
        if (!state.isPaused) {
            timerJob?.cancel()
            soundPlayer.stop()
            _timerState.update {
                it.copy(
                    isPaused = true,
                    petMessage = "Session ruka hua hai ⏸️ Wapas resume karo!"
                )
            }
        } else {
            _timerState.update {
                it.copy(
                    isPaused = false,
                    petMessage = if (it.isBreakMode) "Break chal raha hai ☕" else "Wapas track par! Chal raha hai..."
                )
            }
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
                        petMessage = if (it.isBreakMode) {
                            "Aaraam karo, agla session aur strong hoga 🌿"
                        } else {
                            FocusConstants.PET_MESSAGES[msgIdx]
                        }
                    )
                }
                if (nextLeft <= 0) {
                    finishSession()
                    break
                }
            }
        }
    }

    private fun finishSession() {
        timerJob?.cancel()
        soundPlayer.stop()
        val state = _timerState.value
        val sessionLabel = state.selectedTopic?.takeIf { it.isNotBlank() } ?: state.selectedSubject.ifBlank { "Study" }
        notifier?.notifySessionComplete(sessionLabel, state.durationMinutes, state.isBreakMode)

        if (state.isBreakMode) {
            _timerState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    isWilted = false,
                    remainingSeconds = it.totalSeconds,
                    plantEmoji = "🌿",
                    petMessage = "Break poora hua! Agle focus session ke liye ready?",
                    rewardMessage = "☕ Mindful break complete! Ab ek naya focus session shuru karo."
                )
            }
            return
        }

        val sticker = FocusConstants.STICKERS.random()
        val quote = FocusConstants.QUOTES.random()
        val goalNote = state.sessionGoal.trim()
        val reward = if (goalNote.isNotEmpty()) {
            "$sticker Target \"$goalNote\" poora! $quote"
        } else {
            "$sticker $quote"
        }

        _timerState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                isWilted = false,
                remainingSeconds = it.totalSeconds,
                plantEmoji = "🌳",
                petMessage = "Shabash! Plant poora khil gaya.",
                rewardMessage = reward
            )
        }
        persistSession(
            subject = sessionLabel,
            sound = state.selectedSound,
            durationMinutes = state.durationMinutes.toLong(),
            done = true,
            rewardText = reward,
            distractionReasons = emptyList()
        )
    }

    fun giveUp() {
        val state = _timerState.value
        if (!state.isRunning) return
        timerJob?.cancel()
        soundPlayer.stop()
        if (state.isBreakMode) {
            _timerState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    isWilted = false,
                    remainingSeconds = it.totalSeconds,
                    plantEmoji = "☕",
                    petMessage = "Break band kiya. Padhai ke liye ready?"
                )
            }
            return
        }

        _timerState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                isWilted = true,
                remainingSeconds = it.totalSeconds,
                plantEmoji = "🥀",
                petMessage = "Plant murjha gaya 🥀 Phir se try karo."
            )
        }
        val sessionLabel = state.selectedTopic?.takeIf { it.isNotBlank() } ?: state.selectedSubject.ifBlank { "Study" }
        persistSession(
            subject = sessionLabel,
            sound = state.selectedSound,
            durationMinutes = state.durationMinutes.toLong(),
            done = false,
            rewardText = null,
            distractionReasons = emptyList()
        )
    }

    fun onAppBackgrounded() {
        val state = _timerState.value
        if (!state.isRunning || state.isPaused || state.isBreakMode) return
        timerJob?.cancel()
        soundPlayer.stop()
        val sessionLabel = state.selectedTopic?.takeIf { it.isNotBlank() } ?: state.selectedSubject.ifBlank { "Study" }
        notifier?.notifyBackgroundDistraction(sessionLabel)
        _timerState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                isWilted = true,
                plantEmoji = "🥀",
                petMessage = "App chhoda, plant udaas ho gaya 🥀",
                showDistractionDialog = true
            )
        }
    }

    fun onAppForegrounded() {
        notifier?.clearBackgroundDistraction()
    }

    fun pickDistractionReason(reason: String) {
        val state = _timerState.value
        _timerState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                isWilted = true,
                showDistractionDialog = false,
                remainingSeconds = it.totalSeconds,
                petMessage = "Koi baat nahi, wapas aao aur phir se shuru karo!"
            )
        }
        val sessionLabel = state.selectedTopic?.takeIf { it.isNotBlank() } ?: state.selectedSubject.ifBlank { "Study" }
        persistSession(
            subject = sessionLabel,
            sound = state.selectedSound,
            durationMinutes = state.durationMinutes.toLong(),
            done = false,
            rewardText = null,
            distractionReasons = listOf(reason)
        )
    }

    fun refreshDiaryStory() {
        _timerState.update { it.copy(diaryStoryNonce = it.diaryStoryNonce + 1) }
    }

    private fun persistSession(
        subject: String,
        sound: String,
        durationMinutes: Long,
        done: Boolean,
        rewardText: String?,
        distractionReasons: List<String>
    ) {
        viewModelScope.launch {
            repository.recordSession(
                day = FocusConstants.todayIsoDate(),
                subject = subject,
                sound = sound,
                durationMinutes = durationMinutes,
                done = done,
                rewardText = rewardText,
                distractionReasons = distractionReasons
            ).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Session save nahi ho paya."
            }
            _clockTick.value = System.currentTimeMillis()
        }
    }

    fun computeReverseAlarm(alarmTime: String?, sessions: List<StudySession>): ReverseAlarmStatus {
        val today = FocusConstants.todayIsoDate()
        val todayCount = sessions.count { it.day == today }
        return FocusConstants.evaluateReverseAlarm(alarmTime, todayCount, Calendar.getInstance())
    }

    fun computeSoundStats(sessions: List<StudySession>): List<SoundStats> =
        FocusConstants.computeSoundStats(sessions)

    fun computeBestSound(stats: List<SoundStats>): String? =
        FocusConstants.computeBestSound(stats)

    fun computeMirrorStats(sessions: List<StudySession>): List<MirrorEntry> =
        FocusConstants.computeMirrorStats(sessions)

    fun computeStreakDays(sessions: List<StudySession>): Int =
        FocusConstants.computeStreakDays(sessions)

    fun computeTodayFocusMinutes(sessions: List<StudySession>): Long =
        FocusConstants.computeTotalFocusMinutes(sessions, onlyDay = FocusConstants.todayIsoDate())

    fun computeTotalFocusMinutes(sessions: List<StudySession>): Long =
        FocusConstants.computeTotalFocusMinutes(sessions)

    fun computeSubjectBreakdown(sessions: List<StudySession>): List<SubjectBreakdown> =
        FocusConstants.computeSubjectBreakdown(sessions)

    fun computeTodayDiaryStory(sessions: List<StudySession>): String {
        val today = FocusConstants.todayIsoDate()
        return FocusConstants.buildDiaryStory(sessions.filter { it.day == today })
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        soundPlayer.stop()
    }

    private companion object {
        const val TAG = "FocusGardenVM"
    }
}
