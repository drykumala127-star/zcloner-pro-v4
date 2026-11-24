package com.fms.zclone.enginecoremode.zclone.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fms.zclone.enginecoremode.zclone.ui.DeviceCloneVerificationState
import com.fms.zclone.enginecoremode.zclone.ui.ZCloneUiState
import com.fms.clone.ApkValidationStep
import com.fms.clone.CloneBuildFailureInfo
import com.fms.clone.CloneBuildOutput
import com.fms.clone.CloneBuildRequest
import com.fms.clone.CompatibilityStatus
import com.fms.clone.FMSIconEngine
import com.fms.clone.InstalledAppInfo
import com.fms.clone.ValidationCheckCategory
import com.fms.clone.ValidationCheckStatus
import com.fms.data.LocalCloneRecord
import com.fms.security.FMSIntegrityEngine
import com.fms.ui.FmsGlassCard
import com.fms.ui.FmsGradientButton
import com.fms.ui.FmsStatusBadge
import com.fms.ui.FmsThemePreset
import com.fms.ui.fmsIconContainerShape

@Composable
fun CloneAppsScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onBack: () -> Unit,
    onRefreshApps: () -> Unit,
    onRequestSystemApps: () -> Unit = {},
    onLoadAppIcon: suspend (String) -> Bitmap? = { null },
    onTogglePackage: (String) -> Unit,
    onClearSelection: () -> Unit,
    onConfigureApp: (InstalledAppInfo) -> Unit,
    onImportApkUri: (android.net.Uri) -> Unit,
    onBatchCloneSelected: () -> Unit,
    onCancelBatchJob: (String) -> Unit = {},
    onCancelAllBatch: () -> Unit = {}
) {
    val lang = state.languageCode
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showBatchRightsDialog by remember { mutableStateOf(false) }
    var batchRightsChecked by remember { mutableStateOf(false) }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportApkUri(uri)
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            onRequestSystemApps()
        }
    }

    val rawList = if (selectedTab == 0) state.installedApps else state.systemApps
    val filteredList = remember(rawList, searchQuery) {
        if (searchQuery.isBlank()) rawList
        else rawList.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header bar matching mockup
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("clone_apps_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = tr(lang, "Kembali", "Back"),
                        tint = theme.onBackground
                    )
                }
                Text(
                    text = "Clone Apps",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.onBackground
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { searchVisible = !searchVisible },
                    modifier = Modifier.testTag("search_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = tr(lang, "Cari aplikasi", "Search apps"),
                        tint = theme.onBackground
                    )
                }
                OutlinedButton(
                    onClick = { apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                    enabled = !state.isBusyApkIo,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("import_apk_button")
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = tr(lang, "Impor APK", "Import APK"),
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.primary
                    )
                }
                IconButton(
                    onClick = onRefreshApps,
                    modifier = Modifier.testTag("refresh_apps_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = tr(lang, "Muat ulang daftar", "Reload app list"),
                        tint = theme.onBackground
                    )
                }
            }
        }

        AnimatedVisibility(visible = searchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text(tr(lang, "Cari nama atau package name...", "Search app or package...")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = tr(lang, "Bersihkan", "Clear"))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_apps_input")
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = theme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.testTag("tab_installed_apps"),
                text = {
                    Text(
                        text = tr(lang, "Aplikasi Terinstal (${state.installedApps.size})", "Installed Apps (${state.installedApps.size})"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    onRequestSystemApps()
                },
                modifier = Modifier.testTag("tab_system_apps"),
                text = {
                    val sysSuffix = if (state.hasLoadedSystemApps) " (${state.systemApps.size})" else ""
                    Text(
                        text = tr(lang, "Aplikasi Sistem$sysSuffix", "System Apps$sysSuffix"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        if (state.isBusyApkIo) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(4.dp)
            )
        }

        val activeBatchJobs = state.backgroundJobs.filter {
            it.state == com.fms.task.FmsJobState.QUEUED || it.state == com.fms.task.FmsJobState.RUNNING
        }
        AnimatedVisibility(visible = activeBatchJobs.isNotEmpty()) {
            FmsGlassCard(
                theme = theme,
                cornerRadius = 14.dp,
                borderColor = theme.primary.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "Antrean Batch Aktif (${activeBatchJobs.size})", "Active Batch Queue (${activeBatchJobs.size})"),
                            style = MaterialTheme.typography.labelLarge,
                            color = theme.primary
                        )
                        TextButton(
                            onClick = onCancelAllBatch,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_all_batch_button")
                        ) {
                            Text(tr(lang, "Batalkan Semua", "Cancel All"), color = Color(0xFFEF4444))
                        }
                    }
                    activeBatchJobs.take(3).forEach { job ->
                        val pct = (job.progress * 100).toInt().coerceIn(0, 100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${job.title} ($pct%) — ${job.detail}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { job.progress },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50))
                                )
                            }
                            IconButton(
                                onClick = { onCancelBatchJob(job.jobId) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_batch_job_${job.jobId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = tr(lang, "Batalkan tugas ${job.title}", "Cancel job ${job.title}"),
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isLoadingTab = state.isLoadingApps || (selectedTab == 1 && state.isLoadingSystemApps)
            if (isLoadingTab) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = theme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (selectedTab == 1 && state.isLoadingSystemApps) {
                                tr(lang, "Memuat daftar aplikasi sistem...", "Loading system apps...")
                            } else {
                                tr(lang, "Memindai aplikasi terinstal...", "Scanning installed apps...")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.onSurfaceMuted
                        )
                    }
                }
            } else if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    FmsGlassCard(theme = theme, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.Android, contentDescription = null, tint = theme.primary, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = tr(lang, "Tidak ada aplikasi ditemukan", "No matching apps found"),
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.onBackground
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = tr(
                                    lang,
                                    "Gunakan tombol 'Impor APK' di kanan atas untuk memilih file .apk langsung dari penyimpanan perangkat.",
                                    "Use 'Import APK' above to pick any .apk file directly from device storage."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.onSurfaceMuted
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.packageName }) { app ->
                        val checked = app.packageName in state.selectedPackages
                        AppSelectionRow(
                            app = app,
                            checked = checked,
                            theme = theme,
                            lang = lang,
                            onLoadIcon = onLoadAppIcon,
                            onRowClick = { onConfigureApp(app) },
                            onToggleChecked = { onTogglePackage(app.packageName) }
                        )
                    }
                }
            }
        }

        // Sticky bottom selection bar matching mockup
        AnimatedVisibility(visible = state.selectedPackages.isNotEmpty()) {
            Surface(
                color = theme.surfaceElevated,
                border = BorderStroke(1.dp, theme.border),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = tr(lang, "Batal pilih", "Clear selection"), tint = theme.onSurfaceMuted)
                        }
                        Text(
                            text = tr(
                                lang,
                                "Dipilih: ${state.selectedPackages.size} aplikasi",
                                "Selected: ${state.selectedPackages.size} apps"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.onBackground
                        )
                    }
                    FmsGradientButton(
                        text = tr(lang, "Kloning Batch", "Batch Clone"),
                        theme = theme,
                        leadingIcon = Icons.AutoMirrored.Rounded.Send,
                        onClick = {
                            batchRightsChecked = false
                            showBatchRightsDialog = true
                        },
                        modifier = Modifier.testTag("batch_clone_button")
                    )
                }
            }
        }
    }

    if (showBatchRightsDialog) {
        AlertDialog(
            onDismissRequest = { showBatchRightsDialog = false },
            modifier = Modifier.testTag("batch_ownership_dialog"),
            title = {
                Text(tr(lang, "Konfirmasi Hak Modifikasi APK", "APK Modification Rights Confirmation"))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(
                            lang,
                            "Anda akan mengkloning ${state.selectedPackages.size} APK terpilih beserta injeksi runtime Dhedry Animated Loading. Pastikan seluruh APK tersebut adalah milik Anda atau Anda berhak memodifikasinya.",
                            "You are about to clone ${state.selectedPackages.size} selected APKs with Dhedry Animated Loading runtime injection. Ensure you own or hold explicit rights to modify them."
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { batchRightsChecked = !batchRightsChecked }
                    ) {
                        Checkbox(
                            checked = batchRightsChecked,
                            onCheckedChange = { batchRightsChecked = it },
                            modifier = Modifier.testTag("batch_ownership_checkbox")
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tr(
                                lang,
                                "Saya menyatakan APK yang diproses milik saya atau berhak saya modifikasi.",
                                "I confirm I own or hold legal rights to modify the selected APKs."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchRightsDialog = false
                        onBatchCloneSelected()
                    },
                    enabled = batchRightsChecked,
                    modifier = Modifier.testTag("batch_ownership_confirm_button")
                ) {
                    Text(tr(lang, "Lanjutkan Batch Clone", "Proceed Batch Clone"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchRightsDialog = false },
                    modifier = Modifier.testTag("batch_ownership_cancel_button")
                ) {
                    Text(tr(lang, "Batal", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun AppSelectionRow(
    app: InstalledAppInfo,
    checked: Boolean,
    theme: FmsThemePreset,
    lang: String,
    onLoadIcon: suspend (String) -> Bitmap?,
    onRowClick: () -> Unit,
    onToggleChecked: () -> Unit
) {
    var lazyIcon by remember(app.packageName, app.iconBitmap) { mutableStateOf(app.iconBitmap) }
    LaunchedEffect(app.packageName) {
        if (lazyIcon == null) {
            lazyIcon = onLoadIcon(app.packageName)
        }
    }
    val bmpImage = remember(app.packageName, lazyIcon) { lazyIcon?.asImageBitmap() }
    val sizeMb = remember(app.apkSizeBytes) {
        if (app.apkSizeBytes > 0) String.format("%.1f MB", app.apkSizeBytes / (1024.0 * 1024.0)) else "N/A"
    }

    FmsGlassCard(
        theme = theme,
        cornerRadius = 18.dp,
        borderColor = if (checked) theme.primary else theme.border,
        onClick = onRowClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_row_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconShape = fmsIconContainerShape(theme.iconStyle)
            if (bmpImage != null) {
                Image(
                    bitmap = bmpImage,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(iconShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(iconShape)
                        .background(theme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Android, contentDescription = app.appName, tint = theme.primary)
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.hasSplitApks) {
                        Spacer(Modifier.width(6.dp))
                        FmsStatusBadge(label = "SPLIT", color = theme.accentGold)
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${app.versionName} · $sizeMb · ${app.requestedPermissions.size} ${tr(lang, "izin", "perms")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.primary.copy(alpha = 0.85f)
                )
            }

            Checkbox(
                checked = checked,
                enabled = !app.hasSplitApks && app.sourceApkPath != null,
                onCheckedChange = { onToggleChecked() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("app_checkbox_${app.packageName}")
            )
        }
    }
}

private data class IconVariantSwatch(
    val id: String,
    val label: String,
    val hueShift: Int,
    val accentColor: Color
)

@Composable
fun AppConfigScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onBack: () -> Unit,
    onBuildClone: (CloneBuildRequest, String?) -> Unit,
    onExportApkUri: (String, android.net.Uri) -> Unit,
    onInstallApk: (String) -> Unit,
    onAttachSourceApkUri: (android.net.Uri) -> Unit,
    onVerifyInstalled: (String) -> Unit = {},
    onTestLaunchClone: (String) -> Unit = {},
    onConfirmMainFunction: (String, Boolean) -> Unit = { _, _ -> },
    onCancelBuild: () -> Unit = {}
) {
    val lang = state.languageCode
    val app = state.activeSelectedApp
    val inspection = state.activeInspection
    val prefill: LocalCloneRecord? = state.pendingPrefillConfig

    if (app == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(lang, "Kembali", "Back"))
            }
            Text(tr(lang, "Belum ada aplikasi dipilih.", "No app selected."), color = theme.onBackground)
        }
        return
    }

    var cloneName by remember(app.packageName, prefill?.id) {
        mutableStateOf(prefill?.cloneName ?: "${app.appName} Clone 1")
    }
    var changePackageName by remember(app.packageName, prefill?.id) {
        mutableStateOf(prefill?.changePackageName ?: true)
    }
    var targetPackage by remember(app.packageName, prefill?.id) {
        mutableStateOf(prefill?.targetPackage ?: "${app.packageName}.zclone1".take(150))
    }
    var hideFromLauncher by remember(app.packageName, prefill?.id) {
        mutableStateOf(prefill?.hideFromLauncher ?: false)
    }
    var isolateData by remember(app.packageName, prefill?.id) {
        mutableStateOf(prefill?.isolateData ?: true)
    }
    var disableDebuggable by remember(app.packageName, prefill?.id) { mutableStateOf(true) }
    var rewriteAuthorities by remember(app.packageName, prefill?.id) {
        mutableStateOf(prefill?.isolateData ?: true)
    }
    var reproducibleBuild by remember(app.packageName, prefill?.id) { mutableStateOf(true) }
    var selectedHue by remember(app.packageName, prefill?.id) {
        mutableIntStateOf(prefill?.iconHueShift ?: 0)
    }
    var selectedPresetId by remember(app.packageName, prefill?.id) {
        mutableStateOf(prefill?.iconPresetId ?: "original")
    }
    var strippedPermissions by remember(app.packageName, prefill?.id) {
        mutableStateOf(
            prefill?.strippedPermissionsCsv
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
        )
    }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var buildStartedHere by rememberSaveable(app.packageName, prefill?.id) { mutableStateOf(false) }
    var ownerRightsConfirmed by rememberSaveable(app.packageName, prefill?.id) { mutableStateOf(false) }
    var showBuildRightsDialog by remember { mutableStateOf(false) }
    var dialogRightsChecked by remember { mutableStateOf(false) }

    val iconVariants = remember {
        listOf(
            IconVariantSwatch("original", "Original", 0, Color(0xFF10B981)),
            IconVariantSwatch("neon_cyan", "Cyan", 180, Color(0xFF00D2FF)),
            IconVariantSwatch("violet_glow", "Violet", 270, Color(0xFF8B5CF6)),
            IconVariantSwatch("amber_gold", "Amber", 45, Color(0xFFF59E0B))
        )
    }

    val allPermissions = inspection?.manifestSummary?.permissions ?: app.requestedPermissions
    val exportApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val out = state.lastBuildOutput
        if (uri != null && out != null) {
            onExportApkUri(out.outputApkPath, uri)
        }
    }
    val attachSourceApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onAttachSourceApkUri(uri)
        }
    }

    val packageValid = !changePackageName || FMSIntegrityEngine.isValidPackageName(targetPackage)
    val compatAnalysis = inspection?.compatibility
    val hasBlockingCompatibility = app.hasSplitApks ||
        compatAnalysis == null ||
        compatAnalysis.status == CompatibilityStatus.UNSUPPORTED ||
        compatAnalysis.blockers.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("app_config_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(lang, "Kembali", "Back"), tint = theme.onBackground)
                }
                Text(
                    text = tr(lang, "Konfigurasi Aplikasi", "App Configuration"),
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.onBackground
                )
            }
            val compat = inspection?.compatibility
            if (compat != null) {
                val badgeColor = when (compat.status) {
                    CompatibilityStatus.VERIFIED, CompatibilityStatus.COMPATIBLE -> theme.accentSuccess
                    CompatibilityStatus.LIMITED -> theme.accentGold
                    CompatibilityStatus.UNSUPPORTED -> Color(0xFFEF4444)
                }
                FmsStatusBadge(label = "${compat.status.name} ${compat.score}%", color = badgeColor)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Selected App Banner
            item {
                val previewBmp = remember(app.iconBitmap, selectedHue) {
                    app.iconBitmap?.let { FMSIconEngine.applyPreviewHue(it, selectedHue).asImageBitmap() }
                }
                FmsGlassCard(theme = theme, cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (previewBmp != null) {
                            Image(
                                bitmap = previewBmp,
                                contentDescription = app.appName,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val fallbackAccent = (iconVariants.firstOrNull { it.hueShift == selectedHue } ?: iconVariants.first()).accentColor
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(fallbackAccent.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Android, contentDescription = app.appName, tint = theme.primary, modifier = Modifier.size(30.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = app.appName, style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                            Text(text = app.packageName, style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "v${app.versionName} · ${String.format("%.1f MB", app.apkSizeBytes / (1024.0 * 1024.0))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.primary
                            )
                        }
                    }
                }
            }

            if (app.sourceApkPath == null) {
                item {
                    FmsGlassCard(
                        theme = theme,
                        cornerRadius = 16.dp,
                        borderColor = theme.accentGold.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = tr(lang, "File APK Sumber Diperlukan", "Source APK File Required"),
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.accentGold
                            )
                            Text(
                                text = tr(
                                    lang,
                                    "Aplikasi sumber (${app.packageName}) tidak ditemukan di perangkat ini. Lampirkan berkas .apk melalui SAF untuk membangun klon dari resep tersimpan.",
                                    "Source app (${app.packageName}) is not installed on this device. Attach an .apk via SAF to build from this recipe."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.onSurfaceMuted
                            )
                            OutlinedButton(
                                onClick = { attachSourceApkLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                                enabled = !state.isBusyApkIo,
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("attach_source_apk_button")
                            ) {
                                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.isBusyApkIo) {
                                        tr(lang, "Memeriksa APK...", "Inspecting APK...")
                                    } else {
                                        tr(lang, "Lampirkan File .apk Sumber", "Attach Source .apk File")
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (compatAnalysis != null && (compatAnalysis.status == CompatibilityStatus.UNSUPPORTED || compatAnalysis.blockers.isNotEmpty())) {
                item {
                    FmsGlassCard(
                        theme = theme,
                        cornerRadius = 16.dp,
                        borderColor = Color(0xFFEF4444).copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth().testTag("compatibility_blocker_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tr(lang, "Kloning APK Tunggal Tidak Didukung", "Single-APK Cloning Unsupported"),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFEF4444)
                            )
                            Text(
                                text = compatAnalysis.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.onBackground
                            )
                            compatAnalysis.blockers.forEach { blocker ->
                                Text("• $blocker", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            } else if (inspection != null && compatAnalysis != null) {
                val manifest = inspection.manifestSummary
                val hasNotableAlert = manifest.referenceAuthoritiesCount > 0 ||
                    compatAnalysis.status == CompatibilityStatus.LIMITED ||
                    manifest.customPermissions.isNotEmpty() ||
                    manifest.extractNativeLibs == false
                item {
                    val accent = if (manifest.referenceAuthoritiesCount > 0 || compatAnalysis.status == CompatibilityStatus.LIMITED) {
                        theme.accentGold
                    } else {
                        theme.accentSuccess
                    }
                    FmsGlassCard(
                        theme = theme,
                        cornerRadius = 16.dp,
                        borderColor = accent.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth().testTag("pre_clone_compatibility_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr(lang, "Pemeriksaan Pra-Kloning", "Pre-Clone Check"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.onBackground
                                )
                                FmsStatusBadge(
                                    label = compatAnalysis.status.name,
                                    color = accent
                                )
                            }
                            Text(
                                text = "minSdk ${manifest.minSdkVersion ?: app.minSdk} · targetSdk ${manifest.targetSdkVersion ?: app.targetSdk} · " +
                                    if (inspection.nativeAbis.isEmpty()) "Pure DEX" else "ABI: ${inspection.nativeAbis.joinToString()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.onSurfaceMuted
                            )
                            if (manifest.customPermissions.isNotEmpty()) {
                                Text(
                                    text = tr(
                                        lang,
                                        "• ${manifest.customPermissions.size} izin kustom paket sumber akan ditulis ulang otomatis ke target package.",
                                        "• ${manifest.customPermissions.size} custom source permissions will be rewritten automatically."
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.primary
                                )
                            }
                            if (manifest.referenceAuthoritiesCount > 0) {
                                Text(
                                    text = tr(
                                        lang,
                                        "• Peringatan: ${manifest.referenceAuthoritiesCount} provider memakai @string/... yang berisiko bentrok jika diinstal berdampingan dengan app asli.",
                                        "• Warning: ${manifest.referenceAuthoritiesCount} @string/... provider authorities may conflict alongside original app."
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.accentGold
                                )
                            }
                            if (!hasNotableAlert) {
                                Text(
                                    text = tr(
                                        lang,
                                        "Manifest, DEX, dan ABI lolos pemeriksaan awal. Validasi 7 tahap otomatis akan berjalan setelah build.",
                                        "Manifest, DEX, and ABI passed initial checks. 7-stage automated validation runs after build."
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.onSurfaceMuted
                                )
                            }
                        }
                    }
                }
            }

            // Clone Name Input
            item {
                Text(tr(lang, "Nama Kloning", "Clone Name"), style = MaterialTheme.typography.labelLarge, color = theme.onBackground)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = cloneName,
                    onValueChange = { cloneName = it.take(48) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("clone_name_input")
                )
            }

            // 4-Swatch Live App Icon Hue Selector matching mockup
            item {
                Text(tr(lang, "Ikon Aplikasi (Live Preview)", "App Icon (Live Preview)"), style = MaterialTheme.typography.labelLarge, color = theme.onBackground)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    iconVariants.forEachIndexed { idx, variant ->
                        val isSelected = selectedPresetId == variant.id
                        val swatchBmp = remember(app.iconBitmap, variant.hueShift) {
                            app.iconBitmap?.let { FMSIconEngine.applyPreviewHue(it, variant.hueShift).asImageBitmap() }
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) theme.primary else theme.border,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .background(theme.surfaceElevated.copy(alpha = 0.7f))
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    selectedPresetId = variant.id
                                    selectedHue = variant.hueShift
                                }
                                .padding(8.dp)
                                .testTag("icon_swatch_${variant.id}")
                        ) {
                            if (swatchBmp != null) {
                                Image(
                                    bitmap = swatchBmp,
                                    contentDescription = variant.label,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(variant.accentColor.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Android, contentDescription = variant.label, tint = variant.accentColor)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(variant.label, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted)
                        }
                        if (idx < iconVariants.lastIndex) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = theme.onSurfaceMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Core Toggles matching mockup
            item {
                FmsGlassCard(theme = theme, cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        ConfigToggleRow(
                            title = tr(lang, "Ubah Package Name", "Change Package Name"),
                            subtitle = tr(lang, "Pasang berdampingan sebagai aplikasi terpisah", "Install side-by-side with its own package ID"),
                            checked = changePackageName,
                            onCheckedChange = { changePackageName = it },
                            theme = theme,
                            tag = "toggle_change_package"
                        )
                        AnimatedVisibility(visible = changePackageName) {
                            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                OutlinedTextField(
                                    value = targetPackage,
                                    onValueChange = { targetPackage = it.trim().take(150) },
                                    singleLine = true,
                                    isError = !packageValid,
                                    label = { Text(tr(lang, "Target Package Name", "Target Package Name")) },
                                    supportingText = {
                                        if (!packageValid) {
                                            Text(tr(lang, "Gunakan format valid mis. com.app.zclone1", "Use valid package syntax e.g. com.app.zclone1"))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("target_package_input")
                                )
                            }
                        }
                        HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                        ConfigToggleRow(
                            title = tr(lang, "Sembunyikan dari Launcher", "Hide from Launcher"),
                            subtitle = tr(lang, "Hapus CATEGORY_LAUNCHER dari manifest klon", "Strip CATEGORY_LAUNCHER from cloned manifest"),
                            checked = hideFromLauncher,
                            onCheckedChange = { hideFromLauncher = it },
                            theme = theme,
                            tag = "toggle_hide_launcher"
                        )
                        HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                        ConfigToggleRow(
                            title = tr(lang, "Isolasi Data & Authority", "Isolate Data & Authorities"),
                            subtitle = tr(lang, "Tulis ulang ContentProvider authorities ke package klon", "Rewrite ContentProvider authorities to target package"),
                            checked = isolateData,
                            onCheckedChange = {
                                isolateData = it
                                rewriteAuthorities = it
                            },
                            theme = theme,
                            tag = "toggle_isolate_data"
                        )
                    }
                }
            }

            // Custom Permissions Row matching mockup
            item {
                FmsGlassCard(
                    theme = theme,
                    cornerRadius = 16.dp,
                    onClick = { showPermissionDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("custom_permissions_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = theme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(tr(lang, "Izin Kustom (Privacy Engine)", "Custom Permissions (Privacy Engine)"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                                Text(
                                    text = tr(
                                        lang,
                                        "${strippedPermissions.size} dari ${allPermissions.size} izin akan dihapus",
                                        "${strippedPermissions.size} of ${allPermissions.size} permissions will be stripped"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.onSurfaceMuted
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = theme.onSurfaceMuted)
                    }
                }
            }

            // Dhedry Animated Loading + Ownership Attestation Status Card
            item {
                val hasLauncher = inspection?.manifestSummary?.launcherActivityName != null
                val minSdkTooLow = (inspection?.manifestSummary?.minSdkVersion ?: 24) < 21
                val dhedryWillInject = !hideFromLauncher && !minSdkTooLow && (inspection == null || hasLauncher)
                FmsGlassCard(
                    theme = theme,
                    cornerRadius = 16.dp,
                    borderColor = if (dhedryWillInject) theme.primary.copy(alpha = 0.55f) else theme.border,
                    modifier = Modifier.fillMaxWidth().testTag("dhedry_injection_info_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = theme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Dhedry Animated Loading",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.onBackground
                                )
                            }
                            FmsStatusBadge(
                                label = if (dhedryWillInject) "AUTO INJECT" else "SKIPPED",
                                color = if (dhedryWillInject) theme.accentSuccess else theme.accentGold
                            )
                        }
                        Text(
                            text = when {
                                hideFromLauncher -> tr(
                                    lang,
                                    "Dilewati karena 'Sembunyikan dari Launcher' aktif.",
                                    "Skipped because 'Hide from Launcher' is enabled."
                                )
                                minSdkTooLow -> tr(
                                    lang,
                                    "Dilewati otomatis karena minSdk < 21 agar tidak terjadi crash multidex saat startup.",
                                    "Automatically skipped for minSdk < 21 to avoid Dalvik multidex startup crash."
                                )
                                inspection != null && !hasLauncher -> tr(
                                    lang,
                                    "Aktivitas MAIN/LAUNCHER tidak ditemukan di APK ini; splash tidak disuntikkan.",
                                    "No MAIN/LAUNCHER activity found in this APK; splash injection skipped."
                                )
                                else -> tr(
                                    lang,
                                    "Animasi robot Android ~6.5 detik + audio narasi MP3 offline selalu aktif disuntikkan ke DEX terpisah tanpa mengubah resources.arsc.",
                                    "~6.5s Android robot animation + always-on offline MP3 narration injected via separate DEX without altering resources.arsc."
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.onSurfaceMuted
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { ownerRightsConfirmed = !ownerRightsConfirmed }
                        ) {
                            Checkbox(
                                checked = ownerRightsConfirmed,
                                onCheckedChange = { ownerRightsConfirmed = it },
                                modifier = Modifier.testTag("build_ownership_inline_checkbox")
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = tr(
                                    lang,
                                    "Saya konfirmasi memiliki atau berhak memodifikasi APK ini.",
                                    "I confirm I own or hold rights to modify this APK."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.onBackground
                            )
                        }
                    }
                }
            }

            // Expandable Advanced Settings & Smart Compatibility
            item {
                FmsGlassCard(
                    theme = theme,
                    cornerRadius = 16.dp,
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.fillMaxWidth().testTag("advanced_settings_toggle")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Settings, contentDescription = null, tint = theme.secondary)
                                Spacer(Modifier.width(10.dp))
                                Text(tr(lang, "Pengaturan Lanjutan & Kompatibilitas", "Advanced Settings & Compatibility"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                            }
                            Icon(
                                imageVector = if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = theme.onSurfaceMuted
                            )
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ConfigToggleRow(
                                    title = tr(lang, "Hapus Flag Debuggable", "Disable Debuggable Flag"),
                                    subtitle = tr(lang, "Pastikan klon tidak berjalan dalam mode debug", "Ensure cloned manifest disables android:debuggable"),
                                    checked = disableDebuggable,
                                    onCheckedChange = { disableDebuggable = it },
                                    theme = theme,
                                    tag = "toggle_disable_debuggable"
                                )
                                HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                                ConfigToggleRow(
                                    title = tr(lang, "Reproducible Build (Deterministik)", "Reproducible Build (Deterministic)"),
                                    subtitle = tr(lang, "Urutkan entri ZIP & timestamp kanonik agar SHA-256 build identik", "Sort ZIP entries & apply canonical timestamps for identical SHA-256"),
                                    checked = reproducibleBuild,
                                    onCheckedChange = { reproducibleBuild = it },
                                    theme = theme,
                                    tag = "toggle_reproducible_build"
                                )
                                if (inspection != null) {
                                    Surface(
                                        color = theme.surface,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, theme.border)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Smart Compatibility", style = MaterialTheme.typography.labelLarge, color = theme.primary)
                                            Text(inspection.compatibility.notes, style = MaterialTheme.typography.bodyMedium, color = theme.onBackground)
                                            if (inspection.nativeAbis.isNotEmpty()) {
                                                Text(
                                                    text = "Native ABIs: ${inspection.nativeAbis.joinToString()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = theme.onSurfaceMuted
                                                )
                                            }
                                            inspection.compatibility.warnings.forEach { w ->
                                                Text("• $w", style = MaterialTheme.typography.bodyMedium, color = theme.accentGold)
                                            }
                                            inspection.compatibility.blockers.forEach { b ->
                                                Text("• $b", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444))
                                            }
                                        }
                                    }
                                }
                                state.activeDependencyGraph?.let { graph ->
                                    Surface(
                                        color = theme.surface,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.45f)),
                                        modifier = Modifier.fillMaxWidth().testTag("dependency_graph_card")
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("APK Dependency Graph (FMS Engine Core V1)", style = MaterialTheme.typography.labelLarge, color = theme.primary)
                                            val exportedCount = graph.components.count { it.exported }
                                            Text(
                                                text = "DEX: ${graph.dexFiles.joinToString(", ").ifEmpty { "-" }} (${graph.dexFiles.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = theme.onBackground
                                            )
                                            Text(
                                                text = tr(
                                                    lang,
                                                    "Komponen: ${graph.components.size} total ($exportedCount exported) · <queries>: ${graph.queriedPackages.size} pkg / ${graph.queriedActions.size} action",
                                                    "Components: ${graph.components.size} total ($exportedCount exported) · <queries>: ${graph.queriedPackages.size} pkg / ${graph.queriedActions.size} action"
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = theme.onSurfaceMuted
                                            )
                                            if (graph.providerAuthorities.isNotEmpty()) {
                                                Text(
                                                    text = "Authorities: ${graph.providerAuthorities.take(3).joinToString(", ")}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = theme.onSurfaceMuted
                                                )
                                            }
                                            if (graph.nativeLibs.isEmpty()) {
                                                Text(
                                                    text = "ELF Native .so: Pure DEX (0 .so)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = theme.accentSuccess
                                                )
                                            } else {
                                                Text(
                                                    text = "ELF Native .so (${graph.nativeLibs.size}):",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = theme.onBackground
                                                )
                                                graph.nativeLibs.take(4).forEach { lib ->
                                                    val deps = lib.neededLibraries.ifEmpty { listOf("no DT_NEEDED") }.joinToString(", ")
                                                    Text(
                                                        text = "• ${lib.abi}/${lib.fileName} -> [$deps]",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = theme.onSurfaceMuted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Progress / Output Card
            if (state.isBuildingClone && buildStartedHere) {
                val pct = (state.buildProgress * 100).toInt().coerceIn(0, 100)
                item {
                    FmsGlassCard(theme = theme, cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$pct% · ${state.buildProgressDetail}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = onCancelBuild,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_build_button")
                                ) {
                                    Text(tr(lang, "Batalkan", "Cancel"), color = Color(0xFFEF4444))
                                }
                            }
                            LinearProgressIndicator(
                                progress = { state.buildProgress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                                color = theme.primary,
                                trackColor = theme.surfaceElevated
                            )
                        }
                    }
                }
            }

            val failure = state.lastBuildFailure
            if (buildStartedHere && !state.isBuildingClone && failure != null) {
                item {
                    CloneFailureRecoveryCard(
                        failure = failure,
                        theme = theme,
                        lang = lang
                    )
                }
            }

            val output = state.lastBuildOutput
            if (buildStartedHere && !state.isBuildingClone && output != null) {
                item {
                    BuildSuccessCard(
                        output = output,
                        deviceVerification = state.deviceVerification,
                        theme = theme,
                        lang = lang,
                        onInstall = {
                            onInstallApk(output.outputApkPath)
                        },
                        onExport = {
                            exportApkLauncher.launch("${output.targetPackage}.apk")
                        },
                        onVerifyInstalled = {
                            onVerifyInstalled(output.targetPackage)
                        },
                        onTestLaunch = {
                            onTestLaunchClone(output.targetPackage)
                        },
                        onConfirmMainFunction = { passed ->
                            onConfirmMainFunction(output.targetPackage, passed)
                        }
                    )
                }
            }
        }

        // Bottom Create Clone CTA matching mockup
        Surface(
            color = theme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val canBuild = !state.isBuildingClone &&
                    packageValid &&
                    app.sourceApkPath != null &&
                    cloneName.isNotBlank() &&
                    !hasBlockingCompatibility
                FmsGradientButton(
                    text = when {
                        state.isBuildingClone -> tr(lang, "Membangun APK...", "Building APK...")
                        app.sourceApkPath == null -> tr(lang, "Lampirkan File .apk Sumber", "Attach Source .apk First")
                        hasBlockingCompatibility && inspection == null -> tr(lang, "Memeriksa APK...", "Inspecting APK...")
                        hasBlockingCompatibility -> tr(lang, "Tidak Didukung (Split / No-DEX)", "Unsupported APK Structure")
                        else -> tr(lang, "Buat Klon", "Create Clone")
                    },
                    theme = theme,
                    enabled = canBuild,
                    onClick = {
                        val srcPath = app.sourceApkPath ?: return@FmsGradientButton
                        if (!ownerRightsConfirmed) {
                            dialogRightsChecked = false
                            showBuildRightsDialog = true
                        } else {
                            buildStartedHere = true
                            onBuildClone(
                                CloneBuildRequest(
                                    sourceApkPath = srcPath,
                                    sourcePackage = app.packageName,
                                    targetPackage = if (changePackageName) targetPackage else app.packageName,
                                    sourceAppName = app.appName,
                                    cloneName = cloneName.trim(),
                                    iconPresetId = selectedPresetId,
                                    iconHueShift = selectedHue,
                                    changePackageName = changePackageName,
                                    hideFromLauncher = hideFromLauncher,
                                    isolateData = isolateData,
                                    strippedPermissions = strippedPermissions,
                                    disableDebuggable = disableDebuggable,
                                    rewriteProviderAuthorities = rewriteAuthorities,
                                    ownerConfirmedModificationRights = true,
                                    injectDhedryLoading = !hideFromLauncher,
                                    reproducibleBuild = reproducibleBuild,
                                    embedDependencyGraph = true
                                ),
                                prefill?.id
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("create_clone_button")
                )
            }
        }
    }

    if (showBuildRightsDialog) {
        AlertDialog(
            onDismissRequest = { showBuildRightsDialog = false },
            modifier = Modifier.testTag("build_ownership_dialog"),
            title = {
                Text(tr(lang, "Konfirmasi Hak Modifikasi APK", "APK Modification Rights Confirmation"))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(
                            lang,
                            "Sebelum membangun klon '${cloneName.trim()}', konfirmasikan bahwa Anda adalah pemilik sah atau memiliki izin resmi untuk memodifikasi APK ${app.packageName}.",
                            "Before building '${cloneName.trim()}', confirm that you own or have explicit legal authorization to modify ${app.packageName}."
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { dialogRightsChecked = !dialogRightsChecked }
                    ) {
                        Checkbox(
                            checked = dialogRightsChecked,
                            onCheckedChange = { dialogRightsChecked = it },
                            modifier = Modifier.testTag("build_ownership_checkbox")
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tr(
                                lang,
                                "Saya memiliki atau berhak sah memodifikasi APK ini.",
                                "I own or hold legal rights to modify this APK."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val srcPath = app.sourceApkPath ?: return@Button
                        ownerRightsConfirmed = true
                        showBuildRightsDialog = false
                        buildStartedHere = true
                        onBuildClone(
                            CloneBuildRequest(
                                sourceApkPath = srcPath,
                                sourcePackage = app.packageName,
                                targetPackage = if (changePackageName) targetPackage else app.packageName,
                                sourceAppName = app.appName,
                                cloneName = cloneName.trim(),
                                iconPresetId = selectedPresetId,
                                iconHueShift = selectedHue,
                                changePackageName = changePackageName,
                                hideFromLauncher = hideFromLauncher,
                                isolateData = isolateData,
                                strippedPermissions = strippedPermissions,
                                disableDebuggable = disableDebuggable,
                                rewriteProviderAuthorities = rewriteAuthorities,
                                ownerConfirmedModificationRights = true,
                                injectDhedryLoading = !hideFromLauncher,
                                reproducibleBuild = reproducibleBuild,
                                embedDependencyGraph = true
                            ),
                            prefill?.id
                        )
                    },
                    enabled = dialogRightsChecked,
                    modifier = Modifier.testTag("build_ownership_confirm_button")
                ) {
                    Text(tr(lang, "Konfirmasi & Bangun Klon", "Confirm & Build Clone"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBuildRightsDialog = false },
                    modifier = Modifier.testTag("build_ownership_cancel_button")
                ) {
                    Text(tr(lang, "Batal", "Cancel"))
                }
            }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(tr(lang, "Izin Kustom & Privacy Engine", "Custom Permissions & Privacy Engine")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val sensitive = setOf(
                                "android.permission.CAMERA",
                                "android.permission.RECORD_AUDIO",
                                "android.permission.ACCESS_FINE_LOCATION",
                                "android.permission.ACCESS_COARSE_LOCATION",
                                "android.permission.READ_CONTACTS",
                                "android.permission.READ_CALL_LOG"
                            )
                            strippedPermissions = allPermissions.filter { it in sensitive }.toSet()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("apply_privacy_preset_button")
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr(lang, "Terapkan Preset Privasi (Hapus Izin Sensitif)", "Apply Privacy Preset (Strip Sensitive)"))
                    }

                    if (allPermissions.isEmpty()) {
                        Text(tr(lang, "Manifest tidak mendeklarasikan uses-permission.", "No uses-permission entries declared."))
                    } else {
                        LazyColumn(modifier = Modifier.height(260.dp)) {
                            items(allPermissions) { perm ->
                                val stripped = perm in strippedPermissions
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .clickable {
                                            strippedPermissions = if (stripped) strippedPermissions - perm else strippedPermissions + perm
                                        }
                                ) {
                                    Checkbox(
                                        checked = stripped,
                                        onCheckedChange = {
                                            strippedPermissions = if (stripped) strippedPermissions - perm else strippedPermissions + perm
                                        }
                                    )
                                    Column {
                                        Text(perm.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium)
                                        Text(perm, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPermissionDialog = false }) {
                    Text(tr(lang, "Simpan", "Done"))
                }
            }
        )
    }
}

@Composable
private fun CloneFailureRecoveryCard(
    failure: CloneBuildFailureInfo,
    theme: FmsThemePreset,
    lang: String
) {
    val errorTint = MaterialTheme.colorScheme.error
    FmsGlassCard(
        theme = theme,
        cornerRadius = 18.dp,
        borderColor = errorTint,
        modifier = Modifier.fillMaxWidth().testTag("clone_failure_recovery_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = errorTint)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (failure.failedStageNumber != null) {
                        tr(lang, "Klon Dibatalkan — Tahap ${failure.failedStageNumber}: ${failure.failedStageTitle}", "Build Aborted — Stage ${failure.failedStageNumber}: ${failure.failedStageTitle}")
                    } else {
                        tr(lang, "Klon Dibatalkan — ${failure.failedStageTitle}", "Build Aborted — ${failure.failedStageTitle}")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = errorTint
                )
            }
            Text(
                text = failure.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onBackground
            )
            Surface(
                color = theme.accentSuccess.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, theme.accentSuccess.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = theme.accentSuccess, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = failure.sourcePreservedNotice,
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.accentSuccess
                    )
                }
            }
            Text(
                text = tr(lang, "Saran: ${failure.suggestedAction}", "Suggestion: ${failure.suggestedAction}"),
                style = MaterialTheme.typography.labelSmall,
                color = theme.onSurfaceMuted
            )
        }
    }
}

@Composable
private fun BuildSuccessCard(
    output: CloneBuildOutput,
    deviceVerification: DeviceCloneVerificationState?,
    theme: FmsThemePreset,
    lang: String,
    onInstall: () -> Unit,
    onExport: () -> Unit,
    onVerifyInstalled: () -> Unit,
    onTestLaunch: () -> Unit,
    onConfirmMainFunction: (Boolean) -> Unit
) {
    val verificationMatches = deviceVerification?.targetPackage == output.targetPackage
    val report = output.validationReport
    FmsGlassCard(
        theme = theme,
        cornerRadius = 18.dp,
        borderColor = theme.accentSuccess,
        modifier = Modifier.fillMaxWidth().testTag("build_success_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = theme.accentSuccess)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tr(lang, "Klon APK Valid & Terverifikasi (7/7 Otomatis)", "Clone APK Valid & Verified (7/7 Automated)"),
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.onBackground
                )
            }
            Text("Package: ${output.targetPackage}", style = MaterialTheme.typography.bodyMedium, color = theme.primary)
            Text("APK SHA-256: ${output.outputSha256.take(24)}...", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted)
            Text("Signer SHA-256: ${output.signingSha256.take(24)}...", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted)
            Text(
                text = tr(
                    lang,
                    "Manifest diubah: ${output.modifiedManifest} · Ikon dipatch: ${output.patchedIconsCount} · Izin dihapus: ${output.strippedPermissionsCount}",
                    "Manifest patched: ${output.modifiedManifest} · Icons patched: ${output.patchedIconsCount} · Perms stripped: ${output.strippedPermissionsCount}"
                ),
                style = MaterialTheme.typography.labelSmall,
                color = theme.onSurfaceMuted
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (output.dhedrySplashInjected) theme.accentSuccess else theme.accentGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (output.dhedrySplashInjected) {
                        tr(
                            lang,
                            "Dhedry Animated Loading aktif (${output.dhedryDexEntryName ?: "classes2.dex"} -> ${output.dhedryTargetActivity?.substringAfterLast('.') ?: "Launcher"})",
                            "Dhedry Animated Loading injected (${output.dhedryDexEntryName ?: "classes2.dex"} -> ${output.dhedryTargetActivity?.substringAfterLast('.') ?: "Launcher"})"
                        )
                    } else {
                        tr(
                            lang,
                            "Dhedry Splash dilewati (mode tersembunyi / non-launcher / minSdk < 21)",
                            "Dhedry Splash skipped (hidden, non-launcher, or minSdk < 21)"
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (output.dhedrySplashInjected) theme.accentSuccess else theme.onSurfaceMuted
                )
            }

            if (output.warnings.isNotEmpty()) {
                Surface(
                    color = theme.accentGold.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, theme.accentGold.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        output.warnings.forEach { w ->
                            Text("• $w", style = MaterialTheme.typography.labelSmall, color = theme.accentGold)
                        }
                    }
                }
            }

            if (report != null) {
                Surface(
                    color = theme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, theme.border),
                    modifier = Modifier.fillMaxWidth().testTag("validation_10_stage_card")
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr(lang, "Pemeriksaan 10 Tahap Sebelum & Sesudah Kloning", "10-Stage Pre/Post-Clone Verification"),
                            style = MaterialTheme.typography.labelLarge,
                            color = theme.primary
                        )
                        report.steps.forEach { step ->
                            val resolvedStep = if (deviceVerification != null && verificationMatches && step.category == ValidationCheckCategory.DEVICE_RUNTIME) {
                                when (step.stageNumber) {
                                    8 -> step.copy(status = deviceVerification.stage8Installed, detail = deviceVerification.stage8Detail)
                                    9 -> step.copy(status = deviceVerification.stage9FirstLaunch, detail = deviceVerification.stage9Detail)
                                    10 -> step.copy(status = deviceVerification.stage10MainFunction, detail = deviceVerification.stage10Detail)
                                    else -> step
                                }
                            } else step
                            ValidationStepRow(step = resolvedStep, theme = theme, lang = lang)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("install_clone_button")
                ) {
                    Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(lang, "Pasang APK", "Install APK"))
                }
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("export_clone_button")
                ) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(lang, "Ekspor APK", "Export APK"))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onVerifyInstalled,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("verify_installed_button")
                ) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr(lang, "Cek Terpasang", "Verify Install"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onTestLaunch,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("test_launch_clone_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr(lang, "Uji Luncurkan", "Test Launch"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onConfirmMainFunction(true) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("confirm_functional_pass_button")
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = theme.accentSuccess, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr(lang, "Tahap 10: Normal", "Stage 10: OK"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { onConfirmMainFunction(false) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("report_functional_fail_button")
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr(lang, "Ada Force Close", "Report Crash"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ValidationStepRow(
    step: ApkValidationStep,
    theme: FmsThemePreset,
    lang: String
) {
    val errorColor = MaterialTheme.colorScheme.error
    val (icon, tint) = when (step.status) {
        ValidationCheckStatus.PASS -> Icons.Rounded.CheckCircle to theme.accentSuccess
        ValidationCheckStatus.WARNING -> Icons.Rounded.WarningAmber to theme.accentGold
        ValidationCheckStatus.FAILED -> Icons.Rounded.ErrorOutline to errorColor
        ValidationCheckStatus.PENDING_DEVICE_TEST -> Icons.Rounded.HourglassTop to theme.onSurfaceMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = step.status.name,
            tint = tint,
            modifier = Modifier.padding(top = 2.dp).size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${step.stageNumber}. ${tr(lang, step.titleId, step.titleEn)}",
                style = MaterialTheme.typography.labelMedium,
                color = theme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = step.detail,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

@Composable
private fun ConfigToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    theme: FmsThemePreset,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
