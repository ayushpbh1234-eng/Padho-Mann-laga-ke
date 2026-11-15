package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ChildDeviceState
import com.example.data.model.ChildScreenShare
import com.example.data.model.FamilyLink
import com.example.data.model.FocusConstants
import com.example.data.model.PairingCode
import com.example.data.model.ParentControlRules
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID

class FocusGardenRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                }
                throw error
            }
    }

    fun observeSessions(userId: String): Flow<List<StudySession>> {
        val colRef = db.collection("users").document(userId).collection("sessions")
        return colRef.snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    StudySession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                }
                throw error
            }
    }

    fun observeLinkedChildSessions(childUid: String, parentUid: String): Flow<List<StudySession>> {
        val query = db.collection("users")
            .document(childUid)
            .collection("sessions")
            .whereArrayContains("authorizedParentUids", parentUid)
        val path = "users/$childUid/sessions"
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StudySession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == parentUid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                }
                throw error
            }
    }

    fun observeFamilyLinksForParent(parentUid: String): Flow<List<FamilyLink>> {
        val query = db.collection("familyLinks").whereEqualTo("parentUid", parentUid)
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FamilyLink::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.status == "active" }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == parentUid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "familyLinks", auth)
                }
                throw error
            }
    }

    fun observeFamilyLinksForChild(childUid: String): Flow<List<FamilyLink>> {
        val query = db.collection("familyLinks").whereEqualTo("childUid", childUid)
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FamilyLink::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.status == "active" }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == childUid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "familyLinks", auth)
                }
                throw error
            }
    }

    fun observeActivePairingCodesForChild(childUid: String): Flow<List<PairingCode>> {
        val query = db.collection("pairingCodes").whereEqualTo("childUid", childUid)
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PairingCode::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.active }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == childUid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "pairingCodes", auth)
                }
                throw error
            }
    }

    fun observeChildDeviceState(childUid: String): Flow<ChildDeviceState?> {
        val docRef = db.collection("users").document(childUid)
            .collection("deviceState").document("current")
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        ChildDeviceState::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser != null) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                }
                throw error
            }
    }

    fun observeParentControls(childUid: String): Flow<ParentControlRules?> {
        val docRef = db.collection("users").document(childUid)
            .collection("parentControls").document("current")
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        ParentControlRules::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser != null) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                }
                throw error
            }
    }

    fun observeChildScreenShare(childUid: String): Flow<ChildScreenShare?> {
        val docRef = db.collection("users").document(childUid)
            .collection("screenShare").document("current")
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        ChildScreenShare::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser != null) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                }
                throw error
            }
    }

    suspend fun getSessionsOnce(userId: String = requireUserId()): Result<List<StudySession>> {
        val colRef = db.collection("users").document(userId).collection("sessions")
        return try {
            val snapshot = colRef.get().await()
            Result.success(
                snapshot.toObjects(
                    StudySession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun getProfileOnce(userId: String = requireUserId()): Result<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return try {
            val snapshot = docRef.get().await()
            val profile = if (snapshot.exists()) {
                snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun saveUserRoleAndName(role: String, displayName: String): Result<Unit> =
        upsertProfileFields { current ->
            buildMap {
                put("role", role)
                put("displayName", displayName.trim().take(100))
                if (role == "parent" && current?.onboardingCompleted != true) {
                    put("onboardingCompleted", true)
                }
            }
        }

    suspend fun saveMood(mood: String): Result<Unit> = upsertProfileFields { _ ->
        mapOf("mood" to mood)
    }

    suspend fun advanceChallenge(): Result<Unit> = upsertProfileFields { current ->
        val nextIdx = (current?.challengeIdx ?: 0L) + 1L
        mapOf("challengeIdx" to nextIdx)
    }

    suspend fun saveAlarmTime(alarmTime: String): Result<Unit> = upsertProfileFields { _ ->
        mapOf("alarmTime" to alarmTime)
    }

    suspend fun saveSubjectsText(subjectsText: String): Result<Unit> = upsertProfileFields { _ ->
        mapOf("subjectsText" to subjectsText.take(500))
    }

    suspend fun saveStudyPlan(
        currentStudy: String,
        subjectsText: String,
        syllabusText: String,
        studySummary: String,
        syllabusTopics: List<String>,
        customChallenges: List<String>
    ): Result<Unit> = upsertProfileFields { _ ->
        buildMap {
            put("role", "child")
            put("onboardingCompleted", true)
            put("currentStudy", currentStudy.trim().take(200))
            put("subjectsText", subjectsText.trim().take(500))
            put("syllabusText", syllabusText.trim().take(4000))
            put("studySummary", studySummary.trim().take(500))
            put("syllabusTopics", syllabusTopics.map { it.trim() }.filter { it.isNotEmpty() }.take(40))
            put("customChallenges", customChallenges.map { it.trim() }.filter { it.isNotEmpty() }.take(20))
        }
    }

    suspend fun generatePairingCode(childName: String): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val code = generateRandomCode()
        val docRef = db.collection("pairingCodes").document(code)
        val payload = mapOf(
            "code" to code,
            "childUid" to uid,
            "childName" to childName.trim().ifBlank { "Student" }.take(100),
            "active" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun linkChildWithCode(rawCode: String, parentName: String): Result<FamilyLink> {
        val parentUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val code = rawCode.trim().uppercase()
        if (!Regex("^[A-Z0-9]{6}$").matches(code)) {
            return Result.failure(IllegalArgumentException("6-letter/number valid Family Code likho (jaise AB12CD)."))
        }

        val codeRef = db.collection("pairingCodes").document(code)
        return try {
            val codeSnap = codeRef.get().await()
            if (!codeSnap.exists()) {
                return Result.failure(IllegalStateException("Yeh code galat hai ya exist nahi karta."))
            }
            val pairing = codeSnap.toObject(PairingCode::class.java)
                ?: return Result.failure(IllegalStateException("Code data invalid hai."))
            if (!pairing.active) {
                return Result.failure(IllegalStateException("Yeh code pehle hi use ho chuka hai."))
            }
            if (pairing.childUid == parentUid) {
                return Result.failure(IllegalStateException("Aap apna khud ka child code link nahi kar sakte — child ke phone ke Google account ka code daalein."))
            }

            val linkId = "${parentUid}_${pairing.childUid}"
            val linkRef = db.collection("familyLinks").document(linkId)
            val controlsRef = db.collection("users").document(pairing.childUid)
                .collection("parentControls").document("current")

            val cleanParentName = parentName.trim().ifBlank { "Parent" }.take(100)
            val existingLinks = db.collection("familyLinks")
                .whereEqualTo("parentUid", parentUid)
                .get()
                .await()
            val alreadyExists = existingLinks.documents.any { it.id == linkId }

            val batch = db.batch()
            if (alreadyExists) {
                batch.update(
                    linkRef,
                    mapOf(
                        "status" to "active",
                        "parentName" to cleanParentName,
                        "childName" to pairing.childName.take(100),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val linkPayload = mapOf(
                    "parentUid" to parentUid,
                    "childUid" to pairing.childUid,
                    "parentName" to cleanParentName,
                    "childName" to pairing.childName.take(100),
                    "pairingCode" to code,
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(linkRef, linkPayload)
            }
            batch.update(
                codeRef,
                mapOf(
                    "active" to false,
                    "usedByParentUid" to parentUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            val existingControls = controlsRef.get().await()
            if (!existingControls.exists()) {
                controlsRef.set(
                    mapOf(
                        "childUid" to pairing.childUid,
                        "parentUid" to parentUid,
                        "isPhoneLocked" to false,
                        "lockMessage" to "Abhi padhai ka time hai! Phone side rakho.",
                        "dailyScreenLimitMinutes" to 0L,
                        "bonusMinutesToday" to 0L,
                        "bonusDay" to FocusConstants.todayIsoDate(),
                        "bedtimeEnabled" to false,
                        "bedtimeStart" to "22:00",
                        "bedtimeEnd" to "06:00",
                        "blockedPackages" to emptyList<String>(),
                        "studyOnlyMode" to false,
                        "parentNudgeText" to "",
                        "parentNudgeNonce" to 0L,
                        "parentPin" to "",
                        "assignedSubject" to "",
                        "assignedDurationMinutes" to 0L,
                        "assignedAlarmTime" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                controlsRef.update(
                    mapOf(
                        "parentUid" to parentUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(
                FamilyLink(
                    id = linkId,
                    parentUid = parentUid,
                    childUid = pairing.childUid,
                    parentName = cleanParentName,
                    childName = pairing.childName,
                    pairingCode = code,
                    status = "active"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "familyLinks", auth)
            Result.failure(e)
        }
    }

    suspend fun revokeFamilyLink(linkId: String): Result<Unit> {
        val docRef = db.collection("familyLinks").document(linkId)
        return try {
            docRef.update(
                mapOf(
                    "status" to "revoked",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun publishChildDeviceState(state: ChildDeviceState): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("deviceState").document("current")
        val appsPayload = state.installedApps.take(60).map { app ->
            mapOf(
                "packageName" to app.packageName.take(200),
                "appName" to app.appName.take(60),
                "minutesToday" to app.minutesToday.coerceIn(0L, 1440L),
                "isSystemApp" to app.isSystemApp
            )
        }
        return try {
            db.runTransaction { tx ->
                val existing = tx.get(docRef)
                val fields = mutableMapOf<String, Any>(
                    "childName" to state.childName.take(100),
                    "deviceModel" to state.deviceModel.take(100),
                    "androidVersion" to state.androidVersion.take(40),
                    "batteryPercent" to state.batteryPercent.coerceIn(-1L, 100L),
                    "isCharging" to state.isCharging,
                    "usageAccessGranted" to state.usageAccessGranted,
                    "notificationsGranted" to state.notificationsGranted,
                    "supervisionServiceRunning" to state.supervisionServiceRunning,
                    "currentForegroundApp" to state.currentForegroundApp.take(100),
                    "currentForegroundPackage" to state.currentForegroundPackage.take(200),
                    "totalPhoneScreenMinutesToday" to state.totalPhoneScreenMinutesToday.coerceIn(0L, 1440L),
                    "isFocusSessionActive" to state.isFocusSessionActive,
                    "activeFocusSubject" to state.activeFocusSubject.take(100),
                    "todayStudyMinutes" to state.todayStudyMinutes.coerceIn(0L, 1440L),
                    "todayCompletedSessions" to state.todayCompletedSessions.coerceIn(0L, 1000L),
                    "streakDays" to state.streakDays.coerceIn(0L, 10000L),
                    "recentBlockedAttempt" to state.recentBlockedAttempt.take(200),
                    "installedApps" to appsPayload,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!existing.exists()) {
                    fields["childUid"] = uid
                    fields["createdAt"] = FieldValue.serverTimestamp()
                    tx.set(docRef, fields)
                } else {
                    tx.update(docRef, fields)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun publishChildScreenShare(
        isSharing: Boolean,
        frameBase64: String,
        frameWidth: Long,
        frameHeight: Long,
        frameSeq: Long,
        captureMode: String = "full_screen"
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
            .collection("screenShare").document("current")
        val safeBase64 = frameBase64.take(240_000)
        val safeWidth = frameWidth.coerceIn(0L, 2160L)
        val safeHeight = frameHeight.coerceIn(0L, 3840L)
        val safeSeq = frameSeq.coerceIn(0L, 999_999_999L)
        val safeMode = captureMode.take(40)
        return try {
            db.runTransaction { tx ->
                val existing = tx.get(docRef)
                val fields = mutableMapOf<String, Any>(
                    "isSharing" to isSharing,
                    "frameBase64" to safeBase64,
                    "frameWidth" to safeWidth,
                    "frameHeight" to safeHeight,
                    "frameSeq" to safeSeq,
                    "captureMode" to safeMode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!existing.exists()) {
                    fields["childUid"] = uid
                    fields["createdAt"] = FieldValue.serverTimestamp()
                    tx.set(docRef, fields)
                } else {
                    tx.update(docRef, fields)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateParentControls(
        childUid: String,
        transform: (ParentControlRules) -> Map<String, Any>
    ): Result<Unit> {
        val parentUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(childUid)
            .collection("parentControls").document("current")
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val current = if (snap.exists()) {
                    snap.toObject(ParentControlRules::class.java) ?: ParentControlRules(
                        childUid = childUid,
                        parentUid = parentUid
                    )
                } else {
                    ParentControlRules(childUid = childUid, parentUid = parentUid)
                }
                val updates = transform(current)
                if (!snap.exists()) {
                    val base = mutableMapOf<String, Any>(
                        "childUid" to childUid,
                        "parentUid" to parentUid,
                        "isPhoneLocked" to false,
                        "lockMessage" to "Abhi padhai ka time hai! Phone side rakho.",
                        "dailyScreenLimitMinutes" to 0L,
                        "bonusMinutesToday" to 0L,
                        "bonusDay" to FocusConstants.todayIsoDate(),
                        "bedtimeEnabled" to false,
                        "bedtimeStart" to "22:00",
                        "bedtimeEnd" to "06:00",
                        "blockedPackages" to emptyList<String>(),
                        "studyOnlyMode" to false,
                        "parentNudgeText" to "",
                        "parentNudgeNonce" to 0L,
                        "parentPin" to "",
                        "assignedSubject" to "",
                        "assignedDurationMinutes" to 0L,
                        "assignedAlarmTime" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    base.putAll(updates)
                    tx.set(docRef, base)
                } else {
                    val patch = mutableMapOf<String, Any>(
                        "parentUid" to parentUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    patch.putAll(updates)
                    tx.update(docRef, patch)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    private suspend fun upsertProfileFields(
        buildFields: (UserProfile?) -> Map<String, Any>
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        return try {
            db.runTransaction { tx ->
                val snapshot = tx.get(docRef)
                val existing = if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java)
                } else {
                    null
                }
                val fields = buildFields(existing)
                if (!snapshot.exists()) {
                    val createPayload = mutableMapOf<String, Any>(
                        "userId" to uid,
                        "onboardingCompleted" to false,
                        "challengeIdx" to 0L,
                        "subjectsText" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    createPayload.putAll(fields)
                    tx.set(docRef, createPayload)
                } else {
                    val updatePayload = mutableMapOf<String, Any>(
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    updatePayload.putAll(fields)
                    tx.update(docRef, updatePayload)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun recordSession(
        day: String,
        subject: String,
        sound: String,
        durationMinutes: Long,
        done: Boolean,
        rewardText: String?,
        distractionReasons: List<String>,
        authorizedParentUids: List<String> = emptyList()
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val sessionId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
        val payload = buildMap<String, Any> {
            put("userId", uid)
            if (authorizedParentUids.isNotEmpty()) {
                put("authorizedParentUids", authorizedParentUids.distinct().take(10))
            }
            put("day", day)
            put("subject", subject.trim().ifEmpty { "Study" }.take(100))
            put("sound", sound.trim().ifEmpty { FocusConstants.SOUNDS.first() }.take(50))
            put("durationMinutes", durationMinutes.coerceIn(1L, 180L))
            put("done", done)
            if (!rewardText.isNullOrBlank()) {
                put("rewardText", rewardText.take(300))
            }
            val boundedReasons = distractionReasons.take(20)
            put("distractionReasons", boundedReasons)
            put("distractionCount", boundedReasons.size.toLong())
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.set(payload).await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    private fun generateRandomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return buildString(6) {
            repeat(6) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }
}
