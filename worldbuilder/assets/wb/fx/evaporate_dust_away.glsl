// Star2D shader library: evaporate_dust_away_sand
// Blown away as dust in the wind (moves vertices)
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
vec3 displace(vec3 pos, vec3 normal){ float a = u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(u_time * (u_speed > 0.0 ? u_speed : 1.0) * 0.18) * 2.0 - 1.0)); float r = fract(sin(dot(floor(pos * 9.0), vec3(12.9898, 78.233, 37.719))) * 43758.5453); return pos + vec3(a * a * (0.3 + r) * 0.9, a * r * 0.25, a * a * (r - 0.5) * 0.3); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float p = s2_prog() * 1.1; float n = h31(floor(worldPos * 26.0)) * 0.55 + n3(worldPos * 2.5 + vec3(s2_t() * 0.3, 0.0, 0.0)) * 0.45;
 if (n < p) discard;
 float e = (1.0 - smoothstep(p, p + 0.2, n)) * step(0.001, p);
 return vec4(mix(color.rgb * lambert(normal), s2_tint(vec3(1.000, 0.784, 0.227)), e * 0.8), color.a);
}
