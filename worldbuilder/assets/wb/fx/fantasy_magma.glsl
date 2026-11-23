// Star2D shader library: fantasy_magma_lava
// Dark rock with glowing flowing cracks
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
float n3(vec3 p){ vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
 return mix(mix(mix(h31(i), h31(i + vec3(1.0, 0.0, 0.0)), f.x), mix(h31(i + vec3(0.0, 1.0, 0.0)), h31(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
            mix(mix(h31(i + vec3(0.0, 0.0, 1.0)), h31(i + vec3(1.0, 0.0, 1.0)), f.x), mix(h31(i + vec3(0.0, 1.0, 1.0)), h31(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); vec2 q = s2_map(uv, worldPos, normal) * 5.0 + vec2(sin(t * 0.5), cos(t * 0.4)) * 0.12; vec3 v = vor(q);
 float heat = n3(worldPos * 1.5 + vec3(t * 0.9, t * 0.3, t * 0.6));
 float pulse = 0.6 + 0.4 * sin(t * 3.0 + v.z * 20.0);
 float run = 0.5 + 0.5 * sin((q.x + q.y) * 6.0 - t * 5.0);
 float crack = 1.0 - smoothstep(0.0, 0.07 + 0.16 * heat * pulse, v.y);
 vec3 rock = vec3(0.227, 0.024, 0.000) * (0.6 + 0.6 * v.z) * lambert(normal) * 1.4 + s2_tint(vec3(1.000, 0.478, 0.063)) * 0.12 * heat * pulse;
 vec3 glow = mix(s2_tint(vec3(1.000, 0.478, 0.063)), vec3(1.000, 0.941, 0.627), crack * heat * (0.5 + run));
 return vec4(rock * (1.0 - crack) + glow * crack * (0.6 + heat * 1.2 + run * 0.6) * (0.7 + 0.5 * pulse), color.a);
}
