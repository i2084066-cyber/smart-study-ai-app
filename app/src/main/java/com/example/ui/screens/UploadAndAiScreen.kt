package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ExtractedQuestionDraft
import com.example.data.model.StudyChapter
import com.example.data.model.StudySubject
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.viewmodel.AiExtractionUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadAndAiScreen(
    aiState: AiExtractionUiState,
    subjects: List<StudySubject>,
    chapters: List<StudyChapter>,
    onAnalyzeUris: (List<android.net.Uri>, String) -> Unit,
    onGenerateByTopic: (String, Int, String, String, String) -> Unit,
    onUpdateDraft: (ExtractedQuestionDraft) -> Unit,
    onRemoveDraft: (String) -> Unit,
    onSaveApprovedDrafts: (String, String, String) -> Unit,
    onClearDrafts: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Image/PDF Upload, 1 = AI Topic Generator
    var customExtractionHint by remember { mutableStateOf("") }

    // AI Generator inputs
    var topicOrNotes by remember { mutableStateOf("") }
    var questionCount by remember { mutableIntStateOf(5) }
    var selectedDifficulty by remember { mutableStateOf("MEDIUM") }

    // Target Subject & Chapter for saving
    var targetSubjectName by remember { mutableStateOf("Science") }
    var targetChapterName by remember { mutableStateOf("Chapter 1: Physics") }

    LaunchedEffect(aiState.detectedSubject, aiState.detectedChapter) {
        if (aiState.detectedSubject.isNotBlank()) {
            targetSubjectName = aiState.detectedSubject
        }
        if (aiState.detectedChapter.isNotBlank()) {
            targetChapterName = aiState.detectedChapter
        }
    }

    // Zero-permission Photo Picker for multiple images (JPG, JPEG, PNG, WEBP)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAnalyzeUris(uris, customExtractionHint)
        }
    }

    // SAF Document Picker for PDFs & images
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAnalyzeUris(uris, customExtractionHint)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("upload_ai_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "AI Study Studio & Question Extractor",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Upload question papers, handwritten notes, or PDFs — or generate fresh MCQs on any topic using Gemini AI.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Image & PDF Upload") },
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                    modifier = Modifier.testTag("tab_image_pdf_upload")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("AI MCQ Generator") },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    modifier = Modifier.testTag("tab_ai_generator")
                )
            }
        }

        if (selectedTab == 0) {
            // TAB 0: Multimodal Image & PDF Upload
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 2.dp,
                                    color = RoyalPurplePrimary.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .background(
                                    color = RoyalPurplePrimary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = RoyalPurplePrimary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Upload Study Images or Question PDFs",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "Supports JPG, JPEG, PNG, WEBP (multiple images) & multi-page PDF documents",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = !aiState.isAnalyzing,
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pick_images_button")
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Select Images")
                                    }
                                    Button(
                                        onClick = {
                                            documentPickerLauncher.launch(
                                                arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")
                                            )
                                        },
                                        enabled = !aiState.isAnalyzing,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyanAccent,
                                            contentColor = Color(0xFF042F2E)
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pick_pdf_button")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Upload PDF")
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customExtractionHint,
                            onValueChange = { customExtractionHint = it },
                            label = { Text("Optional Instructions (e.g. 'Focus on handwritten questions 1-10')") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("extraction_hint_input"),
                            singleLine = true
                        )
                    }
                }
            }
        } else {
            // TAB 1: AI Topic / Pasted Study Material MCQ Generator
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
                        Text(
                            text = "Generate Custom MCQs with Gemini AI",
                            style = MaterialTheme.typography.titleLarge
                        )
                        OutlinedTextField(
                            value = topicOrNotes,
                            onValueChange = { topicOrNotes = it },
                            label = { Text("Enter Topic, Chapter, or Paste Study Notes / Q&A") },
                            placeholder = { Text("e.g., Laws of Thermodynamics, Photosynthesis, or paste raw question-answer pairs...") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_topic_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = targetSubjectName,
                                onValueChange = { targetSubjectName = it },
                                label = { Text("Subject") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = targetChapterName,
                                onValueChange = { targetChapterName = it },
                                label = { Text("Chapter") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Number of Questions",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 5, 10, 15, 20).forEach { count ->
                                FilterChip(
                                    selected = questionCount == count,
                                    onClick = { questionCount = count },
                                    label = { Text("$count MCQs") }
                                )
                            }
                        }

                        Text(
                            text = "Difficulty Level",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("EASY", "MEDIUM", "HARD").forEach { diff ->
                                FilterChip(
                                    selected = selectedDifficulty == diff,
                                    onClick = { selectedDifficulty = diff },
                                    label = { Text(diff) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onGenerateByTopic(
                                    topicOrNotes,
                                    questionCount,
                                    selectedDifficulty,
                                    targetSubjectName,
                                    targetChapterName
                                )
                            },
                            enabled = !aiState.isAnalyzing && topicOrNotes.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalPurplePrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("generate_ai_mcqs_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate $questionCount $selectedDifficulty MCQs")
                        }
                    }
                }
            }
        }

        // Loading / Analyzing Indicator
        if (aiState.isAnalyzing) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = RoyalPurplePrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Gemini AI is Analyzing...",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = aiState.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Error Message Card
        if (aiState.errorMessage != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CrimsonError.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = CrimsonError
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = aiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonError
                        )
                    }
                }
            }
        }

        // Saved Confirmation Card
        if (aiState.savedCountMessage != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldSuccess.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldSuccess
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = aiState.savedCountMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Extracted / Generated Questions Review & Editor Section
        if (aiState.drafts.isNotEmpty()) {
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
                            text = "Review & Assign Destination (${aiState.drafts.size} Questions)",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Verify questions, options, and AI-solved answers below. You can edit any question before saving to your Question Bank.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = targetSubjectName,
                                onValueChange = { targetSubjectName = it },
                                label = { Text("Save to Subject") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = targetChapterName,
                                onValueChange = { targetChapterName = it },
                                label = { Text("Save to Chapter") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (subjects.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                subjects.forEach { subj ->
                                    FilterChip(
                                        selected = targetSubjectName.equals(subj.subjectName, ignoreCase = true),
                                        onClick = {
                                            targetSubjectName = subj.subjectName
                                            val firstCh = chapters.firstOrNull { it.subjectId == subj.id }
                                            if (firstCh != null) {
                                                targetChapterName = firstCh.chapterName
                                            }
                                        },
                                        label = { Text(subj.subjectName) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onClearDrafts,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Discard All")
                            }
                            Button(
                                onClick = {
                                    onSaveApprovedDrafts(
                                        targetSubjectName,
                                        targetChapterName,
                                        if (selectedTab == 0) "Uploaded Material" else "AI Generated"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                modifier = Modifier
                                    .weight(1.6f)
                                    .testTag("save_approved_drafts_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save ${aiState.drafts.size} Approved MCQs")
                            }
                        }
                    }
                }
            }

            itemsIndexed(aiState.drafts, key = { _, d -> d.draftId }) { index, draft ->
                EditableDraftQuestionCard(
                    index = index + 1,
                    draft = draft,
                    onUpdate = onUpdateDraft,
                    onRemove = { onRemoveDraft(draft.draftId) }
                )
            }
        }
    }
}

@Composable
private fun EditableDraftQuestionCard(
    index: Int,
    draft: ExtractedQuestionDraft,
    onUpdate: (ExtractedQuestionDraft) -> Unit,
    onRemove: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }

    val confidenceColor = when (draft.answerConfidence) {
        "VERIFIED" -> EmeraldSuccess
        "NEEDS_REVIEW" -> CrimsonError
        else -> AmberWarning
    }
    val confidenceLabel = when (draft.answerConfidence) {
        "VERIFIED" -> "Source Verified"
        "NEEDS_REVIEW" -> "Unclear • Please Review"
        else -> "AI-Solved • Verify Answer"
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RoyalPurplePrimary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Q$index",
                            style = MaterialTheme.typography.labelLarge,
                            color = RoyalPurplePrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = confidenceColor.copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = confidenceColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = confidenceLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = confidenceColor
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Question",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Question",
                            tint = CrimsonError
                        )
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = draft.questionText,
                    onValueChange = { onUpdate(draft.copy(questionText = it)) },
                    label = { Text("Question Stem") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.optionA,
                    onValueChange = { onUpdate(draft.copy(optionA = it)) },
                    label = { Text("Option A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.optionB,
                    onValueChange = { onUpdate(draft.copy(optionB = it)) },
                    label = { Text("Option B") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.optionC,
                    onValueChange = { onUpdate(draft.copy(optionC = it)) },
                    label = { Text("Option C") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.optionD,
                    onValueChange = { onUpdate(draft.copy(optionD = it)) },
                    label = { Text("Option D") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Correct Answer:", style = MaterialTheme.typography.labelLarge)
                    listOf("A", "B", "C", "D").forEach { letter ->
                        FilterChip(
                            selected = draft.correctAnswer == letter,
                            onClick = {
                                onUpdate(draft.copy(correctAnswer = letter, answerConfidence = "VERIFIED"))
                            },
                            label = { Text(letter) }
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.explanation,
                    onValueChange = { onUpdate(draft.copy(explanation = it)) },
                    label = { Text("Explanation") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = draft.questionText,
                    style = MaterialTheme.typography.titleMedium
                )
                val options = listOf(
                    "A" to draft.optionA,
                    "B" to draft.optionB,
                    "C" to draft.optionC,
                    "D" to draft.optionD
                )
                options.forEach { (letter, text) ->
                    val isRight = draft.correctAnswer.equals(letter, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isRight) EmeraldSuccess.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdate(draft.copy(correctAnswer = letter, answerConfidence = "VERIFIED"))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$letter.",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isRight) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isRight) EmeraldSuccess else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isRight) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Correct Option",
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                if (draft.explanation.isNotBlank()) {
                    Text(
                        text = "Explanation: ${draft.explanation}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
