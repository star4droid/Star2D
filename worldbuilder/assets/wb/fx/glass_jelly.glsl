// Star2D shader library: glass_jelly_slime
// Wobbly jelly: see-through and bouncy (moves vertices)
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
vec3 displace(vec3 pos, vec3 normal){ float t = u_time * (u_speed > 0.0 ? u_speed : 1.0); float h = pos.y + 0.5; return pos + vec3(sin(t * 4.0 + pos.y * 5.0) * 0.05 * h, sin(t * 6.0) * 0.03 * h, cos(t * 3.4 + pos.y * 4.0) * 0.05 * h) * 1.0; }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float fr = fres(normal, worldPos, 2.0);
 float sp = pow(max(dot(reflect(-normalize(u_cameraPos - worldPos), normal), -u_lightDir), 0.0), 30.0);
 float a = clamp(0.5 + fr * 0.45 + sp, 0.0, 1.0) * color.a;
 if (a < 0.01) discard;
 return vec4(s2_tint(vec3(0.424, 1.000, 0.227)) * (0.6 + 0.4 * lambert(normal)) + vec3(0.957, 1.000, 0.541) * fr * 0.6 + vec3(sp), a);
}
