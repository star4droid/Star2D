// Star2D shader library: fantasy_energy_bands_aqua
// Light bands rising along the shape
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
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); float b = fract(worldPos.y * 2.0 - t * 0.8); float l = smoothstep(0.0, 0.08, b) * (1.0 - smoothstep(0.08, 0.35, b));
 float fr = fres(normal, worldPos, 2.0);
 return vec4(color.rgb * lambert(normal) * 0.7 + mix(s2_tint(vec3(0.125, 0.878, 0.753)), vec3(0.816, 1.000, 0.957), l) * (l * 1.3 + fr * 0.6), color.a);
}
