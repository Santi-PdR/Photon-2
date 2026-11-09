#version 150

uniform sampler2D inputSampler;
uniform sampler2D sceneSampler;
uniform float Threshold;      // brightest RGB channel threshold
uniform float Knee;           // [0,1]: soft-knee
uniform float SceneMask;      // 0 = every pixel may bloom, 1 = only pixels this pass ADDED to the frame

in vec2 texCoord;
out vec4 fragColor;

void main(){
    vec3 c = texture(inputSampler, texCoord).rgb;
    // Luma suppresses saturated red and blue even when their channels are fully bright.
    // Use the strongest channel so saturated FX colors bloom without lowering the threshold
    // enough to turn ordinary gray scene pixels into highlights.
    float br = max(max(c.r, c.g), c.b);

    // Scene mask: the frame a build started from is the "before" copy, so any pixel that grew is
    // something Photon drew. Without this the sky, the sun, clouds and lit stone all cross a low
    // threshold, and the halo turns into a full-screen haze that distorts the whole picture.
    vec3 bloomColor = c;
    if (SceneMask > 0.5) {
        vec3 scene = texture(sceneSampler, texCoord).rgb;
        float grew = max(max(c.r - scene.r, c.g - scene.g), c.b - scene.b);
        // A small absolute slack absorbs framebuffer rounding and the bilinear taps of a 16F copy.
        // Real FX add far more than this; anything the scene already had cannot grow at all, so it
        // never contributes to the halo.
        // Do not amplify the entire bright background for a tiny difference at an edge.
        // Only the positive contribution can supply energy to the halo.
        bloomColor = max(c - scene, vec3(0.0)) * step(0.01, grew);
    }

    // soft-knee
    float knee = max(Threshold * Knee, 1e-5);
    float soft = clamp((br - Threshold + knee) / (2.0 * knee), 0.0, 1.0);
    float bright = max(br - Threshold, 0.0) + soft * soft * 2.0 * knee;
    fragColor = vec4(bloomColor * (bright / max(br, 1e-5)), 1.0);  // keep the bright area only
}
