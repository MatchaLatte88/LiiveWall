// Plain smooth gradient. Static: drawn once, no animation.
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

// Vertical gradient measured from the reference: deep -> mid over the top 30 %, mid -> shallow
// until 72 %, flat below. Plus a soft Gaussian lift centred at 50 % / 86 % from the top.
vec3 baseGradient(vec2 uv) {
    float s = 1.0 - uv.y;
    vec3 c = mix(uColDeep, uColMid, clamp(s / 0.30, 0.0, 1.0));
    c = mix(c, uColShallow, clamp((s - 0.30) / 0.42, 0.0, 1.0));
    vec2 g = vec2((uv.x - 0.5) / 0.234, (s - 0.86) / 0.203);
    return c + uColGlow * exp(-0.5 * dot(g, g));
}

void main() {
    gl_FragColor = vec4(baseGradient(vUv), 1.0);
}
