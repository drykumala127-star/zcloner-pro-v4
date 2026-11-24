package com.dhedry.loading;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.List;

/**
 * Pure Android-Framework Splash Activity injected into compatible cloned APKs by ZClone / FMSRepackager.
 * Zero external library dependencies (no AndroidX, no Compose, no R.* resource IDs required).
 */
public class DhedrySplashActivity extends Activity {
    public static final long ANIMATION_DURATION_MS = 6500L;
    public static final String ASSET_AUDIO_PATH = "dhedry/zclone_boot_audio.mp3";
    public static final String META_TARGET_ACTIVITY = "dhedry.target_activity";
    private static final String STATE_ACCUMULATED_ELAPSED = "dhedry_accumulated_elapsed_ms";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;
    private DhedryRobotCanvasView splashView;
    private long accumulatedElapsedMs = 0L;
    private long activeResumeElapsedMs = 0L;
    private boolean isPaused = true;
    private boolean audioPrepared = false;
    private boolean audioCompleted = false;
    private boolean launchedTarget = false;

    private final Runnable finishRunnable = new Runnable() {
        @Override
        public void run() {
            launchTargetActivity();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        if (savedInstanceState != null) {
            accumulatedElapsedMs = Math.max(0L, savedInstanceState.getLong(STATE_ACCUMULATED_ELAPSED, 0L));
        }

        splashView = new DhedryRobotCanvasView(this);
        splashView.pauseWith(accumulatedElapsedMs);
        setContentView(splashView);

        prepareBootAudioAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launchedTarget || isFinishing()) return;

        isPaused = false;
        activeResumeElapsedMs = SystemClock.elapsedRealtime();

        long remaining = Math.max(0L, ANIMATION_DURATION_MS - accumulatedElapsedMs);
        if (remaining == 0L) {
            launchTargetActivity();
            return;
        }

        if (splashView != null) {
            splashView.resumeFrom(accumulatedElapsedMs, activeResumeElapsedMs);
        }
        resumeBootAudioSynced();
        handler.removeCallbacks(finishRunnable);
        handler.postDelayed(finishRunnable, remaining);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(finishRunnable);
        accumulatedElapsedMs = computeTotalElapsedMs();
        activeResumeElapsedMs = 0L;
        isPaused = true;
        if (splashView != null) {
            splashView.pauseWith(accumulatedElapsedMs);
        }
        pauseBootAudio();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_ACCUMULATED_ELAPSED, computeTotalElapsedMs());
    }

    @Override
    public void onBackPressed() {
        // Keep splash timeline deterministic and audio uninterrupted during boot sequence.
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(finishRunnable);
        releaseMediaPlayer();
        super.onDestroy();
    }

    private long computeTotalElapsedMs() {
        if (!isPaused && activeResumeElapsedMs > 0L) {
            long delta = Math.max(0L, SystemClock.elapsedRealtime() - activeResumeElapsedMs);
            return Math.min(ANIMATION_DURATION_MS, accumulatedElapsedMs + delta);
        }
        return Math.min(ANIMATION_DURATION_MS, accumulatedElapsedMs);
    }

    private void prepareBootAudioAsync() {
        AssetFileDescriptor afd = null;
        MediaPlayer mp = null;
        try {
            afd = getAssets().openFd(ASSET_AUDIO_PATH);
            mp = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mp.setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                );
            } else {
                @SuppressWarnings("deprecation")
                int stream = AudioManager.STREAM_MUSIC;
                mp.setAudioStreamType(stream);
            }
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mp.setVolume(1.0f, 1.0f);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer player) {
                    if (player != mediaPlayer) return;
                    audioPrepared = true;
                    if (!isPaused && !launchedTarget && !isFinishing() && !audioCompleted) {
                        resumeBootAudioSynced();
                    }
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer player) {
                    audioCompleted = true;
                    releaseMediaPlayer();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer player, int what, int extra) {
                    releaseMediaPlayer();
                    return true;
                }
            });
            mediaPlayer = mp;
            audioPrepared = false;
            audioCompleted = false;
            mp.prepareAsync();
        } catch (Throwable ignored) {
            if (mp != null) {
                try {
                    mp.release();
                } catch (Throwable ignoredRelease) {
                }
            }
            mediaPlayer = null;
            // Never block target app launch if audio subsystem is unavailable.
        } finally {
            if (afd != null) {
                try {
                    afd.close();
                } catch (Throwable ignoredClose) {
                }
            }
        }
    }

    private void resumeBootAudioSynced() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null || !audioPrepared || audioCompleted) return;
        try {
            if (!mp.isPlaying()) {
                long elapsed = computeTotalElapsedMs();
                int duration = mp.getDuration();
                if (elapsed > 150L && (duration <= 0 || elapsed < duration - 120L)) {
                    mp.seekTo((int) elapsed);
                }
                mp.start();
            }
        } catch (Throwable ignored) {
        }
    }

    private void pauseBootAudio() {
        MediaPlayer mp = mediaPlayer;
        if (mp == null || !audioPrepared) return;
        try {
            if (mp.isPlaying()) {
                mp.pause();
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseMediaPlayer() {
        MediaPlayer mp = mediaPlayer;
        mediaPlayer = null;
        audioPrepared = false;
        if (mp != null) {
            try {
                if (mp.isPlaying()) mp.stop();
            } catch (Throwable ignored) {
            }
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private void launchTargetActivity() {
        if (launchedTarget || isFinishing() || isPaused) return;
        launchedTarget = true;
        handler.removeCallbacks(finishRunnable);
        releaseMediaPlayer();

        String primaryTarget = resolveTargetActivityClass();
        boolean started = false;
        Throwable lastError = null;

        if (primaryTarget != null && !primaryTarget.isEmpty() && !primaryTarget.equals(getClass().getName())) {
            try {
                startResolvedComponent(primaryTarget, false);
                started = true;
            } catch (Throwable firstErr) {
                lastError = firstErr;
                try {
                    startResolvedComponent(primaryTarget, true);
                    started = true;
                } catch (Throwable retryErr) {
                    lastError = retryErr;
                }
            }
        }

        if (!started) {
            String fallbackTarget = discoverFallbackMainActivity(primaryTarget);
            if (fallbackTarget != null && !fallbackTarget.isEmpty()) {
                try {
                    startResolvedComponent(fallbackTarget, false);
                    started = true;
                } catch (Throwable fallbackErr) {
                    lastError = fallbackErr;
                    try {
                        startResolvedComponent(fallbackTarget, true);
                        started = true;
                    } catch (Throwable ignoredRetry) {
                    }
                }
            }
        }

        if (!started) {
            String label = primaryTarget != null ? primaryTarget : getPackageName();
            String errMsg = lastError != null && lastError.getMessage() != null
                ? lastError.getMessage()
                : "Target activity unavailable";
            try {
                Toast.makeText(
                    getApplicationContext(),
                    "ZClone: Unable to launch " + label + " (" + errMsg + ")",
                    Toast.LENGTH_LONG
                ).show();
            } catch (Throwable ignoredToast) {
            }
        }

        finish();
    }

    private void startResolvedComponent(String targetClass, boolean forceNewTask) {
        Intent sourceIntent = getIntent();
        Intent targetIntent = new Intent(Intent.ACTION_MAIN);
        if (sourceIntent != null && sourceIntent.getAction() != null &&
            !Intent.ACTION_MAIN.equals(sourceIntent.getAction())) {
            targetIntent.setAction(sourceIntent.getAction());
        }
        if (sourceIntent != null && sourceIntent.getData() != null) {
            targetIntent.setData(sourceIntent.getData());
        }
        if (sourceIntent != null && sourceIntent.getExtras() != null) {
            try {
                targetIntent.putExtras(sourceIntent.getExtras());
            } catch (Throwable ignoredExtras) {
            }
        }
        targetIntent.setPackage(getPackageName());
        targetIntent.setComponent(new ComponentName(getPackageName(), targetClass));
        int flags = Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP;
        if (forceNewTask) {
            flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
        }
        targetIntent.addFlags(flags);
        startActivity(targetIntent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private String discoverFallbackMainActivity(String failedTarget) {
        try {
            PackageManager pm = getPackageManager();
            Intent queryIntent = new Intent(Intent.ACTION_MAIN);
            queryIntent.setPackage(getPackageName());
            List<ResolveInfo> resolved = pm.queryIntentActivities(queryIntent, 0);
            if (resolved != null) {
                for (ResolveInfo ri : resolved) {
                    if (ri.activityInfo == null || !ri.activityInfo.enabled) continue;
                    String candidate = normalizeClassName(ri.activityInfo.name);
                    if (!candidate.equals(getClass().getName()) && !candidate.equals(failedTarget)) {
                        return candidate;
                    }
                }
            }
            android.content.pm.PackageInfo pkgInfo = pm.getPackageInfo(getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pkgInfo != null && pkgInfo.activities != null) {
                for (ActivityInfo ai : pkgInfo.activities) {
                    if (ai == null || !ai.enabled) continue;
                    String candidate = normalizeClassName(ai.name);
                    if (!candidate.equals(getClass().getName()) && !candidate.equals(failedTarget)) {
                        return candidate;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String resolveTargetActivityClass() {
        try {
            PackageManager pm = getPackageManager();
            ActivityInfo ai = pm.getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            if (ai.metaData != null) {
                String cls = ai.metaData.getString(META_TARGET_ACTIVITY);
                if (cls != null && !cls.trim().isEmpty()) {
                    return normalizeClassName(cls.trim());
                }
            }
            ApplicationInfo appInfo = pm.getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                String cls = appInfo.metaData.getString(META_TARGET_ACTIVITY);
                if (cls != null && !cls.trim().isEmpty()) {
                    return normalizeClassName(cls.trim());
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String normalizeClassName(String cls) {
        if (cls.startsWith(".")) {
            return getPackageName() + cls;
        }
        if (!cls.contains(".")) {
            return getPackageName() + "." + cls;
        }
        return cls;
    }

    private static final class DhedryRobotCanvasView extends View {
        private long accumulatedBaseMs = 0L;
        private long resumeAnchorMs = 0L;
        private boolean viewPaused = true;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint modulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tempRect = new RectF();
        private final Path tempPath = new Path();
        private final Matrix haloMatrix = new Matrix();
        private RadialGradient unitHaloShader;

        private static final int BG_DARK = Color.rgb(7, 11, 25);
        private static final int SURFACE_DARK = Color.rgb(17, 24, 46);
        private static final int CYAN = Color.rgb(0, 210, 255);
        private static final int VIOLET = Color.rgb(139, 92, 246);
        private static final int EMERALD = Color.rgb(16, 185, 129);
        private static final int GOLD = Color.rgb(245, 158, 11);

        DhedryRobotCanvasView(Context context) {
            super(context);
            gridPaint.setColor(Color.argb(26, 0, 210, 255));
            gridPaint.setStrokeWidth(1.5f);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);

            fillPaint.setStyle(Paint.Style.FILL);
            progressPaint.setStyle(Paint.Style.FILL);
            modulePaint.setStyle(Paint.Style.FILL);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

            subTextPaint.setTextAlign(Paint.Align.CENTER);
            subTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

            unitHaloShader = new RadialGradient(
                0f, 0f, 1f,
                new int[]{
                    Color.argb(190, 0, 210, 255),
                    Color.argb(110, 139, 92, 246),
                    Color.TRANSPARENT
                },
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
            );
            glowPaint.setShader(unitHaloShader);
        }

        void pauseWith(long elapsedMs) {
            accumulatedBaseMs = Math.max(0L, Math.min(ANIMATION_DURATION_MS, elapsedMs));
            resumeAnchorMs = 0L;
            viewPaused = true;
            invalidate();
        }

        void resumeFrom(long baseElapsedMs, long anchorMs) {
            accumulatedBaseMs = Math.max(0L, Math.min(ANIMATION_DURATION_MS, baseElapsedMs));
            resumeAnchorMs = anchorMs;
            viewPaused = false;
            postInvalidateOnAnimation();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0 || h <= 0) return;
            bgPaint.setShader(new LinearGradient(0f, 0f, 0f, h, Color.rgb(4, 7, 16), BG_DARK, Shader.TileMode.CLAMP));
            float cx = w * 0.5f;
            float unit = Math.min(w, h) * 0.0026f;
            float barW = Math.min(w * 0.68f, 260f * unit);
            float barX = cx - barW * 0.5f;
            progressPaint.setShader(new LinearGradient(barX, 0f, barX + barW, 0f, CYAN, VIOLET, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            long elapsed = viewPaused || resumeAnchorMs <= 0L
                ? accumulatedBaseMs
                : Math.min(ANIMATION_DURATION_MS, accumulatedBaseMs + Math.max(0L, SystemClock.elapsedRealtime() - resumeAnchorMs));
            float t = Math.max(0f, Math.min(1f, elapsed / (float) ANIMATION_DURATION_MS));
            float sec = t * (ANIMATION_DURATION_MS / 1000f);

            // 1. Dark background & circuit grid
            canvas.drawRect(0f, 0f, w, h, bgPaint);

            float gridStep = Math.min(w, h) * 0.08f;
            float gridAlpha = clamp01(sec / 0.65f) * 0.25f;
            gridPaint.setAlpha((int) (gridAlpha * 255));
            for (float gx = 0f; gx < w; gx += gridStep) {
                canvas.drawLine(gx, 0f, gx, h, gridPaint);
            }
            for (float gy = 0f; gy < h; gy += gridStep) {
                canvas.drawLine(0f, gy, w, gy, gridPaint);
            }

            float cx = w * 0.5f;
            float cy = h * 0.44f;
            float unit = Math.min(w, h) * 0.0026f;

            // Core ignition intensity (Stage 6: 3.95s - 4.55s)
            float coreGlow = clamp01((sec - 3.90f) / 0.65f);
            float robotReveal = clamp01(sec / 0.65f);
            float scanProgress = clamp01((sec - 0.65f) / 0.70f);
            float disasmProgress = clamp01((sec - 1.35f) / 0.80f);
            float processPulse = (sec >= 2.15f && sec <= 3.10f)
                ? (0.5f + 0.5f * (float) Math.sin((sec - 2.15f) * 14f))
                : 0f;
            float reasmProgress = clamp01((sec - 3.05f) / 0.90f);
            float logoReveal = clamp01((sec - 4.55f) / 0.50f);
            float welcomeReveal = clamp01((sec - 5.05f) / 0.50f);
            float brandReveal = clamp01((sec - 5.55f) / 0.50f);

            // Ambient plasma halo behind robot using cached unit gradient + Matrix scale
            float haloR = Math.max(1f, (140f + 75f * coreGlow) * unit);
            haloMatrix.reset();
            haloMatrix.setScale(haloR, haloR);
            haloMatrix.postTranslate(cx, cy);
            unitHaloShader.setLocalMatrix(haloMatrix);
            glowPaint.setAlpha((int) (255 * clamp01((0.35f + 0.65f * coreGlow) * robotReveal)));
            canvas.drawCircle(cx, cy, haloR, glowPaint);

            // Draw Android Robot Silhouette (Stage 1..6)
            float robotScale = 0.82f + 0.18f * robotReveal;
            float robotAlpha = robotReveal * (1f - 0.35f * logoReveal);
            drawAndroidRobot(canvas, cx, cy - 28f * unit, unit * robotScale, robotAlpha, coreGlow, processPulse, sec);

            // Stage 2: Laser Scanning Beam (0.65s - 1.35s)
            if (sec >= 0.65f && sec <= 1.45f) {
                float beamY = (cy + 36f * unit) + (scanProgress - 0.5f) * 70f * unit;
                strokePaint.setColor(Color.argb(210, 0, 210, 255));
                strokePaint.setStrokeWidth(3.5f * unit);
                canvas.drawLine(cx - 110f * unit, beamY, cx + 110f * unit, beamY, strokePaint);
                fillPaint.setColor(Color.argb(45, 0, 210, 255));
                canvas.drawRect(cx - 105f * unit, beamY - 12f * unit, cx + 105f * unit, beamY + 12f * unit, fillPaint);
            }

            // Stage 3-5: APK Container & Disassembled Digital Modules
            float targetCubeY = cy + 92f * unit;
            float spreadFactor = disasmProgress * (1f - reasmProgress);
            drawDigitalModules(canvas, cx, targetCubeY, unit, robotReveal * (1f - 0.85f * logoReveal), spreadFactor, processPulse, coreGlow);

            // Stage 6-7: Energy Ring & ZClone Logo Reveal
            if (coreGlow > 0.01f) {
                strokePaint.setColor(Color.argb((int) (220 * coreGlow), 16, 185, 129));
                strokePaint.setStrokeWidth(3f * unit);
                canvas.drawCircle(cx, cy - 28f * unit, (88f + 22f * coreGlow) * unit, strokePaint);
            }
            if (logoReveal > 0.01f) {
                drawZCloneLogo(canvas, cx, cy + 56f * unit, unit * (0.75f + 0.25f * logoReveal), logoReveal);
            }

            // Stage Label / Status Indicator
            String stageStatus = stageLabelForSecond(sec);
            subTextPaint.setTextSize(11.5f * unit);
            subTextPaint.setColor(Color.argb((int) (210 * robotReveal), 0, 210, 255));
            canvas.drawText(stageStatus, cx, h * 0.72f, subTextPaint);

            // Progress track
            float barW = Math.min(w * 0.68f, 260f * unit);
            float barH = 5f * unit;
            float barX = cx - barW * 0.5f;
            float barY = h * 0.75f;
            fillPaint.setColor(SURFACE_DARK);
            tempRect.set(barX, barY, barX + barW, barY + barH);
            canvas.drawRoundRect(tempRect, barH, barH, fillPaint);

            tempRect.set(barX, barY, barX + barW * t, barY + barH);
            canvas.drawRoundRect(tempRect, barH, barH, progressPaint);

            // Stage 8: "Welcome to ZClone Engine"
            if (welcomeReveal > 0.01f) {
                textPaint.setTextSize(20f * unit);
                textPaint.setColor(Color.argb((int) (255 * welcomeReveal), 248, 250, 252));
                canvas.drawText("Welcome to ZClone Engine", cx, h * 0.83f, textPaint);
            }

            // Stage 9: "Powered by Dhedry Library"
            if (brandReveal > 0.01f) {
                subTextPaint.setTextSize(13f * unit);
                subTextPaint.setColor(Color.argb((int) (255 * brandReveal), 16, 185, 129));
                canvas.drawText("Powered by Dhedry Library", cx, h * 0.885f, subTextPaint);
            } else {
                subTextPaint.setTextSize(11f * unit);
                subTextPaint.setColor(Color.argb((int) (140 * robotReveal), 148, 163, 184));
                canvas.drawText("DHEDRY ENGINE · APPLICATION ANALYSIS & REBUILD", cx, h * 0.885f, subTextPaint);
            }

            if (!viewPaused && elapsed < ANIMATION_DURATION_MS) {
                postInvalidateOnAnimation();
            }
        }

        private void drawAndroidRobot(
            Canvas canvas, float cx, float cy, float u, float alpha, float coreGlow, float pulse, float sec
        ) {
            int a = (int) (clamp01(alpha) * 255);
            if (a <= 0) return;

            // Head dome
            float headR = 36f * u;
            float headCy = cy - 28f * u;
            fillPaint.setColor(Color.argb(a, 17, 24, 46));
            tempRect.set(cx - headR, headCy - headR, cx + headR, headCy + headR);
            canvas.drawArc(tempRect, 180f, 180f, true, fillPaint);

            strokePaint.setColor(Color.argb(a, 0, 210, 255));
            strokePaint.setStrokeWidth(2.6f * u);
            canvas.drawArc(tempRect, 180f, 180f, false, strokePaint);
            canvas.drawLine(cx - headR, headCy, cx + headR, headCy, strokePaint);

            // Antennas
            canvas.drawLine(cx - 16f * u, headCy - 30f * u, cx - 26f * u, headCy - 46f * u, strokePaint);
            canvas.drawLine(cx + 16f * u, headCy - 30f * u, cx + 26f * u, headCy - 46f * u, strokePaint);

            // Glowing robot eyes
            int eyeColor = coreGlow > 0.3f ? EMERALD : CYAN;
            fillPaint.setColor(Color.argb(a, Color.red(eyeColor), Color.green(eyeColor), Color.blue(eyeColor)));
            float eyeY = headCy - 14f * u;
            canvas.drawCircle(cx - 14f * u, eyeY, (4.2f + pulse * 1.2f) * u, fillPaint);
            canvas.drawCircle(cx + 14f * u, eyeY, (4.2f + pulse * 1.2f) * u, fillPaint);

            // Torso body
            float bodyTop = headCy + 6f * u;
            float bodyBot = bodyTop + 62f * u;
            float bodyHalf = 38f * u;
            fillPaint.setColor(Color.argb(a, 17, 24, 46));
            tempRect.set(cx - bodyHalf, bodyTop, cx + bodyHalf, bodyBot);
            canvas.drawRoundRect(tempRect, 12f * u, 12f * u, fillPaint);

            strokePaint.setColor(Color.argb(a, 139, 92, 246));
            canvas.drawRoundRect(tempRect, 12f * u, 12f * u, strokePaint);

            // Arms (subtle articulation during scan/assembly)
            float armSwing = (float) Math.sin(sec * 5.0f) * 5f * u * (1f - coreGlow);
            tempRect.set(cx - bodyHalf - 16f * u, bodyTop + 4f * u + armSwing, cx - bodyHalf - 4f * u, bodyTop + 46f * u + armSwing);
            canvas.drawRoundRect(tempRect, 6f * u, 6f * u, fillPaint);
            canvas.drawRoundRect(tempRect, 6f * u, 6f * u, strokePaint);

            tempRect.set(cx + bodyHalf + 4f * u, bodyTop + 4f * u - armSwing, cx + bodyHalf + 16f * u, bodyTop + 46f * u - armSwing);
            canvas.drawRoundRect(tempRect, 6f * u, 6f * u, fillPaint);
            canvas.drawRoundRect(tempRect, 6f * u, 6f * u, strokePaint);

            // Energy Core in chest
            float coreY = bodyTop + 30f * u;
            float coreR = (9f + 6f * coreGlow + 2f * pulse) * u;
            fillPaint.setColor(Color.argb((int) (a * (0.4f + 0.6f * coreGlow)), 0, 210, 255));
            canvas.drawCircle(cx, coreY, coreR * 1.4f, fillPaint);
            fillPaint.setColor(Color.argb(a, 16, 185, 129));
            canvas.drawCircle(cx, coreY, coreR * 0.7f, fillPaint);
        }

        private void drawDigitalModules(
            Canvas canvas, float cx, float cy, float u, float alpha, float spread, float pulse, float coreGlow
        ) {
            int a = (int) (clamp01(alpha) * 255);
            if (a <= 5) return;

            String[] labels = {"AXML", "DEX", "RES", "SIGN"};
            int[] colors = {CYAN, VIOLET, GOLD, EMERALD};
            float[] dx = {-1f, 1f, -1f, 1f};
            float[] dy = {-0.65f, -0.65f, 0.65f, 0.65f};

            float boxW = 34f * u;
            float boxH = 22f * u;
            for (int i = 0; i < 4; i++) {
                float mx = cx + dx[i] * (20f + 56f * spread) * u;
                float my = cy + dy[i] * (14f + 26f * spread) * u;
                tempRect.set(mx - boxW * 0.5f, my - boxH * 0.5f, mx + boxW * 0.5f, my + boxH * 0.5f);

                modulePaint.setColor(Color.argb(a, 17, 24, 46));
                canvas.drawRoundRect(tempRect, 5f * u, 5f * u, modulePaint);

                int c = colors[i];
                strokePaint.setColor(Color.argb(a, Color.red(c), Color.green(c), Color.blue(c)));
                strokePaint.setStrokeWidth((1.8f + pulse * 0.8f + coreGlow) * u);
                canvas.drawRoundRect(tempRect, 5f * u, 5f * u, strokePaint);

                subTextPaint.setTextSize(8.2f * u);
                subTextPaint.setColor(Color.argb(a, 248, 250, 252));
                canvas.drawText(labels[i], mx, my + 3f * u, subTextPaint);
            }
        }

        private void drawZCloneLogo(Canvas canvas, float cx, float cy, float u, float alpha) {
            int a = (int) (clamp01(alpha) * 255);
            float r = 28f * u;
            tempRect.set(cx - r, cy - r, cx + r, cy + r);
            fillPaint.setColor(Color.argb(a, 17, 24, 46));
            canvas.drawRoundRect(tempRect, 12f * u, 12f * u, fillPaint);

            strokePaint.setColor(Color.argb(a, 0, 210, 255));
            strokePaint.setStrokeWidth(2.8f * u);
            canvas.drawRoundRect(tempRect, 12f * u, 12f * u, strokePaint);

            // Stylized 'Z' mark
            tempPath.reset();
            tempPath.moveTo(cx - 12f * u, cy - 11f * u);
            tempPath.lineTo(cx + 12f * u, cy - 11f * u);
            tempPath.lineTo(cx - 12f * u, cy + 11f * u);
            tempPath.lineTo(cx + 12f * u, cy + 11f * u);
            strokePaint.setColor(Color.argb(a, 248, 250, 252));
            strokePaint.setStrokeWidth(3.6f * u);
            canvas.drawPath(tempPath, strokePaint);
        }

        private static String stageLabelForSecond(float sec) {
            if (sec < 0.65f) return "01 // INITIALIZING DHEDRY ROBOT CORE";
            if (sec < 1.35f) return "02 // SCANNING TARGET APK CONTAINER";
            if (sec < 2.15f) return "03 // DISASSEMBLING DIGITAL MODULES";
            if (sec < 3.05f) return "04 // INSPECTING COMPONENTS & INTEGRITY";
            if (sec < 3.95f) return "05 // REASSEMBLING CLONE PACKAGE";
            if (sec < 4.55f) return "06 // ENERGY CORE IGNITION ACTIVE";
            if (sec < 5.05f) return "07 // ZCLONE ENGINE READY";
            if (sec < 5.55f) return "08 // WELCOME TO ZCLONE ENGINE";
            return "09 // LAUNCHING TARGET APPLICATION";
        }

        private static float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }
    }
}
