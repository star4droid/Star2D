// Star2D shader library: evaporate_up_holy
// Evaporate: rises away as streaks of vapor
// Shader Params (all optional): amount=0..1 (0 = plays by itself), speed=1, tint=#rrggbb
uniform float u_amount;
uniform float u_speed;
uniform vec4 u_tint;
float s2_t(){ return u_time * (u_speed > 0.0 ? u_speed : 1.0); }
float s2_prog(){ return u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(s2_t() * 0.18) * 2.0 - 1.0)); }
vec3 s2_tint(vec3 d){ return u_tint.a > 0.0 ? u_tint.rgb : d; }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float h31(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float n3(vec3 p){ vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
 return mix(mix(mix(h31(i), h31(i + vec3(1.0, 0.0, 0.0)), f.x), mix(h31(i + vec3(0.0, 1.0, 0.0)), h31(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
            mix(mix(h31(i + vec3(0.0, 0.0, 1.0)), h31(i + vec3(1.0, 0.0, 1.0)), f.x), mix(h31(i + vec3(0.0, 1.0, 1.0)), h31(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float p = s2_prog() * 1.15; float t = s2_t();
 float n = n3(vec3(worldPos.x * 2.5, worldPos.y * 2.5 * 0.35 - t * 0.8, worldPos.z * 2.5)) * 0.75 + n3(worldPos * 2.5 * 3.0) * 0.25;
 if (n < p - 0.02) discard;
 float e = 1.0 - smoothstep(p, p + 0.22, n);
 vec3 glow = s2_tint(mix(vec3(1.000, 0.949, 0.690), vec3(1.000, 1.000, 1.000), e));
 return vec4(mix(color.rgb * lambert(normal), glow, e) + glow * e * e, color.a * (1.0 - e * 0.6));
}
