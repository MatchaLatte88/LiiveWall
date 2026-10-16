// Underwater aurora. Rendered at reduced resolution, so everything here is kept
// deliberately cheap: sines instead of heavy noise, few noise calls, no loops.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // aurora + light shaft strength
uniform float uParticles;  // 0..1 amount of floating particles
uniform vec3 uColDeep;     // background at the top
uniform vec3 uColShallow;  // background at the bottom
uniform vec3 uColA;        // primary aurora color
uniform vec3 uColB;        // secondary aurora color
uniform vec3 uColC;        // tertiary accent (used when multicolor is on)
uniform float uMulti;      // 0 = single look, 1 = multicolor

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

// One aurora curtain: a soft band along a slowly undulating line with faint vertical streaks.
vec3 ribbon(vec2 uv, float base, float phase, float width, vec3 color, float t) {
    float x = uv.x;
    float wave = 0.055 * sin(x * 3.1 + t * 0.23 + phase)
               + 0.035 * sin(x * 6.3 - t * 0.17 + phase * 1.7)
               + 0.060 * (noise(vec2(x * 1.6 + phase, t * 0.06)) - 0.5);
    float d = uv.y - (base + wave);
    // sharper edge below, long soft falloff above: gives the curtain look
    float k = mix(1.0 / (width * 0.35), 1.0 / (width * 1.6), step(0.0, d));
    float band = exp(-d * d * k * k);
    float streak = 0.55 + 0.45 * noise(vec2(x * 14.0 + phase * 3.0, t * 0.10));
    return color * band * streak;
}

// One layer of softly glowing particles drifting upward. Each grid cell holds at most
// one particle that stays inside its cell, so a single lookup per pixel is enough.
float particleLayer(vec2 p, float t, float scale, float rise, float seed, float density) {
    vec2 q = p * scale + vec2(seed * 7.0, -t * rise * scale);
    vec2 id = floor(q);
    vec2 f = fract(q) - 0.5;
    vec2 r = hash22(id + seed);
    vec2 off = (r - 0.5) * 0.4;
    off.x += 0.05 * sin(t * 0.4 + r.y * 40.0);
    float radius = mix(0.06, 0.18, fract(r.x * 7.31));
    float glow = smoothstep(radius, 0.0, length(f - off));
    float twinkle = 0.55 + 0.45 * sin(t * (0.6 + r.x) + r.y * 30.0);
    return glow * glow * twinkle * step(r.y, density);
}

void main() {
    vec2 uv = vUv;
    float t = uTime;
    vec2 p = vec2(uv.x * uAspect, uv.y);

    // water column
    vec3 col = mix(uColShallow, uColDeep, smoothstep(0.0, 1.0, uv.y));

    // aurora curtains
    // multicolor: three different ribbon colors instead of a blend of two
    vec3 c2 = mix(mix(uColA, uColB, 0.5), uColB, uMulti);
    vec3 c3 = mix(uColB, uColC, uMulti);
    vec3 aur = ribbon(uv, 0.64, 0.0, 0.10, uColA, t)
             + ribbon(uv, 0.50, 2.1, 0.12, c2, t * 0.9) * 0.8
             + ribbon(uv, 0.36, 4.3, 0.14, c3, t * 1.1) * 0.65;
    col += aur * 0.34 * uIntensity;

    // faint light shafts
    float shaft = pow(max(0.0, sin((uv.x + uv.y * 0.35) * 9.0 + t * 0.07)), 4.0)
                + 0.7 * pow(max(0.0, sin((uv.x + uv.y * 0.30) * 5.3 - t * 0.05)), 5.0);
    col += uColA * shaft * 0.06 * uIntensity * smoothstep(0.2, 1.0, uv.y);

    // floating particles
    float dots = particleLayer(p, t, 7.0, 0.020, 1.0, uParticles * 0.55)
               + 0.8 * particleLayer(p, t, 13.0, 0.012, 2.0, uParticles * 0.45);
    col += mix(vec3(1.0), uColA, 0.45) * dots * 0.9;

    // soft shoulder instead of a hard clip
    vec3 over = max(col - 0.75, 0.0);
    col = min(col, 0.75) + 0.25 * (1.0 - exp(-over * 4.0));

    // gentle vignette
    vec2 c = uv - 0.5;
    col *= 1.0 - 0.60 * dot(c, c);

    gl_FragColor = vec4(col, 1.0);
}
