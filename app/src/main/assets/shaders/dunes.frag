// Dunes: wind-shaped ridges in layers, far ones pale and hazy, near ones dark. Each ridge has a
// long gentle face turned towards the light and a short steep one in shadow; a thin glow runs
// along the lit crests and mist lies in the valleys. The layers drift sideways, far ones slower.
precision highp float;

varying vec2 vUv;

uniform float uAspect;     // width / height of the render target
uniform float uTime;       // animation time in seconds, already multiplied by speed
uniform float uIntensity;  // crest glow, mist and moon strength
uniform vec3 uColDeep;     // gradient at the top
uniform vec3 uColMid;      // gradient at 30 % from the top
uniform vec3 uColShallow;  // gradient at the bottom
uniform vec3 uColGlow;     // additive lift in the lower middle
uniform vec3 uColA;        // light color of crests and moon
uniform vec3 uColB;        // mist tint
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

// Smooth A -> B -> C -> A cycle for u in 0..3.
vec3 cycle3(float u) {
    vec3 c = mix(uColA, uColB, smoothstep(0.0, 1.0, u));
    c = mix(c, uColC, smoothstep(1.0, 2.0, u));
    return mix(c, uColA, smoothstep(2.0, 3.0, u));
}

// Height of ridge fi at x. sin(u - lean * sin(u)) leans the wave over: the face rising towards
// +x is long and gentle, the one falling away is short and steep.
float ridge(float x, float fi, float t) {
    float base = 0.60 - 0.095 * fi;
    float amp = 0.030 + 0.007 * fi;
    float u = x * (4.2 + 1.1 * fi) - t * (0.030 + 0.012 * fi) + fi * 2.3;
    return base + amp * sin(u - 0.85 * sin(u))
                + amp * 0.35 * sin(u * 2.3 + fi * 1.1 + t * 0.011);
}

void main() {
    vec2 uv = vUv;
    float t = uTime;
    float k = clamp(uIntensity, 0.0, 1.5);

    vec3 col = baseGradient(uv);

    // a soft moon with a wide halo, low in the sky
    vec2 m = vec2((uv.x - 0.70) * uAspect, uv.y - 0.80);
    float md = length(m);
    vec3 moonColor = mix(uColA, vec3(1.0), 0.45);
    col += moonColor * (smoothstep(0.050, 0.018, md) * 0.36 + exp(-md * md / 0.006) * 0.14
                      + exp(-md * md / 0.06) * 0.05) * k;

    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float depth = fi / 4.0;
        float edge = ridge(uv.x, fi, t);
        float cover = smoothstep(0.0, 0.006, edge - uv.y);

        // lit where the surface rises towards +x (light comes from the left)
        float slope = (ridge(uv.x + 0.01, fi, t) - ridge(uv.x - 0.01, fi, t)) / 0.02;
        float lit = smoothstep(-0.15, 0.15, slope);

        // far layers dissolve into the sky behind them, near ones are dark
        vec3 body = mix(uColShallow * 1.05, uColDeep * 0.75, depth);
        body = mix(body, baseGradient(vec2(0.5, 0.60 - 0.095 * fi)), 0.55 * (1.0 - depth));
        // the light and shadow faces only reach a little way below the crest
        float below = edge - uv.y;
        body *= mix(1.0, 0.72 + 0.40 * lit, exp(-below * 16.0));
        body *= 0.84 + 0.16 * smoothstep(0.25, 0.0, below);          // darker towards the foot

        // mist lying in the valley in front of this layer; patches slowly drift past
        float band = exp(-pow((uv.y - (edge - 0.05)) * 12.0, 2.0));
        float patch = 0.5 + 0.5 * sin(uv.x * 3.4 + t * 0.035 + fi * 2.0);
        vec3 mistColor = mix(mix(uColShallow, uColA, 0.30), mix(uColShallow, uColB, 0.45), uMulti) * 1.25;
        body = mix(body, mistColor, band * patch * 0.20 * k);

        col = mix(col, body, cover);

        // thin glow along the crest, strongest where it is lit
        float rim = exp(-pow((uv.y - edge) * 70.0, 2.0));
        vec3 rimColor = mix(uColA, cycle3(fi * 0.6), uMulti);
        col += rimColor * rim * (0.30 + 0.70 * lit) * 0.14 * k * (1.0 - depth * 0.5);
    }

    // soft shoulder instead of a hard clip, gentle vignette
    vec3 over = max(col - 0.75, 0.0);
    col = min(col, 0.75) + 0.25 * (1.0 - exp(-over * 4.0));
    vec2 c = uv - 0.5;
    col *= 1.0 - 0.35 * dot(c, c);

    gl_FragColor = vec4(col, 1.0);
}
