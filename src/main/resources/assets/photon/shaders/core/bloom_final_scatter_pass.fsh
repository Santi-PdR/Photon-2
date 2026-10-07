#version 150

uniform sampler2D inputA;
uniform sampler2D inputB;

uniform float Threshold;      // luma threhold
uniform float Knee;     // [0,1]：soft-knee
uniform float BloomIntensive;

in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec3 lowRes = texture(inputA, texCoord).rgb;
    vec4 source = texture(inputB, texCoord);
    vec3 highRes = source.rgb;

    vec3 c = highRes;
    float br = dot(c, LUMA);
    float knee = Threshold * Knee;
    float soft = clamp((br - Threshold + knee) / max(2.0 * knee, 1e-5), 0.0, 1.0);
    float bright = max(br - Threshold, 0.0) + soft * soft * 2.0 * knee;
    vec3 hightlight = c * (bright / max(br, 1e-5));

    lowRes = lowRes + highRes - hightlight;
    fragColor = vec4(mix(highRes, lowRes, BloomIntensive), source.a);
}
