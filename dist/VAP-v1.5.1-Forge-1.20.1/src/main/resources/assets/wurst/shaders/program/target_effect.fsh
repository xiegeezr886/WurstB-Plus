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
    }else
    {
        float wave = sin(texCoord.y * 90.0 + Time * 12.0) * 0.5 + 0.5;
        float smoke = max(around * (0.25 + wave * 0.45), center * 0.2);
        fragColor = vec4(mix(PRIMARY, SECONDARY, wave), smoke);
    }
}
