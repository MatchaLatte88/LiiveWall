// Ink: clouds of color that slowly curl and fold into each other, like ink dropped into water.
// A warped noise field decides where the ink lies; thin bright veins mark the edges of the folds.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // ink density
uniform vec3 uColDeep;     // gradient at the top
uniform vec3 uColMid;      // gradient at 30 % from the top
uniform vec3 uColShallow;  // gradient at the bottom
uniform vec3 uColGlow;     // additive lift in the lower middle
uniform vec3 uColA;        // primary ink
uniform vec3 uColB;        // secondary ink
uniform vec3 uColC;        // tertiary ink (used when multicolor is on)
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

// Lattice hash in exact integer arithmetic (all intermediate values stay below 2^24). With the
// usual fract()-based hash this noise showed hard-edged rectangles once the lattice coordinates
// grew past a few dozen; that is why the octave offsets in fbm() are kept small as well.
float permute(float x) {
    return mod((x * 34.0 + 1.0) * x, 289.0);
}

float hash12(vec2 p) {
    vec2 q = mod(p, 289.0);
    return permute(permute(q.x) + q.y) / 289.0;
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Three octaves, rescaled to roughly 0..1.
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 0.9);
        a *= 0.5;
    }
    return v / 0.875;
}

void main() {
    vec2 uv = vUv;
    float k = clamp(uIntensity, 0.0, 1.5);
    float t = uTime * 0.04;
    vec2 p = vec2(uv.x * uAspect, uv.y) * 3.4;

    // one level of domain warping: q bends the space f is read from
    vec2 q = vec2(fbm(p + vec2(0.0, t)), fbm(p + vec2(5.2, 1.3) - vec2(t, 0.0)));
    float f = fbm(p + 2.6 * q + vec2(t * 0.5, 0.0));

    vec3 col = baseGradient(uv);

    // multicolor: the warp field itself selects between the inks
    vec3 inkDuo = mix(uColA, uColB, smoothstep(0.30, 0.70, q.x));
    vec3 inkTrio = mix(inkDuo, uColC, smoothstep(0.35, 0.70, q.y));
    vec3 ink = mix(inkDuo, inkTrio, uMulti);

    float cloud = smoothstep(0.36, 0.72, f);
    col = mix(col, ink * 0.60, cloud * 0.60 * min(k, 1.0));
    col += ink * cloud * cloud * 0.10 * k;

    // thin veins along the edges of the folds
    float vein = exp(-pow((f - 0.54) * 14.0, 2.0));
    col += mix(ink, vec3(1.0), 0.35) * vein * 0.18 * k;

    // soft shoulder instead of a hard clip, gentle vignette
    vec3 over = max(col - 0.75, 0.0);
    col = min(col, 0.75) + 0.25 * (1.0 - exp(-over * 4.0));
    vec2 c = uv - 0.5;
    col *= 1.0 - 0.35 * dot(c, c);

    gl_FragColor = vec4(col, 1.0);
}
