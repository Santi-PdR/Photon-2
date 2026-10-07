#version 150

// Depth-of-field composite: autofocus on the depth under Center, blend toward the blurred
// image by linear-depth distance / FocusRange (blocks). Near/Far mirror the projection planes.

uniform sampler2D DiffuseSampler;
uniform sampler2D BlurSampler;
uniform sampler2D DepthSampler;
uniform vec2 Center;
uniform float FocusRange;
uniform float Near;
uniform float Far;
uniform mat4 ProjMat;

in vec2 texCoord;
out vec4 fragColor;

float linearize(float depth) {
    // Forge 1.20.1 uses forward-Z: perspective depth is nonlinear, while orthographic
    // depth is linear. ProjMat[2][3] is -1 for perspective and 0 for orthographic.
    if (abs(ProjMat[2][3]) < 0.5) {
        return mix(Near, Far, depth);
    }
    return (Near * Far) / (Far - depth * (Far - Near));
}

void main() {
    float z = linearize(texture(DepthSampler, texCoord).r);
    float focus = linearize(texture(DepthSampler, Center).r);
    float coc = clamp(abs(z - focus) / max(FocusRange, 0.01), 0.0, 1.0);
    fragColor = mix(texture(DiffuseSampler, texCoord), texture(BlurSampler, texCoord), coc);
}
