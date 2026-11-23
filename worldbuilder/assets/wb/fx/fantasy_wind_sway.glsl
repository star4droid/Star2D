// Star2D shader library: fantasy_wind_sway_gentle
// Sways in the wind, stronger at the top (plants, flags, hair) (moves vertices)
// Shader Params (all optional): amount=0..1 (0 = plays by itself), speed=1, tint=#rrggbb
uniform float u_amount;
uniform float u_speed;
uniform vec4 u_tint;
float s2_t(){ return u_time * (u_speed > 0.0 ? u_speed : 1.0); }
float s2_prog(){ return u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(s2_t() * 0.18) * 2.0 - 1.0)); }
vec3 s2_tint(vec3 d){ return u_tint.a > 0.0 ? u_tint.rgb : d; }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float h31(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
vec3 displace(vec3 pos, vec3 normal){ float t = u_time * (u_speed > 0.0 ? u_speed : 1.0); float h = clamp(pos.y + 0.5, 0.0, 1.0); h *= h; return pos + vec3(sin(t * 1.8 + pos.z * 2.0 + pos.y) * 0.08 * h, 0.0, cos(t * 1.3 + pos.x * 2.0) * 0.08 * 0.6 * h); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 return vec4(color.rgb * lambert(normal) * mix(vec3(1.0), s2_tint(vec3(1.0)), 0.5), color.a);
}
