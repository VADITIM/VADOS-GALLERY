#version 440

// Text too long for its box fades out on the edge it overflows; never cut at a hard edge, never ellipsised (VAS typography). Only the last line fades, because that is the only line that ran out of room.

layout(location = 0) in vec2 qt_TexCoord0;
layout(location = 0) out vec4 fragColor;

layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    vec2 itemSize;
    float fadeWidth;
    float fadeTop;
    float isMirrored;
} ubuf;

layout(binding = 1) uniform sampler2D source;

void main()
{
    vec2 pixel = qt_TexCoord0 * ubuf.itemSize;
    float fromEdge = ubuf.isMirrored > 0.5 ? pixel.x : ubuf.itemSize.x - pixel.x;
    float fade = pixel.y >= ubuf.fadeTop ? clamp(fromEdge / max(ubuf.fadeWidth, 1.0), 0.0, 1.0) : 1.0;
    fragColor = texture(source, qt_TexCoord0) * fade * ubuf.qt_Opacity;
}
