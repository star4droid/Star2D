// Star2D shader library: shatter_burst_fire
// Shatter: chunks pushed outward while breaking (moves vertices)
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
vec3 vor(vec2 x){ vec2 n = floor(x), f = fract(x); float d1 = 8.0, d2 = 8.0, id = 0.0;
 for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++){ vec2 g = vec2(float(i), float(j)); vec2 o = vec2(h21(n + g), h21(n + g + 17.3)); vec2 r = g + o - f; float d = dot(r, r);
  if (d < d1){ d2 = d1; d1 = d; id = h21(n + g + 7.7); } else if (d < d2){ d2 = d; } }
 return vec3(sqrt(d1), sqrt(d2) - sqrt(d1), id); }
vec3 displace(vec3 pos, vec3 normal){ float a = u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(u_time * (u_speed > 0.0 ? u_speed : 1.0) * 0.18) * 2.0 - 1.0)); float r = fract(sin(dot(floor(pos * 5.0 + 0.5), vec3(12.9898, 78.233, 37.719))) * 43758.5453); return pos + normal * a * a * (0.15 + r) * 0.9; }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float p = s2_prog(); vec3 v = vor(s2_map(uv, worldPos, normal) * 8.0);
 float th = v.z * 0.7 + 0.2;
 if (p > th) discard;
 float heat = smoothstep(th - 0.35, th, p) * step(0.001, p);
 float edge = 1.0 - smoothstep(0.0, 0.05 + 0.2 * heat, v.y);
 vec3 glow = s2_tint(mix(vec3(1.000, 0.478, 0.063), vec3(1.000, 0.941, 0.627), heat));
 return vec4(color.rgb * lambert(normal) * (1.0 - heat * 0.5) + glow * (edge + heat * 0.6) * (0.3 + heat), color.a);
}
