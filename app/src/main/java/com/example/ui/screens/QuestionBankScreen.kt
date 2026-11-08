package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyChapter
import com.example.data.model.StudyQuestion
import com.example.data.model.StudySubject
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoyalPurplePrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionBankScreen(
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    questions: List<StudyQuestion>,
    onAddManualQuestion: (StudyQuestion) -> Unit,
    onUpdateQuestion: (StudyQuestion) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onToggleBookmark: (StudyQuestion) -> Unit,
    onCreateSubject: (String, String) -> Unit,
    onCreateChapter: (String, String, Int) -> Unit,
    onPracticeChapter: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf("") }
    var selectedChapterId by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf("ALL") }
    var onlyBookmarked by remember { mutableStateOf(false) }

    var showQuestionDialog by remember { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf<StudyQuestion?>(null) }
    var showManageCurriculumDialog by remember { mutableStateOf(false) }

    val filteredQuestions = remember(
        questions,
        searchQuery,
        selectedSubjectId,
        selectedChapterId,
        selectedDifficulty,
        onlyBookmarked
    ) {
        questions.filter { q ->
            val matchesQuery = searchQuery.isBlank() ||
                q.questionText.contains(searchQuery, ignoreCase = true) ||
                q.explanation.contains(searchQuery, ignoreCase = true) ||
                q.collectionTag.contains(searchQuery, ignoreCase = true) ||
                q.subjectName.contains(searchQuery, ignoreCase = true) ||
                q.chapterName.contains(searchQuery, ignoreCase = true)
            val matchesSubj = selectedSubjectId.isBlank() || q.subjectId == selectedSubjectId
            val matchesChap = selectedChapterId.isBlank() || q.chapterId == selectedChapterId
            val matchesDiff = selectedDifficulty == "ALL" || q.difficulty.equals(selectedDifficulty, ignoreCase = true)
            val matchesBook = !onlyBookmarked || q.isBookmarked
            matchesQuery && matchesSubj && matchesChap && matchesDiff && matchesBook
        }
    }

    val visibleChapters = remember(chapters, selectedSubjectId) {
        if (selectedSubjectId.isBlank()) chapters
        else chapters.filter { it.subjectId == selectedSubjectId }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("question_bank_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Question Bank & Subjects",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${filteredQuestions.size} of ${questions.size} questions shown",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showManageCurriculumDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("manage_subjects_button")
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Subjects")
                    }
                    Button(
                        onClick = {
                            editingQuestion = null
                            showQuestionDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_manual_question_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add MCQ")
                    }
                }
            }
        }

        // Search & Filters Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search questions, explanations, chapters, or tags...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("question_search_input")
                    )

                    // Subject filter chips
                    Text("Filter by Subject", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedSubjectId.isBlank(),
                            onClick = {
                                selectedSubjectId = ""
                                selectedChapterId = ""
                            },
                            label = { Text("All Subjects") }
                        )
                        subjects.forEach { subj ->
                            FilterChip(
                                selected = selectedSubjectId == subj.id,
                                onClick = {
                                    selectedSubjectId = subj.id
                                    selectedChapterId = ""
                                },
                                label = { Text(subj.subjectName) }
                            )
                        }
                    }

                    // Chapter filter chips if available
                    if (visibleChapters.isNotEmpty()) {
                        Text("Filter by Chapter", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedChapterId.isBlank(),
                                onClick = { selectedChapterId = "" },
                                label = { Text("All Chapters") }
                            )
                            visibleChapters.forEach { ch ->
                                FilterChip(
                                    selected = selectedChapterId == ch.id,
                                    onClick = { selectedChapterId = ch.id },
                                    label = { Text(ch.chapterName) }
                                )
                            }
                        }
                    }

                    // Difficulty + Bookmarked filter chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL", "EASY", "MEDIUM", "HARD").forEach { diff ->
                            FilterChip(
                                selected = selectedDifficulty == diff,
                                onClick = { selectedDifficulty = diff },
                                label = { Text(if (diff == "ALL") "All Difficulties" else diff) }
                            )
                        }
                        FilterChip(
                            selected = onlyBookmarked,
                            onClick = { onlyBookmarked = !onlyBookmarked },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (onlyBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Bookmarked") }
                        )
                    }
                }
            }
        }

        // Questions List
        items(filteredQuestions, key = { it.id }) { q ->
            QuestionBankItemCard(
                question = q,
                onToggleBookmark = { onToggleBookmark(q) },
                onEdit = {
                    editingQuestion = q
                    showQuestionDialog = true
                },
                onDelete = { onDeleteQuestion(q.id) }
            )
        }
    }

    if (showQuestionDialog) {
        AddOrEditQuestionDialog(
            initialQuestion = editingQuestion,
            subjects = subjects,
            chapters = chapters,
            onDismiss = { showQuestionDialog = false },
            onSave = { updated ->
                if (editingQuestion == null) {
                    onAddManualQuestion(updated)
                } else {
                    onUpdateQuestion(updated)
                }
                showQuestionDialog = false
            }
        )
    }

    if (showManageCurriculumDialog) {
        ManageSubjectsAndChaptersDialog(
            subjects = subjects,
            chapters = chapters,
            questions = questions,
            onCreateSubject = onCreateSubject,
            onCreateChapter = onCreateChapter,
            onPracticeChapter = { sId, cId ->
                showManageCurriculumDialog = false
                onPracticeChapter(sId, cId)
            },
            onDismiss = { showManageCurriculumDialog = false }
        )
    }
}

@Composable
private fun QuestionBankItemCard(
    question: StudyQuestion,
    onToggleBookmark: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val diffColor = when (question.difficulty) {
        "EASY" -> EmeraldSuccess
        "HARD" -> CrimsonError
        else -> AmberWarning
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RoyalPurplePrimary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = question.subjectName,
                            style = MaterialTheme.typography.labelMedium,
                            color = RoyalPurplePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (question.chapterName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanAccent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = question.chapterName,
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = diffColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = question.difficulty,
                            style = MaterialTheme.typography.labelMedium,
                            color = diffColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row {
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (question.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark Question",
                            tint = if (question.isBookmarked) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Question",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Question",
                            tint = CrimsonError
                        )
                    }
                }
            }

            Text(
                text = question.questionText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            val letters = listOf("A", "B", "C", "D")
            question.options.take(4).forEachIndexed { idx, optText ->
                val letter = letters.getOrElse(idx) { "A" }
                val isCorrect = question.correctAnswer.equals(letter, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCorrect) EmeraldSuccess.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$letter.",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isCorrect) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = optText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCorrect) EmeraldSuccess else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCorrect) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Correct Answer",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (question.explanation.isNotBlank()) {
                Text(
                    text = "Explanation: ${question.explanation}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddOrEditQuestionDialog(
    initialQuestion: StudyQuestion?,
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    onDismiss: () -> Unit,
    onSave: (StudyQuestion) -> Unit
) {
    val defaultSubject = subjects.firstOrNull()
    var selectedSubjectId by remember {
        mutableStateOf(initialQuestion?.subjectId ?: defaultSubject?.id ?: "subj_science")
    }
    var subjectName by remember {
        mutableStateOf(initialQuestion?.subjectName ?: defaultSubject?.subjectName ?: "Science")
    }
    val matchingChapters = chapters.filter { it.subjectId == selectedSubjectId }
    var selectedChapterId by remember {
        mutableStateOf(initialQuestion?.chapterId ?: matchingChapters.firstOrNull()?.id ?: "ch_general")
    }
    var chapterName by remember {
        mutableStateOf(initialQuestion?.chapterName ?: matchingChapters.firstOrNull()?.chapterName ?: "Chapter 1")
    }

    var questionText by remember { mutableStateOf(initialQuestion?.questionText ?: "") }
    var optionA by remember { mutableStateOf(initialQuestion?.options?.getOrNull(0) ?: "") }
    var optionB by remember { mutableStateOf(initialQuestion?.options?.getOrNull(1) ?: "") }
    var optionC by remember { mutableStateOf(initialQuestion?.options?.getOrNull(2) ?: "") }
    var optionD by remember { mutableStateOf(initialQuestion?.options?.getOrNull(3) ?: "") }
    var correctAnswer by remember { mutableStateOf(initialQuestion?.correctAnswer ?: "A") }
    var explanation by remember { mutableStateOf(initialQuestion?.explanation ?: "") }
    var difficulty by remember { mutableStateOf(initialQuestion?.difficulty ?: "MEDIUM") }
    var collectionTag by remember { mutableStateOf(initialQuestion?.collectionTag ?: "General") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialQuestion == null) "Add Manual MCQ" else "Edit Question") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { subjectName = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = chapterName,
                        onValueChange = { chapterName = it },
                        label = { Text("Chapter") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Question Text") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = optionA,
                    onValueChange = { optionA = it },
                    label = { Text("Option A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = optionB,
                    onValueChange = { optionB = it },
                    label = { Text("Option B") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = optionC,
                    onValueChange = { optionC = it },
                    label = { Text("Option C") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = optionD,
                    onValueChange = { optionD = it },
                    label = { Text("Option D") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Answer:", style = MaterialTheme.typography.labelLarge)
                    listOf("A", "B", "C", "D").forEach { letter ->
                        FilterChip(
                            selected = correctAnswer == letter,
                            onClick = { correctAnswer = letter },
                            label = { Text(letter) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Difficulty:", style = MaterialTheme.typography.labelLarge)
                    listOf("EASY", "MEDIUM", "HARD").forEach { diff ->
                        FilterChip(
                            selected = difficulty == diff,
                            onClick = { difficulty = diff },
                            label = { Text(diff) }
                        )
                    }
                }

                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("Short Explanation") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = collectionTag,
                    onValueChange = { collectionTag = it },
                    label = { Text("Collection / Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = initialQuestion ?: StudyQuestion()
                    onSave(
                        base.copy(
                            subjectId = selectedSubjectId,
                            subjectName = subjectName.ifBlank { "General" },
                            chapterId = selectedChapterId,
                            chapterName = chapterName.ifBlank { "Chapter 1" },
                            questionText = questionText,
                            options = listOf(optionA, optionB, optionC, optionD),
                            correctAnswer = correctAnswer,
                            explanation = explanation,
                            difficulty = difficulty,
                            collectionTag = collectionTag
                        )
                    )
                },
                enabled = questionText.isNotBlank() && optionA.isNotBlank() && optionB.isNotBlank()
            ) {
                Text("Save Question")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ManageSubjectsAndChaptersDialog(
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    questions: List<StudyQuestion>,
    onCreateSubject: (String, String) -> Unit,
    onCreateChapter: (String, String, Int) -> Unit,
    onPracticeChapter: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var newSubjectName by remember { mutableStateOf("") }
    var newChapterName by remember { mutableStateOf("") }
    var selectedSubjectForChapter by remember { mutableStateOf(subjects.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subject & Chapter Hierarchy") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Create New Subject", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newSubjectName,
                        onValueChange = { newSubjectName = it },
                        label = { Text("Subject Name (e.g. Mathematics)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newSubjectName.isNotBlank()) {
                                onCreateSubject(newSubjectName, "Custom Subject")
                                newSubjectName = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }

                if (subjects.isNotEmpty()) {
                    Text("Add Chapter to Subject", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        subjects.take(4).forEach { subj ->
                            FilterChip(
                                selected = selectedSubjectForChapter == subj.id,
                                onClick = { selectedSubjectForChapter = subj.id },
                                label = { Text(subj.subjectName) }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newChapterName,
                            onValueChange = { newChapterName = it },
                            label = { Text("Chapter Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val targetSId = selectedSubjectForChapter.ifBlank { subjects.first().id }
                                if (newChapterName.isNotBlank()) {
                                    val nextNum = chapters.count { it.subjectId == targetSId } + 1
                                    onCreateChapter(targetSId, newChapterName, nextNum)
                                    newChapterName = ""
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Your Subjects & Chapters", style = MaterialTheme.typography.titleMedium)
                subjects.forEach { subj ->
                    val subjChapters = chapters.filter { it.subjectId == subj.id }
                    val subjQCount = questions.count { it.subjectId == subj.id }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${subj.subjectName} ($subjQCount MCQs)",
                                style = MaterialTheme.typography.titleMedium,
                                color = RoyalPurplePrimary
                            )
                            subjChapters.forEach { ch ->
                                val chQCount = questions.count { it.chapterId == ch.id }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${ch.chapterName} ($chQCount Qs)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (chQCount > 0) {
                                        TextButton(onClick = { onPracticeChapter(subj.id, ch.id) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Practice")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
