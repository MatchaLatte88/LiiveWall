// Rays: soft beams of light fanning out from above the top edge through hazy water. They sway
// and breathe slowly, and dust drifts down through them, brightest where a beam passes.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // beam strength
uniform float uParticles;  // 0..1 amount of floating dust
uniform vec3 uColDeep;     // gradient at the top
uniform vec3 uColMid;      // gradient at 30 % from the top
uniform vec3 uColShallow;  // gradient at the bottom
uniform vec3 uColGlow;     // additive lift in the lower middle
uniform vec3 uColA;        // light color of the beams
uniform vec3 uColB;        // second beam color
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

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// One layer of softly glowing particles (one particle per grid cell); a negative rise lets
// them sink.
float particleLayer(vec2 p, float t, float scale, float rise, float seed, float density) {
    vec2 q = p * scale + vec2(seed * 7.0, -t * rise * scale);
    vec2 id = floor(q);
    vec2 f = fract(q) - 0.5;
    vec2 r = hash22(id + seed);
    vec2 off = (r - 0.5) * 0.4;
    off.x += 0.05 * sin(t * 0.4 + r.y * 40.0);
    float radius = mix(0.05, 0.14, fract(r.x * 7.31));
    float glow = smoothstep(radius, 0.0, length(f - off));
    float twinkle = 0.55 + 0.45 * sin(t * (0.6 + r.x) + r.y * 30.0);
    return glow * glow * twinkle * step(r.y, density);
}

void main() {
    vec2 uv = vUv;
    float t = uTime;
    float k = clamp(uIntensity, 0.0, 1.5);
    vec2 p = vec2(uv.x * uAspect, uv.y);

    vec3 col = baseGradient(uv);

    // the beams fan out from a point above the screen that sways a little
    vec2 src = vec2(uAspect * (0.5 + 0.10 * sin(t * 0.021)), 1.15);
    vec2 d = p - src;
    float dist = length(d);
    float ang = atan(d.x, -d.y);                         // 0 straight down, grows towards +x

    float beams = 0.0;
    vec3 beamColor = vec3(0.0);
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float centre = (fi - 2.5) * 0.095 + 0.040 * sin(t * (0.050 + 0.011 * fi) + fi * 2.1);
        float width = 0.026 + 0.014 * (0.5 + 0.5 * sin(fi * 1.9 + t * 0.03));
        float b = exp(-pow((ang - centre) / width, 2.0));
        b *= 0.45 + 0.55 * (0.5 + 0.5 * sin(t * (0.09 + 0.017 * fi) + fi * 3.1));   // slow breathing
        float which = mod(fi, 3.0);
        vec3 pick = which < 0.5 ? uColA : (which < 1.5 ? uColB : uColC);
        beamColor += mix(uColA, pick, uMulti) * b;
        beams += b;
    }
    float fade = smoothstep(1.30, 0.10, dist);           // strongest at the top, gone far below
    vec3 light = mix(beamColor, vec3(beams), 0.25);      // a touch of white in the beams
    col += light * fade * 0.30 * k;

    // faint haze around the source
    vec2 g = vec2(p.x - src.x, (p.y - 1.0) * 0.8);
    col += mix(uColA, vec3(1.0), 0.3) * exp(-dot(g, g) / 0.18) * 0.07 * k;

    // dust sinking through the light: dim outside the beams, bright inside
    float dots = particleLayer(p, t, 8.0, -0.012, 1.0, uParticles * 0.55)
               + 0.8 * particleLayer(p, t, 14.0, -0.008, 2.0, uParticles * 0.45);
    col += mix(vec3(1.0), uColA, 0.4) * dots * (0.12 + 1.6 * min(beams, 1.0) * fade);

    // soft shoulder instead of a hard clip, gentle vignette
    vec3 over = max(col - 0.75, 0.0);
    col = min(col, 0.75) + 0.25 * (1.0 - exp(-over * 4.0));
    vec2 c = uv - 0.5;
    col *= 1.0 - 0.45 * dot(c, c);

    gl_FragColor = vec4(col, 1.0);
}
