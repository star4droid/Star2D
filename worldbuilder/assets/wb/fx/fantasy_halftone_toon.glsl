// Star2D shader library: fantasy_halftone_toon_fine
// Comic book shading with printed dots
// Shader Params (all optional): amount=0..1 (0 = plays by itself), speed=1, tint=#rrggbb
uniform float u_amount;
uniform float u_speed;
uniform vec4 u_tint;
float s2_t(){ return u_time * (u_speed > 0.0 ? u_speed : 1.0); }
float s2_prog(){ return u_amount > 0.0 ? clamp(u_amount, 0.0, 1.0) : smoothstep(0.08, 0.92, abs(fract(s2_t() * 0.18) * 2.0 - 1.0)); }
vec3 s2_tint(vec3 d){ return u_tint.a > 0.0 ? u_tint.rgb : d; }
float h21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float h31(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){
 float d = max(dot(normal, -u_lightDir), 0.0); vec2 g = gl_FragCoord.xy / 2.5;
 float dot1 = sin(g.x + g.y) * sin(g.x - g.y) * 0.5 + 0.5; float sh = step(dot1, d * 1.3 + 0.1 + 0.12 * sin(s2_t() * 3.0));
 vec3 lit = color.rgb * (u_ambient + u_lightColor);
 return vec4(mix(mix(lit, s2_tint(vec3(0.039, 0.008, 0.094)), 0.75), lit, sh), color.a);
}
