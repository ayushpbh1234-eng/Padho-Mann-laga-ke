package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ChildDeviceState
import com.example.data.model.FocusConstants
import com.example.data.model.PhoneAppUsageInfo
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
    fun parentChildPairing_allowsParentDeviceStateReadAndPhoneLockControl() = runBlocking {
        val childUid = signInTestUser(ALICE_EMAIL)
        val childRepo = FocusGardenRepository(firestore, auth)

        val code = withTimeout(DEFAULT_TIMEOUT_MS) {
            childRepo.saveUserRoleAndName("child", "Aarav").getOrThrow()
            childRepo.publishChildDeviceState(
                ChildDeviceState(
                    childUid = childUid,
                    childName = "Aarav",
                    deviceModel = "Pixel Test",
                    androidVersion = "Android 15",
                    batteryPercent = 76L,
                    isCharging = false,
                    usageAccessGranted = true,
                    notificationsGranted = true,
                    supervisionServiceRunning = true,
                    currentForegroundApp = "YouTube",
                    currentForegroundPackage = "com.google.android.youtube",
                    totalPhoneScreenMinutesToday = 88L,
                    installedApps = listOf(
                        PhoneAppUsageInfo("com.google.android.youtube", "YouTube", 45L, false)
                    )
                )
            ).getOrThrow()
            childRepo.generatePairingCode("Aarav").getOrThrow()
        }
        assertEquals(6, code.length)

        val parentUid = signInTestUser(BOB_EMAIL)
        val parentRepo = FocusGardenRepository(firestore, auth)
        val link = withTimeout(DEFAULT_TIMEOUT_MS) {
            parentRepo.saveUserRoleAndName("parent", "Papa").getOrThrow()
            parentRepo.linkChildWithCode(code, "Papa").getOrThrow()
        }
        assertEquals(childUid, link.childUid)
        assertEquals(parentUid, link.parentUid)

        val observedDevice = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeChildDeviceState(childUid).first { it != null }
        }
        assertEquals("Pixel Test", observedDevice?.deviceModel)
        assertEquals(88L, observedDevice?.totalPhoneScreenMinutesToday)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            parentRepo.updateParentControls(childUid) {
                mapOf(
                    "isPhoneLocked" to true,
                    "studyOnlyMode" to true,
                    "blockedPackages" to listOf("com.google.android.youtube"),
                    "dailyScreenLimitMinutes" to 90L
                )
            }.getOrThrow()
        }

        val updatedControls = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeParentControls(childUid).first { it?.isPhoneLocked == true }
        }
        assertEquals(true, updatedControls?.studyOnlyMode)
        assertEquals(listOf("com.google.android.youtube"), updatedControls?.blockedPackages)

        // Child signs back in to publish an explicitly consented screen share frame
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            childRepo.publishChildScreenShare(
                isSharing = true,
                frameBase64 = "ZmFrZV9zY3JlZW5fZnJhbWU=",
                frameWidth = 360L,
                frameHeight = 760L,
                frameSeq = 1L,
                captureMode = "full_screen"
            ).getOrThrow()
        }

        // Parent signs in and observes the live screen share frame
        signInTestUser(BOB_EMAIL)
        val observedScreen = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeChildScreenShare(childUid).first { it?.isSharing == true }
        }
        assertEquals("ZmFrZV9zY3JlZW5fZnJhbWU=", observedScreen?.frameBase64)
        assertEquals(360L, observedScreen?.frameWidth)
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

        signInTestUser(STRANGER_EMAIL)
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
        const val BOB_EMAIL = "bob_parent@test.com"
        const val STRANGER_EMAIL = "stranger_eve@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
