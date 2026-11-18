#version 150
// Flowing tint adapted from moon-rebase flowing_glow.frag (Apache-2.0).
uniform sampler2D glowTexture;
uniform sampler2D maskTexture;
uniform vec2 extent;
uniform vec2 cardSize;
uniform vec2 direction;
uniform float radius;
uniform float feather;
uniform int mode;
in vec2 uv;
out vec4 fragColor;
const vec3 glowColor = vec3(181.0, 190.0, 240.0) / 255.0;

void main() {
    if(mode == 0) {
        vec2 p = vec2(uv.x, 1.0-uv.y) * extent;
        vec2 q = abs(p - extent * 0.5) - cardSize * 0.5 + radius;
        float d = length(max(q, 0.0)) + min(max(q.x,q.y),0.0) - radius;
        float a = exp(-0.5 * pow(max(d,0.0) / feather,2.0)) * smoothstep(-0.7,0.7,d) * 0.5;
        fragColor = vec4(glowColor, a);
        return;
    }
    float a = texture(glowTexture, uv).a * 0.227027;
    a += (texture(glowTexture,uv+direction*1.384615).a + texture(glowTexture,uv-direction*1.384615).a) * 0.316216;
    a += (texture(glowTexture,uv+direction*3.230769).a + texture(glowTexture,uv-direction*3.230769).a) * 0.070270;
    if(mode == 1) { fragColor=vec4(1.0,1.0,1.0,a); return; }
    float mask = texture(maskTexture,uv).a;
    fragColor = vec4(glowColor, min(1.0,a*2.4)*(1.0-mask));
}
