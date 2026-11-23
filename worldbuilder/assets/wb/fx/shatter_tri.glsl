// Star2D shader library: shatter_tri_fire
// Shatter: triangle shards with bright outlines
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
 float p = s2_prog(); vec2 q = s2_map(uv, worldPos, normal) * 7.0; q.x += q.y * 0.5;
 vec2 i = floor(q), f = fract(q); float up = step(1.0, f.x + f.y);
 float id = h21(i + up * 0.37 + 3.1);
 float d = mix(min(min(f.x, f.y), 1.0 - f.x - f.y), min(min(1.0 - f.x, 1.0 - f.y), f.x + f.y - 1.0), up);
 float th = id * 0.8 + 0.08;
 if (p > th + 0.1) discard;
 float heat = smoothstep(th - 0.3, th + 0.1, p) * step(0.001, p);
 float line = 1.0 - smoothstep(0.02, 0.07 + 0.08 * heat, d);
 vec3 glow = s2_tint(mix(vec3(1.000, 0.478, 0.063), vec3(1.000, 0.941, 0.627), heat));
 return vec4(mix(color.rgb * lambert(normal), vec3(0.227, 0.024, 0.000), heat * 0.7) + glow * line * (0.3 + 2.0 * heat), color.a);
}
