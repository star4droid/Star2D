// Star2D shader library: fantasy_electric_storm
// Electric arcs crawling over the surface
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
float fres(vec3 n, vec3 wp, float pw){ return pow(1.0 - abs(dot(normalize(u_cameraPos - wp), n)), pw); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); float n1 = n3(worldPos * 2.2 + vec3(0.0, t * 2.5, t)); float n2 = n3(worldPos * 2.2 * 1.7 - vec3(t * 2.0, 0.0, t * 1.3));
 float bolt = (1.0 - smoothstep(0.0, 0.035, abs(n1 - 0.5))) + (1.0 - smoothstep(0.0, 0.03, abs(n2 - 0.5))) * 0.7;
 float fl = 0.6 + 0.4 * step(0.5, fract(t * 13.0 + n1 * 3.0)); float fr = fres(normal, worldPos, 2.5);
 vec3 glow = mix(s2_tint(vec3(0.478, 0.549, 1.000)), vec3(0.910, 0.941, 1.000), clamp(bolt, 0.0, 1.0));
 return vec4(color.rgb * lambert(normal) * 0.55 + glow * (bolt * fl * 1.8 + fr * 0.5), color.a);
}
