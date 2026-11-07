#version 150

uniform sampler2D inputA;
uniform sampler2D inputB;

uniform float BloomIntensive;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(inputB, texCoord);
    vec3 blurredHighlights = texture(inputA, texCoord).rgb;
    // Preserve the scene exactly and add only the blurred highlight buffer. The previous scatter
    // mix replaced scene colour at intensity 1, which made ordinary FX and their surroundings
    // appear washed out instead of glowing.
    fragColor = vec4(scene.rgb + blurredHighlights * clamp(BloomIntensive, 0.0, 2.0), scene.a);
}

