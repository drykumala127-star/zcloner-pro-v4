package com.fms.cloud

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.fms.core.FmsLogger
import com.fms.zclone.enginecoremode.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * FMSConnectivity: Observes real network availability so ZClone can operate Local-First seamlessly.
 */
object FMSConnectivity {
    fun isOnline(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun observeOnline(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        trySend(isOnline(context))
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(isOnline(context)) }
        }
        cm.registerDefaultNetworkCallback(cb)
        awaitClose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
}

enum class FmsAuthSessionStatus {
    UNAUTHENTICATED,
    VERIFIED_ONLINE,
    CACHED_OFFLINE,
    TOKEN_EXPIRED,
    DEVICE_REVOKED
}

data class FmsAuthSessionSnapshot(
    val status: FmsAuthSessionStatus = FmsAuthSessionStatus.UNAUTHENTICATED,
    val uid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val isOnline: Boolean = true,
    val lastVerifiedEpochMs: Long? = null,
    val lastTokenRefreshEpochMs: Long? = null,
    val rateLimitRemainingMs: Long = 0L,
    val statusNote: String = ""
)

/**
 * Thread-safe exponential backoff limiter for interactive authentication retries.
 */
class FmsAuthRateLimiter(
    private val baseCooldownMs: Long = 3_000L,
    private val maxCooldownMs: Long = 60_000L,
    private val maxFailuresBeforeCooldown: Int = 3,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Any()
    private var consecutiveFailures: Int = 0
    private var cooldownUntilMs: Long = 0L

    fun remainingCooldownMs(): Long = synchronized(lock) {
        val now = nowProvider()
        (cooldownUntilMs - now).coerceAtLeast(0L)
    }

    fun failureCount(): Int = synchronized(lock) { consecutiveFailures }

    fun checkAllowed(): Long = remainingCooldownMs()

    fun recordFailure(): Long = synchronized(lock) {
        consecutiveFailures += 1
        if (consecutiveFailures < maxFailuresBeforeCooldown) {
            return@synchronized 0L
        }
        val exponent = (consecutiveFailures - maxFailuresBeforeCooldown).coerceIn(0, 6)
        val cooldown = (baseCooldownMs shl exponent).coerceAtMost(maxCooldownMs)
        cooldownUntilMs = nowProvider() + cooldown
        cooldown
    }

    fun recordSuccess() = synchronized(lock) {
        consecutiveFailures = 0
        cooldownUntilMs = 0L
    }

    fun reset() = synchronized(lock) {
        consecutiveFailures = 0
        cooldownUntilMs = 0L
    }
}

/**
 * FMSAuthEngine: Hardened Jetpack Credential Manager + Firebase Auth (Google Sign-In exclusively).
 * Enforces offline honesty, rate limiting, ID-token freshness validation, and safe zero-plaintext logging.
 */
object FMSAuthEngine {
    private const val TAG = "FMSAuth"

    private val rateLimiter = FmsAuthRateLimiter()
    private val _sessionState = MutableStateFlow(FmsAuthSessionSnapshot())
    val sessionState: StateFlow<FmsAuthSessionSnapshot> = _sessionState.asStateFlow()

    fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        addAuthStateListener(listener)
        awaitClose { removeAuthStateListener(listener) }
    }

    fun currentCooldownRemainingMs(): Long = rateLimiter.remainingCooldownMs()

    fun updateConnectivityState(
        online: Boolean,
        currentUser: FirebaseUser? = runCatching { Firebase.auth.currentUser }.getOrNull()
    ) {
        val cur = _sessionState.value
        val activeUid = currentUser?.uid ?: cur.uid
        val nextStatus = when {
            cur.status == FmsAuthSessionStatus.DEVICE_REVOKED && activeUid != null -> FmsAuthSessionStatus.DEVICE_REVOKED
            cur.status == FmsAuthSessionStatus.TOKEN_EXPIRED -> FmsAuthSessionStatus.TOKEN_EXPIRED
            activeUid == null -> FmsAuthSessionStatus.UNAUTHENTICATED
            !online -> FmsAuthSessionStatus.CACHED_OFFLINE
            cur.lastVerifiedEpochMs != null -> FmsAuthSessionStatus.VERIFIED_ONLINE
            else -> FmsAuthSessionStatus.VERIFIED_ONLINE
        }
        val nextNote = when (nextStatus) {
            FmsAuthSessionStatus.DEVICE_REVOKED ->
                "Perangkat ini berstatus REVOKED; sinkronisasi cloud diblokir hingga diaktifkan kembali."
            FmsAuthSessionStatus.TOKEN_EXPIRED ->
                "Token sesi akun Google kedaluwarsa; silakan masuk ulang."
            FmsAuthSessionStatus.CACHED_OFFLINE ->
                "Offline — menggunakan sesi lokal tercache (verifikasi server menunggu internet)."
            FmsAuthSessionStatus.VERIFIED_ONLINE ->
                if (cur.lastVerifiedEpochMs != null) "Sesi terverifikasi online." else "Terhubung online."
            FmsAuthSessionStatus.UNAUTHENTICATED ->
                if (online) "Mode lokal aktif (belum masuk akun Google)." else "Offline — mode lokal aktif."
        }
        _sessionState.value = cur.copy(
            status = nextStatus,
            uid = activeUid,
            email = currentUser?.email ?: cur.email,
            displayName = currentUser?.displayName ?: cur.displayName,
            isOnline = online,
            rateLimitRemainingMs = rateLimiter.remainingCooldownMs(),
            statusNote = nextNote
        )
    }

    fun syncFromAuthUser(
        user: FirebaseUser?,
        online: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val cur = _sessionState.value
        if (user == null) {
            val keepExpired = cur.status == FmsAuthSessionStatus.TOKEN_EXPIRED
            _sessionState.value = FmsAuthSessionSnapshot(
                status = if (keepExpired) FmsAuthSessionStatus.TOKEN_EXPIRED else FmsAuthSessionStatus.UNAUTHENTICATED,
                isOnline = online,
                rateLimitRemainingMs = rateLimiter.remainingCooldownMs(),
                statusNote = if (keepExpired) {
                    "Sesi akun Google kedaluwarsa atau dicabut; silakan masuk ulang."
                } else if (online) {
                    "Mode lokal aktif (belum masuk akun Google)."
                } else {
                    "Offline — mode lokal aktif."
                }
            )
            return
        }
        val isRevoked = cur.status == FmsAuthSessionStatus.DEVICE_REVOKED && cur.uid == user.uid
        val status = when {
            isRevoked -> FmsAuthSessionStatus.DEVICE_REVOKED
            online -> FmsAuthSessionStatus.VERIFIED_ONLINE
            else -> FmsAuthSessionStatus.CACHED_OFFLINE
        }
        _sessionState.value = cur.copy(
            status = status,
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            isOnline = online,
            lastVerifiedEpochMs = if (online) (cur.lastVerifiedEpochMs ?: nowMs) else cur.lastVerifiedEpochMs,
            rateLimitRemainingMs = rateLimiter.remainingCooldownMs(),
            statusNote = when (status) {
                FmsAuthSessionStatus.DEVICE_REVOKED ->
                    "Perangkat berstatus REVOKED; sinkronisasi cloud diblokir."
                FmsAuthSessionStatus.CACHED_OFFLINE ->
                    "Offline — sesi tercache lokal."
                else -> "Akun Google terpasang (${safeUidTag(user.uid)})."
            }
        )
    }

    fun setDeviceRevoked(revoked: Boolean) {
        val cur = _sessionState.value
        if (cur.uid == null && !revoked) {
            _sessionState.value = cur.copy(status = FmsAuthSessionStatus.UNAUTHENTICATED)
            return
        }
        val nextStatus = when {
            revoked -> FmsAuthSessionStatus.DEVICE_REVOKED
            cur.uid == null -> FmsAuthSessionStatus.UNAUTHENTICATED
            cur.isOnline -> FmsAuthSessionStatus.VERIFIED_ONLINE
            else -> FmsAuthSessionStatus.CACHED_OFFLINE
        }
        _sessionState.value = cur.copy(
            status = nextStatus,
            statusNote = if (revoked) {
                "Perangkat ini berstatus REVOKED; sinkronisasi cloud diblokir hingga diaktifkan kembali."
            } else if (cur.isOnline) {
                "Perangkat aktif kembali; sesi cloud tersedia."
            } else {
                "Offline — sesi tercache lokal."
            }
        )
    }

    suspend fun refreshActiveSession(
        context: Context,
        forceRefresh: Boolean = true,
        isOnlineOverride: Boolean? = null,
        nowProvider: () -> Long = { System.currentTimeMillis() }
    ): Result<FmsAuthSessionSnapshot> {
        val online = isOnlineOverride ?: FMSConnectivity.isOnline(context)
        val user = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (user == null) {
            syncFromAuthUser(null, online, nowProvider())
            return Result.failure(IllegalStateException("Belum ada akun Google yang terhubung."))
        }
        if (!online) {
            updateConnectivityState(online = false, currentUser = user)
            return Result.failure(
                IllegalStateException("Offline: verifikasi sesi & refresh token membutuhkan koneksi internet.")
            )
        }
        return try {
            val tokenResult = user.getIdToken(forceRefresh).await()
            val hasToken = !tokenResult?.token.isNullOrBlank()
            if (!hasToken) {
                throw IllegalStateException("ID token Firebase kosong.")
            }
            val now = nowProvider()
            val cur = _sessionState.value
            val status = if (cur.status == FmsAuthSessionStatus.DEVICE_REVOKED && cur.uid == user.uid) {
                FmsAuthSessionStatus.DEVICE_REVOKED
            } else {
                FmsAuthSessionStatus.VERIFIED_ONLINE
            }
            val updated = cur.copy(
                status = status,
                uid = user.uid,
                email = user.email,
                displayName = user.displayName,
                isOnline = true,
                lastVerifiedEpochMs = now,
                lastTokenRefreshEpochMs = now,
                rateLimitRemainingMs = 0L,
                statusNote = if (status == FmsAuthSessionStatus.DEVICE_REVOKED) {
                    "Token diperbarui, namun perangkat masih berstatus REVOKED."
                } else {
                    "Sesi & ID token Google berhasil diverifikasi."
                }
            )
            _sessionState.value = updated
            rateLimiter.recordSuccess()
            FmsLogger.info(TAG, "Refreshed Firebase ID token for uid=${safeUidTag(user.uid)} (force=$forceRefresh)")
            Result.success(updated)
        } catch (e: FirebaseAuthInvalidUserException) {
            handleExpiredToken("Akun Google tidak valid atau dinonaktifkan (${e.errorCode}).", online)
            Result.failure(e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            handleExpiredToken("Kredensial sesi Google kedaluwarsa (${e.errorCode}).", online)
            Result.failure(e)
        } catch (e: Exception) {
            FmsLogger.warn(TAG, "Token refresh warning for uid=${safeUidTag(user.uid)}: ${e.javaClass.simpleName}")
            val cur = _sessionState.value
            val fallbackStatus = when {
                cur.status == FmsAuthSessionStatus.DEVICE_REVOKED && cur.uid == user.uid -> FmsAuthSessionStatus.DEVICE_REVOKED
                !online -> FmsAuthSessionStatus.CACHED_OFFLINE
                else -> FmsAuthSessionStatus.CACHED_OFFLINE
            }
            _sessionState.value = cur.copy(
                status = fallbackStatus,
                uid = user.uid,
                email = user.email,
                displayName = user.displayName,
                isOnline = online,
                rateLimitRemainingMs = rateLimiter.remainingCooldownMs(),
                statusNote = if (fallbackStatus == FmsAuthSessionStatus.DEVICE_REVOKED) {
                    "Perangkat berstatus REVOKED; verifikasi token tertunda (${e.javaClass.simpleName})."
                } else {
                    "Verifikasi token tertunda (${e.javaClass.simpleName}); sesi tercache aktif."
                }
            )
            Result.failure(e)
        }
    }

    private fun handleExpiredToken(reason: String, online: Boolean) {
        FmsLogger.warn(TAG, "Session invalidated: $reason")
        runCatching { Firebase.auth.signOut() }
        _sessionState.value = FmsAuthSessionSnapshot(
            status = FmsAuthSessionStatus.TOKEN_EXPIRED,
            uid = null,
            email = null,
            displayName = null,
            isOnline = online,
            rateLimitRemainingMs = rateLimiter.remainingCooldownMs(),
            statusNote = reason
        )
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope,
        isOnlineOverride: Boolean? = null
    ) {
        val online = isOnlineOverride ?: FMSConnectivity.isOnline(context)
        val cachedUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (cachedUser != null) {
            if (!online) {
                updateConnectivityState(online = false, currentUser = cachedUser)
                FmsLogger.info(TAG, "Offline cached session retained for uid=${safeUidTag(cachedUser.uid)}")
                onAuthSuccess()
                return
            }
            scope.launch {
                val res = refreshActiveSession(context, forceRefresh = true, isOnlineOverride = true)
                val userAfter = runCatching { Firebase.auth.currentUser }.getOrNull()
                if (res.isSuccess && userAfter != null) {
                    onAuthSuccess()
                } else if (_sessionState.value.status == FmsAuthSessionStatus.TOKEN_EXPIRED || userAfter == null) {
                    onUnauthenticated()
                } else {
                    // Transient network failure while Firebase cached user remains signed in
                    FmsLogger.warn(TAG, "Auto sign-in retained cached session after transient refresh issue")
                    onAuthSuccess()
                }
            }
            return
        }

        if (!online) {
            syncFromAuthUser(null, online = false)
            onUnauthenticated()
            return
        }

        if (rateLimiter.remainingCooldownMs() > 0L) {
            _sessionState.value = _sessionState.value.copy(
                rateLimitRemainingMs = rateLimiter.remainingCooldownMs()
            )
            onUnauthenticated()
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val authResult = Firebase.auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    rateLimiter.recordSuccess()
                    val now = System.currentTimeMillis()
                    syncFromAuthUser(authResult.user ?: Firebase.auth.currentUser, online = true, nowMs = now)
                    _sessionState.value = _sessionState.value.copy(
                        lastVerifiedEpochMs = now,
                        lastTokenRefreshEpochMs = now,
                        rateLimitRemainingMs = 0L
                    )
                    onAuthSuccess()
                } else {
                    syncFromAuthUser(null, online = true)
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                syncFromAuthUser(runCatching { Firebase.auth.currentUser }.getOrNull(), online = online)
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {},
        isOnlineOverride: Boolean? = null
    ) {
        val online = isOnlineOverride ?: FMSConnectivity.isOnline(context)
        if (!online) {
            updateConnectivityState(online = false)
            FmsLogger.warn(TAG, "Interactive Google Sign-In blocked while offline")
            onAuthError(
                "Koneksi internet offline. Masuk dengan Google memerlukan internet aktif; fitur kloning lokal tetap dapat dipakai."
            )
            return
        }

        val remainingCooldown = rateLimiter.checkAllowed()
        if (remainingCooldown > 0L) {
            val waitSeconds = ((remainingCooldown + 999L) / 1000L).coerceAtLeast(1L)
            _sessionState.value = _sessionState.value.copy(rateLimitRemainingMs = remainingCooldown)
            FmsLogger.warn(TAG, "Google Sign-In throttled for ${waitSeconds}s")
            onAuthError("Terlalu banyak percobaan gagal. Coba lagi dalam $waitSeconds detik.")
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Konfigurasi Google Sign-In belum lengkap (default_web_client_id tidak ditemukan).")
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val activity = context as? Activity
                val cred = if (activity != null) {
                    credentialManager.getCredential(activity, request).credential
                } else {
                    credentialManager.getCredential(context, request).credential
                }
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val authResult = Firebase.auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    rateLimiter.recordSuccess()
                    val now = System.currentTimeMillis()
                    val signedInUser = authResult.user ?: Firebase.auth.currentUser
                    syncFromAuthUser(signedInUser, online = true, nowMs = now)
                    _sessionState.value = _sessionState.value.copy(
                        lastVerifiedEpochMs = now,
                        lastTokenRefreshEpochMs = now,
                        rateLimitRemainingMs = 0L,
                        statusNote = "Masuk Google & verifikasi sesi berhasil."
                    )
                    FmsLogger.info(TAG, "Google Sign-In succeeded for uid=${safeUidTag(signedInUser?.uid)}")
                    onAuthSuccess()
                } else {
                    val nextWait = rateLimiter.recordFailure()
                    _sessionState.value = _sessionState.value.copy(rateLimitRemainingMs = nextWait)
                    onAuthError("Tipe kredensial tidak didukung.")
                }
            } catch (e: GetCredentialCancellationException) {
                FmsLogger.warn(TAG, "Google Sign-In cancelled or dismissed")
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                FmsLogger.warn(TAG, "No Google credential available")
                val nextWait = rateLimiter.recordFailure()
                _sessionState.value = _sessionState.value.copy(rateLimitRemainingMs = nextWait)
                onAuthError("Tidak ada akun Google aktif di perangkat atau konfigurasi OAuth SHA-1 belum cocok.")
            } catch (e: GetCredentialException) {
                FmsLogger.error(TAG, "Credential Manager error: ${e.type}")
                val nextWait = rateLimiter.recordFailure()
                _sessionState.value = _sessionState.value.copy(rateLimitRemainingMs = nextWait)
                onAuthError("Gagal Credential Manager (${e.type}): ${e.localizedMessage ?: "periksa Play Services & SHA-1"}")
            } catch (e: Exception) {
                FmsLogger.error(TAG, "Google Sign-In failed: ${e.javaClass.simpleName}")
                val nextWait = rateLimiter.recordFailure()
                _sessionState.value = _sessionState.value.copy(rateLimitRemainingMs = nextWait)
                onAuthError(e.localizedMessage ?: "Gagal masuk dengan Google")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    FmsLogger.warn(TAG, "Failed to clear credential state: ${e.javaClass.simpleName}")
                } finally {
                    runCatching { Firebase.auth.signOut() }
                    rateLimiter.reset()
                    val currentOnline = _sessionState.value.isOnline
                    _sessionState.value = FmsAuthSessionSnapshot(
                        status = FmsAuthSessionStatus.UNAUTHENTICATED,
                        isOnline = currentOnline,
                        statusNote = if (currentOnline) "Berhasil keluar akun Google." else "Offline — keluar akun."
                    )
                    FmsLogger.info(TAG, "Signed out cleanly and cleared credential session")
                    onSignOutComplete()
                }
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting(online: Boolean = true) {
        rateLimiter.reset()
        _sessionState.value = FmsAuthSessionSnapshot(isOnline = online)
    }

    private fun safeUidTag(uid: String?): String =
        if (uid.isNullOrBlank()) "none" else "${uid.take(6)}…"
}

enum class FmsSyncState { LOCAL_ONLY, SYNCING, SYNCED, OFFLINE, ERROR }
