// Caustics: the light net on a pool floor, seen as the borders of slowly shifting cells.
// Strongest near the surface (top), fading with depth.
precision highp float;

varying vec2 vUv;

uniform float uAspect;
uniform float uTime;
uniform float uIntensity;
uniform vec3 uColDeep;
uniform vec3 uColShallow;
uniform vec3 uColA;
uniform vec3 uColB;        // secondary accent
uniform vec3 uColC;        // tertiary accent (used when multicolor is on)
uniform float uMulti;      // 0 = single look, 1 = multicolor

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// Distance to the nearest border of an animated Voronoi diagram (0 on the border itself).
float border(vec2 p, float t) {
    vec2 ip = floor(p);
    vec2 fp = fract(p);
    float f1 = 8.0;
    float f2 = 8.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash22(ip + g);
            o = 0.5 + 0.45 * sin(t + 6.2831 * o);
            vec2 r = g + o - fp;
            float d = dot(r, r);
            if (d < f1) {
                f2 = f1;
                f1 = d;
            } else if (d < f2) {
                f2 = d;
            }
        }
    }
    return sqrt(f2) - sqrt(f1);
}

void main() {
    vec2 uv = vUv;
    float t = uTime * 0.5;
    vec2 p = vec2(uv.x * uAspect, uv.y) * 6.5;

    vec3 col = mix(uColDeep, uColShallow, pow(uv.y, 1.3));    // light water at the top

    float e1 = border(p + vec2(t * 0.15, 0.0), t);
    float e2 = border(p * 1.7 + 11.3, t * 1.3 + 2.0);
    float c1 = pow(1.0 - smoothstep(0.0, 0.13, e1), 2.0);
    float c2 = 0.6 * pow(1.0 - smoothstep(0.0, 0.16, e2), 2.0);
    float fade = 0.25 + 0.75 * smoothstep(0.0, 1.0, uv.y);      // fades with depth

    // single color: both layers in A. multicolor: the layers differ (A, B) and deeper water
    // turns towards C
    vec3 tA = mix(uColA, vec3(1.0), 0.35);
    vec3 tB = mix(uColB, vec3(1.0), 0.35);
    vec3 tC = mix(uColC, vec3(1.0), 0.35);
    vec3 l1 = mix(tA, tC, uMulti * (1.0 - uv.y));
    vec3 l2 = mix(tA, tB, uMulti);
    col += (l1 * c1 + l2 * c2) * fade * 0.20 * clamp(uIntensity, 0.0, 1.5);

    vec3 over = max(col - 0.75, 0.0);
    col = min(col, 0.75) + 0.25 * (1.0 - exp(-over * 4.0));

    gl_FragColor = vec4(col, 1.0);
}
