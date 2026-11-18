#version 150
// Adapted from NeoRaven, GPL-3.0; see META-INF/licenses/neoraven/LOADING-NOTICE.txt.

// Material 3 Expressive loading indicator.
//
// The M3E indicator is not an arc: a filled shape continuously morphs between the M3 forms
// (circle -> nine-sided "cookie" -> rounded square) while it rotates. Rendering it as a signed
// distance field keeps the silhouette clean at every intermediate shape - the morph never shows
// polygon facets because the SDF is sampled per pixel instead of being built from vertices.
//
// Two polygon fields (A and B) are blended instead of interpolating a single non-integer side
// count: a fractional side count leaves one wedge that does not close, which shows up as a seam
// running across the shape for the whole morph.
//
// localPos is the fragment's offset from the indicator centre in GUI pixels (see onyx_shape.vsh),
// so every uniform below is expressed in the same units as the UI.

uniform vec4 vertexColor;
out vec4 fragColor;
in vec2 localPos;

uniform float uPixel;      // one screen pixel in GUI units, for the minimum edge feather
uniform float uSidesA;     // side count of form A
uniform float uApothemA;   // centre-to-edge distance of form A
uniform float uRoundA;     // corner rounding (dilation radius) of form A
uniform float uSidesB;     // side count of form B
uniform float uApothemB;
uniform float uRoundB;
uniform float uMorph;      // 0 = form A, 1 = form B
uniform float uCircle;     // 1 = blend in the pure circle form
uniform float uRadius;     // circle radius
uniform float uRotation;   // spin, in radians
uniform float uScale;      // breathing scale applied to the whole shape

/**
 * Signed distance to a regular polygon with `sides` edges, `apothem` from the centre to an edge and
 * `round` on top of that.
 *
 * The boundary is evaluated in polar form: inside one wedge the polygon's radius is
 * {@code apothem / cos(u)} (u measured from the edge normal), so adding {@code round} to that radius
 * moves the flat edges out by exactly `round` and turns every corner into a circular arc - which is
 * what makes the square form a rounded square instead of a sharp diamond. Offsetting the edge
 * planes would only inflate the polygon and keep the corners sharp.
 */
float polygonDistance(vec2 p, float sides, float apothem, float round) {
    float r = length(p);
    float segment = 6.28318530718 / max(sides, 3.0);
    float angle = atan(p.y, p.x) + uRotation;
    float u = mod(angle + segment * 0.5, segment) - segment * 0.5;
    float boundary = apothem / max(cos(u), 0.001) + round;
    return r - boundary;
}

void main() {
    vec2 p = localPos / max(uScale, 0.01);
    float a = polygonDistance(p, uSidesA, uApothemA, uRoundA);
    float b = polygonDistance(p, uSidesB, uApothemB, uRoundB);
    float polygon = mix(a, b, clamp(uMorph, 0.0, 1.0));
    float circle = length(p) - uRadius;
    float distance = mix(polygon, circle, clamp(uCircle, 0.0, 1.0));

    // A sub-pixel feather keeps every intermediate shape smooth; uPixel guarantees at least a
    // sliver of ramp even where the derivative is unavailable.
    float edge = max(fwidth(distance), uPixel) * 0.75 + 0.0005;
    float alpha = 1.0 - smoothstep(-edge, edge, distance);
    if (alpha <= 0.002) {
        discard;
    }

    vec4 color = vertexColor;
    color.a *= alpha;
    fragColor = color;
}
