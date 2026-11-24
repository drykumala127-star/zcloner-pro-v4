package com.fms.zclone.enginecoremode

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.fms.cloud.FMSAuthEngine
import com.fms.cloud.FmsAuthRateLimiter
import com.fms.cloud.FmsAuthSessionSnapshot
import com.fms.cloud.FmsAuthSessionStatus
import com.fms.cloud.FmsSyncState
import com.fms.core.FmsLogger
import com.fms.device.FMSDeviceManager
import com.fms.ui.FMSThemeEngine
import com.fms.zclone.enginecoremode.zclone.data.CloudDeviceRecord
import com.fms.zclone.enginecoremode.zclone.data.OperationType
import com.fms.zclone.enginecoremode.zclone.data.ZCloneCloudRepository
import com.fms.zclone.enginecoremode.zclone.data.handleFirestoreError
import com.fms.zclone.enginecoremode.zclone.ui.ZCloneUiState
import com.fms.zclone.enginecoremode.zclone.ui.ZCloneViewModel
import com.fms.zclone.enginecoremode.zclone.ui.screens.CloudAuthDialogCard
import com.fms.zclone.enginecoremode.zclone.ui.screens.DeviceManagerScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthSessionHardeningTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApplicationId("1:759195378420:android:test000000000000000000")
                    .setApiKey("AIzaSyTestFakeKeyForRobolectricUnitTests00")
                    .setProjectId("zclone-fms")
                    .build()
            )
        }
        FmsLogger.clear()
        FMSAuthEngine.resetForTesting(online = true)
    }

    @After
    fun tearDown() {
        FMSAuthEngine.resetForTesting(online = true)
    }

    @Test
    fun `rate limiter enforces exponential cooldown after threshold and resets on success`() {
        var fakeNow = 10_000L
        val limiter = FmsAuthRateLimiter(
            baseCooldownMs = 3_000L,
            maxCooldownMs = 20_000L,
            maxFailuresBeforeCooldown = 3,
            nowProvider = { fakeNow }
        )

        assertEquals(0L, limiter.checkAllowed())
        assertEquals(0L, limiter.recordFailure())
        assertEquals(0L, limiter.recordFailure())
        assertEquals(3_000L, limiter.recordFailure())
        assertEquals(3_000L, limiter.checkAllowed())

        fakeNow += 1_500L
        assertEquals(1_500L, limiter.checkAllowed())

        assertEquals(6_000L, limiter.recordFailure())
        fakeNow += 6_001L
        assertEquals(0L, limiter.checkAllowed())

        assertEquals(12_000L, limiter.recordFailure())
        assertEquals(20_000L, limiter.recordFailure())

        limiter.recordSuccess()
        assertEquals(0, limiter.failureCount())
        assertEquals(0L, limiter.checkAllowed())
    }

    @Test
    fun `offline sign-in and session refresh fail honestly without launching credential flow`() = runBlocking {
        val cm = CredentialManager.create(appContext)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var successCalled = false
            var errorMsg: String? = null

            FMSAuthEngine.onGoogleSignInClicked(
                context = appContext,
                credentialManager = cm,
                onAuthSuccess = { successCalled = true },
                onAuthError = { errorMsg = it },
                scope = scope,
                isOnlineOverride = false
            )

            assertFalse(successCalled)
            assertNotNull(errorMsg)
            assertTrue(errorMsg!!.contains("offline", ignoreCase = true))
            assertFalse(FMSAuthEngine.sessionState.value.isOnline)
            assertEquals(FmsAuthSessionStatus.UNAUTHENTICATED, FMSAuthEngine.sessionState.value.status)

            var autoSuccess = false
            var autoUnauth = false
            FMSAuthEngine.attemptAutoSignIn(
                context = appContext,
                credentialManager = cm,
                onAuthSuccess = { autoSuccess = true },
                onUnauthenticated = { autoUnauth = true },
                scope = scope,
                isOnlineOverride = false
            )

            assertFalse(autoSuccess)
            assertTrue(autoUnauth)

            val refreshRes = FMSAuthEngine.refreshActiveSession(
                context = appContext,
                forceRefresh = true,
                isOnlineOverride = false
            )
            assertTrue(refreshRes.isFailure)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `viewmodel blocks cloud sync when local device is revoked and clears stale devices on uid switch`() {
        val dbName = "auth_hardening_${System.nanoTime()}.db"
        val db = com.fms.data.FMSDatabaseEngine(appContext, dbName)
        db.writableDatabase
        val vmStore = ViewModelStore()
        val vm = ZCloneViewModel(
            appContext = appContext,
            database = db,
            prefs = com.fms.data.FMSPreferenceEngine(appContext),
            storage = com.fms.data.FMSStorageEngine(appContext),
            inspector = com.fms.clone.FMSAPKInspector(appContext),
            deviceManager = FMSDeviceManager(appContext),
            backgroundEngine = com.fms.task.FMSBackgroundEngine(),
            cloudRepository = ZCloneCloudRepository(appContext)
        )
        try {
            ShadowLooper.idleMainLooper()
            Thread.sleep(150L)

            val localUuid = vm.uiState.value.localDevice!!.installUuid

            vm.applyCloudDevicesSnapshot(
                listOf(
                    localUuid to CloudDeviceRecord(
                        userId = "uid_alpha",
                        installUuid = localUuid,
                        deviceLabel = "Pixel Test",
                        status = "ACTIVE"
                    )
                )
            )
            assertFalse(vm.uiState.value.isLocalDeviceRevoked)

            vm.applyCloudDevicesSnapshot(
                listOf(
                    localUuid to CloudDeviceRecord(
                        userId = "uid_alpha",
                        installUuid = localUuid,
                        deviceLabel = "Pixel Test",
                        status = "REVOKED"
                    )
                )
            )
            assertTrue(vm.uiState.value.isLocalDeviceRevoked)
            assertEquals(FmsSyncState.ERROR, vm.uiState.value.syncState)
            assertEquals(FmsAuthSessionStatus.DEVICE_REVOKED, FMSAuthEngine.sessionState.value.status)
            assertNull(vm.activeCloudSyncUid())

            vm.applyCloudDevicesSnapshot(
                listOf(
                    localUuid to CloudDeviceRecord(
                        userId = "uid_alpha",
                        installUuid = localUuid,
                        deviceLabel = "Pixel Test",
                        status = "ACTIVE"
                    )
                )
            )
            assertFalse(vm.uiState.value.isLocalDeviceRevoked)

            vm.detachAuthenticatedSession()
            assertTrue(vm.uiState.value.cloudDevices.isEmpty())
            assertFalse(vm.uiState.value.isLocalDeviceRevoked)
            assertEquals(FmsSyncState.LOCAL_ONLY, vm.uiState.value.syncState)
        } finally {
            runCatching {
                val onCleared = ZCloneViewModel::class.java.getDeclaredMethod("onCleared")
                onCleared.isAccessible = true
                onCleared.invoke(vm)
            }
            vmStore.clear()
            Thread.sleep(100L)
            db.close()
        }
    }

    @Test
    fun `logger redacts bearer idToken password and no raw token is persisted`() {
        FmsLogger.info(
            "FMSAuth",
            "Auth failure token=eyJhbGciOiJSUzI1NiIsImtpZCI6IjEyMyJ9.fake bearer=abc123 password=secretPass"
        )
        val lastLog = FmsLogger.logsFlow.value.last().message
        assertFalse(lastLog.contains("eyJhbGci"))
        assertFalse(lastLog.contains("abc123"))
        assertFalse(lastLog.contains("secretPass"))
        assertTrue(lastLog.contains("[REDACTED]"))
    }

    @Test
    fun `auth dialog ui disables google sign-in offline and surfaces revoked session controls`() {
        val theme = FMSThemeEngine.presets.first()
        var mode by mutableStateOf("offline_unauth")

        composeRule.setContent {
            MaterialTheme {
                when (mode) {
                    "offline_unauth" -> CloudAuthDialogCard(
                        theme = theme,
                        languageCode = "id",
                        isSignedIn = false,
                        userEmail = null,
                        authSession = FmsAuthSessionSnapshot(
                            status = FmsAuthSessionStatus.UNAUTHENTICATED,
                            isOnline = false
                        ),
                        isOnline = false,
                        isLocalDeviceRevoked = false,
                        onAuthSuccess = {},
                        onSignOut = {},
                        onDismiss = {}
                    )
                    else -> CloudAuthDialogCard(
                        theme = theme,
                        languageCode = "id",
                        isSignedIn = true,
                        userEmail = "user@example.com",
                        authSession = FmsAuthSessionSnapshot(
                            status = FmsAuthSessionStatus.DEVICE_REVOKED,
                            uid = "uid_123",
                            email = "user@example.com",
                            isOnline = true,
                            lastVerifiedEpochMs = 1700000000000L
                        ),
                        isOnline = true,
                        isLocalDeviceRevoked = true,
                        onAuthSuccess = {},
                        onSignOut = {},
                        onDismiss = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("auth_offline_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsNotEnabled()

        composeRule.runOnUiThread { mode = "signed_in_revoked" }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_revoked_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("refresh_auth_session_button").assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_out_button").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_last_verified_label").assertIsDisplayed()
    }

    @Test
    fun `viewmodel blocks REVOKED self-reactivation and drops stale UID snapshots`() {
        val dbName = "fase2_revocation_${System.nanoTime()}.db"
        val db = com.fms.data.FMSDatabaseEngine(appContext, dbName)
        db.writableDatabase
        val vmStore = ViewModelStore()
        val vm = ZCloneViewModel(
            appContext = appContext,
            database = db,
            prefs = com.fms.data.FMSPreferenceEngine(appContext),
            storage = com.fms.data.FMSStorageEngine(appContext),
            inspector = com.fms.clone.FMSAPKInspector(appContext),
            deviceManager = FMSDeviceManager(appContext),
            backgroundEngine = com.fms.task.FMSBackgroundEngine(),
            cloudRepository = ZCloneCloudRepository(appContext)
        )
        try {
            ShadowLooper.idleMainLooper()
            Thread.sleep(150L)

            val localUuid = vm.uiState.value.localDevice!!.installUuid
            vm.attachAuthenticatedSession("uid_active", "Tester", null)

            vm.applyCloudDevicesSnapshot(
                list = listOf(
                    localUuid to CloudDeviceRecord(
                        userId = "uid_active",
                        installUuid = localUuid,
                        deviceLabel = "Locked Phone",
                        status = "REVOKED"
                    )
                ),
                expectedUid = "uid_active"
            )
            assertTrue(vm.uiState.value.isLocalDeviceRevoked)
            assertNull(vm.activeCloudSyncUid())

            // Self-reactivation attempt must be blocked locally
            vm.updateDeviceStatus(localUuid, "ACTIVE")
            assertTrue(vm.uiState.value.isLocalDeviceRevoked)
            assertTrue(vm.uiState.value.errorMessage?.contains("REVOKED") == true)

            // Stale UID snapshot emission must be ignored cleanly
            vm.applyCloudDevicesSnapshot(
                list = listOf(
                    "other_dev" to CloudDeviceRecord(
                        userId = "uid_stale_other",
                        installUuid = "other_uuid",
                        deviceLabel = "Wrong Account Device",
                        status = "ACTIVE"
                    )
                ),
                expectedUid = "uid_stale_other"
            )
            assertEquals(1, vm.uiState.value.cloudDevices.size)
            assertEquals(localUuid, vm.uiState.value.cloudDevices.first().first)
        } finally {
            runCatching {
                val onCleared = ZCloneViewModel::class.java.getDeclaredMethod("onCleared")
                onCleared.isAccessible = true
                onCleared.invoke(vm)
            }
            vmStore.clear()
            Thread.sleep(100L)
            db.close()
        }
    }

    @Test
    fun `handleFirestoreError redacts sensitive user path and omits raw PII fields`() {
        val diagnostic = handleFirestoreError(
            exception = RuntimeException("PERMISSION_DENIED"),
            operationType = OperationType.UPDATE,
            path = "users/sensitive_full_uid_secret_999/devices/dev1"
        )
        assertFalse(diagnostic.contains("sensitive_full_uid_secret_999"))
        assertFalse(diagnostic.contains("\"email\""))
        assertFalse(diagnostic.contains("\"userId\""))
        assertTrue(diagnostic.contains("userIdTag"))
        assertTrue(diagnostic.contains("PERMISSION_DENIED"))
    }

    @Test
    fun `device manager ui removes self-reactivation and locks revoked device cards`() {
        val theme = FMSThemeEngine.presets.first()
        val state = ZCloneUiState(
            languageCode = "id",
            isOnline = true,
            userId = "uid_alpha",
            isLocalDeviceRevoked = true,
            cloudDevices = listOf(
                "dev_revoked" to CloudDeviceRecord(
                    userId = "uid_alpha",
                    installUuid = "uuid_revoked_device",
                    deviceLabel = "Revoked Pixel",
                    modelName = "Pixel 8",
                    androidVersion = "Android 15",
                    status = "REVOKED"
                )
            )
        )

        composeRule.setContent {
            MaterialTheme {
                DeviceManagerScreen(
                    state = state,
                    theme = theme,
                    onBack = {},
                    onToggleDeviceStatus = { _, _ -> },
                    onUpdateDeviceLabel = {},
                    onOpenAuthDialog = {}
                )
            }
        }

        composeRule.onNodeWithTag("device_manager_list")
            .performScrollToNode(hasTestTag("device_revoked_alert_card"))
        composeRule.onNodeWithTag("device_revoked_alert_card").assertIsDisplayed()
        composeRule.onNodeWithTag("open_auth_from_revoked_button").assertIsDisplayed()
        composeRule.onNodeWithTag("device_manager_list")
            .performScrollToNode(hasTestTag("cloud_device_card_dev_revoked"))
        composeRule.onNodeWithTag("locked_revoked_label_dev_revoked").assertIsDisplayed()
        composeRule.onNodeWithTag("reactivate_local_device_button").assertDoesNotExist()
        composeRule.onNodeWithTag("toggle_device_dev_revoked").assertDoesNotExist()
    }
}

