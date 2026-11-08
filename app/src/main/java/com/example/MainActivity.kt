package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.GeminiStudyService
import com.example.data.repository.StudyRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PracticeScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.QuestionBankScreen
import com.example.ui.screens.ResultsAndAnalyticsScreen
import com.example.ui.screens.TestExamScreen
import com.example.ui.screens.UploadAndAiScreen
import com.example.ui.theme.SmartStudyTheme
import com.example.ui.viewmodel.SmartStudyViewModel
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class StudyDestination(
    val routeId: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Default.Home),
    UPLOAD("upload", "Upload", Icons.Default.CloudUpload),
    BANK("bank", "Bank", Icons.AutoMirrored.Filled.LibraryBooks),
    PRACTICE("practice", "Practice", Icons.Default.PlayCircleFilled),
    TEST("test", "Test", Icons.AutoMirrored.Filled.Assignment),
    RESULTS("results", "Results", Icons.Default.Analytics),
    PROFILE("profile", "Profile", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            SmartStudyTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartStudyRootApp(
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = { isDarkTheme = it }
                    )
                }
            }
        }
    }
}

@Composable
fun SmartStudyRootApp(
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedStudyApp(
                currentUserId = uid,
                userDisplayName = user.displayName ?: "Smart Scholar",
                userEmail = user.email.orEmpty(),
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
                onSignedOut = { currentUser = null }
            )
        }
    }
}

@Composable
fun AuthenticatedStudyApp(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SmartStudyViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SmartStudyViewModel(
                    repository = StudyRepository(db),
                    aiService = GeminiStudyService(),
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail
                )
            }
        }
    )

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val practiceSessions by viewModel.practiceSessions.collectAsStateWithLifecycle()
    val uploadedMaterials by viewModel.uploadedMaterials.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val practiceState by viewModel.practiceState.collectAsStateWithLifecycle()
    val examState by viewModel.examState.collectAsStateWithLifecycle()
    val selectedResultForDetail by viewModel.selectedResultForDetail.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var currentDestination by rememberSaveable { mutableStateOf(StudyDestination.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    // Handle system back button on secondary screens
    BackHandler(enabled = currentDestination != StudyDestination.HOME || selectedResultForDetail != null) {
        when {
            currentDestination == StudyDestination.RESULTS && selectedResultForDetail != null -> {
                viewModel.selectResultForAnalysis(null)
            }
            currentDestination == StudyDestination.PRACTICE && practiceState != null -> {
                viewModel.exitPractice()
            }
            currentDestination == StudyDestination.TEST && examState?.completedResult != null -> {
                viewModel.closeExamSession()
            }
            else -> {
                currentDestination = StudyDestination.HOME
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        StudyDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = currentDestination == dest,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${dest.routeId}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = "Smart Study",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) {
                        StudyDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = currentDestination == dest,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.routeId}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentDestination) {
                        StudyDestination.HOME -> DashboardScreen(
                            userProfile = userProfile,
                            userName = userDisplayName,
                            subjects = subjects,
                            questions = questions,
                            results = results,
                            practiceSessions = practiceSessions,
                            uploadedMaterials = uploadedMaterials,
                            onNavigateToUpload = { currentDestination = StudyDestination.UPLOAD },
                            onNavigateToPractice = { currentDestination = StudyDestination.PRACTICE },
                            onNavigateToTest = { currentDestination = StudyDestination.TEST },
                            onNavigateToQuestionBank = { currentDestination = StudyDestination.BANK },
                            onSelectResult = { res ->
                                viewModel.selectResultForAnalysis(res)
                                currentDestination = StudyDestination.RESULTS
                            },
                            onSeedStarterPack = { viewModel.seedStarterCurriculum() }
                        )

                        StudyDestination.UPLOAD -> UploadAndAiScreen(
                            aiState = aiState,
                            subjects = subjects,
                            chapters = chapters,
                            onAnalyzeUris = { uris, hint ->
                                viewModel.analyzeUploadedUris(context, uris, hint)
                            },
                            onGenerateByTopic = { topic, count, diff, subj, chap ->
                                viewModel.generateAiQuestions(topic, count, diff, subj, chap)
                            },
                            onUpdateDraft = { viewModel.updateExtractedDraft(it) },
                            onRemoveDraft = { viewModel.removeExtractedDraft(it) },
                            onSaveApprovedDrafts = { subj, chap, tag ->
                                viewModel.saveApprovedDraftsToDatabase(subj, chap, tag)
                            },
                            onClearDrafts = { viewModel.clearExtractedDrafts() }
                        )

                        StudyDestination.BANK -> QuestionBankScreen(
                            subjects = subjects,
                            chapters = chapters,
                            questions = questions,
                            onAddManualQuestion = { viewModel.addManualQuestion(it) },
                            onUpdateQuestion = { viewModel.updateExistingQuestion(it) },
                            onDeleteQuestion = { viewModel.deleteQuestion(it) },
                            onToggleBookmark = { viewModel.toggleBookmark(it) },
                            onCreateSubject = { name, desc -> viewModel.createSubject(name, desc) },
                            onCreateChapter = { sId, name, num -> viewModel.createChapter(sId, name, num) },
                            onPracticeChapter = { sId, cId ->
                                viewModel.startPractice(mode = "CHAPTER", subjectId = sId, chapterId = cId)
                                currentDestination = StudyDestination.PRACTICE
                            }
                        )

                        StudyDestination.PRACTICE -> PracticeScreen(
                            practiceState = practiceState,
                            questions = questions,
                            subjects = subjects,
                            chapters = chapters,
                            onStartPractice = { mode, sId, cId ->
                                viewModel.startPractice(mode = mode, subjectId = sId, chapterId = cId)
                            },
                            onSelectOption = { viewModel.selectPracticeOption(it) },
                            onJumpToQuestion = { viewModel.jumpToPracticeQuestion(it) },
                            onRestartPractice = { onlyWrong -> viewModel.restartCurrentPractice(onlyWrong) },
                            onToggleBookmark = { viewModel.toggleBookmark(it) },
                            onExitPractice = { viewModel.exitPractice() }
                        )

                        StudyDestination.TEST -> TestExamScreen(
                            examState = examState,
                            questions = questions,
                            subjects = subjects,
                            chapters = chapters,
                            onStartExam = { title, sId, sName, cId, cName, count, mins, timed ->
                                viewModel.startExam(title, sId, sName, cId, cName, count, mins, timed)
                            },
                            onSelectAnswer = { viewModel.selectExamAnswer(it) },
                            onToggleMarkForReview = { viewModel.toggleExamMarkForReview() },
                            onNavigateQuestion = { viewModel.navigateExamQuestion(it) },
                            onTickSecond = { viewModel.tickExamSecond() },
                            onSubmitExam = { viewModel.submitExam() },
                            onViewDetailedResult = { res ->
                                viewModel.selectResultForAnalysis(res)
                                viewModel.closeExamSession()
                                currentDestination = StudyDestination.RESULTS
                            },
                            onReviewWrongAnswers = { wrongPool ->
                                viewModel.closeExamSession()
                                viewModel.startPractice(mode = "INCORRECT", customQuestions = wrongPool)
                                currentDestination = StudyDestination.PRACTICE
                            },
                            onRetakeExam = { pool, title, mins, timed ->
                                viewModel.startExam(
                                    title = title,
                                    subjectId = "",
                                    subjectName = "Retake Exam",
                                    chapterId = "",
                                    chapterName = "",
                                    questionCount = pool.size,
                                    durationMinutes = mins,
                                    isTimerEnabled = timed,
                                    customPool = pool
                                )
                            },
                            onReturnToDashboard = {
                                viewModel.closeExamSession()
                                currentDestination = StudyDestination.HOME
                            }
                        )

                        StudyDestination.RESULTS -> ResultsAndAnalyticsScreen(
                            selectedResult = selectedResultForDetail,
                            results = results,
                            questions = questions,
                            subjects = subjects,
                            chapters = chapters,
                            practiceSessions = practiceSessions,
                            onSelectResult = { viewModel.selectResultForAnalysis(it) },
                            onRetryWrongQuestions = { wrongPool ->
                                viewModel.startPractice(mode = "INCORRECT", customQuestions = wrongPool)
                                currentDestination = StudyDestination.PRACTICE
                            }
                        )

                        StudyDestination.PROFILE -> ProfileSettingsScreen(
                            userProfile = userProfile,
                            fallbackName = userDisplayName,
                            fallbackEmail = userEmail,
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = onToggleDarkTheme,
                            onSeedSampleData = { viewModel.seedStarterCurriculum() },
                            onSignedOut = onSignedOut
                        )
                    }
                }
            }
        }
    }
}
