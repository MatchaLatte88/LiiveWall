// Honeycomb: a faint hexagonal lattice with soft waves of light running through its lines.
// The lattice rests almost invisible; strength raises both the resting lines and the waves.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // lattice and wave strength
uniform float uParticles;  // 0..1 amount of floating particles
uniform vec3 uColDeep;     // edges of the picture
uniform vec3 uColMid;      // body
uniform vec3 uColShallow;  // pooled light in the upper middle
uniform vec3 uColGlow;     // additive warm lift
uniform vec3 uColA;        // light color of the lines and waves
uniform vec3 uColB;        // secondary light (cell sparkles)
uniform vec3 uColC;        // tertiary accent (used when multicolor is on)
uniform float uMulti;      // 0 = single look, 1 = multicolor
uniform float uFill;       // 1 = tint the cells, not only the lines
uniform vec3 uTouch;       // finger position (0..1, y up) and how firmly it rests on the screen (0..1)
uniform vec3 uRipple;      // where the last tap landed (0..1, y up) and its age in seconds

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

// Flat-top hexagonal tiling with unit cell width. Returns the distance to the nearest cell
// border (0 on the border, 0.5 in the centre) and an integer cell index.
vec3 hexCell(vec2 q) {
    // The classic row-offset construction yields pointy-top cells; swapping axes makes them flat-top.
    vec2 u = q.yx;
    const vec2 r = vec2(1.0, 1.7320508);
    vec2 h = r * 0.5;
    vec2 a = mod(u, r) - h;
    vec2 b = mod(u - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    vec2 centre = u - gv;
    vec2 p = abs(gv);
    float c = max(dot(p, vec2(0.5, 0.8660254)), p.x);
    // Centres sit on a regular grid; rounding gives stable integers (raw floats would carry noise
    // that the hash turns into visible stripes).
    return vec3(0.5 - c, floor(centre / vec2(0.5, 0.8660254) + 0.5));
}

// Soft ring waves spreading from a point: narrow bright bands that fade with distance.
float ring(vec2 q, vec2 src, float freq, float speed, float t) {
    float r = length(q - src);
    float s = 0.5 + 0.5 * sin(r * freq - t * speed);
    return pow(s, 9.0) * exp(-r * 0.13);
}

// One layer of softly glowing particles drifting upward (one particle per grid cell).
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
    float k = clamp(uIntensity, 0.0, 1.5);

    // light pooled in the upper middle, dark towards the edges
    vec2 g = (uv - vec2(0.5, 0.72)) * vec2(1.0, 0.8);
    float pool = smoothstep(0.80, 0.0, length(g));
    vec3 col = mix(uColDeep, uColMid, smoothstep(0.0, 0.55, pool));
    col = mix(col, uColShallow, smoothstep(0.55, 1.0, pool));
    col += uColGlow * 0.5;

    // lattice in units of 1/5.5 screen width
    vec2 q = vec2(uv.x, uv.y / uAspect) * 5.5;
    vec3 hc = hexCell(q);
    float e = hc.x;

    // waves: two slowly wandering ring sources plus a slow diagonal sweep
    vec2 s1 = vec2(2.75 + 1.4 * sin(t * 0.045), 8.5 + 1.8 * cos(t * 0.037));
    vec2 s2 = vec2(1.2 + 1.0 * cos(t * 0.031 + 1.0), 3.0 + 1.6 * sin(t * 0.041));
    float w1 = ring(q, s1, 1.15, 0.95, t);
    float w2 = 0.8 * ring(q, s2, 0.9, 0.8, t + 20.0);
    float w3 = 0.5 * pow(0.5 + 0.5 * sin(dot(q, vec2(0.6, 0.8)) * 0.45 - t * 0.35), 16.0);

    // touch (all zero at rest): a tap sends a ring through the lattice that lights one cell
    // after the other, and a resting finger leaves a soft glow that fades with distance
    float w4 = 0.0;
    if (uTouch.z > 0.0) {
        vec2 d = q - vec2(uTouch.x, uTouch.y / uAspect) * 5.5;
        w4 += uTouch.z * exp(-dot(d, d) / 2.4);
    }
    if (uRipple.z < 5.0) {
        vec2 cellCentre = (hc.yz * vec2(0.5, 0.8660254)).yx;   // hexCell works on swapped axes
        float front = uRipple.z * 1.9;
        float off = length(cellCentre - vec2(uRipple.x, uRipple.y / uAspect) * 5.5) - front;
        float life = smoothstep(0.0, 0.15, uRipple.z) * (1.0 - smoothstep(2.5, 5.0, uRipple.z));
        w4 += 1.2 * life * exp(-off * off / 0.45) * exp(-front * 0.12);
    }
    w4 = min(w4, 1.0);
    float wave = clamp(w1 + w2 + w3 + w4, 0.0, 1.0);

    // lines: crisp core plus a soft halo, almost invisible at rest and bright where a wave passes
    float line = 1.0 - smoothstep(0.0, 0.016, e);
    float halo = exp(-e * e / 0.0036);
    vec3 light = mix(uColA, vec3(1.0), 0.25);
    // multicolor: each wave source has its own light color (the touch shares the first one)
    vec3 waveMulti = light * (w1 + w4)
                   + mix(uColB, vec3(1.0), 0.25) * w2
                   + mix(uColC, vec3(1.0), 0.25) * w3;
    vec3 waveCol = mix(light * wave, waveMulti, uMulti);
    col += (light * (line * 0.028 + halo * 0.006) + waveCol * (line * 0.34 + halo * 0.07)) * k;

    // cells: faint frosted-glass variation, a touch of fill where a wave passes, rare sparkles
    float inside = smoothstep(0.0, 0.25, e);
    col *= 1.0 + (hash12(hc.yz) - 0.5) * 0.05 * k;
    col += waveCol * 0.05 * k * inside;
    // the touch gets a little extra so it reads over the ambient waves
    col += light * w4 * (line * 0.22 + halo * 0.04 + inside * 0.045) * k;

    // cell fill: every cell carries a faint tint of its own (one of the accents when multicolor
    // is on), and the waves light whole cells instead of only their borders
    float pickCell = hash12(hc.yz + 3.1);
    vec3 spreadCell = pickCell < 0.34 ? uColA : (pickCell < 0.67 ? uColB : uColC);
    vec3 cellColor = mix(uColA, spreadCell, uMulti);
    float cellAmount = 0.07 + 0.10 * hash12(hc.yz + 11.7);
    col = mix(col, cellColor * 0.9, cellAmount * k * uFill * smoothstep(0.0, 0.12, e));
    col += waveCol * 0.22 * k * uFill * inside;
    float sparkle = pow(0.5 + 0.5 * sin(t * 0.35 + hash12(hc.yz + 7.3) * 6.2831), 18.0);
    col += uColB * sparkle * 0.05 * k * inside;

    // bokeh particles
    vec2 p = vec2(uv.x * uAspect, uv.y);
    float dots = particleLayer(p, t, 6.0, 0.016, 1.0, uParticles * 0.5)
               + 0.8 * particleLayer(p, t, 11.0, 0.010, 2.0, uParticles * 0.4);
    col += mix(vec3(1.0), uColA, 0.6) * dots * 0.55;

    // soft shoulder instead of a hard clip, gentle vignette
    vec3 over = max(col - 0.75, 0.0);
    col = min(col, 0.75) + 0.25 * (1.0 - exp(-over * 4.0));
    vec2 c = uv - 0.5;
    col *= 1.0 - 0.35 * dot(c, c);

    gl_FragColor = vec4(col, 1.0);
}
