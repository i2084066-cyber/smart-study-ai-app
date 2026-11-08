package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val studyStreak: Int = 1,
    val lastStudyDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudySubject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectName: String = "",
    val description: String = "",
    val colorHex: String = "#7C3AED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudyChapter(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val chapterName: String = "",
    val chapterNumber: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudyQuestion(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val chapterId: String = "",
    val chapterName: String = "",
    val questionNumber: Int = 1,
    val questionText: String = "",
    val options: List<String> = listOf("", "", "", ""),
    val correctAnswer: String = "A", // "A", "B", "C", "D"
    val explanation: String = "",
    val sourceReference: String = "",
    val sourceImage: String = "",
    val answerConfidence: String = "VERIFIED", // "VERIFIED", "AI_GENERATED", "NEEDS_REVIEW"
    val difficulty: String = "MEDIUM", // "EASY", "MEDIUM", "HARD"
    val isBookmarked: Boolean = false,
    val collectionTag: String = "General",
    val lastAttemptCorrect: Boolean = true,
    val attemptCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudyTest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val chapterId: String = "",
    val questionIds: List<String> = emptyList(),
    val durationSeconds: Int = 900,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudyResult(
    @DocumentId val id: String = "",
    val userId: String = "",
    val testId: String = "",
    val testTitle: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val chapterId: String = "",
    val chapterName: String = "",
    val questionIds: List<String> = emptyList(),
    val selectedAnswers: Map<String, String> = emptyMap(),
    val questionTimeSeconds: Map<String, Int> = emptyMap(),
    val totalQuestions: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val unansweredCount: Int = 0,
    val percentage: Int = 0,
    val timeTakenSeconds: Int = 0,
    @ServerTimestamp val completedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PracticeSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val mode: String = "ALL", // "ALL", "INCORRECT", "BOOKMARKED", "SUBJECT", "CHAPTER"
    val subjectId: String = "",
    val chapterId: String = "",
    val questionIds: List<String> = emptyList(),
    val currentQuestion: Int = 0,
    val completedQuestions: Int = 0,
    val score: Int = 0,
    val wrongQuestionIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UploadedMaterial(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fileName: String = "",
    val fileType: String = "IMAGE", // "IMAGE", "PDF", "TEXT"
    val extractedQuestionCount: Int = 0,
    val subjectName: String = "",
    val chapterName: String = "",
    val status: String = "SAVED", // "PROCESSED", "SAVED"
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class ExtractedQuestionDraft(
    val draftId: String,
    val questionNumber: Int,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String, // "A", "B", "C", "D"
    val explanation: String,
    val sourceReference: String,
    val answerConfidence: String, // "VERIFIED", "AI_GENERATED", "NEEDS_REVIEW"
    val difficulty: String // "EASY", "MEDIUM", "HARD"
)
