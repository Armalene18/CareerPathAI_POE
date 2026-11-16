
package com.example.careerpathai

import androidx.compose.foundation.clickable
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.careerpathai.ui.theme.CareerPathAITheme
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private const val TAG = "CareerPathAI"

// ============================================================
// CAREERPATH AI DESIGN SYSTEM
// ============================================================

private val CareerBlue = Color(0xFF3B82F6)
private val CareerPurple = Color(0xFF8B5CF6)
private val CareerCyan = Color(0xFF06B6D4)
private val CareerGreen = Color(0xFF10B981)
private val CareerRed = Color(0xFFEF4444)

private val auth: FirebaseAuth
    get() = FirebaseAuth.getInstance()

private val db: FirebaseFirestore
    get() = FirebaseFirestore.getInstance()

private data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val title: Color,
    val text: Color,
    val secondary: Color,
    val border: Color
)

private fun appColors(dark: Boolean) = AppColors(
    background = if (dark) Color(0xFF0B1120) else Color(0xFFF4F7FC),
    surface = if (dark) Color(0xFF172238) else Color.White,
    surfaceAlt = if (dark) Color(0xFF202E46) else Color(0xFFEEF3FF),
    title = if (dark) Color(0xFFF8FAFC) else Color(0xFF14213D),
    text = if (dark) Color(0xFFE2E8F0) else Color(0xFF334155),
    secondary = if (dark) Color(0xFF9CAFC5) else Color(0xFF718096),
    border = if (dark) Color(0xFF334155) else Color(0xFFE2E8F0)
)

private val brandGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF2563EB),
        Color(0xFF6366F1),
        Color(0xFF9333EA)
    )
)

private val backgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF2563EB),
        Color(0xFF4F46E5),
        Color(0xFF7C3AED)
    )
)

// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            var darkMode by rememberSaveable {
                mutableStateOf(false)
            }

            CareerPathAITheme {
                CareerPathAIApp(
                    darkMode = darkMode,
                    onDarkModeChange = { darkMode = it }
                )
            }
        }
    }
}

// ============================================================
// MAIN APPLICATION NAVIGATION
// ============================================================

@Composable
fun CareerPathAIApp(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {

    var currentScreen by rememberSaveable {
        mutableStateOf(
            if (auth.currentUser != null) "dashboard" else "login"
        )
    }

    var careerResults by remember {
        mutableStateOf(emptyList<Pair<String, Int>>())
    }

    var assessmentSaveMessage by remember {
        mutableStateOf("")
    }

    var assessmentSaved by remember {
        mutableStateOf(false)
    }

    // Load the user's saved appearance preference.
    LaunchedEffect(auth.currentUser?.uid) {

        val user = auth.currentUser

        if (user != null) {
            db.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->

                    if (document.exists()) {
                        onDarkModeChange(
                            document.getBoolean("darkMode") ?: false
                        )
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "Could not load appearance preference", it)
                }
        }
    }

    val colors = appColors(darkMode)

    MaterialTheme(
        colorScheme = if (darkMode) {
            darkColorScheme(
                primary = CareerBlue,
                secondary = CareerPurple,
                background = colors.background,
                surface = colors.surface,
                onBackground = colors.title,
                onSurface = colors.title
            )
        } else {
            lightColorScheme(
                primary = CareerBlue,
                secondary = CareerPurple,
                background = colors.background,
                surface = colors.surface,
                onBackground = colors.title,
                onSurface = colors.title
            )
        }
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background
        ) {

            when (currentScreen) {

                "login" -> LoginScreen(
                    darkMode = darkMode,
                    onLoginSuccess = {
                        currentScreen = "dashboard"
                    },
                    onRegisterClick = {
                        currentScreen = "register"
                    }
                )

                "register" -> RegisterScreen(
                    darkMode = darkMode,
                    onRegistrationSuccess = {
                        currentScreen = "dashboard"
                    },
                    onBackToLogin = {
                        currentScreen = "login"
                    }
                )
                "dashboard" -> DashboardScreen(
                    darkMode = darkMode,
                    onStartAssessment = {
                        currentScreen = "assessment"
                    },
                    onSettingsClick = {
                        currentScreen = "settings"
                    },
                    onHistoryClick = {
                        currentScreen = "history"
                    },
                    onSkillGapClick = {
                        currentScreen = "skillgap"
                    },
                    onLearningRoadmapClick = {
                        currentScreen = "roadmap"
                    }
                )


                "assessment" -> CareerAssessmentScreen(
                    darkMode = darkMode,
                    onFinished = { answers ->

                        val calculatedResults =
                            calculateCareerResults(answers)

                        careerResults = calculatedResults
                        assessmentSaved = false
                        assessmentSaveMessage = "Saving your assessment..."

                        currentScreen = "saving"

                        saveAssessmentToFirestore(
                            answers,
                            calculatedResults
                        ) { success, message ->

                            assessmentSaved = success
                            assessmentSaveMessage = message

                            if (success) {
                                Log.d(TAG, message)
                            } else {
                                Log.e(TAG, message)
                            }

                            currentScreen = "results"
                        }
                    }
                )

                "saving" -> SavingScreen(darkMode)

                "results" -> CareerResultsScreen(
                    results = careerResults,
                    darkMode = darkMode,
                    saved = assessmentSaved,
                    saveMessage = assessmentSaveMessage,
                    onBackToDashboard = {
                        currentScreen = "dashboard"
                    }
                )

                "history" -> AssessmentHistoryScreen(
                    darkMode = darkMode,
                    onBack = {
                        currentScreen = "dashboard"
                    }
                )

                "settings" -> SettingsScreen(
                    darkMode = darkMode,
                    onDarkModeChange = onDarkModeChange,
                    onLogout = {
                        auth.signOut()
                        currentScreen = "login"
                    },
                    onBack = {
                        currentScreen = "dashboard"
                    }
                )
            }
        }
    }
}

// ============================================================
// REUSABLE DESIGN COMPONENTS
// ============================================================

@Composable
fun AppPage(
    darkMode: Boolean,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {

    val colors = appColors(darkMode)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .then(
                if (scrollable) {
                    Modifier.verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        content = content
    )
}

@Composable
fun BrandLogo(
    size: Int = 58,
    darkMode: Boolean = false
) {

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(brandGradient),
        contentAlignment = Alignment.Center
    ) {

        Text(
            "CP",
            color = Color.White,
            fontSize = (size / 2.8).sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String,
    darkMode: Boolean
) {

    val colors = appColors(darkMode)

    Column {

        Text(
            title,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.title,
            lineHeight = 34.sp
        )

        Spacer(Modifier.height(7.dp))

        Text(
            subtitle,
            fontSize = 14.sp,
            color = colors.secondary,
            lineHeight = 21.sp
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(55.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CareerBlue,
            disabledContainerColor = Color(0xFF94A3B8)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp
        )
    ) {

        Text(
            text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun AppCard(
    darkMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {

    val colors = appColors(darkMode)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (darkMode) 0.dp else 3.dp
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.border
        )
    ) {

        Column(
            modifier = Modifier.padding(19.dp),
            content = content
        )
    }
}

@Composable
fun StatusMessage(
    message: String,
    success: Boolean,
    darkMode: Boolean
) {

    if (message.isNotBlank()) {

        val colors = appColors(darkMode)

        Surface(
            color = if (success) {
                Color(0xFFDCFCE7).copy(alpha = if (darkMode) 0.15f else 1f)
            } else {
                Color(0xFFFEE2E2).copy(alpha = if (darkMode) 0.15f else 1f)
            },
            shape = RoundedCornerShape(12.dp)
        ) {

            Text(
                message,
                modifier = Modifier.padding(12.dp),
                fontSize = 13.sp,
                color = if (success) CareerGreen else CareerRed
            )
        }
    }
}

@Composable
fun BackButton(
    darkMode: Boolean,
    onClick: () -> Unit
) {

    val colors = appColors(darkMode)

    TextButton(onClick = onClick) {

        Text(
            "← Back",
            color = colors.secondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================
// SAVING SCREEN
// ============================================================

@Composable
fun SavingScreen(darkMode: Boolean) {

    val colors = appColors(darkMode)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            BrandLogo(75)

            Spacer(Modifier.height(28.dp))

            CircularProgressIndicator(
                color = CareerBlue,
                strokeWidth = 4.dp
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Analysing your career profile",
                color = colors.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "Please wait while we securely save your results.",
                color = colors.secondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================
// FIRESTORE ASSESSMENT STORAGE
// ============================================================


private fun saveAssessmentToFirestore(
    answers: List<String>,
    results: List<Pair<String, Int>>,
    onComplete: (Boolean, String) -> Unit
) {
    val user = auth.currentUser

    if (user == null) {
        onComplete(false, "No authenticated user found. Please log in.")
        return
    }

    if (answers.isEmpty() || results.isEmpty()) {
        onComplete(false, "Assessment data is incomplete.")
        return
    }

    val assessmentRef = db.collection("users")
        .document(user.uid)
        .collection("assessments")
        .document()

    val assessmentData = hashMapOf<String, Any>(
        "assessmentId" to assessmentRef.id,
        "uid" to user.uid,
        "email" to (user.email ?: ""),
        "answers" to answers,
        "results" to results.map { result ->
            mapOf(
                "career" to result.first,
                "score" to result.second
            )
        },
        "resultCount" to results.size,
        "assessmentVersion" to "1.0",
        "completedAt" to FieldValue.serverTimestamp()
    )

    assessmentRef.set(assessmentData)
        .addOnSuccessListener {
            Log.d(TAG, "Assessment saved: ${assessmentRef.id}")

            onComplete(
                true,
                "Your assessment was successfully saved!"
            )
        }
        .addOnFailureListener { exception ->
            Log.e(TAG, "Assessment saving failed", exception)

            onComplete(
                false,
                "Assessment could not be saved. Please check your internet connection and try again."
            )
        }
}


// ============================================================
// LOGIN SCREEN
// ============================================================

@Composable
fun LoginScreen(
    darkMode: Boolean,
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit
) {

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val colors = appColors(darkMode)

    AppPage(darkMode) {

        Spacer(Modifier.height(35.dp))

        BrandLogo(75)

        Spacer(Modifier.height(30.dp))

        Text(
            "Welcome back.",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.title
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Your future starts with the right career decision.",
            fontSize = 15.sp,
            color = colors.secondary
        )

        Spacer(Modifier.height(34.dp))

        AppCard(darkMode) {

            Text(
                "Sign in to your account",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = colors.title
            )

            Spacer(Modifier.height(22.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = ""
                    successMessage = ""
                },
                label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(13.dp)
            )

            Spacer(Modifier.height(15.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(13.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {

                TextButton(
                    onClick = {

                        when {
                            email.isBlank() ->
                                errorMessage = "Enter your email address."

                            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                                errorMessage = "Enter a valid email address."

                            else -> {

                                isLoading = true
                                errorMessage = ""

                                auth.sendPasswordResetEmail(email.trim())
                                    .addOnCompleteListener { task ->

                                        isLoading = false

                                        if (task.isSuccessful) {
                                            successMessage =
                                                "Password reset instructions have been sent."
                                        } else {
                                            errorMessage =
                                                task.exception?.localizedMessage
                                                    ?: "Unable to send reset email."
                                        }
                                    }
                            }
                        }
                    },
                    enabled = !isLoading
                ) {

                    Text(
                        "Forgot password?",
                        color = CareerBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            StatusMessage(errorMessage, false, darkMode)
            StatusMessage(successMessage, true, darkMode)

            Spacer(Modifier.height(14.dp))

            GradientButton(
                text = if (isLoading) "PLEASE WAIT..." else "SIGN IN",
                enabled = !isLoading
            ) {

                when {
                    email.isBlank() ->
                        errorMessage = "Please enter your email."

                    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                        errorMessage = "Please enter a valid email."

                    password.isBlank() ->
                        errorMessage = "Please enter your password."

                    else -> {

                        isLoading = true
                        errorMessage = ""
                        successMessage = ""

                        auth.signInWithEmailAndPassword(
                            email.trim(),
                            password
                        ).addOnCompleteListener { task ->

                            isLoading = false

                            if (task.isSuccessful) {
                                onLoginSuccess()
                            } else {
                                Log.e(TAG, "Login failed", task.exception)

                                errorMessage =
                                    task.exception?.localizedMessage
                                        ?: "Login failed. Please try again."
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "Your career journey, all in one place.",
                modifier = Modifier.fillMaxWidth(),
                color = colors.secondary,
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        TextButton(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                "New to CareerPath AI? Create an account",
                color = CareerBlue,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ============================================================
// REGISTRATION SCREEN
// ============================================================

@Composable
fun RegisterScreen(
    darkMode: Boolean,
    onRegistrationSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {

    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val colors = appColors(darkMode)

    AppPage(darkMode) {

        BackButton(darkMode, onBackToLogin)

        Spacer(Modifier.height(15.dp))

        BrandLogo(65)

        Spacer(Modifier.height(22.dp))

        Text(
            "Create your account",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.title
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Take the first step towards discovering your future.",
            color = colors.secondary,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(25.dp))

        AppCard(darkMode) {

            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    errorMessage = ""
                },
                label = { Text("Full name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(13.dp)
            )

            Spacer(Modifier.height(13.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = ""
                },
                label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(13.dp)
            )

            Spacer(Modifier.height(13.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                label = { Text("Password (minimum 8 characters)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(13.dp)
            )

            Spacer(Modifier.height(13.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = ""
                },
                label = { Text("Confirm password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(13.dp)
            )

            Spacer(Modifier.height(15.dp))

            StatusMessage(errorMessage, false, darkMode)

            GradientButton(
                text = if (isLoading) "CREATING ACCOUNT..." else "CREATE ACCOUNT",
                enabled = !isLoading
            ) {

                when {
                    fullName.isBlank() ->
                        errorMessage = "Please enter your full name."

                    email.isBlank() ->
                        errorMessage = "Please enter your email."

                    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                        errorMessage = "Enter a valid email address."

                    password.length < 8 ->
                        errorMessage = "Password must contain at least 8 characters."

                    password != confirmPassword ->
                        errorMessage = "Passwords do not match."

                    else -> {

                        isLoading = true
                        errorMessage = ""

                        auth.createUserWithEmailAndPassword(
                            email.trim(),
                            password
                        ).addOnCompleteListener { task ->

                            if (!task.isSuccessful) {

                                isLoading = false

                                errorMessage =
                                    task.exception?.localizedMessage
                                        ?: "Registration failed."

                                return@addOnCompleteListener
                            }

                            val user = auth.currentUser

                            if (user == null) {

                                isLoading = false
                                errorMessage = "Unable to load your account."

                                return@addOnCompleteListener
                            }

                            val profile = hashMapOf<String, Any>(
                                "uid" to user.uid,
                                "fullName" to fullName.trim(),
                                "email" to email.trim(),
                                "createdAt" to FieldValue.serverTimestamp(),
                                "darkMode" to false,
                                "notificationsEnabled" to true
                            )

                            val update =
                                UserProfileChangeRequest.Builder()
                                    .setDisplayName(fullName.trim())
                                    .build()

                            user.updateProfile(update)
                                .addOnCompleteListener { profileTask ->

                                    if (!profileTask.isSuccessful) {
                                        Log.e(
                                            TAG,
                                            "Display name update failed",
                                            profileTask.exception
                                        )
                                    }

                                    db.collection("users")
                                        .document(user.uid)
                                        .set(profile)
                                        .addOnSuccessListener {

                                            isLoading = false
                                            onRegistrationSuccess()
                                        }
                                        .addOnFailureListener { exception ->

                                            isLoading = false

                                            errorMessage =
                                                "Account created, but profile saving failed: ${exception.localizedMessage}"

                                            Log.e(TAG, "Profile save failed", exception)
                                        }
                                }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        TextButton(
            onClick = onBackToLogin,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                "Already have an account? Sign in",
                color = CareerBlue
            )
        }
    }
}

// ============================================================
// DASHBOARD SCREEN
// ============================================================

@Composable
fun DashboardScreen(
    darkMode: Boolean,
    onStartAssessment: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSkillGapClick: () -> Unit,
    onLearningRoadmapClick: () -> Unit

) {

    val colors = appColors(darkMode)
    val user = auth.currentUser

    val displayName = user?.displayName
        ?.substringBefore(" ")
        ?: "there"

    AppPage(darkMode) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                BrandLogo(48)

                Spacer(Modifier.width(12.dp))

                Column {

                    Text(
                        "CareerPath AI",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.title
                    )

                    Text(
                        "YOUR CAREER COMPANION",
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        color = colors.secondary
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = colors.surfaceAlt,
                modifier = Modifier
                    .size(46.dp)
                    .clickable { onSettingsClick() }
            ) {

                Box(contentAlignment = Alignment.Center) {

                    Text(
                        "⚙",
                        fontSize = 23.sp,
                        color = colors.title
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        Text(
            "Hello, $displayName 👋",
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.title
        )

        Spacer(Modifier.height(7.dp))

        Text(
            "Ready to discover what you're capable of?",
            fontSize = 14.sp,
            color = colors.secondary
        )

        Spacer(Modifier.height(25.dp))

        // FEATURE HERO CARD

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(brandGradient)
                .padding(23.dp)
        ) {

            Column {

                Text(
                    "YOUR FUTURE STARTS HERE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.3.sp
                )

                Spacer(Modifier.height(15.dp))

                Text(
                    "Discover a career that matches YOU.",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 34.sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Explore your interests, identify your strengths and discover possible career opportunities.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 21.sp
                )

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = onStartAssessment,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF4338CA)
                    )
                ) {

                    Text(
                        "START YOUR ASSESSMENT  →",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "Explore your platform",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.title
        )

        Spacer(Modifier.height(15.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            DashboardFeatureCard(
                modifier = Modifier.weight(1f),
                emoji = "📊",
                title = "Career Assessment",
                description = "Explore your interests and strengths",
                darkMode = darkMode,
                onClick = onStartAssessment
            )

            DashboardFeatureCard(
                modifier = Modifier.weight(1f),
                emoji = "📁",
                title = "My History",
                description = "Review previous assessments",
                darkMode = darkMode,
                onClick = onHistoryClick
            )
        }

        Spacer(Modifier.height(23.dp))

        AppCard(darkMode) {

            Text(
                "How CareerPath AI helps you",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.title
            )

            Spacer(Modifier.height(15.dp))

            FeatureLine("✓", "Understand your interests", darkMode)
            FeatureLine("✓", "Explore potential career paths", darkMode)
            FeatureLine("✓", "Compare your career matches", darkMode)
            FeatureLine("✓", "Keep track of your progress", darkMode)
        }

        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {

            Text(
                "SETTINGS & PREFERENCES",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "CareerPath AI • Discover. Explore. Achieve.",
            modifier = Modifier.fillMaxWidth(),
            color = colors.secondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DashboardFeatureCard(
    modifier: Modifier,
    emoji: String,
    title: String,
    description: String,
    darkMode: Boolean,
    onClick: () -> Unit
) {

    val colors = appColors(darkMode)

    Card(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.border
        )
    ) {

        Column(
            modifier = Modifier.padding(15.dp)
        ) {

            Text(emoji, fontSize = 27.sp)

            Spacer(Modifier.height(12.dp))

            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.title
            )

            Spacer(Modifier.height(6.dp))

            Text(
                description,
                fontSize = 12.sp,
                color = colors.secondary,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
fun FeatureLine(
    icon: String,
    text: String,
    darkMode: Boolean
) {

    val colors = appColors(darkMode)

    Row(
        modifier = Modifier.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            icon,
            color = CareerGreen,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text,
            fontSize = 14.sp,
            color = colors.text
        )
    }
}

// ============================================================
// SETTINGS SCREEN
// ============================================================

@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {

    var notificationsEnabled by remember {
        mutableStateOf(true)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var saveMessage by remember {
        mutableStateOf("")
    }

    val colors = appColors(darkMode)
    val user = auth.currentUser

    LaunchedEffect(user?.uid) {

        if (user == null) {
            isLoading = false
            saveMessage = "Please log in again."
            return@LaunchedEffect
        }

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->

                if (document.exists()) {

                    notificationsEnabled =
                        document.getBoolean("notificationsEnabled") ?: true

                    onDarkModeChange(
                        document.getBoolean("darkMode") ?: false
                    )
                }

                isLoading = false
            }
            .addOnFailureListener { exception ->

                Log.e(TAG, "Preference loading failed", exception)

                saveMessage = "Unable to load saved preferences."
                isLoading = false
            }
    }

    fun savePreference(field: String, value: Boolean) {

        if (user == null) {
            saveMessage = "Please log in again."
            return
        }

        db.collection("users")
            .document(user.uid)
            .update(field, value)
            .addOnSuccessListener {

                saveMessage = "Your preference has been saved."
            }
            .addOnFailureListener { exception ->

                Log.e(TAG, "Preference saving failed", exception)

                saveMessage =
                    "Could not save preference: ${exception.localizedMessage}"
            }
    }

    AppPage(darkMode, scrollable = false) {

        BackButton(darkMode, onBack)

        Spacer(Modifier.height(10.dp))

        SectionTitle(
            "Settings",
            "Personalise your CareerPath AI experience.",
            darkMode
        )

        Spacer(Modifier.height(25.dp))

        if (isLoading) {

            CircularProgressIndicator(color = CareerBlue)

        } else {

            AppCard(darkMode) {

                Text(
                    "Notifications",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.title
                )

                Spacer(Modifier.height(7.dp))

                Text(
                    "Control your notification preferences.",
                    fontSize = 13.sp,
                    color = colors.secondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 17.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(Modifier.weight(1f)) {

                        Text(
                            "Career notifications",
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text
                        )

                        Text(
                            "Receive career-related updates",
                            fontSize = 12.sp,
                            color = colors.secondary
                        )
                    }

                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {

                            notificationsEnabled = it
                            savePreference("notificationsEnabled", it)
                        }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            AppCard(darkMode) {

                Text(
                    "Appearance",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.title
                )

                Spacer(Modifier.height(7.dp))

                Text(
                    "Choose how the application looks.",
                    fontSize = 13.sp,
                    color = colors.secondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 17.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(Modifier.weight(1f)) {

                        Text(
                            "Dark Mode",
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text
                        )

                        Text(
                            "Use a darker application appearance",
                            fontSize = 12.sp,
                            color = colors.secondary
                        )
                    }

                    Switch(
                        checked = darkMode,
                        onCheckedChange = {

                            onDarkModeChange(it)
                            savePreference("darkMode", it)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(15.dp))

        Text(
            saveMessage,
            color = CareerGreen,
            fontSize = 13.sp
        )

        Spacer(Modifier.weight(1f))

        AppCard(darkMode) {

            Text(
                "Account",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.title
            )

            Spacer(Modifier.height(7.dp))

            Text(
                user?.email ?: "No account information",
                fontSize = 13.sp,
                color = colors.secondary
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CareerRed
                )
            ) {

                Text(
                    "SIGN OUT",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "CareerPath AI • Version 1.0",
            modifier = Modifier.fillMaxWidth(),
            color = colors.secondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ============================================================
// CAREER ASSESSMENT SCREEN
// ============================================================

@Composable
fun CareerAssessmentScreen(
    darkMode: Boolean,
    onFinished: (List<String>) -> Unit
) {

    val colors = appColors(darkMode)

    var currentQuestion by rememberSaveable {
        mutableIntStateOf(0)
    }

    var selectedAnswer by rememberSaveable {
        mutableStateOf("")
    }

    var answers by remember {
        mutableStateOf(listOf<String>())
    }

    val questions = listOf(

        "Which area interests you the most?" to listOf(
            "Technology",
            "Business",
            "Design and Creativity",
            "Healthcare"
        ),

        "What type of work do you enjoy?" to listOf(
            "Solving problems",
            "Working with people",
            "Working with data",
            "Creating new ideas"
        ),

        "Which skill would you like to develop?" to listOf(
            "Programming",
            "Communication",
            "Data Analysis",
            "Leadership"
        ),

        "What work environment do you prefer?" to listOf(
            "Office",
            "Remote",
            "Hybrid",
            "Flexible"
        )
    )

    val question = questions[currentQuestion]

    val progress =
        (currentQuestion + 1).toFloat() / questions.size

    AppPage(darkMode, scrollable = false) {

        BackButton(darkMode) {
            if (currentQuestion > 0) {
                currentQuestion--
                selectedAnswer = answers[currentQuestion]
                answers = answers.dropLast(1)
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "CAREER DISCOVERY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = CareerBlue
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Let's get to know you.",
            fontSize = 29.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.title
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Answer these questions to discover potential career paths.",
            fontSize = 14.sp,
            color = colors.secondary,
            lineHeight = 21.sp
        )

        Spacer(Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                "YOUR PROGRESS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.secondary,
                letterSpacing = 1.sp
            )

            Text(
                "${currentQuestion + 1} / ${questions.size}",
                color = CareerBlue,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = CareerBlue,
            trackColor = colors.border
        )

        Spacer(Modifier.height(30.dp))

        AppCard(darkMode) {

            Text(
                "QUESTION ${currentQuestion + 1}",
                color = CareerPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                question.first,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = colors.title,
                lineHeight = 28.sp
            )

            Spacer(Modifier.height(22.dp))

            question.second.forEachIndexed { index, answer ->

                val isSelected = selectedAnswer == answer

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable {
                            selectedAnswer = answer
                        },
                    shape = RoundedCornerShape(13.dp),
                    color = if (isSelected) {
                        CareerBlue.copy(alpha = 0.13f)
                    } else {
                        colors.surfaceAlt
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) CareerBlue else colors.border
                    )
                ) {

                    Row(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 12.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedAnswer = answer
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CareerBlue
                            )
                        )

                        Spacer(Modifier.width(9.dp))

                        Text(
                            answer,
                            color = if (isSelected) CareerBlue else colors.text,
                            fontWeight = if (isSelected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            },
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        GradientButton(
            text = if (currentQuestion == questions.lastIndex) {
                "GENERATE MY CAREER RESULTS"
            } else {
                "CONTINUE  →"
            },
            enabled = selectedAnswer.isNotEmpty()
        ) {

            val updatedAnswers =
                answers + selectedAnswer

            if (currentQuestion < questions.lastIndex) {

                answers = updatedAnswers
                currentQuestion++
                selectedAnswer = ""

            } else {

                onFinished(updatedAnswers)
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            "Select the option that best describes you.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = colors.secondary
        )
    }
}

// ============================================================
// CAREER RESULT CALCULATION
// ============================================================

fun calculateCareerResults(
    answers: List<String>
): List<Pair<String, Int>> {

    var softwareDeveloper = 0
    var businessAnalyst = 0
    var dataAnalyst = 0

    answers.forEach { answer ->

        when (answer) {

            "Technology" -> {
                softwareDeveloper += 30
                dataAnalyst += 10
            }

            "Business" -> {
                businessAnalyst += 30
            }

            "Design and Creativity" -> {
                softwareDeveloper += 10
                businessAnalyst += 10
            }

            "Solving problems" -> {
                softwareDeveloper += 20
                businessAnalyst += 20
            }

            "Working with people" -> {
                businessAnalyst += 20
            }

            "Working with data" -> {
                dataAnalyst += 30
                businessAnalyst += 10
            }

            "Creating new ideas" -> {
                softwareDeveloper += 10
                businessAnalyst += 10
            }

            "Programming" -> {
                softwareDeveloper += 30
            }

            "Communication" -> {
                businessAnalyst += 20
            }

            "Data Analysis" -> {
                dataAnalyst += 30
            }

            "Leadership" -> {
                businessAnalyst += 20
            }

            "Remote" -> {
                softwareDeveloper += 10
                dataAnalyst += 10
            }

            "Hybrid" -> {
                softwareDeveloper += 10
                businessAnalyst += 10
                dataAnalyst += 10
            }

            "Flexible" -> {
                softwareDeveloper += 10
                dataAnalyst += 10
            }
        }
    }

    val scores = listOf(
        "Software Developer" to softwareDeveloper,
        "Business Analyst" to businessAnalyst,
        "Data Analyst" to dataAnalyst
    )

    val totalScore = scores.sumOf { it.second }

    return scores.map { result ->

        result.first to if (totalScore > 0) {
            (result.second * 100) / totalScore
        } else {
            0
        }

    }.sortedByDescending { it.second }
}

// ============================================================
// CAREER RESULTS SCREEN
// ============================================================

@Composable
fun CareerResultsScreen(
    results: List<Pair<String, Int>>,
    darkMode: Boolean,
    saved: Boolean,
    saveMessage: String,
    onBackToDashboard: () -> Unit
) {

    val colors = appColors(darkMode)

    AppPage(darkMode) {

        BrandLogo(55)

        Spacer(Modifier.height(22.dp))

        Text(
            "YOUR CAREER PROFILE",
            color = CareerBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            "Your results are ready!",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.title
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Here are the career paths that best match your assessment answers.",
            fontSize = 14.sp,
            color = colors.secondary,
            lineHeight = 21.sp
        )

        Spacer(Modifier.height(20.dp))

        AppCard(darkMode) {

            Text(
                "ASSESSMENT STATUS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.secondary
            )

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    if (saved) "✓" else "!",
                    color = if (saved) CareerGreen else CareerRed,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.width(10.dp))

                Column {

                    Text(
                        if (saved) "Successfully saved" else "Saving unsuccessful",
                        color = colors.title,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        saveMessage,
                        color = colors.secondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Your career matches",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = colors.title
        )

        Spacer(Modifier.height(14.dp))

        results.forEachIndexed { index, result ->

            CareerResultCard(
                career = result.first,
                score = result.second,
                careerBlue = when (index) {
                    0 -> CareerBlue
                    1 -> CareerPurple
                    else -> CareerCyan
                },
                darkMode = darkMode,
                rank = index + 1
            )

            Spacer(Modifier.height(13.dp))
        }

        AppCard(darkMode) {

            Text(
                "What do these results mean?",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.title
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Your match percentages represent how your selected answers align with the career categories in this assessment. Use these results as a starting point for further career research.",
                fontSize = 13.sp,
                color = colors.secondary,
                lineHeight = 20.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        GradientButton(
            text = "RETURN TO DASHBOARD"
        ) {
            onBackToDashboard()
        }

        Spacer(Modifier.height(15.dp))
    }
}

// ============================================================
// CAREER RESULT CARD
// ============================================================

@Composable
fun CareerResultCard(
    career: String,
    score: Int,
    careerBlue: Color,
    darkMode: Boolean = false,
    rank: Int = 1
) {

    val colors = appColors(darkMode)

    AppCard(darkMode) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(Modifier.weight(1f)) {

                Text(
                    "MATCH #$rank",
                    color = careerBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    career,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.title
                )
            }

            Text(
                "$score%",
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                color = careerBlue
            )
        }

        Spacer(Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = (score / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(CircleShape),
            color = careerBlue,
            trackColor = colors.border
        )

        Spacer(Modifier.height(14.dp))

        Text(
            when (career) {

                "Software Developer" ->
                    "A technology-focused career involving programming, software design, application development and problem-solving."

                "Business Analyst" ->
                    "A career focused on understanding business requirements, analysing processes and communicating technology-driven solutions."

                else ->
                    "A career focused on analysing information, identifying patterns and helping organisations make data-driven decisions."
            },
            fontSize = 13.sp,
            color = colors.secondary,
            lineHeight = 20.sp
        )
    }
}

// ============================================================
// ASSESSMENT HISTORY SCREEN
// ============================================================


@Composable
fun AssessmentHistoryScreen(
    darkMode: Boolean,
    onBack: () -> Unit
) {
    val colors = appColors(darkMode)

    var history by remember {
        mutableStateOf<List<Map<String, Any>>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var selectedAssessment by remember {
        mutableStateOf<Map<String, Any>?>(null)
    }

    var selectedIndex by remember {
        mutableIntStateOf(0)
    }

    val user = auth.currentUser

    LaunchedEffect(user?.uid) {

        if (user == null) {
            isLoading = false
            errorMessage = "Please log in to view your history."
            return@LaunchedEffect
        }

        db.collection("users")
            .document(user.uid)
            .collection("assessments")
            .orderBy("completedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->

                history = documents.documents.map { document ->
                    document.data ?: emptyMap<String, Any>()
                }

                isLoading = false
                errorMessage = ""
            }
            .addOnFailureListener { exception ->

                Log.e(TAG, "History retrieval failed", exception)

                errorMessage = exception.localizedMessage
                    ?: "Unable to retrieve assessment history."

                isLoading = false
            }
    }

    AppPage(darkMode) {

        BackButton(darkMode, onBack)

        Spacer(Modifier.height(10.dp))

        SectionTitle(
            "My Assessment History",
            "Track your career discoveries and review your previous results.",
            darkMode
        )

        Spacer(Modifier.height(22.dp))

        when {

            isLoading -> {

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = CareerBlue
                    )
                }
            }

            errorMessage.isNotEmpty() -> {

                StatusMessage(
                    errorMessage,
                    false,
                    darkMode
                )
            }

            history.isEmpty() -> {

                AppCard(darkMode) {

                    Text(
                        "📂",
                        fontSize = 35.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "No assessments yet",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.title
                    )

                    Spacer(Modifier.height(7.dp))

                    Text(
                        "Complete your first career assessment to start building your career history.",
                        color = colors.secondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            else -> {

                Text(
                    "${history.size} assessment(s) completed",
                    color = CareerBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(15.dp))

                history.forEachIndexed { index, assessment ->

                    val results =
                        assessment["results"] as? List<*>
                            ?: emptyList<Any>()

                    val firstResult =
                        results.firstOrNull() as? Map<*, *>

                    val topCareer =
                        firstResult?.get("career")?.toString()
                            ?: "Career recommendation"

                    val topScore =
                        (firstResult?.get("score") as? Number)
                            ?.toInt() ?: 0

                    val timestamp =
                        assessment["completedAt"] as? Timestamp

                    val formattedDate =
                        timestamp?.toDate()?.let {
                            java.text.SimpleDateFormat(
                                "dd MMM yyyy • hh:mm a",
                                java.util.Locale.getDefault()
                            ).format(it)
                        } ?: "Completion date unavailable"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAssessment = assessment
                                selectedIndex = index
                            }
                    ) {

                        AppCard(darkMode) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Text(
                                    "ASSESSMENT ${index + 1}",
                                    color = CareerBlue,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )

                                Text(
                                    "✓ COMPLETED",
                                    color = CareerGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(
                                "TOP CAREER MATCH",
                                color = colors.secondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                topCareer,
                                color = colors.title,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {

                                Text(
                                    "Career compatibility",
                                    color = colors.secondary,
                                    fontSize = 12.sp
                                )

                                Text(
                                    "$topScore%",
                                    color = CareerBlue,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(Modifier.height(7.dp))

                            LinearProgressIndicator(
                                progress = {
                                    (topScore.coerceIn(0, 100) / 100f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = CareerBlue,
                                trackColor = colors.border
                            )

                            Spacer(Modifier.height(15.dp))

                            HorizontalDivider(
                                color = colors.border
                            )

                            Spacer(Modifier.height(12.dp))

                            Text(
                                "📅  $formattedDate",
                                color = colors.secondary,
                                fontSize = 11.sp
                            )

                            Spacer(Modifier.height(12.dp))

                            Text(
                                "VIEW FULL ASSESSMENT  →",
                                color = CareerBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(13.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp)
        ) {
            Text("BACK TO DASHBOARD")
        }

        Spacer(Modifier.height(15.dp))
    }

    // Detailed assessment dialog

    selectedAssessment?.let { assessment ->

        val results =
            assessment["results"] as? List<*>
                ?: emptyList<Any>()

        val answers =
            assessment["answers"] as? List<*>
                ?: emptyList<Any>()

        val timestamp =
            assessment["completedAt"] as? Timestamp

        val formattedDate =
            timestamp?.toDate()?.let {
                java.text.SimpleDateFormat(
                    "dd MMM yyyy • hh:mm a",
                    java.util.Locale.getDefault()
                ).format(it)
            } ?: "Date unavailable"

        AlertDialog(

            onDismissRequest = {
                selectedAssessment = null
            },

            title = {
                Text(
                    "Assessment Details",
                    fontWeight = FontWeight.Bold,
                    color = colors.title
                )
            },

            text = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    Text(
                        "ASSESSMENT ${selectedIndex + 1}",
                        color = CareerBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Completed: $formattedDate",
                        color = colors.secondary,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(18.dp))

                    Text(
                        "YOUR CAREER MATCHES",
                        color = CareerBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    results.forEachIndexed { index, item ->

                        val result = item as? Map<*, *>

                        val career =
                            result?.get("career")?.toString()
                                ?: "Career"

                        val score =
                            (result?.get("score") as? Number)
                                ?.toInt() ?: 0

                        Text(
                            career,
                            color = colors.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )

                        Spacer(Modifier.height(5.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            LinearProgressIndicator(
                                progress = {
                                    score.coerceIn(0, 100) / 100f
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(7.dp),
                                color = if (index == 0) {
                                    CareerBlue
                                } else {
                                    CareerGreen
                                },
                                trackColor = colors.border
                            )

                            Spacer(Modifier.width(10.dp))

                            Text(
                                "$score%",
                                color = colors.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                    }

                    HorizontalDivider(color = colors.border)

                    Spacer(Modifier.height(15.dp))

                    Text(
                        "YOUR ASSESSMENT ANSWERS",
                        color = CareerBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    if (answers.isEmpty()) {

                        Text(
                            "No answer details are available.",
                            color = colors.secondary,
                            fontSize = 12.sp
                        )

                    } else {

                        answers.forEachIndexed { index, answer ->

                            Text(
                                "Question ${index + 1}",
                                color = colors.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                answer.toString(),
                                color = colors.secondary,
                                fontSize = 12.sp
                            )

                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        selectedAssessment = null
                    }
                ) {
                    Text(
                        "CLOSE",
                        color = CareerBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            },


            containerColor = if (darkMode) {
                Color(0xFF1E293B)
            } else {
                Color.White
            }
        )
    }
}

// ============================================================
// SKILL GAP ANALYSIS SCREEN
// ============================================================

@Composable
fun SkillGapScreen(
    darkMode: Boolean,
    onBack: () -> Unit
) {
    val colors = appColors(darkMode)

    val requiredSkills = listOf(
        "Java",
        "SQL",
        "HTML & CSS",
        "JavaScript",
        "Git & GitHub",
        "Problem Solving"
    )

    var selectedSkills by remember {
        mutableStateOf(setOf("Java", "SQL"))
    }

    val percentage =
        (selectedSkills.size * 100) / requiredSkills.size

    AppPage(darkMode) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            TextButton(onClick = onBack) {
                Text("← Back to Dashboard")
            }

            Text(
                text = "Skill-Gap Analysis",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = colors.title
            )

            Text(
                text = "Identify your existing skills and discover what you need to improve.",
                color = colors.secondary
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Career Readiness",
                        fontWeight = FontWeight.Bold,
                        color = colors.title
                    )

                    Text(
                        text = "$percentage%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CareerBlue
                    )

                    LinearProgressIndicator(
                        progress = percentage / 100f,
                        modifier = Modifier.fillMaxWidth(),
                        color = CareerGreen
                    )

                    Text(
                        text = "Based on your selected skills",
                        color = colors.secondary
                    )
                }
            }

            Text(
                text = "Select the skills you currently have:",
                fontWeight = FontWeight.Bold,
                color = colors.title
            )

            requiredSkills.forEach { skill ->

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = skill in selectedSkills,
                            onCheckedChange = { checked ->
                                selectedSkills =
                                    if (checked) {
                                        selectedSkills + skill
                                    } else {
                                        selectedSkills - skill
                                    }
                            }
                        )

                        Text(
                            text = skill,
                            color = colors.text
                        )
                    }
                }
            }

            Text(
                text = "Skills to improve:",
                fontWeight = FontWeight.Bold,
                color = colors.title
            )

            requiredSkills
                .filterNot { it in selectedSkills }
                .forEach { skill ->
                    Text(
                        text = "• $skill",
                        color = CareerRed
                    )
                }
        }
    }
}
