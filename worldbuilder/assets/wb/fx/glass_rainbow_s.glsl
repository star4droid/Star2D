// Star2D shader library: glass_rainbow_soft
// Rainbow glass: colors shift with the viewing angle
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
vec3 hue(float h){ return clamp(abs(fract(h + vec3(0.0, 0.6667, 0.3333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float fr = fres(normal, worldPos, 1.6);
 vec3 rb = hue(fr * 0.9 + worldPos.y * 0.25 + s2_t() * 0.35);
 float a = clamp(0.15 + fr * 0.8, 0.0, 1.0) * color.a;
 if (a < 0.01) discard;
 return vec4(mix(s2_tint(vec3(0.345, 0.784, 1.000)), rb, 0.75) * (0.7 + 0.3 * lambert(normal)) + vec3(pow(fr, 4.0)), a);
}
