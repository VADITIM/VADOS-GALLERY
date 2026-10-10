#version 440

// The one surface primitive: a superellipse-cornered box with a hairline border, an optional lit inset ring, and the pointer-tracked hue ring masked to the edge. Colours arrive premultiplied from ShaderEffect.

layout(location = 0) in vec2 qt_TexCoord0;
layout(location = 0) out vec4 fragColor;

layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    vec2 itemSize;
    float radius;
    float exponent;
    float borderWidth;
    float insetWidth;
    float glowOpacity;
    float glowRadius;
    vec2 glowPosition;
    vec4 fillColor;
    vec4 borderColor;
    vec4 insetColor;
    vec4 glowColor;
} ubuf;

float superLength(vec2 v, float n)
{
    v = max(v, vec2(0.0));
    return pow(pow(v.x, n) + pow(v.y, n), 1.0 / n);
}

float squircleDistance(vec2 point, vec2 halfSize, float cornerRadius, float n)
{
    vec2 q = abs(point) - halfSize + vec2(cornerRadius);
    return superLength(q, n) + min(max(q.x, q.y), 0.0) - cornerRadius;
}

vec4 over(vec4 top, vec4 bottom)
{
    return top + bottom * (1.0 - top.a);
}

void main()
{
    vec2 halfSize = ubuf.itemSize * 0.5;
    vec2 point = qt_TexCoord0 * ubuf.itemSize - halfSize;
    float cornerRadius = min(ubuf.radius, min(halfSize.x, halfSize.y));
    float distance = squircleDistance(point, halfSize, cornerRadius, ubuf.exponent);
    float coverage = clamp(0.5 - distance, 0.0, 1.0);

    vec4 color = ubuf.fillColor;
    if (ubuf.borderWidth > 0.0) {
        float band = clamp(distance + ubuf.borderWidth + 0.5, 0.0, 1.0);
        color = mix(color, over(ubuf.borderColor, color), band);
    }
    if (ubuf.insetWidth > 0.0) {
        float band = clamp(distance + ubuf.insetWidth + 0.5, 0.0, 1.0);
        color = mix(color, over(ubuf.insetColor, color), band);
    }
    if (ubuf.glowOpacity > 0.0) {
        // The last stop stays at 20% so the whole edge is charged and the pointer only marks its hottest point (VAS module §3).
        float ring = clamp(distance + 1.5 + 0.5, 0.0, 1.0);
        float along = length(point + halfSize - ubuf.glowPosition) / max(ubuf.glowRadius, 1.0);
        float intensity = along < 0.45 ? mix(0.95, 0.5, along / 0.45) : mix(0.5, 0.2, clamp((along - 0.45) / 0.55, 0.0, 1.0));
        color = over(ubuf.glowColor * intensity * ring * ubuf.glowOpacity, color);
    }
    fragColor = color * coverage * ubuf.qt_Opacity;
}
