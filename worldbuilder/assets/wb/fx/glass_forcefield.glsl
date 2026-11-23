// Star2D shader library: glass_forcefield_aqua
// Force field: hexagons, rim light and moving rings
// Shader Params (all optional): amount=0..1 (0 = plays by itself), speed=1, tint=#rrggbb
uniform float u_amount;
uniform float u_speed;
uniform vec4 u_tint;
float s2_t(){ return u_time * (u_speed > 0.0 ? u_speed : 1.0); }
float s2_prog(){ return u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(s2_t() * 0.18) * 2.0 - 1.0)); }
vec3 s2_tint(vec3 d){ return u_tint.a > 0.0 ? u_tint.rgb : d; }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float h31(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float fres(vec3 n, vec3 wp, float pw){ return pow(1.0 - abs(dot(normalize(u_cameraPos - wp), n)), pw); }
vec2 s2_map(vec2 uv, vec3 p, vec3 n){ if (abs(uv.x) + abs(uv.y) > 0.0001) return uv * vec2(2.0, 1.0); vec3 a = abs(n); return (a.y > a.x && a.y > a.z ? p.xz : (a.x > a.z ? p.zy : p.xy)) * 0.35; }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); vec2 q = s2_map(uv, worldPos, normal) * 10.0 * vec2(1.0, 1.1547);
 vec2 a1 = mod(q, vec2(1.0, 1.732)) - vec2(0.5, 0.866), b1 = mod(q + vec2(0.5, 0.866), vec2(1.0, 1.732)) - vec2(0.5, 0.866);
 vec2 g = dot(a1, a1) < dot(b1, b1) ? a1 : b1; g = abs(g);
 float hx = max(g.x, g.x * 0.5 + g.y * 0.866); float line = smoothstep(0.42, 0.5, hx);
 float fr = fres(normal, worldPos, 2.0); float ring = smoothstep(0.9, 1.0, sin(worldPos.y * 5.0 - t * 3.0));
 float al = clamp(0.06 + line * 0.45 + fr * 0.7 + ring * 0.35, 0.0, 1.0);
 if (al < 0.01) discard;
 return vec4(s2_tint(vec3(0.125, 0.878, 0.753)) * (0.5 + line + ring) + vec3(0.816, 1.000, 0.957) * fr, al);
}
