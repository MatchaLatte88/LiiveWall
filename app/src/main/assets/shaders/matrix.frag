// Matrix: a grid of characters (katakana, hiragana, hangul or zhuyin) that is only visible
// inside slowly drifting clouds. Inside a cloud the characters are bright, towards its edge they
// get dimmer and drop out one by one; single characters keep switching to a new one.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // style strength
uniform vec3 uColDeep;     // gradient at the top
uniform vec3 uColMid;      // gradient at 30 % from the top
uniform vec3 uColShallow;  // gradient at the bottom
uniform vec3 uColGlow;     // additive lift in the lower middle
uniform vec3 uColA;        // character color (at full strength)
uniform sampler2D uGlyphs; // glyph atlas, coverage in alpha (see GlyphAtlas.kt)
uniform float uGlyphCount; // how many cells of the atlas hold a glyph

const float COLS = 42.0;        // characters per screen width
const float CELL_RATIO = 1.3;   // cell height / cell width
const float SPAN = 0.95;        // share of the cell width one atlas cell covers (rest is gap)
const float ATLAS_N = 9.0;      // atlas is ATLAS_N x ATLAS_N glyphs; keep in sync with GlyphAtlas.kt

// Vertical gradient measured from the reference: deep -> mid over the top 30 %, mid -> shallow
// until 72 %, flat below. Plus a soft Gaussian lift centred at 50 % / 86 % from the top.
vec3 baseGradient(vec2 uv) {
    float s = 1.0 - uv.y;
    vec3 c = mix(uColDeep, uColMid, clamp(s / 0.30, 0.0, 1.0));
    c = mix(c, uColShallow, clamp((s - 0.30) / 0.42, 0.0, 1.0));
    vec2 g = vec2((uv.x - 0.5) / 0.234, (s - 0.86) / 0.203);
    return c + uColGlow * exp(-0.5 * dot(g, g));
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec2(17.3, 9.1);
        a *= 0.5;
    }
    return s;
}

void main() {
    float t = uTime;

    // Grid position, y counted from the top.
    vec2 g = vec2(vUv.x * COLS, (1.0 - vUv.y) * COLS / (CELL_RATIO * uAspect));
    vec2 cell = floor(g);
    vec2 f = fract(g);

    // Cloud value of the whole cell (so a character is lit evenly): two noise layers drifting
    // in opposite directions, which makes the clouds slowly change shape as well as move.
    vec2 q = vec2(cell.x + 0.5, (cell.y + 0.5) * CELL_RATIO) / COLS * 2.3;
    float n1 = fbm(q + vec2(t * 0.020, t * 0.013));
    float n2 = fbm(q * 1.15 + vec2(31.7, -12.4) - vec2(t * 0.016, -t * 0.011));
    float m = 0.5 * (n1 + n2);
    float lo = 0.46 + 0.035 * sin(t * 0.07);   // the clouds slowly grow and shrink as a whole
    float cloud = smoothstep(lo, lo + 0.20, m);

    // Every cell has its own pace of switching to a new character.
    float h = hash21(cell);
    float rate = 0.25 + 1.6 * h * h;
    float tick = floor(t * rate + h * 37.0);
    float id = floor(hash21(cell + tick * vec2(7.13, 3.71)) * uGlyphCount);
    float flick = mix(0.55, 1.0, hash21(cell * 1.7 + tick * vec2(1.9, 5.3)));

    // Ragged edge: the lower the cloud value, the more cells are gone completely.
    float alive = step(hash21(cell + 91.7) * 0.8, cloud);

    vec2 l = vec2((f.x - 0.5) / SPAN + 0.5, (f.y - 0.5) * CELL_RATIO / SPAN + 0.5);
    vec2 cellInAtlas = vec2(mod(id, ATLAS_N), floor(id / ATLAS_N));
    float cov = texture2D(uGlyphs, (cellInAtlas + clamp(l, 0.0, 1.0)) / ATLAS_N).a;
    cov *= step(0.0, l.x) * step(l.x, 1.0) * step(0.0, l.y) * step(l.y, 1.0);

    vec3 col = baseGradient(vUv);
    float k = clamp(cov * cloud * flick * alive * 0.55 * uIntensity, 0.0, 1.0);
    col = mix(col, uColA, k);

    gl_FragColor = vec4(col, 1.0);
}
