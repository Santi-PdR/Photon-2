// Photon engine uniforms exposed to custom shaders on Forge 1.20.1.
// Minecraft 1.20.1 uses forward-Z with an OpenGL [-1, 1] projection depth range; U_DepthParams
// maps sampled depth to NDC with (2, -1), then carries the projection's near/far distances.
uniform mat4 U_InverseProjectionMatrix;
uniform mat4 U_InverseViewMatrix;
uniform vec4 U_CameraPosition;
uniform vec4 U_ViewPort;
uniform vec4 U_DepthParams;

/** Convert a sampled depth-buffer value to normalized-device-coordinate Z. */
float photon_ndc_depth(float depth) {
    return depth * U_DepthParams.x + U_DepthParams.y;
}

/** Return positive view-space distance reconstructed from a depth-buffer value. */
float photon_eye_depth(float depth) {
    vec4 view = U_InverseProjectionMatrix * vec4(0.0, 0.0, photon_ndc_depth(depth), 1.0);
    return -view.z / view.w;
}
