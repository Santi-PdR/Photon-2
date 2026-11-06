#version 150

uniform sampler2D inputSampler;
uniform float Threshold;      // brightest RGB channel threshold
uniform float Knee;     // [0,1]：soft-knee

in vec2 texCoord;
out vec4 fragColor;

void main(){
    vec3 c = texture(inputSampler, texCoord).rgb;
    // Luma suppresses saturated red and blue even when their channels are fully bright.
    // Use the strongest channel so saturated FX colors bloom without lowering the threshold
    // enough to turn ordinary gray scene pixels into highlights.
    float br = max(max(c.r, c.g), c.b);
    // soft-knee
    float knee = max(Threshold * Knee, 1e-5);
    float soft = clamp((br - Threshold + knee) / (2.0 * knee), 0.0, 1.0);
    float bright = max(br - Threshold, 0.0) + soft * soft * 2.0 * knee;
    fragColor = vec4(c * (bright / max(br, 1e-5)), 1.0);  // 仅保留亮区
}

