// Moire: sets of fine concentric rings whose centres wander slowly. Where the rings of two sets
// line up they form bright, broad fringes; where they interleave the picture turns fine and
// quiet. The rings are soft sine bands, not hard lines, so they hold up at a low render scale.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // line strength
uniform vec3 uColDeep;     // gradient at the top
uniform vec3 uColMid;      // gradient at 30 % from the top
uniform vec3 uColShallow;  // gradient at the bottom
uniform vec3 uColGlow;     // additive lift in the lower middle
uniform vec3 uColA;        // first set of rings
uniform vec3 uColB;        // second set of rings
uniform vec3 uColC;        // third set of rings (used when multicolor is on)
uniform float uMulti;      // 0 = single look, 1 = multicolor

// Vertical gradient measured from the reference: deep -> mid over the top 30 %, mid -> shallow
// until 72 %, flat below. Plus a soft Gaussian lift centred at 50 % / 86 % from the top.
vec3 baseGradient(vec2 uv) {
    float s = 1.0 - uv.y;
    vec3 c = mix(uColDeep, uColMid, clamp(s / 0.30, 0.0, 1.0));
    c = mix(c, uColShallow, clamp((s - 0.30) / 0.42, 0.0, 1.0));
    vec2 g = vec2((uv.x - 0.5) / 0.234, (s - 0.86) / 0.203);
    return c + uColGlow * exp(-0.5 * dot(g, g));
}

// One set of rings around [centre] with the given period (screen-height units); [drift] makes
// them slowly travel outwards (positive) or inwards (negative). Returns 0..1.
float rings(vec2 p, vec2 centre, float period, float drift, float t) {
    float phase = length(p - centre) / period - t * drift;
    return pow(0.5 + 0.5 * cos(6.2831853 * phase), 3.0);
}

void main() {
    vec2 uv = vUv;
    float t = uTime;
    float k = clamp(uIntensity, 0.0, 1.5);
    vec2 p = vec2(uv.x * uAspect, uv.y);

    // the centres wander on slow, unrelated paths, so the fringes keep changing shape
    vec2 c1 = vec2(uAspect * (0.5 + 0.34 * sin(t * 0.031)), 0.5 + 0.26 * cos(t * 0.023 + 1.0));
    vec2 c2 = vec2(uAspect * (0.5 + 0.34 * sin(t * 0.027 + 2.6)), 0.5 + 0.26 * cos(t * 0.034 + 3.7));
    vec2 c3 = vec2(uAspect * (0.5 + 0.34 * sin(t * 0.019 + 4.9)), 0.5 + 0.26 * cos(t * 0.029 + 0.4));

    float la = rings(p, c1, 0.028, 0.05, t);
    float lb = rings(p, c2, 0.028, -0.04, t);
    float lc = rings(p, c3, 0.028, 0.03, t) * uMulti;

    vec3 col = baseGradient(uv);
    col += (uColA * la + uColB * lb + uColC * lc) * 0.085 * k;
    // where two rings meet the light adds up: this is what draws the fringes
    col += mix(uColA, uColB, 0.5) * la * lb * 0.14 * k;
    col += mix(uColB, uColC, 0.5) * lb * lc * 0.14 * k;
    col += mix(uColA, uColC, 0.5) * la * lc * 0.14 * k;

    // soft shoulder instead of a hard clip, gentle vignette
    vec3 over = max(col - 0.75, 0.0);
    col = min(col, 0.75) + 0.25 * (1.0 - exp(-over * 4.0));
    vec2 c = uv - 0.5;
    col *= 1.0 - 0.35 * dot(c, c);

    gl_FragColor = vec4(col, 1.0);
}
