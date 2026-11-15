package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.FocusConstants
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

    suspend fun saveMood(mood: String): Result<Unit> = upsertProfileFields { current ->
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
            put("onboardingCompleted", true)
            put("currentStudy", currentStudy.trim().take(200))
            put("subjectsText", subjectsText.trim().take(500))
            put("syllabusText", syllabusText.trim().take(4000))
            put("studySummary", studySummary.trim().take(500))
            put("syllabusTopics", syllabusTopics.map { it.trim() }.filter { it.isNotEmpty() }.take(40))
            put("customChallenges", customChallenges.map { it.trim() }.filter { it.isNotEmpty() }.take(20))
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
        distractionReasons: List<String>
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
}
