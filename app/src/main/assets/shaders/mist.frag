// Mist: a few large soft color blobs drifting on slow Lissajous paths.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // style strength
uniform vec3 uColDeep;     // gradient at the top
uniform vec3 uColMid;      // gradient at 30 % from the top
uniform vec3 uColShallow;  // gradient at the bottom
uniform vec3 uColGlow;     // additive lift in the lower middle
uniform vec3 uColA;        // primary accent
uniform vec3 uColB;        // secondary accent
uniform vec3 uColC;        // tertiary accent (used when multicolor is on)
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

vec3 blob(vec3 col, vec2 p, float fi, vec3 tint, float t) {
    vec2 c = vec2(
        uAspect * (0.5 + 0.36 * sin(t * (0.09 + 0.021 * fi) + fi * 2.4)),
        0.5 + 0.34 * sin(t * (0.07 + 0.017 * fi) + fi * 1.3 + 1.0));
    vec2 d = p - c;
    float r = 0.36 - 0.025 * fi;                       // radius in screen-height units
    float w = exp(-dot(d, d) / (r * r));
    return mix(col, tint, w * 0.6 * clamp(uIntensity, 0.0, 1.5));
}

void main() {
    vec2 uv = vUv;
    float t = uTime;
    vec2 p = vec2(uv.x * uAspect, uv.y);

    vec3 col = baseGradient(uv);
    col = blob(col, p, 0.0, uColA * 0.65, t);
    col = blob(col, p, 1.0, uColB * 0.65, t);
    col = blob(col, p, 2.0, mix(mix(uColA, uColB, 0.5), uColC, uMulti) * 0.65, t);
    col = blob(col, p, 3.0, uColShallow * 1.3, t);

    gl_FragColor = vec4(col, 1.0);
}
