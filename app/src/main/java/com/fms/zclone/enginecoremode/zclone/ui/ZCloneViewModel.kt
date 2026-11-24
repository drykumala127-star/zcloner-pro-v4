package com.fms.zclone.enginecoremode.zclone.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fms.zclone.enginecoremode.zclone.data.CloudCloneConfig
import com.fms.zclone.enginecoremode.zclone.data.CloudDeviceRecord
import com.fms.zclone.enginecoremode.zclone.data.CloudSecurityReport
import com.fms.zclone.enginecoremode.zclone.data.CloudThemePreset
import com.fms.zclone.enginecoremode.zclone.data.CloudUserProfile
import com.fms.zclone.enginecoremode.zclone.data.ZCloneCloudRepository
import android.content.pm.PackageManager
import com.fms.clone.ApkDependencyGraph
import com.fms.clone.ApkInspectionResult
import com.fms.clone.BinaryVersionDiffReport
import com.fms.clone.CloneBuildFailureInfo
import com.fms.clone.CloneBuildOutput
import com.fms.clone.CloneBuildRequest
import com.fms.clone.FMSAPKInspector
import com.fms.clone.FMSCloneValidator
import com.fms.clone.FMSRepackager
import com.fms.clone.FMSSecurityScanner
import com.fms.clone.FeatureParityRecord
import com.fms.clone.FmsBinaryVersionDiff
import com.fms.clone.FmsDependencyGraphAnalyzer
import com.fms.clone.FmsParityCatalog
import com.fms.clone.InstalledAppInfo
import com.fms.clone.SecurityScanResult
import com.fms.clone.ValidationCheckStatus
import com.fms.cloud.FMSAuthEngine
import com.fms.cloud.FMSConnectivity
import com.fms.cloud.FmsAuthSessionSnapshot
import com.fms.cloud.FmsAuthSessionStatus
import com.fms.cloud.FmsSyncState
import com.fms.core.FMSCore
import com.fms.core.FMSRuntime
import com.fms.core.FmsLogEntry
import com.fms.core.FmsLogger
import com.fms.core.FmsModuleStatus
import com.fms.data.FMSBackupEngine
import com.fms.data.FMSDatabaseEngine
import com.fms.data.FMSPreferenceEngine
import com.fms.data.FMSStorageEngine
import com.fms.data.LocalBuildHistoryRecord
import com.fms.data.LocalCloneRecord
import com.fms.data.LocalSecurityReportRecord
import com.fms.device.FMSDeviceManager
import com.fms.device.LicenseTier
import com.fms.device.LocalDeviceIdentity
import com.fms.task.FMSBackgroundEngine
import com.fms.task.FmsJobSnapshot
import com.fms.ui.FMSThemeEngine
import com.fms.ui.FMSThemeEngine.toHex
import com.fms.ui.FmsFontChoice
import com.fms.ui.FmsIconStyle
import com.fms.ui.FmsThemePreset
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class DeviceCloneVerificationState(
    val targetPackage: String = "",
    val stage8Installed: ValidationCheckStatus = ValidationCheckStatus.PENDING_DEVICE_TEST,
    val stage8Detail: String = "Belum diuji — pasang APK lalu ketuk 'Cek Terpasang'",
    val stage9FirstLaunch: ValidationCheckStatus = ValidationCheckStatus.PENDING_DEVICE_TEST,
    val stage9Detail: String = "Belum diuji — ketuk 'Uji Luncurkan' setelah terpasang",
    val stage10MainFunction: ValidationCheckStatus = ValidationCheckStatus.PENDING_DEVICE_TEST,
    val stage10Detail: String = "Belum diuji — uji alur utama aplikasi lalu konfirmasi hasil"
)

data class ZCloneUiState(
    val languageCode: String = "id",
    val isOnline: Boolean = true,
    val syncState: FmsSyncState = FmsSyncState.LOCAL_ONLY,
    val authSession: FmsAuthSessionSnapshot = FmsAuthSessionSnapshot(),
    val isLocalDeviceRevoked: Boolean = false,
    val userId: String? = null,
    val userDisplayName: String? = null,
    val userEmail: String? = null,
    val userTier: LicenseTier = LicenseTier.FREE,
    val deviceLimit: Int = 3,
    val localDevice: LocalDeviceIdentity? = null,
    val cloudDevices: List<Pair<String, CloudDeviceRecord>> = emptyList(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val systemApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val isLoadingSystemApps: Boolean = false,
    val hasLoadedSystemApps: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val activeInspection: ApkInspectionResult? = null,
    val activeDependencyGraph: ApkDependencyGraph? = null,
    val activeBinaryDiff: BinaryVersionDiffReport? = null,
    val isAnalyzingGraphOrDiff: Boolean = false,
    val activeSelectedApp: InstalledAppInfo? = null,
    val pendingPrefillConfig: LocalCloneRecord? = null,
    val localClones: List<LocalCloneRecord> = emptyList(),
    val buildHistory: List<LocalBuildHistoryRecord> = emptyList(),
    val localSecurityReports: List<LocalSecurityReportRecord> = emptyList(),
    val activeSecurityScan: SecurityScanResult? = null,
    val lastBuildOutput: CloneBuildOutput? = null,
    val lastBuildFailure: CloneBuildFailureInfo? = null,
    val deviceVerification: DeviceCloneVerificationState? = null,
    val isBuildingClone: Boolean = false,
    val buildProgress: Float = 0f,
    val buildProgressDetail: String = "",
    val backgroundJobs: List<FmsJobSnapshot> = emptyList(),
    val moduleStatuses: List<FmsModuleStatus> = emptyList(),
    val fmsLogs: List<FmsLogEntry> = emptyList(),
    val parityRecords: List<FeatureParityRecord> = FmsParityCatalog.allRecords,
    val workCacheBytes: Long = 0L,
    val isBusyApkIo: Boolean = false,
    val statusBanner: String? = null,
    val errorMessage: String? = null
)

class ZCloneViewModel(
    private val appContext: Context,
    private val database: FMSDatabaseEngine,
    private val prefs: FMSPreferenceEngine,
    private val storage: FMSStorageEngine,
    private val inspector: FMSAPKInspector,
    private val deviceManager: FMSDeviceManager,
    private val backgroundEngine: FMSBackgroundEngine,
    private val cloudRepository: ZCloneCloudRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZCloneUiState())
    val uiState: StateFlow<ZCloneUiState> = _uiState.asStateFlow()

    private var deviceSyncJob: Job? = null
    private var cloudDataSyncJobs: List<Job> = emptyList()
    private var activeSingleBuildJob: Job? = null
    private val refreshDbMutex = Mutex()

    init {
        FMSCore.registerStandardCatalog()
        val statuses = FMSRuntime.initializeAll()
        val savedLang = prefs.getString("language_code", "id")
        val savedTheme = prefs.getString("active_theme_id", "dark_neon")
        val savedAutoTheme = prefs.getBoolean("auto_theme_enabled", false)
        val savedFont = runCatching { FmsFontChoice.valueOf(prefs.getString("theme_font_choice", FmsFontChoice.PLUS_JAKARTA_SANS.name)) }
            .getOrDefault(FmsFontChoice.PLUS_JAKARTA_SANS)
        val savedIconStyle = runCatching { FmsIconStyle.valueOf(prefs.getString("theme_icon_style", FmsIconStyle.SQUIRCLE.name)) }
            .getOrDefault(FmsIconStyle.SQUIRCLE)

        if (savedTheme == "custom") {
            val defaultCustom = FMSThemeEngine.presets.first { it.id == "custom" }
            val primaryArgb = prefs.getInt("custom_primary_argb", defaultCustom.primary.toArgb())
            val secondaryArgb = prefs.getInt("custom_secondary_argb", defaultCustom.secondary.toArgb())
            FMSThemeEngine.applyCustomTheme(Color(primaryArgb), Color(secondaryArgb), savedFont, savedIconStyle)
        } else {
            FMSThemeEngine.applyPreset(savedTheme)
            FMSThemeEngine.setFontChoice(savedFont)
            FMSThemeEngine.setIconStyle(savedIconStyle)
        }
        if (savedAutoTheme) {
            val nightMask = appContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            FMSThemeEngine.toggleAutoTheme(true, nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        }
        val localIdentity = deviceManager.getOrCreateInstallIdentity("1.0.0")

        _uiState.update {
            it.copy(
                languageCode = savedLang,
                localDevice = localIdentity,
                moduleStatuses = statuses
            )
        }

        viewModelScope.launch {
            var previousOnline: Boolean? = null
            FMSConnectivity.observeOnline(appContext).collect { online ->
                FMSAuthEngine.updateConnectivityState(online)
                val stateNow = _uiState.value
                _uiState.update {
                    it.copy(
                        isOnline = online,
                        syncState = when {
                            it.userId == null -> FmsSyncState.LOCAL_ONLY
                            it.isLocalDeviceRevoked -> FmsSyncState.ERROR
                            !online -> FmsSyncState.OFFLINE
                            it.syncState == FmsSyncState.OFFLINE -> FmsSyncState.SYNCING
                            else -> it.syncState
                        }
                    )
                }
                if (previousOnline == false && online && stateNow.userId != null) {
                    val res = FMSAuthEngine.refreshActiveSession(appContext, forceRefresh = true, isOnlineOverride = true)
                    val latest = _uiState.value
                    if (res.isSuccess && latest.userId == stateNow.userId && !latest.isLocalDeviceRevoked) {
                        attachAuthenticatedSession(
                            uid = latest.userId,
                            displayName = latest.userDisplayName,
                            email = latest.userEmail
                        )
                    } else if (FMSAuthEngine.sessionState.value.status == FmsAuthSessionStatus.TOKEN_EXPIRED) {
                        detachAuthenticatedSession()
                        _uiState.update {
                            it.copy(errorMessage = "Sesi akun Google kedaluwarsa saat kembali online. Silakan masuk ulang.")
                        }
                    } else if (res.isFailure) {
                        _uiState.update {
                            it.copy(
                                syncState = FmsSyncState.ERROR,
                                errorMessage = "Verifikasi token gagal saat kembali online: ${res.exceptionOrNull()?.message ?: "coba refresh di Pengaturan Akun."}"
                            )
                        }
                    }
                } else if (!online && stateNow.userId != null) {
                    stopCloudSyncListeners()
                }
                previousOnline = online
            }
        }
        viewModelScope.launch {
            FMSAuthEngine.sessionState.collect { snap ->
                _uiState.update { it.copy(authSession = snap) }
            }
        }
        viewModelScope.launch {
            backgroundEngine.jobs.collect { jobs ->
                _uiState.update { it.copy(backgroundJobs = jobs) }
            }
        }
        viewModelScope.launch {
            FmsLogger.logsFlow.collect { logs ->
                _uiState.update { it.copy(fmsLogs = logs) }
            }
        }

        refreshLocalDatabase()
        viewModelScope.launch {
            delay(250L)
            loadDeviceApps(includeSystem = false)
        }
    }

    fun refreshLocalDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshDbMutex.withLock {
                val clones = database.listCloneConfigs()
                val history = database.listBuildHistory()
                val scans = database.listSecurityReports()
                val cacheSize = storage.workCacheSizeBytes()
                _uiState.update {
                    it.copy(
                        localClones = clones,
                        buildHistory = history,
                        localSecurityReports = scans,
                        workCacheBytes = cacheSize
                    )
                }
            }
        }
    }

    fun loadDeviceApps(includeSystem: Boolean = _uiState.value.hasLoadedSystemApps) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingApps = true,
                    isLoadingSystemApps = includeSystem
                )
            }
            val (userApps, sysApps) = withContext(Dispatchers.IO) {
                val user = inspector.loadVisibleApps(includeSystem = false, loadIcons = false)
                val sys = if (includeSystem) {
                    inspector.loadVisibleApps(includeSystem = true, loadIcons = false)
                } else {
                    _uiState.value.systemApps
                }
                user to sys
            }
            _uiState.update {
                it.copy(
                    installedApps = userApps,
                    systemApps = sysApps,
                    isLoadingApps = false,
                    isLoadingSystemApps = false,
                    hasLoadedSystemApps = it.hasLoadedSystemApps || includeSystem
                )
            }
        }
    }

    fun loadSystemAppsIfNeeded() {
        val current = _uiState.value
        if (current.hasLoadedSystemApps || current.isLoadingSystemApps) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSystemApps = true) }
            val sysApps = withContext(Dispatchers.IO) {
                inspector.loadVisibleApps(includeSystem = true, loadIcons = false)
            }
            _uiState.update {
                it.copy(
                    systemApps = sysApps,
                    isLoadingSystemApps = false,
                    hasLoadedSystemApps = true
                )
            }
        }
    }

    suspend fun loadAppIcon(packageName: String): android.graphics.Bitmap? {
        inspector.getCachedIcon(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            inspector.loadAppIcon(packageName)
        }
    }

    fun togglePackageSelection(pkg: String) {
        _uiState.update { state ->
            val next = state.selectedPackages.toMutableSet()
            if (!next.add(pkg)) next.remove(pkg)
            state.copy(selectedPackages = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPackages = emptySet()) }
    }

    fun prepareSingleAppForConfig(app: InstalledAppInfo, onReady: () -> Unit) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeSelectedApp = app,
                    activeInspection = null,
                    activeDependencyGraph = null,
                    pendingPrefillConfig = null,
                    lastBuildFailure = null,
                    deviceVerification = null,
                    errorMessage = null
                )
            }
            val resolvedIcon = app.iconBitmap ?: withContext(Dispatchers.IO) {
                inspector.loadAppIcon(app.packageName)
            }
            if (resolvedIcon != null && app.iconBitmap == null) {
                _uiState.update { state ->
                    if (state.activeSelectedApp?.packageName == app.packageName) {
                        state.copy(activeSelectedApp = state.activeSelectedApp.copy(iconBitmap = resolvedIcon))
                    } else state
                }
            }
            val sourcePath = app.sourceApkPath
            if (sourcePath != null) {
                val (res, graphRes) = withContext(Dispatchers.IO) {
                    val f = File(sourcePath)
                    inspector.inspectApkFile(f, hasSplitApksHint = app.hasSplitApks) to
                        FmsDependencyGraphAnalyzer.analyze(f)
                }
                res.onSuccess { inspection ->
                    _uiState.update {
                        it.copy(
                            activeInspection = inspection,
                            activeDependencyGraph = graphRes.getOrNull()
                        )
                    }
                }.onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Gagal memeriksa APK.") }
                }
            }
            onReady()
        }
    }

    fun prepareSavedRecipeForConfig(record: LocalCloneRecord, onReady: () -> Unit) {
        viewModelScope.launch {
            val allKnown = _uiState.value.installedApps + _uiState.value.systemApps
            val matched = allKnown.firstOrNull { it.packageName == record.sourcePackage }
            val fallbackApp = matched ?: InstalledAppInfo(
                packageName = record.sourcePackage,
                appName = record.sourceAppName,
                versionName = "1.0",
                versionCode = 1L,
                isSystemApp = false,
                sourceApkPath = null,
                hasSplitApks = false,
                apkSizeBytes = 0L,
                minSdk = 24,
                targetSdk = 36,
                requestedPermissions = record.strippedPermissionsCsv.split(",").filter { it.isNotBlank() }
            )
            _uiState.update {
                it.copy(
                    activeSelectedApp = fallbackApp,
                    activeInspection = null,
                    activeDependencyGraph = null,
                    pendingPrefillConfig = record,
                    lastBuildFailure = null,
                    deviceVerification = null,
                    errorMessage = if (matched?.sourceApkPath == null) {
                        "Aplikasi sumber ${record.sourcePackage} tidak terinstal; gunakan Impor APK jika ingin membangun ulang."
                    } else null
                )
            }
            val sourcePath = matched?.sourceApkPath
            if (sourcePath != null) {
                val (res, graphRes) = withContext(Dispatchers.IO) {
                    val f = File(sourcePath)
                    inspector.inspectApkFile(f, hasSplitApksHint = matched.hasSplitApks) to
                        FmsDependencyGraphAnalyzer.analyze(f)
                }
                res.onSuccess { inspection ->
                    _uiState.update {
                        it.copy(
                            activeInspection = inspection,
                            activeDependencyGraph = graphRes.getOrNull()
                        )
                    }
                }
            }
            onReady()
        }
    }

    fun importExternalApk(uri: Uri, onReady: () -> Unit) {
        if (_uiState.value.isBusyApkIo) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusyApkIo = true, errorMessage = null) }
            val copied = withContext(Dispatchers.IO) {
                storage.copyUriToWorkFile(uri, "imported_${System.currentTimeMillis()}.apk")
            }
            copied.onFailure { err ->
                _uiState.update { it.copy(isBusyApkIo = false, errorMessage = err.message ?: "Gagal mengimpor APK.") }
                return@launch
            }
            val file = copied.getOrThrow()
            val (inspectionRes, graphRes) = withContext(Dispatchers.IO) {
                inspector.inspectApkFile(file, hasSplitApksHint = false) to
                    FmsDependencyGraphAnalyzer.analyze(file)
            }
            inspectionRes.onSuccess { inspection ->
                val extractedIcon = withContext(Dispatchers.IO) {
                    inspector.extractIconBitmapFromApk(file, inspection.iconEntryPaths)
                }
                val appInfo = InstalledAppInfo(
                    packageName = inspection.packageName,
                    appName = inspection.appName,
                    versionName = inspection.versionName,
                    versionCode = inspection.versionCode,
                    isSystemApp = false,
                    sourceApkPath = file.absolutePath,
                    hasSplitApks = inspection.hasSplitApks,
                    apkSizeBytes = inspection.apkSizeBytes,
                    minSdk = inspection.manifestSummary.minSdkVersion ?: 24,
                    targetSdk = inspection.manifestSummary.targetSdkVersion ?: 36,
                    requestedPermissions = inspection.manifestSummary.permissions,
                    iconBitmap = extractedIcon
                )
                val cacheSize = withContext(Dispatchers.IO) { storage.workCacheSizeBytes() }
                _uiState.update {
                    it.copy(
                        isBusyApkIo = false,
                        activeSelectedApp = appInfo,
                        activeInspection = inspection,
                        activeDependencyGraph = graphRes.getOrNull(),
                        pendingPrefillConfig = null,
                        lastBuildFailure = null,
                        deviceVerification = null,
                        workCacheBytes = cacheSize,
                        statusBanner = "APK eksternal dimuat: ${inspection.appName}"
                    )
                }
                onReady()
            }.onFailure { err ->
                withContext(Dispatchers.IO) { file.delete() }
                _uiState.update { it.copy(isBusyApkIo = false, errorMessage = "File APK tidak valid: ${err.message}") }
            }
        }
    }

    fun attachExternalSourceApk(uri: Uri) {
        if (_uiState.value.isBusyApkIo) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusyApkIo = true, errorMessage = null) }
            val copied = withContext(Dispatchers.IO) {
                storage.copyUriToWorkFile(uri, "recipe_src_${System.currentTimeMillis()}.apk")
            }
            copied.onFailure { err ->
                _uiState.update { it.copy(isBusyApkIo = false, errorMessage = err.message ?: "Gagal memuat file APK sumber.") }
                return@launch
            }
            val file = copied.getOrThrow()
            val (inspectionRes, graphRes) = withContext(Dispatchers.IO) {
                inspector.inspectApkFile(file, hasSplitApksHint = false) to
                    FmsDependencyGraphAnalyzer.analyze(file)
            }
            inspectionRes.onSuccess { inspection ->
                val extractedIcon = withContext(Dispatchers.IO) {
                    inspector.extractIconBitmapFromApk(file, inspection.iconEntryPaths)
                }
                val appInfo = InstalledAppInfo(
                    packageName = inspection.packageName,
                    appName = inspection.appName,
                    versionName = inspection.versionName,
                    versionCode = inspection.versionCode,
                    isSystemApp = false,
                    sourceApkPath = file.absolutePath,
                    hasSplitApks = inspection.hasSplitApks,
                    apkSizeBytes = inspection.apkSizeBytes,
                    minSdk = inspection.manifestSummary.minSdkVersion ?: 24,
                    targetSdk = inspection.manifestSummary.targetSdkVersion ?: 36,
                    requestedPermissions = inspection.manifestSummary.permissions,
                    iconBitmap = extractedIcon
                )
                val cacheSize = withContext(Dispatchers.IO) { storage.workCacheSizeBytes() }
                val currentPending = _uiState.value.pendingPrefillConfig
                val recipeMatches = currentPending == null || currentPending.sourcePackage == inspection.packageName
                _uiState.update {
                    it.copy(
                        isBusyApkIo = false,
                        activeSelectedApp = appInfo,
                        activeInspection = inspection,
                        activeDependencyGraph = graphRes.getOrNull(),
                        pendingPrefillConfig = if (recipeMatches) currentPending else null,
                        lastBuildFailure = null,
                        deviceVerification = null,
                        workCacheBytes = cacheSize,
                        errorMessage = null,
                        statusBanner = if (recipeMatches) {
                            "Sumber APK dilampirkan: ${inspection.appName}"
                        } else {
                            "APK ${inspection.packageName} berbeda dari resep ${currentPending.sourcePackage}; konfigurasi direset."
                        }
                    )
                }
            }.onFailure { err ->
                withContext(Dispatchers.IO) { file.delete() }
                _uiState.update { it.copy(isBusyApkIo = false, errorMessage = "File APK sumber tidak valid: ${err.message}") }
            }
        }
    }

    fun clearTempCache() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { storage.clearWorkCache() }
            val currentSrc = _uiState.value.activeSelectedApp?.sourceApkPath
            val srcMissing = currentSrc != null && !File(currentSrc).exists()
            _uiState.update { state ->
                state.copy(
                    workCacheBytes = 0L,
                    activeSelectedApp = if (srcMissing) state.activeSelectedApp?.copy(sourceApkPath = null) else state.activeSelectedApp,
                    statusBanner = if (freed > 0L) {
                        val mb = String.format("%.2f MB", freed / (1024.0 * 1024.0))
                        "Cache kerja sementara dibersihkan ($mb dibebaskan)."
                    } else {
                        "Cache kerja sementara sudah bersih."
                    }
                )
            }
        }
    }

    fun buildCloneFromConfig(request: CloneBuildRequest, existingId: String? = null) {
        if (_uiState.value.isBuildingClone) return
        if (!request.ownerConfirmedModificationRights) {
            _uiState.update {
                it.copy(errorMessage = "Konfirmasi hak modifikasi APK wajib disetujui sebelum membangun klon.")
            }
            return
        }
        activeSingleBuildJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBuildingClone = true,
                    buildProgress = 0.05f,
                    buildProgressDetail = "Menyiapkan FMS Engine Core V1 & Dhedry Runtime...",
                    lastBuildOutput = null,
                    lastBuildFailure = null,
                    deviceVerification = null,
                    errorMessage = null
                )
            }

            val configId = existingId ?: "cfg_${UUID.randomUUID().toString().take(12)}"
            val safeName = request.targetPackage.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val outputFile = File(storage.clonedApkOutputDir, "${safeName}_zclone.apk")

            try {
                val result = withContext(Dispatchers.IO) {
                    val ioJob = currentCoroutineContext()[Job]
                    FMSRepackager.buildClonedApk(
                        request = request,
                        privateFilesDir = appContext.filesDir,
                        outputFile = outputFile,
                        context = appContext,
                        shouldCancel = { ioJob?.isActive == false }
                    ) { p, msg ->
                        _uiState.update { it.copy(buildProgress = p, buildProgressDetail = msg) }
                    }
                }

                result.onSuccess { output ->
                    val compatStr = _uiState.value.activeInspection?.compatibility?.status?.name ?: "COMPATIBLE"
                    val record = LocalCloneRecord(
                        id = configId,
                        sourcePackage = request.sourcePackage,
                        targetPackage = output.targetPackage,
                        sourceAppName = request.sourceAppName,
                        cloneName = request.cloneName,
                        iconPresetId = request.iconPresetId,
                        iconHueShift = request.iconHueShift,
                        changePackageName = request.changePackageName,
                        hideFromLauncher = request.hideFromLauncher,
                        isolateData = request.isolateData,
                        strippedPermissionsCsv = request.strippedPermissions.joinToString(","),
                        compatibilityStatus = compatStr,
                        buildStatus = "BUILT",
                        outputApkPath = output.outputApkPath,
                        updatedAtMs = System.currentTimeMillis()
                    )
                    val snapshotJson = FMSDatabaseEngine.encodeCloneSnapshotJson(record)
                    withContext(Dispatchers.IO) {
                        database.upsertCloneConfig(record)
                        database.insertBuildHistory(
                            LocalBuildHistoryRecord(
                                id = "bld_${UUID.randomUUID().toString().take(10)}",
                                configId = configId,
                                sourcePackage = request.sourcePackage,
                                targetPackage = output.targetPackage,
                                status = if (output.signatureVerified) "VERIFIED" else "SIGNED",
                                outputPath = output.outputApkPath,
                                sha256 = output.outputSha256,
                                createdAtMs = System.currentTimeMillis(),
                                configSnapshotJson = snapshotJson
                            )
                        )
                    }
                    refreshLocalDatabase()
                    val uid = activeCloudSyncUid()
                    var cloudSyncWarn: String? = null
                    if (uid != null) {
                        cloudRepository.syncCloneConfig(uid, record).onFailure { err ->
                            cloudSyncWarn = err.message
                            _uiState.update { it.copy(syncState = FmsSyncState.ERROR) }
                        }
                    } else if (_uiState.value.userId != null && !_uiState.value.isOnline) {
                        _uiState.update { it.copy(syncState = FmsSyncState.OFFLINE) }
                    }
                    _uiState.update {
                        it.copy(
                            isBuildingClone = false,
                            buildProgress = 1f,
                            buildProgressDetail = output.validationReport?.summaryMessage ?: "APK klon siap diuji pasang",
                            lastBuildOutput = output,
                            lastBuildFailure = null,
                            deviceVerification = DeviceCloneVerificationState(targetPackage = output.targetPackage),
                            statusBanner = if (cloudSyncWarn == null) {
                                "Validasi 7-tahap lulus: ${output.cloneName}"
                            } else {
                                "Build lokal lulus validasi: ${output.cloneName} (cloud: $cloudSyncWarn)"
                            }
                        )
                    }
                }.onFailure { err ->
                    val failureInfo = FMSCloneValidator.toFailureInfo(err)
                    _uiState.update {
                        it.copy(
                            isBuildingClone = false,
                            buildProgress = 0f,
                            lastBuildFailure = failureInfo,
                            errorMessage = failureInfo.userMessage
                        )
                    }
                }
            } catch (ce: CancellationException) {
                if (!viewModelScope.isActive) throw ce
                _uiState.update {
                    it.copy(
                        isBuildingClone = false,
                        buildProgress = 0f,
                        buildProgressDetail = "Build dibatalkan oleh pengguna.",
                        statusBanner = "Proses kloning dibatalkan; APK sumber dan resep tersimpan tetap aman."
                    )
                }
            } finally {
                activeSingleBuildJob = null
            }
        }
    }

    fun cancelActiveBuild() {
        val job = activeSingleBuildJob
        if (job != null && job.isActive) {
            job.cancel(CancellationException("Build dibatalkan oleh pengguna"))
            _uiState.update {
                it.copy(
                    isBuildingClone = false,
                    buildProgress = 0f,
                    buildProgressDetail = "Build dibatalkan.",
                    statusBanner = "Membatalkan proses kloning..."
                )
            }
        }
    }

    fun cancelBatchJob(jobId: String) {
        if (backgroundEngine.cancelJob(jobId)) {
            _uiState.update { it.copy(statusBanner = "Tugas batch $jobId dibatalkan.") }
        }
    }

    fun cancelAllBatchJobs() {
        val count = backgroundEngine.cancelAll()
        if (count > 0) {
            _uiState.update { it.copy(statusBanner = "$count antrean batch dibatalkan.") }
        }
    }

    fun compareHistoryOrCloneApks(pathA: String, pathB: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingGraphOrDiff = true, errorMessage = null) }
            val res = withContext(Dispatchers.IO) {
                FmsBinaryVersionDiff.compareApks(File(pathA), File(pathB))
            }
            res.onSuccess { report ->
                _uiState.update {
                    it.copy(
                        isAnalyzingGraphOrDiff = false,
                        activeBinaryDiff = report
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAnalyzingGraphOrDiff = false,
                        errorMessage = "Gagal membandingkan biner APK: ${err.message ?: err.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun clearBinaryDiff() {
        _uiState.update { it.copy(activeBinaryDiff = null) }
    }

    fun exportBinaryDiffToUri(diff: BinaryVersionDiffReport, uri: Uri) {
        viewModelScope.launch {
            val json = FmsBinaryVersionDiff.toJsonString(diff)
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val outStream = runCatching { appContext.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                        ?: appContext.contentResolver.openOutputStream(uri)
                        ?: error("Tidak dapat menulis file diff JSON.")
                    outStream.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            }
            res.onSuccess {
                _uiState.update { it.copy(statusBanner = "Laporan Binary Version Diff diekspor ke JSON.") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Gagal mengekspor diff JSON.") }
            }
        }
    }

    fun restoreTimeMachineSnapshot(history: LocalBuildHistoryRecord, onReady: () -> Unit) {
        val decoded = FMSDatabaseEngine.decodeCloneSnapshotJson(history.configSnapshotJson, history)
            ?: _uiState.value.localClones.firstOrNull { it.id == history.configId }
            ?: LocalCloneRecord(
                id = history.configId.ifBlank { "cfg_${UUID.randomUUID().toString().take(8)}" },
                sourcePackage = history.sourcePackage,
                targetPackage = history.targetPackage,
                sourceAppName = history.sourcePackage,
                cloneName = "${history.targetPackage.substringAfterLast('.')} Restored",
                iconPresetId = "neon_cyan",
                iconHueShift = 180,
                changePackageName = history.targetPackage != history.sourcePackage,
                hideFromLauncher = false,
                isolateData = true,
                strippedPermissionsCsv = "",
                compatibilityStatus = "COMPATIBLE",
                buildStatus = "SNAPSHOT_RESTORED",
                outputApkPath = history.outputPath.takeIf { it.isNotBlank() },
                updatedAtMs = history.createdAtMs
            )
        prepareSavedRecipeForConfig(decoded) {
            _uiState.update {
                it.copy(statusBanner = "Snapshot Time Machine '${decoded.cloneName}' dipulihkan ke App Config.")
            }
            onReady()
        }
    }

    fun runBatchCloneSelected(ownerConfirmedModificationRights: Boolean = true) {
        if (!ownerConfirmedModificationRights) {
            _uiState.update {
                it.copy(errorMessage = "Konfirmasi hak modifikasi APK wajib disetujui sebelum menjalankan Batch Clone.")
            }
            return
        }
        val selected = _uiState.value.selectedPackages.toList()
        if (selected.isEmpty()) return
        val allVisible = _uiState.value.installedApps + _uiState.value.systemApps
        val matchingApps = allVisible.filter { it.packageName in selected && it.sourceApkPath != null }
        val skippedSplits = matchingApps.filter { it.hasSplitApks }
        val targets = matchingApps.filter { !it.hasSplitApks }
        if (targets.isEmpty()) {
            _uiState.update {
                it.copy(
                    errorMessage = if (skippedSplits.isNotEmpty()) {
                        "Semua aplikasi terpilih adalah Split APK dan tidak dapat dikloning sebagai APK tunggal."
                    } else {
                        "Tidak ada APK sumber yang dapat diakses dari pilihan ini."
                    }
                )
            }
            return
        }

        targets.forEachIndexed { idx, app ->
            val targetPkg = buildValidBatchTargetPackage(app.packageName, idx + 1)
            val cloneTitle = "${app.appName} Clone"
            backgroundEngine.enqueue(
                title = "Batch Clone: $cloneTitle",
                block = { progress ->
                    val srcPath = requireNotNull(app.sourceApkPath) { "Source APK path tidak tersedia." }
                    val inspection = inspector.inspectApkFile(File(srcPath), hasSplitApksHint = app.hasSplitApks).getOrThrow()
                    require(inspection.compatibility.blockers.isEmpty()) {
                        inspection.compatibility.blockers.first()
                    }
                    val ioJob = currentCoroutineContext()[Job]
                    val outFile = File(storage.clonedApkOutputDir, "${targetPkg}_batch.apk")
                    val res = FMSRepackager.buildClonedApk(
                        request = CloneBuildRequest(
                            sourceApkPath = srcPath,
                            sourcePackage = app.packageName,
                            targetPackage = targetPkg,
                            sourceAppName = app.appName,
                            cloneName = cloneTitle,
                            iconPresetId = "neon_cyan",
                            iconHueShift = 180,
                            changePackageName = true,
                            hideFromLauncher = false,
                            isolateData = true,
                            strippedPermissions = emptySet(),
                            ownerConfirmedModificationRights = true,
                            injectDhedryLoading = true
                        ),
                        privateFilesDir = appContext.filesDir,
                        outputFile = outFile,
                        context = appContext,
                        shouldCancel = { ioJob?.isActive == false },
                        onProgress = progress
                    ).getOrThrow()

                    val cfgId = "batch_${UUID.randomUUID().toString().take(10)}"
                    val rec = LocalCloneRecord(
                        id = cfgId,
                        sourcePackage = app.packageName,
                        targetPackage = res.targetPackage,
                        sourceAppName = app.appName,
                        cloneName = cloneTitle,
                        iconPresetId = "neon_cyan",
                        iconHueShift = 180,
                        changePackageName = true,
                        hideFromLauncher = false,
                        isolateData = true,
                        strippedPermissionsCsv = "",
                        compatibilityStatus = inspection.compatibility.status.name,
                        buildStatus = "BUILT",
                        outputApkPath = res.outputApkPath,
                        updatedAtMs = System.currentTimeMillis()
                    )
                    val snapshotJson = FMSDatabaseEngine.encodeCloneSnapshotJson(rec)
                    database.upsertCloneConfig(rec)
                    database.insertBuildHistory(
                        LocalBuildHistoryRecord(
                            id = "bld_${UUID.randomUUID().toString().take(10)}",
                            configId = cfgId,
                            sourcePackage = app.packageName,
                            targetPackage = res.targetPackage,
                            status = if (res.signatureVerified) "VERIFIED" else "SIGNED",
                            outputPath = res.outputApkPath,
                            sha256 = res.outputSha256,
                            createdAtMs = System.currentTimeMillis(),
                            configSnapshotJson = snapshotJson
                        )
                    )
                    activeCloudSyncUid()?.let { uid ->
                        cloudRepository.syncCloneConfig(uid, rec).onFailure { err ->
                            handleCloudWriteFailure("batch clone", err)
                        }
                    }
                    refreshLocalDatabase()
                },
                onComplete = { jobResult ->
                    jobResult.onSuccess {
                        _uiState.update { it.copy(statusBanner = "Batch Clone selesai: $cloneTitle") }
                    }.onFailure { err ->
                        _uiState.update { it.copy(errorMessage = "Batch Clone gagal ($cloneTitle): ${err.message}") }
                    }
                }
            )
        }
        clearSelection()
        val msg = if (skippedSplits.isNotEmpty()) {
            "${targets.size} tugas masuk antrean (${skippedSplits.size} Split APK dilewati)."
        } else {
            "${targets.size} tugas Batch Clone masuk antrean FMSJobQueue."
        }
        _uiState.update { it.copy(statusBanner = msg) }
    }

    fun deleteCloneConfig(configId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.deleteCloneConfig(configId) }
            refreshLocalDatabase()
            activeCloudSyncUid()?.let { uid ->
                cloudRepository.deleteCloneConfig(uid, configId).onFailure { err ->
                    _uiState.update {
                        it.copy(
                            syncState = FmsSyncState.ERROR,
                            errorMessage = "Dihapus lokal, namun gagal hapus di cloud: ${err.message}"
                        )
                    }
                }
            }
        }
    }

    fun scanInstalledAppSecurity(app: InstalledAppInfo) {
        val path = app.sourceApkPath ?: run {
            _uiState.update { it.copy(errorMessage = "Path APK tidak dapat diakses untuk ${app.appName}") }
            return
        }
        scanApkFileSecurity(File(path), app.hasSplitApks)
    }

    fun scanExternalApkSecurity(uri: Uri) {
        if (_uiState.value.isBusyApkIo) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusyApkIo = true, errorMessage = null) }
            val copied = withContext(Dispatchers.IO) {
                storage.copyUriToWorkFile(uri, "scan_${System.currentTimeMillis()}.apk")
            }
            copied.onFailure { err ->
                _uiState.update { it.copy(isBusyApkIo = false, errorMessage = err.message ?: "Gagal membaca file APK.") }
                return@launch
            }
            val file = copied.getOrThrow()
            scanApkFileSecurity(file, hasSplitApks = false, deleteAfterScan = true)
        }
    }

    private fun scanApkFileSecurity(apkFile: File, hasSplitApks: Boolean, deleteAfterScan: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusyApkIo = true) }
            val inspection = withContext(Dispatchers.IO) {
                try {
                    inspector.inspectApkFile(apkFile, hasSplitApks)
                } finally {
                    if (deleteAfterScan) apkFile.delete()
                }
            }
            inspection.onSuccess { insp ->
                val scan = FMSSecurityScanner.scan(insp)
                val report = LocalSecurityReportRecord(
                    id = "rep_${insp.packageName.replace('.', '_').take(40)}",
                    packageName = scan.packageName,
                    appName = scan.appName,
                    riskScore = scan.riskScore,
                    riskLevel = scan.riskLevel.name,
                    exportedComponentsCount = scan.exportedComponentsCount,
                    dangerousPermissionsCount = scan.dangerousPermissionsCount,
                    cleartextAllowed = scan.cleartextAllowed,
                    debuggableFlag = scan.debuggableFlag,
                    certSha256Prefix = scan.certSha256Prefix,
                    summary = scan.summary,
                    scannedAtMs = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) { database.upsertSecurityReport(report) }
                refreshLocalDatabase()
                activeCloudSyncUid()?.let { uid ->
                    cloudRepository.syncSecurityReport(uid, report).onFailure {
                        _uiState.update { s -> s.copy(syncState = FmsSyncState.ERROR) }
                    }
                }
                _uiState.update {
                    it.copy(
                        isBusyApkIo = false,
                        activeSecurityScan = scan,
                        statusBanner = "Pemindaian selesai: ${scan.appName} (${scan.riskLevel.name})"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isBusyApkIo = false, errorMessage = err.message ?: "Gagal memindai APK.") }
            }
        }
    }

    fun selectSavedSecurityReport(report: LocalSecurityReportRecord) {
        _uiState.update {
            it.copy(
                activeSecurityScan = SecurityScanResult(
                    packageName = report.packageName,
                    appName = report.appName,
                    riskScore = report.riskScore,
                    riskLevel = runCatching { com.fms.clone.RiskLevel.valueOf(report.riskLevel) }
                        .getOrDefault(com.fms.clone.RiskLevel.LOW),
                    exportedComponentsCount = report.exportedComponentsCount,
                    dangerousPermissionsCount = report.dangerousPermissionsCount,
                    cleartextAllowed = report.cleartextAllowed,
                    debuggableFlag = report.debuggableFlag,
                    certSha256Prefix = report.certSha256Prefix,
                    findings = emptyList(),
                    summary = report.summary,
                    limitationNotice = "Ringkasan laporan tersimpan (pindai ulang APK untuk melihat detail per temuan)."
                )
            )
        }
    }

    fun applyThemePreset(presetId: String) {
        prefs.putBoolean("auto_theme_enabled", false)
        FMSThemeEngine.toggleAutoTheme(false, false)
        FMSThemeEngine.applyPreset(presetId)
        val active = FMSThemeEngine.activeTheme.value
        prefs.putString("active_theme_id", active.id)
        prefs.putString("theme_font_choice", active.fontChoice.name)
        prefs.putString("theme_icon_style", active.iconStyle.name)
        val uid = activeCloudSyncUid()
        if (uid != null) {
            viewModelScope.launch {
                cloudRepository.updateUserPreferences(uid, active.id, _uiState.value.languageCode)
                    .onFailure { handleCloudWriteFailure("preferensi tema", it) }
            }
        }
        _uiState.update { it.copy(statusBanner = "Tema '${active.name}' diterapkan.") }
    }

    fun toggleAutoTheme(enabled: Boolean, systemDark: Boolean) {
        prefs.putBoolean("auto_theme_enabled", enabled)
        FMSThemeEngine.toggleAutoTheme(enabled, systemDark)
        val active = FMSThemeEngine.activeTheme.value
        prefs.putString("active_theme_id", active.id)
        val uid = activeCloudSyncUid()
        if (uid != null) {
            viewModelScope.launch {
                cloudRepository.updateUserPreferences(uid, active.id, _uiState.value.languageCode)
                    .onFailure { handleCloudWriteFailure("auto theme", it) }
            }
        }
        _uiState.update {
            it.copy(
                statusBanner = if (enabled) "Auto Theme aktif (${active.name})." else "Auto Theme dimatikan."
            )
        }
    }

    fun setFontChoice(fontChoice: FmsFontChoice) {
        FMSThemeEngine.setFontChoice(fontChoice)
        prefs.putString("theme_font_choice", fontChoice.name)
    }

    fun setIconStyle(iconStyle: FmsIconStyle) {
        FMSThemeEngine.setIconStyle(iconStyle)
        prefs.putString("theme_icon_style", iconStyle.name)
    }

    fun applyCustomThemeAndSync(
        name: String,
        primary: Color,
        secondary: Color,
        fontChoice: FmsFontChoice,
        iconStyle: FmsIconStyle
    ) {
        prefs.putBoolean("auto_theme_enabled", false)
        FMSThemeEngine.toggleAutoTheme(false, false)
        FMSThemeEngine.applyCustomTheme(primary, secondary, fontChoice, iconStyle)
        prefs.putString("active_theme_id", "custom")
        prefs.putInt("custom_primary_argb", primary.toArgb())
        prefs.putInt("custom_secondary_argb", secondary.toArgb())
        prefs.putString("theme_font_choice", fontChoice.name)
        prefs.putString("theme_icon_style", iconStyle.name)
        val uid = activeCloudSyncUid()
        if (uid != null) {
            viewModelScope.launch {
                val active = FMSThemeEngine.activeTheme.value
                cloudRepository.saveCustomThemePreset(
                    userId = uid,
                    themeId = "custom_${name.lowercase().replace(Regex("[^a-z0-9]"), "_").take(24)}",
                    name = name,
                    primaryHex = primary.toHex(),
                    secondaryHex = secondary.toHex(),
                    surfaceHex = active.surface.toHex(),
                    fontChoice = fontChoice,
                    iconStyle = iconStyle
                ).onFailure { handleCloudWriteFailure("tema kustom", it) }
                cloudRepository.updateUserPreferences(uid, "custom", _uiState.value.languageCode)
                    .onFailure { handleCloudWriteFailure("preferensi tema kustom", it) }
            }
        }
        _uiState.update { it.copy(statusBanner = "Tema '$name' diterapkan melalui FMSThemeEngine.") }
    }

    fun setLanguage(code: String) {
        val clean = if (code in setOf("id", "en")) code else "id"
        prefs.putString("language_code", clean)
        _uiState.update { it.copy(languageCode = clean) }
        activeCloudSyncUid()?.let { uid ->
            viewModelScope.launch {
                cloudRepository.updateUserPreferences(uid, FMSThemeEngine.activeTheme.value.id, clean)
                    .onFailure { handleCloudWriteFailure("bahasa", it) }
            }
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val active = FMSThemeEngine.activeTheme.value
            val json = FMSBackupEngine.exportJson(
                configs = _uiState.value.localClones,
                activeThemeId = active.id,
                languageCode = _uiState.value.languageCode,
                fontChoice = active.fontChoice.name,
                iconStyle = active.iconStyle.name,
                customPrimaryArgb = if (active.id == "custom") active.primary.toArgb() else null,
                customSecondaryArgb = if (active.id == "custom") active.secondary.toArgb() else null
            )
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val outStream = runCatching { appContext.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                        ?: appContext.contentResolver.openOutputStream(uri)
                        ?: error("Tidak dapat menulis ke file tujuan.")
                    outStream.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            }
            res.onSuccess {
                _uiState.update { it.copy(statusBanner = "Backup JSON (${_uiState.value.localClones.size} konfigurasi) diekspor.") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Ekspor backup gagal.") }
            }
        }
    }

    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val bytesRes = withContext(Dispatchers.IO) {
                storage.readBytesFromUri(uri, FMSBackupEngine.MAX_BACKUP_BYTES.toLong())
            }
            bytesRes.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Gagal membaca file backup.") }
                return@launch
            }
            val parsed = FMSBackupEngine.parseAndValidate(bytesRes.getOrThrow())
            parsed.onSuccess { backup ->
                withContext(Dispatchers.IO) {
                    backup.cloneConfigs.forEach { database.upsertCloneConfig(it) }
                }
                val restoredFont = backup.fontChoice?.let { runCatching { FmsFontChoice.valueOf(it) }.getOrNull() }
                    ?: FMSThemeEngine.activeTheme.value.fontChoice
                val restoredIcon = backup.iconStyle?.let { runCatching { FmsIconStyle.valueOf(it) }.getOrNull() }
                    ?: FMSThemeEngine.activeTheme.value.iconStyle
                if (backup.activeThemeId == "custom" && backup.customPrimaryArgb != null && backup.customSecondaryArgb != null) {
                    applyCustomThemeAndSync(
                        name = "Restored Custom",
                        primary = Color(backup.customPrimaryArgb),
                        secondary = Color(backup.customSecondaryArgb),
                        fontChoice = restoredFont,
                        iconStyle = restoredIcon
                    )
                } else {
                    setFontChoice(restoredFont)
                    setIconStyle(restoredIcon)
                    applyThemePreset(backup.activeThemeId)
                }
                setLanguage(backup.languageCode)
                refreshLocalDatabase()
                var cloudSyncErrors = 0
                activeCloudSyncUid()?.let { uid ->
                    backup.cloneConfigs.forEach { cfg ->
                        cloudRepository.syncCloneConfig(uid, cfg).onFailure { err ->
                            cloudSyncErrors += 1
                            handleCloudWriteFailure("restore konfigurasi klon", err)
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        statusBanner = if (cloudSyncErrors == 0) {
                            "Restore berhasil: ${backup.cloneConfigs.size} konfigurasi dimuat."
                        } else {
                            "Restore lokal berhasil (${backup.cloneConfigs.size}), namun $cloudSyncErrors konfigurasi gagal sinkron cloud."
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Validasi backup gagal: ${err.message}") }
            }
        }
    }

    fun exportApkToUri(sourcePath: String, targetUri: Uri) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                storage.exportFileToUri(File(sourcePath), targetUri)
            }
            res.onSuccess {
                val matching = _uiState.value.localClones.filter { it.outputApkPath == sourcePath }
                if (matching.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        matching.forEach { rec ->
                            database.upsertCloneConfig(rec.copy(buildStatus = "EXPORTED", updatedAtMs = System.currentTimeMillis()))
                        }
                    }
                    refreshLocalDatabase()
                    activeCloudSyncUid()?.let { uid ->
                        matching.forEach { rec ->
                            cloudRepository.syncCloneConfig(uid, rec.copy(buildStatus = "EXPORTED"))
                                .onFailure { err -> handleCloudWriteFailure("status ekspor APK", err) }
                        }
                    }
                }
                _uiState.update { it.copy(statusBanner = "APK klon berhasil diekspor ke lokasi pilihan Anda.") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Gagal mengekspor APK.") }
            }
        }
    }

    fun createInstallIntent(apkPath: String): Intent? = runCatching {
        val file = File(apkPath)
        require(file.exists() && file.isFile) { "File APK tidak ditemukan." }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }.getOrNull()

    fun launchApkInstall(launcherContext: Context, apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) {
            _uiState.update { it.copy(errorMessage = "File APK klon sudah tidak ditemukan di penyimpanan internal.") }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !launcherContext.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${launcherContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { launcherContext.startActivity(settingsIntent) }
                .onSuccess {
                    _uiState.update {
                        it.copy(statusBanner = "Aktifkan izin 'Install unknown apps' untuk ZClone lalu ulangi pemasangan.")
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = "Tidak dapat membuka pengaturan izin instalasi: ${err.message}") }
                }
            return
        }
        val intent = createInstallIntent(apkPath)
        if (intent == null) {
            _uiState.update { it.copy(errorMessage = "Gagal menyiapkan FileProvider URI untuk pemasangan APK.") }
            return
        }
        runCatching { launcherContext.startActivity(intent) }.onFailure { err ->
            _uiState.update { it.copy(errorMessage = "Gagal memicu installer sistem: ${err.message}") }
        }
    }

    fun verifyClonedAppInstalled(targetPackage: String) {
        val pm = appContext.packageManager
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(targetPackage, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(targetPackage, 0)
            }
        }.getOrNull()

        _uiState.update { state ->
            val current = state.deviceVerification ?: DeviceCloneVerificationState(targetPackage = targetPackage)
            if (pkgInfo != null) {
                val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                state.copy(
                    deviceVerification = current.copy(
                        targetPackage = targetPackage,
                        stage8Installed = ValidationCheckStatus.PASS,
                        stage8Detail = "Terpasang di perangkat (v${pkgInfo.versionName ?: "?"} · code=$verCode)."
                    ),
                    statusBanner = "Tahap 8 LULUS: $targetPackage terdeteksi terpasang di perangkat."
                )
            } else {
                state.copy(
                    deviceVerification = current.copy(
                        targetPackage = targetPackage,
                        stage8Installed = ValidationCheckStatus.FAILED,
                        stage8Detail = "Paket $targetPackage belum terdeteksi terpasang di PackageManager."
                    ),
                    errorMessage = "Paket $targetPackage belum terpasang. Tekan Pasang APK dan selesaikan proses installer Android."
                )
            }
        }
    }

    fun testLaunchClonedApp(launcherContext: Context, targetPackage: String) {
        val pm = launcherContext.packageManager
        val isInstalled = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(targetPackage, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(targetPackage, 0)
            }
        }.isSuccess

        if (!isInstalled) {
            _uiState.update { state ->
                val current = state.deviceVerification ?: DeviceCloneVerificationState(targetPackage = targetPackage)
                state.copy(
                    deviceVerification = current.copy(
                        targetPackage = targetPackage,
                        stage8Installed = ValidationCheckStatus.FAILED,
                        stage8Detail = "Paket $targetPackage belum terpasang.",
                        stage9FirstLaunch = ValidationCheckStatus.FAILED,
                        stage9Detail = "Tidak dapat menguji peluncuran sebelum APK terpasang."
                    ),
                    errorMessage = "Pasang APK klon ($targetPackage) terlebih dahulu sebelum menguji peluncuran."
                )
            }
            return
        }

        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent == null) {
            _uiState.update { state ->
                val current = state.deviceVerification ?: DeviceCloneVerificationState(targetPackage = targetPackage)
                state.copy(
                    deviceVerification = current.copy(
                        targetPackage = targetPackage,
                        stage8Installed = ValidationCheckStatus.PASS,
                        stage9FirstLaunch = ValidationCheckStatus.FAILED,
                        stage9Detail = "Tidak ada Launcher Activity (kemungkinan mode Hide from Launcher aktif)."
                    ),
                    errorMessage = "Launcher Activity tidak tersedia untuk $targetPackage."
                )
            }
            return
        }

        runCatching {
            launcherContext.startActivity(launchIntent)
        }.onSuccess {
            _uiState.update { state ->
                val current = state.deviceVerification ?: DeviceCloneVerificationState(targetPackage = targetPackage)
                state.copy(
                    deviceVerification = current.copy(
                        targetPackage = targetPackage,
                        stage8Installed = ValidationCheckStatus.PASS,
                        stage8Detail = "Paket $targetPackage terpasang di perangkat.",
                        stage9FirstLaunch = ValidationCheckStatus.PASS,
                        stage9Detail = "Peluncuran pertama berhasil dimulai dari ZClone — periksa kestabilan aplikasi."
                    ),
                    statusBanner = "Tahap 9 dipicu: periksa apakah klon berjalan normal lalu konfirmasi Tahap 10."
                )
            }
        }.onFailure { err ->
            _uiState.update { state ->
                val current = state.deviceVerification ?: DeviceCloneVerificationState(targetPackage = targetPackage)
                state.copy(
                    deviceVerification = current.copy(
                        targetPackage = targetPackage,
                        stage8Installed = ValidationCheckStatus.PASS,
                        stage9FirstLaunch = ValidationCheckStatus.FAILED,
                        stage9Detail = "Gagal meluncurkan: ${err.message ?: err.javaClass.simpleName}"
                    ),
                    errorMessage = "Peluncuran klon gagal: ${err.message}"
                )
            }
        }
    }

    fun recordPostInstallFunctionalCheck(targetPackage: String, passed: Boolean, note: String = "") {
        _uiState.update { state ->
            val current = state.deviceVerification ?: DeviceCloneVerificationState(targetPackage = targetPackage)
            val suffix = note.trim().takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""
            val updated = if (passed) {
                current.copy(
                    targetPackage = targetPackage,
                    stage10MainFunction = ValidationCheckStatus.PASS,
                    stage10Detail = "Terverifikasi berjalan normal pada perangkat fisik$suffix."
                )
            } else {
                current.copy(
                    targetPackage = targetPackage,
                    stage10MainFunction = ValidationCheckStatus.FAILED,
                    stage10Detail = "Dilaporkan gagal / force close saat pengujian perangkat$suffix."
                )
            }
            state.copy(
                deviceVerification = updated,
                statusBanner = if (passed) {
                    "Tahap 10 dicatat LULUS untuk $targetPackage."
                } else {
                    "Tahap 10 dicatat GAGAL — laporkan detail error untuk diagnosa."
                }
            )
        }
    }

    internal fun activeCloudSyncUid(): String? {
        val st = _uiState.value
        val uid = st.userId ?: return null
        if (!st.isOnline || st.isLocalDeviceRevoked || st.authSession.status == FmsAuthSessionStatus.TOKEN_EXPIRED) {
            return null
        }
        return uid
    }

    fun refreshAuthSession(context: Context = appContext) {
        viewModelScope.launch {
            val res = FMSAuthEngine.refreshActiveSession(context, forceRefresh = true)
            res.onSuccess { snap ->
                val uid = snap.uid
                if (uid != null && !_uiState.value.isLocalDeviceRevoked && deviceSyncJob == null && cloudDataSyncJobs.isEmpty()) {
                    attachAuthenticatedSession(uid, snap.displayName, snap.email)
                }
                _uiState.update { it.copy(statusBanner = "Sesi akun Google terverifikasi & token diperbarui.") }
            }.onFailure { err ->
                val expired = FMSAuthEngine.sessionState.value.status == FmsAuthSessionStatus.TOKEN_EXPIRED
                if (expired) {
                    detachAuthenticatedSession()
                }
                _uiState.update {
                    it.copy(
                        syncState = if (expired) FmsSyncState.LOCAL_ONLY else if (!_uiState.value.isOnline) FmsSyncState.OFFLINE else FmsSyncState.ERROR,
                        errorMessage = err.message ?: "Gagal memverifikasi sesi akun Google."
                    )
                }
            }
        }
    }

    internal fun applyCloudDevicesSnapshot(
        list: List<Pair<String, CloudDeviceRecord>>,
        expectedUid: String? = _uiState.value.userId
    ) {
        val currentUid = _uiState.value.userId
        if (expectedUid != null && currentUid != expectedUid) {
            return
        }
        val localUuid = _uiState.value.localDevice?.installUuid
        val localCloudRecord = if (localUuid != null) {
            list.firstOrNull { it.second.installUuid == localUuid }?.second
        } else null
        val revokedNow = localCloudRecord?.status == "REVOKED"
        FMSAuthEngine.setDeviceRevoked(revokedNow)
        if (revokedNow) {
            stopCloudDataSyncListeners()
        }
        _uiState.update { state ->
            state.copy(
                cloudDevices = list,
                isLocalDeviceRevoked = revokedNow,
                syncState = when {
                    revokedNow -> FmsSyncState.ERROR
                    !state.isOnline -> FmsSyncState.OFFLINE
                    else -> FmsSyncState.SYNCED
                },
                errorMessage = if (revokedNow) {
                    "Perangkat ini berstatus REVOKED. Sinkronisasi cloud dikunci; silakan keluar akun untuk kembali ke mode lokal."
                } else state.errorMessage
            )
        }
    }

    fun attachAuthenticatedSession(uid: String, displayName: String?, email: String?) {
        stopCloudSyncListeners()
        val uidChanged = _uiState.value.userId != null && _uiState.value.userId != uid
        val onlineNow = _uiState.value.isOnline
        FMSAuthEngine.updateConnectivityState(onlineNow)
        _uiState.update {
            it.copy(
                userId = uid,
                userDisplayName = displayName ?: "Pengguna ZClone",
                userEmail = email,
                userTier = if (uidChanged) LicenseTier.FREE else it.userTier,
                deviceLimit = if (uidChanged) 3 else it.deviceLimit,
                cloudDevices = if (uidChanged) emptyList() else it.cloudDevices,
                isLocalDeviceRevoked = if (uidChanged) false else it.isLocalDeviceRevoked,
                syncState = if (onlineNow) FmsSyncState.SYNCING else FmsSyncState.OFFLINE
            )
        }

        if (!onlineNow) {
            return
        }

        val sessionUid = uid
        deviceSyncJob = viewModelScope.launch {
            cloudRepository.observeDevices(sessionUid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (_uiState.value.userId != sessionUid) return@catch
                    val safeMsg = FmsLogger.sanitizeMessage((fsErr ?: error).message ?: error.javaClass.simpleName)
                    FmsLogger.warn("ZCloneVM", "Error observing devices: $safeMsg")
                    _uiState.update {
                        it.copy(
                            syncState = FmsSyncState.ERROR,
                            errorMessage = "Gagal memuat daftar perangkat cloud: $safeMsg"
                        )
                    }
                }
                .collect { list ->
                    applyCloudDevicesSnapshot(list, expectedUid = sessionUid)
                }
        }

        val dataJobs = mutableListOf<Job>()
        dataJobs += viewModelScope.launch {
            val profRes = cloudRepository.ensureUserProfile(
                userId = sessionUid,
                displayName = displayName ?: "ZClone User",
                activeThemeId = FMSThemeEngine.activeTheme.value.id,
                languageCode = _uiState.value.languageCode
            )
            if (_uiState.value.userId != sessionUid) return@launch
            var resolvedLimit = 3
            profRes.onSuccess { prof ->
                resolvedLimit = prof.deviceLimit.toInt().coerceIn(1, 20)
                _uiState.update {
                    it.copy(
                        userTier = prof.toTier(),
                        deviceLimit = resolvedLimit,
                        syncState = if (it.isLocalDeviceRevoked) FmsSyncState.ERROR else FmsSyncState.SYNCED
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(syncState = FmsSyncState.ERROR, errorMessage = err.message) }
            }
            var deviceBlocked = _uiState.value.isLocalDeviceRevoked
            _uiState.value.localDevice?.let { dev ->
                if (_uiState.value.userId != sessionUid) return@launch
                val regRes = cloudRepository.registerOrSyncDevice(sessionUid, dev, deviceLimit = resolvedLimit)
                if (_uiState.value.userId != sessionUid) return@launch
                regRes.onSuccess { status ->
                    when (status) {
                        "REVOKED" -> {
                            deviceBlocked = true
                            FMSAuthEngine.setDeviceRevoked(true)
                            stopCloudDataSyncListeners()
                            _uiState.update {
                                it.copy(
                                    isLocalDeviceRevoked = true,
                                    syncState = FmsSyncState.ERROR,
                                    errorMessage = "Perangkat ini berstatus REVOKED di akun Anda. Sinkronisasi cloud dihentikan."
                                )
                            }
                        }
                        "INACTIVE" -> {
                            _uiState.update {
                                it.copy(
                                    statusBanner = "Kuota perangkat aktif tier penuh ($resolvedLimit). Perangkat ini didaftarkan sebagai Nonaktif di Device Manager."
                                )
                            }
                        }
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            syncState = FmsSyncState.ERROR,
                            errorMessage = "Gagal sinkronisasi perangkat ke Firestore: ${err.message}"
                        )
                    }
                }
            }
            if (!deviceBlocked && _uiState.value.userId == sessionUid && !_uiState.value.isLocalDeviceRevoked) {
                // Push any local-first clones & security reports created offline to cloud
                _uiState.value.localClones.forEach { local ->
                    if (activeCloudSyncUid() == sessionUid) {
                        cloudRepository.syncCloneConfig(sessionUid, local).onFailure { err ->
                            handleCloudWriteFailure("sinkronisasi konfigurasi klon", err)
                        }
                    }
                }
                _uiState.value.localSecurityReports.forEach { rep ->
                    if (activeCloudSyncUid() == sessionUid) {
                        cloudRepository.syncSecurityReport(sessionUid, rep).onFailure { err ->
                            handleCloudWriteFailure("sinkronisasi laporan keamanan", err)
                        }
                    }
                }
            }
        }

        dataJobs += viewModelScope.launch {
            cloudRepository.observeCloneConfigs(sessionUid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (_uiState.value.userId != sessionUid) return@catch
                    val safeMsg = FmsLogger.sanitizeMessage((fsErr ?: error).message ?: error.javaClass.simpleName)
                    FmsLogger.warn("ZCloneVM", "Error observing cloneConfigs: $safeMsg")
                    _uiState.update { it.copy(syncState = FmsSyncState.ERROR) }
                }
                .collect { remoteList ->
                    if (_uiState.value.userId != sessionUid || _uiState.value.isLocalDeviceRevoked) return@collect
                    if (remoteList.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            val localById = _uiState.value.localClones.associateBy { it.id }
                            remoteList.forEach { (id, cfg) ->
                                if (_uiState.value.userId != sessionUid) return@withContext
                                val existing = localById[id]
                                if (existing == null || existing.outputApkPath == null) {
                                    database.upsertCloneConfig(
                                        LocalCloneRecord(
                                            id = id,
                                            sourcePackage = cfg.sourcePackage,
                                            targetPackage = cfg.targetPackage,
                                            sourceAppName = cfg.sourceAppName,
                                            cloneName = cfg.cloneName,
                                            iconPresetId = cfg.iconPresetId,
                                            iconHueShift = cfg.iconHueShift.toInt(),
                                            changePackageName = cfg.changePackageName,
                                            hideFromLauncher = cfg.hideFromLauncher,
                                            isolateData = cfg.isolateData,
                                            strippedPermissionsCsv = existing?.strippedPermissionsCsv.orEmpty(),
                                            compatibilityStatus = cfg.compatibilityStatus,
                                            buildStatus = cfg.buildStatus,
                                            outputApkPath = existing?.outputApkPath,
                                            updatedAtMs = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                        if (_uiState.value.userId == sessionUid) {
                            refreshLocalDatabase()
                        }
                    }
                }
        }

        dataJobs += viewModelScope.launch {
            cloudRepository.observeSecurityReports(sessionUid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (_uiState.value.userId != sessionUid) return@catch
                    val safeMsg = FmsLogger.sanitizeMessage((fsErr ?: error).message ?: error.javaClass.simpleName)
                    FmsLogger.warn("ZCloneVM", "Error observing securityReports: $safeMsg")
                    _uiState.update { it.copy(syncState = FmsSyncState.ERROR) }
                }
                .collect { remoteReports ->
                    if (_uiState.value.userId != sessionUid || _uiState.value.isLocalDeviceRevoked) return@collect
                    if (remoteReports.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            remoteReports.forEach { (id, rep) ->
                                if (_uiState.value.userId != sessionUid) return@withContext
                                database.upsertSecurityReport(
                                    LocalSecurityReportRecord(
                                        id = id,
                                        packageName = rep.packageName,
                                        appName = rep.appName,
                                        riskScore = rep.riskScore.toInt(),
                                        riskLevel = rep.riskLevel,
                                        exportedComponentsCount = rep.exportedComponentsCount.toInt(),
                                        dangerousPermissionsCount = rep.dangerousPermissionsCount.toInt(),
                                        cleartextAllowed = rep.cleartextAllowed,
                                        debuggableFlag = rep.debuggableFlag,
                                        certSha256Prefix = rep.certSha256Prefix,
                                        summary = rep.summary,
                                        scannedAtMs = rep.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                        if (_uiState.value.userId == sessionUid) {
                            refreshLocalDatabase()
                        }
                    }
                }
        }

        dataJobs += viewModelScope.launch {
            cloudRepository.observeThemePresets(sessionUid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (_uiState.value.userId != sessionUid) return@catch
                    val safeMsg = FmsLogger.sanitizeMessage((fsErr ?: error).message ?: error.javaClass.simpleName)
                    FmsLogger.warn("ZCloneVM", "Error observing themePresets: $safeMsg")
                    _uiState.update { it.copy(syncState = FmsSyncState.ERROR) }
                }
                .collect { remoteThemes ->
                    if (_uiState.value.userId != sessionUid || _uiState.value.isLocalDeviceRevoked) return@collect
                    if (prefs.getString("active_theme_id", "dark_neon") == "custom" && remoteThemes.isNotEmpty()) {
                        val latest = remoteThemes.last().second
                        val primary = runCatching { Color(android.graphics.Color.parseColor(latest.primaryHex)) }.getOrNull()
                        val secondary = runCatching { Color(android.graphics.Color.parseColor(latest.secondaryHex)) }.getOrNull()
                        if (primary != null && secondary != null) {
                            val font = runCatching { FmsFontChoice.valueOf(latest.fontFamily) }.getOrDefault(FmsFontChoice.SPACE_GROTESK)
                            val icon = runCatching { FmsIconStyle.valueOf(latest.iconStyle) }.getOrDefault(FmsIconStyle.SQUIRCLE)
                            FMSThemeEngine.applyCustomTheme(primary, secondary, font, icon)
                        }
                    }
                }
        }

        cloudDataSyncJobs = dataJobs
    }

    fun detachAuthenticatedSession() {
        stopCloudSyncListeners()
        FMSAuthEngine.setDeviceRevoked(false)
        _uiState.update {
            it.copy(
                userId = null,
                userDisplayName = null,
                userEmail = null,
                userTier = LicenseTier.FREE,
                deviceLimit = 3,
                cloudDevices = emptyList(),
                isLocalDeviceRevoked = false,
                syncState = FmsSyncState.LOCAL_ONLY
            )
        }
    }

    fun updateLocalDeviceLabel(newLabel: String) {
        val clean = newLabel.trim().take(60)
        if (clean.isEmpty()) return
        deviceManager.updateDeviceLabel(clean)
        val updated = deviceManager.getOrCreateInstallIdentity("1.0.0")
        _uiState.update { it.copy(localDevice = updated, statusBanner = "Label perangkat diperbarui: ${updated.deviceLabel}") }
        activeCloudSyncUid()?.let { uid ->
            viewModelScope.launch {
                cloudRepository.registerOrSyncDevice(uid, updated, deviceLimit = _uiState.value.deviceLimit).onFailure { err ->
                    _uiState.update {
                        it.copy(
                            syncState = FmsSyncState.ERROR,
                            errorMessage = "Label disimpan lokal, gagal sinkron cloud: ${err.message}"
                        )
                    }
                }
            }
        }
    }

    fun updateDeviceStatus(deviceId: String, status: String) {
        val uid = _uiState.value.userId ?: return
        val localDocId = _uiState.value.localDevice?.installUuid?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")?.take(128)
        if (_uiState.value.isLocalDeviceRevoked) {
            _uiState.update {
                it.copy(errorMessage = "Perangkat ini berstatus REVOKED dan terkunci. Tidak dapat memulihkan diri sendiri atau mengubah status perangkat lain.")
            }
            return
        }
        val targetCurrentStatus = _uiState.value.cloudDevices.firstOrNull { it.first == deviceId }?.second?.status
        if (targetCurrentStatus == "REVOKED") {
            _uiState.update {
                it.copy(errorMessage = "Perangkat berstatus REVOKED terkunci oleh aturan keamanan dan tidak dapat diaktifkan kembali oleh klien.")
            }
            return
        }
        if (deviceId == localDocId && status == "REVOKED") {
            _uiState.update {
                it.copy(errorMessage = "Gunakan Keluar Akun untuk memutus perangkat aktif ini; cabut perangkat dilakukan dari perangkat lain.")
            }
            return
        }
        if (!_uiState.value.isOnline) {
            _uiState.update {
                it.copy(errorMessage = "Perubahan status perangkat membutuhkan koneksi internet aktif.")
            }
            return
        }
        if (status == "ACTIVE") {
            val activeCount = _uiState.value.cloudDevices.count { it.second.status == "ACTIVE" && it.first != deviceId }
            val quota = deviceManager.checkQuota(activeCount + 1, _uiState.value.userTier, _uiState.value.deviceLimit)
            if (!quota.withinQuota) {
                _uiState.update {
                    it.copy(errorMessage = "Kuota perangkat aktif tier ${_uiState.value.userTier.displayName} penuh (${quota.limit}). Nonaktifkan perangkat lain dahulu.")
                }
                return
            }
        }
        viewModelScope.launch {
            cloudRepository.setDeviceStatus(uid, deviceId, status, callerDeviceId = localDocId)
                .onSuccess {
                    _uiState.update { it.copy(statusBanner = "Status perangkat diperbarui: $status") }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            syncState = FmsSyncState.ERROR,
                            errorMessage = "Gagal memperbarui status perangkat: ${err.message}"
                        )
                    }
                }
        }
    }

    private fun handleCloudWriteFailure(contextLabel: String, err: Throwable) {
        _uiState.update {
            it.copy(
                syncState = if (!it.isOnline) FmsSyncState.OFFLINE else FmsSyncState.ERROR,
                errorMessage = "Sinkronisasi $contextLabel ke cloud gagal: ${err.message ?: err.javaClass.simpleName}"
            )
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = null, errorMessage = null) }
    }

    private fun stopCloudDataSyncListeners() {
        cloudDataSyncJobs.forEach { it.cancel() }
        cloudDataSyncJobs = emptyList()
    }

    private fun stopCloudSyncListeners() {
        deviceSyncJob?.cancel()
        deviceSyncJob = null
        stopCloudDataSyncListeners()
    }

    override fun onCleared() {
        super.onCleared()
        stopCloudSyncListeners()
        backgroundEngine.shutdown()
    }

    companion object {
        fun buildValidBatchTargetPackage(sourcePackage: String, index: Int): String {
            val suffix = ".zclone$index"
            val maxPrefix = (150 - suffix.length).coerceAtLeast(3)
            var prefix = sourcePackage.take(maxPrefix).trimEnd('.')
            while (prefix.contains('.')) {
                val candidate = "$prefix$suffix"
                if (com.fms.security.FMSIntegrityEngine.isValidPackageName(candidate)) {
                    return candidate
                }
                prefix = prefix.substringBeforeLast('.').trimEnd('.')
            }
            val safeFallback = "com.zclone.app$index"
            return if (com.fms.security.FMSIntegrityEngine.isValidPackageName(safeFallback)) safeFallback else "com.zclone.clone$index"
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = context.applicationContext
            return ZCloneViewModel(
                appContext = app,
                database = FMSDatabaseEngine(app),
                prefs = FMSPreferenceEngine(app),
                storage = FMSStorageEngine(app),
                inspector = FMSAPKInspector(app),
                deviceManager = FMSDeviceManager(app),
                backgroundEngine = FMSBackgroundEngine(),
                cloudRepository = ZCloneCloudRepository(app)
            ) as T
        }
    }
}
