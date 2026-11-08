package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiStudyService
import com.example.data.model.ExtractedQuestionDraft
import com.example.data.model.PracticeSession
import com.example.data.model.StudyChapter
import com.example.data.model.StudyQuestion
import com.example.data.model.StudyResult
import com.example.data.model.StudySubject
import com.example.data.model.StudyTest
import com.example.data.model.UploadedMaterial
import com.example.data.model.UserProfile
import com.example.data.repository.StudyRepository
import com.example.util.DocumentMediaReader
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExamQuestionStatus {
    NOT_VISITED,
    ANSWERED,
    NOT_ANSWERED,
    MARKED_FOR_REVIEW
}

data class ActivePracticeState(
    val sessionId: String = "",
    val title: String = "Interactive Practice",
    val mode: String = "ALL",
    val questions: List<StudyQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptionByQuestionId: Map<String, String> = emptyMap(),
    val autoNavigatedQuestionIds: Set<String> = emptySet(),
    val wrongQuestionIds: Set<String> = emptySet(),
    val score: Int = 0,
    val isFinished: Boolean = false
)

data class ActiveExamState(
    val testId: String = "",
    val title: String = "Mock Examination",
    val subjectId: String = "",
    val subjectName: String = "All Subjects",
    val chapterId: String = "",
    val chapterName: String = "All Chapters",
    val questions: List<StudyQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val selectedAnswers: Map<String, String> = emptyMap(),
    val visitedQuestionIds: Set<String> = emptySet(),
    val markedForReviewIds: Set<String> = emptySet(),
    val questionTimeSeconds: Map<String, Int> = emptyMap(),
    val totalDurationSeconds: Int = 600,
    val remainingSeconds: Int = 600,
    val elapsedSeconds: Int = 0,
    val isTimerEnabled: Boolean = true,
    val isSubmitting: Boolean = false,
    val completedResult: StudyResult? = null
)

data class AiExtractionUiState(
    val isAnalyzing: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val detectedSubject: String = "Science",
    val detectedChapter: String = "Physics",
    val uploadedFileNames: List<String> = emptyList(),
    val uploadedFileType: String = "IMAGE",
    val drafts: List<ExtractedQuestionDraft> = emptyList(),
    val savedCountMessage: String? = null
)

class SmartStudyViewModel(
    private val repository: StudyRepository,
    private val aiService: GeminiStudyService,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String
) : ViewModel() {

    private fun <T> Flow<T>.safeStateIn(initial: T): StateFlow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w("SmartStudyVM", "Firestore stream error: ${ (firestoreError ?: error).message }")
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = initial
        )

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId).safeStateIn(null)
    val subjects: StateFlow<List<StudySubject>> = repository.observeSubjects(currentUserId).safeStateIn(emptyList())
    val chapters: StateFlow<List<StudyChapter>> = repository.observeChapters(currentUserId).safeStateIn(emptyList())
    val questions: StateFlow<List<StudyQuestion>> = repository.observeQuestions(currentUserId).safeStateIn(emptyList())
    val tests: StateFlow<List<StudyTest>> = repository.observeTests(currentUserId).safeStateIn(emptyList())
    val results: StateFlow<List<StudyResult>> = repository.observeResults(currentUserId).safeStateIn(emptyList())
    val practiceSessions: StateFlow<List<PracticeSession>> = repository.observePracticeSessions(currentUserId).safeStateIn(emptyList())
    val uploadedMaterials: StateFlow<List<UploadedMaterial>> = repository.observeUploadedMaterials(currentUserId).safeStateIn(emptyList())

    private val _aiState = MutableStateFlow(AiExtractionUiState())
    val aiState: StateFlow<AiExtractionUiState> = _aiState.asStateFlow()

    private val _practiceState = MutableStateFlow<ActivePracticeState?>(null)
    val practiceState: StateFlow<ActivePracticeState?> = _practiceState.asStateFlow()

    private val _examState = MutableStateFlow<ActiveExamState?>(null)
    val examState: StateFlow<ActiveExamState?> = _examState.asStateFlow()

    private val _selectedResultForDetail = MutableStateFlow<StudyResult?>(null)
    val selectedResultForDetail: StateFlow<StudyResult?> = _selectedResultForDetail.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var practiceAutoNextJob: Job? = null
    private var hasCheckedInitialSeed = false

    init {
        viewModelScope.launch {
            repository.ensureOrUpdateUserProfile(userDisplayName, userEmail)
        }
        viewModelScope.launch {
            delay(1800L)
            if (!hasCheckedInitialSeed && questions.value.isEmpty() && subjects.value.isEmpty()) {
                hasCheckedInitialSeed = true
                seedStarterCurriculum()
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    // --- SUBJECT & CHAPTER MANAGEMENT ---

    fun createSubject(name: String, description: String, colorHex: String = "#7C3AED") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createSubject(name, description, colorHex)
                .onSuccess { showBanner("Subject '$name' created") }
                .onFailure { showBanner("Failed to create subject: ${it.localizedMessage}") }
        }
    }

    fun deleteSubject(subjectId: String) {
        viewModelScope.launch {
            repository.deleteSubject(subjectId)
        }
    }

    fun createChapter(subjectId: String, chapterName: String, chapterNumber: Int) {
        if (subjectId.isBlank() || chapterName.isBlank()) return
        viewModelScope.launch {
            repository.createChapter(subjectId, chapterName, chapterNumber)
                .onSuccess { showBanner("Chapter '$chapterName' added") }
                .onFailure { showBanner("Failed to create chapter: ${it.localizedMessage}") }
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch {
            repository.deleteChapter(chapterId)
        }
    }

    // --- QUESTION BANK MANAGEMENT ---

    fun addManualQuestion(question: StudyQuestion) {
        viewModelScope.launch {
            repository.addQuestion(question)
                .onSuccess { showBanner("Question saved to Question Bank") }
                .onFailure { showBanner("Failed to save question: ${it.localizedMessage}") }
        }
    }

    fun updateExistingQuestion(question: StudyQuestion) {
        viewModelScope.launch {
            repository.updateQuestion(question)
                .onSuccess { showBanner("Question updated") }
                .onFailure { showBanner("Failed to update question: ${it.localizedMessage}") }
        }
    }

    fun deleteQuestion(questionId: String) {
        viewModelScope.launch {
            repository.deleteQuestion(questionId)
                .onSuccess { showBanner("Question deleted") }
        }
    }

    fun toggleBookmark(question: StudyQuestion) {
        viewModelScope.launch {
            repository.toggleQuestionBookmark(question.id, !question.isBookmarked)
        }
    }

    // --- AI MULTIMODAL UPLOAD & EXTRACTION ---

    fun analyzeUploadedUris(context: Context, uris: List<Uri>, customHint: String = "") {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _aiState.update {
                it.copy(
                    isAnalyzing = true,
                    statusMessage = "Reading ${uris.size} file(s) and rendering pages...",
                    errorMessage = null,
                    savedCountMessage = null
                )
            }

            val allBitmaps = mutableListOf<Bitmap>()
            val fileNames = mutableListOf<String>()
            var hasPdf = false

            for (uri in uris) {
                val loadedResult = DocumentMediaReader.loadUriAsBitmaps(context, uri)
                loadedResult.onSuccess { doc ->
                    fileNames.add(doc.displayName)
                    if (doc.isPdf) hasPdf = true
                    allBitmaps.addAll(doc.bitmaps)
                }.onFailure { err ->
                    _aiState.update {
                        it.copy(
                            isAnalyzing = false,
                            errorMessage = err.localizedMessage ?: "Failed to read uploaded file."
                        )
                    }
                    return@launch
                }
            }

            val combinedLabel = fileNames.joinToString(", ").take(180)
            _aiState.update {
                it.copy(
                    statusMessage = "Analyzing ${allBitmaps.size} page(s)/image(s) with Gemini AI...",
                    uploadedFileNames = fileNames,
                    uploadedFileType = if (hasPdf) "PDF" else "IMAGE"
                )
            }

            val extractionResult = aiService.extractQuestionsFromMaterials(
                bitmaps = allBitmaps,
                sourceLabel = combinedLabel,
                customInstructions = customHint
            )

            extractionResult.onSuccess { response ->
                if (response.drafts.isEmpty()) {
                    _aiState.update {
                        it.copy(
                            isAnalyzing = false,
                            statusMessage = "",
                            errorMessage = "No questions could be detected. Please ensure the image or PDF is clear and legible."
                        )
                    }
                } else {
                    _aiState.update {
                        it.copy(
                            isAnalyzing = false,
                            statusMessage = "Extracted ${response.drafts.size} question(s). Review & edit below before saving.",
                            detectedSubject = response.detectedSubject,
                            detectedChapter = response.detectedChapter,
                            drafts = response.drafts,
                            errorMessage = null
                        )
                    }
                }
            }.onFailure { err ->
                _aiState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusMessage = "",
                        errorMessage = err.localizedMessage ?: "Gemini extraction failed."
                    )
                }
            }
        }
    }

    fun generateAiQuestions(
        topicOrNotes: String,
        count: Int,
        difficulty: String,
        subjectName: String,
        chapterName: String
    ) {
        if (topicOrNotes.isBlank()) return
        viewModelScope.launch {
            _aiState.update {
                it.copy(
                    isAnalyzing = true,
                    statusMessage = "Generating $count $difficulty MCQs with Gemini AI...",
                    errorMessage = null,
                    savedCountMessage = null,
                    uploadedFileNames = listOf("AI Topic: ${topicOrNotes.take(40)}"),
                    uploadedFileType = "TEXT"
                )
            }

            val result = aiService.generateQuestionsFromTopicOrNotes(
                topicOrNotes = topicOrNotes,
                questionCount = count,
                difficulty = difficulty,
                subjectHint = subjectName,
                chapterHint = chapterName
            )

            result.onSuccess { response ->
                _aiState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusMessage = "Generated ${response.drafts.size} MCQs. Review & edit before saving.",
                        detectedSubject = subjectName.ifBlank { response.detectedSubject },
                        detectedChapter = chapterName.ifBlank { response.detectedChapter },
                        drafts = response.drafts,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _aiState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusMessage = "",
                        errorMessage = err.localizedMessage ?: "AI question generation failed."
                    )
                }
            }
        }
    }

    fun updateExtractedDraft(updated: ExtractedQuestionDraft) {
        _aiState.update { state ->
            state.copy(
                drafts = state.drafts.map { if (it.draftId == updated.draftId) updated else it }
            )
        }
    }

    fun removeExtractedDraft(draftId: String) {
        _aiState.update { state ->
            state.copy(drafts = state.drafts.filterNot { it.draftId == draftId })
        }
    }

    fun clearExtractedDrafts() {
        _aiState.update { AiExtractionUiState() }
    }

    fun saveApprovedDraftsToDatabase(
        subjectNameInput: String,
        chapterNameInput: String,
        collectionTag: String = "AI Extracted"
    ) {
        val currentDrafts = _aiState.value.drafts
        if (currentDrafts.isEmpty()) return

        viewModelScope.launch {
            val targetSubjectName = subjectNameInput.trim().ifBlank { _aiState.value.detectedSubject.ifBlank { "Science" } }
            val targetChapterName = chapterNameInput.trim().ifBlank { _aiState.value.detectedChapter.ifBlank { "General" } }

            // Find or create Subject
            val existingSubject = subjects.value.firstOrNull {
                it.subjectName.equals(targetSubjectName, ignoreCase = true)
            }
            val subjectId = existingSubject?.id ?: repository.createSubject(
                subjectName = targetSubjectName,
                description = "Created via AI Smart Study",
                colorHex = "#7C3AED"
            ).getOrDefault("default_subject")

            // Find or create Chapter
            val existingChapter = chapters.value.firstOrNull {
                it.subjectId == subjectId && it.chapterName.equals(targetChapterName, ignoreCase = true)
            }
            val nextChapterNum = (chapters.value.count { it.subjectId == subjectId } + 1).coerceAtLeast(1)
            val chapterId = existingChapter?.id ?: repository.createChapter(
                subjectId = subjectId,
                chapterName = targetChapterName,
                chapterNumber = nextChapterNum
            ).getOrDefault("default_chapter")

            var savedCount = 0
            val baseNumber = questions.value.size
            for ((idx, draft) in currentDrafts.withIndex()) {
                val q = StudyQuestion(
                    userId = currentUserId,
                    subjectId = subjectId,
                    subjectName = targetSubjectName,
                    chapterId = chapterId,
                    chapterName = targetChapterName,
                    questionNumber = baseNumber + idx + 1,
                    questionText = draft.questionText,
                    options = listOf(draft.optionA, draft.optionB, draft.optionC, draft.optionD),
                    correctAnswer = draft.correctAnswer,
                    explanation = draft.explanation,
                    sourceReference = draft.sourceReference,
                    sourceImage = _aiState.value.uploadedFileNames.firstOrNull().orEmpty(),
                    answerConfidence = draft.answerConfidence,
                    difficulty = draft.difficulty,
                    isBookmarked = false,
                    collectionTag = collectionTag
                )
                if (repository.addQuestion(q).isSuccess) {
                    savedCount++
                }
            }

            val fileNameRecord = _aiState.value.uploadedFileNames.joinToString(", ").ifBlank {
                "$targetSubjectName - $targetChapterName"
            }
            repository.recordUploadedMaterial(
                fileName = fileNameRecord,
                fileType = _aiState.value.uploadedFileType,
                extractedQuestionCount = savedCount,
                subjectName = targetSubjectName,
                chapterName = targetChapterName,
                status = "SAVED"
            )

            _aiState.update {
                it.copy(
                    drafts = emptyList(),
                    statusMessage = "",
                    savedCountMessage = "Successfully saved $savedCount question(s) to $targetSubjectName • $targetChapterName!"
                )
            }
            showBanner("Saved $savedCount question(s) to Question Bank!")
        }
    }

    // --- PRACTICE MODE ---

    fun startPractice(
        mode: String = "ALL",
        subjectId: String = "",
        chapterId: String = "",
        customQuestions: List<StudyQuestion>? = null
    ) {
        practiceAutoNextJob?.cancel()
        val all = questions.value
        val filtered = customQuestions ?: when (mode) {
            "INCORRECT" -> all.filter { !it.lastAttemptCorrect && it.attemptCount > 0 }
            "BOOKMARKED" -> all.filter { it.isBookmarked }
            "SUBJECT" -> all.filter { it.subjectId == subjectId }
            "CHAPTER" -> all.filter { it.chapterId == chapterId }
            else -> all
        }

        if (filtered.isEmpty()) {
            showBanner("No questions match this filter yet.")
            return
        }

        val sessionTitle = when (mode) {
            "INCORRECT" -> "Retry Incorrect Questions"
            "BOOKMARKED" -> "Bookmarked Questions"
            "SUBJECT" -> "Subject Practice"
            "CHAPTER" -> "Chapter Practice"
            else -> "Complete Question Bank Practice"
        }

        _practiceState.value = ActivePracticeState(
            title = sessionTitle,
            mode = mode,
            questions = filtered,
            currentIndex = 0
        )

        viewModelScope.launch {
            repository.createPracticeSession(
                title = sessionTitle,
                mode = mode,
                subjectId = subjectId,
                chapterId = chapterId,
                questionIds = filtered.map { it.id }
            ).onSuccess { sessionId ->
                _practiceState.update { it?.copy(sessionId = sessionId) }
            }
        }
    }

    fun selectPracticeOption(optionLetter: String) {
        val current = _practiceState.value ?: return
        val question = current.questions.getOrNull(current.currentIndex) ?: return

        // Prevent accidental double submissions on the same question
        if (current.selectedOptionByQuestionId.containsKey(question.id)) return

        val isCorrect = optionLetter.equals(question.correctAnswer, ignoreCase = true)
        val updatedSelections = current.selectedOptionByQuestionId + (question.id to optionLetter)
        val updatedWrong = if (isCorrect) current.wrongQuestionIds else current.wrongQuestionIds + question.id
        val updatedScore = if (isCorrect) current.score + 1 else current.score
        val shouldAutoAdvance = !current.autoNavigatedQuestionIds.contains(question.id)

        _practiceState.value = current.copy(
            selectedOptionByQuestionId = updatedSelections,
            autoNavigatedQuestionIds = current.autoNavigatedQuestionIds + question.id,
            wrongQuestionIds = updatedWrong,
            score = updatedScore
        )

        viewModelScope.launch {
            repository.recordQuestionAttempt(question.id, isCorrect, question.attemptCount)
            val sId = _practiceState.value?.sessionId.orEmpty()
            if (sId.isNotBlank()) {
                repository.updatePracticeProgress(
                    sessionId = sId,
                    currentQuestion = current.currentIndex,
                    completedQuestions = updatedSelections.size,
                    score = updatedScore,
                    wrongQuestionIds = updatedWrong.toList()
                )
            }
        }

        if (shouldAutoAdvance) {
            practiceAutoNextJob?.cancel()
            val delayMs = if (isCorrect) 1500L else 2500L
            practiceAutoNextJob = viewModelScope.launch {
                delay(delayMs)
                val latest = _practiceState.value ?: return@launch
                if (latest.questions.getOrNull(latest.currentIndex)?.id == question.id) {
                    if (latest.currentIndex < latest.questions.lastIndex) {
                        _practiceState.value = latest.copy(currentIndex = latest.currentIndex + 1)
                    } else {
                        _practiceState.value = latest.copy(isFinished = true)
                    }
                }
            }
        }
    }

    fun jumpToPracticeQuestion(index: Int) {
        practiceAutoNextJob?.cancel()
        _practiceState.update { state ->
            state ?: return@update null
            if (index in state.questions.indices) {
                state.copy(currentIndex = index, isFinished = false)
            } else state
        }
    }

    fun restartCurrentPractice(onlyWrongFromSession: Boolean = false) {
        val current = _practiceState.value ?: return
        val pool = if (onlyWrongFromSession) {
            current.questions.filter { current.wrongQuestionIds.contains(it.id) }
        } else {
            current.questions
        }
        if (pool.isEmpty()) {
            showBanner("No wrong answers to retry!")
            return
        }
        startPractice(
            mode = if (onlyWrongFromSession) "INCORRECT" else current.mode,
            customQuestions = pool
        )
    }

    fun exitPractice() {
        practiceAutoNextJob?.cancel()
        _practiceState.value = null
    }

    // --- TEST / EXAM MODE ---

    fun startExam(
        title: String,
        subjectId: String,
        subjectName: String,
        chapterId: String,
        chapterName: String,
        questionCount: Int,
        durationMinutes: Int,
        isTimerEnabled: Boolean,
        customPool: List<StudyQuestion>? = null
    ) {
        val all = questions.value
        val filtered = customPool ?: all.filter { q ->
            (subjectId.isBlank() || q.subjectId == subjectId) &&
                (chapterId.isBlank() || q.chapterId == chapterId)
        }.shuffled().take(questionCount.coerceAtLeast(1))

        if (filtered.isEmpty()) {
            showBanner("No questions available for the selected subject/chapter.")
            return
        }

        val firstId = filtered.first().id
        val durationSec = if (isTimerEnabled) (durationMinutes * 60).coerceAtLeast(60) else 0

        _examState.value = ActiveExamState(
            title = title.ifBlank { "$subjectName Mock Test" },
            subjectId = subjectId,
            subjectName = subjectName.ifBlank { "All Subjects" },
            chapterId = chapterId,
            chapterName = chapterName.ifBlank { "All Chapters" },
            questions = filtered,
            currentIndex = 0,
            visitedQuestionIds = setOf(firstId),
            totalDurationSeconds = durationSec,
            remainingSeconds = durationSec,
            isTimerEnabled = isTimerEnabled
        )

        viewModelScope.launch {
            repository.createTest(
                title = title.ifBlank { "$subjectName Mock Test" },
                subjectId = subjectId,
                subjectName = subjectName.ifBlank { "All Subjects" },
                chapterId = chapterId,
                questionIds = filtered.map { it.id },
                durationSeconds = durationSec
            ).onSuccess { testId ->
                _examState.update { it?.copy(testId = testId) }
            }
        }
    }

    fun selectExamAnswer(optionLetter: String) {
        // IMPORTANT TEST RULE: Never reveal correct answer and never auto-advance in Exam Mode
        _examState.update { state ->
            state ?: return@update null
            if (state.completedResult != null) return@update state
            val q = state.questions.getOrNull(state.currentIndex) ?: return@update state
            state.copy(
                selectedAnswers = state.selectedAnswers + (q.id to optionLetter),
                visitedQuestionIds = state.visitedQuestionIds + q.id
            )
        }
    }

    fun toggleExamMarkForReview() {
        _examState.update { state ->
            state ?: return@update null
            val q = state.questions.getOrNull(state.currentIndex) ?: return@update state
            val nextMarked = if (state.markedForReviewIds.contains(q.id)) {
                state.markedForReviewIds - q.id
            } else {
                state.markedForReviewIds + q.id
            }
            state.copy(markedForReviewIds = nextMarked)
        }
    }

    fun navigateExamQuestion(targetIndex: Int) {
        _examState.update { state ->
            state ?: return@update null
            if (targetIndex !in state.questions.indices) return@update state
            val targetQ = state.questions[targetIndex]
            state.copy(
                currentIndex = targetIndex,
                visitedQuestionIds = state.visitedQuestionIds + targetQ.id
            )
        }
    }

    fun tickExamSecond() {
        val current = _examState.value ?: return
        if (current.completedResult != null || current.isSubmitting) return

        val currentQ = current.questions.getOrNull(current.currentIndex)
        val updatedQuestionTimes = if (currentQ != null) {
            val prev = current.questionTimeSeconds[currentQ.id] ?: 0
            current.questionTimeSeconds + (currentQ.id to (prev + 1))
        } else current.questionTimeSeconds

        val nextElapsed = current.elapsedSeconds + 1
        val nextRemaining = if (current.isTimerEnabled) {
            (current.remainingSeconds - 1).coerceAtLeast(0)
        } else 0

        _examState.value = current.copy(
            questionTimeSeconds = updatedQuestionTimes,
            elapsedSeconds = nextElapsed,
            remainingSeconds = nextRemaining
        )

        if (current.isTimerEnabled && nextRemaining == 0) {
            submitExam()
        }
    }

    fun submitExam() {
        val current = _examState.value ?: return
        if (current.isSubmitting || current.completedResult != null) return

        _examState.update { it?.copy(isSubmitting = true) }

        var correct = 0
        var incorrect = 0
        var unanswered = 0

        for (q in current.questions) {
            val selected = current.selectedAnswers[q.id]
            if (selected.isNullOrBlank()) {
                unanswered++
            } else if (selected.equals(q.correctAnswer, ignoreCase = true)) {
                correct++
            } else {
                incorrect++
            }
        }

        val total = current.questions.size.coerceAtLeast(1)
        val percentage = ((correct * 100.0) / total).toInt().coerceIn(0, 100)

        val resultObj = StudyResult(
            userId = currentUserId,
            testId = current.testId.ifBlank { "exam_${System.currentTimeMillis()}" },
            testTitle = current.title,
            subjectId = current.subjectId,
            subjectName = current.subjectName,
            chapterId = current.chapterId,
            chapterName = current.chapterName,
            questionIds = current.questions.map { it.id },
            selectedAnswers = current.selectedAnswers,
            questionTimeSeconds = current.questionTimeSeconds,
            totalQuestions = total,
            correctCount = correct,
            incorrectCount = incorrect,
            unansweredCount = unanswered,
            percentage = percentage,
            timeTakenSeconds = current.elapsedSeconds
        )

        viewModelScope.launch {
            // Update per-question attempt stats
            for (q in current.questions) {
                val sel = current.selectedAnswers[q.id]
                if (!sel.isNullOrBlank()) {
                    val isRight = sel.equals(q.correctAnswer, ignoreCase = true)
                    repository.recordQuestionAttempt(q.id, isRight, q.attemptCount)
                }
            }
            val savedId = repository.saveResult(resultObj).getOrDefault("local_result")
            val finalResult = resultObj.copy(id = savedId)
            _examState.update {
                it?.copy(isSubmitting = false, completedResult = finalResult)
            }
            _selectedResultForDetail.value = finalResult
        }
    }

    fun selectResultForAnalysis(result: StudyResult?) {
        _selectedResultForDetail.value = result
    }

    fun closeExamSession() {
        _examState.value = null
    }

    // --- STARTER SEED DATA ---

    fun seedStarterCurriculum() {
        viewModelScope.launch {
            val sciId = repository.createSubject("Science", "Physics, Chemistry, and Biology fundamentals", "#7C3AED")
                .getOrDefault("subj_science")
            val csId = repository.createSubject("Computer Science", "Data Structures, Algorithms & AI", "#06B6D4")
                .getOrDefault("subj_cs")

            val phyId = repository.createChapter(sciId, "Chapter 1: Physics", 1).getOrDefault("ch_phy")
            val chemId = repository.createChapter(sciId, "Chapter 2: Chemistry", 2).getOrDefault("ch_chem")
            val bioId = repository.createChapter(sciId, "Chapter 3: Biology", 3).getOrDefault("ch_bio")
            val aiId = repository.createChapter(csId, "Chapter 1: Artificial Intelligence", 1).getOrDefault("ch_ai")

            val starterQuestions = listOf(
                StudyQuestion(
                    subjectId = sciId, subjectName = "Science", chapterId = phyId, chapterName = "Chapter 1: Physics",
                    questionNumber = 1,
                    questionText = "Which law of motion states that for every action there is an equal and opposite reaction?",
                    options = listOf("Newton's First Law", "Newton's Second Law", "Newton's Third Law", "Law of Universal Gravitation"),
                    correctAnswer = "C",
                    explanation = "Newton's Third Law states that when two bodies interact, they apply forces to one another that are equal in magnitude and opposite in direction.",
                    sourceReference = "Classical Mechanics • Ch. 1",
                    answerConfidence = "VERIFIED", difficulty = "EASY", collectionTag = "Core Physics"
                ),
                StudyQuestion(
                    subjectId = sciId, subjectName = "Science", chapterId = phyId, chapterName = "Chapter 1: Physics",
                    questionNumber = 2,
                    questionText = "What happens to the electrical resistance of a metallic conductor when its temperature increases?",
                    options = listOf("It decreases exponentially", "It increases linearly", "It remains completely unchanged", "It drops to zero"),
                    correctAnswer = "B",
                    explanation = "In metallic conductors, increasing temperature increases thermal agitation of lattice ions, causing more frequent electron collisions and higher resistance.",
                    sourceReference = "Electrodynamics • Ch. 1",
                    answerConfidence = "VERIFIED", difficulty = "MEDIUM", collectionTag = "Core Physics"
                ),
                StudyQuestion(
                    subjectId = sciId, subjectName = "Science", chapterId = chemId, chapterName = "Chapter 2: Chemistry",
                    questionNumber = 3,
                    questionText = "What is the hybridization of carbon atoms in a benzene (C6H6) ring?",
                    options = listOf("sp3", "sp2", "sp", "dsp2"),
                    correctAnswer = "B",
                    explanation = "Each carbon atom in benzene forms three sigma bonds in a trigonal planar geometry (120° bond angles) and has one unhybridized p-orbital, corresponding to sp2 hybridization.",
                    sourceReference = "Organic Chemistry • Ch. 2",
                    answerConfidence = "VERIFIED", difficulty = "MEDIUM", collectionTag = "Organic"
                ),
                StudyQuestion(
                    subjectId = sciId, subjectName = "Science", chapterId = bioId, chapterName = "Chapter 3: Biology",
                    questionNumber = 4,
                    questionText = "Which cellular organelle is responsible for synthesizing ATP through oxidative phosphorylation?",
                    options = listOf("Golgi Apparatus", "Smooth Endoplasmic Reticulum", "Mitochondrion", "Lysosome"),
                    correctAnswer = "C",
                    explanation = "Mitochondria generate most of the cell's supply of adenosine triphosphate (ATP) across their inner membrane via the electron transport chain.",
                    sourceReference = "Cell Biology • Ch. 3",
                    answerConfidence = "VERIFIED", difficulty = "EASY", collectionTag = "Cell Bio"
                ),
                StudyQuestion(
                    subjectId = csId, subjectName = "Computer Science", chapterId = aiId, chapterName = "Chapter 1: Artificial Intelligence",
                    questionNumber = 5,
                    questionText = "In Transformer neural networks, what is the primary purpose of the scaled dot-product attention mechanism?",
                    options = listOf(
                        "To reduce image resolution using max pooling",
                        "To dynamically weigh the relevance of all tokens in a sequence relative to each other",
                        "To enforce sequential recurrent hidden states like an LSTM",
                        "To convert floating-point weights into binary trees"
                    ),
                    correctAnswer = "B",
                    explanation = "Self-attention computes query, key, and value projections to model dependencies between any pair of tokens regardless of their distance in the sequence.",
                    sourceReference = "Deep Learning Notes",
                    answerConfidence = "VERIFIED", difficulty = "HARD", collectionTag = "AI & ML"
                ),
                StudyQuestion(
                    subjectId = csId, subjectName = "Computer Science", chapterId = aiId, chapterName = "Chapter 1: Artificial Intelligence",
                    questionNumber = 6,
                    questionText = "What is the worst-case time complexity of searching for an element in a balanced Binary Search Tree (AVL Tree) with n nodes?",
                    options = listOf("O(1)", "O(log n)", "O(n)", "O(n log n)"),
                    correctAnswer = "B",
                    explanation = "Because an AVL tree maintains a height balance factor of at most 1 at every node, its height is strictly bounded by O(log n).",
                    sourceReference = "Algorithms & Data Structures",
                    answerConfidence = "VERIFIED", difficulty = "MEDIUM", collectionTag = "Algorithms"
                )
            )

            for (q in starterQuestions) {
                repository.addQuestion(q)
            }
        }
    }
}
