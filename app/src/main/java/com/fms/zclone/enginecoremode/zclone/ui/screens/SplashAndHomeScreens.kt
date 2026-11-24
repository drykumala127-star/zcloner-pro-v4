package com.fms.zclone.enginecoremode.zclone.ui.screens

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.fms.zclone.enginecoremode.R
import com.fms.zclone.enginecoremode.zclone.ui.ZCloneUiState
import com.fms.cloud.FMSAuthEngine
import com.fms.cloud.FmsAuthSessionSnapshot
import com.fms.cloud.FmsAuthSessionStatus
import com.fms.cloud.FmsSyncState
import com.fms.task.FmsJobState
import com.fms.ui.FmsGlassCard
import com.fms.ui.FmsStatusBadge
import com.fms.ui.FmsThemePreset
import com.fms.ui.fmsIconContainerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

enum class ZCloneRoute {
    SPLASH,
    HOME,
    LIBRARY,
    TOOLS,
    SETTINGS,
    CLONE_APPS,
    APP_CONFIG,
    THEME_STUDIO,
    DEVICE_MANAGER,
    SECURITY_SCANNER,
    FMS_HUB
}

fun tr(lang: String, idText: String, enText: String): String = if (lang == "en") enText else idText

data class DhedryStageInfo(
    val stageNumber: Int,
    val code: String,
    val labelId: String,
    val labelEn: String
)

/**
 * Deterministic 10-stage timeline (6.5s) synchronized with offline R.raw.dhedry_zclone_boot narration + SFX.
 */
object DhedryLoadingTimeline {
    const val DURATION_MS: Long = 6500L
    const val STAGE_COUNT: Int = 10
    const val AUDIO_ALWAYS_ENABLED: Boolean = true
    const val HAS_MUTE_CONTROL: Boolean = false

    fun stageForElapsedMs(elapsedMs: Long): DhedryStageInfo {
        val sec = (elapsedMs.coerceIn(0L, DURATION_MS) / 1000f)
        return when {
            sec < 0.65f -> DhedryStageInfo(1, "BOOT_DARK", "01 // Robot Dhedry muncul dari gelap", "01 // Dhedry Robot emerges from dark")
            sec < 1.35f -> DhedryStageInfo(2, "SCAN_APK", "02 // Memindai paket APK", "02 // Scanning APK container")
            sec < 2.15f -> DhedryStageInfo(3, "DISASSEMBLE", "03 // Mengurai APK menjadi modul digital", "03 // Disassembling into digital modules")
            sec < 3.05f -> DhedryStageInfo(4, "INSPECT", "04 // Memproses & memeriksa komponen", "04 // Inspecting components & integrity")
            sec < 3.95f -> DhedryStageInfo(5, "REASSEMBLE", "05 // Merakit kembali komponen APK", "05 // Reassembling clone modules")
            sec < 4.55f -> DhedryStageInfo(6, "CORE_IGNITION", "06 // Energy Core menyala penuh", "06 // Energy core ignited")
            sec < 5.05f -> DhedryStageInfo(7, "LOGO_REVEAL", "07 // Memuat identitas ZClone Engine", "07 // Revealing ZClone Engine logo")
            sec < 5.55f -> DhedryStageInfo(8, "WELCOME_TEXT", "08 // Welcome to ZClone Engine", "08 // Welcome to ZClone Engine")
            sec < 6.10f -> DhedryStageInfo(9, "DHEDRY_BRAND", "09 // Powered by Dhedry Library", "09 // Powered by Dhedry Library")
            else -> DhedryStageInfo(10, "LAUNCH_TARGET", "10 // Meluncurkan aplikasi...", "10 // Launching application...")
        }
    }
}

private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

@Composable
fun SplashScreen(
    theme: FmsThemePreset,
    languageCode: String,
    onContinueFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnFinished by rememberUpdatedState(onContinueFinished)
    var savedProgress by rememberSaveable { mutableFloatStateOf(0f) }
    var hasFinished by rememberSaveable { mutableStateOf(false) }
    var isLifecyclePaused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(savedProgress) }
    val activePlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    val playerPrepared = remember { mutableStateOf(false) }

    val splashBgBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF03050C), Color(0xFF070C1E), Color(0xFF0A1128))
        )
    }
    val progressBarBrush = remember {
        Brush.horizontalGradient(listOf(Color(0xFF00D2FF), Color(0xFF8B5CF6)))
    }

    // Always-on offline local MP3 playback synced with Android lifecycle (no mute control)
    DisposableEffect(Unit) {
        val player = runCatching {
            val afd = context.resources.openRawResourceFd(R.raw.dhedry_zclone_boot)
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                try {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } finally {
                    runCatching { afd.close() }
                }
                setVolume(1.0f, 1.0f)
                setOnPreparedListener { mp ->
                    playerPrepared.value = true
                    val targetMs = (savedProgress * DhedryLoadingTimeline.DURATION_MS).toInt().coerceAtLeast(0)
                    if (targetMs > 0) {
                        runCatching { mp.seekTo(targetMs) }
                    }
                    if (!isLifecyclePaused && !hasFinished) {
                        runCatching { mp.start() }
                    }
                }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        }.getOrNull()
        activePlayer.value = player

        onDispose {
            playerPrepared.value = false
            activePlayer.value = null
            runCatching {
                if (player != null) {
                    if (player.isPlaying) player.stop()
                    player.release()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    isLifecyclePaused = true
                    savedProgress = progress.value
                    activePlayer.value?.let { mp ->
                        runCatching { if (mp.isPlaying) mp.pause() }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    isLifecyclePaused = true
                    activePlayer.value?.let { mp ->
                        runCatching { if (mp.isPlaying) mp.pause() }
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    isLifecyclePaused = false
                    if (!hasFinished && playerPrepared.value) {
                        activePlayer.value?.let { mp ->
                            runCatching {
                                val targetMs = (savedProgress * DhedryLoadingTimeline.DURATION_MS).toInt().coerceAtLeast(0)
                                mp.seekTo(targetMs)
                                if (!mp.isPlaying) mp.start()
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isLifecyclePaused, hasFinished) {
        if (!isLifecyclePaused && !hasFinished) {
            val startP = savedProgress.coerceIn(0f, 1f)
            progress.snapTo(startP)
            val remainingMs = ((1f - startP) * DhedryLoadingTimeline.DURATION_MS).toInt().coerceAtLeast(1)
            try {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = remainingMs,
                        easing = LinearEasing
                    )
                ) {
                    savedProgress = value
                }
                if (!isLifecyclePaused && !hasFinished) {
                    hasFinished = true
                    latestOnFinished()
                }
            } catch (_: CancellationException) {
                savedProgress = progress.value
            }
        }
    }

    val p = progress.value
    val sec = p * (DhedryLoadingTimeline.DURATION_MS / 1000f)
    val currentStage = remember(sec) {
        DhedryLoadingTimeline.stageForElapsedMs((sec * 1000f).toLong())
    }

    val robotReveal = clamp01(sec / 0.65f)
    val scanProgress = clamp01((sec - 0.65f) / 0.70f)
    val disasmProgress = clamp01((sec - 1.35f) / 0.80f)
    val processPulse = if (sec in 2.15f..3.10f) {
        0.5f + 0.5f * sin((sec - 2.15f) * 14f)
    } else {
        0f
    }
    val activeInspectIdx = if (sec in 2.15f..3.05f) {
        (((sec - 2.15f) / 0.225f).toInt()).coerceIn(0, 3)
    } else {
        -1
    }
    val reasmProgress = clamp01((sec - 3.05f) / 0.90f)
    val coreGlow = clamp01((sec - 3.90f) / 0.65f)
    val logoReveal = clamp01((sec - 4.55f) / 0.50f)
    val welcomeReveal = clamp01((sec - 5.05f) / 0.50f)
    val brandReveal = clamp01((sec - 5.55f) / 0.50f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashBgBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("dhedry_splash_root")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cyan = Color(0xFF00D2FF)
            val violet = Color(0xFF8B5CF6)
            val emerald = Color(0xFF10B981)
            val gold = Color(0xFFF59E0B)
            val darkSurface = Color(0xFF11182E)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val cx = w * 0.5f
                val cy = h * 0.42f
                val u = minOf(w, h) * 0.0027f

                // Stage 1+: subtle technical grid emerging from darkness
                val gridStep = minOf(w, h) * 0.085f
                val gridAlpha = 0.14f * robotReveal
                var gx = 0f
                while (gx < w) {
                    drawLine(
                        color = cyan.copy(alpha = gridAlpha),
                        start = Offset(gx, 0f),
                        end = Offset(gx, h),
                        strokeWidth = 1.2f
                    )
                    gx += gridStep
                }
                var gy = 0f
                while (gy < h) {
                    drawLine(
                        color = cyan.copy(alpha = gridAlpha),
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 1.2f
                    )
                    gy += gridStep
                }

                // Ambient energy halo
                val haloR = (135f + 78f * coreGlow) * u
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            cyan.copy(alpha = (0.24f + 0.45f * coreGlow) * robotReveal),
                            violet.copy(alpha = (0.14f + 0.28f * coreGlow) * robotReveal),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = haloR.coerceAtLeast(1f)
                    ),
                    radius = haloR,
                    center = Offset(cx, cy)
                )

                // Android Robot silhouette (stages 1..6)
                val robotAlpha = (robotReveal * (1f - 0.35f * logoReveal)).coerceIn(0f, 1f)
                val ru = u * (0.84f + 0.16f * robotReveal)
                val robotCy = cy - 26f * ru
                val headR = 36f * ru
                val headCy = robotCy - 28f * ru

                if (robotAlpha > 0.02f) {
                    // Robot head dome
                    drawArc(
                        color = darkSurface.copy(alpha = robotAlpha),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(cx - headR, headCy - headR),
                        size = Size(headR * 2f, headR * 2f)
                    )
                    drawArc(
                        color = cyan.copy(alpha = robotAlpha),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - headR, headCy - headR),
                        size = Size(headR * 2f, headR * 2f),
                        style = Stroke(width = 2.8f * ru, cap = StrokeCap.Round)
                    )
                    drawLine(
                        color = cyan.copy(alpha = robotAlpha),
                        start = Offset(cx - headR, headCy),
                        end = Offset(cx + headR, headCy),
                        strokeWidth = 2.6f * ru,
                        cap = StrokeCap.Round
                    )

                    // Antennas
                    drawLine(
                        color = cyan.copy(alpha = robotAlpha),
                        start = Offset(cx - 16f * ru, headCy - 30f * ru),
                        end = Offset(cx - 26f * ru, headCy - 46f * ru),
                        strokeWidth = 2.6f * ru,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = cyan.copy(alpha = robotAlpha),
                        start = Offset(cx + 16f * ru, headCy - 30f * ru),
                        end = Offset(cx + 26f * ru, headCy - 46f * ru),
                        strokeWidth = 2.6f * ru,
                        cap = StrokeCap.Round
                    )

                    // Glowing robot eyes
                    val eyeColor = if (coreGlow > 0.3f) emerald else cyan
                    val eyeY = headCy - 14f * ru
                    val eyeR = (4.2f + processPulse * 1.2f) * ru
                    drawCircle(eyeColor.copy(alpha = robotAlpha), eyeR, Offset(cx - 14f * ru, eyeY))
                    drawCircle(eyeColor.copy(alpha = robotAlpha), eyeR, Offset(cx + 14f * ru, eyeY))

                    // Torso body
                    val bodyTop = headCy + 7f * ru
                    val bodyH = 62f * ru
                    val bodyHalf = 38f * ru
                    drawRoundRect(
                        color = darkSurface.copy(alpha = robotAlpha),
                        topLeft = Offset(cx - bodyHalf, bodyTop),
                        size = Size(bodyHalf * 2f, bodyH),
                        cornerRadius = CornerRadius(12f * ru, 12f * ru)
                    )
                    drawRoundRect(
                        color = violet.copy(alpha = robotAlpha),
                        topLeft = Offset(cx - bodyHalf, bodyTop),
                        size = Size(bodyHalf * 2f, bodyH),
                        cornerRadius = CornerRadius(12f * ru, 12f * ru),
                        style = Stroke(width = 2.6f * ru, join = StrokeJoin.Round)
                    )

                    // Articulated arms during scan/inspect/assembly
                    val armSwing = sin(sec * 5.2f) * 5.5f * ru * (1f - coreGlow)
                    drawRoundRect(
                        color = darkSurface.copy(alpha = robotAlpha),
                        topLeft = Offset(cx - bodyHalf - 16f * ru, bodyTop + 4f * ru + armSwing),
                        size = Size(12f * ru, 42f * ru),
                        cornerRadius = CornerRadius(6f * ru, 6f * ru)
                    )
                    drawRoundRect(
                        color = violet.copy(alpha = robotAlpha),
                        topLeft = Offset(cx - bodyHalf - 16f * ru, bodyTop + 4f * ru + armSwing),
                        size = Size(12f * ru, 42f * ru),
                        cornerRadius = CornerRadius(6f * ru, 6f * ru),
                        style = Stroke(width = 2.2f * ru)
                    )
                    drawRoundRect(
                        color = darkSurface.copy(alpha = robotAlpha),
                        topLeft = Offset(cx + bodyHalf + 4f * ru, bodyTop + 4f * ru - armSwing),
                        size = Size(12f * ru, 42f * ru),
                        cornerRadius = CornerRadius(6f * ru, 6f * ru)
                    )
                    drawRoundRect(
                        color = violet.copy(alpha = robotAlpha),
                        topLeft = Offset(cx + bodyHalf + 4f * ru, bodyTop + 4f * ru - armSwing),
                        size = Size(12f * ru, 42f * ru),
                        cornerRadius = CornerRadius(6f * ru, 6f * ru),
                        style = Stroke(width = 2.2f * ru)
                    )

                    // Energy Core inside robot chest (ignites Stage 6)
                    val coreCenter = Offset(cx, bodyTop + 30f * ru)
                    val coreRadius = (9f + 6.5f * coreGlow + 2.2f * processPulse) * ru
                    drawCircle(
                        color = cyan.copy(alpha = robotAlpha * (0.35f + 0.65f * coreGlow)),
                        radius = coreRadius * 1.45f,
                        center = coreCenter
                    )
                    drawCircle(
                        color = emerald.copy(alpha = robotAlpha),
                        radius = coreRadius * 0.72f,
                        center = coreCenter
                    )
                }

                // Stage 6: Ignition ring shockwave
                if (coreGlow > 0.01f) {
                    drawCircle(
                        color = emerald.copy(alpha = 0.85f * coreGlow),
                        radius = (90f + 24f * coreGlow) * u,
                        center = Offset(cx, cy - 26f * u),
                        style = Stroke(width = 3f * u)
                    )
                }

                // Stage 2..5: APK Digital Modules (AXML, DEX, RES, SIGN)
                val moduleAlpha = (robotReveal * (1f - 0.9f * logoReveal)).coerceIn(0f, 1f)
                val cubeCy = cy + 92f * u
                val spread = disasmProgress * (1f - reasmProgress)
                if (moduleAlpha > 0.02f) {
                    val modColors = listOf(cyan, violet, gold, emerald)
                    val dx = floatArrayOf(-1f, 1f, -1f, 1f)
                    val dy = floatArrayOf(-0.65f, -0.65f, 0.65f, 0.65f)
                    val boxW = 36f * u
                    val boxH = 22f * u

                    for (i in 0 until 4) {
                        val mx = cx + dx[i] * (21f + 58f * spread) * u
                        val my = cubeCy + dy[i] * (14f + 26f * spread) * u
                        val isInspected = activeInspectIdx == i
                        val modColor = modColors[i]
                        drawRoundRect(
                            color = darkSurface.copy(alpha = moduleAlpha),
                            topLeft = Offset(mx - boxW * 0.5f, my - boxH * 0.5f),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(6f * u, 6f * u)
                        )
                        drawRoundRect(
                            color = modColor.copy(alpha = moduleAlpha),
                            topLeft = Offset(mx - boxW * 0.5f, my - boxH * 0.5f),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(6f * u, 6f * u),
                            style = Stroke(
                                width = (if (isInspected) 3.4f else (1.8f + processPulse * 0.7f + coreGlow)) * u
                            )
                        )
                    }
                }

                // Stage 2: Scanning laser beam sweeping APK modules
                if (sec in 0.65f..1.42f) {
                    val beamY = cubeCy + (scanProgress - 0.5f) * 62f * u
                    drawRect(
                        color = cyan.copy(alpha = 0.18f),
                        topLeft = Offset(cx - 105f * u, beamY - 12f * u),
                        size = Size(210f * u, 24f * u)
                    )
                    drawLine(
                        color = cyan.copy(alpha = 0.9f),
                        start = Offset(cx - 112f * u, beamY),
                        end = Offset(cx + 112f * u, beamY),
                        strokeWidth = 3.5f * u,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Stage 7-10 Overlay: Logo reveal + synchronized typography + stage readout (no mute toggle)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (logoReveal > 0.02f) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_zclone_brand_mark),
                        contentDescription = "ZClone Engine Logo",
                        modifier = Modifier
                            .size(84.dp)
                            .scale(0.75f + 0.25f * logoReveal)
                            .alpha(logoReveal)
                            .clip(RoundedCornerShape(22.dp))
                            .border(2.dp, Color(0xFF00D2FF).copy(alpha = 0.75f), RoundedCornerShape(22.dp))
                            .testTag("splash_zclone_logo"),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(14.dp))
                }

                // Synchronized stage indicator
                Text(
                    text = tr(languageCode, currentStage.labelId, currentStage.labelEn),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF00D2FF).copy(alpha = (0.35f + 0.65f * robotReveal).coerceIn(0f, 1f)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("splash_stage_label")
                )

                Spacer(Modifier.height(10.dp))

                // Progress Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF11182E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(p.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(progressBarBrush)
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Stage 8: Welcome to ZClone Engine
                Text(
                    text = "Welcome to ZClone Engine",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .alpha(welcomeReveal)
                        .testTag("splash_welcome_text")
                )

                Spacer(Modifier.height(6.dp))

                // Stage 9: Powered by Dhedry Library
                Text(
                    text = "Powered by Dhedry Library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF10B981),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .alpha(brandReveal)
                        .testTag("splash_dhedry_brand")
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha((0.4f + 0.6f * brandReveal).coerceIn(0f, 1f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = tr(
                            languageCode,
                            "Dhedry Offline Audio Engine · Aktif Selalu",
                            "Dhedry Offline Audio Engine · Always Active"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "ZCloner PRO · Copyright FMS Clone Engine V1",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha((0.4f + 0.6f * brandReveal).coerceIn(0f, 1f))
                )
            }
        }
    }
}

@Composable
fun HomeDashboardScreen(
    state: ZCloneUiState,
    theme: FmsThemePreset,
    onNavigate: (ZCloneRoute) -> Unit,
    onToggleAutoTheme: () -> Unit,
    onOpenAuthDialog: () -> Unit,
    onOpenTierDialog: () -> Unit,
    onCancelActiveBuild: () -> Unit = {},
    onCancelAllBatchJobs: () -> Unit = {}
) {
    val lang = state.languageCode
    val activeBatchJobs = remember(state.backgroundJobs) {
        state.backgroundJobs.filter { it.state == FmsJobState.RUNNING || it.state == FmsJobState.QUEUED }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar matching mockup
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onNavigate(ZCloneRoute.FMS_HUB) },
                        modifier = Modifier.testTag("menu_hub_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "FMSCloneEngine Hub",
                            tint = theme.onBackground
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "ZCloner PRO",
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = theme.accentGold.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, theme.accentGold.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenTierDialog() }
                            .testTag("tier_badge_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = null,
                                tint = theme.accentGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = state.userTier.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = theme.accentGold
                            )
                        }
                    }

                    IconButton(
                        onClick = { onNavigate(ZCloneRoute.SETTINGS) },
                        modifier = Modifier.testTag("top_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = tr(lang, "Pengaturan", "Settings"),
                            tint = theme.onBackground
                        )
                    }
                }
            }
        }

        // Cloud Auth / Local-First Status Pill
        item {
            FmsGlassCard(
                theme = theme,
                cornerRadius = 16.dp,
                onClick = onOpenAuthDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_status_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val isSignedIn = state.userId != null
                        val isRevoked = state.isLocalDeviceRevoked || state.authSession.status == FmsAuthSessionStatus.DEVICE_REVOKED
                        Icon(
                            imageVector = if (isSignedIn && !isRevoked && state.isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = when {
                                isRevoked || state.syncState == FmsSyncState.ERROR -> Color(0xFFEF4444)
                                isSignedIn && !state.isOnline -> theme.accentGold
                                isSignedIn -> theme.accentSuccess
                                else -> theme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when {
                                    isRevoked -> tr(
                                        lang,
                                        "Perangkat REVOKED · Sync Cloud Diblokir",
                                        "Device REVOKED · Cloud Sync Blocked"
                                    )
                                    isSignedIn && !state.isOnline -> tr(
                                        lang,
                                        "Offline · Sesi Tercache (${state.userDisplayName ?: "Google"})",
                                        "Offline · Cached Session (${state.userDisplayName ?: "Google"})"
                                    )
                                    isSignedIn -> "Cloud Sync: ${state.userDisplayName ?: "Google"}"
                                    else -> tr(
                                        lang,
                                        "Mode Lokal Aktif · Masuk Google untuk Cloud Sync",
                                        "Local Mode Active · Sign in for Cloud Sync"
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isRevoked -> tr(
                                        lang,
                                        "Aktifkan kembali perangkat di Device Manager atau keluar akun",
                                        "Reactivate device in Device Manager or sign out"
                                    )
                                    isSignedIn && !state.isOnline -> tr(
                                        lang,
                                        "Perubahan tersimpan lokal & menunggu koneksi internet",
                                        "Changes stay saved locally until online"
                                    )
                                    isSignedIn -> "${state.syncState.name} · ${state.cloudDevices.size} perangkat"
                                    else -> tr(
                                        lang,
                                        "Semua inspeksi & repackage APK tetap jalan offline",
                                        "All APK inspection & repackaging works offline"
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.onSurfaceMuted
                            )
                        }
                    }
                    val badgeInfo = when {
                        state.userId == null && state.authSession.status == FmsAuthSessionStatus.TOKEN_EXPIRED ->
                            "TOKEN EXP" to Color(0xFFEF4444)
                        state.userId == null ->
                            "LOCAL" to theme.primary
                        state.isLocalDeviceRevoked || state.authSession.status == FmsAuthSessionStatus.DEVICE_REVOKED ->
                            "REVOKED" to Color(0xFFEF4444)
                        !state.isOnline || state.syncState == FmsSyncState.OFFLINE ->
                            "OFFLINE" to theme.accentGold
                        state.syncState == FmsSyncState.ERROR ->
                            "SYNC ERR" to Color(0xFFEF4444)
                        state.syncState == FmsSyncState.SYNCING ->
                            "SYNCING" to theme.primary
                        else -> "SYNCED" to theme.accentSuccess
                    }
                    FmsStatusBadge(
                        label = badgeInfo.first,
                        color = badgeInfo.second
                    )
                }
            }
        }

        // Active Single / Batch Job Summary Card (only rendered while builds are running or queued)
        if (state.isBuildingClone || activeBatchJobs.isNotEmpty()) {
            item {
                FmsGlassCard(
                    theme = theme,
                    cornerRadius = 18.dp,
                    borderColor = theme.primary.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_jobs_summary_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr(lang, "Pekerjaan Aktif", "Active Builds"),
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.primary
                            )
                            TextButton(
                                onClick = {
                                    if (state.isBuildingClone) onNavigate(ZCloneRoute.APP_CONFIG)
                                    else onNavigate(ZCloneRoute.TOOLS)
                                },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("home_view_jobs_button")
                            ) {
                                Text(tr(lang, "Detail", "Details"), color = theme.primary)
                            }
                        }

                        if (state.isBuildingClone) {
                            val pct = (state.buildProgress * 100).toInt().coerceIn(0, 100)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Build ($pct%) · ${state.buildProgressDetail}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = theme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { state.buildProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                                        color = theme.primary,
                                        trackColor = theme.surfaceElevated
                                    )
                                }
                                IconButton(
                                    onClick = onCancelActiveBuild,
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("home_cancel_single_build_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = tr(lang, "Batalkan build aktif", "Cancel active build"),
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }

                        if (activeBatchJobs.isNotEmpty()) {
                            val headJob = activeBatchJobs.first()
                            val headPct = (headJob.progress * 100).toInt().coerceIn(0, 100)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${activeBatchJobs.size} Batch (${headPct}%) · ${headJob.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = theme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { headJob.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                                        color = theme.secondary,
                                        trackColor = theme.surfaceElevated
                                    )
                                }
                                IconButton(
                                    onClick = onCancelAllBatchJobs,
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("home_cancel_batch_jobs_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = tr(lang, "Batalkan antrean batch", "Cancel batch queue"),
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hero Banner Card matching mockup
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1D4ED8), Color(0xFF5B21B6))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .clickable { onNavigate(ZCloneRoute.CLONE_APPS) }
                    .padding(18.dp)
                    .testTag("hero_start_cloning_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.35f)) {
                        Text(
                            text = tr(lang, "Buat Klon Sempurnamu", "Create Your Perfect Clone"),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = tr(
                                lang,
                                "Kloning APK, ubah manifest & ikon, lalu kelola secara aman.",
                                "Clone APKs, customize manifest & icons, and manage safely."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { onNavigate(ZCloneRoute.CLONE_APPS) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF)),
                            modifier = Modifier.testTag("start_cloning_button")
                        ) {
                            Text(
                                text = tr(lang, "Mulai Kloning", "Start Cloning"),
                                color = Color(0xFF051324),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF051324),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))
                    Image(
                        painter = painterResource(id = R.drawable.img_zclone_hero_layers),
                        contentDescription = "Ilustrasi kloning aplikasi",
                        modifier = Modifier
                            .weight(0.9f)
                            .height(126.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // 2x2 Main Grid matching mockup
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardGridCard(
                        title = tr(lang, "Kloning Saya", "My Clones"),
                        subtitle = "${state.localClones.size} ${tr(lang, "Aplikasi", "Apps")}",
                        icon = Icons.Rounded.Apps,
                        accent = Color(0xFF38BDF8),
                        theme = theme,
                        onClick = { onNavigate(ZCloneRoute.LIBRARY) },
                        modifier = Modifier.weight(1f).testTag("grid_my_clones")
                    )
                    DashboardGridCard(
                        title = "Theme Studio",
                        subtitle = tr(lang, "Buat Tema", "Create Theme"),
                        icon = Icons.Rounded.Palette,
                        accent = Color(0xFFA855F7),
                        theme = theme,
                        onClick = { onNavigate(ZCloneRoute.THEME_STUDIO) },
                        modifier = Modifier.weight(1f).testTag("grid_theme_studio")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val activeDevCount = state.cloudDevices.count { it.second.status == "ACTIVE" }.coerceAtLeast(1)
                    DashboardGridCard(
                        title = "Device Manager",
                        subtitle = "$activeDevCount ${tr(lang, "Perangkat", "Devices")}",
                        icon = Icons.Rounded.Devices,
                        accent = Color(0xFF10B981),
                        theme = theme,
                        onClick = { onNavigate(ZCloneRoute.DEVICE_MANAGER) },
                        modifier = Modifier.weight(1f).testTag("grid_device_manager")
                    )
                    DashboardGridCard(
                        title = "Security Scanner",
                        subtitle = tr(lang, "Pindai Sekarang", "Scan Now"),
                        icon = Icons.Rounded.Shield,
                        accent = Color(0xFFF59E0B),
                        theme = theme,
                        onClick = { onNavigate(ZCloneRoute.SECURITY_SCANNER) },
                        modifier = Modifier.weight(1f).testTag("grid_security_scanner")
                    )
                }
            }
        }

        // Quick Tools strip matching mockup
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "Alat Cepat", "Quick Tools"),
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.onBackground
                    )
                    Text(
                        text = tr(lang, "Lihat Semua >", "See All >"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.primary,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onNavigate(ZCloneRoute.TOOLS) }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        QuickToolChip(
                            label = "Clone Apps",
                            icon = Icons.Rounded.ContentCopy,
                            tint = Color(0xFF38BDF8),
                            theme = theme,
                            onClick = { onNavigate(ZCloneRoute.CLONE_APPS) }
                        )
                    }
                    item {
                        QuickToolChip(
                            label = "Auto Theme",
                            icon = Icons.Rounded.AutoAwesome,
                            tint = Color(0xFFA855F7),
                            theme = theme,
                            onClick = onToggleAutoTheme
                        )
                    }
                    item {
                        QuickToolChip(
                            label = "Batch Clone",
                            icon = Icons.Rounded.Layers,
                            tint = Color(0xFF818CF8),
                            theme = theme,
                            onClick = { onNavigate(ZCloneRoute.CLONE_APPS) }
                        )
                    }
                    item {
                        QuickToolChip(
                            label = "Backup & Restore",
                            icon = Icons.Rounded.Backup,
                            tint = Color(0xFF10B981),
                            theme = theme,
                            onClick = { onNavigate(ZCloneRoute.SETTINGS) }
                        )
                    }
                    item {
                        QuickToolChip(
                            label = "FMS Hub",
                            icon = Icons.Rounded.MoreHoriz,
                            tint = Color(0xFFF59E0B),
                            theme = theme,
                            onClick = { onNavigate(ZCloneRoute.FMS_HUB) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "ZCloner PRO · FMS Engine Core V1 · Copyright FMS Clone Engine V1",
                style = MaterialTheme.typography.labelSmall,
                color = theme.onSurfaceMuted.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun DashboardGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    theme: FmsThemePreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FmsGlassCard(
        theme = theme,
        cornerRadius = 20.dp,
        borderColor = accent.copy(alpha = 0.35f),
        onClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(fmsIconContainerShape(theme.iconStyle))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickToolChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    theme: FmsThemePreset,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f))
                .border(1.dp, tint.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CloudAuthDialogCard(
    theme: FmsThemePreset,
    languageCode: String,
    isSignedIn: Boolean,
    userEmail: String?,
    authSession: FmsAuthSessionSnapshot = FmsAuthSessionSnapshot(),
    isOnline: Boolean = true,
    isLocalDeviceRevoked: Boolean = false,
    onRefreshSession: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember(isSignedIn, userEmail) { mutableStateOf<String?>(null) }

    val sessionBadge: Pair<String, Color> = when {
        isLocalDeviceRevoked || authSession.status == FmsAuthSessionStatus.DEVICE_REVOKED ->
            "REVOKED" to Color(0xFFEF4444)
        authSession.status == FmsAuthSessionStatus.TOKEN_EXPIRED ->
            "TOKEN EXPIRED" to Color(0xFFEF4444)
        isSignedIn && !isOnline ->
            "CACHED OFFLINE" to theme.accentGold
        isSignedIn ->
            "VERIFIED ONLINE" to theme.accentSuccess
        !isOnline ->
            "OFFLINE" to theme.accentGold
        else -> "LOCAL ONLY" to theme.primary
    }

    val lastVerifiedLabel = remember(authSession.lastVerifiedEpochMs) {
        authSession.lastVerifiedEpochMs?.let { epoch ->
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epoch))
        }
    }

    FmsGlassCard(theme = theme, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = theme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = tr(languageCode, "Akun Google & Cloud Sync", "Google Account & Cloud Sync"),
                        style = MaterialTheme.typography.titleLarge,
                        color = theme.onBackground
                    )
                }
                FmsStatusBadge(
                    label = sessionBadge.first,
                    color = sessionBadge.second
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isSignedIn) {
                    tr(
                        languageCode,
                        "Terhubung sebagai ${userEmail ?: "Pengguna Google"}. Resep klon, tema, laporan scan, dan Device Manager dikelola per akun.",
                        "Connected as ${userEmail ?: "Google User"}. Clone recipes, themes, scan reports, and Device Manager sync per account."
                    )
                } else {
                    tr(
                        languageCode,
                        "Masuk dengan Google melalui Credential Manager untuk menyinkronkan konfigurasi klon & perangkat ke Cloud Firestore. Mode lokal tetap berfungsi penuh tanpa login.",
                        "Sign in with Google via Credential Manager to sync clone configs & devices to Cloud Firestore. Local mode works offline anytime."
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onSurfaceMuted
            )

            if (lastVerifiedLabel != null && isSignedIn) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr(
                        languageCode,
                        "Verifikasi token terakhir: $lastVerifiedLabel",
                        "Last token check: $lastVerifiedLabel"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.primary,
                    modifier = Modifier.testTag("auth_last_verified_label")
                )
            }

            if (!isOnline) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.accentGold.copy(alpha = 0.14f))
                        .border(1.dp, theme.accentGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .testTag("auth_offline_notice")
                ) {
                    Text(
                        text = if (isSignedIn) {
                            tr(
                                languageCode,
                                "Perangkat sedang offline. Anda melihat sesi lokal tercache; refresh token dan sinkronisasi Firestore menunggu koneksi internet.",
                                "Device is offline. Cached local session active; token refresh and Firestore sync require internet."
                            )
                        } else {
                            tr(
                                languageCode,
                                "Koneksi internet offline. Masuk dengan Google diblokir saat offline, namun inspeksi & build APK lokal tetap berjalan.",
                                "Internet offline. Google Sign-In is blocked offline, while local APK inspection and cloning remain ready."
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.onBackground
                    )
                }
            }

            if (isSignedIn && isLocalDeviceRevoked) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .testTag("auth_revoked_warning")
                ) {
                    Text(
                        text = tr(
                            languageCode,
                            "Perangkat ini berstatus REVOKED. Sinkronisasi cloud dihentikan sementara sampai diaktifkan kembali di Device Manager atau Anda keluar akun.",
                            "This device is REVOKED. Cloud sync is paused until reactivated in Device Manager or you sign out."
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }

            if (authError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = authError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("auth_error_text")
                )
            }

            Spacer(Modifier.height(16.dp))
            if (!isSignedIn) {
                Button(
                    onClick = {
                        isLoading = true
                        authError = null
                        FMSAuthEngine.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                authError = msg
                            },
                            scope = scope,
                            onAuthCancelled = { isLoading = false },
                            isOnlineOverride = isOnline
                        )
                    },
                    enabled = !isLoading && isOnline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .height(48.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (!isOnline) {
                        Text(tr(languageCode, "Butuh Koneksi Internet (Offline)", "Internet Required (Offline)"))
                    } else {
                        Text(tr(languageCode, "Masuk dengan Google", "Sign in with Google"))
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        authError = null
                        onRefreshSession()
                    },
                    enabled = isOnline && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .height(48.dp)
                        .testTag("refresh_auth_session_button")
                ) {
                    Text(
                        tr(
                            languageCode,
                            if (isOnline) "Verifikasi & Refresh Token" else "Refresh Token (Butuh Internet)",
                            if (isOnline) "Verify & Refresh Token" else "Refresh Token (Offline)"
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        FMSAuthEngine.signOut(credentialManager, scope) {
                            onSignOut()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .height(48.dp)
                        .testTag("google_sign_out_button")
                ) {
                    Text(tr(languageCode, "Keluar Akun Google", "Sign Out"))
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .height(48.dp)
                    .testTag("auth_close_button")
            ) {
                Text(tr(languageCode, "Tutup", "Close"))
            }
        }
    }
}
