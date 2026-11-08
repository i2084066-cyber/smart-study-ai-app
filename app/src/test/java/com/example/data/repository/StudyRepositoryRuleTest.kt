package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.StudyQuestion
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class StudyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createSubjectAndQuestion_authenticatedOwner_succeedsAndEmitsInFlow() = runBlocking {
        val aliceUid = signInTestUser("alice@smartstudy.test")
        val repository = StudyRepository(firestore, auth)

        val subjectResult = withTimeout(5000L) {
            repository.createSubject("Science", "Physics & Chemistry", "#7C3AED")
        }
        assertTrue(subjectResult.isSuccess)
        val subjectId = subjectResult.getOrThrow()

        val questionResult = withTimeout(5000L) {
            repository.addQuestion(
                StudyQuestion(
                    userId = aliceUid,
                    subjectId = subjectId,
                    subjectName = "Science",
                    chapterId = "ch_1",
                    chapterName = "Physics",
                    questionNumber = 1,
                    questionText = "What is the speed of light in vacuum?",
                    options = listOf("3x10^8 m/s", "3x10^6 m/s", "3x10^5 m/s", "3x10^10 m/s"),
                    correctAnswer = "A",
                    explanation = "Light travels at approximately 3 x 10^8 meters per second in vacuum.",
                    sourceReference = "Physics Chapter 1",
                    answerConfidence = "VERIFIED",
                    difficulty = "EASY"
                )
            )
        }
        assertTrue(questionResult.isSuccess)
        val questionId = questionResult.getOrThrow()

        val emittedQuestions = withTimeout(4000L) {
            repository.observeQuestions(aliceUid).first { list -> list.any { it.id == questionId } }
        }
        assertTrue(emittedQuestions.any { it.id == questionId })
    }

    @Test
    fun getQuestionById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice@smartstudy.test")
        val aliceRepo = StudyRepository(firestore, auth)

        val questionId = withTimeout(5000L) {
            aliceRepo.addQuestion(
                StudyQuestion(
                    userId = aliceUid,
                    subjectId = "subj_science",
                    subjectName = "Science",
                    chapterId = "ch_physics",
                    chapterName = "Physics",
                    questionNumber = 1,
                    questionText = "Private question for Alice",
                    options = listOf("A1", "B1", "C1", "D1"),
                    correctAnswer = "A"
                )
            ).getOrThrow()
        }

        signInTestUser("bob@smartstudy.test")
        val bobRepo = StudyRepository(firestore, auth)

        val readResult = withTimeout(5000L) {
            bobRepo.getQuestionById(questionId)
        }
        assertTrue(readResult.isFailure)
        val firestoreEx = readResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeQuestions_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = StudyRepository(firestore, auth)

        var caughtException: Throwable? = null
        try {
            withTimeout(4000L) {
                repository.observeQuestions("unauth_uid").first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val firestoreEx = caughtException?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
}
