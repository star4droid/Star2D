// Star2D shader library: fantasy_plasma_neon
// Flowing plasma colors
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
 float t = s2_t(); vec3 p = worldPos * 2.5;
 float v = sin(p.x + t) + sin(p.y * 1.3 - t * 1.2) + sin(p.z * 0.9 + t * 0.7) + sin(length(p.xy) * 1.5 - t * 1.5);
 v = v * 0.125 + 0.5;
 vec3 c = mix(vec3(0.031, 0.008, 0.102), s2_tint(vec3(1.000, 0.227, 0.816)), smoothstep(0.2, 0.6, v)); c = mix(c, vec3(0.227, 0.941, 1.000), smoothstep(0.6, 0.9, v));
 return vec4(c * (0.7 + 0.5 * v), color.a);
}
