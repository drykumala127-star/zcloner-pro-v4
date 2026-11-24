package com.fms.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * FMSLayoutEngine: Responsive width-class classification (Compact, Medium, Expanded).
 */
enum class FmsWindowWidthClass { COMPACT, MEDIUM, EXPANDED }

object FMSLayoutEngine {
    fun classifyWidth(maxWidthDp: Dp): FmsWindowWidthClass = when {
        maxWidthDp < 600.dp -> FmsWindowWidthClass.COMPACT
        maxWidthDp < 840.dp -> FmsWindowWidthClass.MEDIUM
        else -> FmsWindowWidthClass.EXPANDED
    }
}

/**
 * FMSNavigationEngine: Independent, type-safe navigation backstack with native BackHandler support.
 */
@Stable
class FMSNavigationEngine<T : Any>(initialRoute: T) {
    private val _backStack = mutableStateListOf(initialRoute)
    val currentRoute: T get() = _backStack.last()
    val canPop: Boolean get() = _backStack.size > 1

    fun snapshot(): List<T> = _backStack.toList()

    fun restore(routes: List<T>) {
        if (routes.isNotEmpty()) {
            _backStack.clear()
            _backStack.addAll(routes)
        }
    }

    fun navigate(route: T) {
        if (_backStack.lastOrNull() != route) {
            _backStack.add(route)
        }
    }

    fun replaceRoot(route: T) {
        _backStack.clear()
        _backStack.add(route)
    }

    fun pop(): Boolean {
        return if (_backStack.size > 1) {
            _backStack.removeAt(_backStack.lastIndex)
            true
        } else {
            false
        }
    }
}

@Composable
fun <T : Any> FmsBackHandler(navEngine: FMSNavigationEngine<T>) {
    BackHandler(enabled = navEngine.canPop) {
        navEngine.pop()
    }
}

/**
 * FMSRenderingEngine: GPU-friendly glowing cyan-to-violet wave atmosphere matching the ZClone mockup.
 */
@Composable
fun FmsNeonWaveBackdrop(
    theme: FmsThemePreset,
    modifier: Modifier = Modifier,
    animateWaves: Boolean = true
) {
    val phaseState = if (animateWaves) {
        val inf = rememberInfiniteTransition(label = "fms_wave")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fms_wave_phase"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(
                    colors = listOf(
                        theme.background,
                        theme.surface.copy(alpha = if (theme.isDark) 0.92f else 0.7f),
                        theme.background
                    )
                )
                val glowBrush = Brush.radialGradient(
                    colors = listOf(theme.primary.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.14f),
                    radius = (size.maxDimension * 0.55f).coerceAtLeast(1f)
                )
                val waveBrushes = List(3) { layer ->
                    Brush.horizontalGradient(
                        listOf(
                            theme.primary.copy(alpha = 0.45f - layer * 0.1f),
                            theme.secondary.copy(alpha = 0.55f - layer * 0.12f),
                            theme.primary.copy(alpha = 0.25f)
                        )
                    )
                }
                val wavePath = Path()
                val strokeWidths = FloatArray(3) { layer -> (6.dp - layer.dp).toPx() }

                onDrawBehind {
                    val phase = phaseState.value
                    drawRect(brush = bgBrush)
                    drawCircle(brush = glowBrush)

                    for (layer in 0..2) {
                        wavePath.reset()
                        val baseY = size.height * (0.64f + layer * 0.055f)
                        val amp = size.height * (0.045f + layer * 0.01f)
                        wavePath.moveTo(0f, baseY)
                        var x = 0f
                        val step = (size.width / 24f).coerceAtLeast(8f)
                        while (x <= size.width + step) {
                            val norm = x / size.width.coerceAtLeast(1f)
                            val y = baseY + sin(norm * 5.2f + phase + layer * 0.9f) * amp
                            wavePath.lineTo(x, y)
                            x += step
                        }
                        drawPath(
                            path = wavePath,
                            brush = waveBrushes[layer],
                            style = Stroke(width = strokeWidths[layer], cap = StrokeCap.Round)
                        )
                    }
                }
            }
    )
}

/**
 * FMSWidgetEngine & FMSDesignSystem: Core ZClone glassmorphic cards, gradient buttons, and badges.
 */
@Composable
fun FmsGlassCard(
    theme: FmsThemePreset,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = theme.border,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    theme.surfaceElevated.copy(alpha = if (theme.isDark) 0.9f else 0.98f),
                    theme.surface.copy(alpha = if (theme.isDark) 0.95f else 1f)
                )
            )
        )
        .border(BorderStroke(1.dp, borderColor.copy(alpha = 0.7f)), shape)
        .let { m ->
            if (onClick != null) m.minimumInteractiveComponentSize().clickable(onClick = onClick) else m
        }

    Box(modifier = baseModifier, content = content)
}

@Composable
fun FmsGradientButton(
    text: String,
    theme: FmsThemePreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = theme.surfaceElevated.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                brush = if (enabled) {
                    Brush.horizontalGradient(listOf(theme.primary, theme.secondary))
                } else {
                    Brush.horizontalGradient(listOf(theme.border, theme.border))
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FmsStatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = modifier.semantics { contentDescription = label }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

fun fmsIconContainerShape(iconStyle: FmsIconStyle) = when (iconStyle) {
    FmsIconStyle.ROUNDED -> RoundedCornerShape(10.dp)
    FmsIconStyle.SQUIRCLE -> RoundedCornerShape(16.dp)
    FmsIconStyle.NEON_RING -> androidx.compose.foundation.shape.CircleShape
}

