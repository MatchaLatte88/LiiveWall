// Waves: five layered silhouettes, far ones light and high, near ones dark and low.
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

float edgeAt(float x, float fi, float t) {
    float base = 0.60 - 0.115 * fi;
    float amp = 0.030 + 0.006 * fi;
    float f = 4.0 + 0.9 * fi;
    float s = 0.10 + 0.025 * fi;
    return base
        + amp * sin(x * f + t * s + fi * 1.9)
        + amp * 0.45 * sin(x * f * 2.1 - t * s * 1.6 + fi * 0.7);
}

// Smooth A -> B -> C -> A cycle for u in 0..3.
vec3 cycle3(float u) {
    vec3 c = mix(uColA, uColB, smoothstep(0.0, 1.0, u));
    c = mix(c, uColC, smoothstep(1.0, 2.0, u));
    return mix(c, uColA, smoothstep(2.0, 3.0, u));
}

void main() {
    vec2 uv = vUv;
    float t = uTime;

    vec3 col = baseGradient(uv);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float edge = edgeAt(uv.x, fi, t);
        float m = smoothstep(0.0, 0.010, edge - uv.y);
        vec3 layer = mix(uColShallow, uColDeep, fi / 4.0);
        layer = mix(layer, uColB, 0.12);
        layer *= 0.72 + 0.28 * smoothstep(edge - 0.28, edge, uv.y);   // lighter near the crest
        col = mix(col, layer, m);
        float rim = exp(-pow((uv.y - edge) * 55.0, 2.0));              // thin glow on the crest
        vec3 rimColor = mix(uColA, cycle3(fi * 0.6), uMulti);
        col += rimColor * rim * 0.10 * clamp(uIntensity, 0.0, 1.5) * (1.0 - fi * 0.12);
    }

    gl_FragColor = vec4(col, 1.0);
}
