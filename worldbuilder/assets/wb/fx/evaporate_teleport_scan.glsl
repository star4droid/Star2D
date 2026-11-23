// Star2D shader library: evaporate_teleport_scan_aqua
// Teleport: sliced into moving light bands
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
 float p = s2_prog() * 1.05; float l = fract(worldPos.y * 5.0 - s2_t() * 0.9 + h21(vec2(floor(worldPos.y * 5.0 - s2_t() * 0.9), 3.0)) * 0.0);
 float r = h21(vec2(floor(worldPos.y * 5.0 * 4.0), 1.0));
 if (l * 0.6 + r * 0.4 < p) discard;
 float e = (1.0 - smoothstep(p, p + 0.25, l * 0.6 + r * 0.4)) * step(0.001, p);
 vec3 glow = s2_tint(mix(vec3(0.125, 0.878, 0.753), vec3(0.816, 1.000, 0.957), e));
 return vec4(mix(color.rgb * lambert(normal), glow, e) + glow * e, color.a);
}
