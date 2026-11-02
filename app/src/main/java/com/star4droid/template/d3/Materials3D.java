package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;

import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;

/** Material helpers: the element's look (color, texture, opacity, metal, emission) for every render mode. */
public final class Materials3D {
	private Materials3D() {
	}

	public static Color color(String hex, Color fallback) {
		if (hex == null || hex.length() == 0) return new Color(fallback);
		try {
			return Color.valueOf(hex.startsWith("#") ? hex.substring(1) : hex);
		} catch (Exception e) {
			return new Color(fallback);
		}
	}

	/** glTF materials carry their color as PBR base color: copy it to the default shader's diffuse too. */
	public static void fixImported(Model m) {
		for (Material mat : m.materials) {
			PBRColorAttribute base = (PBRColorAttribute) mat.get(PBRColorAttribute.BaseColorFactor);
			if (base != null && !mat.has(ColorAttribute.Diffuse)) mat.set(ColorAttribute.createDiffuse(base.color));
			PBRColorAttribute em = (PBRColorAttribute) mat.get(PBRColorAttribute.Emissive);
			if (em != null && !mat.has(ColorAttribute.Emissive)) mat.set(ColorAttribute.createEmissive(em.color));
			if (!mat.has(ColorAttribute.Specular)) mat.set(ColorAttribute.createSpecular(0.08f, 0.08f, 0.08f, 1f));
		}
	}

	/** Look of a primitive / terrain (replace = also for models that don't keep their own material). */
	public static void apply(ModelInstance mi, Look look, boolean replace) {
		for (Material mat : mi.materials) {
			if (!replace) {
				// keep the model's material, only opacity / emission / double side / tint multiply
				if (look.opacity < 1f) setOpacity(mat, look.opacity);
				if (look.doubleSided) mat.set(IntAttribute.createCullFace(0));
				if (look.tintModel && look.color != null) {
					ColorAttribute d = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
					if (d != null) d.color.mul(look.color);
				}
				continue;
			}
			Color c = look.color == null ? Color.WHITE : look.color;
			mat.set(ColorAttribute.createDiffuse(c));
			mat.set(PBRColorAttribute.createBaseColorFactor(c));
			mat.set(PBRFloatAttribute.createMetallic(look.metallic));
			mat.set(PBRFloatAttribute.createRoughness(look.roughness));
			float spec = (1f - look.roughness) * 0.6f + look.metallic * 0.3f;
			mat.set(ColorAttribute.createSpecular(spec, spec, spec, 1f));
			mat.set(FloatAttribute.createShininess(Math.max(1f, look.shininess)));
			if (look.texture != null) {
				TextureAttribute t = TextureAttribute.createDiffuse(look.texture);
				t.scaleU = look.repeatX;
				t.scaleV = look.repeatY;
				mat.set(t);
			} else mat.remove(TextureAttribute.Diffuse);
			if (look.normalMap != null) {
				PBRTextureAttribute n = PBRTextureAttribute.createNormalTexture(look.normalMap);
				n.scaleU = look.repeatX;
				n.scaleV = look.repeatY;
				mat.set(n);
			}
			if (look.emissive != null && (look.emissive.r + look.emissive.g + look.emissive.b) > 0.001f) {
				Color e = new Color(look.emissive).mul(look.emissiveIntensity);
				e.a = 1f;
				mat.set(ColorAttribute.createEmissive(e));
				mat.set(PBRColorAttribute.createEmissive(e));
			} else {
				mat.remove(ColorAttribute.Emissive);
				mat.remove(PBRColorAttribute.Emissive);
			}
			setOpacity(mat, look.opacity);
			if (look.doubleSided) mat.set(IntAttribute.createCullFace(0));
			else mat.remove(IntAttribute.CullFace);
		}
	}

	public static void setOpacity(Material mat, float opacity) {
		if (opacity < 0.999f) {
			BlendingAttribute b = (BlendingAttribute) mat.get(BlendingAttribute.Type);
			if (b == null) mat.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, opacity));
			else {
				b.blended = true;
				b.opacity = opacity;
			}
		} else {
			BlendingAttribute b = (BlendingAttribute) mat.get(BlendingAttribute.Type);
			if (b != null && b.opacity < 1f) mat.remove(BlendingAttribute.Type);
		}
	}

	/** All the inputs of a look. */
	public static class Look {
		public Color color = new Color(Color.WHITE), emissive;
		public Texture texture, normalMap;
		public float repeatX = 1, repeatY = 1, opacity = 1, metallic = 0, roughness = 0.8f, emissiveIntensity = 1, shininess = 16;
		public boolean doubleSided, tintModel;
	}
}
