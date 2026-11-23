// Star2D shader library: glass_holo_scan_aqua
// Hologram: scan lines, flicker and a bright rim
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
 float t = s2_t(); float fr = fres(normal, worldPos, 1.5);
 float l = 0.5 + 0.5 * sin(worldPos.y * 40.0 - t * 4.0);
 float fl = 0.85 + 0.15 * sin(t * 37.0) * sin(t * 11.3);
 float sweep = smoothstep(0.96, 1.0, sin(worldPos.y * 1.5 - t * 1.7));
 vec3 c = s2_tint(vec3(0.125, 0.878, 0.753));
 return vec4((c * (0.35 + 0.5 * l) + vec3(0.816, 1.000, 0.957) * (fr + sweep)) * fl, clamp(0.2 + 0.25 * l + fr * 0.6 + sweep, 0.0, 1.0));
}
