// Contours: the level lines of a slowly changing landscape, drawn as faint light lines.
// Individual levels breathe and a glow travels along them, so the map seems to wake up in places.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // line strength
uniform vec3 uColDeep;
uniform vec3 uColMid;
uniform vec3 uColShallow;
uniform vec3 uColGlow;
uniform vec3 uColA;        // light color of the lines
uniform vec3 uColB;        // secondary accent
uniform vec3 uColC;        // tertiary accent (used when multicolor is on)
uniform float uMulti;      // 0 = single look, 1 = multicolor
uniform float uFill;       // 1 = tint the bands between the lines too
uniform vec3 uTouch;       // finger position (0..1, y up) and how firmly it rests on the screen (0..1)
uniform vec3 uRipple;      // where the last tap landed (0..1, y up) and its age in seconds

// Vertical gradient measured from the reference screenshot (see gradient.frag).
vec3 baseGradient(vec2 uv) {
    float s = 1.0 - uv.y;
    vec3 c = mix(uColDeep, uColMid, clamp(s / 0.30, 0.0, 1.0));
    c = mix(c, uColShallow, clamp((s - 0.30) / 0.42, 0.0, 1.0));
    vec2 g = vec2((uv.x - 0.5) / 0.234, (s - 0.86) / 0.203);
    return c + uColGlow * exp(-0.5 * dot(g, g));
}

// Height field: a sum of travelling waves in well-spread directions. Returns the height in x
// and its exact gradient in yz, so contour lines get an even width without screen derivatives.
vec3 terrain(vec2 p, float t) {
    float h = 0.0;
    vec2 g = vec2(0.0);
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float ang = fi * 2.399963;                            // golden angle
        vec2 dir = vec2(cos(ang), sin(ang));
        float k = 1.7 + 0.55 * mod(fi * 3.0, 7.0);           // wave numbers 1.7 .. 5.0
        float a = 0.30 / (0.6 + 0.35 * k);                    // finer waves are weaker
        float arg = dot(p, dir) * k + fi * 1.7 + t * (0.05 + 0.012 * fi);
        h += a * sin(arg);
        g += a * k * cos(arg) * dir;
    }
    return vec3(h, g);
}

// The landscape with touch applied (all zero at rest): a resting finger bulges the map under it
// like a lens, so the lines spread apart around the finger; a tap sends a soft ridge running
// outwards. Same return value as terrain(); [glow] is the light the touch lends to the lines.
vec3 touchedTerrain(vec2 p, float t, out float glow) {
    glow = 0.0;
    vec2 q = p;
    vec2 d = vec2(0.0);
    float m = 0.0;
    if (uTouch.z > 0.0) {
        d = p - vec2(uTouch.x * uAspect, uTouch.y) * 4.0;
        m = 0.6 * uTouch.z * exp(-dot(d, d) / 0.32);   // lens strength, sigma 0.4
        q = p - d * m;
        glow += uTouch.z * exp(-dot(d, d) / 0.8);
    }
    vec3 tr = terrain(q, t);
    if (m > 0.0) {
        // chain rule for the lens p -> p - d*m, whose Jacobian is (1 - m) I + (m / sigma^2) d d^T
        tr.yz = (1.0 - m) * tr.yz + (m / 0.16) * d * dot(d, tr.yz);
    }
    if (uRipple.z < 5.0) {
        vec2 r = p - vec2(uRipple.x * uAspect, uRipple.y) * 4.0;
        float len = max(length(r), 0.0001);
        float front = uRipple.z * 1.1;
        float off = len - front;
        float life = smoothstep(0.0, 0.2, uRipple.z) * (1.0 - smoothstep(2.5, 5.0, uRipple.z));
        float fade = life * exp(-front * 0.15);
        float ridge = 0.035 * fade * exp(-off * off / 0.12);
        tr += vec3(ridge, ridge * (-off / 0.06) * r / len);
        glow += fade * exp(-off * off / 0.12);
    }
    return tr;
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
    float k = clamp(uIntensity, 0.0, 1.5);
    vec2 p = vec2(uv.x * uAspect, uv.y) * 4.0;

    float touchGlow;
    vec3 tr = touchedTerrain(p, t, touchGlow);
    vec3 col = baseGradient(uv);
    col *= 0.93 + 0.14 * smoothstep(-0.9, 0.9, tr.x);        // gentle relief shading

    // distance (in p units) to the nearest level line
    float s = tr.x * 9.0;
    float steep = length(tr.yz) * 9.0 + 0.001;
    float lvl = floor(s + 0.5);
    float d = abs(s - lvl) / steep;

    float line = 1.0 - smoothstep(0.0, 0.006, d);
    float halo = exp(-d * d / 0.0004);

    // every fourth level is an index line; levels breathe, and a glow travels along them
    float major = 1.0 - step(0.5, mod(lvl, 4.0));
    float pulse = pow(0.5 + 0.5 * sin(t * 0.35 + lvl * 1.3), 6.0);
    float travel = 0.5 + 0.5 * sin(dot(p, vec2(0.8, 0.6)) * 1.4 - t * 0.5 + lvl * 0.9);
    float lit = clamp(pulse * travel + touchGlow, 0.0, 1.0);

    // multicolor: the accent colors cycle with the level, so neighbouring lines differ
    vec3 lineColor = mix(uColA, cycle3(fract(lvl * 0.11) * 3.0), uMulti);
    vec3 light = mix(lineColor, vec3(1.0), 0.25);

    // band fill: every band between two level lines gets its own tint, every other one a bit
    // stronger, so the map reads like a hypsometric chart
    float band = floor(s);
    vec3 bandColor = mix(uColA, cycle3(fract(band * 0.11) * 3.0), uMulti);
    float bandAmount = 0.11 + 0.08 * step(0.5, mod(band, 2.0));
    col = mix(col, bandColor * 0.9, bandAmount * k * uFill);
    col += light * (line * (0.05 + 0.55 * lit) * (1.0 + 0.6 * major)
                  + halo * (0.010 + 0.10 * lit)) * k;

    vec3 over = max(col - 0.75, 0.0);
    col = min(col, 0.75) + 0.25 * (1.0 - exp(-over * 4.0));
    vec2 c = uv - 0.5;
    col *= 1.0 - 0.35 * dot(c, c);

    gl_FragColor = vec4(col, 1.0);
}
