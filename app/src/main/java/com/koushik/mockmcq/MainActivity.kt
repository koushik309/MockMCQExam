@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.koushik.mockmcq

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.koushik.mockmcq.data.db.AnswerEntity
import com.koushik.mockmcq.data.db.ExamStatus
import com.koushik.mockmcq.data.db.ExamSummaryDb
import com.koushik.mockmcq.data.db.ParsingConfidence
import com.koushik.mockmcq.data.db.QuestionWithOptions
import com.koushik.mockmcq.data.repository.ExamSession
import com.koushik.mockmcq.presentation.AppScreen
import com.koushik.mockmcq.presentation.AppViewModel
import com.koushik.mockmcq.presentation.AnswerKeyImportUiState
import com.koushik.mockmcq.presentation.ImportUiState
import com.koushik.mockmcq.ui.theme.MockMcqTheme
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MockMcqApplication
        val previousCrash = app.consumeLastCrashReport()
        setContent {
            var crashReport by remember { mutableStateOf(previousCrash) }
            MockMcqTheme {
                val viewModel: AppViewModel = viewModel(factory = AppViewModel.factory(app.repository))
                MockMcqRoot(viewModel)
                CrashReportDialog(crashReport) { crashReport = null }
            }
        }
    }
}

@Composable
private fun MockMcqRoot(viewModel: AppViewModel) {
    val screen by viewModel.screen.collectAsState()
    val exams by viewModel.exams.collectAsState()
    val session by viewModel.session.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.importPdf(context, it, displayName(context, it))
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.pauseActiveExam()
                Lifecycle.Event.ON_START -> viewModel.resumeActiveExam()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.HOME -> HomeScreen(exams, onImport = { launcher.launch(arrayOf("application/pdf")) }, viewModel::openExam)
            AppScreen.PARSING_REVIEW -> SessionGate(session) { ParsingReviewScreen(it, viewModel::goHome, viewModel::startSelectedExam) }
            AppScreen.EXAM -> SessionGate(session) {
                ExamScreen(it, viewModel) { launcher.launch(arrayOf("application/pdf")) }
            }
            AppScreen.RESULT -> SessionGate(session) { ResultScreen(it, viewModel) }
            AppScreen.REVIEW -> SessionGate(session) { ReviewScreen(it, viewModel) }
        }
    }
    ImportDialogs(importState, viewModel::clearImportError)
    ActionErrorDialog(actionError, viewModel::clearActionError)
}

@Composable
private fun SessionGate(session: ExamSession?, content: @Composable (ExamSession) -> Unit) {
    if (session == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else content(session)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    exams: List<ExamSummaryDb>,
    onImport: () -> Unit,
    onOpen: (String, Boolean) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val visible = exams.filter { summary ->
        summary.exam.examName.contains(search, ignoreCase = true) && when (filter) {
            "Not Started" -> summary.exam.status == ExamStatus.NOT_STARTED
            "In Progress" -> summary.exam.status == ExamStatus.IN_PROGRESS || summary.exam.status == ExamStatus.PAUSED
            "Completed" -> summary.exam.status == ExamStatus.SUBMITTED
            else -> true
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("My Mock Tests") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onImport, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Import PDF") })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search tests") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Not Started", "In Progress", "Completed").forEach { value ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value) })
                }
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Assignment, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(if (exams.isEmpty()) "Import your first mock-exam PDF" else "No tests match this filter")
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.exam.examId }) { summary ->
                        ExamCard(summary) { onOpen(summary.exam.examId, true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamCard(summary: ExamSummaryDb, onOpen: () -> Unit) {
    val exam = summary.exam
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(exam.examName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${exam.totalQuestions} questions • ${summary.attemptedCount} answered • ${summary.markedCount} marked")
            Text(
                when (exam.status) {
                    ExamStatus.NOT_STARTED -> "Status: Not started"
                    ExamStatus.PAUSED -> "Status: Paused • Last question ${exam.currentQuestionIndex + 1}"
                    ExamStatus.IN_PROGRESS -> "Status: In progress • Last question ${exam.currentQuestionIndex + 1}"
                    else -> "Status: Completed"
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Button(onClick = onOpen, modifier = Modifier.align(Alignment.End)) {
                Text(if (exam.status == ExamStatus.SUBMITTED) "View Result" else if (exam.status == ExamStatus.NOT_STARTED) "Start Test" else "Resume Test")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParsingReviewScreen(session: ExamSession, onBack: () -> Unit, onStart: () -> Unit) {
    val high = session.questions.count { it.question.parsingConfidence == ParsingConfidence.HIGH }
    val medium = session.questions.count { it.question.parsingConfidence == ParsingConfidence.MEDIUM }
    val low = session.questions.count { it.question.parsingConfidence == ParsingConfidence.LOW }
    Scaffold(
        topBar = { TopAppBar(title = { Text("PDF Processing Complete") }, navigationIcon = { BackButton(onBack) }) },
        bottomBar = { Surface(shadowElevation = 8.dp) { Button(onClick = onStart, Modifier.fillMaxWidth().padding(16.dp)) { Text("Start Exam") } } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(session.exam.examName, style = MaterialTheme.typography.titleLarge)
                        Text("Questions detected: ${session.questions.size}")
                        Text("High confidence: $high • Medium: $medium • Review required: $low")
                        if (medium + low > 0) Text("Check uncertain items against the original PDF.", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            items(session.questions, key = { it.question.questionId }) { item ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Question ${item.question.questionNumber} • Page ${item.question.pageNumber}", fontWeight = FontWeight.Bold)
                        Text("${item.question.parsingConfidence} confidence", color = confidenceColor(item.question.parsingConfidence))
                        Text(item.question.questionText, maxLines = 3)
                        Text("${item.options.size} options detected", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamScreen(session: ExamSession, viewModel: AppViewModel, onReimport: () -> Unit) {
    val exam = session.exam
    val index = exam.currentQuestionIndex.coerceIn(0, (session.questions.size - 1).coerceAtLeast(0))
    val question = session.questions.getOrNull(index)
    if (question == null) {
        EmptyExamScreen(exam.examName, viewModel::goHome, onReimport)
        return
    }
    val answer = session.answers[question.question.questionId]
    var showPalette by remember { mutableStateOf(false) }
    var showSubmit by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    LaunchedEffect(exam.endAt) {
        while (exam.endAt != null && exam.status == ExamStatus.IN_PROGRESS) {
            now = System.currentTimeMillis()
            viewModel.submitIfExpired(now)
            delay(1_000)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(exam.examName, maxLines = 1); Text("Question ${index + 1} / ${session.questions.size}", style = MaterialTheme.typography.labelMedium) } },
                navigationIcon = { BackButton(viewModel::goHome) },
                actions = {
                    IconButton(onClick = { openOriginalPdf(context, exam.pdfUri) }) {
                        Icon(Icons.Default.PictureAsPdf, "Open original PDF")
                    }
                    exam.endAt?.let { Text(formatRemaining(it - now), Modifier.padding(end = 12.dp), fontWeight = FontWeight.Bold) }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.navigateTo(index - 1) }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
                        Button(onClick = { viewModel.navigateTo(index + 1) }, enabled = index < session.questions.lastIndex, modifier = Modifier.weight(1f)) { Text("Save & Next") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = viewModel::clearResponse) { Text("Clear") }
                        TextButton(onClick = viewModel::toggleMarked) { Text(if (answer?.isMarkedForReview == true) "Unmark" else "Mark for review") }
                        TextButton(onClick = { showPalette = true }) { Text("Palette") }
                        TextButton(onClick = { showSubmit = true }) { Text("Submit") }
                    }
                }
            }
        },
    ) { padding ->
        QuestionContent(question, answer, session.answerKeys[question.question.questionId]?.correctOptionLabel, false, viewModel::selectOption, Modifier.padding(padding))
    }
    if (showPalette) QuestionPalette(session, onDismiss = { showPalette = false }) { viewModel.navigateTo(it); showPalette = false }
    if (showSubmit) SubmitDialog(session, onDismiss = { showSubmit = false }, onSubmit = viewModel::submit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyExamScreen(examName: String, onBack: () -> Unit, onReimport: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(examName) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Warning, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Text("No questions are stored", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("This saved test is incomplete. Import the question PDF again to create a fresh test.")
                    Button(onClick = onReimport, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Text("  Import Question PDF")
                    }
                    TextButton(onClick = onBack) { Text("Return to My Mock Tests") }
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(
    question: QuestionWithOptions,
    answer: AnswerEntity?,
    correctLabel: String?,
    reviewMode: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${question.question.questionNumber}.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            AssistChip(onClick = {}, label = { Text("Page ${question.question.pageNumber} • ${question.question.parsingConfidence}") })
        }
        Text(question.question.questionText, style = MaterialTheme.typography.bodyLarge)
        question.options.sortedBy { it.displayOrder }.forEach { option ->
            val selected = answer?.selectedOptionId == option.optionId
            val correct = reviewMode && correctLabel == option.optionLabel
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().selectable(enabled = !reviewMode, selected = selected, onClick = { onSelect(option.optionId) }),
                border = BorderStroke(if (selected || correct) 2.dp else 1.dp, if (correct) Color(0xFF2E7D32) else if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = if (reviewMode) null else { { onSelect(option.optionId) } })
                    Text("${option.optionLabel}. ${option.optionText}", Modifier.padding(start = 8.dp).weight(1f))
                    if (correct) Icon(Icons.Default.CheckCircle, "Correct answer", tint = Color(0xFF2E7D32))
                }
            }
        }
        if (answer?.isMarkedForReview == true) Text("★ Marked for review", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        if (!reviewMode && answer?.selectedOptionId != null) {
            Text("✓ Answer saved", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(110.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionPalette(session: ExamSession, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Question palette", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
        Text("✓ answered  ★ marked  • visited  ○ not visited", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(56.dp), modifier = Modifier.fillMaxWidth().height(360.dp), contentPadding = PaddingValues(16.dp)) {
            itemsIndexed(session.questions) { index, question ->
                val answer = session.answers[question.question.questionId]
                val symbol = when {
                    answer?.isMarkedForReview == true -> "★"
                    answer?.selectedOptionId != null -> "✓"
                    answer?.isVisited == true -> "•"
                    else -> "○"
                }
                OutlinedCard(
                    modifier = Modifier.padding(3.dp).clickable { onSelect(index) }.semantics { contentDescription = "Question ${index + 1}, $symbol" },
                ) { Box(Modifier.fillMaxWidth().padding(vertical = 9.dp), contentAlignment = Alignment.Center) { Text("${index + 1}$symbol", fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

@Composable
private fun SubmitDialog(session: ExamSession, onDismiss: () -> Unit, onSubmit: () -> Unit) {
    val answered = session.answers.values.count { it.selectedOptionId != null }
    val marked = session.answers.values.count { it.isMarkedForReview }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null) },
        title = { Text("Submit test?") },
        text = { Text("Total: ${session.questions.size}\nAnswered: $answered\nUnanswered: ${session.questions.size - answered}\nMarked: $marked\n\nYour saved responses will be preserved.") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Continue Exam") } },
        confirmButton = { Button(onClick = onSubmit) { Text("Submit Test") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultScreen(session: ExamSession, viewModel: AppViewModel) {
    val result = viewModel.calculateResult(session)
    val hasKey = session.answerKeys.isNotEmpty()
    val answerKeyState by viewModel.answerKeyImportState.collectAsState()
    val context = LocalContext.current
    val answerPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.importAnswerKeyPdf(context, it)
        }
    }
    var keyText by remember { mutableStateOf("") }
    var importedCount by remember { mutableIntStateOf(0) }
    Scaffold(topBar = { TopAppBar(title = { Text("Result") }, navigationIcon = { BackButton(viewModel::goHome) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(session.exam.examName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (hasKey) Text(String.format(Locale.US, "Score %.2f / %.2f", result.score, result.maximumScore), style = MaterialTheme.typography.headlineMedium)
                    else Text("Responses submitted", style = MaterialTheme.typography.headlineMedium)
                    Text("Attempted: ${result.attempted} • Unanswered: ${result.unanswered}")
                    if (hasKey) {
                        Text("Correct: ${result.correct} • Incorrect: ${result.incorrect}")
                        Text("Scored questions: ${result.scoredQuestions} • Key missing: ${result.missingKeys}")
                        Text(String.format(Locale.US, "Accuracy: %.1f%% • Negative marks: %.2f", result.accuracyPercent, result.negativeMarksLost))
                    } else Text("Add an answer key to calculate score and accuracy.")
                }
            }
            Button(
                onClick = { answerPdfLauncher.launch(arrayOf("application/pdf")) },
                enabled = !answerKeyState.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PictureAsPdf, null)
                Text("  Import Answer PDF")
            }
            if (answerKeyState.importedCount > 0) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            "Imported ${answerKeyState.importedCount} of ${answerKeyState.totalQuestions} explicit answers",
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (answerKeyState.missingQuestionNumbers.isNotEmpty()) {
                            Text(
                                "No explicit answer in the PDF for questions: ${answerKeyState.missingQuestionNumbers.joinToString()}",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Text("Or paste an answer key", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                modifier = Modifier.fillMaxWidth().height(170.dp),
                label = { Text("Answer key (for example: 1-C)") },
                supportingText = { if (importedCount > 0) Text("Imported $importedCount answers") },
            )
            Button(onClick = { importedCount = viewModel.importAnswerKey(keyText) }, enabled = keyText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Import Pasted Key") }
            OutlinedButton(onClick = viewModel::showReview, modifier = Modifier.fillMaxWidth()) { Text("Review Test") }
            TextButton(onClick = viewModel::goHome, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Home, null); Text("  My Mock Tests") }
        }
    }
    AnswerKeyImportDialogs(answerKeyState, viewModel::clearAnswerKeyImportError)
}

private enum class ReviewFilter { ALL, CORRECT, INCORRECT, UNANSWERED, MARKED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewScreen(session: ExamSession, viewModel: AppViewModel) {
    var filter by remember { mutableStateOf(ReviewFilter.ALL) }
    val filtered = session.questions.filter { question ->
        val answer = session.answers[question.question.questionId]
        val selected = question.options.firstOrNull { it.optionId == answer?.selectedOptionId }?.optionLabel
        val correct = session.answerKeys[question.question.questionId]?.correctOptionLabel
        when (filter) {
            ReviewFilter.ALL -> true
            ReviewFilter.CORRECT -> selected != null && correct != null && selected == correct
            ReviewFilter.INCORRECT -> selected != null && correct != null && selected != correct
            ReviewFilter.UNANSWERED -> selected == null
            ReviewFilter.MARKED -> answer?.isMarkedForReview == true
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Review Test") }, navigationIcon = { BackButton(viewModel::showResult) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReviewFilter.entries.forEach { value ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { filter = value },
                        label = { Text(value.name.lowercase().replaceFirstChar { it.titlecase() }) },
                    )
                }
            }
            LazyColumn(contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.question.questionId }) { question -> ReviewQuestionCard(question, session) }
            }
        }
    }
}

@Composable
private fun ReviewQuestionCard(question: QuestionWithOptions, session: ExamSession) {
    val answer = session.answers[question.question.questionId]
    val selected = question.options.firstOrNull { it.optionId == answer?.selectedOptionId }?.optionLabel
    val correct = session.answerKeys[question.question.questionId]?.correctOptionLabel
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Question ${question.question.questionNumber}", fontWeight = FontWeight.Bold)
            Text(question.question.questionText)
            question.options.sortedBy { it.displayOrder }.forEach { option ->
                val prefix = when {
                    option.optionLabel == correct -> "✓"
                    option.optionLabel == selected -> "Your answer →"
                    else -> ""
                }
                Text("$prefix ${option.optionLabel}. ${option.optionText}".trim())
            }
            HorizontalDivider()
            Text("Your answer: ${selected ?: "Unanswered"} • Correct: ${correct ?: "Not provided"}")
            if (answer?.isMarkedForReview == true) Text("★ Marked for review")
        }
    }
}

@Composable
private fun ImportDialogs(state: ImportUiState, onDismissError: () -> Unit) {
    if (state.isImporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Processing PDF…") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(if (state.totalPages > 0) "Page ${state.currentPage} / ${state.totalPages}" else "Opening document") } },
            confirmButton = {},
        )
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("PDF could not be imported") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun ActionErrorDialog(message: String?, onDismiss: () -> Unit) {
    message?.let {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Exam could not be opened") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}

@Composable
private fun CrashReportDialog(report: String?, onDismiss: () -> Unit) {
    report?.let {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("The previous session crashed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Please take a screenshot of the details below if the problem continues.")
                    Text(it.take(3_500), Modifier.height(260.dp).verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
        )
    }
}

@Composable
private fun AnswerKeyImportDialogs(state: AnswerKeyImportUiState, onDismissError: () -> Unit) {
    if (state.isImporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Processing answer PDF…") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(if (state.totalPages > 0) "Page ${state.currentPage} / ${state.totalPages}" else "Opening document")
                }
            },
            confirmButton = {},
        )
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Answer PDF could not be imported") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
}

private fun formatRemaining(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1_000)
    return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
}

private fun confidenceColor(confidence: String): Color = when (confidence) {
    ParsingConfidence.HIGH -> Color(0xFF2E7D32)
    ParsingConfidence.MEDIUM -> Color(0xFFF57C00)
    else -> Color(0xFFC62828)
}

private fun displayName(context: android.content.Context, uri: Uri): String {
    var name = "Imported Mock Test.pdf"
    val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
    return name
}

private fun openOriginalPdf(context: android.content.Context, uriText: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uriText), "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "No PDF viewer is available on this device.", Toast.LENGTH_LONG).show()
    }
}
