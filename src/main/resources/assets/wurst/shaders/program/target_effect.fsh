#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform int Mode;

in vec2 texCoord;
out vec4 fragColor;

const vec3 PRIMARY = vec3(0.0, 0.388, 0.4);
const vec3 SECONDARY = vec3(0.25, 0.9, 0.84);

float alphaAt(vec2 offset)
{
    return texture(DiffuseSampler, texCoord + offset / InSize).a;
}

// 一圈 8 个方向的采样取最大值。8 个方向是 2*pi/8 = 0.7853981634 弧度一步。
float ringAlpha(float radius)
{
    float best = 0.0;
    for(int i = 0; i < 8; i++)
    {
        float angle = float(i) * 0.7853981634;
        best = max(best, alphaAt(vec2(cos(angle), sin(angle)) * radius));
    }
    return best;
}

void main()
{
    float center = alphaAt(vec2(0.0));
    float around = 0.0;
    for(int x = -2; x <= 2; x++)
        for(int y = -2; y <= 2; y++)
            around = max(around, alphaAt(vec2(x, y)));

    if(Mode == 0)
    {
        float edge = max(around - center, 0.0);
        fragColor = vec4(PRIMARY, edge);
    }else if(Mode == 1)
    {
        float pulse = 0.55 + 0.35 * sin(Time * 6.283185307);
        fragColor = vec4(PRIMARY, center * pulse);
    }else if(Mode == 2)
    {
        vec3 color = mix(PRIMARY, SECONDARY,
            clamp(1.0 - texCoord.y, 0.0, 1.0));
        fragColor = vec4(color, center * 0.8);
    }else if(Mode == 3)
    {
        float wave = sin(texCoord.y * 90.0 + Time * 12.0) * 0.5 + 0.5;
        float smoke = max(around * (0.25 + wave * 0.45), center * 0.2);
        fragColor = vec4(mix(PRIMARY, SECONDARY, wave), smoke);
    }else if(Mode == 4)
    {
        // bloom：稀疏环形采样代替稠密卷积核。半径 12 的稠密核是 25x25 =
        // 625 次取样/像素，三圈各 8 次共 24 次（比 Mode 0-2 的 25 次还便宜）
        // 就能给出很宽的光晕。半径越大权重越低，所以是衰减的光晕而不是实心块。
        float glow = center;
        glow = max(glow, ringAlpha(4.0) * 0.75);
        glow = max(glow, ringAlpha(8.0) * 0.45);
        glow = max(glow, ringAlpha(12.0) * 0.25);
        fragColor = vec4(mix(PRIMARY, SECONDARY, glow), glow * 0.85);
    }else
    {
        // 未知模式什么都不画，而不是默默画成上一个模式——写错 Mode 时
        // 「没有效果」比「看起来像别的效果」好排查。
        fragColor = vec4(0.0);
    }
}
