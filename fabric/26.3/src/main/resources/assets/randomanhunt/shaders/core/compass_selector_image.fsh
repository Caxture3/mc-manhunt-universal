#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in float vertexAlpha;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 sampled = texture(Sampler0, texCoord0);
    float alpha = sampled.a * vertexAlpha * ColorModulator.a;
    if (alpha == 0.0) {
        discard;
    }

    // Keep the source image RGB independent from GUI tint/color transforms.
    fragColor = vec4(sampled.rgb * alpha, alpha);
}
