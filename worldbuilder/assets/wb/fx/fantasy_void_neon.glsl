// Star2D shader library: fantasy_void_neon_cyan_pink
// Black void body with a two-color neon edge and halftone dots
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
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float t = s2_t(); float fr = fres(normal, worldPos, 1.0);
 fr += 0.07 * sin(worldPos.y * 9.0 + t * 5.0) + 0.05 * sin(worldPos.x * 11.0 - t * 4.0) + 0.06 * sin(t * 2.5);
 vec3 rim = mix(s2_tint(vec3(1.000, 0.227, 0.816)), vec3(0.227, 0.941, 1.000), 0.5 + 0.5 * sin(worldPos.y * 2.5 + worldPos.x * 1.5 + t * 4.0));
 vec2 g = (gl_FragCoord.xy + vec2(t * 14.0, -t * 9.0)) / 3.0; float m = smoothstep(0.24, 0.58, fr); float dots = step(1.0 - m, sin(g.x) * sin(g.y) * 0.5 + 0.5);
 float edge = smoothstep(0.52, 0.68, fr), mid = dots * (1.0 - edge) * step(0.24, fr);
 float sweep = smoothstep(0.93, 1.0, sin(worldPos.y * 3.0 - t * 3.0)) * 0.6;
 return vec4(vec3(0.031, 0.008, 0.102) * 0.6 + rim * (edge * 1.6 + mid * 0.9 + sweep), color.a);
}
