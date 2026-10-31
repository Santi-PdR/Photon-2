#version 150

#moj_import <fog.glsl>

uniform sampler2D ShieldTexture;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec4 FrontHDR;
uniform vec4 BackHDR;
uniform float Power;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in  vec3  vViewPos;
in  vec3  vViewNormal;

out vec4 fragColor;

// ------------------------------------------------------------
// Fresnel effect (Schlick-like) utility
//  ‣  Normal   : surface normal   (any space, MUST be normalized later)
//  ‣  ViewDir  : direction TO camera (same space as Normal)
//  ‣  power    : controls edge fall-off
//  ←  return   : Fresnel term (0‥1)
// ------------------------------------------------------------
float FresnelEffect(vec3 Normal, vec3 ViewDir, float power)
{
    float cosTheta = clamp(dot(normalize(Normal), normalize(ViewDir)), 0.0, 1.0);
    return pow(1.0 - cosTheta, power);
}

void main() {
    vec4 color = texture(ShieldTexture, texCoord0) * vertexColor * ColorModulator;
    vec3 frontColor = FrontHDR.rgb * FrontHDR.a;
    vec3 backColor = BackHDR.rgb * BackHDR.a;


    vec3 viewDir = normalize(-vViewPos);
    float fres = FresnelEffect(vViewNormal, viewDir, Power);

    if (gl_FrontFacing) {
        color.rgb *= frontColor * (fres * 1.2 + 0.3);
    } else {
        color.rgb *= backColor;
    }


    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
