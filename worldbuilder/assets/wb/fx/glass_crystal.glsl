// Star2D shader library: glass_crystal_ice
// Faceted crystal with sharp highlights
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
 vec3 fn = normalize(floor(normal * 3.0 + 0.5) + normal * 0.35);
 vec3 v = normalize(u_cameraPos - worldPos);
 float sp = pow(max(dot(reflect(-v, fn), -u_lightDir), 0.0), 24.0), fr = fres(fn, worldPos, 2.0);
 float fl = 0.5 + 0.5 * sin(dot(fn, vec3(7.0, 11.0, 5.0)) * 4.0 + s2_t() * 3.5);
 float a = clamp(0.55 + fr * 0.45 + sp, 0.0, 1.0) * color.a;
 if (a < 0.01) discard;
 return vec4(s2_tint(vec3(0.345, 0.784, 1.000)) * (0.3 + 0.45 * lambert(fn) + 0.35 * fl) + vec3(0.941, 0.984, 1.000) * (fr * 0.7 + sp + pow(fl, 6.0) * 0.5), a);
}
