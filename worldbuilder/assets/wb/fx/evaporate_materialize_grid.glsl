// Star2D shader library: evaporate_materialize_grid_aqua
// Spawn in: wire grid first, then the surface fills in
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
 float p = 1.0 - s2_prog(); vec3 f = abs(fract(worldPos * 6.0) - 0.5); float line = smoothstep(0.43, 0.5, max(f.x, max(f.y, f.z)));
 float n = n3(worldPos * 2.0);
 float solid = step(n, p * 1.6 - 0.3), wire = line * step(n * 0.6, p * 1.4);
 if (solid + wire < 0.5) discard;
 float e = 1.0 - smoothstep(0.0, 0.25, p * 1.6 - 0.3 - n);
 vec3 glow = s2_tint(mix(vec3(0.125, 0.878, 0.753), vec3(0.816, 1.000, 0.957), line));
 return vec4(color.rgb * lambert(normal) * solid * (1.0 - e * 0.5) + glow * (wire * (1.0 - solid) * 1.6 + e * solid), color.a);
}
