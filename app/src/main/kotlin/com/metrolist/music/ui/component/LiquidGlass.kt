package com.metrolist.music.ui.component

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.composed
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent

const val DEFAULT_LIQUID_GLASS_VIBRANCY = 1.4f
const val DEFAULT_LIQUID_GLASS_BLUR_RADIUS = 61f
const val DEFAULT_LIQUID_GLASS_REFRACTION_HEIGHT_DP = 12f
const val DEFAULT_LIQUID_GLASS_REFRACTION_AMOUNT_DP = 24f
const val DEFAULT_LIQUID_GLASS_CHROMATIC_ABERRATION = 0.5f
const val DEFAULT_LIQUID_GLASS_DEPTH_EFFECT = 0.2f

@Stable
class LiquidGlassBackdrop internal constructor(
    val layer: GraphicsLayer,
    val rootOffset: MutableState<Offset>,
)

val LocalLiquidGlassBackdrop = staticCompositionLocalOf<LiquidGlassBackdrop?> { null }

@Composable
fun rememberLiquidGlassBackdrop(): LiquidGlassBackdrop {
    val layer = rememberGraphicsLayer()
    val rootOffset = remember { mutableStateOf(Offset.Zero) }
    return remember(layer, rootOffset) { LiquidGlassBackdrop(layer, rootOffset) }
}

fun Modifier.recordLiquidGlassBackdrop(backdrop: LiquidGlassBackdrop, enabled: Boolean = true): Modifier =
    onGloballyPositioned { backdrop.rootOffset.value = it.positionInRoot() }.drawWithContent {
    if (!enabled) {
        this@drawWithContent.drawContent()
        return@drawWithContent
    }
    backdrop.layer.record { this@drawWithContent.drawContent() }
    drawLayer(backdrop.layer)
}

/** Uses the runtime shader on Android 13+ and otherwise leaves a caller-provided glass tint. */
@Composable
fun Modifier.liquidGlassIfAvailable(
    enabled: Boolean,
    cornerRadius: Dp,
    refractionHeightDp: Float = DEFAULT_LIQUID_GLASS_REFRACTION_HEIGHT_DP,
    refractionAmountDp: Float = DEFAULT_LIQUID_GLASS_REFRACTION_AMOUNT_DP,
    blurRadiusPx: Float = DEFAULT_LIQUID_GLASS_BLUR_RADIUS,
    vibrancy: Float = DEFAULT_LIQUID_GLASS_VIBRANCY,
    chromaticAberration: Float = DEFAULT_LIQUID_GLASS_CHROMATIC_ABERRATION,
    depthEffect: Float = DEFAULT_LIQUID_GLASS_DEPTH_EFFECT,
): Modifier {
    val backdrop = LocalLiquidGlassBackdrop.current
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backdrop == null) return this

    val density = LocalDensity.current
    return liquidGlass(
        backdrop = backdrop,
        cornerRadiusPx = with(density) { cornerRadius.toPx() },
        refractionHeightPx = with(density) { refractionHeightDp.coerceIn(1f, 48f).dp.toPx() },
        refractionAmountPx = with(density) { refractionAmountDp.coerceIn(0f, 80f).dp.toPx() },
        blurPx = blurRadiusPx.coerceIn(0f, 100f),
        vibrancy = vibrancy.coerceIn(0.5f, 2f),
        chromaticAberration = chromaticAberration.coerceIn(0f, 1f),
        depthEffect = depthEffect.coerceIn(0f, 1f),
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.liquidGlass(
    backdrop: LiquidGlassBackdrop,
    cornerRadiusPx: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    blurPx: Float,
    vibrancy: Float,
    chromaticAberration: Float,
    depthEffect: Float,
): Modifier = composed {
    var position by remember { mutableStateOf(Offset.Zero) }
    val glassLayer = rememberGraphicsLayer()
    val shader = remember { RuntimeShader(SHADER) }

    this
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawBehind {
            if (size.width <= 0f || size.height <= 0f) return@drawBehind

            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("offset", 0f, 0f)
            shader.setFloatUniform("cornerRadii", cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx)
            shader.setFloatUniform("refractionHeight", refractionHeightPx)
            shader.setFloatUniform("refractionAmount", refractionAmountPx)
            shader.setFloatUniform("depthEffect", depthEffect)
            shader.setFloatUniform("chromaticAberration", chromaticAberration)

            val blur = RenderEffect.createBlurEffect(
                blurPx.coerceAtLeast(0.01f),
                blurPx.coerceAtLeast(0.01f),
                Shader.TileMode.CLAMP,
            )
            val saturation = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(vibrancy) }),
                blur,
            )
            val refracted = RenderEffect.createRuntimeShaderEffect(shader, "content")
            glassLayer.renderEffect = RenderEffect.createChainEffect(refracted, saturation).asComposeRenderEffect()

            glassLayer.record(
                size = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            ) {
                translate(
                    backdrop.rootOffset.value.x - position.x,
                    backdrop.rootOffset.value.y - position.y,
                ) { drawLayer(backdrop.layer) }
            }
            drawLayer(glassLayer)
        }
}

private const val SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y; else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x; else return radii.w;
    }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        float2 safeCorner = max(cornerCoord, float2(0.0001));
        return sign(coord) * normalize(safeCorner);
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) return content.eval(coord);
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 radial = centeredCoord / max(length(centeredCoord), 0.0001);
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * radial);

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5; color.a += red.a / 7.0;
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5; color.a += green.a / 7.0;
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0;
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0; color.a += blue.a / 7.0;
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;
    return color;
}
"""
