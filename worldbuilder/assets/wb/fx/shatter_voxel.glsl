// Star2D shader library: shatter_voxel_neon
// Break into small cubes that vanish
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
 float p = s2_prog(); vec3 g = worldPos * 5.0; float r = h31(floor(g));
 float th = r * 0.85 + 0.08;
 if (p > th) discard;
 vec3 f = abs(fract(g) - 0.5); float line = smoothstep(0.42, 0.5, max(f.x, max(f.y, f.z)));
 float heat = smoothstep(th - 0.25, th, p) * step(0.001, p);
 vec3 glow = s2_tint(mix(vec3(1.000, 0.227, 0.816), vec3(0.227, 0.941, 1.000), heat));
 return vec4(color.rgb * lambert(normal) * (1.0 - heat * 0.4) + glow * (heat * 0.9 + line * (0.15 + heat)), color.a);
}
