package com.fms.clone

import android.graphics.Bitmap

enum class CompatibilityStatus {
    VERIFIED,
    COMPATIBLE,
    LIMITED,
    UNSUPPORTED
}

enum class ParityStatus {
    NOT_AUDITED,
    PLANNED,
    IMPLEMENTED,
    TESTED,
    VERIFIED,
    LIMITED,
    UNSUPPORTED,
    BLOCKED
}

enum class RiskLevel { LOW, MEDIUM, HIGH }

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val sourceApkPath: String?,
    val hasSplitApks: Boolean,
    val apkSizeBytes: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val requestedPermissions: List<String>,
    val iconBitmap: Bitmap? = null
)

data class ManifestSummary(
    val packageName: String,
    val applicationLabel: String?,
    val versionCode: Int?,
    val versionName: String?,
    val sharedUserId: String?,
    val debuggable: Boolean,
    val usesCleartextTraffic: Boolean,
    val allowBackup: Boolean,
    val permissions: List<String>,
    val exportedComponentsCount: Int,
    val launcherCategoriesCount: Int,
    val authorityStrings: List<String>,
    val splitName: String? = null,
    val configForSplit: String? = null,
    val isFeatureSplit: Boolean = false,
    val hasSplitsRequiredMeta: Boolean = false,
    val usesPlayLicensing: Boolean = false,
    val minSdkVersion: Int? = null,
    val launcherActivityName: String? = null,
    val targetSdkVersion: Int? = null,
    val extractNativeLibs: Boolean? = null,
    val customPermissions: List<String> = emptyList(),
    val referenceAuthoritiesCount: Int = 0,
    val requiredUsesLibraries: List<String> = emptyList(),
    val usesAppComponentFactory: String? = null
)

data class ApkInspectionResult(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val apkSizeBytes: Long,
    val entryCount: Int,
    val hasSplitApks: Boolean,
    val hasDex: Boolean,
    val nativeAbis: List<String>,
    val iconEntryPaths: List<String>,
    val certSha256: String,
    val certSubject: String,
    val manifestSummary: ManifestSummary,
    val compatibility: CompatibilityAnalysis,
    val hasApkSigBlockV2Plus: Boolean = false
)

data class CompatibilityAnalysis(
    val status: CompatibilityStatus,
    val score: Int,
    val blockers: List<String>,
    val warnings: List<String>,
    val notes: String
)

data class SecurityScanFinding(
    val id: String,
    val title: String,
    val severity: RiskLevel,
    val detail: String,
    val remediation: String
)

data class SecurityScanResult(
    val packageName: String,
    val appName: String,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val exportedComponentsCount: Int,
    val dangerousPermissionsCount: Int,
    val cleartextAllowed: Boolean,
    val debuggableFlag: Boolean,
    val certSha256Prefix: String,
    val findings: List<SecurityScanFinding>,
    val summary: String,
    val limitationNotice: String
)

data class CloneBuildRequest(
    val sourceApkPath: String,
    val sourcePackage: String,
    val targetPackage: String,
    val sourceAppName: String,
    val cloneName: String,
    val iconPresetId: String,
    val iconHueShift: Int,
    val changePackageName: Boolean,
    val hideFromLauncher: Boolean,
    val isolateData: Boolean,
    val strippedPermissions: Set<String>,
    val disableDebuggable: Boolean = true,
    val rewriteProviderAuthorities: Boolean = true,
    val ownerConfirmedModificationRights: Boolean = true,
    val injectDhedryLoading: Boolean = true,
    val dhedryDexBytes: ByteArray? = null,
    val dhedryAudioMp3Bytes: ByteArray? = null,
    val reproducibleBuild: Boolean = true,
    val embedDependencyGraph: Boolean = true
)

enum class ValidationCheckCategory {
    AUTOMATED_BUILD,
    DEVICE_RUNTIME
}

enum class ValidationCheckStatus {
    PASS,
    WARNING,
    FAILED,
    PENDING_DEVICE_TEST
}

data class ApkValidationStep(
    val stageNumber: Int,
    val titleId: String,
    val titleEn: String,
    val category: ValidationCheckCategory,
    val status: ValidationCheckStatus,
    val detail: String
)

data class ApkValidationReport(
    val passedAutomatedChecks: Boolean,
    val steps: List<ApkValidationStep>,
    val summaryMessage: String,
    val canInstallOnDevice: Boolean
)

data class CloneBuildFailureInfo(
    val failedStageNumber: Int?,
    val failedStageTitle: String,
    val userMessage: String,
    val technicalCause: String,
    val sourcePreservedNotice: String,
    val suggestedAction: String
)

data class CloneBuildOutput(
    val outputApkPath: String,
    val targetPackage: String,
    val cloneName: String,
    val outputSizeBytes: Long,
    val outputSha256: String,
    val signingSha256: String,
    val modifiedManifest: Boolean,
    val patchedIconsCount: Int,
    val strippedPermissionsCount: Int,
    val signatureVerified: Boolean,
    val warnings: List<String>,
    val dhedrySplashInjected: Boolean = false,
    val dhedryDexEntryName: String? = null,
    val dhedryTargetActivity: String? = null,
    val reproducibleBuildApplied: Boolean = true,
    val dependencyGraphEmbedded: Boolean = false,
    val validationReport: ApkValidationReport? = null
)

data class ComponentNode(
    val kind: String,
    val name: String,
    val exported: Boolean,
    val authorityOrPerm: String? = null
)

data class NativeLibDependencyNode(
    val abi: String,
    val path: String,
    val fileName: String,
    val neededLibraries: List<String>,
    val isElf64: Boolean
)

data class ApkDependencyGraph(
    val packageName: String,
    val versionCode: Long,
    val dexFiles: List<String>,
    val components: List<ComponentNode>,
    val queriedPackages: List<String>,
    val queriedActions: List<String>,
    val providerAuthorities: List<String>,
    val requiredUsesLibraries: List<String>,
    val nativeLibs: List<NativeLibDependencyNode>,
    val sha256Source: String
)

data class BinaryVersionDiffReport(
    val apkAPath: String,
    val apkBPath: String,
    val packageA: String,
    val packageB: String,
    val versionCodeA: Long,
    val versionCodeB: Long,
    val versionNameA: String,
    val versionNameB: String,
    val minSdkA: Int?,
    val minSdkB: Int?,
    val targetSdkA: Int?,
    val targetSdkB: Int?,
    val sizeDeltaBytes: Long,
    val addedPermissions: List<String>,
    val removedPermissions: List<String>,
    val addedComponents: List<String>,
    val removedComponents: List<String>,
    val addedDexFiles: List<String>,
    val removedDexFiles: List<String>,
    val addedNativeLibs: List<String>,
    val removedNativeLibs: List<String>,
    val addedDhedryModules: List<String>,
    val removedDhedryModules: List<String>,
    val sha256A: String,
    val sha256B: String
)

data class FeatureParityRecord(
    val featureId: String,
    val featureName: String,
    val officialCategory: String,
    val officialSourceUrl: String,
    val description: String,
    val fmsModule: String,
    val proposedApi: String,
    val androidRequirements: String,
    val requiredPermissions: String,
    val externalDependencies: String,
    val securityImplications: String,
    val technicalLimitations: String,
    val testScenarios: String,
    val acceptanceCriteria: String,
    val status: ParityStatus,
    val evidence: String
)
