#version 150
// Rise bloom.frag, adapted from GLSL 120 to Minecraft 1.20.1's core profile.
uniform sampler2D u_diffuse_sampler, u_other_sampler;
uniform vec2 u_texel_size, u_direction;
uniform float u_radius, u_kernel[24];
uniform bool u_premultiplied;
in vec2 uv;
out vec4 fragColor;

vec4 sampleSource(vec2 at) {
    vec4 color = texture(u_diffuse_sampler, at);
    if (u_premultiplied && color.a > 0.0) color.rgb /= color.a;
    return color;
}

void main() {
    if (u_direction.x == 0.0 && texture(u_other_sampler, uv).a > 0.0) discard;
    vec4 pixel_color = sampleSource(uv) * u_kernel[0];
    pixel_color.rgb *= pixel_color.a;
    for (int f = 0; f <= int(u_radius); f++) {
        vec2 offset = (u_texel_size * u_direction) * float(f);
        vec4 left = sampleSource(uv - offset);
        vec4 right = sampleSource(uv + offset);
        left.rgb *= left.a;
        right.rgb *= right.a;
        pixel_color += (left + right) * u_kernel[f];
    }
    // Empty pixels must stay transparent instead of producing NaN.
    if (pixel_color.a <= 0.0) { fragColor = vec4(0.0); return; }
    pixel_color.rgb /= pixel_color.a;
    fragColor = pixel_color;
}
