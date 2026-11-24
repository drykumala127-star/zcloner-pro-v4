package com.fms.zclone.enginecoremode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fms.zclone.enginecoremode.zclone.ui.ZCloneViewModel
import com.fms.zclone.enginecoremode.zclone.ui.screens.AppConfigScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.CloneAppsScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.CloudAuthDialogCard
import com.fms.zclone.enginecoremode.zclone.ui.screens.DeviceManagerScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.FmsHubScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.HomeDashboardScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.MyClonesLibraryScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.SecurityScannerScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.SettingsScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.SplashScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.ThemeStudioScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.TierOverviewDialog
import com.fms.zclone.enginecoremode.zclone.ui.screens.ToolsHubScreen
import com.fms.zclone.enginecoremode.zclone.ui.screens.ZCloneRoute
import com.fms.zclone.enginecoremode.zclone.ui.screens.tr
import com.fms.cloud.FMSAuthEngine
import com.fms.cloud.FMSAuthEngine.authStateFlow
import com.fms.ui.FMSLayoutEngine
import com.fms.ui.FMSNavigationEngine
import com.fms.ui.FMSThemeEngine
import com.fms.ui.FmsBackHandler
import com.fms.ui.FmsNeonWaveBackdrop
import com.fms.ui.FmsWindowWidthClass
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZCloneRootApp()
        }
    }
}

@Composable
fun ZCloneRootApp() {
    val context = LocalContext.current
    val vm: ZCloneViewModel = viewModel(factory = ZCloneViewModel.Factory(context))
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val theme by FMSThemeEngine.activeTheme.collectAsStateWithLifecycle()
    val autoThemeEnabled by FMSThemeEngine.autoThemeEnabled.collectAsStateWithLifecycle()
    val currentUser by remember { Firebase.auth.authStateFlow() }.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val navEngine = rememberSaveable(
        saver = listSaver(
            save = { engine -> engine.snapshot().map { it.name } },
            restore = { savedNames ->
                val routes = savedNames.mapNotNull { n -> ZCloneRoute.entries.firstOrNull { it.name == n } }
                FMSNavigationEngine(ZCloneRoute.HOME).apply { restore(routes) }
            }
        )
    ) {
        FMSNavigationEngine(ZCloneRoute.SPLASH)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val systemDark = isSystemInDarkTheme()

    var showAuthDialog by remember { mutableStateOf(false) }
    var showTierDialog by remember { mutableStateOf(false) }

    // Non-blocking auto sign-in for returning authenticated users (local mode remains available offline)
    LaunchedEffect(Unit) {
        FMSAuthEngine.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { },
            onUnauthenticated = { },
            scope = scope
        )
    }

    // Bind Firestore real-time repositories strictly to authenticated user identity
    LaunchedEffect(currentUser?.uid) {
        val u = currentUser
        FMSAuthEngine.syncFromAuthUser(u, uiState.isOnline)
        if (u != null) {
            vm.attachAuthenticatedSession(u.uid, u.displayName, u.email)
        } else {
            vm.detachAuthenticatedSession()
        }
    }

    LaunchedEffect(uiState.statusBanner, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.clearBanner()
        }
    }

    FmsBackHandler(navEngine)

    MaterialTheme(
        colorScheme = FMSThemeEngine.toColorScheme(theme),
        typography = FMSThemeEngine.toTypography(theme)
    ) {
        val route = navEngine.currentRoute
        if (route == ZCloneRoute.SPLASH) {
            SplashScreen(
                theme = theme,
                languageCode = uiState.languageCode,
                onContinueFinished = { navEngine.replaceRoot(ZCloneRoute.HOME) }
            )
            return@MaterialTheme
        }

        val rootTabs = remember(uiState.languageCode) {
            listOf(
                Triple(ZCloneRoute.HOME, Icons.Rounded.Home, tr(uiState.languageCode, "Beranda", "Home")),
                Triple(ZCloneRoute.LIBRARY, Icons.Rounded.Apps, tr(uiState.languageCode, "Pustaka", "Library")),
                Triple(ZCloneRoute.TOOLS, Icons.Rounded.Build, tr(uiState.languageCode, "Alat", "Tools")),
                Triple(ZCloneRoute.SETTINGS, Icons.Rounded.Settings, tr(uiState.languageCode, "Pengaturan", "Settings"))
            )
        }
        val showBottomOrRail = route in setOf(
            ZCloneRoute.HOME,
            ZCloneRoute.LIBRARY,
            ZCloneRoute.TOOLS,
            ZCloneRoute.SETTINGS
        )

        Box(modifier = Modifier.fillMaxSize()) {
            FmsNeonWaveBackdrop(theme = theme, animateWaves = route == ZCloneRoute.FMS_HUB || route == ZCloneRoute.HOME)

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val widthClass = FMSLayoutEngine.classifyWidth(maxWidth)
                val useRail = widthClass != FmsWindowWidthClass.COMPACT && showBottomOrRail

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (showBottomOrRail && !useRail) {
                            NavigationBar(
                                containerColor = theme.surface.copy(alpha = 0.96f),
                                contentColor = theme.onBackground
                            ) {
                                rootTabs.forEach { (tabRoute, icon, label) ->
                                    val selected = route == tabRoute
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navEngine.replaceRoot(tabRoute) },
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = theme.primary,
                                            selectedTextColor = theme.primary,
                                            indicatorColor = theme.primary.copy(alpha = 0.16f),
                                            unselectedIconColor = theme.onSurfaceMuted,
                                            unselectedTextColor = theme.onSurfaceMuted
                                        ),
                                        modifier = Modifier.testTag("nav_${tabRoute.name.lowercase()}")
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
                        if (useRail) {
                            NavigationRail(
                                containerColor = theme.surface.copy(alpha = 0.94f),
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                rootTabs.forEach { (tabRoute, icon, label) ->
                                    val selected = route == tabRoute
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navEngine.replaceRoot(tabRoute) },
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = theme.primary,
                                            selectedTextColor = theme.primary,
                                            indicatorColor = theme.primary.copy(alpha = 0.16f),
                                            unselectedIconColor = theme.onSurfaceMuted,
                                            unselectedTextColor = theme.onSurfaceMuted
                                        ),
                                        modifier = Modifier.testTag("rail_${tabRoute.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 840.dp)
                            ) {
                                when (route) {
                                    ZCloneRoute.HOME -> HomeDashboardScreen(
                                        state = uiState,
                                        theme = theme,
                                        onNavigate = { target ->
                                            if (target in setOf(ZCloneRoute.LIBRARY, ZCloneRoute.TOOLS, ZCloneRoute.SETTINGS)) {
                                                navEngine.replaceRoot(target)
                                            } else {
                                                navEngine.navigate(target)
                                            }
                                        },
                                        onToggleAutoTheme = {
                                            vm.toggleAutoTheme(!autoThemeEnabled, systemDark)
                                        },
                                        onOpenAuthDialog = { showAuthDialog = true },
                                        onOpenTierDialog = { showTierDialog = true },
                                        onCancelActiveBuild = { vm.cancelActiveBuild() },
                                        onCancelAllBatchJobs = { vm.cancelAllBatchJobs() }
                                    )

                                    ZCloneRoute.LIBRARY -> MyClonesLibraryScreen(
                                        state = uiState,
                                        theme = theme,
                                        onNavigateCloneApps = { navEngine.navigate(ZCloneRoute.CLONE_APPS) },
                                        onDeleteClone = { vm.deleteCloneConfig(it) },
                                        onInstallApk = { vm.launchApkInstall(context, it) },
                                        onExportApkUri = { path, uri -> vm.exportApkToUri(path, uri) },
                                        onBuildFromRecipe = { rec ->
                                            vm.prepareSavedRecipeForConfig(rec) {
                                                navEngine.navigate(ZCloneRoute.APP_CONFIG)
                                            }
                                        },
                                        onRestoreSnapshot = { hist ->
                                            vm.restoreTimeMachineSnapshot(hist) {
                                                navEngine.navigate(ZCloneRoute.APP_CONFIG)
                                            }
                                        },
                                        onCompareBinaryApks = { pathA, pathB -> vm.compareHistoryOrCloneApks(pathA, pathB) },
                                        onClearBinaryDiff = { vm.clearBinaryDiff() },
                                        onExportBinaryDiffUri = { diff, uri -> vm.exportBinaryDiffToUri(diff, uri) }
                                    )

                                    ZCloneRoute.TOOLS -> ToolsHubScreen(
                                        state = uiState,
                                        theme = theme,
                                        onNavigate = { target ->
                                            if (target == ZCloneRoute.SETTINGS) navEngine.replaceRoot(target)
                                            else navEngine.navigate(target)
                                        },
                                        onCancelBatchJob = { vm.cancelBatchJob(it) },
                                        onCancelAllBatch = { vm.cancelAllBatchJobs() }
                                    )

                                    ZCloneRoute.SETTINGS -> SettingsScreen(
                                        state = uiState,
                                        theme = theme,
                                        onBack = { navEngine.replaceRoot(ZCloneRoute.HOME) },
                                        onSetLanguage = { vm.setLanguage(it) },
                                        onExportBackupUri = { vm.exportBackupToUri(it) },
                                        onImportBackupUri = { vm.importBackupFromUri(it) },
                                        onClearWorkCache = { vm.clearTempCache() },
                                        onOpenAuthDialog = { showAuthDialog = true },
                                        onOpenTierDialog = { showTierDialog = true }
                                    )

                                    ZCloneRoute.CLONE_APPS -> CloneAppsScreen(
                                        state = uiState,
                                        theme = theme,
                                        onBack = { navEngine.pop() },
                                        onRefreshApps = { vm.loadDeviceApps() },
                                        onRequestSystemApps = { vm.loadSystemAppsIfNeeded() },
                                        onLoadAppIcon = { pkg -> vm.loadAppIcon(pkg) },
                                        onTogglePackage = { vm.togglePackageSelection(it) },
                                        onClearSelection = { vm.clearSelection() },
                                        onConfigureApp = { app ->
                                            vm.prepareSingleAppForConfig(app) {
                                                navEngine.navigate(ZCloneRoute.APP_CONFIG)
                                            }
                                        },
                                        onImportApkUri = { uri ->
                                            vm.importExternalApk(uri) {
                                                navEngine.navigate(ZCloneRoute.APP_CONFIG)
                                            }
                                        },
                                        onBatchCloneSelected = { vm.runBatchCloneSelected() },
                                        onCancelBatchJob = { vm.cancelBatchJob(it) },
                                        onCancelAllBatch = { vm.cancelAllBatchJobs() }
                                    )

                                    ZCloneRoute.APP_CONFIG -> AppConfigScreen(
                                        state = uiState,
                                        theme = theme,
                                        onBack = { navEngine.pop() },
                                        onBuildClone = { req, existingId -> vm.buildCloneFromConfig(req, existingId) },
                                        onExportApkUri = { path, uri -> vm.exportApkToUri(path, uri) },
                                        onInstallApk = { vm.launchApkInstall(context, it) },
                                        onAttachSourceApkUri = { vm.attachExternalSourceApk(it) },
                                        onVerifyInstalled = { vm.verifyClonedAppInstalled(it) },
                                        onTestLaunchClone = { vm.testLaunchClonedApp(context, it) },
                                        onConfirmMainFunction = { pkg, passed -> vm.recordPostInstallFunctionalCheck(pkg, passed) },
                                        onCancelBuild = { vm.cancelActiveBuild() }
                                    )

                                    ZCloneRoute.THEME_STUDIO -> ThemeStudioScreen(
                                        state = uiState,
                                        theme = theme,
                                        autoThemeEnabled = autoThemeEnabled,
                                        onBack = { navEngine.pop() },
                                        onApplyPreset = { vm.applyThemePreset(it) },
                                        onToggleAutoTheme = { enabled -> vm.toggleAutoTheme(enabled, systemDark) },
                                        onFontChoiceSelected = { vm.setFontChoice(it) },
                                        onIconStyleSelected = { vm.setIconStyle(it) },
                                        onApplyCustomTheme = { name, primary, secondary, font, iconStyle ->
                                            vm.applyCustomThemeAndSync(name, primary, secondary, font, iconStyle)
                                        }
                                    )

                                    ZCloneRoute.DEVICE_MANAGER -> DeviceManagerScreen(
                                        state = uiState,
                                        theme = theme,
                                        onBack = { navEngine.pop() },
                                        onToggleDeviceStatus = { docId, nextStatus ->
                                            vm.updateDeviceStatus(docId, nextStatus)
                                        },
                                        onUpdateDeviceLabel = { vm.updateLocalDeviceLabel(it) },
                                        onOpenAuthDialog = { showAuthDialog = true }
                                    )

                                    ZCloneRoute.SECURITY_SCANNER -> SecurityScannerScreen(
                                        state = uiState,
                                        theme = theme,
                                        onBack = { navEngine.pop() },
                                        onRequestSystemApps = { vm.loadSystemAppsIfNeeded() },
                                        onScanApp = { vm.scanInstalledAppSecurity(it) },
                                        onScanApkUri = { vm.scanExternalApkSecurity(it) },
                                        onSelectSavedReport = { vm.selectSavedSecurityReport(it) }
                                    )

                                    ZCloneRoute.FMS_HUB -> FmsHubScreen(
                                        state = uiState,
                                        theme = theme,
                                        onBack = { navEngine.pop() }
                                    )

                                    ZCloneRoute.SPLASH -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAuthDialog) {
        Dialog(onDismissRequest = { showAuthDialog = false }) {
            CloudAuthDialogCard(
                theme = theme,
                languageCode = uiState.languageCode,
                isSignedIn = currentUser != null,
                userEmail = currentUser?.email ?: uiState.userEmail,
                authSession = uiState.authSession,
                isOnline = uiState.isOnline,
                isLocalDeviceRevoked = uiState.isLocalDeviceRevoked,
                onRefreshSession = { vm.refreshAuthSession(context) },
                onAuthSuccess = { showAuthDialog = false },
                onSignOut = {
                    vm.detachAuthenticatedSession()
                    showAuthDialog = false
                },
                onDismiss = { showAuthDialog = false }
            )
        }
    }

    if (showTierDialog) {
        Dialog(onDismissRequest = { showTierDialog = false }) {
            TierOverviewDialog(
                state = uiState,
                theme = theme,
                onDismiss = { showTierDialog = false }
            )
        }
    }
}
