#version 150
uniform sampler2D Source;
uniform vec2 Direction;
uniform vec2 SourceTexel;
uniform float Downsample;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    if (Downsample > 0.5) {
        vec2 d = SourceTexel * 0.75;
        vec3 color = texture(Source, texCoord + vec2(-d.x,-d.y)).rgb;
        color += texture(Source, texCoord + vec2(d.x,-d.y)).rgb;
        color += texture(Source, texCoord + vec2(-d.x,d.y)).rgb;
        color += texture(Source, texCoord + d).rgb;
        fragColor = vec4(color * 0.25,1.0);
        return;
    }
    vec3 color = vec3(0.0);
    float total = 0.0;
    for (int i=-16; i<=16; i++) {
        float t = float(i)/16.0;
        float weight = exp(-4.5*t*t);
        color += texture(Source, clamp(texCoord + Direction*t,SourceTexel*0.5,1.0-SourceTexel*0.5)).rgb * weight;
        total += weight;
    }
    fragColor = vec4(color / total, 1.0);
}
