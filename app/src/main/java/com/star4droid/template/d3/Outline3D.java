package com.star4droid.template.d3;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Pool;

/**
 * Outline (stroke) around an element: the editor's white selection stroke and the "Outline" property.
 * Inverted hull: the mesh is drawn again, pushed out along its normals, with its front faces culled.
 */
public final class Outline3D {
	private Outline3D() {
	}

	public static class Attr extends Attribute {
		public static final long Type = register("s2dOutline");
		public final Color color = new Color(Color.WHITE);
		public float width = 0.03f;

		public Attr(Color c, float w) {
			super(Type);
			color.set(c);
			width = w;
		}

		@Override
		public Attribute copy() {
			return new Attr(color, width);
		}

		@Override
		public int compareTo(Attribute o) {
			return type == o.type ? 0 : (type < o.type ? -1 : 1);
		}
	}

	private static final Array<Renderable> tmp = new Array<Renderable>();
	private static final Pool<Renderable> pool = new Pool<Renderable>() {
		@Override
		protected Renderable newObject() {
			return new Renderable();
		}
	};
	private static final Material OUT = new Material();

	/** Renderables of the outline of an instance, drawn by {@link OutlineShader}. */
	public static void collect(ModelInstance mi, Color color, float width, Array<Renderable> out) {
		tmp.clear();
		mi.getRenderables(tmp, pool);
		for (Renderable r : tmp) {
			if (r.meshPart.primitiveType != GL20.GL_TRIANGLES) continue;
			if (r.meshPart.mesh.getVertexAttribute(VertexAttributes.Usage.Normal) == null) continue;
			Renderable o = pool.obtain();
			o.set(r);
			Material m = new Material();
			m.set(new Attr(color, width));
			o.material = m;
			o.environment = null;
			o.shader = null;
			out.add(o);
		}
		tmp.clear();
	}

	public static void free(Array<Renderable> list) {
		pool.freeAll(list);
		list.clear();
	}

	public static class OutlineShader implements Shader {
		private static ShaderProgram program;
		private RenderContext context;
		private final boolean skinned;

		public OutlineShader(Renderable r) {
			skinned = false;
		}

		@Override
		public void init() {
			if (program != null) return;
			String v = "attribute vec3 a_position;\nattribute vec3 a_normal;\nuniform mat4 u_projViewTrans;\nuniform mat4 u_worldTrans;\nuniform float u_width;\n"
					+ "void main(){ vec4 wp = u_worldTrans * vec4(a_position, 1.0); vec3 n = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz); gl_Position = u_projViewTrans * vec4(wp.xyz + n * u_width, 1.0); }";
			String f = "#ifdef GL_ES\nprecision mediump float;\n#endif\nuniform vec4 u_color;\nvoid main(){ gl_FragColor = u_color; }";
			program = new ShaderProgram(v, f);
			if (!program.isCompiled()) throw new GdxRuntimeException(program.getLog());
		}

		@Override
		public int compareTo(Shader other) {
			return 0;
		}

		@Override
		public boolean canRender(Renderable r) {
			return r.material != null && r.material.has(Attr.Type);
		}

		@Override
		public void begin(Camera camera, RenderContext context) {
			this.context = context;
			program.bind();
			program.setUniformMatrix("u_projViewTrans", camera.combined);
			context.setDepthTest(GL20.GL_LEQUAL);
			context.setDepthMask(true);
			context.setCullFace(GL20.GL_FRONT);
			context.setBlending(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		}

		@Override
		public void render(Renderable r) {
			Attr a = (Attr) r.material.get(Attr.Type);
			program.setUniformMatrix("u_worldTrans", r.worldTransform);
			program.setUniformf("u_color", a.color);
			program.setUniformf("u_width", a.width);
			r.meshPart.render(program);
		}

		@Override
		public void end() {
			if (context != null) context.setCullFace(GL20.GL_BACK);
		}

		@Override
		public void dispose() {
		}
	}
}
