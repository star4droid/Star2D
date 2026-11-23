// Star2D shader library: fantasy_rainbow_flow_wide
// Rainbow bands flowing over the shape
// Shader Params (all optional): amount=0..1 (0 = plays by itself), speed=1, tint=#rrggbb
uniform float u_amount;
uniform float u_speed;
uniform vec4 u_tint;
float s2_t(){ return u_time * (u_speed > 0.0 ? u_speed : 1.0); }
float s2_prog(){ return u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(s2_t() * 0.18) * 2.0 - 1.0)); }
vec3 s2_tint(vec3 d){ return u_tint.a > 0.0 ? u_tint.rgb : d; }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float h31(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
vec3 hue(float h){ return clamp(abs(fract(h + vec3(0.0, 0.6667, 0.3333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); float h = dot(worldPos, vec3(0.2, 0.35, 0.15)) + t * 0.25;
 vec3 rb = hue(h); float l = dot(lambert(normal), vec3(0.333));
 return vec4(mix(rb, s2_tint(rb), 0.5) * (0.45 + 0.6 * l) + rb * 0.25, color.a);
}
