package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.PracticeSession
import com.example.data.model.StudyChapter
import com.example.data.model.StudyQuestion
import com.example.data.model.StudyResult
import com.example.data.model.StudySubject
import com.example.data.model.StudyTest
import com.example.data.model.UploadedMaterial
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StudyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- USER PROFILE & STREAK ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureOrUpdateUserProfile(name: String, email: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "name" to name.ifBlank { "Scholar" }.take(120),
                    "email" to email.take(200),
                    "studyStreak" to 1,
                    "lastStudyDate" to todayStr,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val lastDate = existingSnap.getString("lastStudyDate").orEmpty()
                val currentStreak = (existingSnap.getLong("studyStreak") ?: 1L).toInt()
                val newStreak = calculateNextStreak(lastDate, todayStr, currentStreak)
                if (lastDate != todayStr) {
                    docRef.update(
                        mapOf(
                            "studyStreak" to newStreak,
                            "lastStudyDate" to todayStr,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    private fun calculateNextStreak(lastDate: String, todayStr: String, currentStreak: Int): Int {
        if (lastDate == todayStr) return currentStreak.coerceAtLeast(1)
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(todayStr) ?: Date()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = sdf.format(cal.time)
            if (lastDate == yesterdayStr) currentStreak + 1 else 1
        } catch (_: Exception) {
            1
        }
    }

    // --- SUBJECTS ---

    fun observeSubjects(userId: String): Flow<List<StudySubject>> = flow {
        val path = "subjects"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StudySubject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.subjectName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createSubject(
        subjectName: String,
        description: String = "",
        colorHex: String = "#7C3AED"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("subjects").document()
        val payload = mapOf(
            "userId" to uid,
            "subjectName" to subjectName.trim().take(120),
            "description" to description.trim().take(500),
            "colorHex" to colorHex.take(16),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteSubject(subjectId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("subjects").document(subjectId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- CHAPTERS ---

    fun observeChapters(userId: String): Flow<List<StudyChapter>> = flow {
        val path = "chapters"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StudyChapter::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy({ it.subjectId }, { it.chapterNumber }))
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createChapter(
        subjectId: String,
        chapterName: String,
        chapterNumber: Int = 1
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("chapters").document()
        val payload = mapOf(
            "userId" to uid,
            "subjectId" to subjectId.take(128),
            "chapterName" to chapterName.trim().take(150),
            "chapterNumber" to chapterNumber.coerceIn(1, 1000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteChapter(chapterId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("chapters").document(chapterId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- QUESTIONS ---

    fun observeQuestions(userId: String): Flow<List<StudyQuestion>> = flow {
        val path = "questions"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StudyQuestion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy({ it.subjectName }, { it.chapterName }, { it.questionNumber }))
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getQuestionById(questionId: String): Result<StudyQuestion> = runCatching {
        requireUserId()
        val docRef = db.collection("questions").document(questionId)
        try {
            val snap = docRef.get().await()
            snap.toObject(StudyQuestion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw IllegalStateException("Question not found")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun addQuestion(question: StudyQuestion): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("questions").document()
        val normalizedOptions = (question.options + listOf("Option A", "Option B", "Option C", "Option D"))
            .take(4)
            .mapIndexed { idx, opt -> opt.trim().ifBlank { "Option ${'A' + idx}" }.take(1000) }
        val normalizedCorrect = question.correctAnswer.uppercase(Locale.US)
            .takeIf { it in listOf("A", "B", "C", "D") } ?: "A"
        val normalizedConfidence = question.answerConfidence.uppercase(Locale.US)
            .takeIf { it in listOf("VERIFIED", "AI_GENERATED", "NEEDS_REVIEW") } ?: "VERIFIED"
        val normalizedDifficulty = question.difficulty.uppercase(Locale.US)
            .takeIf { it in listOf("EASY", "MEDIUM", "HARD") } ?: "MEDIUM"

        val payload = mapOf(
            "userId" to uid,
            "subjectId" to question.subjectId.ifBlank { "default_subject" }.take(128),
            "subjectName" to question.subjectName.ifBlank { "General Studies" }.take(120),
            "chapterId" to question.chapterId.take(128),
            "chapterName" to question.chapterName.take(150),
            "questionNumber" to question.questionNumber.coerceIn(1, 100000),
            "questionText" to question.questionText.trim().ifBlank { "Untitled Question" }.take(3000),
            "options" to normalizedOptions,
            "correctAnswer" to normalizedCorrect,
            "explanation" to question.explanation.trim().take(3000),
            "sourceReference" to question.sourceReference.trim().take(300),
            "sourceImage" to question.sourceImage.trim().take(500),
            "answerConfidence" to normalizedConfidence,
            "difficulty" to normalizedDifficulty,
            "isBookmarked" to question.isBookmarked,
            "collectionTag" to question.collectionTag.trim().ifBlank { "General" }.take(100),
            "lastAttemptCorrect" to question.lastAttemptCorrect,
            "attemptCount" to question.attemptCount.coerceIn(0, 100000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateQuestion(question: StudyQuestion): Result<Unit> = runCatching {
        requireUserId()
        require(question.id.isNotBlank()) { "Question ID must not be blank for update" }
        val docRef = db.collection("questions").document(question.id)
        val normalizedOptions = (question.options + listOf("Option A", "Option B", "Option C", "Option D"))
            .take(4)
            .mapIndexed { idx, opt -> opt.trim().ifBlank { "Option ${'A' + idx}" }.take(1000) }
        val normalizedCorrect = question.correctAnswer.uppercase(Locale.US)
            .takeIf { it in listOf("A", "B", "C", "D") } ?: "A"
        val normalizedConfidence = question.answerConfidence.uppercase(Locale.US)
            .takeIf { it in listOf("VERIFIED", "AI_GENERATED", "NEEDS_REVIEW") } ?: "VERIFIED"
        val normalizedDifficulty = question.difficulty.uppercase(Locale.US)
            .takeIf { it in listOf("EASY", "MEDIUM", "HARD") } ?: "MEDIUM"

        val updates = mapOf(
            "subjectId" to question.subjectId.ifBlank { "default_subject" }.take(128),
            "subjectName" to question.subjectName.ifBlank { "General Studies" }.take(120),
            "chapterId" to question.chapterId.take(128),
            "chapterName" to question.chapterName.take(150),
            "questionNumber" to question.questionNumber.coerceIn(1, 100000),
            "questionText" to question.questionText.trim().ifBlank { "Untitled Question" }.take(3000),
            "options" to normalizedOptions,
            "correctAnswer" to normalizedCorrect,
            "explanation" to question.explanation.trim().take(3000),
            "sourceReference" to question.sourceReference.trim().take(300),
            "sourceImage" to question.sourceImage.trim().take(500),
            "answerConfidence" to normalizedConfidence,
            "difficulty" to normalizedDifficulty,
            "isBookmarked" to question.isBookmarked,
            "collectionTag" to question.collectionTag.trim().ifBlank { "General" }.take(100),
            "lastAttemptCorrect" to question.lastAttemptCorrect,
            "attemptCount" to question.attemptCount.coerceIn(0, 100000),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleQuestionBookmark(questionId: String, isBookmarked: Boolean): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("questions").document(questionId)
        try {
            docRef.update(
                mapOf(
                    "isBookmarked" to isBookmarked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun recordQuestionAttempt(questionId: String, isCorrect: Boolean, currentAttempts: Int): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("questions").document(questionId)
        try {
            docRef.update(
                mapOf(
                    "lastAttemptCorrect" to isCorrect,
                    "attemptCount" to (currentAttempts + 1).coerceAtMost(100000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteQuestion(questionId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("questions").document(questionId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- TESTS & RESULTS ---

    fun observeTests(userId: String): Flow<List<StudyTest>> = flow {
        val path = "tests"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StudyTest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createTest(
        title: String,
        subjectId: String,
        subjectName: String,
        chapterId: String,
        questionIds: List<String>,
        durationSeconds: Int
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("tests").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().ifBlank { "Examination" }.take(200),
            "subjectId" to subjectId.take(128),
            "subjectName" to subjectName.take(120),
            "chapterId" to chapterId.take(128),
            "questionIds" to questionIds.take(200),
            "durationSeconds" to durationSeconds.coerceIn(0, 86400),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeResults(userId: String): Flow<List<StudyResult>> = flow {
        val path = "results"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StudyResult::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.completedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveResult(result: StudyResult): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("results").document()
        val payload = mapOf(
            "userId" to uid,
            "testId" to result.testId.ifBlank { "custom_test" }.take(128),
            "testTitle" to result.testTitle.ifBlank { "Practice Exam" }.take(200),
            "subjectId" to result.subjectId.take(128),
            "subjectName" to result.subjectName.take(120),
            "chapterId" to result.chapterId.take(128),
            "chapterName" to result.chapterName.take(150),
            "questionIds" to result.questionIds.take(200),
            "selectedAnswers" to result.selectedAnswers,
            "questionTimeSeconds" to result.questionTimeSeconds,
            "totalQuestions" to result.totalQuestions.coerceIn(1, 200),
            "correctCount" to result.correctCount.coerceIn(0, 200),
            "incorrectCount" to result.incorrectCount.coerceIn(0, 200),
            "unansweredCount" to result.unansweredCount.coerceIn(0, 200),
            "percentage" to result.percentage.coerceIn(0, 100),
            "timeTakenSeconds" to result.timeTakenSeconds.coerceIn(0, 86400),
            "completedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- PRACTICE SESSIONS ---

    fun observePracticeSessions(userId: String): Flow<List<PracticeSession>> = flow {
        val path = "practice_sessions"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PracticeSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createPracticeSession(
        title: String,
        mode: String,
        subjectId: String,
        chapterId: String,
        questionIds: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("practice_sessions").document()
        val validMode = mode.uppercase(Locale.US)
            .takeIf { it in listOf("ALL", "INCORRECT", "BOOKMARKED", "SUBJECT", "CHAPTER") } ?: "ALL"
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().ifBlank { "Practice Session" }.take(200),
            "mode" to validMode,
            "subjectId" to subjectId.take(128),
            "chapterId" to chapterId.take(128),
            "questionIds" to questionIds.take(200),
            "currentQuestion" to 0,
            "completedQuestions" to 0,
            "score" to 0,
            "wrongQuestionIds" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updatePracticeProgress(
        sessionId: String,
        currentQuestion: Int,
        completedQuestions: Int,
        score: Int,
        wrongQuestionIds: List<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("practice_sessions").document(sessionId)
        try {
            docRef.update(
                mapOf(
                    "currentQuestion" to currentQuestion.coerceIn(0, 200),
                    "completedQuestions" to completedQuestions.coerceIn(0, 200),
                    "score" to score.coerceIn(0, 200),
                    "wrongQuestionIds" to wrongQuestionIds.take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- UPLOADED MATERIALS ---

    fun observeUploadedMaterials(userId: String): Flow<List<UploadedMaterial>> = flow {
        val path = "uploaded_materials"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UploadedMaterial::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordUploadedMaterial(
        fileName: String,
        fileType: String,
        extractedQuestionCount: Int,
        subjectName: String,
        chapterName: String,
        status: String = "SAVED"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("uploaded_materials").document()
        val validType = fileType.uppercase(Locale.US).takeIf { it in listOf("IMAGE", "PDF", "TEXT") } ?: "IMAGE"
        val validStatus = status.uppercase(Locale.US).takeIf { it in listOf("PROCESSED", "SAVED") } ?: "SAVED"
        val payload = mapOf(
            "userId" to uid,
            "fileName" to fileName.trim().ifBlank { "Study Material" }.take(250),
            "fileType" to validType,
            "extractedQuestionCount" to extractedQuestionCount.coerceIn(0, 500),
            "subjectName" to subjectName.take(120),
            "chapterName" to chapterName.take(150),
            "status" to validStatus,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }
}
