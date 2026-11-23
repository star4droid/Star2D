// Star2D shader library: fantasy_rune_glow_aqua
// Ancient runes pulsing on dark stone
// Shader Params (all optional): amount=0..1 (0 = plays by itself), speed=1, tint=#rrggbb
uniform float u_amount;
uniform float u_speed;
uniform vec4 u_tint;
float s2_t(){ return u_time * (u_speed > 0.0 ? u_speed : 1.0); }
float s2_prog(){ return u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(s2_t() * 0.18) * 2.0 - 1.0)); }
vec3 s2_tint(vec3 d){ return u_tint.a > 0.0 ? u_tint.rgb : d; }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float h31(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
vec2 s2_map(vec2 uv, vec3 p, vec3 n){ if (abs(uv.x) + abs(uv.y) > 0.0001) return uv * vec2(2.0, 1.0); vec3 a = abs(n); return (a.y > a.x && a.y > a.z ? p.xz : (a.x > a.z ? p.zy : p.xy)) * 0.35; }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); vec2 q = s2_map(uv, worldPos, normal) * 8.0; vec2 i = floor(q), g = fract(q) - 0.5; float id = h21(i);
 float r = step(abs(g.x), 0.05) * step(abs(g.y), 0.32) * step(0.35, id);
 r += step(abs(g.y - 0.15), 0.05) * step(abs(g.x), 0.3) * step(id, 0.6);
 r += step(abs(g.x + g.y), 0.06) * step(abs(g.x), 0.25) * step(0.55, fract(id * 7.0));
 r += step(abs(length(g) - 0.3), 0.04) * step(0.75, fract(id * 13.0));
 r = clamp(r, 0.0, 1.0); float pulse = 0.5 + 0.5 * sin(t * 2.0 + id * 12.0 + i.y);
 return vec4(vec3(0.008, 0.188, 0.173) * lambert(normal) * 1.6 * (1.0 - r) + mix(s2_tint(vec3(0.125, 0.878, 0.753)), vec3(0.816, 1.000, 0.957), pulse) * r * (0.6 + 1.6 * pulse), color.a);
}
