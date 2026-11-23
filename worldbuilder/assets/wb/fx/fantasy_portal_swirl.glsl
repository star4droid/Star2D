// Star2D shader library: fantasy_portal_swirl_void
// Portal: spinning swirl (best on QUAD, DISC or PLANE)
// Shader Params (all optional): amount=0..1 (0 = plays by itself), speed=1, tint=#rrggbb
uniform float u_amount;
uniform float u_speed;
uniform vec4 u_tint;
float s2_t(){ return u_time * (u_speed > 0.0 ? u_speed : 1.0); }
float s2_prog(){ return u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(s2_t() * 0.18) * 2.0 - 1.0)); }
vec3 s2_tint(vec3 d){ return u_tint.a > 0.0 ? u_tint.rgb : d; }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float h31(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); vec2 c = uv - 0.5; float r = length(c), a = atan(c.y, c.x);
 if (r > 0.5) discard;
 float s = sin(a * 5.0 + r * 22.0 - t * 4.0) * 0.5 + 0.5; float s2 = sin(a * 3.0 - r * 12.0 + t * 2.0) * 0.5 + 0.5;
 float core = (1.0 - smoothstep(0.0, 0.22, r)), rim = smoothstep(0.36, 0.48, r) * (1.0 - smoothstep(0.47, 0.5, r));
 vec3 col = mix(vec3(0.039, 0.008, 0.094), mix(s2_tint(vec3(0.690, 0.251, 1.000)), vec3(0.251, 0.910, 1.000), s2), s) + vec3(0.251, 0.910, 1.000) * (core + rim * 1.5);
 return vec4(col, clamp((1.0 - smoothstep(0.42, 0.5, r)) * (0.55 + s * 0.45) + core, 0.0, 1.0));
}
