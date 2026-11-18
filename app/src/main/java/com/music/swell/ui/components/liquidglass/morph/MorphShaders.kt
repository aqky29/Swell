package com.music.swell.ui.components.liquidglass.morph

private const val SdfFunctions = """
float2 nativeGlassSafeNormal(float2 v) {
    return v / max(length(v), 0.000001);
}
float3 nativeGlassRoundedField(float2 point, float2 halfExtent, float2 radii, float normalMix) {
    if (min(abs(radii.x), abs(radii.y)) <= 0.000001) {
        float2 edge = abs(point) - halfExtent;
        float2 signs = float2(point.x < 0.0 ? -1.0 : 1.0, point.y < 0.0 ? -1.0 : 1.0);
        float2 normal = signs * (edge.x > edge.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
        float2 oval = nativeGlassSafeNormal(float2(point.x, point.y * halfExtent.x / max(halfExtent.y, 0.000001)));
        return float3(max(edge.x, edge.y), nativeGlassSafeNormal(mix(normal, oval, normalMix)));
    }
    float2 r = max(abs(radii), float2(0.001));
    float radius = min(r.x, r.y);
    float2 inverseAxis = radius / r;
    float2 local = point * inverseAxis;
    float2 extent = halfExtent * inverseAxis;
    float expandedRadius = radius * 1.5286649465560913;
    float2 curvature = clamp(2.8915570351735034 -
        1.4457785175867517 * (extent * 2.0) / expandedRadius, 0.0, 1.0);
    float baseRadius = mix(expandedRadius, radius, max(curvature.x, curvature.y));
    float2 edge = abs(local) - extent;
    float2 circle = edge + baseRadius;
    float interior = min(max(circle.x, circle.y), 0.0);
    float2 z = max((edge + expandedRadius) / expandedRadius, 0.0);
    float largest = max(z.x, z.y);
    float t = largest > 0.0 ? clamp(min(z.x, z.y) / largest, 0.0, 1.0) : 0.0;
    float d = length(z);
    float polynomial = ((((-0.926054 * t + 3.15601) * t - 3.64122) * t +
        1.26803) * t + 0.268531) * t * t;
    float raw = d + 1.0 - 1.0 / (1.0 - polynomial * clamp(d, 0.0, 1.0));
    float alternate = 0.3458344340324402 +
        0.6541655659675598 * length(max(1.5286649465560913 * z - 0.528665, 0.0));
    float weight = clamp((z.y > z.x ? 1.0 : -1.0) * (t - 1.0) + 0.5, 0.0, 1.0);
    float corrected = mix(mix(raw, alternate, curvature.x), mix(raw, alternate, curvature.y), weight);
    float distance = interior + expandedRadius * (corrected - 1.0);
    float2 positiveNormal = nativeGlassSafeNormal(max(circle, 0.0));
    if (positiveNormal.x + positiveNormal.y <= 0.0)
        positiveNormal = circle.x > circle.y ? float2(1.0, 0.0) : float2(0.0, 1.0);
    float2 normal = positiveNormal * float2(local.x < 0.0 ? -1.0 : 1.0, local.y < 0.0 ? -1.0 : 1.0);
    float2 oval = nativeGlassSafeNormal(float2(local.x, local.y * extent.x / max(extent.y, 0.000001)));
    normal = nativeGlassSafeNormal(mix(normal, oval, normalMix));
    return float3(distance, normal * inverseAxis);
}
"""

private const val FieldFunctions = SdfFunctions + """
uniform float2 center;
uniform float2 halfSize;
uniform float2 cornerRadii;
uniform float secondaryEnabled;
uniform float2 secondaryCenter;
uniform float2 secondaryHalfSize;
uniform float2 secondaryRadii;
uniform float smoothing;
uniform shader smoothedField;
uniform float smoothedFieldEnabled;
uniform float2 fieldCoordinateScale;
uniform float2 fieldCoordinateOffset;
uniform float2 fieldExtent;
float3 liquidAnalyticFieldGradient(float2 point) {
    float3 a = nativeGlassRoundedField(point - center, halfSize, cornerRadii, 0.5);
    if (secondaryEnabled < 0.5) return a;
    float3 b = nativeGlassRoundedField(point - secondaryCenter, secondaryHalfSize, secondaryRadii, 0.5);
    float k = smoothing * clamp(0.5 - 0.5 * dot(a.yz, b.yz), 0.0, 1.0);
    if (k <= 0.000001) return a.x < b.x ? a : b;
    float h = clamp(0.5 + 0.5 * (a.x - b.x) / k, 0.0, 1.0);
    return float3(mix(a.x, b.x, h) - k * h * (1.0 - h), mix(a.yz, b.yz, h));
}
float3 smoothedFieldSample(float2 pixel) {
    float2 st = pixel - 0.5;
    float2 base = floor(st);
    float2 f = st - base;
    float2 f2 = f * f;
    float2 f3 = f2 * f;
    float2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;
    float2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;
    float2 w2 = (1.0 + 3.0 * f + 3.0 * f2 - 3.0 * f3) / 6.0;
    float2 w3 = f3 / 6.0;
    float2 g0 = w0 + w1;
    float2 g1 = w2 + w3;
    float2 h0 = base + 0.5 + w1 / g0 - 1.0;
    float2 h1 = base + 0.5 + w3 / g1 + 1.0;
    float3 top = float3(smoothedField.eval(float2(h0.x, h0.y)).rgb) * g0.x
        + float3(smoothedField.eval(float2(h1.x, h0.y)).rgb) * g1.x;
    float3 bottom = float3(smoothedField.eval(float2(h0.x, h1.y)).rgb) * g0.x
        + float3(smoothedField.eval(float2(h1.x, h1.y)).rgb) * g1.x;
    return top * g0.y + bottom * g1.y;
}
float3 liquidFieldGradient(float2 point) {
    if (smoothedFieldEnabled > 0.5) {
        float2 pixel = point * fieldCoordinateScale + fieldCoordinateOffset;
        if (pixel.x >= 1.5 && pixel.y >= 1.5 &&
            pixel.x <= fieldExtent.x - 1.5 && pixel.y <= fieldExtent.y - 1.5) {
            return smoothedFieldSample(pixel);
        }
    }
    return liquidAnalyticFieldGradient(point);
}
"""

private const val EdgeFunctions = """
float nativeGaussianTail(float value) {
    float x = clamp(value, -2.0, 2.0);
    float q = x * x;
    return (((0.00295448 * q - 0.03445430) * q + 0.168213) * q - 0.560546) * x + 0.5;
}
float nativeHighlightStrip(float depth, float height, float curvature, float aa) {
    float u = clamp(depth / max(height, 0.000001), 0.0, 1.0);
    float profile = mix(u < 1.0 ? 1.0 : 0.0, 1.0 - u, curvature);
    return profile * clamp(depth / aa + 0.5, 0.0, 1.0)
        * clamp((height - depth) / aa + 0.5, 0.0, 1.0);
}
float nativeHighlightAngular(float alignment, float threshold) {
    return clamp((alignment - threshold) / max(1.0 - threshold, 0.000001), 0.0, 1.0);
}
float nativeHighlightBias(float coverage, float bias) {
    return coverage / max(1.0 + bias * (1.0 - coverage), 0.000001);
}
float nativeGlassShapeCoverage(float3 field) {
    float aa = max(abs(field.y) + abs(field.z), 0.0001);
    return clamp(.5 - field.x / aa, 0.0, 1.0);
}
float nativeGlassStrokeKeyFill(float3 field, float density, float threshold) {
    float aa = max(abs(field.y) + abs(field.z), .0001);
    float coverage = nativeGlassShapeCoverage(field);
    float height = .5333333333333333 * density;
    float strip = (1.0 - coverage) * clamp((height - field.x) / aa + .5, 0.0, 1.0);
    float key = strip * nativeHighlightAngular(field.y, threshold);
    float fill = strip * nativeHighlightAngular(-field.y, threshold);
    return nativeHighlightBias(key, .5) + nativeHighlightBias(fill, .5);
}
float nativeGlassRingCoverage(float shiftedDistance, float coverage, float density) {
    float a = shiftedDistance / (5.0 * density) * .7071067811865476;
    float b = (shiftedDistance + 4.0 * density) / (5.0 * density) * .7071067811865476;
    return clamp(nativeGaussianTail(a) - nativeGaussianTail(b), 0.0, 1.0) * .06 * coverage;
}
float nativeCasdfHighlightCoverage(float signedDistance, float2 gradient,
    float height, float angle, float curvature) {
    float depth = -signedDistance;
    if (depth < -5.0) return 0.0;
    float2 normal = normalize(gradient);
    float aa = max(abs(normal.x) + abs(normal.y), 0.0001);
    float alignment = dot(normal, float2(sin(angle), -cos(angle)));
    float direct = nativeHighlightStrip(depth, height, curvature, aa);
    float diffuse = nativeHighlightStrip(depth, height * 8.0, 1.0, aa);
    float spread = cos(1.3962634015954636);
    float diffuseSpread = cos(1.3962634015954636 * 0.65);
    float key = direct * nativeHighlightAngular(alignment, spread);
    float fill = direct * nativeHighlightAngular(-alignment, spread);
    float diffuseKey = diffuse * nativeHighlightAngular(alignment, diffuseSpread);
    float diffuseFill = diffuse * nativeHighlightAngular(-alignment, diffuseSpread);
    return key + fill + nativeHighlightBias(diffuseKey, 11.333333333333334)
        + nativeHighlightBias(diffuseFill, 11.333333333333334);
}
"""

/**
 * The glass material is written once and drawn over two kinds of shape: the morph's one or two
 * fused shapes ([MorphMaterialShader]), and the up-to-four capsules of a navigation bar
 * ([MorphGroupShader]). Both supply `liquidFieldGradient`, a signed distance and a normal, and
 * share everything from there on, so a button, the bar and the menu are one material.
 */
private const val MaterialUniforms = """
uniform shader content;
uniform float2 nodeOrigin;
uniform float2 sampleExtent;
uniform float density;
uniform float lensHeight;
uniform float lensAmount;
uniform float outerHeight;
uniform float outerAmount;
uniform float4 face;
uniform float4 faceFill;
uniform float4 tint;
uniform float keyThreshold;
uniform float highlightAlpha;
uniform float rimAlpha;
uniform float surfaceOpacity;
uniform float4 press;
uniform float pressLift;
uniform float rimLift;
"""

private const val MaterialBody = """
float glassDisplacement(float signedDistance, float inverseHeight, float signedAmount) {
    float u = clamp(-signedDistance * inverseHeight, 0.0, 1.0);
    return signedAmount * (1.0 - clamp(sqrt(u * (2.0 - u)), 0.0, 1.0));
}
float3 glassSample(float2 coord) {
    half4 color = content.eval(clamp(coord, float2(0.5), sampleExtent - float2(0.5)));
    return float3(color.rgb) / max(float(color.a), 0.000001);
}
float3 glassFace(float3 sampled) {
    float3 color = sampled;
    float luminance = dot(color, float3(.2126, .7152, .0722));
    if (face.w > 0.0) {
        float gain = clamp(1.0 - luminance * face.w, 0.0, 1.0);
        color = mix(float3(luminance * gain), color * gain, 1.0 + .3 * (1.0 - gain));
        luminance = dot(color, float3(.2126, .7152, .0722));
    }
    color = (luminance * (face.z - face.y) + face.y) + (color - luminance) * face.x;
    color = mix(color, faceFill.rgb, faceFill.a);
    return mix(color, tint.rgb, tint.a);
}
float3 glassVibrant(float3 c) {
    return float3(
        dot(float3(1.1201999187, -.1894000024, -.0189999994), c),
        dot(float3(-.0562999994, .9870999455, -.0190999992), c),
        dot(float3(-.0562999994, -.1893000007, 1.1574000120), c)) + .1471000016;
}
half4 materialColor(float2 coord) {
    float2 p = coord + nodeOrigin;
    float3 field = liquidFieldGradient(p);
    float depth = -field.x;
    float coverage = nativeGlassShapeCoverage(field);
    // The reference draws a one-pixel stroke of the backdrop just outside the edge. It matches
    // the page only while the glass's frost is as sharp as the reference's 1dp; with Swell's
    // blur it picks up the blurred colour and shows as a pale line around corners and over
    // photos. Over a black page it is black either way, so dropping it changes nothing there.
    float key = 0.0;
    float ring = 0.0;
    if (depth < 22.0 * density) {
        float shifted = liquidFieldGradient(p - float2(0.0, 8.0 * density)).x;
        ring = nativeGlassRingCoverage(shifted, coverage, density);
    }
    if (coverage <= 0.0 && key <= .000001) return half4(0.0);

    float3 original = glassSample(coord);
    // The reference's glass is drawn over dark pages. Over a bright one the lens drags bright
    // content into the edge and the rim picks its colour from it, so both show as patchy lines.
    // Calm them by how bright the backdrop is right here; on a dark backdrop nothing changes.
    float brightness = clamp((dot(original, float3(.2126, .7152, .0722)) - .25) / .4, 0.0, 1.0);
    float calm = 1.0 - .6 * brightness;
    float3 filtered = original;
    if (coverage > 0.0) {
        float2 grad = field.yz;
        if (lensHeight > 0.0 && depth < lensHeight) {
            filtered = glassSample(coord + glassDisplacement(field.x, 1.0 / lensHeight, lensAmount * calm) * grad);
        }
        float weight = clamp((field.x + density) / density, 0.0, 1.0) * .6;
        if (outerHeight > 0.0 && weight > 0.0) {
            float3 outer = glassSample(coord + glassDisplacement(field.x, 1.0 / outerHeight, outerAmount * calm) * grad);
            filtered = mix(filtered, outer, weight);
        }
    }

    float4 covered = float4(glassFace(filtered), 1.0) * coverage;
    covered.rgb *= 1.0 - ring;
    covered.a = covered.a * (1.0 - ring) + ring;

    if (key > .000001 && covered.a < 1.0) {
        float3 corrected = min(glassFace(original), original);
        covered.rgb += (1.0 - covered.a) * mix(original, corrected, key);
        covered.a = mix(covered.a, 1.0, key);
    }

    float pool = 0.0;
    if (coverage > 0.0 && (press.w > 0.0 || pressLift > 0.0)) {
        float2 v = p - press.xy;
        pool = exp(-dot(v, v) / (2.0 * press.z * press.z));
        covered.rgb += (pressLift + pool * press.w) * coverage * covered.a;
    }

    float mask = 0.0;
    float aa = max(abs(field.y) + abs(field.z), .0001);
    float strip = 0.0;
    if (depth < 9.0 * density) {
        strip = nativeHighlightStrip(depth, density, .75, aa);
        float directional = nativeCasdfHighlightCoverage(field.x, field.yz, density, 0.0, .75) * highlightAlpha;
        mask = clamp(max(directional, strip * rimAlpha), 0.0, 1.0) * (1.0 - .7 * brightness);
    }
    if (mask > 0.0) {
        float3 actual = covered.rgb + (1.0 - covered.a) * original;
        float3 light = clamp(glassVibrant(actual), float3(-.75), float3(1.2));
        // A lift on the glass, never white: on a dark glass this is the reference's rim
        // untouched, on a bright one it would otherwise clip to a hard white line.
        light = min(light, min(actual + .3, float3(.88)));
        covered = float4(light * mask + covered.rgb * (1.0 - mask), mask + covered.a * (1.0 - mask));
        covered.rgb += (rimLift * (.45 + .55 * pool)) * strip;
    }
    return half4(covered * surfaceOpacity);
}
"""

internal const val MorphMaterialShader = MaterialUniforms + FieldFunctions + EdgeFunctions + MaterialBody + """
half4 main(float2 coord) {
    return materialColor(coord);
}
"""

/**
 * A navigation bar's capsules (tabs, search, now playing, dismiss) as one surface: up to four
 * rounded shapes united with the same smooth union the morph's neck uses, so two capsules that
 * come near melt into each other. A shape's alpha below one erodes it inward, so a capsule that
 * is fading out also melts away instead of just turning transparent.
 *
 * `content` is the bar's backdrop, already blurred; the material adds the lens, face, tint and
 * rim of the resting pill on top.
 */
private const val GroupFieldFunctions = SdfFunctions + """
uniform float4 shape0;
uniform float4 shape1;
uniform float4 shape2;
uniform float4 shape3;
uniform float4 shapeAlpha;
uniform float mergeDistance;
float3 groupShapeField(float2 point, float4 s, float a) {
    if (a <= 0.02 || s.z <= 0.0 || s.w <= 0.0) return float3(100000.0, 0.0, 0.0);
    float r = min(s.z, s.w);
    float3 f = nativeGlassRoundedField(point - s.xy, s.zw, float2(r), 0.5);
    float fade = 1.0 - a;
    f.x += fade * fade * r;
    return f;
}
float3 groupUnion(float3 a, float3 b) {
    if (b.x > 50000.0) return a;
    if (a.x > 50000.0) return b;
    float k = mergeDistance * clamp(0.5 - 0.5 * dot(a.yz, b.yz), 0.0, 1.0);
    if (k <= 0.000001) return a.x < b.x ? a : b;
    float h = clamp(0.5 + 0.5 * (a.x - b.x) / k, 0.0, 1.0);
    return float3(mix(a.x, b.x, h) - k * h * (1.0 - h), mix(a.yz, b.yz, h));
}
float3 liquidFieldGradient(float2 point) {
    float3 f = groupShapeField(point, shape0, shapeAlpha.x);
    f = groupUnion(f, groupShapeField(point, shape1, shapeAlpha.y));
    f = groupUnion(f, groupShapeField(point, shape2, shapeAlpha.z));
    f = groupUnion(f, groupShapeField(point, shape3, shapeAlpha.w));
    return f;
}
"""

internal const val MorphGroupShader = MaterialUniforms + GroupFieldFunctions + EdgeFunctions + MaterialBody + """
half4 main(float2 coord) {
    return materialColor(coord);
}
"""

/**
 * The pill's rim alone, over transparent: for controls that draw their own fill and only need
 * the edge to read as the same glass. There is no backdrop to take the rim's colour from, so it
 * is lit from [base], the colour of the veil it sits on.
 */
internal const val MorphRimShader = SdfFunctions + EdgeFunctions + """
uniform float2 center;
uniform float2 halfSize;
uniform float2 cornerRadii;
uniform float density;
uniform float rimAlpha;
uniform float3 base;
float3 glassVibrant(float3 c) {
    return float3(
        dot(float3(1.1201999187, -.1894000024, -.0189999994), c),
        dot(float3(-.0562999994, .9870999455, -.0190999992), c),
        dot(float3(-.0562999994, -.1893000007, 1.1574000120), c)) + .1471000016;
}
half4 main(float2 coord) {
    float3 field = nativeGlassRoundedField(coord - center, halfSize, cornerRadii, 0.5);
    float depth = -field.x;
    float coverage = nativeGlassShapeCoverage(field);
    if (coverage <= 0.0 || depth >= 9.0 * density) return half4(0.0);
    float aa = max(abs(field.y) + abs(field.z), .0001);
    float strip = nativeHighlightStrip(depth, density, .75, aa);
    float directional = nativeCasdfHighlightCoverage(field.x, field.yz, density, 0.0, .75);
    float mask = clamp(max(directional, strip * rimAlpha), 0.0, 1.0) * coverage;
    if (mask <= 0.0) return half4(0.0);
    float3 light = clamp(glassVibrant(base), float3(-.75), float3(1.2));
    light = min(light, min(base + .3, float3(.88)));
    return half4(light * mask, mask);
}
"""

internal const val MorphPortalShader = """
uniform shader content;
uniform float2 nodeOrigin;
uniform float height;
uniform float amount;
""" + FieldFunctions + """
half4 main(float2 coord) {
    float2 p = coord + nodeOrigin;
    float3 field = liquidFieldGradient(p);
    float inside = -field.x;
    float aa = max(abs(field.y) + abs(field.z), 0.0001);
    float coverage = inside < -5.0 ? 0.0 : clamp(inside / aa + 0.5, 0.0, 1.0);
    if (coverage < 0.0039215686) return half4(0.0);
    float t = clamp(inside / max(height, 0.0001), 0.0, 1.0);
    float profile = sqrt(max(1.0 - (1.0 - t) * (1.0 - t), 0.0));
    float innerAA = clamp((height - inside) / aa + 0.5, 0.0, 1.0);
    return content.eval(coord + amount * field.yz * (1.0 - profile) * innerAA) * half(coverage);
}
"""

internal const val MorphShadowShader = """
uniform float4 shadowParams;
uniform float surfaceOpacity;
""" + FieldFunctions + EdgeFunctions + """
half4 main(float2 p) {
    float distance = liquidFieldGradient(p - float2(0.0, shadowParams.z)).x;
    float shadow = nativeGaussianTail(distance / shadowParams.x) * shadowParams.y * shadowParams.w * surfaceOpacity;
    return half4(0.0, 0.0, 0.0, shadow);
}
"""
