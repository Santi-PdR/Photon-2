#version 330 core

uniform vec2 ScreenSize;
uniform vec3 U_CameraPosition;
uniform mat4 U_InverseProjectionMatrix;
uniform mat4 U_InverseViewMatrix;

uniform sampler2D IceTexture;
uniform sampler2D SamplerSceneDepth;
uniform sampler2D SamplerSceneColor;
uniform float GameTime;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
//in vec3 vViewPos;
//in vec3 vViewNormal;

out vec4 fragColor;

const float SCANNING_WIDTH = 1.0;

float remapClamped(float value, float minVal, float maxVal) {
    if (value < minVal) return 0;
    if (value > maxVal) return 1;
    return (value - minVal) / (maxVal - minVal);
}

void main() {
    vec2 screenUV = gl_FragCoord.xy / ScreenSize;
    float depth = texture(SamplerSceneDepth, screenUV).r;
    vec4 color = texture(SamplerSceneColor, screenUV);

    if (depth >= 1) {
        fragColor = color;
    } else {
        vec3 ndc;
        ndc.xy = screenUV.xy * 2.0 - 1.0;
        ndc.z = depth * 2.0 - 1.0;
        vec4 clipSpacePos = vec4(ndc, 1.0);
        vec4 viewSpacePos = U_InverseProjectionMatrix * clipSpacePos;
        viewSpacePos /= viewSpacePos.w;
        vec4 worldSpacePos = U_InverseViewMatrix * viewSpacePos;
        worldSpacePos /= worldSpacePos.w;
        float distToCamera = length(worldSpacePos.xyz);

        float radius = 15 + sin(GameTime * 1000) * 20;
        vec3 scanColor = vec3(0.6, 0.2, 0.3);
        if (distToCamera < radius) {
            if (distToCamera > radius - SCANNING_WIDTH) {
                float step = smoothstep(radius - SCANNING_WIDTH, radius, distToCamera);
                fragColor = color + vec4(step, step, step, 0.);
            } else{
                fragColor = vec4(vec3(1 - remapClamped(distToCamera, 0, 20)), 1.0);
            }
        } else {
            fragColor = color;
        }
    }


}