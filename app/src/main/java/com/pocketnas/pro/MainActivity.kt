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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.pocketnas.pro.ui.screens.TransferScreen
import com.pocketnas.pro.ui.screens.MusicScreen
import com.pocketnas.pro.ui.screens.MeScreen
import com.pocketnas.pro.ui.screens.LogScreen
import com.pocketnas.pro.ui.screens.SettingsScreen
import com.pocketnas.pro.ui.screens.WebAdminScreen
import com.pocketnas.pro.ui.screens.StorageScreen
import com.pocketnas.pro.ui.screens.StorageAddScreen
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

private val tabs = listOf(
    TabItem("home", "首页", Icons.Default.Home),
    TabItem("files", "文件", Icons.Default.Folder),
    TabItem("transfer", "传输", Icons.Default.Upload),
    TabItem("music", "音乐", Icons.Default.MusicNote),
    TabItem("me", "我的", Icons.Default.Person),
)

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun AppRoot(vm: AppViewModel = viewModel()) {
    val navController = rememberNavController()

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
        startDestination = "home",
        modifier = modifier,
    ) {
        composable("home") { DashboardScreen(vm, navController) }
        composable("files") { FileBrowserScreen(vm, navController) }
        composable("transfer") { TransferScreen(vm, navController) }
        composable("music") { MusicScreen(vm, navController) }
        composable("me") { MeScreen(vm, navController) }
        composable("webadmin") { WebAdminScreen(onBack = { navController.popBackStack() }) }
        composable("logs") { LogScreen() }
        composable("settings") { SettingsScreen(vm, onOpenWebAdmin = { navController.navigate("webadmin") }, onOpenStorages = { navController.navigate("storages") }) }
        composable("storages") { StorageScreen(vm, navController) }
        composable("storage_add") { StorageAddScreen(vm, onBack = { navController.popBackStack() }) }
    }
}
