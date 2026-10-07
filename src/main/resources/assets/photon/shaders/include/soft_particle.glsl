uniform sampler2D SamplerSceneDepth;
uniform mat4 ProjMat;

float photon_eye_depth(float depth) {
    float clipDepth = depth * 2.0 - 1.0;
    // Solve the projection's clip-Z / clip-W relation for view-space Z. The shorter
    // perspective-only formula fails for orthographic cameras because clip-W is constant.
    float viewZ = (ProjMat[3][2] - clipDepth * ProjMat[3][3])
            / (clipDepth * ProjMat[2][3] - ProjMat[2][2]);
    return abs(viewZ);
}

vec4 photon_soft_particle(vec4 color, vec4 params) {
    if (params.w < 0.5) return color;
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(SamplerSceneDepth, 0));
    float sceneEye = photon_eye_depth(texture(SamplerSceneDepth, uv).r);
    float fragmentEye = photon_eye_depth(gl_FragCoord.z);
    float fade = clamp((sceneEye - fragmentEye) / max(params.x, 1e-5), 0.0, 1.0);
    fade = pow(fade, max(params.y, 1e-5));
    return params.z > 0.5 ? vec4(color.rgb, color.a * fade) : color * fade;
}
