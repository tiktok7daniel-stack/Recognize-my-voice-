package com.daniel.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.daniel.assistant.ui.aliases.AliasesScreen
import com.daniel.assistant.ui.commands.CommandEditorScreen
import com.daniel.assistant.ui.commands.CommandsListScreen
import com.daniel.assistant.ui.download.ModelScreen
import com.daniel.assistant.ui.home.HomeScreen
import com.daniel.assistant.ui.intro.IntroScreen
import com.daniel.assistant.ui.logs.LogsScreen
import com.daniel.assistant.ui.onboarding.PermissionsScreen
import com.daniel.assistant.ui.settings.SettingsScreen
import com.daniel.assistant.ui.theme.Neo
import com.daniel.assistant.ui.theme.NabraTheme
import com.daniel.assistant.ui.tutorial.TutorialScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleListenIntent(intent)
    }

    /** Shortcut / tile: start listening for one command and get out of the way. */
    private fun handleListenIntent(intent: android.content.Intent?) {
        if (intent?.action != com.daniel.assistant.service.ListeningService.ACTION_LISTEN_NOW) return
        if (!com.daniel.assistant.ui.onboarding.Permissions.mic(this)) return
        com.daniel.assistant.service.ListeningService.listenNow(this)
        intent.action = null
        moveTaskToBack(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleListenIntent(intent)
        setContent {
            NabraTheme {
                Box(Modifier.fillMaxSize().background(Neo.Bg0)) { NabraNav() }
            }
        }
    }
}

private object Routes {
    const val INTRO = "intro"
    const val TUTORIAL = "tutorial"
    const val PERMISSIONS = "permissions"
    const val MODEL = "model"
    const val HOME = "home"
    const val COMMANDS = "commands"
    const val EDITOR = "editor?id={id}"
    const val ALIASES = "aliases"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
    fun editor(id: Long?) = if (id == null) "editor" else "editor?id=$id"
}

private data class Tab(val route: String, val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "الرئيسية", Icons.Filled.GraphicEq),
    Tab(Routes.COMMANDS, "أوامري", Icons.Filled.RecordVoiceOver),
    Tab(Routes.ALIASES, "التطبيقات", Icons.Filled.Apps),
    Tab(Routes.SETTINGS, "الإعدادات", Icons.Filled.Settings),
)

@Composable
private fun NabraNav() {
    val nav = rememberNavController()
    val c = rememberContainer()
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Routes.INTRO) {
            composable(Routes.INTRO) {
                IntroScreen {
                    scope.launch {
                        val s = c.settings.current()
                        val next = when {
                            !s.tutorialDone -> Routes.TUTORIAL
                            !s.onboardingDone -> Routes.PERMISSIONS
                            !c.models.isReady() -> Routes.MODEL
                            else -> Routes.HOME
                        }
                        nav.navigate(next) { popUpTo(Routes.INTRO) { inclusive = true } }
                    }
                }
            }
            composable(Routes.TUTORIAL) {
                val firstRun = nav.previousBackStackEntry == null
                TutorialScreen(
                    onFinish = {
                        scope.launch {
                            c.settings.setTutorialDone(true)
                            if (firstRun) {
                                val s = c.settings.current()
                                val next = when {
                                    !s.onboardingDone -> Routes.PERMISSIONS
                                    !c.models.isReady() -> Routes.MODEL
                                    else -> Routes.HOME
                                }
                                nav.navigate(next) { popUpTo(Routes.TUTORIAL) { inclusive = true } }
                            } else nav.popBackStack()
                        }
                    },
                    onCreateFirstCommand = {
                        scope.launch { c.settings.setTutorialDone(true) }
                        if (firstRun) {
                            nav.navigate(Routes.HOME) { popUpTo(Routes.TUTORIAL) { inclusive = true } }
                        } else nav.popBackStack()
                        nav.navigate(Routes.editor(null))
                    },
                    onClose = if (firstRun) null else ({ nav.popBackStack() }),
                )
            }
            composable(Routes.PERMISSIONS) {
                val firstRun = nav.previousBackStackEntry == null
                PermissionsScreen(
                    onContinue = {
                        scope.launch { c.settings.setOnboardingDone(true) }
                        if (firstRun) {
                            nav.navigate(if (c.models.isReady()) Routes.HOME else Routes.MODEL) {
                                popUpTo(Routes.PERMISSIONS) { inclusive = true }
                            }
                        } else nav.popBackStack()
                    },
                    onBack = if (firstRun) null else ({ nav.popBackStack() }),
                )
            }
            composable(Routes.MODEL) {
                val firstRun = nav.previousBackStackEntry == null
                ModelScreen(
                    onDone = {
                        if (firstRun) nav.navigate(Routes.HOME) { popUpTo(Routes.MODEL) { inclusive = true } }
                        else nav.popBackStack()
                    },
                    onBack = if (firstRun) null else ({ nav.popBackStack() }),
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                    onOpenModel = { nav.navigate(Routes.MODEL) },
                    onOpenTutorial = { nav.navigate(Routes.TUTORIAL) },
                    onNewCommand = { nav.navigate(Routes.editor(null)) },
                    onOpenCommands = {
                        nav.navigate(Routes.COMMANDS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Routes.COMMANDS) {
                CommandsListScreen(onNew = { nav.navigate(Routes.editor(null)) }, onEdit = { nav.navigate(Routes.editor(it)) })
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                CommandEditorScreen(id) { nav.popBackStack() }
            }
            composable(Routes.ALIASES) { AliasesScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenModel = { nav.navigate(Routes.MODEL) },
                    onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                    onOpenLogs = { nav.navigate(Routes.LOGS) },
                    onOpenTutorial = { nav.navigate(Routes.TUTORIAL) },
                )
            }
            composable(Routes.LOGS) { LogsScreen { nav.popBackStack() } }
        }

        if (tabs.any { it.route == current }) {
            BottomBar(nav, current, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    NavigationBar(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(20.dp, shape)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        containerColor = Neo.Bg2.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = {
                    if (current != tab.route) nav.navigate(tab.route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, tab.title) },
                label = { Text(tab.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Neo.Text,
                    selectedTextColor = Neo.Cyan,
                    indicatorColor = Neo.Accent,
                    unselectedIconColor = Neo.TextDim,
                    unselectedTextColor = Neo.TextDim,
                ),
            )
        }
    }
}
