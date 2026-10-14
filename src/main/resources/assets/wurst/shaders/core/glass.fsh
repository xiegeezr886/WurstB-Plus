#version 150
uniform sampler2D Scene;
uniform sampler2D Blurred;
uniform sampler2D Artwork;
uniform vec4 ArtworkUv;
uniform float UseArtwork;
uniform float Opacity;
uniform vec2 FrameSize;
uniform vec2 PanelSize;
uniform vec2 Mouse;
uniform float Radius;
uniform float Refraction;
uniform float Frost;
uniform float Tint;
uniform float Highlight;
uniform vec3 GlowColor;
uniform float GlowStrength;
uniform float PixelScale;
in vec2 localPos;
out vec4 fragColor;

float roundedBox(vec2 p) {
    vec2 q = abs(p - PanelSize * 0.5) - PanelSize * 0.5 + Radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - Radius;
}
vec2 safeUV(vec2 uv) {
    vec2 margin = 0.5 / FrameSize;
    return clamp(uv, margin, 1.0 - margin);
}
vec3 material(vec2 uv) {
    return mix(texture(Scene, safeUV(uv)).rgb, texture(Blurred, safeUV(uv)).rgb, Frost);
}
void main() {
    float d = roundedBox(localPos);
    float aa = max(fwidth(d), 0.35);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    // A soft, offset shadow is outside the glass, and never blurs its contents.
    float shadowD = roundedBox(localPos - vec2(0.0, 3.0));
    float shadow = exp(-max(shadowD, 0.0) / 3.2) * 0.19;
    if (coverage <= 0.001) {
        fragColor = vec4(vec3(0.025), shadow * Opacity * (1.0 - UseArtwork));
        return;
    }
    vec2 grad = vec2(roundedBox(localPos + vec2(0.5, 0.0)) - roundedBox(localPos - vec2(0.5, 0.0)),
                     roundedBox(localPos + vec2(0.0, 0.5)) - roundedBox(localPos - vec2(0.0, 0.5)));
    vec2 normal = grad / max(length(grad), 0.0001);
    float bevel = min(18.0, min(PanelSize.x, PanelSize.y) * 0.28);
    float edge = 1.0 - smoothstep(0.0, bevel, max(-d, 0.0));
    // Curved rim: displacement is strongest at the lip and vanishes into the flat centre.
    vec2 offset = -normal * edge * edge * Refraction * PixelScale / FrameSize;
    offset.y = -offset.y;
    vec2 uv = gl_FragCoord.xy / FrameSize;
    vec3 col;
    col.r = material(uv + offset * 1.045).r;
    col.g = material(uv + offset).g;
    col.b = material(uv + offset * 0.955).b;
    float vertical = clamp(localPos.y / PanelSize.y, 0.0, 1.0);
    // Surface tint is neutral; only the user's glow color should add a hue.
    col = mix(col, vec3(mix(0.93, 0.41, vertical)), Tint);
    float light = pow(max(dot(normal, normalize(vec2(-0.55, -0.85))), 0.0), 3.0);
    float opposite = pow(max(dot(normal, normalize(vec2(0.65, 0.75))), 0.0), 4.0);
    float rim = exp(-abs(d + 0.65) * 1.35);
    vec2 mouseDelta = (localPos - Mouse) / vec2(100.0, 85.0);
    float pointer = exp(-dot(mouseDelta, mouseDelta) * 1.8) * Highlight;
    col += GlowColor * GlowStrength * (edge * light * 0.12
            + rim * (0.12 + 0.40 * light + 0.23 * opposite + pointer * 0.38)
            + pointer * 0.035);
    col *= 1.0 - edge * (1.0 - light) * 0.075;
    if (UseArtwork > 0.5) {
        vec2 artUv = mix(ArtworkUv.xy, ArtworkUv.zw, clamp(localPos / PanelSize, 0.0, 1.0));
        vec4 art = texture(Artwork, artUv);
        float brightest = max(max(art.r, art.g), art.b);
        float darkest = min(min(art.r, art.g), art.b);
        float luminance = dot(art.rgb, vec3(0.299, 0.587, 0.114));
        float colored = smoothstep(0.10, 0.28, brightest - darkest);
        // Retain interior slots/diagrams, but let the smooth glass rim replace the
        // atlas's old black pixel border (including creative inventory tabs).
        vec2 inset = min(localPos, PanelSize - localPos);
        float interior = smoothstep(2.0, 5.0, min(inset.x, inset.y));
        // Some tab silhouettes end before their blit rectangle. Also fade embossing
        // near transparent artwork, rather than leaving a black line at that inset edge.
        vec2 stepUv = (ArtworkUv.zw - ArtworkUv.xy) * 4.0 / PanelSize;
        vec2 uvMin = min(ArtworkUv.xy, ArtworkUv.zw), uvMax = max(ArtworkUv.xy, ArtworkUv.zw);
        float silhouette = min(min(texture(Artwork, clamp(artUv + vec2(stepUv.x, 0), uvMin, uvMax)).a,
                                   texture(Artwork, clamp(artUv - vec2(stepUv.x, 0), uvMin, uvMax)).a),
                               min(texture(Artwork, clamp(artUv + vec2(0, stepUv.y), uvMin, uvMax)).a,
                                   texture(Artwork, clamp(artUv - vec2(0, stepUv.y), uvMin, uvMax)).a));
        interior *= silhouette;
        col += (luminance - 0.75) * 0.38 * interior;
        float ink = (1.0 - smoothstep(0.08, 0.27, luminance)) * 0.65;
        col = mix(col, art.rgb, max(colored, ink * interior));
        coverage *= art.a;
    }
    fragColor = vec4(clamp(col, 0.0, 1.0), coverage * Opacity);
}
