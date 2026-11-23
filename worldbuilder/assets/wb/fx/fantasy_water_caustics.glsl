// Star2D shader library: fantasy_water_caustics_aqua
// Underwater light ripples over the surface
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
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); vec2 q = s2_map(uv, worldPos, normal) * 5.0;
 float c1 = vor(q + vec2(t * 0.25, t * 0.1)).x, c2 = vor(q * 1.4 - vec2(t * 0.15, t * 0.2)).x;
 float ca = pow(c1 * c2 * 2.2, 2.0);
 return vec4(mix(color.rgb, s2_tint(vec3(0.125, 0.878, 0.753)), 0.55) * lambert(normal) + vec3(0.816, 1.000, 0.957) * ca * 0.9, color.a);
}
