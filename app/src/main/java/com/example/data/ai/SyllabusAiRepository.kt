package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GeneratedStudyPlan(
    val subjects: List<String>,
    val syllabusTopics: List<String>,
    val customChallenges: List<String>,
    val studySummary: String
)

class SyllabusAiRepository {

    private val syllabusPlanSchema = Schema.obj(
        properties = mapOf(
            "subjects" to Schema.array(
                items = Schema.string(),
                description = "Clean list of study subjects tailored to the student's course and syllabus (at least 1, up to 12)."
            ),
            "syllabusTopics" to Schema.array(
                items = Schema.string(),
                description = "Up to 15 concrete chapters or study topics from the syllabus, formatted as 'Subject: Topic'."
            ),
            "customChallenges" to Schema.array(
                items = Schema.string(),
                description = "4 to 6 short, motivating Hinglish 'Pehli Line' starter micro-challenges directly referencing the student's actual subjects and syllabus topics."
            ),
            "studySummary" to Schema.string(
                description = "A concise 1-sentence encouraging Hinglish summary of the personalized study plan."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = syllabusPlanSchema
            }
        )
    }

    suspend fun analyzeStudyAndSyllabus(
        currentStudy: String,
        enteredSubjects: String,
        typedSyllabus: String,
        syllabusBitmap: Bitmap?
    ): Result<GeneratedStudyPlan> {
        return try {
            val promptText = buildString {
                appendLine("You are building a personalized Focus Garden study plan for a student in Hinglish (Hindi written in Latin script + simple English academic terms).")
                appendLine("Student's current study/course/exam: ${currentStudy.ifBlank { "Not specified" }}")
                appendLine("Student's entered subjects: ${enteredSubjects.ifBlank { "Extract from syllabus if provided" }}")
                if (typedSyllabus.isNotBlank()) {
                    appendLine("Typed syllabus details:")
                    appendLine(typedSyllabus.take(3500))
                }
                if (syllabusBitmap != null) {
                    appendLine("A syllabus photo/screenshot is attached. Carefully read all subjects, units, chapters, and topics visible in the image.")
                }
                appendLine("Instructions:")
                appendLine("1. Combine the student's entered subjects with any additional subjects discovered in the syllabus (if enteredSubjects was blank, extract all subjects from the syllabus; never invent unrelated subjects like Python or Statistics unless the student's study/syllabus includes them).")
                appendLine("2. Extract concrete chapter/unit topics as 'Subject: Topic' strings.")
                appendLine("3. Write 4 to 6 ultra-short Hinglish 'Pehli Line' micro-challenges (each 1 sentence, e.g., 'Organic Chemistry ka pehla reaction mechanism copy mein likho.') specifically tailored to their actual subjects and syllabus.")
                appendLine("4. Write a warm 1-sentence Hinglish summary of their study plan.")
            }

            val scaledBitmap = syllabusBitmap?.let { scaleDownBitmap(it, maxDimension = 1280) }

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        if (scaledBitmap != null) {
                            image(scaledBitmap)
                        }
                        text(promptText)
                    }
                )
            }

            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("AI model returned an empty response."))

            val json = JSONObject(jsonText)
            val subjectsArray = json.optJSONArray("subjects")
            val subjects = buildList {
                if (subjectsArray != null) {
                    for (i in 0 until subjectsArray.length()) {
                        val item = subjectsArray.optString(i).trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }.distinct()

            val topicsArray = json.optJSONArray("syllabusTopics")
            val topics = buildList {
                if (topicsArray != null) {
                    for (i in 0 until topicsArray.length()) {
                        val item = topicsArray.optString(i).trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }.distinct().take(40)

            val challengesArray = json.optJSONArray("customChallenges")
            val challenges = buildList {
                if (challengesArray != null) {
                    for (i in 0 until challengesArray.length()) {
                        val item = challengesArray.optString(i).trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }.distinct().take(20)

            val summary = json.optString("studySummary").trim()
                .ifEmpty { "$currentStudy ke liye tumhara Focus Garden ready hai!" }

            Result.success(
                GeneratedStudyPlan(
                    subjects = subjects,
                    syllabusTopics = topics,
                    customChallenges = challenges,
                    studySummary = summary
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Syllabus analysis timed out", e)
            Result.failure(IllegalStateException("Syllabus analysis timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze syllabus with Firebase AI Logic", e)
            Result.failure(e)
        }
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxOf(width, height).toFloat()
        val newW = (width * ratio).toInt().coerceAtLeast(1)
        val newH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private companion object {
        const val TAG = "SyllabusAiRepo"
    }
}
