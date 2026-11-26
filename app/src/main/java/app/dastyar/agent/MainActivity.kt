package app.dastyar.agent

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.dastyar.agent.chat.ChatScreen
import app.dastyar.agent.chat.ChatViewModel
import app.dastyar.agent.connectors.ConnectorsScreen
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.loop.ConfirmationBus
import app.dastyar.agent.providers.ProvidersScreen
import app.dastyar.agent.settings.AuditScreen
import app.dastyar.agent.settings.DisclosureScreen
import app.dastyar.agent.settings.MemoriesScreen
import app.dastyar.agent.settings.OnboardingScreen
import app.dastyar.agent.settings.PermissionsScreen
import app.dastyar.agent.settings.RoutinesScreen
import app.dastyar.agent.settings.SettingsScreen
import app.dastyar.agent.settings.SettingsViewModel
import app.dastyar.agent.tools.RestrictedSettings
import app.dastyar.agent.tools.RiskLevel
import app.dastyar.agent.ui.ConfirmPanel
import app.dastyar.agent.ui.DastyarTheme
import app.dastyar.agent.ui.t
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var ui: AppUiBridge
    @Inject lateinit var auth: BiometricAuthenticator
    @Inject lateinit var confirm: ConfirmationBus
    @Inject lateinit var settingsStore: SettingsStore

    private val chatVm: ChatViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()
    private val routes = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent { DastyarRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ui.setResumed(true)
        auth.attach(this)
    }

    override fun onPause() {
        ui.setResumed(false)
        super.onPause()
    }

    override fun onDestroy() {
        auth.detach(this)
        super.onDestroy()
    }

    private fun consumeIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                    .ifBlank { intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty() }
                val stream = streamUri(intent)
                val path = stream?.let { copyShare(it) }
                chatVm.acceptShare(text, path)
                if (Bootstrap.onboardingDone(this)) routes.value = "chat"
            }
            ACTION_NEW_CHAT -> {
                chatVm.newChat()
                if (Bootstrap.onboardingDone(this)) routes.value = "chat"
            }
            ACTION_VOICE -> {
                chatVm.requestVoice()
                if (Bootstrap.onboardingDone(this)) routes.value = "chat"
            }
            ACTION_ROUTINE -> {
                val id = intent.getStringExtra(AgentForegroundService.EXTRA_ID) ?: return
                val start = Intent(this, AgentForegroundService::class.java)
                    .setAction(AgentForegroundService.ACTION_ROUTINE)
                    .putExtra(AgentForegroundService.EXTRA_ID, id)
                androidx.core.content.ContextCompat.startForegroundService(this, start)
                if (Bootstrap.onboardingDone(this)) routes.value = "chat"
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun streamUri(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)

    private fun copyShare(uri: Uri): String? = runCatching {
        val dest = File(cacheDir, "share-${System.currentTimeMillis()}")
        contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } } ?: return null
        dest.absolutePath
    }.getOrNull()

    fun applyLanguage(language: String) {
        Bootstrap.writeLanguage(this, language)
        val tag = if (language.startsWith("fa")) "fa" else "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DastyarRoot() {
        val settings by settingsStore.data.collectAsStateWithLifecycle(
            initialValue = app.dastyar.agent.data.SettingsSnapshot(language = Bootstrap.language(this)),
        )
        val lang = settings.language
        val direction = if (lang.startsWith("fa")) LayoutDirection.Rtl else LayoutDirection.Ltr
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides direction) {
            DastyarTheme {
                val nav = rememberNavController()
                val back by nav.currentBackStackEntryAsState()
                val route = back?.destination?.route
                val scope = rememberCoroutineScope()
                val pending by confirm.requests.collectAsStateWithLifecycle()
                var perm by remember { mutableStateOf<UiRequest.Permissions?>(null) }
                var permNeeded by remember { mutableStateOf<List<String>>(emptyList()) }
                var image by remember { mutableStateOf<UiRequest.ConfirmImage?>(null) }
                var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                val photoReply = remember { androidx.compose.runtime.mutableStateOf<CompletableDeferred<String?>?>(null) }
                val videoReply = remember { androidx.compose.runtime.mutableStateOf<CompletableDeferred<String?>?>(null) }
                val screenReply = remember { androidx.compose.runtime.mutableStateOf<CompletableDeferred<String?>?>(null) }
                val dirReply = remember { androidx.compose.runtime.mutableStateOf<CompletableDeferred<String?>?>(null) }
                val docReply = remember { androidx.compose.runtime.mutableStateOf<CompletableDeferred<String?>?>(null) }
                val permReply = remember { androidx.compose.runtime.mutableStateOf<CompletableDeferred<Boolean>?>(null) }
                var restrictedHelp by remember { mutableStateOf(false) }
                val epoch by ui.resumeEpoch.collectAsStateWithLifecycle()

                fun finishPerm(ok: Boolean) {
                    val reply = permReply.value ?: return
                    permReply.value = null
                    perm = null
                    restrictedHelp = false
                    reply.complete(ok)
                }

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                    val ok = result.isNotEmpty() && result.values.all { it }
                    if (ok) {
                        finishPerm(true)
                    } else if (RestrictedSettings.applies(Build.VERSION.SDK_INT) && RestrictedSettings.touchesRuntime(permNeeded)) {
                        restrictedHelp = true
                    } else {
                        finishPerm(false)
                    }
                }
                LaunchedEffect(epoch, restrictedHelp) {
                    if (!restrictedHelp) return@LaunchedEffect
                    val reply = permReply.value ?: return@LaunchedEffect
                    if (reply.isCompleted) return@LaunchedEffect
                    val still = permNeeded.filter { permission ->
                        ContextCompat.checkSelfPermission(this@MainActivity, permission) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (still.isEmpty()) finishPerm(true)
                }
                val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                    val reply = photoReply.value
                    photoReply.value = null
                    val path = pendingPhoto
                    if (ok && path != null && File(path).exists()) reply?.complete(path) else {
                        path?.let { File(it).delete() }
                        reply?.complete(null)
                    }
                }
                val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
                    val reply = videoReply.value
                    videoReply.value = null
                    val path = pendingVideo
                    if (ok && path != null && File(path).exists()) reply?.complete(path) else reply?.complete(null)
                }
                val screenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    val reply = screenReply.value
                    if (result.resultCode != RESULT_OK || result.data == null) {
                        screenReply.value = null
                        reply?.complete(null)
                    } else {
                        ProjectionSession.resultCode = result.resultCode
                        ProjectionSession.data = result.data
                        ProjectionSession.waiter = reply
                        androidx.core.content.ContextCompat.startForegroundService(
                            this,
                            Intent(this, ProjectionForegroundService::class.java),
                        )
                        screenReply.value = null
                    }
                }
                val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    val reply = dirReply.value
                    dirReply.value = null
                    if (uri != null) {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
                        settingsVm.setSaf(uri.toString())
                        reply?.complete(uri.toString())
                    } else {
                        reply?.complete(null)
                    }
                }
                val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                    val reply = docReply.value
                    docReply.value = null
                    reply?.complete(uri?.toString())
                }

                LaunchedEffect(route) {
                    if (route == "providers" || route == "connectors") {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                LaunchedEffect(Unit) {
                    routes.collect { dest ->
                        if (!dest.isNullOrBlank()) {
                            nav.navigate(dest) { launchSingleTop = true }
                            routes.value = null
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    ui.requests.collect { request ->
                        when (request) {
                            is UiRequest.Permissions -> {
                                val needed = request.permissions.filter { permission ->
                                    ContextCompat.checkSelfPermission(this@MainActivity, permission) !=
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                                if (needed.isEmpty()) {
                                    request.reply.complete(true)
                                } else {
                                    permNeeded = needed
                                    permReply.value = request.reply
                                    restrictedHelp = false
                                    perm = request
                                    request.reply.await()
                                    perm = null
                                    restrictedHelp = false
                                }
                            }
                            is UiRequest.Photo -> {
                                val file = File(cacheDir, "cam-${System.currentTimeMillis()}.jpg")
                                pendingPhoto = file.absolutePath
                                photoReply.value = request.reply
                                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                                photoLauncher.launch(uri)
                                request.reply.await()
                            }
                            is UiRequest.Video -> {
                                val file = File(cacheDir, "vid-${System.currentTimeMillis()}.mp4")
                                pendingVideo = file.absolutePath
                                videoReply.value = request.reply
                                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                                videoLauncher.launch(uri)
                                request.reply.await()
                            }
                            is UiRequest.Screen -> {
                                screenReply.value = request.reply
                                val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
                                screenLauncher.launch(manager.createScreenCaptureIntent())
                                request.reply.await()
                            }
                            is UiRequest.Directory -> {
                                dirReply.value = request.reply
                                dirLauncher.launch(null)
                                request.reply.await()
                            }
                            is UiRequest.Document -> {
                                docReply.value = request.reply
                                docLauncher.launch(request.name)
                                request.reply.await()
                            }
                            is UiRequest.ConfirmImage -> {
                                imageBitmap = withContext(Dispatchers.IO) { decodePreview(request.path) }
                                image = request
                                request.reply.await()
                                image = null
                                imageBitmap = null
                            }
                        }
                    }
                }

                val start = if (Bootstrap.onboardingDone(this) || settings.onboardingDone) "chat" else "onboarding"
                Scaffold(
                    bottomBar = {
                        if (route in TOP) {
                            NavigationBar {
                                NavItem(nav, route, "chat", t(lang, "گفتگو", "Chat"), Icons.AutoMirrored.Outlined.Chat)
                                NavItem(nav, route, "providers", t(lang, "کلیدها", "Providers"), Icons.Outlined.Key)
                                NavItem(nav, route, "connectors", t(lang, "اتصال", "Links"), Icons.Outlined.Hub)
                                NavItem(nav, route, "routines", t(lang, "روال", "Routines"), Icons.Outlined.Schedule)
                                NavItem(nav, route, "settings", t(lang, "تنظیم", "Settings"), Icons.Outlined.Settings)
                            }
                        }
                    },
                ) { padding ->
                    NavHost(nav, startDestination = start, modifier = Modifier.padding(padding).fillMaxSize()) {
                        composable("onboarding") {
                            OnboardingScreen(
                                vm = settingsVm,
                                onLanguage = ::applyLanguage,
                                onPermissions = { nav.navigate("permissions?onboarding=1") },
                                onDone = { providers ->
                                    Bootstrap.writeOnboarding(this@MainActivity, true)
                                    scope.launch {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.POST_NOTIFICATIONS),
                                                t(lang, "برای نشان دادن کار دستیار در پس‌زمینه اعلان لازم است.", "Notifications are how you see Dastyar working in the background."),
                                            )
                                        }
                                        nav.navigate(if (providers) "providers" else "chat") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                },
                            )
                        }
                        composable("chat") {
                            ChatScreen(
                                vm = chatVm,
                                ensureMic = {
                                    ui.ensurePermissions(
                                        listOf(Manifest.permission.RECORD_AUDIO),
                                        t(lang, "برای شنیدن صدا میکروفون لازم است. اگر موتور گفتار فارسی نباشد، انگلیسی را امتحان کنید.", "Microphone is required to listen. If the phone has no Persian recognizer, try English."),
                                    )
                                },
                                takePhoto = takePhoto@{
                                    val granted = ui.ensurePermissions(
                                        listOf(Manifest.permission.CAMERA),
                                        t(lang, "دوربین فقط برای عکسی است که خودتان می‌گیرید.", "The camera is only for a photo you take."),
                                    )
                                    if (!granted) return@takePhoto null
                                    val path = ui.takePhoto() ?: return@takePhoto null
                                    val ok = ui.confirmImage(
                                        path,
                                        t(lang, "این عکس به گفتگو اضافه شود؟", "Attach this photo to the chat?"),
                                        t(lang, "بعد از ارسال، فقط به ارائه‌دهندهٔ انتخاب‌شده می‌رود.", "After you send, it goes only to the provider you selected."),
                                    )
                                    if (!ok) {
                                        File(path).delete()
                                        null
                                    } else {
                                        path
                                    }
                                },
                                onOpenProviders = { nav.navigate("providers") },
                            )
                        }
                        composable("providers") { ProvidersScreen() }
                        composable("connectors") {
                            ConnectorsScreen(onPermissions = { nav.navigate("permissions?onboarding=0") })
                        }
                        composable("routines") { RoutinesScreen(settingsVm, onExactAlarm = ui::openExactAlarmSettings) }
                        composable("settings") {
                            SettingsScreen(
                                vm = settingsVm,
                                versionName = BuildConfig.VERSION_NAME,
                                onLanguage = ::applyLanguage,
                                onProviders = { nav.navigate("providers") },
                                onAudit = { nav.navigate("audit") },
                                onMemories = { nav.navigate("memories") },
                                onDisclosure = { nav.navigate("disclosure") },
                                onPermissions = { nav.navigate("permissions?onboarding=0") },
                                onConnectors = { nav.navigate("connectors") },
                                onPickFolder = {
                                    scope.launch { ui.pickDirectory() }
                                },
                            )
                        }
                        composable(
                            "permissions?onboarding={onboarding}",
                            arguments = listOf(navArgument("onboarding") { type = NavType.StringType; defaultValue = "0" }),
                        ) { entry ->
                            val fromOnboarding = entry.arguments?.getString("onboarding") == "1"
                            PermissionsScreen(
                                lang = lang,
                                safGranted = !settings.safTreeUri.isNullOrBlank(),
                                showOnboardingChoices = fromOnboarding,
                                onAppInfo = ui::openAppSettings,
                                onProviders = {
                                    settingsVm.finishOnboarding()
                                    Bootstrap.writeOnboarding(this@MainActivity, true)
                                    scope.launch {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.POST_NOTIFICATIONS),
                                                t(lang, "برای نشان دادن کار دستیار در پس‌زمینه اعلان لازم است.", "Notifications are how you see Dastyar working in the background."),
                                            )
                                        }
                                        nav.navigate("providers") { popUpTo("onboarding") { inclusive = true } }
                                    }
                                },
                                onChat = {
                                    settingsVm.finishOnboarding()
                                    Bootstrap.writeOnboarding(this@MainActivity, true)
                                    scope.launch {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.POST_NOTIFICATIONS),
                                                t(lang, "برای نشان دادن کار دستیار در پس‌زمینه اعلان لازم است.", "Notifications are how you see Dastyar working in the background."),
                                            )
                                        }
                                        nav.navigate("chat") { popUpTo("onboarding") { inclusive = true } }
                                    }
                                },
                                onAction = { id ->
                                    when (id) {
                                        "notifications" -> if (Build.VERSION.SDK_INT >= 33) {
                                            scope.launch {
                                                ui.ensurePermissions(
                                                    listOf(Manifest.permission.POST_NOTIFICATIONS),
                                                    t(lang, "اعلان، وضعیت دستیار را نشان می‌دهد.", "Notifications show what Dastyar is doing."),
                                                )
                                            }
                                        }
                                        "sms" -> scope.launch {
                                            ui.ensurePermissions(
                                                listOf(
                                                    Manifest.permission.READ_SMS,
                                                    Manifest.permission.SEND_SMS,
                                                    Manifest.permission.RECEIVE_SMS,
                                                    Manifest.permission.RECEIVE_MMS,
                                                    Manifest.permission.RECEIVE_WAP_PUSH,
                                                ),
                                                t(lang, "پیامک برای خواندن و فرستادن است. هر ارسال جدا تأیید می‌شود. اگر گفتگو فوراً بسته شد، اجازه به تنظیمات محدود لازم است.", "SMS is for reading and sending. Each send is confirmed on its own. If the dialog closes at once, allow restricted settings."),
                                            )
                                        }
                                        "contacts" -> scope.launch {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                                                t(lang, "مخاطبین برای جستجو و افزودن شماره است.", "Contacts are for search and for saving a number."),
                                            )
                                        }
                                        "call_phone" -> scope.launch {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.CALL_PHONE),
                                                t(lang, "تماس مستقیم فقط بعد از تأیید شما انجام می‌شود.", "A direct call happens only after you confirm."),
                                            )
                                        }
                                        "call_log" -> scope.launch {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.READ_CALL_LOG),
                                                t(lang, "گزارش تماس برای دیدن تماس‌های اخیر است. اگر گفتگو فوراً بسته شد، اجازه به تنظیمات محدود لازم است.", "Call log reads recent calls. If the dialog closes at once, allow restricted settings."),
                                            )
                                        }
                                        "calendar" -> scope.launch {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                                                t(lang, "تقویم برای دیدن و ساختن رویداد است.", "Calendar is for reading and creating events."),
                                            )
                                        }
                                        "camera" -> scope.launch {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.CAMERA),
                                                t(lang, "دوربین فقط برای عکس یا ویدیویی است که خودتان می‌گیرید.", "The camera is only for a photo or video you take."),
                                            )
                                        }
                                        "microphone" -> scope.launch {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.RECORD_AUDIO),
                                                t(lang, "میکروفون برای شنیدن صدای شماست.", "The microphone is for hearing you."),
                                            )
                                        }
                                        "location" -> scope.launch {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                                                t(lang, "موقعیت فقط وقتی یک ابزار بخواهد خوانده می‌شود.", "Location is read only when a tool asks."),
                                            )
                                        }
                                        "background_location" -> if (Build.VERSION.SDK_INT >= 29) {
                                            scope.launch {
                                                ui.ensurePermissions(
                                                    listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                                    t(lang, "موقعیت پس‌زمینه جدا است و می‌توانید رد کنید.", "Background location is separate, and you can deny it."),
                                                )
                                            }
                                        }
                                        "phone_state" -> scope.launch {
                                            ui.ensurePermissions(
                                                listOf(Manifest.permission.READ_PHONE_STATE),
                                                t(lang, "وضعیت تلفن برای ابزارهایی است که وضعیت خط را می‌خواهند.", "Phone state is for tools that need the line status."),
                                            )
                                        }
                                        "accessibility" -> nav.navigate("disclosure")
                                        "notification_listener" -> ui.openNotificationListenerSettings()
                                        "overlay" -> ui.openOverlaySettings()
                                        "all_files" -> ui.openAllFilesSettings()
                                        "saf" -> scope.launch { ui.pickDirectory() }
                                        "exact_alarm" -> ui.openExactAlarmSettings()
                                        "usage_stats" -> ui.openUsageAccessSettings()
                                        "battery" -> ui.openBatterySettings()
                                    }
                                },
                            )
                        }
                        composable("audit") {
                            AuditScreen(settingsVm) { text ->
                                val uri = ui.createDocument("dastyar-audit.txt", "text/plain") ?: return@AuditScreen
                                contentResolver.openOutputStream(Uri.parse(uri))?.use { it.write(text.toByteArray()) }
                            }
                        }
                        composable("memories") { MemoriesScreen(settingsVm) }
                        composable("disclosure") {
                            DisclosureScreen(
                                vm = settingsVm,
                                onOpenSettings = ui::openAccessibilitySettings,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
                val ask = pending
                if (ask != null) {
                    ModalBottomSheet(onDismissRequest = { confirm.answer(ask.id, false) }) {
                        ConfirmPanel(
                            title = ask.title,
                            details = ask.details,
                            approve = t(lang, "انجام بده", "Do it"),
                            deny = t(lang, "نه", "Don't"),
                            onApprove = { confirm.answer(ask.id, true) },
                            onDeny = { confirm.answer(ask.id, false) },
                            dangerous = ask.risk == RiskLevel.DANGEROUS,
                        )
                    }
                }
                perm?.let { request ->
                    AlertDialog(
                        onDismissRequest = { finishPerm(false) },
                        title = { Text(t(lang, "اجازه", "Permission")) },
                        text = { Text(request.rationale) },
                        confirmButton = {
                            TextButton(onClick = {
                                val needed = permNeeded
                                perm = null
                                permissionLauncher.launch(needed.toTypedArray())
                            }) { Text(t(lang, "ادامه", "Continue")) }
                        },
                        dismissButton = {
                            TextButton(onClick = { finishPerm(false) }) { Text(t(lang, "رد", "Deny")) }
                        },
                    )
                }
                if (restrictedHelp) {
                    AlertDialog(
                        onDismissRequest = { finishPerm(false) },
                        title = { Text(t(lang, "تنظیمات محدود", "Restricted settings")) },
                        text = {
                            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                Text(RestrictedSettings.guide(lang))
                                TextButton(onClick = ui::openAppSettings, modifier = Modifier.fillMaxWidth()) {
                                    Text(t(lang, "باز کردن اطلاعات برنامه", "Open app info"))
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { permissionLauncher.launch(permNeeded.toTypedArray()) }) {
                                Text(t(lang, "دوباره تلاش کن", "Try again"))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { finishPerm(false) }) { Text(t(lang, "الان نه", "Not now")) }
                        },
                    )
                }
                image?.let { request ->
                    AlertDialog(
                        onDismissRequest = { request.reply.complete(false) },
                        title = { Text(request.title) },
                        text = {
                            Box {
                                val bmp = imageBitmap
                                if (bmp != null) {
                                    Image(bmp.asImageBitmap(), contentDescription = request.title)
                                } else {
                                    Text(request.body)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { request.reply.complete(true) }) { Text(t(lang, "بفرست", "Send")) }
                        },
                        dismissButton = {
                            TextButton(onClick = { request.reply.complete(false) }) { Text(t(lang, "نه", "Don't")) }
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun RowScope.NavItem(
        nav: androidx.navigation.NavHostController,
        current: String?,
        route: String,
        label: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
    ) {
        NavigationBarItem(
            selected = current == route,
            onClick = {
                nav.navigate(route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(icon, contentDescription = label) },
            label = { Text(label) },
        )
    }

    companion object {
        const val ACTION_NEW_CHAT = "app.dastyar.agent.NEW_CHAT"
        const val ACTION_VOICE = "app.dastyar.agent.VOICE"
        const val ACTION_ROUTINE = "app.dastyar.agent.ROUTINE"
        private val TOP = setOf("chat", "providers", "connectors", "routines", "settings")
        private var pendingPhoto: String? = null
        private var pendingVideo: String? = null

        private fun decodePreview(path: String): android.graphics.Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 1080) sample *= 2
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
