#version 440

// A picture cropped to cover its box and clipped to the squircle, with the same hairline and lit ring as the surface primitive so a thumbnail and a panel read as one material.

layout(location = 0) in vec2 qt_TexCoord0;
layout(location = 0) out vec4 fragColor;

layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    vec2 itemSize;
    vec2 sourceSize;
    float radius;
    float exponent;
    float borderWidth;
    float insetWidth;
    vec4 borderColor;
    vec4 insetColor;
} ubuf;

layout(binding = 1) uniform sampler2D source;

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

    vec2 safeSource = max(ubuf.sourceSize, vec2(1.0));
    float scale = max(ubuf.itemSize.x / safeSource.x, ubuf.itemSize.y / safeSource.y);
    vec2 shown = safeSource * scale;
    vec2 uv = (qt_TexCoord0 * ubuf.itemSize - (ubuf.itemSize - shown) * 0.5) / shown;
    vec4 color = texture(source, uv);

    if (ubuf.borderWidth > 0.0) {
        float band = clamp(distance + ubuf.borderWidth + 0.5, 0.0, 1.0);
        color = mix(color, over(ubuf.borderColor, color), band);
    }
    if (ubuf.insetWidth > 0.0) {
        float band = clamp(distance + ubuf.insetWidth + 0.5, 0.0, 1.0);
        color = mix(color, over(ubuf.insetColor, color), band);
    }
    fragColor = color * coverage * ubuf.qt_Opacity;
}
