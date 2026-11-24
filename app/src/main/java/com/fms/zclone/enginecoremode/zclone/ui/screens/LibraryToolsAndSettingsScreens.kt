package com.fms.zclone.enginecoremode.zclone.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fms.zclone.enginecoremode.R
import com.fms.zclone.enginecoremode.zclone.ui.ZCloneUiState
import com.fms.clone.BinaryVersionDiffReport
import com.fms.clone.InstalledAppInfo
import com.fms.clone.ParityStatus
import com.fms.clone.RiskLevel
import com.fms.core.FmsModuleState
import com.fms.data.LocalBuildHistoryRecord
import com.fms.data.LocalCloneRecord
import com.fms.data.LocalSecurityReportRecord
import com.fms.device.LicenseTier
import com.fms.ui.FMSThemeEngine
import com.fms.ui.FmsFontChoice
import com.fms.ui.FmsGlassCard
import com.fms.ui.FmsGradientButton
import com.fms.ui.FmsIconStyle
import com.fms.ui.FmsNeonWaveBackdrop
import com.fms.ui.FmsStatusBadge
import com.fms.ui.FmsThemePreset
import com.fms.ui.fmsIconContainerShape
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyClonesLibraryScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onNavigateCloneApps: () -> Unit,
    onDeleteClone: (String) -> Unit,
    onInstallApk: (String) -> Unit,
    onExportApkUri: (String, android.net.Uri) -> Unit,
    onBuildFromRecipe: (LocalCloneRecord) -> Unit,
    onRestoreSnapshot: (LocalBuildHistoryRecord) -> Unit = {},
    onCompareBinaryApks: (String, String) -> Unit = { _, _ -> },
    onClearBinaryDiff: () -> Unit = {},
    onExportBinaryDiffUri: (BinaryVersionDiffReport, android.net.Uri) -> Unit = { _, _ -> }
) {
    val lang = state.languageCode
    var showHistoryTab by remember { mutableStateOf(false) }
    var showDiffDialog by remember { mutableStateOf(false) }
    var diffTabIndex by remember { mutableIntStateOf(0) }
    var diffSource by remember { mutableStateOf<LocalCloneRecord?>(null) }
    var diffTarget by remember { mutableStateOf<LocalCloneRecord?>(null) }
    var binaryPathA by remember { mutableStateOf<String?>(null) }
    var binaryPathB by remember { mutableStateOf<String?>(null) }
    var pendingExportPath by remember { mutableStateOf<String?>(null) }

    val availableApkArtifacts = remember(state.localClones, state.buildHistory, state.activeSelectedApp) {
        val list = linkedMapOf<String, String>()
        state.buildHistory.forEach { h ->
            if (h.outputPath.isNotBlank() && File(h.outputPath).exists()) {
                list[h.outputPath] = "${h.targetPackage} (${h.status})"
            }
        }
        state.localClones.forEach { c ->
            val p = c.outputApkPath
            if (!p.isNullOrBlank() && File(p).exists() && p !in list) {
                list[p] = "${c.cloneName} (${c.targetPackage})"
            }
        }
        state.activeSelectedApp?.sourceApkPath?.let { src ->
            if (File(src).exists() && src !in list) {
                list[src] = "Source: ${state.activeSelectedApp.appName}"
            }
        }
        list.entries.map { it.key to it.value }
    }

    val exportApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val p = pendingExportPath
        if (uri != null && p != null) {
            onExportApkUri(p, uri)
        }
        pendingExportPath = null
    }

    val exportDiffJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val diff = state.activeBinaryDiff
        if (uri != null && diff != null) {
            onExportBinaryDiffUri(diff, uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(tr(lang, "Pustaka Kloning", "Clone Library"), style = MaterialTheme.typography.headlineMedium, color = theme.onBackground)
                Text(
                    text = "${state.localClones.size} ${tr(lang, "resep tersimpan", "saved recipes")} · ${state.buildHistory.size} ${tr(lang, "riwayat build", "builds")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceMuted
                )
            }
            Button(
                onClick = onNavigateCloneApps,
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("library_new_clone_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr(lang, "Baru", "New"))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = !showHistoryTab,
                onClick = { showHistoryTab = false },
                label = { Text(tr(lang, "Konfigurasi (${state.localClones.size})", "Configs (${state.localClones.size})")) },
                modifier = Modifier.testTag("library_tab_configs")
            )
            FilterChip(
                selected = showHistoryTab,
                onClick = { showHistoryTab = true },
                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Time Machine (${state.buildHistory.size})") },
                modifier = Modifier.testTag("library_tab_history")
            )
            if (state.localClones.size >= 2 || availableApkArtifacts.size >= 2) {
                FilterChip(
                    selected = showDiffDialog,
                    onClick = {
                        if (state.localClones.size >= 2) {
                            diffSource = state.localClones[0]
                            diffTarget = state.localClones[1]
                        }
                        if (availableApkArtifacts.size >= 2) {
                            val a = availableApkArtifacts[0].first
                            val b = availableApkArtifacts[1].first
                            binaryPathA = a
                            binaryPathB = b
                            if (state.localClones.size < 2) {
                                diffTabIndex = 1
                                onCompareBinaryApks(a, b)
                            }
                        }
                        showDiffDialog = true
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Diff") },
                    modifier = Modifier.testTag("library_version_diff_button")
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!showHistoryTab) {
            if (state.localClones.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    FmsGlassCard(theme = theme, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Layers, contentDescription = null, tint = theme.primary, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(tr(lang, "Belum ada klon dibangun", "No cloned builds yet"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = tr(lang, "Pilih aplikasi terinstal atau impor file .apk untuk memulai kloning pertama Anda.", "Pick an installed app or import an .apk to create your first clone."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.onSurfaceMuted
                            )
                            Spacer(Modifier.height(14.dp))
                            FmsGradientButton(text = tr(lang, "Mulai Kloning", "Start Cloning"), theme = theme, onClick = onNavigateCloneApps)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.localClones, key = { it.id }) { item ->
                        val apkPath = item.outputApkPath
                        FmsGlassCard(theme = theme, cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.cloneName, style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                                        Text(item.targetPackage, style = MaterialTheme.typography.bodyMedium, color = theme.primary)
                                        Text("${tr(lang, "Sumber", "Source")}: ${item.sourcePackage}", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted)
                                    }
                                    FmsStatusBadge(label = item.buildStatus, color = theme.accentSuccess)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (apkPath != null && File(apkPath).exists()) {
                                        OutlinedButton(
                                            onClick = { onInstallApk(apkPath) },
                                            modifier = Modifier.weight(1f).height(42.dp)
                                        ) {
                                            Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr(lang, "Pasang", "Install"))
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pendingExportPath = apkPath
                                                exportApkLauncher.launch("${item.targetPackage}.apk")
                                            },
                                            modifier = Modifier.weight(1f).height(42.dp)
                                        ) {
                                            Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr(lang, "Ekspor", "Export"))
                                        }
                                        IconButton(onClick = { onBuildFromRecipe(item) }) {
                                            Icon(Icons.Rounded.Build, contentDescription = tr(lang, "Bangun ulang", "Rebuild"), tint = theme.primary)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onBuildFromRecipe(item) },
                                            modifier = Modifier.weight(1f).height(42.dp)
                                        ) {
                                            Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr(lang, "Bangun APK", "Build APK"))
                                        }
                                    }
                                    IconButton(onClick = { onDeleteClone(item.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = tr(lang, "Hapus", "Delete"), tint = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Clone Time Machine build history
            if (state.buildHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    FmsGlassCard(theme = theme, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = theme.primary, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = tr(lang, "Belum ada riwayat build", "No Time Machine builds yet"),
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.onBackground
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = tr(
                                    lang,
                                    "Setiap APK klon yang selesai dibangun mencatat SHA-256 dan jalur file di Time Machine.",
                                    "Every completed APK build records its SHA-256 checksum and artifact path in Time Machine."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.onSurfaceMuted
                            )
                        }
                    }
                }
            } else {
                val fmt = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.buildHistory, key = { it.id }) { rec ->
                        val historyFileExists = remember(rec.outputPath) { File(rec.outputPath).exists() }
                        FmsGlassCard(theme = theme, cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(rec.targetPackage, style = MaterialTheme.typography.titleMedium, color = theme.onBackground, modifier = Modifier.weight(1f))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (rec.configSnapshotJson.isNotBlank()) {
                                            FmsStatusBadge(label = "SNAPSHOT", color = theme.primary)
                                        }
                                        FmsStatusBadge(label = rec.status, color = theme.accentSuccess)
                                    }
                                }
                                Text("SHA-256: ${rec.sha256.take(32)}...", style = MaterialTheme.typography.labelSmall, color = theme.primary)
                                Text(fmt.format(Date(rec.createdAtMs)), style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted)
                                Spacer(Modifier.height(2.dp))
                                OutlinedButton(
                                    onClick = { onRestoreSnapshot(rec) },
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("restore_snapshot_${rec.id}")
                                ) {
                                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr(lang, "Pulihkan Snapshot ke App Config", "Restore Snapshot to App Config"))
                                }
                                if (historyFileExists) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onInstallApk(rec.outputPath) },
                                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr(lang, "Pasang", "Install"))
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pendingExportPath = rec.outputPath
                                                exportApkLauncher.launch("${rec.targetPackage}.apk")
                                            },
                                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr(lang, "Ekspor", "Export"))
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

    if (showDiffDialog) {
        val closeDiff: () -> Unit = {
            showDiffDialog = false
            diffSource = null
            diffTarget = null
            onClearBinaryDiff()
        }
        AlertDialog(
            onDismissRequest = closeDiff,
            title = { Text("Version Diff (FMS Engine Core V1)") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabRow(selectedTabIndex = diffTabIndex, containerColor = Color.Transparent, contentColor = theme.primary) {
                        Tab(
                            selected = diffTabIndex == 0,
                            onClick = { diffTabIndex = 0 },
                            enabled = state.localClones.size >= 2,
                            text = { Text(tr(lang, "Resep", "Recipes")) }
                        )
                        Tab(
                            selected = diffTabIndex == 1,
                            onClick = {
                                diffTabIndex = 1
                                val a = binaryPathA
                                val b = binaryPathB
                                if (a != null && b != null && state.activeBinaryDiff == null) {
                                    onCompareBinaryApks(a, b)
                                }
                            },
                            enabled = availableApkArtifacts.size >= 2,
                            text = { Text(tr(lang, "Biner APK", "APK Binary")) }
                        )
                    }

                    if (diffTabIndex == 0 && diffSource != null && diffTarget != null) {
                        val a = diffSource!!
                        val b = diffTarget!!
                        if (state.localClones.size > 2) {
                            Text(tr(lang, "Pilih Klon A:", "Pick Clone A:"), style = MaterialTheme.typography.labelSmall, color = theme.primary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(state.localClones, key = { "src_${it.id}" }) { cand ->
                                    FilterChip(
                                        selected = cand.id == a.id,
                                        onClick = { diffSource = cand },
                                        label = { Text(cand.cloneName, maxLines = 1) }
                                    )
                                }
                            }
                            Text(tr(lang, "Pilih Klon B:", "Pick Clone B:"), style = MaterialTheme.typography.labelSmall, color = theme.primary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(state.localClones, key = { "tgt_${it.id}" }) { cand ->
                                    FilterChip(
                                        selected = cand.id == b.id,
                                        onClick = { diffTarget = cand },
                                        label = { Text(cand.cloneName, maxLines = 1) }
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                        val permsA = a.strippedPermissionsCsv.split(",").filter { it.isNotBlank() }.toSet()
                        val permsB = b.strippedPermissionsCsv.split(",").filter { it.isNotBlank() }.toSet()
                        val onlyInA = (permsA - permsB).map { it.substringAfterLast('.') }
                        val onlyInB = (permsB - permsA).map { it.substringAfterLast('.') }
                        Text("Source A: ${a.sourcePackage} -> ${a.targetPackage}")
                        Text("Source B: ${b.sourcePackage} -> ${b.targetPackage}")
                        HorizontalDivider()
                        Text("Hide Launcher: ${a.hideFromLauncher} vs ${b.hideFromLauncher}")
                        Text("Isolate Data: ${a.isolateData} vs ${b.isolateData}")
                        Text("Hue Shift: ${a.iconHueShift}° vs ${b.iconHueShift}°")
                        Text("Status Compat: ${a.compatibilityStatus} vs ${b.compatibilityStatus}")
                        Text("Stripped Only A: ${onlyInA.ifEmpty { listOf("None") }.joinToString(", ")}")
                        Text("Stripped Only B: ${onlyInB.ifEmpty { listOf("None") }.joinToString(", ")}")
                    } else if (diffTabIndex == 1 && availableApkArtifacts.size >= 2) {
                        Text("APK A:", style = MaterialTheme.typography.labelSmall, color = theme.primary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableApkArtifacts, key = { "binA_${it.first}" }) { (path, label) ->
                                FilterChip(
                                    selected = binaryPathA == path,
                                    onClick = {
                                        binaryPathA = path
                                        binaryPathB?.let { onCompareBinaryApks(path, it) }
                                    },
                                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                        Text("APK B:", style = MaterialTheme.typography.labelSmall, color = theme.primary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableApkArtifacts, key = { "binB_${it.first}" }) { (path, label) ->
                                FilterChip(
                                    selected = binaryPathB == path,
                                    onClick = {
                                        binaryPathB = path
                                        binaryPathA?.let { onCompareBinaryApks(it, path) }
                                    },
                                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                        if (state.isAnalyzingGraphOrDiff) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                        }
                        state.activeBinaryDiff?.let { diff ->
                            HorizontalDivider()
                            Text("Package: ${diff.packageA} -> ${diff.packageB}", style = MaterialTheme.typography.bodyMedium)
                            Text("Version: ${diff.versionNameA} (${diff.versionCodeA}) -> ${diff.versionNameB} (${diff.versionCodeB})", style = MaterialTheme.typography.labelSmall)
                            Text("SDK: min ${diff.minSdkA ?: "?"}->${diff.minSdkB ?: "?"}, target ${diff.targetSdkA ?: "?"}->${diff.targetSdkB ?: "?"}", style = MaterialTheme.typography.labelSmall)
                            val sign = if (diff.sizeDeltaBytes >= 0) "+" else ""
                            Text("Size Delta: $sign${diff.sizeDeltaBytes} bytes", style = MaterialTheme.typography.labelSmall, color = theme.primary)
                            Text("+ Permissions (${diff.addedPermissions.size}): ${diff.addedPermissions.take(4).joinToString { it.substringAfterLast('.') }.ifEmpty { "None" }}", style = MaterialTheme.typography.labelSmall)
                            Text("- Permissions (${diff.removedPermissions.size}): ${diff.removedPermissions.take(4).joinToString { it.substringAfterLast('.') }.ifEmpty { "None" }}", style = MaterialTheme.typography.labelSmall)
                            Text("+ Components (${diff.addedComponents.size}) / - (${diff.removedComponents.size})", style = MaterialTheme.typography.labelSmall)
                            Text("+ DEX (${diff.addedDexFiles.size}): ${diff.addedDexFiles.joinToString().ifEmpty { "None" }}", style = MaterialTheme.typography.labelSmall)
                            Text("+ Native .so (${diff.addedNativeLibs.size}) / - (${diff.removedNativeLibs.size})", style = MaterialTheme.typography.labelSmall)
                            Text("+ Dhedry/Pro (${diff.addedDhedryModules.size}): ${diff.addedDhedryModules.joinToString().ifEmpty { "None" }}", style = MaterialTheme.typography.labelSmall)
                            OutlinedButton(
                                onClick = { exportDiffJsonLauncher.launch("zclone_version_diff.json") },
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("export_binary_diff_json_button")
                            ) {
                                Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "Ekspor Diff JSON", "Export Diff JSON"))
                            }
                        }
                    } else {
                        Text(
                            text = tr(lang, "Diperlukan minimal 2 konfigurasi atau 2 APK tersimpan untuk membandingkan.", "At least 2 saved recipes or 2 built APKs are required to diff."),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = closeDiff) {
                    Text(tr(lang, "Tutup", "Close"))
                }
            }
        )
    }
}

@Composable
fun ToolsHubScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onNavigate: (ZCloneRoute) -> Unit,
    onCancelBatchJob: (String) -> Unit = {},
    onCancelAllBatch: () -> Unit = {}
) {
    val lang = state.languageCode
    val tools = listOf(
        Triple(ZCloneRoute.CLONE_APPS, Icons.Rounded.ContentCopy, "Clone & Batch Apps" to tr(lang, "Inspeksi manifest, ubah package, & antrekan batch clone", "Inspect manifest, rename package, & batch clone")),
        Triple(ZCloneRoute.THEME_STUDIO, Icons.Rounded.Palette, "Theme Studio" to tr(lang, "Kustomisasi palet Dark Neon, font TTF lokal, & Auto Theme", "Customize Dark Neon palettes, local TTF fonts, & Auto Theme")),
        Triple(ZCloneRoute.DEVICE_MANAGER, Icons.Rounded.Devices, "Device Manager" to tr(lang, "Kelola perangkat tersinkron & kuota install UUID", "Manage synced devices & install UUID quota")),
        Triple(ZCloneRoute.SECURITY_SCANNER, Icons.Rounded.Shield, "Security Scanner" to tr(lang, "Audit komponen exported, izin berbahaya, & sertifikat APK", "Audit exported components, dangerous perms, & APK certs")),
        Triple(ZCloneRoute.FMS_HUB, Icons.Rounded.Layers, "FMSCloneEngine Hub" to tr(lang, "Matriks paritas lengkap, status modul FMS & log diagnostik", "Full parity matrix, FMS module status & diagnostic logs")),
        Triple(ZCloneRoute.SETTINGS, Icons.Rounded.Backup, "Backup & Restore" to tr(lang, "Ekspor & impor resep klon JSON tervalidasi skema", "Export & import schema-validated JSON clone recipes"))
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(tr(lang, "Alat & Mesin FMS", "Tools & FMS Engines"), style = MaterialTheme.typography.headlineMedium, color = theme.onBackground)
            Text(
                text = tr(lang, "Akses cepat seluruh modul FMSCloneEngine tanpa root", "Quick access to all non-root FMSCloneEngine modules"),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onSurfaceMuted
            )
        }

        items(tools) { (route, icon, titles) ->
            FmsGlassCard(
                theme = theme,
                cornerRadius = 18.dp,
                onClick = { onNavigate(route) },
                modifier = Modifier.fillMaxWidth().testTag("tool_card_${route.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(fmsIconContainerShape(theme.iconStyle))
                            .background(theme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = theme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(titles.first, style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                        Text(titles.second, style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = theme.onSurfaceMuted)
                }
            }
        }

        if (state.backgroundJobs.isNotEmpty()) {
            val hasActiveJobs = state.backgroundJobs.any {
                it.state == com.fms.task.FmsJobState.QUEUED || it.state == com.fms.task.FmsJobState.RUNNING
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FMSJobQueue (${state.backgroundJobs.size})", style = MaterialTheme.typography.titleMedium, color = theme.primary)
                    if (hasActiveJobs) {
                        TextButton(
                            onClick = onCancelAllBatch,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("tools_cancel_all_jobs_button")
                        ) {
                            Text(tr(lang, "Batalkan Semua", "Cancel All"), color = Color(0xFFEF4444))
                        }
                    }
                }
            }
            items(state.backgroundJobs, key = { it.jobId }) { job ->
                val isActive = job.state == com.fms.task.FmsJobState.QUEUED || job.state == com.fms.task.FmsJobState.RUNNING
                val pct = (job.progress * 100).toInt().coerceIn(0, 100)
                FmsGlassCard(theme = theme, cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${job.title} ($pct%)", style = MaterialTheme.typography.bodyMedium, color = theme.onBackground, modifier = Modifier.weight(1f))
                            FmsStatusBadge(label = job.state.name, color = if (job.state == com.fms.task.FmsJobState.CANCELLED) theme.accentGold else theme.primary)
                            if (isActive) {
                                IconButton(
                                    onClick = { onCancelBatchJob(job.jobId) },
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("tools_cancel_job_${job.jobId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = tr(lang, "Batalkan ${job.title}", "Cancel ${job.title}"),
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { job.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                        )
                        Text(job.detail, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeStudioScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    autoThemeEnabled: Boolean,
    onBack: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onToggleAutoTheme: (Boolean) -> Unit,
    onFontChoiceSelected: (FmsFontChoice) -> Unit,
    onIconStyleSelected: (FmsIconStyle) -> Unit,
    onApplyCustomTheme: (String, Color, Color, FmsFontChoice, FmsIconStyle) -> Unit
) {
    val lang = state.languageCode
    var activeTab by remember { mutableIntStateOf(0) }
    var selectedPresetId by remember(theme.id) { mutableStateOf(theme.id) }
    var customPrimary by remember(theme.primary) { mutableStateOf(theme.primary) }
    var customSecondary by remember(theme.secondary) { mutableStateOf(theme.secondary) }
    var customFont by remember(theme.fontChoice) { mutableStateOf(theme.fontChoice) }
    var customIconStyle by remember(theme.iconStyle) { mutableStateOf(theme.iconStyle) }

    val colorSwatches = listOf(
        Color(0xFF00D2FF),
        Color(0xFF8B5CF6),
        Color(0xFF3A7BD5),
        Color(0xFF10B981),
        Color(0xFFF97316),
        Color(0xFFEC4899)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("theme_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(lang, "Kembali", "Back"), tint = theme.onBackground)
                }
                Text("Theme Studio", style = MaterialTheme.typography.titleLarge, color = theme.onBackground)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (autoThemeEnabled) {
                    FmsStatusBadge(label = "AUTO", color = theme.accentGold)
                }
                IconButton(
                    onClick = { onToggleAutoTheme(!autoThemeEnabled) },
                    modifier = Modifier.testTag("theme_auto_toggle_button")
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = "Auto Theme",
                        tint = if (autoThemeEnabled) theme.accentGold else theme.primary
                    )
                }
            }
        }

        TabRow(selectedTabIndex = activeTab, containerColor = Color.Transparent, contentColor = theme.primary) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text(tr(lang, "Tema", "Themes")) })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text(tr(lang, "Warna", "Colors")) })
            Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Font") })
            Tab(selected = activeTab == 3, onClick = { activeTab = 3 }, text = { Text(tr(lang, "Ikon", "Icons")) })
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                0 -> {
                    val presets = FMSThemeEngine.presets
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val rows = presets.chunked(3)
                        items(rows) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { preset ->
                                    val selected = selectedPresetId == preset.id
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(18.dp))
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) theme.primary else theme.border,
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .background(theme.surfaceElevated.copy(alpha = 0.7f))
                                            .clickable {
                                                selectedPresetId = preset.id
                                                if (!preset.customTheme) {
                                                    onApplyPreset(preset.id)
                                                } else {
                                                    activeTab = 1
                                                }
                                            }
                                            .padding(10.dp)
                                            .testTag("theme_preset_${preset.id}")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(64.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(preset.background, preset.primary, preset.secondary)
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (preset.customTheme) {
                                                Icon(Icons.Rounded.Add, contentDescription = preset.name, tint = Color.White, modifier = Modifier.size(28.dp))
                                            } else if (selected) {
                                                Icon(Icons.Rounded.CheckCircle, contentDescription = preset.name, tint = Color.White, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = if (preset.customTheme) tr(lang, "Kustom +", "Custom +") else preset.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = theme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(tr(lang, "Warna Primer", "Primary Color"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            colorSwatches.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(if (customPrimary == c) 3.dp else 1.dp, Color.White, CircleShape)
                                        .clickable { customPrimary = c; selectedPresetId = "custom" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (customPrimary == c) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                        Text(tr(lang, "Warna Sekunder", "Secondary Color"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            colorSwatches.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(if (customSecondary == c) 3.dp else 1.dp, Color.White, CircleShape)
                                        .clickable { customSecondary = c; selectedPresetId = "custom" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (customSecondary == c) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr(lang, "Font Lokal Bundled (Google Fonts TTF)", "Bundled Local Fonts (TTF)"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                        FmsFontChoice.entries.forEach { fc ->
                            FmsGlassCard(
                                theme = theme,
                                onClick = {
                                    customFont = fc
                                    onFontChoiceSelected(fc)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = customFont == fc, onClick = { customFont = fc; onFontChoiceSelected(fc) })
                                    Spacer(Modifier.width(8.dp))
                                    Text(fc.displayName, style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                                }
                            }
                        }
                    }
                }
                3 -> {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr(lang, "Gaya Ikon UI", "UI Icon Style"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                        FmsIconStyle.entries.forEach { st ->
                            FmsGlassCard(
                                theme = theme,
                                onClick = {
                                    customIconStyle = st
                                    onIconStyleSelected(st)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = customIconStyle == st, onClick = { customIconStyle = st; onIconStyleSelected(st) })
                                    Spacer(Modifier.width(8.dp))
                                    Text(st.displayName, style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            FmsGradientButton(
                text = tr(lang, "Terapkan Tema", "Apply Theme"),
                theme = theme,
                onClick = {
                    if (selectedPresetId == "custom") {
                        onApplyCustomTheme("Custom Studio", customPrimary, customSecondary, customFont, customIconStyle)
                    } else {
                        onApplyPreset(selectedPresetId)
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("apply_theme_button")
            )
        }
    }
}

@Composable
fun DeviceManagerScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onBack: () -> Unit,
    onToggleDeviceStatus: (String, String) -> Unit,
    onUpdateDeviceLabel: (String) -> Unit,
    onOpenAuthDialog: () -> Unit
) {
    val lang = state.languageCode
    val activeCount = when {
        state.userId != null && state.cloudDevices.isNotEmpty() -> state.cloudDevices.count { it.second.status == "ACTIVE" }
        else -> 1
    }
    var showEditLabelDialog by remember { mutableStateOf(false) }
    var labelDraft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("device_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(lang, "Kembali", "Back"), tint = theme.onBackground)
            }
            Text("Device Manager", style = MaterialTheme.typography.titleLarge, color = theme.onBackground)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("device_manager_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Card matching mockup
            item {
                FmsGlassCard(theme = theme, cornerRadius = 20.dp, borderColor = theme.accentSuccess.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(theme.accentSuccess.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = theme.accentSuccess)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(tr(lang, "Perangkat Saya", "My Devices"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                                Text(
                                    text = "$activeCount / ${state.deviceLimit} ${tr(lang, "Perangkat Aktif (${state.userTier.displayName})", "Active Devices (${state.userTier.displayName})")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.onSurfaceMuted
                                )
                            }
                        }
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = theme.accentSuccess, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Local Install UUID card
            state.localDevice?.let { local ->
                item {
                    FmsGlassCard(theme = theme, cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${local.deviceLabel} (${tr(lang, "Perangkat Ini", "This Device")})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        labelDraft = local.deviceLabel
                                        showEditLabelDialog = true
                                    },
                                    modifier = Modifier.testTag("edit_device_label_button")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = tr(lang, "Ubah Label", "Edit Label"), tint = theme.primary)
                                }
                                val cloudStatusForLocal = state.cloudDevices
                                    .firstOrNull { it.second.installUuid == local.installUuid }
                                    ?.second
                                    ?.status
                                val badgeLabel = when {
                                    state.userId == null -> tr(lang, "Lokal", "Local")
                                    cloudStatusForLocal != null -> cloudStatusForLocal
                                    state.syncState == com.fms.cloud.FmsSyncState.ERROR -> "SYNC ERR"
                                    else -> tr(lang, "Sinkron", "Syncing")
                                }
                                val badgeColor = when (cloudStatusForLocal) {
                                    "ACTIVE" -> theme.accentSuccess
                                    "INACTIVE" -> theme.accentGold
                                    "REVOKED" -> Color(0xFFEF4444)
                                    else -> if (state.syncState == com.fms.cloud.FmsSyncState.ERROR) Color(0xFFEF4444) else theme.primary
                                }
                                FmsStatusBadge(label = badgeLabel, color = badgeColor)
                            }
                            Text("${local.modelName} · ${local.androidVersion}", style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
                            Text("Install UUID: ${local.installUuid} (AndroidKeyStore)", style = MaterialTheme.typography.labelSmall, color = theme.primary)
                        }
                    }
                }
            }

            if (state.isLocalDeviceRevoked && state.userId != null) {
                item {
                    FmsGlassCard(
                        theme = theme,
                        cornerRadius = 18.dp,
                        borderColor = Color(0xFFEF4444).copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().testTag("device_revoked_alert_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = tr(lang, "Perangkat Berstatus REVOKED (Terkunci)", "Device Status: REVOKED (Locked)"),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFCA5A5)
                            )
                            Text(
                                text = tr(
                                    lang,
                                    "Sinkronisasi cloud dari instalasi ini diblokir secara permanen oleh aturan keamanan. Keluar akun untuk kembali ke mode lokal atau gunakan perangkat terotorisasi lain.",
                                    "Cloud sync from this install is permanently locked by security rules. Sign out to return to local mode or use another authorized device."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.onSurfaceMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onOpenAuthDialog,
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("open_auth_from_revoked_button")
                                ) {
                                    Text(tr(lang, "Kelola Sesi / Keluar", "Manage Session / Sign Out"))
                                }
                            }
                        }
                    }
                }
            }

            if (state.userId == null) {
                item {
                    FmsGlassCard(theme = theme, cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(tr(lang, "Sinkronisasi Multi-Perangkat", "Multi-Device Cloud Sync"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                            Text(
                                text = tr(lang, "Masuk dengan Google untuk mengelola perangkat tertaut dan sinkronisasi resep antar perangkat.", "Sign in with Google to manage linked devices and sync configs across devices."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.onSurfaceMuted
                            )
                            Button(onClick = onOpenAuthDialog, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Text(tr(lang, "Masuk dengan Google", "Sign in with Google"))
                            }
                        }
                    }
                }
            } else {
                items(state.cloudDevices, key = { it.first }) { (docId, dev) ->
                    val isActive = dev.status == "ACTIVE"
                    val isRevoked = dev.status == "REVOKED"
                    val isThisDevice = dev.installUuid == state.localDevice?.installUuid
                    val statusColor = when (dev.status) {
                        "ACTIVE" -> theme.accentSuccess
                        "INACTIVE" -> theme.accentGold
                        else -> Color(0xFFEF4444)
                    }
                    FmsGlassCard(
                        theme = theme,
                        cornerRadius = 16.dp,
                        borderColor = if (isRevoked) Color(0xFFEF4444).copy(alpha = 0.65f) else if (isThisDevice) theme.primary.copy(alpha = 0.5f) else theme.border,
                        modifier = Modifier.fillMaxWidth().testTag("cloud_device_card_$docId")
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = dev.deviceLabel.ifBlank { dev.modelName },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = theme.onBackground
                                        )
                                        if (isThisDevice) {
                                            FmsStatusBadge(label = tr(lang, "Perangkat Ini", "This Device"), color = theme.primary)
                                        }
                                    }
                                    Text("${dev.modelName} · ${dev.androidVersion}", style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
                                }
                                FmsStatusBadge(label = dev.status, color = statusColor)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isRevoked) {
                                    Text(
                                        text = tr(lang, "Terkunci permanen (REVOKED)", "Permanently locked (REVOKED)"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFFFCA5A5),
                                        modifier = Modifier.testTag("locked_revoked_label_$docId")
                                    )
                                } else {
                                    if (!isThisDevice) {
                                        OutlinedButton(
                                            onClick = { onToggleDeviceStatus(docId, "REVOKED") },
                                            enabled = state.isOnline && !state.isLocalDeviceRevoked,
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.minimumInteractiveComponentSize().testTag("revoke_device_$docId")
                                        ) {
                                            Text("Revoke", color = Color(0xFFEF4444))
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val nextStatus = if (isActive) "INACTIVE" else "ACTIVE"
                                            onToggleDeviceStatus(docId, nextStatus)
                                        },
                                        enabled = state.isOnline && !(isThisDevice && isActive) && !state.isLocalDeviceRevoked,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_device_$docId")
                                    ) {
                                        Text(
                                            text = if (isActive) tr(lang, "Nonaktifkan", "Deactivate") else tr(lang, "Aktifkan", "Activate"),
                                            color = if (isActive) theme.onSurfaceMuted else theme.accentSuccess
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

    if (showEditLabelDialog) {
        AlertDialog(
            onDismissRequest = { showEditLabelDialog = false },
            title = { Text(tr(lang, "Ubah Label Perangkat", "Edit Device Label")) },
            text = {
                OutlinedTextField(
                    value = labelDraft,
                    onValueChange = { labelDraft = it.take(60) },
                    singleLine = true,
                    label = { Text(tr(lang, "Nama Perangkat", "Device Name")) },
                    modifier = Modifier.fillMaxWidth().testTag("device_label_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDeviceLabel(labelDraft)
                        showEditLabelDialog = false
                    },
                    enabled = labelDraft.isNotBlank()
                ) {
                    Text(tr(lang, "Simpan", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLabelDialog = false }) {
                    Text(tr(lang, "Batal", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun SecurityScannerScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onBack: () -> Unit,
    onRequestSystemApps: () -> Unit = {},
    onScanApp: (InstalledAppInfo) -> Unit,
    onScanApkUri: (android.net.Uri) -> Unit,
    onSelectSavedReport: (LocalSecurityReportRecord) -> Unit
) {
    val lang = state.languageCode
    val scan = state.activeSecurityScan
    var searchQuery by remember { mutableStateOf("") }
    var includeSystem by remember { mutableStateOf(false) }

    val apkScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onScanApkUri(uri)
    }

    val sourceList = if (includeSystem) state.systemApps else state.installedApps
    val filteredApps = remember(sourceList, searchQuery) {
        if (searchQuery.isBlank()) sourceList
        else sourceList.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("scanner_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(lang, "Kembali", "Back"), tint = theme.onBackground)
                }
                Text("Security Scanner", style = MaterialTheme.typography.titleLarge, color = theme.onBackground)
            }
            OutlinedButton(
                onClick = { apkScanLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                enabled = !state.isBusyApkIo,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(40.dp).testTag("scan_external_apk_button")
            ) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Scan .apk", color = theme.primary)
            }
        }

        if (state.isBusyApkIo) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (scan != null) {
                item {
                    val riskColor = when (scan.riskLevel) {
                        RiskLevel.LOW -> theme.accentSuccess
                        RiskLevel.MEDIUM -> theme.accentGold
                        RiskLevel.HIGH -> Color(0xFFEF4444)
                    }
                    FmsGlassCard(theme = theme, cornerRadius = 20.dp, borderColor = riskColor, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(scan.appName, style = MaterialTheme.typography.titleLarge, color = theme.onBackground)
                                    Text(scan.packageName, style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
                                }
                                FmsStatusBadge(label = "${scan.riskLevel.name} · ${scan.riskScore}/100", color = riskColor)
                            }
                            Text(scan.summary, style = MaterialTheme.typography.bodyMedium, color = theme.onBackground)
                            Text("Cert SHA-256: ${scan.certSha256Prefix}", style = MaterialTheme.typography.labelSmall, color = theme.primary)
                            if (scan.findings.isNotEmpty()) {
                                HorizontalDivider(color = theme.border)
                                scan.findings.forEach { finding ->
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("• ${finding.title} (${finding.severity.name})", style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                                        Text(finding.detail, style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
                                        Text("${tr(lang, "Saran", "Fix")}: ${finding.remediation}", style = MaterialTheme.typography.labelSmall, color = theme.accentSuccess)
                                    }
                                }
                            }
                            Text(scan.limitationNotice, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted)
                        }
                    }
                }
            }

            if (state.localSecurityReports.isNotEmpty()) {
                item {
                    Text(
                        text = tr(lang, "Riwayat Laporan (${state.localSecurityReports.size})", "Saved Scan Reports (${state.localSecurityReports.size})"),
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.onBackground
                    )
                }
                items(state.localSecurityReports, key = { it.id }) { rep ->
                    val repColor = when (rep.riskLevel) {
                        "LOW" -> theme.accentSuccess
                        "MEDIUM" -> theme.accentGold
                        else -> Color(0xFFEF4444)
                    }
                    FmsGlassCard(
                        theme = theme,
                        cornerRadius = 14.dp,
                        onClick = { onSelectSavedReport(rep) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rep.appName, style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                                Text(rep.packageName, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FmsStatusBadge(label = "${rep.riskLevel} · ${rep.riskScore}", color = repColor)
                        }
                    }
                }
            }

            item {
                Text(tr(lang, "Pilih Aplikasi untuk Dipindai", "Select App to Scan"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text(tr(lang, "Cari aplikasi atau package...", "Search app or package...")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = tr(lang, "Bersihkan", "Clear"))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("scanner_search_input")
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !includeSystem,
                        onClick = { includeSystem = false },
                        label = { Text(tr(lang, "Terinstal (${state.installedApps.size})", "Installed (${state.installedApps.size})")) }
                    )
                    FilterChip(
                        selected = includeSystem,
                        onClick = {
                            includeSystem = true
                            onRequestSystemApps()
                        },
                        label = {
                            val suffix = if (state.hasLoadedSystemApps) " (${state.systemApps.size})" else ""
                            Text(tr(lang, "Sistem$suffix", "System$suffix"))
                        }
                    )
                }
                if (includeSystem && state.isLoadingSystemApps) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
                }
            }

            items(filteredApps, key = { it.packageName }) { app ->
                FmsGlassCard(
                    theme = theme,
                    cornerRadius = 16.dp,
                    onClick = { onScanApp(app) },
                    modifier = Modifier.fillMaxWidth().testTag("scan_app_${app.packageName}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                            Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FmsStatusBadge(label = tr(lang, "Pindai", "Scan"), color = theme.primary, icon = Icons.Rounded.Security)
                    }
                }
            }
        }
    }
}

@Composable
fun FmsHubScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onBack: () -> Unit
) {
    val lang = state.languageCode
    var parityFilter by remember { mutableStateOf("ALL") }
    var searchParity by remember { mutableStateOf("") }

    val filteredParity = remember(state.parityRecords, parityFilter, searchParity) {
        state.parityRecords.filter { rec ->
            val matchStatus = parityFilter == "ALL" || rec.status.name == parityFilter
            val matchText = searchParity.isBlank() ||
                rec.featureName.contains(searchParity, true) ||
                rec.officialCategory.contains(searchParity, true)
            matchStatus && matchText
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("fms_hub_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(lang, "Kembali", "Back"), tint = theme.onBackground)
            }
            Text("FMSCloneEngine Hub", style = MaterialTheme.typography.titleLarge, color = theme.onBackground)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mockup FMSCloneEngine Showcase Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, theme.primary.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                ) {
                    FmsNeonWaveBackdrop(theme = theme, animateWaves = false, modifier = Modifier.matchParentSize())
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(theme.primary.copy(alpha = 0.16f))
                                .border(1.5.dp, theme.primary, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Layers, contentDescription = "FMSCloneEngine", tint = theme.primary, modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("FMS Engine Core V1", style = MaterialTheme.typography.headlineMedium, color = theme.onBackground)
                        Text(
                            text = tr(lang, "Menggerakkan ZCloner PRO — manajemen & kloning APK", "Powering ZCloner PRO — APK inspection & cloning"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.onSurfaceMuted
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Copyright FMS Clone Engine V1",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.primary
                        )
                        Spacer(Modifier.height(14.dp))
                        val highlights = listOf(
                            "Independent Engine (com.fms.* · v1.1.0)",
                            "Dependency Graph (AXML, DEX & ELF DT_NEEDED)",
                            "Reproducible Builder & Binary Version Diff",
                            "Bounded Queue with Real Cancellation",
                            "Enterprise Local Keystore Security"
                        )
                        highlights.forEach { text ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = theme.accentSuccess, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(text, style = MaterialTheme.typography.bodyMedium, color = theme.onBackground)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        FmsStatusBadge(
                            label = tr(lang, "Engine Siap (${state.moduleStatuses.count { it.state == FmsModuleState.READY }}/${state.moduleStatuses.size} Modul)", "Engine Ready (${state.moduleStatuses.count { it.state == FmsModuleState.READY }}/${state.moduleStatuses.size} Modules)"),
                            color = theme.accentSuccess,
                            icon = Icons.Rounded.CheckCircle
                        )
                    }
                }
            }

            // Feature Parity Matrix Header + Filters
            item {
                Text(tr(lang, "Matriks Paritas Fitur (${state.parityRecords.size})", "Feature Parity Matrix (${state.parityRecords.size})"), style = MaterialTheme.typography.titleLarge, color = theme.onBackground)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchParity,
                    onValueChange = { searchParity = it },
                    singleLine = true,
                    placeholder = { Text(tr(lang, "Filter fitur atau kategori...", "Filter feature or category...")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL", "IMPLEMENTED", "LIMITED", "PLANNED", "UNSUPPORTED").forEach { f ->
                        item {
                            FilterChip(
                                selected = parityFilter == f,
                                onClick = { parityFilter = f },
                                label = { Text(f) }
                            )
                        }
                    }
                }
            }

            items(filteredParity, key = { it.featureId }) { rec ->
                val statusColor = when (rec.status) {
                    ParityStatus.IMPLEMENTED, ParityStatus.VERIFIED, ParityStatus.TESTED -> theme.accentSuccess
                    ParityStatus.LIMITED, ParityStatus.PLANNED -> theme.accentGold
                    else -> Color(0xFFEF4444)
                }
                FmsGlassCard(theme = theme, cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${rec.featureId} · ${rec.featureName}", style = MaterialTheme.typography.titleMedium, color = theme.onBackground, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(6.dp))
                            FmsStatusBadge(label = rec.status.name, color = statusColor)
                        }
                        Text("${rec.officialCategory} · ${rec.fmsModule}", style = MaterialTheme.typography.labelSmall, color = theme.primary)
                        Text(rec.description, style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
                        Text("${tr(lang, "Batasan", "Limits")}: ${rec.technicalLimitations}", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceMuted)
                    }
                }
            }

            // Recent FMSLogger Entries
            item {
                Text("FMSLogging (${state.fmsLogs.size})", style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
            }
            items(state.fmsLogs.takeLast(15).reversed()) { log ->
                Text(
                    text = "[${log.level.name}] ${log.tag}: ${log.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onBack: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onExportBackupUri: (android.net.Uri) -> Unit,
    onImportBackupUri: (android.net.Uri) -> Unit,
    onClearWorkCache: () -> Unit,
    onOpenAuthDialog: () -> Unit,
    onOpenTierDialog: () -> Unit
) {
    val lang = state.languageCode
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogBody by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportBackupUri(uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportBackupUri(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr(lang, "Kembali", "Back"), tint = theme.onBackground)
                }
                Text(tr(lang, "Pengaturan", "Settings"), style = MaterialTheme.typography.headlineMedium, color = theme.onBackground)
            }
        }

        // Brand Identity Card matching mockup
        item {
            FmsGlassCard(theme = theme, cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_zclone_brand_mark),
                        contentDescription = "ZCloner PRO",
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ZCloner PRO", style = MaterialTheme.typography.titleLarge, color = theme.onBackground)
                        Text("v1.0.0 (Build 001) · FMS Engine Core V1", style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
                        Text("Copyright FMS Clone Engine V1", style = MaterialTheme.typography.labelSmall, color = theme.primary)
                    }
                    OutlinedButton(onClick = onOpenTierDialog, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = theme.accentGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(state.userTier.displayName, color = theme.accentGold)
                    }
                }
            }
        }

        // Language & Cloud Sync Card
        item {
            FmsGlassCard(theme = theme, cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = theme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(tr(lang, "Bahasa Antarmuka", "Interface Language"), style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                                Text(if (lang == "en") "English (US)" else "Indonesia (ID + istilah teknis)", style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = lang == "id", onClick = { onSetLanguage("id") }, label = { Text("ID") }, modifier = Modifier.testTag("lang_id_chip"))
                            FilterChip(selected = lang == "en", onClick = { onSetLanguage("en") }, label = { Text("EN") }, modifier = Modifier.testTag("lang_en_chip"))
                        }
                    }

                    HorizontalDivider(color = theme.border.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().clickable { onOpenAuthDialog() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val iconTint = when {
                                state.isLocalDeviceRevoked -> Color(0xFFEF4444)
                                state.userId != null && !state.isOnline -> theme.accentGold
                                else -> theme.accentSuccess
                            }
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = iconTint)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Cloud Sync & Akun Google", style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                                val cloudSubtitle = when {
                                    state.isLocalDeviceRevoked -> "${state.userEmail ?: "Akun"} · Perangkat REVOKED"
                                    state.userId != null && !state.isOnline -> "${state.userEmail ?: "Akun"} · Offline (tercache)"
                                    state.userEmail != null -> state.userEmail
                                    else -> tr(lang, "Belum terhubung · Berjalan lokal", "Not signed in · Local mode")
                                }
                                Text(cloudSubtitle, style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
                            }
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = theme.onSurfaceMuted)
                    }
                }
            }
        }

        // Backup & Restore JSON (SAF)
        item {
            FmsGlassCard(theme = theme, cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backup & Restore (JSON <= 2 MB)", style = MaterialTheme.typography.titleMedium, color = theme.onBackground)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { exportBackupLauncher.launch("zclone_backup.json") },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("export_backup_button")
                        ) {
                            Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "Ekspor JSON", "Export JSON"))
                        }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("import_backup_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "Impor JSON", "Import JSON"))
                        }
                    }
                }
            }
        }

        // Settings Menu Rows matching mockup
        item {
            FmsGlassCard(theme = theme, cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRowItem(
                        icon = Icons.Rounded.Shield,
                        tint = theme.accentSuccess,
                        title = tr(lang, "Keamanan & Privasi", "Security & Privacy"),
                        onClick = {
                            infoDialogTitle = tr(lang, "Keamanan & Privasi", "Security & Privacy")
                            infoDialogBody = tr(
                                lang,
                                "ZClone menggunakan AndroidKeyStore AES-256-GCM, keystore lokal pengguna untuk signing APK, pelindung Zip-Slip, dan UUID acak tanpa mengumpulkan IMEI/Serial/MAC.",
                                "ZClone uses AndroidKeyStore AES-256-GCM, a user-local keystore for APK signing, Zip-Slip protection, and random install UUIDs with zero IMEI/Serial/MAC collection."
                            )
                        }
                    )
                    HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                    SettingsRowItem(
                        icon = Icons.Rounded.Lock,
                        tint = theme.accentGold,
                        title = tr(lang, "Izin Aplikasi", "Permissions"),
                        onClick = {
                            infoDialogTitle = tr(lang, "Izin Minimal ZClone", "ZClone Least-Privilege Permissions")
                            infoDialogBody = tr(
                                lang,
                                "• INTERNET & ACCESS_NETWORK_STATE: Opsional untuk Google Sign-In & Cloud Firestore.\n• REQUEST_INSTALL_PACKAGES: Memicu installer sistem saat Anda memasang APK klon.\n• Pemilihan APK & Backup menggunakan Storage Access Framework (SAF) tanpa izin penyimpanan luas.",
                                "• INTERNET & ACCESS_NETWORK_STATE: Optional for Google Sign-In & Cloud Firestore.\n• REQUEST_INSTALL_PACKAGES: Triggers system installer for cloned APKs.\n• APK/Backup picking uses Storage Access Framework without broad storage permissions."
                            )
                        }
                    )
                    HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                    SettingsRowItem(
                        icon = Icons.Rounded.Info,
                        tint = theme.primary,
                        title = tr(lang, "Tentang ZCloner PRO", "About ZCloner PRO"),
                        onClick = {
                            infoDialogTitle = "ZCloner PRO · FMS Engine Core V1"
                            infoDialogBody = tr(
                                lang,
                                "ZCloner PRO dibangun di atas modul com.fms.* (FMSCore v1.1.0, FMSSecurityEngine, FMSDatabaseEngine v2, FMSThemeEngine, FMSBackgroundEngine, FMSCloudEngine, dan FMS Engine Core V1).\n\nCopyright FMS Clone Engine V1",
                                "ZCloner PRO is built on modular com.fms.* libraries (FMSCore v1.1.0, FMSSecurityEngine, FMSDatabaseEngine v2, FMSThemeEngine, FMSBackgroundEngine, FMSCloudEngine, and FMS Engine Core V1).\n\nCopyright FMS Clone Engine V1"
                            )
                        }
                    )
                    HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                    SettingsRowItem(
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        tint = theme.secondary,
                        title = tr(lang, "Bantuan & Dukungan", "Help & Support"),
                        onClick = {
                            infoDialogTitle = tr(lang, "Bantuan & Batasan Non-Root", "Help & Non-Root Limitations")
                            infoDialogBody = tr(
                                lang,
                                "Pada Android standar tanpa root: beberapa APK Split (.apks) memerlukan basis APK tunggal yang kompatibel, dan aplikasi dengan certificate pinning atau Play Integrity server-side dapat menolak berjalan setelah re-signing lokal.",
                                "On standard unrooted Android: Split APKs may have limited support, and apps enforcing certificate pinning or server-side Play Integrity checks may refuse to run when re-signed locally."
                            )
                        }
                    )
                    HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                    val cacheMb = String.format("%.2f MB", state.workCacheBytes / (1024.0 * 1024.0))
                    SettingsRowItem(
                        icon = Icons.Rounded.CleaningServices,
                        tint = Color(0xFF38BDF8),
                        title = tr(lang, "Bersihkan Cache Kerja ($cacheMb)", "Clear Work Cache ($cacheMb)"),
                        onClick = onClearWorkCache
                    )
                }
            }
        }
    }

    if (infoDialogTitle != null && infoDialogBody != null) {
        AlertDialog(
            onDismissRequest = { infoDialogTitle = null; infoDialogBody = null },
            title = { Text(infoDialogTitle!!) },
            text = { Text(infoDialogBody!!) },
            confirmButton = {
                Button(onClick = { infoDialogTitle = null; infoDialogBody = null }) {
                    Text(tr(lang, "Mengerti", "OK"))
                }
            }
        )
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    tint: Color,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
fun TierOverviewDialog(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onDismiss: () -> Unit
) {
    val lang = state.languageCode
    FmsGlassCard(theme = theme, cornerRadius = 24.dp, borderColor = theme.accentGold.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ZCloner PRO", style = MaterialTheme.typography.headlineMedium, color = theme.onBackground)
            FmsStatusBadge(label = state.userTier.displayName, color = theme.accentGold, icon = Icons.Rounded.WorkspacePremium)
            Spacer(Modifier.height(4.dp))
            listOf(
                tr(lang, "Seluruh Fitur Non-Root Lokal Tersedia", "All Local Non-Root Features Unlocked"),
                tr(lang, "Tanpa Iklan & Pelacak Pihak Ketiga", "No Ads or 3rd-Party Trackers"),
                tr(lang, "Sinkronisasi Cloud Firestore & Device Manager", "Cloud Firestore Sync & Device Manager"),
                tr(lang, "Kuota Perangkat Aktif Saat Ini: ${state.deviceLimit}", "Current Active Device Limit: ${state.deviceLimit}")
            ).forEach { feat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = theme.accentGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(feat, style = MaterialTheme.typography.bodyMedium, color = theme.onBackground)
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentGold),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(tr(lang, "Tutup", "Close"), color = Color(0xFF1F1600), fontWeight = FontWeight.Bold)
            }
        }
    }
}
