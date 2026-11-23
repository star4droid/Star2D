// Star2D shader library: evaporate_dissolve_burn_fire
// Dissolve with a burning edge and charred border
// Shader Params (all optional): amount=0..1 (0 = plays by itself), speed=1, tint=#rrggbb
uniform float u_amount;
uniform float u_speed;
uniform vec4 u_tint;
float s2_t(){ return u_time * (u_speed > 0.0 ? u_speed : 1.0); }
float s2_prog(){ return u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(s2_t() * 0.18) * 2.0 - 1.0)); }
vec3 s2_tint(vec3 d){ return u_tint.a > 0.0 ? u_tint.rgb : d; }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float h31(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float n3(vec3 p){ vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
 return mix(mix(mix(h31(i), h31(i + vec3(1.0, 0.0, 0.0)), f.x), mix(h31(i + vec3(0.0, 1.0, 0.0)), h31(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
            mix(mix(h31(i + vec3(0.0, 0.0, 1.0)), h31(i + vec3(1.0, 0.0, 1.0)), f.x), mix(h31(i + vec3(0.0, 1.0, 1.0)), h31(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float p = s2_prog() * 1.1; float n = n3(worldPos * 3.0) * 0.7 + n3(worldPos * 3.0 * 2.7) * 0.3;
 if (n < p) discard;
 float e = 1.0 - smoothstep(p, p + 0.12, n), ch = 1.0 - smoothstep(p + 0.1, p + 0.3, n);
 vec3 base = mix(color.rgb * lambert(normal), vec3(0.227, 0.024, 0.000), ch * step(0.001, p));
 return vec4(base + s2_tint(mix(vec3(1.000, 0.478, 0.063), vec3(1.000, 0.941, 0.627), e)) * e * 2.2 * step(0.001, p), color.a);
}
