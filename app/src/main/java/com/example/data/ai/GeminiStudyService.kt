package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ExtractedQuestionDraft
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

private const val TAG = "GeminiStudyService"

class GeminiStudyService {

    private val mcqArraySchema = Schema.obj(
        properties = mapOf(
            "detectedSubject" to Schema.string(description = "Detected subject name, e.g., Physics, Biology, Mathematics"),
            "detectedChapter" to Schema.string(description = "Detected chapter or topic name"),
            "questions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "questionNumber" to Schema.integer(description = "Sequential question number starting at 1"),
                        "questionText" to Schema.string(description = "Exact text of the question stem without altering original meaning"),
                        "optionA" to Schema.string(description = "Text of Option A (without the 'A.' prefix)"),
                        "optionB" to Schema.string(description = "Text of Option B (without the 'B.' prefix)"),
                        "optionC" to Schema.string(description = "Text of Option C (without the 'C.' prefix)"),
                        "optionD" to Schema.string(description = "Text of Option D (without the 'D.' prefix)"),
                        "correctAnswer" to Schema.enumeration(
                            values = listOf("A", "B", "C", "D"),
                            description = "Correct option letter: A, B, C, or D"
                        ),
                        "explanation" to Schema.string(description = "Concise, accurate explanation of why the correct option is right"),
                        "sourceReference" to Schema.string(description = "Page, image, or topic reference"),
                        "answerConfidence" to Schema.enumeration(
                            values = listOf("VERIFIED", "AI_GENERATED", "NEEDS_REVIEW"),
                            description = "VERIFIED if answer is explicitly in source material; AI_GENERATED if solved by AI; NEEDS_REVIEW if handwriting/image is unclear"
                        ),
                        "difficulty" to Schema.enumeration(
                            values = listOf("EASY", "MEDIUM", "HARD"),
                            description = "Estimated difficulty of the question"
                        )
                    )
                ),
                description = "Ordered list of extracted or generated multiple-choice questions"
            )
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = mcqArraySchema
            }
        )
    }

    data class ExtractionResponse(
        val detectedSubject: String,
        val detectedChapter: String,
        val drafts: List<ExtractedQuestionDraft>
    )

    suspend fun extractQuestionsFromMaterials(
        bitmaps: List<Bitmap>,
        sourceLabel: String,
        customInstructions: String = ""
    ): Result<ExtractionResponse> {
        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(
                    content {
                        bitmaps.take(8).forEach { bmp ->
                            image(bmp)
                        }
                        text(
                            buildString {
                                appendLine("You are an expert educational AI analyzing uploaded study material ('$sourceLabel').")
                                appendLine("Carefully read all handwritten or printed questions, options, answers, and notes in the provided image(s).")
                                appendLine("Follow these strict rules:")
                                appendLine("1. Extract each question in its original sequence. Do NOT silently change the meaning of any original question.")
                                appendLine("2. If a question already has four options (A, B, C, D), preserve those exact four options.")
                                appendLine("3. If a question has only a question and answer (or is study notes/flashcards), generate three plausible incorrect options and place the correct answer in one of the four options (A, B, C, or D).")
                                appendLine("4. If the source material explicitly indicates the correct answer, set answerConfidence to 'VERIFIED'.")
                                appendLine("5. If the source does NOT provide the correct answer, solve the question carefully and set answerConfidence to 'AI_GENERATED' so the student knows it requires verification.")
                                appendLine("6. If any part of the question or handwriting is unclear or ambiguous, set answerConfidence to 'NEEDS_REVIEW' and note what needs review in the explanation.")
                                appendLine("7. Do not generate duplicate questions.")
                                if (customInstructions.isNotBlank()) {
                                    appendLine("Additional user context: $customInstructions")
                                }
                            }
                        )
                    }
                )
            }

            val rawJson = response.text ?: throw IllegalStateException("Gemini returned an empty response.")
            Result.success(parseExtractionJson(rawJson, sourceLabel))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Question extraction timed out", e)
            Result.failure(Exception("AI analysis timed out after 60 seconds. Please try with fewer pages or clearer images."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Multimodal question extraction failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to extract questions from uploaded material."))
        }
    }

    suspend fun generateQuestionsFromTopicOrNotes(
        topicOrNotes: String,
        questionCount: Int,
        difficulty: String,
        subjectHint: String = "",
        chapterHint: String = ""
    ): Result<ExtractionResponse> {
        return try {
            val safeCount = questionCount.coerceIn(1, 25)
            val safeDifficulty = difficulty.uppercase(Locale.US).takeIf { it in listOf("EASY", "MEDIUM", "HARD") } ?: "MEDIUM"

            val prompt = buildString {
                appendLine("Generate $safeCount unique, high-quality multiple-choice questions (MCQs) based on the following topic, chapter, or study material:")
                appendLine("--- START MATERIAL ---")
                appendLine(topicOrNotes.trim())
                appendLine("--- END MATERIAL ---")
                if (subjectHint.isNotBlank()) appendLine("Subject: $subjectHint")
                if (chapterHint.isNotBlank()) appendLine("Chapter: $chapterHint")
                appendLine("Requirements:")
                appendLine("- Difficulty level: $safeDifficulty")
                appendLine("- Format: 4 distinct options (optionA, optionB, optionC, optionD) with exactly 1 unambiguous correctAnswer (A, B, C, or D).")
                appendLine("- Provide a clear, educational explanation for each question.")
                appendLine("- Set answerConfidence to 'AI_GENERATED' unless the exact answer was quoted from pasted study material (then 'VERIFIED').")
                appendLine("- Do not generate duplicate questions.")
            }

            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Gemini returned an empty response.")
            Result.success(parseExtractionJson(rawJson, "AI Generator: ${topicOrNotes.take(40)}"))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI question generation timed out", e)
            Result.failure(Exception("Question generation timed out after 60 seconds. Try generating fewer questions at once."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI question generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to generate questions."))
        }
    }

    private fun parseExtractionJson(jsonString: String, defaultSource: String): ExtractionResponse {
        val root = JSONObject(jsonString)
        val detectedSubject = root.optString("detectedSubject", "General Science").ifBlank { "General Science" }
        val detectedChapter = root.optString("detectedChapter", "Core Concepts").ifBlank { "Core Concepts" }
        val questionsArray = root.optJSONArray("questions")
        val drafts = mutableListOf<ExtractedQuestionDraft>()

        if (questionsArray != null) {
            for (i in 0 until questionsArray.length()) {
                val obj = questionsArray.getJSONObject(i)
                val qNum = obj.optInt("questionNumber", i + 1).coerceAtLeast(1)
                val qText = obj.optString("questionText", "").trim()
                if (qText.isBlank()) continue

                val optA = obj.optString("optionA", "Option A").trim().ifBlank { "Option A" }
                val optB = obj.optString("optionB", "Option B").trim().ifBlank { "Option B" }
                val optC = obj.optString("optionC", "Option C").trim().ifBlank { "Option C" }
                val optD = obj.optString("optionD", "Option D").trim().ifBlank { "Option D" }

                val correct = obj.optString("correctAnswer", "A").uppercase(Locale.US)
                    .takeIf { it in listOf("A", "B", "C", "D") } ?: "A"
                val explanation = obj.optString("explanation", "").trim()
                val sourceRef = obj.optString("sourceReference", defaultSource).trim().ifBlank { defaultSource }
                val confidence = obj.optString("answerConfidence", "AI_GENERATED").uppercase(Locale.US)
                    .takeIf { it in listOf("VERIFIED", "AI_GENERATED", "NEEDS_REVIEW") } ?: "AI_GENERATED"
                val difficulty = obj.optString("difficulty", "MEDIUM").uppercase(Locale.US)
                    .takeIf { it in listOf("EASY", "MEDIUM", "HARD") } ?: "MEDIUM"

                drafts.add(
                    ExtractedQuestionDraft(
                        draftId = UUID.randomUUID().toString(),
                        questionNumber = qNum,
                        questionText = qText,
                        optionA = optA,
                        optionB = optB,
                        optionC = optC,
                        optionD = optD,
                        correctAnswer = correct,
                        explanation = explanation,
                        sourceReference = sourceRef,
                        answerConfidence = confidence,
                        difficulty = difficulty
                    )
                )
            }
        }
        return ExtractionResponse(
            detectedSubject = detectedSubject,
            detectedChapter = detectedChapter,
            drafts = drafts
        )
    }
}
