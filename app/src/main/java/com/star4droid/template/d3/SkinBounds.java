package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;

/**
 * Bounds of a model as it is drawn. ModelInstance.calculateBoundingBox measures skinned meshes in their bind space (exported
 * characters are often 2 units tall and centered there while the drawn character stands 1 m tall on its feet), so sizes,
 * feet, colliders and ground checks of animated characters were wrong. Here skinned parts go through their bones (the pose of
 * the instance: the rest pose before any animation), a sample of the vertices (fast for big meshes).
 */
public final class SkinBounds {
	private SkinBounds() {
	}

	/** True when a part of the model is skinned. */
	public static boolean skinned(ModelInstance inst) {
		for (Node n : inst.nodes) if (skinned(n)) return true;
		return false;
	}

	private static boolean skinned(Node n) {
		for (NodePart p : n.parts) if (p.bones != null && p.bones.length > 0 && p.invBoneBindTransforms != null) return true;
		for (Node c : n.getChildren()) if (skinned(c)) return true;
		return false;
	}

	/** Bounds of the drawn model (local to the instance); false when nothing could be measured. */
	public static boolean calculate(ModelInstance inst, BoundingBox out) {
		out.inf();
		inst.calculateTransforms();
		Vector3 v = new Vector3(), acc = new Vector3(), t = new Vector3();
		Matrix4 m = new Matrix4();
		for (Node n : inst.nodes) add(n, out, v, acc, t, m);
		return out.isValid();
	}

	private static void add(Node n, BoundingBox out, Vector3 v, Vector3 acc, Vector3 t, Matrix4 m) {
		for (NodePart p : n.parts) {
			if (!p.enabled || p.meshPart == null) continue;
			if (p.bones != null && p.bones.length > 0 && p.invBoneBindTransforms != null) {
				for (int i = 0; i < p.bones.length && i < p.invBoneBindTransforms.size; i++)
					p.bones[i].set(p.invBoneBindTransforms.keys[i].globalTransform).mul(p.invBoneBindTransforms.values[i]);
				skinnedPart(p, out, v, acc, t);
			} else {
				BoundingBox b = new BoundingBox();
				p.meshPart.mesh.calculateBoundingBox(b, p.meshPart.offset, p.meshPart.size);
				out.ext(b.mul(m.set(n.globalTransform)));
			}
		}
		for (Node c : n.getChildren()) add(c, out, v, acc, t, m);
	}

	private static void skinnedPart(NodePart p, BoundingBox out, Vector3 v, Vector3 acc, Vector3 t) {
		MeshPart mp = p.meshPart;
		Mesh mesh = mp.mesh;
		VertexAttributes at = mesh.getVertexAttributes();
		VertexAttribute pos = at.findByUsage(VertexAttributes.Usage.Position);
		if (pos == null) return;
		Array<VertexAttribute> bw = new Array<VertexAttribute>();
		for (VertexAttribute a : at) if (a.usage == VertexAttributes.Usage.BoneWeight) bw.add(a);
		int stride = mesh.getVertexSize() / 4, po = pos.offset / 4;
		int nv = mesh.getNumVertices();
		float[] verts = new float[nv * stride];
		mesh.getVertices(verts);
		// the vertices this part draws (indexed meshes share a vertex buffer between parts)
		int step = Math.max(1, nv / 4000);
		if (mesh.getNumIndices() > 0) {
			short[] idx = new short[mesh.getNumIndices()];
			mesh.getIndices(idx);
			int end = Math.min(idx.length, mp.offset + mp.size);
			int istep = Math.max(1, (end - mp.offset) / 6000);
			for (int k = mp.offset; k < end; k += istep) one(verts, (idx[k] & 0xFFFF) * stride, po, bw, p.bones, out, v, acc, t);
		} else {
			for (int k = 0; k < nv; k += step) one(verts, k * stride, po, bw, p.bones, out, v, acc, t);
		}
	}

	private static void one(float[] verts, int base, int po, Array<VertexAttribute> bw, Matrix4[] bones, BoundingBox out, Vector3 v, Vector3 acc, Vector3 t) {
		if (base + po + 2 >= verts.length) return;
		v.set(verts[base + po], verts[base + po + 1], verts[base + po + 2]);
		acc.setZero();
		float sum = 0;
		for (int i = 0; i < bw.size; i++) {
			int o = base + bw.get(i).offset / 4;
			int bone = (int) verts[o];
			float w = verts[o + 1];
			if (w <= 0 || bone < 0 || bone >= bones.length) continue;
			acc.mulAdd(t.set(v).mul(bones[bone]), w);
			sum += w;
		}
		if (sum > 0.0001f) out.ext(acc.scl(1f / sum));
		else out.ext(v);
	}
}
