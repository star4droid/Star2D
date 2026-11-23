// Star2D shader library: fantasy_glitch_neon
// Digital glitch: bands jump sideways and split colors (moves vertices)
// Shader Params (all optional): amount=0..1 (0 = plays by itself), speed=1, tint=#rrggbb
uniform float u_amount;
uniform float u_speed;
uniform vec4 u_tint;
float s2_t(){ return u_time * (u_speed > 0.0 ? u_speed : 1.0); }
float s2_prog(){ return u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(s2_t() * 0.18) * 2.0 - 1.0)); }
vec3 s2_tint(vec3 d){ return u_tint.a > 0.0 ? u_tint.rgb : d; }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float h31(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
vec3 displace(vec3 pos, vec3 normal){ float t = u_time * (u_speed > 0.0 ? u_speed : 1.0); float b = floor(pos.y * 10.0); float k = step(0.88, fract(sin(b * 12.9898 + floor(t * 7.0) * 78.233) * 43758.5453)); return pos + vec3(k * 0.5 * (fract(sin(b * 3.7 + floor(t * 7.0)) * 91.7) - 0.5), 0.0, 0.0); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); float b = floor(worldPos.y * 10.0); float k = step(0.88, fract(sin(b * 12.9898 + floor(t * 7.0) * 78.233) * 43758.5453));
 vec3 lit = color.rgb * lambert(normal); float sc = 0.85 + 0.15 * step(0.5, fract(gl_FragCoord.y * 0.25));
 vec3 sh = vec3(lit.r * (1.0 + k), lit.g * (1.0 - k * 0.6), lit.b * (1.0 + k * 0.5));
 return vec4(mix(sh, s2_tint(vec3(1.000, 0.227, 0.816)), k * 0.45) * sc + vec3(0.227, 0.941, 1.000) * k * 0.2, color.a);
}
