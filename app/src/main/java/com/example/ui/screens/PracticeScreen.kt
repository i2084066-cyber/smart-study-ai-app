package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyChapter
import com.example.data.model.StudyQuestion
import com.example.data.model.StudySubject
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonErrorContainer
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.viewmodel.ActivePracticeState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(
    practiceState: ActivePracticeState?,
    questions: List<StudyQuestion>,
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    onStartPractice: (String, String, String) -> Unit,
    onSelectOption: (String) -> Unit,
    onJumpToQuestion: (Int) -> Unit,
    onRestartPractice: (Boolean) -> Unit,
    onToggleBookmark: (StudyQuestion) -> Unit,
    onExitPractice: () -> Unit
) {
    if (practiceState == null) {
        PracticeLobbyView(
            questions = questions,
            subjects = subjects,
            chapters = chapters,
            onStartPractice = onStartPractice
        )
    } else if (practiceState.isFinished) {
        PracticeSummaryView(
            practiceState = practiceState,
            onRestartAll = { onRestartPractice(false) },
            onRetryWrong = { onRestartPractice(true) },
            onExit = onExitPractice
        )
    } else {
        ActivePracticeSessionView(
            practiceState = practiceState,
            onSelectOption = onSelectOption,
            onJumpToQuestion = onJumpToQuestion,
            onRestartPractice = { onRestartPractice(false) },
            onToggleBookmark = onToggleBookmark,
            onExitPractice = onExitPractice
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeLobbyView(
    questions: List<StudyQuestion>,
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    onStartPractice: (String, String, String) -> Unit
) {
    val incorrectCount = questions.count { !it.lastAttemptCorrect && it.attemptCount > 0 }
    val bookmarkedCount = questions.count { it.isBookmarked }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("practice_lobby_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Interactive Practice Mode",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Instant Duolingo-style feedback, explanations, and automatic progression after each answer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Core Practice Mode Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PracticeModeLaunchCard(
                    title = "Practice All Questions",
                    subtitle = "${questions.size} total MCQs across all subjects",
                    badgeText = "${questions.size} Qs",
                    accentColor = RoyalPurplePrimary,
                    enabled = questions.isNotEmpty(),
                    testTag = "practice_all_button",
                    onClick = { onStartPractice("ALL", "", "") }
                )
                PracticeModeLaunchCard(
                    title = "Practice Only Incorrect Questions",
                    subtitle = "Focus on questions you missed previously to build mastery",
                    badgeText = "$incorrectCount Qs",
                    accentColor = CrimsonError,
                    enabled = incorrectCount > 0,
                    testTag = "practice_incorrect_button",
                    onClick = { onStartPractice("INCORRECT", "", "") }
                )
                PracticeModeLaunchCard(
                    title = "Practice Bookmarked Questions",
                    subtitle = "Review your starred & high-priority exam questions",
                    badgeText = "$bookmarkedCount Qs",
                    accentColor = AmberWarning,
                    enabled = bookmarkedCount > 0,
                    testTag = "practice_bookmarked_button",
                    onClick = { onStartPractice("BOOKMARKED", "", "") }
                )
            }
        }

        // Practice by Subject & Chapter
        if (subjects.isNotEmpty()) {
            item {
                Text(
                    text = "Practice by Subject & Chapter",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            itemsIndexed(subjects, key = { _, s -> s.id }) { _, subj ->
                val subjQuestions = questions.count { it.subjectId == subj.id }
                val subjChapters = chapters.filter { it.subjectId == subj.id }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subj.subjectName,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "$subjQuestions questions available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onStartPractice("SUBJECT", subj.id, "") },
                                enabled = subjQuestions > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Practice Subject")
                            }
                        }

                        if (subjChapters.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                subjChapters.forEach { ch ->
                                    val chCount = questions.count { it.chapterId == ch.id }
                                    OutlinedButton(
                                        onClick = { onStartPractice("CHAPTER", subj.id, ch.id) },
                                        enabled = chCount > 0,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("${ch.chapterName} ($chCount)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeModeLaunchCard(
    title: String,
    subtitle: String,
    badgeText: String,
    accentColor: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivePracticeSessionView(
    practiceState: ActivePracticeState,
    onSelectOption: (String) -> Unit,
    onJumpToQuestion: (Int) -> Unit,
    onRestartPractice: () -> Unit,
    onToggleBookmark: (StudyQuestion) -> Unit,
    onExitPractice: () -> Unit
) {
    val totalCount = practiceState.questions.size.coerceAtLeast(1)
    val currentIndex = practiceState.currentIndex.coerceIn(0, totalCount - 1)
    val currentQuestion = practiceState.questions[currentIndex]
    val selectedOption = practiceState.selectedOptionByQuestionId[currentQuestion.id]
    val isAnswered = selectedOption != null
    val isCorrect = selectedOption != null && selectedOption.equals(currentQuestion.correctAnswer, ignoreCase = true)

    val progressFraction by animateFloatAsState(
        targetValue = ((currentIndex + 1).toFloat() / totalCount.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "practiceProgress"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("active_practice_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header + Progress Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onExitPractice,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Mode")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldSuccess.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "Score: ${practiceState.score}/${practiceState.selectedOptionByQuestionId.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldSuccess,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(onClick = onRestartPractice) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Practice",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question ${currentIndex + 1} of $totalCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${currentQuestion.subjectName} • ${currentQuestion.chapterName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = RoyalPurplePrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Question Number Horizontal Jump Strip
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(practiceState.questions, key = { _, q -> q.id }) { idx, q ->
                    val ans = practiceState.selectedOptionByQuestionId[q.id]
                    val bgColor = when {
                        idx == currentIndex -> RoyalPurplePrimary
                        ans == null -> MaterialTheme.colorScheme.surfaceVariant
                        ans.equals(q.correctAnswer, ignoreCase = true) -> EmeraldSuccess
                        else -> CrimsonError
                    }
                    val textColor = if (idx == currentIndex || ans != null) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant

                    Surface(
                        shape = CircleShape,
                        color = bgColor,
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { onJumpToQuestion(idx) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }

        // Question Stem Card
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
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanAccent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = currentQuestion.difficulty,
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(onClick = { onToggleBookmark(currentQuestion) }) {
                            Icon(
                                imageVector = if (currentQuestion.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark Question",
                                tint = if (currentQuestion.isBookmarked) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentQuestion.questionText,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        // 4 Answer Option Cards
        val optionLetters = listOf("A", "B", "C", "D")
        itemsIndexed(currentQuestion.options.take(4)) { idx, optionText ->
            val letter = optionLetters.getOrElse(idx) { "A" }
            val isThisSelected = selectedOption.equals(letter, ignoreCase = true)
            val isThisCorrect = currentQuestion.correctAnswer.equals(letter, ignoreCase = true)

            val containerColor = when {
                !isAnswered -> MaterialTheme.colorScheme.surfaceVariant
                isThisCorrect -> EmeraldSuccessContainer
                isThisSelected && !isThisCorrect -> CrimsonErrorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
            val borderColor = when {
                !isAnswered -> Color.Transparent
                isThisCorrect -> EmeraldSuccess
                isThisSelected && !isThisCorrect -> CrimsonError
                else -> Color.Transparent
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                border = if (borderColor != Color.Transparent) BorderStroke(2.dp, borderColor) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isAnswered) { onSelectOption(letter) }
                    .testTag("practice_option_$letter")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isAnswered && isThisCorrect -> EmeraldSuccess
                            isAnswered && isThisSelected && !isThisCorrect -> CrimsonError
                            else -> RoyalPurplePrimary.copy(alpha = 0.25f)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = optionText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isAnswered && (isThisCorrect || isThisSelected)) Color.White
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (isAnswered && isThisCorrect) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Correct Option",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(26.dp)
                        )
                    } else if (isAnswered && isThisSelected && !isThisCorrect) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Wrong Option",
                            tint = CrimsonError,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Animated Feedback & Explanation Banner
        item {
            AnimatedVisibility(
                visible = isAnswered,
                enter = fadeIn() + slideInVertically()
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCorrect) EmeraldSuccessContainer else CrimsonErrorContainer
                    ),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (isCorrect) EmeraldSuccess else CrimsonError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isCorrect) EmeraldSuccess else CrimsonError,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isCorrect) {
                                    "Brilliant! Correct Answer! (Auto-advancing in 1.5s...)"
                                } else {
                                    "Not quite! Correct Answer is Option ${currentQuestion.correctAnswer}. (Auto-advancing in 2.5s...)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        if (currentQuestion.explanation.isNotBlank()) {
                            Text(
                                text = currentQuestion.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }

        // Previous & Next Manual Navigation Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onJumpToQuestion(currentIndex - 1) },
                    enabled = currentIndex > 0,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("practice_prev_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Previous")
                }

                Button(
                    onClick = {
                        if (currentIndex < totalCount - 1) {
                            onJumpToQuestion(currentIndex + 1)
                        } else {
                            onExitPractice()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("practice_next_button")
                ) {
                    Text(if (currentIndex < totalCount - 1) "Next Question" else "Finish Practice")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun PracticeSummaryView(
    practiceState: ActivePracticeState,
    onRestartAll: () -> Unit,
    onRetryWrong: () -> Unit,
    onExit: () -> Unit
) {
    val total = practiceState.questions.size.coerceAtLeast(1)
    val correct = practiceState.score
    val wrong = practiceState.wrongQuestionIds.size
    val pct = ((correct * 100.0) / total).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Practice Session Complete!",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Accuracy: $pct% ($correct Correct • $wrong Wrong)",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (pct >= 75) EmeraldSuccess else RoyalPurplePrimary
                )

                Button(
                    onClick = onRestartAll,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restart Full Practice")
                }

                if (wrong > 0) {
                    Button(
                        onClick = onRetryWrong,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Practice Only Incorrect ($wrong Qs)")
                    }
                }

                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Practice Menu")
                }
            }
        }
    }
}
