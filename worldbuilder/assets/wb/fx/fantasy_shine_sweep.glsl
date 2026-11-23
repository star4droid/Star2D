// Star2D shader library: fantasy_shine_sweep_gold
// Metal shine: a bright band glides across
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
 float t = s2_t(); float b = fract((worldPos.x + worldPos.y * 0.8 + worldPos.z * 0.3) * 0.5 - t * 0.5);
 float sh = smoothstep(0.42, 0.5, b) * (1.0 - smoothstep(0.5, 0.58, b)); float fr = fres(normal, worldPos, 3.0);
 float sp = pow(max(dot(reflect(-normalize(u_cameraPos - worldPos), normal), -u_lightDir), 0.0), 20.0);
 vec3 metal = s2_tint(vec3(1.000, 0.784, 0.227)) * (0.35 + 0.65 * lambert(normal));
 return vec4(metal + vec3(1.000, 0.973, 0.816) * (sh * 0.9 + sp * 0.8 + fr * 0.35), color.a);
}
