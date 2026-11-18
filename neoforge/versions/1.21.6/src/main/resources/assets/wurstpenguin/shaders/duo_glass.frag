#version 150
// Adapted from WindowsDuo FS_DUO (MIT), commit 69dab2c.
// See assets/wurstpenguin/windowsduo/LICENSE and NOTICE.txt.
uniform sampler2D uTex;
uniform vec4 uRect;
uniform vec2 uRes;
uniform float uFold;
in vec2 uv;
out vec4 fragColor;
const float GOLDEN = 2.39996322972865332;
float hash21(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    if(uFold < 0) { fragColor = texture(uTex, uv); return; }
    vec2 local = (uv - uRect.xy) / uRect.zw;
    if(any(lessThan(local, vec2(0))) || any(greaterThan(local, vec2(1)))) discard;
    vec2 p = local * uRes;
    float tilt = uFold * 1.535889742;
    vec3 glass = vec3(p.x, p.y * cos(tilt), p.y * sin(tilt));
    vec3 eye = vec3(uRes * .5, uRes.y * 2.0);
    float depth = eye.z - glass.z;
    if(depth <= .001) { fragColor = vec4(0); return; }
    vec2 hit = eye.xy + (glass.xy - eye.xy) * (eye.z / depth);
    float radius = .42 * glass.z;
    if(any(lessThan(hit, vec2(-radius))) || any(greaterThan(hit, uRes + radius))) {
        fragColor = vec4(0); return;
    }
    float attenuation = max(1.0 - .001 * radius, 0.0);
    float lod = clamp(log2(max(radius, 1.0) / 16.0), 0.0, 6.0);
    float effectiveRadius = radius / exp2(lod);
    int taps = radius < .5 ? 1 : int(clamp(effectiveRadius, 6.0, 12.0));
    float rotation = hash21(gl_FragCoord.xy) * 6.28318530718;
    vec2 footprint = (radius + 1.0) / uRes;
    vec4 sum = vec4(0);
    for(int i = 0; i < 12; i++) {
        if(i >= taps) break;
        float r = taps == 1 ? 0 : effectiveRadius * sqrt((float(i) + .5) / float(taps));
        float angle = float(i) * GOLDEN + rotation;
        vec2 sampleUV = (hit + r * vec2(cos(angle), sin(angle))) / uRes;
        vec2 coverage = smoothstep(vec2(0), footprint, sampleUV)
            * (1.0 - smoothstep(1.0 - footprint, vec2(1), sampleUV));
        // Render-target textures are bottom-first; unlike desktop screenshots, no Y flip.
        sum += textureLod(uTex, uRect.xy + sampleUV * uRect.zw, lod) * coverage.x * coverage.y;
    }
    vec4 color = sum / float(taps);
    color.rgb *= attenuation;
    // Keep the scene visible beyond the transparent GUI, and remove the hinge at full close.
    fragColor = color * (1.0 - smoothstep(.7, 1.0, uFold));
}
