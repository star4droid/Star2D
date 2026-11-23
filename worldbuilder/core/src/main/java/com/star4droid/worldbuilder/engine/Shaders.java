package com.star4droid.worldbuilder.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ObjectMap;

/** All GLSL of the renderer (GLES 2.0 compatible) and a cache of compiled variants. */
public final class Shaders {
    private Shaders() {}

    static final String PRE = "#ifdef GL_ES\n#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n#endif\n";

    /** Shared lighting + fog + shadow lookup for fragment shaders. */
    static final String LIGHT = "uniform vec3 u_sunDir;\nuniform vec3 u_sunColor;\nuniform vec3 u_ambSky;\nuniform vec3 u_ambGround;\nuniform vec3 u_fogColor;\nuniform vec4 u_fog;\n"   // u_fog: start, 1/(end-start), density cap, unused
            + "uniform vec3 u_camPos;\nuniform float u_time;\n"
            + "#ifdef SHADOW\nuniform sampler2D u_shadowMap;\nuniform vec4 u_shadowInfo;\nvarying vec3 v_shadow;\n"
            + "float shadowTap(vec2 uv, float z){ vec4 c = texture2D(u_shadowMap, uv); float d = dot(c, vec4(1.0, 1.0/255.0, 1.0/65025.0, 1.0/16581375.0)); return step(z, d); }\n"
            + "float shadow(){\n vec3 s = v_shadow; vec2 e = abs(s.xy - 0.5) * 2.0; float fade = smoothstep(0.86, 0.98, max(e.x, e.y));\n if (s.z > 1.0) return 1.0;\n float t = u_shadowInfo.x;\n s.z -= u_shadowInfo.y;\n"
            + "#ifdef SHADOW_PCF\n float r = shadowTap(s.xy + vec2(-t, -t) * 0.6, s.z) + shadowTap(s.xy + vec2(t, -t) * 0.6, s.z) + shadowTap(s.xy + vec2(-t, t) * 0.6, s.z) + shadowTap(s.xy + vec2(t, t) * 0.6, s.z);\n r *= 0.25;\n"
            + "#else\n float r = shadowTap(s.xy, s.z);\n#endif\n return max(r, fade);\n}\n"
            + "#else\nfloat shadow(){ return 1.0; }\n#endif\n"
            + "vec3 lit(vec3 albedo, vec3 n, float sh){\n float ndl = dot(n, u_sunDir);\n float d = smoothstep(-0.25, 0.85, ndl);\n vec3 amb = mix(u_ambGround, u_ambSky, n.y * 0.5 + 0.5);\n return albedo * (amb * 0.92 + u_sunColor * d * mix(0.22, 1.0, sh) * step(-0.3, ndl) * 0.80);\n}\n"
            + "vec3 fogged(vec3 c, vec3 wp){ float d = distance(wp, u_camPos); float f = clamp((d - u_fog.x) * u_fog.y, 0.0, 1.0); return mix(c, u_fogColor, f * f * u_fog.z); }\n";
    static final String SHADOW_V = "#ifdef SHADOW\nuniform mat4 u_shadowMat;\nvarying vec3 v_shadow;\n#endif\n";
    static final String SHADOW_VMAIN = "#ifdef SHADOW\n vec4 sp = u_shadowMat * vec4(wp.xyz + wn * u_shadowBias, 1.0);\n v_shadow = sp.xyz * 0.5 + 0.5;\n#endif\n";

    // ------------------------------------------------------------------ objects
    static final String OBJ_V = "attribute vec3 a_position;\nattribute vec3 a_normal;\nattribute vec4 a_color;\nattribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_world;\nuniform mat4 u_viewProj;\nuniform float u_time;\nuniform vec4 u_wind;\nuniform float u_shadowBias;\n" + SHADOW_V
            + "varying vec4 v_color;\nvarying vec3 v_normal;\nvarying vec3 v_wp;\nvarying vec2 v_uv;\nvarying vec3 v_lp;\nvarying vec3 v_ln;\n"
            + "//DISPLACE\n"
            + "void main(){\n vec3 lp = a_position;\n#ifdef FXV\n lp = displace(lp, a_normal);\n#endif\n vec4 wp = u_world * vec4(lp, 1.0);\n vec3 wn = normalize((u_world * vec4(a_normal, 0.0)).xyz);\n"
            + "#ifdef WIND\n float w = a_color.a * u_wind.z;\n float ph = dot(wp.xz, vec2(0.31, 0.23)) + u_time * 1.7;\n float sw = sin(ph) + 0.45 * sin(ph * 2.3 + 1.7);\n"
            + " wp.xz += u_wind.xy * sw * w * 0.16;\n wp.y += sin(u_time * 3.1 + wp.x * 2.3 + wp.z * 1.9) * 0.025 * w;\n#endif\n"
            + " v_color = a_color;\n v_normal = wn;\n v_wp = wp.xyz;\n v_uv = a_texCoord0;\n v_lp = a_position;\n v_ln = a_normal;\n" + SHADOW_VMAIN + " gl_Position = u_viewProj * wp;\n}\n";
    static final String OBJ_F = "uniform sampler2D u_tex;\nuniform vec3 u_tintA;\nuniform vec3 u_tintB;\nuniform vec4 u_mat;\nuniform vec4 u_sel;\nuniform float u_det;\n"   // u_mat: detail scale, emissive, spec, alpha
            + "varying vec4 v_color;\nvarying vec3 v_normal;\nvarying vec3 v_wp;\nvarying vec2 v_uv;\nvarying vec3 v_lp;\nvarying vec3 v_ln;\n" + LIGHT
            + "//FX\n"
            + "void main(){\n vec3 n = normalize(v_normal);\n"
            + "#ifdef ALPHA\n vec4 t = texture2D(u_tex, v_uv);\n if (t.a < 0.45) discard;\n vec3 albedo = v_color.rgb * u_tintA * (t.rgb * 1.5 + 0.08);\n"
            + "#else\n vec3 albedo = v_color.rgb * mix(u_tintA, u_tintB, v_color.a);\n"
            + "#ifdef DETAIL\n vec3 bw = abs(v_ln); bw = bw * bw; bw /= (bw.x + bw.y + bw.z + 0.0001);\n vec3 p = v_lp * u_mat.x;\n"
            + " float dt = texture2D(u_tex, p.zy).r * bw.x + texture2D(u_tex, p.xz).r * bw.y + texture2D(u_tex, p.xy).r * bw.z;\n albedo *= mix(1.0, dt * 2.0, u_det);\n#endif\n#endif\n"
            + "#ifdef FX\n vec4 fx = effect(vec4(albedo, u_mat.w), v_uv, n, v_wp);\n vec3 c = fx.rgb;\n float al = fx.a;\n"
            + "#else\n float sh = shadow();\n vec3 c = mix(lit(albedo, n, sh), albedo * 1.25, u_mat.y);\n"
            + " vec3 v = normalize(u_camPos - v_wp);\n float sp = pow(max(dot(reflect(-v, n), u_sunDir), 0.0), 28.0) * u_mat.z * sh;\n c += u_sunColor * sp;\n"
            + " c += u_ambSky * pow(1.0 - max(dot(v, n), 0.0), 3.0) * (0.10 + u_mat.z * 0.5);\n float al = u_mat.w;\n#endif\n"
            + " c = mix(c, u_sel.rgb, u_sel.a);\n gl_FragColor = vec4(fogged(c, v_wp), al);\n}\n";

    /** Depth pass (shadow map): depth packed into RGBA. */
    static final String DEPTH_V = "attribute vec3 a_position;\nuniform mat4 u_world;\nuniform mat4 u_viewProj;\nvarying float v_d;\nvoid main(){ vec4 p = u_viewProj * (u_world * vec4(a_position, 1.0)); v_d = p.z * 0.5 + 0.5; gl_Position = p; }\n";
    static final String DEPTH_F = "varying float v_d;\nvoid main(){ float d = clamp(v_d, 0.0, 0.9999); vec4 e = fract(d * vec4(1.0, 255.0, 65025.0, 16581375.0)); e -= e.yzww * vec4(1.0/255.0, 1.0/255.0, 1.0/255.0, 0.0); gl_FragColor = e; }\n";

    // ------------------------------------------------------------------ terrain
    static final String TER_V = "attribute vec3 a_position;\nattribute vec3 a_normal;\nattribute vec4 a_color;\nuniform mat4 u_viewProj;\nuniform float u_shadowBias;\n" + SHADOW_V
            + "varying vec3 v_wp;\nvarying vec3 v_normal;\nvarying vec4 v_splat;\n"
            + "void main(){\n vec4 wp = vec4(a_position, 1.0);\n vec3 wn = a_normal;\n v_wp = a_position;\n v_normal = a_normal;\n v_splat = a_color;\n" + SHADOW_VMAIN + " gl_Position = u_viewProj * wp;\n}\n";
    static final String TER_F = "uniform sampler2D u_grass;\nuniform sampler2D u_dirt;\nuniform sampler2D u_sand;\nuniform sampler2D u_rock;\nuniform sampler2D u_snow;\nuniform sampler2D u_noise;\n"
            + "uniform vec4 u_brush;\nuniform vec4 u_brushColor;\nuniform vec4 u_grid;\nuniform vec4 u_waterInfo;\n"
            + "varying vec3 v_wp;\nvarying vec3 v_normal;\nvarying vec4 v_splat;\n" + LIGHT
            + "void main(){\n vec3 n = normalize(v_normal);\n vec2 uv = v_wp.xz * 0.21;\n"
            + "#ifdef DETAIL\n vec3 g = texture2D(u_grass, uv).rgb;\n vec3 c = g;\n"
            + " c = mix(c, texture2D(u_dirt, uv).rgb, v_splat.r);\n c = mix(c, texture2D(u_sand, uv).rgb, v_splat.g);\n"
            + " float slope = 1.0 - n.y;\n float cl = max(v_splat.b, smoothstep(0.30, 0.46, slope));\n"
            + " vec3 an = abs(n); vec3 rk = texture2D(u_rock, uv * 0.7).rgb;\n"
            + " if (cl > 0.01) { vec3 side = an.x > an.z ? texture2D(u_rock, v_wp.zy * 0.11).rgb : texture2D(u_rock, v_wp.xy * 0.11).rgb; rk = mix(rk, side, smoothstep(0.25, 0.5, slope)); }\n"
            + " c = mix(c, rk, cl);\n c = mix(c, texture2D(u_snow, uv).rgb, v_splat.a * (1.0 - smoothstep(0.45, 0.7, slope) * 0.8));\n"
            + " float mac = texture2D(u_noise, v_wp.xz * 0.012).r;\n c *= 0.84 + mac * 0.34;\n c = mix(c, c * vec3(1.12, 1.04, 0.72), smoothstep(0.55, 0.85, texture2D(u_noise, v_wp.xz * 0.02 + 0.37).g) * 0.5 * (1.0 - cl) * (1.0 - max(v_splat.r, max(v_splat.g, v_splat.a))));\n"
            + "#else\n vec3 c = vec3(0.42, 0.68, 0.27);\n c = mix(c, vec3(0.62, 0.48, 0.32), v_splat.r);\n c = mix(c, vec3(0.90, 0.82, 0.60), v_splat.g);\n float slope = 1.0 - n.y;\n"
            + " c = mix(c, vec3(0.56, 0.57, 0.60), max(v_splat.b, smoothstep(0.30, 0.46, slope)));\n c = mix(c, vec3(0.94, 0.96, 1.0), v_splat.a);\n#endif\n"
            // under the global water: sandy, darker with depth
            + " float uw = clamp((u_waterInfo.x - v_wp.y) * u_waterInfo.y, 0.0, 1.0);\n c = mix(c, c * vec3(0.55, 0.78, 0.86), uw);\n"
            + " vec3 col = lit(c, n, shadow());\n"
            + " if (u_grid.x > 0.0) { vec2 gp = abs(fract(v_wp.xz / u_grid.y + 0.5) - 0.5) * u_grid.y; float ln = 1.0 - smoothstep(0.0, 0.035 + u_grid.w, min(gp.x, gp.y));\n"
            + "  float gd = distance(v_wp.xz, u_grid.zw * 0.0 + u_camPos.xz); col = mix(col, vec3(1.0), ln * u_grid.x * 0.5); }\n"
            + " if (u_brush.w > 0.0) { float bd = distance(v_wp.xz, u_brush.xy); float ring = 1.0 - smoothstep(0.0, 0.18 + u_brush.z * 0.02, abs(bd - u_brush.z));\n"
            + "  float fill = (1.0 - smoothstep(0.0, u_brush.z, bd)) * 0.16; col = mix(col, u_brushColor.rgb, clamp(ring * 0.9 + fill, 0.0, 1.0) * u_brush.w); }\n"
            + " gl_FragColor = vec4(fogged(col, v_wp), 1.0);\n}\n";

    // ------------------------------------------------------------------ water (a_color.r = depth 0..1 over 4 m, a_color.g = flow mask)
    static final String WAT_V = "attribute vec3 a_position;\nattribute vec4 a_color;\nattribute vec2 a_texCoord0;\nuniform mat4 u_world;\nuniform mat4 u_viewProj;\nuniform float u_time;\n"
            + "varying vec3 v_wp;\nvarying vec4 v_c;\nvarying vec2 v_uv;\n"
            + "void main(){\n vec4 wp = u_world * vec4(a_position, 1.0);\n wp.y += (sin(wp.x * 0.9 + u_time * 1.3) + sin(wp.z * 1.1 + u_time * 1.1)) * 0.025 * a_color.b;\n v_wp = wp.xyz;\n v_c = a_color;\n v_uv = a_texCoord0;\n gl_Position = u_viewProj * wp;\n}\n";
    static final String WAT_F = "uniform sampler2D u_noise;\nuniform vec3 u_shallow;\nuniform vec3 u_deep;\nuniform vec4 u_flow;\nuniform vec3 u_skyColor;\n"
            + "varying vec3 v_wp;\nvarying vec4 v_c;\nvarying vec2 v_uv;\n" + LIGHT
            + "void main(){\n float depth = v_c.r * 4.5 - 0.5;\n if (depth < -0.08) discard;\n vec2 p = v_wp.xz;\n float t = u_time * u_flow.z;\n vec2 fl = u_flow.xy * t;\n"
            + " vec3 n1 = texture2D(u_noise, p * 0.045 + fl * 0.05 + vec2(t * 0.011, t * 0.007)).rgb;\n vec3 n2 = texture2D(u_noise, p * 0.11 + fl * 0.11 - vec2(t * 0.013, t * 0.017)).rgb;\n"
            + " vec3 n = normalize(vec3((n1.r + n2.g - 1.0) * 0.55, 1.0, (n1.g + n2.r - 1.0) * 0.55));\n"
            + " vec3 v = normalize(u_camPos - v_wp);\n float fr = pow(1.0 - max(dot(v, n), 0.0), 3.0);\n"
            + " vec3 body = mix(u_shallow, u_deep, smoothstep(0.0, 2.6, depth + (n1.b - 0.5) * 0.5));\n"
            + " vec3 c = body * (u_ambSky * 1.1 + u_sunColor * 0.45);\n c = mix(c, u_skyColor, fr * 0.55);\n"
            + " float sp = pow(max(dot(reflect(-v, n), u_sunDir), 0.0), 90.0);\n c += u_sunColor * sp * 1.4;\n"
            + "#ifdef FULL\n float cau = smoothstep(0.62, 0.95, n2.b) * smoothstep(0.45, 0.8, n1.r);\n c += vec3(0.9, 1.0, 1.0) * cau * 0.22 * (1.0 - smoothstep(0.0, 2.0, depth));\n#endif\n"
            // shore foam: a band at the edge and moving lines behind it
            + " float edge = depth + (n2.r - 0.5) * 0.22;\n float foam = 1.0 - smoothstep(0.03, 0.2, edge);\n"
            + " float lines = smoothstep(0.75, 1.0, sin(edge * 9.0 - t * 2.2 + n1.g * 5.0)) * (1.0 - smoothstep(0.15, 0.95, edge)) * 0.65;\n"
            + " foam = clamp(foam + lines + v_c.g * smoothstep(0.55, 0.9, n2.b + n1.r * 0.4), 0.0, 1.0);\n c = mix(c, vec3(1.0), foam * 0.9);\n"
            + " float a = clamp(depth * 2.4 + 0.25, 0.0, 0.9);\n a = max(a, foam) * smoothstep(0.0, 0.04, depth + 0.02) * v_c.a;\n"
            + " gl_FragColor = vec4(fogged(c, v_wp), a);\n}\n";
    /** Falling water sheet: uv.y runs down the fall in meters, a_color.r = edge fade, a_color.g = foam at the foot. */
    static final String FALL_F = "uniform sampler2D u_noise;\nuniform vec3 u_shallow;\nuniform vec3 u_deep;\nuniform vec4 u_flow;\nuniform vec3 u_skyColor;\n"
            + "varying vec3 v_wp;\nvarying vec4 v_c;\nvarying vec2 v_uv;\n" + LIGHT
            + "void main(){\n float t = u_time * u_flow.z;\n vec2 uv = vec2(v_uv.x * 0.5, v_uv.y * 0.11 - t * 0.55);\n"
            + " float s1 = texture2D(u_noise, vec2(uv.x * 1.7, uv.y)).r;\n float s2 = texture2D(u_noise, vec2(uv.x * 3.9 + 0.3, uv.y * 1.7 - t * 0.2)).g;\n"
            + " float streak = smoothstep(0.35, 0.8, s1 * 0.6 + s2 * 0.6);\n vec3 c = mix(u_shallow * 1.15, vec3(1.0), streak * 0.85 + v_c.g);\n c = c * (u_ambSky + u_sunColor * 0.5);\n"
            + " float a = clamp(0.62 + streak * 0.4 + v_c.g, 0.0, 1.0) * v_c.r * v_c.a;\n gl_FragColor = vec4(fogged(min(c, vec3(1.0)), v_wp), a);\n}\n";

    // ------------------------------------------------------------------ sky (full screen; direction rebuilt from the camera basis)
    static final String SKY_V = "attribute vec3 a_position;\nuniform vec3 u_right;\nuniform vec3 u_up;\nuniform vec3 u_fwd;\nvarying vec3 v_dir;\nvoid main(){ v_dir = u_fwd + u_right * a_position.x + u_up * a_position.y; gl_Position = vec4(a_position.xy, 0.9999, 1.0); }\n";
    static final String SKY_F = "uniform vec3 u_zenith;\nuniform vec3 u_horizon;\nuniform vec3 u_sunDir;\nuniform vec3 u_sunColor;\nuniform sampler2D u_noise;\nuniform float u_time;\nuniform vec4 u_cloud;\nvarying vec3 v_dir;\n"
            + "void main(){\n vec3 d = normalize(v_dir);\n float h = clamp(d.y, -0.2, 1.0);\n vec3 c = mix(u_horizon, u_zenith, pow(max(h, 0.0), 0.55));\n c = mix(c, u_horizon * 0.9, (1.0 - smoothstep(-0.2, 0.0, d.y)));\n"
            + " float sd = max(dot(d, u_sunDir), 0.0);\n c += u_sunColor * (pow(sd, 350.0) * 2.0 + pow(sd, 12.0) * 0.22);\n"
            + " if (d.y > 0.02 && u_cloud.x > 0.0) { vec2 p = d.xz / (d.y + 0.12) * 0.11 + vec2(u_time * 0.004, u_time * 0.002);\n"
            + "  float n = texture2D(u_noise, p).r * 0.62 + texture2D(u_noise, p * 2.7 + 0.3).g * 0.38;\n float cl = smoothstep(1.0 - u_cloud.x * 0.62, 1.05 - u_cloud.x * 0.3, n) * smoothstep(0.02, 0.2, d.y);\n"
            + "  float shade = texture2D(u_noise, p + u_sunDir.xz * 0.02).r;\n vec3 cc = mix(u_horizon * 0.85 + 0.12, vec3(1.0) * (0.55 + 0.5 * length(u_sunColor) * 0.6), smoothstep(0.35, 0.75, shade));\n c = mix(c, cc, cl * 0.92); }\n"
            + " gl_FragColor = vec4(c, 1.0);\n}\n";

    // ------------------------------------------------------------------ additive sprites / unlit colored geometry (effects, gizmo, outlines)
    static final String SPR_V = "attribute vec3 a_position;\nattribute vec4 a_color;\nattribute vec2 a_texCoord0;\nuniform mat4 u_viewProj;\nvarying vec4 v_c;\nvarying vec2 v_uv;\nvoid main(){ v_c = a_color; v_uv = a_texCoord0; gl_Position = u_viewProj * vec4(a_position, 1.0); }\n";
    static final String SPR_F = "uniform sampler2D u_tex;\nvarying vec4 v_c;\nvarying vec2 v_uv;\nvoid main(){ vec4 t = texture2D(u_tex, v_uv); gl_FragColor = vec4(v_c.rgb * t.rgb, v_c.a * t.a); }\n";

    /** Blit of the scene buffer (resolution scale) with a light tone curve. */
    static final String BLIT_V = "attribute vec3 a_position;\nvarying vec2 v_uv;\nvoid main(){ v_uv = a_position.xy * 0.5 + 0.5; gl_Position = vec4(a_position.xy, 0.0, 1.0); }\n";
    static final String BLIT_F = "uniform sampler2D u_tex;\nvarying vec2 v_uv;\nvoid main(){ gl_FragColor = vec4(texture2D(u_tex, v_uv).rgb, 1.0); }\n";

    private static final ObjectMap<String, ShaderProgram> cache = new ObjectMap<>();

    public static ShaderProgram get(String key, String defines, String vs, String fs) {
        if (cache.containsKey(key)) return cache.get(key);
        ShaderProgram p;
        ShaderProgram.pedantic = false;
        p = new ShaderProgram(defines + vs, PRE + defines + fs);
        if (!p.isCompiled()) {
            Gdx.app.error("WB", "shader " + key + ":\n" + p.getLog());
            cache.put(key, null);
            return null;
        }
        cache.put(key, p);
        return p;
    }
    public static boolean has(String key) { return cache.containsKey(key); }
    public static void put(String key, ShaderProgram p) { cache.put(key, p); }

    /** Drops the compiled programs without freeing them (they belong to another graphics context). */
    public static void forget() { cache.clear(); }

    public static void dispose() {
        for (ShaderProgram p : cache.values()) if (p != null) p.dispose();
        cache.clear();
    }
}
