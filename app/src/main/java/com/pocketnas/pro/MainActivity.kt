@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pocketnas.pro

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.pocketnas.pro.core.AppSettingStore
import java.util.Locale
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketnas.pro.ui.AppViewModel
import com.pocketnas.pro.ui.screens.DashboardScreen
import com.pocketnas.pro.ui.screens.FileBrowserScreen
import com.pocketnas.pro.ui.screens.LogScreen
import com.pocketnas.pro.ui.screens.LoginScreen
import com.pocketnas.pro.ui.screens.MediaLibraryScreen
import com.pocketnas.pro.ui.screens.SettingsScreen
import com.pocketnas.pro.ui.screens.StorageAddScreen
import com.pocketnas.pro.ui.screens.StorageScreen
import com.pocketnas.pro.ui.screens.UsersScreen
import com.pocketnas.pro.ui.screens.WebAdminScreen
import com.pocketnas.pro.ui.theme.PocketNasTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = AppSettingStore.getLang(newBase)
        val locale = when (lang) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            PocketNasTheme {
                AppRoot()
            }
        }
    }
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

// 底部导航：仪表盘 / 文件 / 媒体库 / 用户 / 日志 / 设置（存储源并入文件页）
private val tabs = listOf(
    TabItem("dashboard", "仪表盘", Icons.Default.Dashboard),
    TabItem("files", "文件", Icons.Default.Folder),
    TabItem("media", "媒体库", Icons.Default.PhotoLibrary),
    TabItem("users", "用户", Icons.Default.Groups),
    TabItem("logs", "日志", Icons.Default.Terminal),
    TabItem("settings", "设置", Icons.Default.Settings),
)

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun AppRoot(vm: AppViewModel = viewModel()) {
    val loggedIn by vm.loggedIn.collectAsState()
    val navController = rememberNavController()

    if (!loggedIn) {
        val error by vm.loginError.collectAsState()
        LoginScreen(
            onLogin = { u, p -> vm.login(u, p) },
            error = error,
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            vm = vm,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = modifier,
    ) {
        composable("dashboard") {
            DashboardScreen(
                vm = vm,
                onOpenLogs = { navController.navigate("logs") },
                onOpenFiles = { navController.navigate("files") },
                onOpenWebAdmin = { navController.navigate("webadmin") },
            )
        }
        composable("files") {
            FileBrowserScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenStorages = { navController.navigate("storages") },
            )
        }
        composable("media") {
            MediaLibraryScreen(vm)
        }
        // 存储源管理：从文件页进入，不占底部 tab
        composable("storages") {
            StorageScreen(
                vm = vm,
                onAdd = { navController.navigate("storage_add") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("storage_add") {
            StorageAddScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
            )
        }
        composable("users") { UsersScreen(vm) }
        composable("logs") { LogScreen() }
        composable("settings") { SettingsScreen(vm, onOpenWebAdmin = { navController.navigate("webadmin") }, onOpenStorages = { navController.navigate("storages") }) }
        composable("webadmin") { WebAdminScreen(onBack = { navController.popBackStack() }) }
    }
}
