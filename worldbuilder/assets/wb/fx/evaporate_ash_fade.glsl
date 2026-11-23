// Star2D shader library: evaporate_ash_fade_ember
// Turns to grey ash, then crumbles with tiny embers
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
 float p = s2_prog(); float n = n3(worldPos * 3.0) * 0.6 + h31(floor(worldPos * 30.0)) * 0.4;
 float cut = max(p * 2.0 - 0.9, 0.0);
 if (n < cut) discard;
 vec3 lit = color.rgb * lambert(normal); float g = dot(lit, vec3(0.3, 0.6, 0.1));
 vec3 ash = mix(lit, vec3(g) * 0.55, clamp(p * 2.2, 0.0, 1.0));
 float e = (1.0 - smoothstep(cut, cut + 0.08, n)) * step(0.001, cut);
 return vec4(ash + s2_tint(vec3(1.000, 0.478, 0.063)) * e * 2.0, color.a);
}
