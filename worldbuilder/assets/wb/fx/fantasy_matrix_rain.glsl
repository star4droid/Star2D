// Star2D shader library: fantasy_matrix_rain_green
// Digital rain of glowing symbols
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
 float t = s2_t(); vec2 q = s2_map(uv, worldPos, normal) * 6.0; float col = floor(q.x * 3.0);
 float sp = 0.6 + h21(vec2(col, 1.0)) * 1.2; float y = q.y * 0.5 + t * sp * 0.4 + h21(vec2(col, 5.0));
 float head = fract(y); float trail = pow(head, 3.0);
 float glyph = step(0.45, h21(vec2(col, floor(q.y * 8.0) + floor(t * 6.0 * sp))));
 vec2 cg = fract(vec2(q.x * 3.0, q.y * 8.0)) - 0.5; float box = step(abs(cg.x), 0.36) * step(abs(cg.y), 0.36);
 float v = trail * glyph * box;
 return vec4(vec3(0.024, 0.157, 0.039) * 0.8 + mix(s2_tint(vec3(0.424, 1.000, 0.227)), vec3(0.957, 1.000, 0.541), smoothstep(0.85, 1.0, head)) * v * 1.8, color.a);
}
