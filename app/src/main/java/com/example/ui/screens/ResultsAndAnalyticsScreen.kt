package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PracticeSession
import com.example.data.model.StudyChapter
import com.example.data.model.StudyQuestion
import com.example.data.model.StudyResult
import com.example.data.model.StudySubject
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonErrorContainer
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.example.ui.theme.RoyalPurplePrimary

@Composable
fun ResultsAndAnalyticsScreen(
    selectedResult: StudyResult?,
    results: List<StudyResult>,
    questions: List<StudyQuestion>,
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    practiceSessions: List<PracticeSession>,
    onSelectResult: (StudyResult?) -> Unit,
    onRetryWrongQuestions: (List<StudyQuestion>) -> Unit
) {
    if (selectedResult != null) {
        DetailedResultAnalysisView(
            result = selectedResult,
            allQuestions = questions,
            onBackToAnalytics = { onSelectResult(null) },
            onRetryWrongQuestions = onRetryWrongQuestions
        )
    } else {
        PerformanceAnalyticsOverview(
            results = results,
            questions = questions,
            subjects = subjects,
            chapters = chapters,
            practiceSessions = practiceSessions,
            onSelectResult = { onSelectResult(it) }
        )
    }
}

@Composable
private fun PerformanceAnalyticsOverview(
    results: List<StudyResult>,
    questions: List<StudyQuestion>,
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    practiceSessions: List<PracticeSession>,
    onSelectResult: (StudyResult) -> Unit
) {
    val attemptedQuestions = questions.filter { it.attemptCount > 0 }
    val totalAttempted = attemptedQuestions.size
    val correctQuestionsCount = attemptedQuestions.count { it.lastAttemptCorrect }
    val incorrectQuestionsCount = totalAttempted - correctQuestionsCount
    val correctPercentage = if (totalAttempted > 0) ((correctQuestionsCount * 100.0) / totalAttempted).toInt() else 0
    val incorrectPercentage = if (totalAttempted > 0) 100 - correctPercentage else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("analytics_overview_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Performance Analytics & Test History",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Track your accuracy, subject-wise mastery, chapter performance, and full examination history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Top Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnalyticsMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "Attempted Qs",
                    value = "$totalAttempted / ${questions.size}",
                    color = RoyalPurplePrimary
                )
                AnalyticsMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "Correct %",
                    value = "$correctPercentage%",
                    color = EmeraldSuccess
                )
                AnalyticsMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "Incorrect %",
                    value = "$incorrectPercentage%",
                    color = CrimsonError
                )
            }
        }

        // Subject-Wise Performance Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Subject-Wise Performance",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (subjects.isEmpty()) {
                        Text(
                            text = "No subjects created yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        subjects.forEach { subj ->
                            val subjQs = questions.filter { it.subjectId == subj.id }
                            val subjAttempted = subjQs.filter { it.attemptCount > 0 }
                            val subjAccuracy = if (subjAttempted.isNotEmpty()) {
                                ((subjAttempted.count { it.lastAttemptCorrect } * 100.0) / subjAttempted.size).toInt()
                            } else 0

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${subj.subjectName} (${subjAttempted.size}/${subjQs.size} practiced)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "$subjAccuracy%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (subjAccuracy >= 75) EmeraldSuccess else RoyalPurplePrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (subjAccuracy / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    color = if (subjAccuracy >= 75) EmeraldSuccess else RoyalPurplePrimary,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chapter-Wise Performance Card
        if (chapters.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Chapter-Wise Mastery",
                            style = MaterialTheme.typography.titleLarge
                        )
                        chapters.forEach { ch ->
                            val chQs = questions.filter { it.chapterId == ch.id }
                            val chAttempted = chQs.filter { it.attemptCount > 0 }
                            val chAcc = if (chAttempted.isNotEmpty()) {
                                ((chAttempted.count { it.lastAttemptCorrect } * 100.0) / chAttempted.size).toInt()
                            } else 0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ch.chapterName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${chAttempted.size}/${chQs.size} Qs • $chAcc%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyanAccent
                                )
                            }
                        }
                    }
                }
            }
        }

        // Test History Section
        item {
            Text(
                text = "Examination History (Tap for Detailed Analysis)",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (results.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No examinations completed yet. Complete a test in Exam Mode to see question-by-question analysis here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(results, key = { it.id.ifBlank { it.testId } }) { res ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectResult(res) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = res.testTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Correct: ${res.correctCount} • Wrong: ${res.incorrectCount} • Unanswered: ${res.unansweredCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Time Taken: ${res.timeTakenSeconds / 60}m ${res.timeTakenSeconds % 60}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanAccent
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (res.percentage >= 70) EmeraldSuccess.copy(alpha = 0.2f)
                            else CrimsonError.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${res.percentage}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (res.percentage >= 70) EmeraldSuccess else CrimsonError,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailedResultAnalysisView(
    result: StudyResult,
    allQuestions: List<StudyQuestion>,
    onBackToAnalytics: () -> Unit,
    onRetryWrongQuestions: (List<StudyQuestion>) -> Unit
) {
    val questionMap = allQuestions.associateBy { it.id }
    val orderedQuestions = result.questionIds.mapNotNull { questionMap[it] }
        .ifEmpty { allQuestions.take(result.totalQuestions) }

    val wrongQuestions = orderedQuestions.filter { q ->
        val sel = result.selectedAnswers[q.id]
        sel.isNullOrBlank() || !sel.equals(q.correctAnswer, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("detailed_result_analysis_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBackToAnalytics,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("All Results")
                }

                if (wrongQuestions.isNotEmpty()) {
                    Button(
                        onClick = { onRetryWrongQuestions(wrongQuestions) },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("retry_incorrect_from_analysis_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry ${wrongQuestions.size} Wrong Qs")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = result.testTitle,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Score: ${result.percentage}% • Correct: ${result.correctCount} • Wrong: ${result.incorrectCount} • Unanswered: ${result.unansweredCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (result.percentage >= 70) EmeraldSuccess else AmberWarning
                    )
                    Text(
                        text = "Total Time: ${result.timeTakenSeconds / 60}m ${result.timeTakenSeconds % 60}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        itemsIndexed(orderedQuestions, key = { _, q -> q.id }) { idx, question ->
            val userSelected = result.selectedAnswers[question.id].orEmpty()
            val isUnanswered = userSelected.isBlank()
            val isCorrect = !isUnanswered && userSelected.equals(question.correctAnswer, ignoreCase = true)
            val timeSpentSec = result.questionTimeSeconds[question.id] ?: 0

            val statusColor = when {
                isCorrect -> EmeraldSuccess
                isUnanswered -> AmberWarning
                else -> CrimsonError
            }
            val statusText = when {
                isCorrect -> "CORRECT"
                isUnanswered -> "UNANSWERED"
                else -> "INCORRECT"
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isCorrect -> Icons.Default.CheckCircle
                                            isUnanswered -> Icons.AutoMirrored.Filled.HelpOutline
                                            else -> Icons.Default.Cancel
                                        },
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Q${idx + 1} • $statusText",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${timeSpentSec}s spent",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanAccent
                            )
                        }
                    }

                    Text(
                        text = question.questionText,
                        style = MaterialTheme.typography.titleMedium
                    )

                    val letters = listOf("A", "B", "C", "D")
                    question.options.take(4).forEachIndexed { optIdx, optText ->
                        val letter = letters.getOrElse(optIdx) { "A" }
                        val isOptionRight = question.correctAnswer.equals(letter, ignoreCase = true)
                        val isOptionUserPick = userSelected.equals(letter, ignoreCase = true)

                        val rowBg = when {
                            isOptionRight -> EmeraldSuccessContainer
                            isOptionUserPick && !isOptionRight -> CrimsonErrorContainer
                            else -> MaterialTheme.colorScheme.surface
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = rowBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$letter.",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isOptionRight || isOptionUserPick) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = optText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isOptionRight || isOptionUserPick) Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isOptionRight) {
                                    Text(
                                        text = "Correct Answer",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmeraldSuccess
                                    )
                                } else if (isOptionUserPick) {
                                    Text(
                                        text = "Your Pick",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CrimsonError
                                    )
                                }
                            }
                        }
                    }

                    if (question.explanation.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Explanation: ${question.explanation}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
