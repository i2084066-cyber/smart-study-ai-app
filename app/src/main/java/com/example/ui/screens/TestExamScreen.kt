package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyChapter
import com.example.data.model.StudyQuestion
import com.example.data.model.StudyResult
import com.example.data.model.StudySubject
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.viewmodel.ActiveExamState
import com.example.ui.viewmodel.ExamQuestionStatus
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun TestExamScreen(
    examState: ActiveExamState?,
    questions: List<StudyQuestion>,
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    onStartExam: (String, String, String, String, String, Int, Int, Boolean) -> Unit,
    onSelectAnswer: (String) -> Unit,
    onToggleMarkForReview: () -> Unit,
    onNavigateQuestion: (Int) -> Unit,
    onTickSecond: () -> Unit,
    onSubmitExam: () -> Unit,
    onViewDetailedResult: (StudyResult) -> Unit,
    onReviewWrongAnswers: (List<StudyQuestion>) -> Unit,
    onRetakeExam: (List<StudyQuestion>, String, Int, Boolean) -> Unit,
    onReturnToDashboard: () -> Unit
) {
    if (examState == null) {
        ExamConfigurationLobby(
            questions = questions,
            subjects = subjects,
            chapters = chapters,
            onStartExam = onStartExam
        )
    } else if (examState.completedResult != null) {
        FinalResultPopupScreen(
            result = examState.completedResult,
            examQuestions = examState.questions,
            onViewDetailedResult = { onViewDetailedResult(examState.completedResult) },
            onReviewWrongAnswers = {
                val wrongPool = examState.questions.filter { q ->
                    val sel = examState.selectedAnswers[q.id]
                    sel == null || !sel.equals(q.correctAnswer, ignoreCase = true)
                }
                onReviewWrongAnswers(wrongPool.ifEmpty { examState.questions })
            },
            onRetakeTest = {
                onRetakeExam(
                    examState.questions,
                    examState.title,
                    (examState.totalDurationSeconds / 60).coerceAtLeast(5),
                    examState.isTimerEnabled
                )
            },
            onReturnToDashboard = onReturnToDashboard
        )
    } else {
        ActiveExaminationView(
            examState = examState,
            onSelectAnswer = onSelectAnswer,
            onToggleMarkForReview = onToggleMarkForReview,
            onNavigateQuestion = onNavigateQuestion,
            onTickSecond = onTickSecond,
            onSubmitExam = onSubmitExam
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamConfigurationLobby(
    questions: List<StudyQuestion>,
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    onStartExam: (String, String, String, String, String, Int, Int, Boolean) -> Unit
) {
    var examTitle by remember { mutableStateOf("Comprehensive Mock Examination") }
    var selectedSubjectId by remember { mutableStateOf("") }
    var selectedSubjectName by remember { mutableStateOf("All Subjects") }
    var selectedChapterId by remember { mutableStateOf("") }
    var selectedChapterName by remember { mutableStateOf("All Chapters") }
    var questionCount by remember { mutableIntStateOf(10) }
    var durationMinutes by remember { mutableIntStateOf(15) }
    var isTimerEnabled by remember { mutableStateOf(true) }

    val availableCount = remember(questions, selectedSubjectId, selectedChapterId) {
        questions.count { q ->
            (selectedSubjectId.isBlank() || q.subjectId == selectedSubjectId) &&
                (selectedChapterId.isBlank() || q.chapterId == selectedChapterId)
        }
    }

    val visibleChapters = remember(chapters, selectedSubjectId) {
        if (selectedSubjectId.isBlank()) emptyList()
        else chapters.filter { it.subjectId == selectedSubjectId }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("exam_lobby_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Examination & Test Simulator",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Strict examination rules: no answer hints during the test, manual navigation, review flags, and detailed post-exam analytics.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = examTitle,
                        onValueChange = { examTitle = it },
                        label = { Text("Examination Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Select Subject", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedSubjectId.isBlank(),
                            onClick = {
                                selectedSubjectId = ""
                                selectedSubjectName = "All Subjects"
                                selectedChapterId = ""
                                selectedChapterName = "All Chapters"
                            },
                            label = { Text("All Subjects (${questions.size})") }
                        )
                        subjects.forEach { subj ->
                            val cnt = questions.count { it.subjectId == subj.id }
                            FilterChip(
                                selected = selectedSubjectId == subj.id,
                                onClick = {
                                    selectedSubjectId = subj.id
                                    selectedSubjectName = subj.subjectName
                                    selectedChapterId = ""
                                    selectedChapterName = "All Chapters"
                                },
                                label = { Text("${subj.subjectName} ($cnt)") }
                            )
                        }
                    }

                    if (visibleChapters.isNotEmpty()) {
                        Text("Select Chapter", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedChapterId.isBlank(),
                                onClick = {
                                    selectedChapterId = ""
                                    selectedChapterName = "All Chapters"
                                },
                                label = { Text("All Chapters") }
                            )
                            visibleChapters.forEach { ch ->
                                FilterChip(
                                    selected = selectedChapterId == ch.id,
                                    onClick = {
                                        selectedChapterId = ch.id
                                        selectedChapterName = ch.chapterName
                                    },
                                    label = { Text(ch.chapterName) }
                                )
                            }
                        }
                    }

                    Text("Number of Questions (Available: $availableCount)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 20, 30, 50).forEach { count ->
                            FilterChip(
                                selected = questionCount == count,
                                onClick = { questionCount = count },
                                label = { Text("$count Qs") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Countdown Timer", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (isTimerEnabled) "$durationMinutes minutes time limit" else "Untimed examination",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isTimerEnabled,
                            onCheckedChange = { isTimerEnabled = it }
                        )
                    }

                    if (isTimerEnabled) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(5, 10, 15, 30, 60).forEach { mins ->
                                FilterChip(
                                    selected = durationMinutes == mins,
                                    onClick = { durationMinutes = mins },
                                    label = { Text("$mins Min") }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onStartExam(
                                examTitle,
                                selectedSubjectId,
                                selectedSubjectName,
                                selectedChapterId,
                                selectedChapterName,
                                questionCount.coerceAtMost(availableCount.coerceAtLeast(1)),
                                durationMinutes,
                                isTimerEnabled
                            )
                        },
                        enabled = availableCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_exam_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Examination (${questionCount.coerceAtMost(availableCount)} Questions)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveExaminationView(
    examState: ActiveExamState,
    onSelectAnswer: (String) -> Unit,
    onToggleMarkForReview: () -> Unit,
    onNavigateQuestion: (Int) -> Unit,
    onTickSecond: () -> Unit,
    onSubmitExam: () -> Unit
) {
    var showPalettePanel by remember { mutableStateOf(true) }
    var showSubmitConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(examState.testId, examState.completedResult) {
        while (examState.completedResult == null) {
            delay(1000L)
            onTickSecond()
        }
    }

    val totalCount = examState.questions.size.coerceAtLeast(1)
    val currentIndex = examState.currentIndex.coerceIn(0, totalCount - 1)
    val currentQuestion = examState.questions[currentIndex]
    val selectedOption = examState.selectedAnswers[currentQuestion.id]
    val isMarkedForReview = examState.markedForReviewIds.contains(currentQuestion.id)

    val answeredCount = examState.selectedAnswers.count { it.value.isNotBlank() }
    val unansweredCount = totalCount - answeredCount
    val markedCount = examState.markedForReviewIds.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("active_exam_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Exam Bar: Timer + Submit Button
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Exam Timer",
                                tint = if (examState.isTimerEnabled && examState.remainingSeconds < 60) CrimsonError else CyanAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val secondsToDisplay = if (examState.isTimerEnabled) examState.remainingSeconds else examState.elapsedSeconds
                            val mins = secondsToDisplay / 60
                            val secs = secondsToDisplay % 60
                            Text(
                                text = String.format(Locale.US, "%02d:%02d", mins, secs),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (examState.isTimerEnabled && examState.remainingSeconds < 60) CrimsonError
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showPalettePanel = !showPalettePanel },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Panel")
                            }
                            Button(
                                onClick = { showSubmitConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("submit_test_button")
                            ) {
                                Text("Submit Test", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Live Counts Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusCounterBadge("Total", totalCount, RoyalPurplePrimary)
                        StatusCounterBadge("Answered", answeredCount, EmeraldSuccess)
                        StatusCounterBadge("Unanswered", unansweredCount, CrimsonError)
                        StatusCounterBadge("Review", markedCount, AmberWarning)
                    }
                }
            }
        }

        // Collapsible Question Navigation Panel
        item {
            AnimatedVisibility(visible = showPalettePanel) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Question Navigation Panel",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            examState.questions.forEachIndexed { idx, q ->
                                val status = when {
                                    examState.markedForReviewIds.contains(q.id) -> ExamQuestionStatus.MARKED_FOR_REVIEW
                                    !examState.selectedAnswers[q.id].isNullOrBlank() -> ExamQuestionStatus.ANSWERED
                                    examState.visitedQuestionIds.contains(q.id) -> ExamQuestionStatus.NOT_ANSWERED
                                    else -> ExamQuestionStatus.NOT_VISITED
                                }
                                val badgeColor = when (status) {
                                    ExamQuestionStatus.ANSWERED -> EmeraldSuccess
                                    ExamQuestionStatus.MARKED_FOR_REVIEW -> AmberWarning
                                    ExamQuestionStatus.NOT_ANSWERED -> CrimsonError
                                    ExamQuestionStatus.NOT_VISITED -> Color(0xFF475569)
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = badgeColor,
                                    border = if (idx == currentIndex) BorderStroke(2.5.dp, Color.White) else null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable { onNavigateQuestion(idx) }
                                        .testTag("exam_nav_q_${idx + 1}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LegendDot("Answered", EmeraldSuccess)
                            LegendDot("Not Answered", CrimsonError)
                            LegendDot("Review", AmberWarning)
                            LegendDot("Not Visited", Color(0xFF475569))
                        }
                    }
                }
            }
        }

        // Question Header + Mark for Review Button
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Question ${currentIndex + 1} of $totalCount",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = onToggleMarkForReview,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isMarkedForReview) AmberWarning else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("mark_for_review_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = if (isMarkedForReview) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMarkedForReview) "Marked for Review" else "Mark for Review",
                                color = if (isMarkedForReview) AmberWarning else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentQuestion.questionText,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        // 4 Selectable Options (STRICT EXAM RULE: Never reveal correct/wrong status before submission)
        val letters = listOf("A", "B", "C", "D")
        itemsIndexed(currentQuestion.options.take(4)) { idx, optionText ->
            val letter = letters.getOrElse(idx) { "A" }
            val isSelected = selectedOption.equals(letter, ignoreCase = true)

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) RoyalPurplePrimary.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isSelected) BorderStroke(2.dp, RoyalPurplePrimary) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAnswer(letter) }
                    .testTag("exam_option_$letter")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) RoyalPurplePrimary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = optionText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Manual Previous & Next Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigateQuestion(currentIndex - 1) },
                    enabled = currentIndex > 0,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("exam_prev_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Previous")
                }

                if (currentIndex < totalCount - 1) {
                    Button(
                        onClick = { onNavigateQuestion(currentIndex + 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("exam_next_button")
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = { showSubmitConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("exam_finish_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit Test")
                    }
                }
            }
        }
    }

    if (showSubmitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            title = { Text("Submit Examination?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Please confirm your examination summary before final submission:")
                    Text("• Total Questions: $totalCount")
                    Text("• Answered: $answeredCount")
                    Text("• Unanswered: $unansweredCount")
                    Text("• Marked for Review: $markedCount")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirmDialog = false
                        onSubmitExam()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    modifier = Modifier.testTag("confirm_submit_exam_button")
                ) {
                    Text("Confirm & Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirmDialog = false }) {
                    Text("Continue Exam")
                }
            }
        )
    }
}

@Composable
private fun FinalResultPopupScreen(
    result: StudyResult,
    examQuestions: List<StudyQuestion>,
    onViewDetailedResult: () -> Unit,
    onReviewWrongAnswers: () -> Unit,
    onRetakeTest: () -> Unit,
    onReturnToDashboard: () -> Unit
) {
    val ratingText = when {
        result.percentage >= 80 -> "EXCELLENT!"
        result.percentage >= 65 -> "GREAT JOB!"
        result.percentage >= 50 -> "GOOD EFFORT!"
        else -> "KEEP PRACTICING!"
    }
    val ratingColor = when {
        result.percentage >= 80 -> EmeraldSuccess
        result.percentage >= 65 -> CyanAccent
        result.percentage >= 50 -> AmberWarning
        else -> CrimsonError
    }

    val animatedSweep by animateFloatAsState(
        targetValue = (result.percentage / 100f) * 360f,
        animationSpec = tween(durationMillis = 1100),
        label = "circularScore"
    )

    val mins = result.timeTakenSeconds / 60
    val secs = result.timeTakenSeconds % 60

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("final_result_popup_screen"),
        contentPadding = PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Decorative Confetti Canvas for good scores
                    if (result.percentage >= 60) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            val colors = listOf(EmeraldSuccess, RoyalPurplePrimary, CyanAccent, AmberWarning)
                            val positions = listOf(
                                Offset(size.width * 0.12f, size.height * 0.18f),
                                Offset(size.width * 0.85f, size.height * 0.15f),
                                Offset(size.width * 0.22f, size.height * 0.42f),
                                Offset(size.width * 0.78f, size.height * 0.48f),
                                Offset(size.width * 0.08f, size.height * 0.72f),
                                Offset(size.width * 0.91f, size.height * 0.68f)
                            )
                            positions.forEachIndexed { idx, pos ->
                                drawCircle(
                                    color = colors[idx % colors.size].copy(alpha = 0.6f),
                                    radius = 12f + (idx % 3) * 5f,
                                    center = pos
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "EXAMINATION COMPLETED",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Animated Circular Score Indicator
                        Box(
                            modifier = Modifier.size(165.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = Color(0xFF334155),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = ratingColor,
                                    startAngle = -90f,
                                    sweepAngle = animatedSweep,
                                    useCenter = false,
                                    style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${result.percentage}%",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ratingColor
                                )
                                Text(
                                    text = "YOUR SCORE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "RESULT: $ratingText",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = ratingColor,
                            textAlign = TextAlign.Center
                        )

                        // Detailed Scorecard Breakdown
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ResultStatLine("TOTAL QUESTIONS:", "${result.totalQuestions}")
                                ResultStatLine("CORRECT ANSWERS:", "${result.correctCount}", EmeraldSuccess)
                                ResultStatLine("WRONG ANSWERS:", "${result.incorrectCount}", CrimsonError)
                                ResultStatLine("UNANSWERED:", "${result.unansweredCount}", AmberWarning)
                                ResultStatLine("OBTAINED MARKS:", "${result.correctCount} / ${result.totalQuestions}")
                                ResultStatLine("TIME TAKEN:", "${mins}m ${secs}s")
                            }
                        }

                        // Action Buttons
                        Button(
                            onClick = onViewDetailedResult,
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("view_detailed_result_button")
                        ) {
                            Text("View Detailed Result", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onReviewWrongAnswers,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("review_wrong_answers_button")
                        ) {
                            Text("Review Wrong Answers in Practice Mode")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRetakeTest,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("retake_test_button")
                            ) {
                                Text("Retake Test")
                            }
                            OutlinedButton(
                                onClick = onReturnToDashboard,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("return_dashboard_button")
                            ) {
                                Text("Dashboard")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCounterBadge(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.22f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultStatLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
