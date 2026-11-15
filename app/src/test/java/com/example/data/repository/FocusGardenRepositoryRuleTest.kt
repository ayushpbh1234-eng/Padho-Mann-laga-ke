package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FocusConstants
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusGardenRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun profileUpsertAndAdvance_authenticatedOwner_persistsState() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = FocusGardenRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            assertTrue(
                repository.saveStudyPlan(
                    currentStudy = "NEET 2027",
                    subjectsText = "Biology, Organic Chemistry, Physics",
                    syllabusText = "Human Physiology, Rotational Motion",
                    studySummary = "NEET 2027 study garden ready.",
                    syllabusTopics = listOf("Biology: Human Physiology", "Physics: Rotational Motion"),
                    customChallenges = listOf("Biology ka ek diagram draw karo.")
                ).isSuccess
            )
            assertTrue(repository.saveMood("energetic").isSuccess)
            assertTrue(repository.advanceChallenge().isSuccess)
            assertTrue(repository.saveAlarmTime("18:30").isSuccess)
        }

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getProfileOnce(uid).getOrThrow()
        }
        assertNotNull(profile)
        assertEquals(true, profile?.onboardingCompleted)
        assertEquals("NEET 2027", profile?.currentStudy)
        assertEquals("Biology, Organic Chemistry, Physics", profile?.subjectsText)
        assertEquals(2, profile?.syllabusTopics?.size)
        assertEquals("energetic", profile?.mood)
        assertEquals("18:30", profile?.alarmTime)
        assertTrue((profile?.challengeIdx ?: 0L) >= 1L)
    }

    @Test
    fun recordAndObserveSessions_authenticatedOwner_emitsCreatedSession() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = FocusGardenRepository(firestore, auth)

        val sessionId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordSession(
                day = FocusConstants.todayIsoDate(),
                subject = "DBMS",
                sound = "Barish 🌧",
                durationMinutes = 25L,
                done = true,
                rewardText = "⭐ Ek page bhi ek jeet hai.",
                distractionReasons = emptyList()
            ).getOrThrow()
        }
        assertTrue(sessionId.isNotEmpty())

        val sessions = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSessions(uid).first { list -> list.any { it.id == sessionId } }
        }
        val created = sessions.first { it.id == sessionId }
        assertEquals("DBMS", created.subject)
        assertEquals(true, created.done)
    }

    @Test
    fun crossUserAccess_bobReadingAliceSessions_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FocusGardenRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.recordSession(
                day = FocusConstants.todayIsoDate(),
                subject = "Maths",
                sound = "Silence 🤫",
                durationMinutes = 10L,
                done = false,
                rewardText = null,
                distractionReasons = listOf("Phone")
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FocusGardenRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSessionsOnce(aliceUid)
        }
        assertTrue(result.isFailure)
        val firestoreException = checkNotNull(result.exceptionOrNull()?.unwrapFirestoreException())
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeSessions_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = FocusGardenRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeSessions("some_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertNotNull(caught)
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_focus@test.com"
        const val BOB_EMAIL = "bob_focus@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
