#version 440

// A white glyph mask re-inked on the GPU, so an icon can take the accent, or ease between two inks, without decoding its SVG again. Black in the mask is a cut-out: the card a glyph lays over another hides the lines behind it.

layout(location = 0) in vec2 qt_TexCoord0;
layout(location = 0) out vec4 fragColor;

layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    vec4 ink;
} ubuf;

layout(binding = 1) uniform sampler2D source;

void main()
{
    // Premultiplied, so red is the white share of the coverage: white draws, black cuts out.
    fragColor = ubuf.ink * texture(source, qt_TexCoord0).r * ubuf.qt_Opacity;
}
