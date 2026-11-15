package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.FocusConstants
import com.example.data.model.UserProfile
import com.example.ui.StudySetupUiState
import com.example.ui.components.clearActiveTextInput

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudySetupScreen(
    userDisplayName: String,
    existingProfile: UserProfile,
    isEditingExisting: Boolean,
    setupState: StudySetupUiState,
    onCancelEdit: () -> Unit,
    onSubmitWithAi: (currentStudy: String, enteredSubjects: String, typedSyllabus: String, bitmap: Bitmap?) -> Unit,
    onSubmitManual: (currentStudy: String, enteredSubjects: String, typedSyllabus: String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isEditingExisting) {
        BackHandler {
            clearActiveTextInput(focusManager, textToolbar, keyboardController)
            onCancelEdit()
        }
    }
    var currentStudy by remember(existingProfile.currentStudy) {
        mutableStateOf(existingProfile.currentStudy)
    }
    var subjectDraft by remember { mutableStateOf("") }
    val subjectsList = remember(existingProfile.subjectsText) {
        mutableStateListOf<String>().apply {
            addAll(FocusConstants.parseSubjects(existingProfile.subjectsText))
        }
    }
    var typedSyllabus by remember(existingProfile.syllabusText) {
        mutableStateOf(existingProfile.syllabusText)
    }
    var syllabusBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageReadError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val loaded = decodeBitmapFromUri(context, uri)
            if (loaded != null) {
                syllabusBitmap = loaded
                imageReadError = null
                onClearError()
            } else {
                imageReadError = "Photo load nahi ho payi. Kripya dusri picture try karein."
            }
        }
    }

    val isAnalyzing = setupState is StudySetupUiState.Analyzing
    val combinedSubjects = buildString {
        append(subjectsList.joinToString(", "))
        if (subjectDraft.isNotBlank()) {
            if (isNotEmpty()) append(", ")
            append(subjectDraft.trim())
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isEditingExisting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        clearActiveTextInput(focusManager, textToolbar, keyboardController)
                        onCancelEdit()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Study Plan & Syllabus Update",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Welcome Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Namaste, $userDisplayName! 🌱",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Abhi tum kya padh rahi ho, kaun-kaun se subjects hain, aur apna syllabus (photo ya likh ke) share karo. Tumhara Focus Garden bilkul usi ke according banega!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Step 1: Current Study
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. Abhi tum kya padh rahi ho?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                OutlinedTextField(
                    value = currentStudy,
                    onValueChange = {
                        currentStudy = it
                        onClearError()
                    },
                    enabled = !isAnalyzing,
                    label = { Text("Class / Course / Exam") },
                    placeholder = { Text("Jaise: B.Tech CSE Sem 4, Class 12 Boards, NEET, UPSC") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_current_study_input")
                )
            }
        }

        // Step 2: Subjects
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "2. Ismein kaun-kaun se subjects hain?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = subjectDraft,
                        onValueChange = {
                            subjectDraft = it
                            onClearError()
                        },
                        enabled = !isAnalyzing,
                        label = { Text("Subject likho (comma se bhi add kar sakti ho)") },
                        placeholder = { Text("Jaise: Organic Chemistry, Physics") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("setup_subject_input")
                    )
                    OutlinedButton(
                        onClick = {
                            val newItems = FocusConstants.parseSubjects(subjectDraft)
                            newItems.forEach { item ->
                                if (subjectsList.none { it.equals(item, ignoreCase = true) }) {
                                    subjectsList.add(item)
                                }
                            }
                            subjectDraft = ""
                        },
                        enabled = !isAnalyzing && subjectDraft.isNotBlank(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("setup_add_subject_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add subject")
                    }
                }

                if (subjectsList.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        subjectsList.forEach { subject ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    if (!isAnalyzing) subjectsList.remove(subject)
                                },
                                label = { Text(subject) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove $subject",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Agar niche syllabus ki photo upload karogi, toh subjects khali bhi chhod sakti ho.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Step 3: Syllabus (Picture OR Typed)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "3. Syllabus share karo (Picture ya Likh ke)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Apne syllabus ki photo chuno ya chapters/topics niche likho.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        clearActiveTextInput(focusManager, textToolbar, keyboardController)
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isAnalyzing,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("setup_pick_syllabus_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (syllabusBitmap == null) {
                            "Syllabus ki Picture Chuno 📷"
                        } else {
                            "Dusri Picture Chuno 📷"
                        }
                    )
                }

                if (syllabusBitmap != null) {
                    val bmp = syllabusBitmap!!
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Selected syllabus image preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        TextButton(
                            onClick = { syllabusBitmap = null },
                            enabled = !isAnalyzing,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Photo Hatao")
                        }
                    }
                }

                if (imageReadError != null) {
                    Text(
                        text = imageReadError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = typedSyllabus,
                    onValueChange = {
                        typedSyllabus = it
                        onClearError()
                    },
                    enabled = !isAnalyzing,
                    label = { Text("Ya syllabus yahan likho / paste karo") },
                    placeholder = { Text("Unit 1: ..., Unit 2: ..., Important chapters...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_syllabus_text_input")
                )
            }
        }

        if (setupState is StudySetupUiState.Error) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = setupState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("setup_error_text")
                    )
                    if (combinedSubjects.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                clearActiveTextInput(focusManager, textToolbar, keyboardController)
                                onSubmitManual(currentStudy, combinedSubjects, typedSyllabus)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Subjects ke saath seedha save karein")
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                clearActiveTextInput(focusManager, textToolbar, keyboardController)
                onSubmitWithAi(currentStudy, combinedSubjects, typedSyllabus, syllabusBitmap)
            },
            enabled = !isAnalyzing,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("setup_submit_button")
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Syllabus samajh rahe hain... ✨")
            } else {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mera Study Garden Banao 🌱")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
