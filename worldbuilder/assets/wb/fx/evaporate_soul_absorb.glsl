// Star2D shader library: evaporate_soul_absorb_spirit
// Pulled into its center while fading (moves vertices)
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
vec3 displace(vec3 pos, vec3 normal){ float a = u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(u_time * (u_speed > 0.0 ? u_speed : 1.0) * 0.18) * 2.0 - 1.0)); float t = u_time * (u_speed > 0.0 ? u_speed : 1.0); float w = sin(pos.y * 9.0 + t * 6.0) * 0.06 * a; return pos * (1.0 - a * a * 0.92) + normal * w + vec3(0.0, a * a * 0.6, 0.0); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float p = s2_prog(); float fr = fres(normal, worldPos, 2.0);
 float a = color.a * (1.0 - p * 0.85);
 if (a < 0.02) discard;
 vec3 glow = s2_tint(mix(vec3(0.125, 0.878, 0.753), vec3(0.816, 1.000, 0.957), fr));
 return vec4(mix(color.rgb * lambert(normal), glow, p) + glow * fr * (0.3 + p * 1.5), a);
}
