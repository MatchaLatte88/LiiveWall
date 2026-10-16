// Bokeh: large soft out-of-focus discs drifting slowly upward, in three depth layers.
// Closer layers are bigger, brighter and a little faster.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // disc brightness
uniform float uParticles;  // 0..1 amount of discs
uniform vec3 uColDeep;
uniform vec3 uColMid;
uniform vec3 uColShallow;
uniform vec3 uColGlow;
uniform vec3 uColA;
uniform vec3 uColB;
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

// Vertical gradient measured from the reference screenshot (see gradient.frag).
vec3 baseGradient(vec2 uv) {
    float s = 1.0 - uv.y;
    vec3 c = mix(uColDeep, uColMid, clamp(s / 0.30, 0.0, 1.0));
    c = mix(c, uColShallow, clamp((s - 0.30) / 0.42, 0.0, 1.0));
    vec2 g = vec2((uv.x - 0.5) / 0.234, (s - 0.86) / 0.203);
    return c + uColGlow * exp(-0.5 * dot(g, g));
}

// One layer of discs. Every grid cell holds at most one disc that stays inside its cell,
// so a single lookup per pixel is enough. size is the disc radius in cell units.
vec3 bokehLayer(vec2 p, float t, float scale, float rise, float seed, float density, float size) {
    vec2 q = p * scale + vec2(seed * 11.0, -t * rise * scale);
    vec2 id = floor(q);
    vec2 f = fract(q) - 0.5;
    vec2 r = hash22(id + seed * 3.7);
    float present = step(r.x, density);
    vec2 off = (r - 0.5) * 0.36;
    off += 0.04 * vec2(sin(t * 0.21 + r.y * 31.0), cos(t * 0.17 + r.x * 27.0));
    float d = length(f - off);
    float rad = size * mix(0.55, 1.0, hash12(id + seed * 5.3));
    float body = smoothstep(rad, rad * 0.82, d);                 // soft disc
    float rim = smoothstep(rad * 0.62, rad * 0.97, d) * body;    // slightly brighter edge
    float breathe = 0.6 + 0.4 * sin(t * (0.25 + 0.3 * r.y) + r.x * 40.0);
    float amp = (0.65 * body + 0.35 * rim) * breathe * present;
    float pick = hash12(id + seed * 9.1);
    vec3 spread = pick < 0.34 ? uColA : (pick < 0.67 ? uColB : uColC);
    vec3 tint = mix(mix(mix(uColA, uColB, pick), spread, uMulti), vec3(1.0), 0.25);
    return tint * amp;
}

void main() {
    vec2 uv = vUv;
    float t = uTime;
    float k = clamp(uIntensity, 0.0, 1.5);
    vec2 p = vec2(uv.x * uAspect, uv.y);

    vec3 col = baseGradient(uv);
    vec3 discs = 0.30 * bokehLayer(p, t, 7.0, 0.010, 1.0, uParticles * 0.9, 0.22)
               + 0.34 * bokehLayer(p, t, 4.5, 0.016, 2.0, uParticles * 0.7, 0.30)
               + 0.38 * bokehLayer(p, t, 2.8, 0.024, 3.0, uParticles * 0.45, 0.34);
    col += discs * k;

    vec3 over = max(col - 0.75, 0.0);
    col = min(col, 0.75) + 0.25 * (1.0 - exp(-over * 4.0));
    vec2 c = uv - 0.5;
    col *= 1.0 - 0.35 * dot(c, c);

    gl_FragColor = vec4(col, 1.0);
}
