#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform vec2 Frequency;
uniform vec2 WobbleAmount;

in vec2 texCoord;

out vec4 fragColor;

vec3 hue(float value)
{
    float red = abs(value * 6.0 - 3.0) - 1.0;
    float green = 2.0 - abs(value * 6.0 - 2.0);
    float blue = 2.0 - abs(value * 6.0 - 4.0);
    return clamp(vec3(red, green, blue), 0.0, 1.0);
}

vec3 hsvToRgb(vec3 hsv)
{
    return ((hue(hsv.x) - 1.0) * hsv.y + 1.0) * hsv.z;
}

vec3 rgbToHsv(vec3 rgb)
{
    vec3 hsv = vec3(0.0);
    hsv.z = max(rgb.r, max(rgb.g, rgb.b));
    float minimum = min(rgb.r, min(rgb.g, rgb.b));
    float chroma = hsv.z - minimum;
    if(chroma != 0.0)
    {
        hsv.y = chroma / hsv.z;
        vec3 delta = (hsv.z - rgb) / chroma;
        delta.rgb -= delta.brg;
        delta.rg += vec2(2.0, 4.0);
        if(rgb.r >= hsv.z)
            hsv.x = delta.b;
        else if(rgb.g >= hsv.z)
            hsv.x = delta.r;
        else
            hsv.x = delta.g;
        hsv.x = fract(hsv.x / 6.0);
    }
    return hsv;
}

void main()
{
    float phase = Time * 6.283185307;
    float xOffset = sin(texCoord.y * Frequency.x + phase) * WobbleAmount.x;
    float yOffset = cos(texCoord.x * Frequency.y + phase) * WobbleAmount.y;
    vec3 color = texture(DiffuseSampler,
        texCoord + vec2(xOffset, yOffset)).rgb;
    vec3 hsv = rgbToHsv(color);
    hsv.x = fract(hsv.x + Time);
    fragColor = vec4(hsvToRgb(hsv), 1.0);
}
