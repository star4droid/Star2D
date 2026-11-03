package com.star4droid.template.d3;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSlider;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisWindow;
import com.star4droid.template.Items.StageImp;

/**
 * Ready-made graphics settings window for 3D games (block "show graphics settings"): quality presets, render scale,
 * view distance, shadows, water, effects and frame limit. The choice is saved for the player.
 * Texts follow the game language when it is Arabic, otherwise English.
 */
public final class GraphicsMenu3D {
	private GraphicsMenu3D() {
	}

	private static VisWindow open;

	private static String t(boolean ar, String en, String a) {
		return ar ? a : en;
	}

	public static void show(StageImp scene, boolean arabic) {
		if (scene == null) return;
		if (!VisUI.isLoaded()) VisUI.load(VisUI.SkinScale.X2);
		Stage ui = scene.activeStage().getUiStage();
		if (open != null) open.remove();
		final boolean ar = arabic;
		final VisWindow w = new VisWindow(t(ar, "Graphics", "الرسومات"));
		open = w;
		w.setModal(true);
		w.addCloseButton();
		w.closeOnEscape();
		w.defaults().pad(6).left();
		final VisLabel level = new VisLabel(t(ar, "Quality: ", "الجودة: ") + Quality3D.level);
		w.add(level).colspan(4).row();
		String[] levels = {Quality3D.LOW, Quality3D.MEDIUM, Quality3D.HIGH, Quality3D.ULTRA};
		String[] names = ar ? new String[]{"منخفضة", "متوسطة", "عالية", "فائقة"} : new String[]{"Low", "Medium", "High", "Ultra"};
		final VisSlider scale = new VisSlider(0.4f, 1f, 0.05f, false);
		final VisSlider dist = new VisSlider(40f, 500f, 10f, false);
		final VisCheckBox shadows = new VisCheckBox(t(ar, "Shadows", "الظلال"));
		final VisCheckBox water = new VisCheckBox(t(ar, "Detailed water", "ماء مفصل"));
		final VisCheckBox effects = new VisCheckBox(t(ar, "Screen effects", "تأثيرات الشاشة"));
		final VisCheckBox fps = new VisCheckBox(t(ar, "Limit to 30 FPS (saves battery)", "حد 30 إطاراً (يوفر البطارية)"));
		// setValue / setChecked fire change events: without this flag a preset button turned itself into CUSTOM
		// (and LOW's flat water became 1) through the listener below
		final boolean[] refreshing = {false};
		final Runnable refresh = new Runnable() {
			public void run() {
				refreshing[0] = true;
				level.setText(t(ar, "Quality: ", "الجودة: ") + Quality3D.level);
				scale.setValue(Quality3D.renderScale);
				dist.setValue(Quality3D.viewDistance);
				shadows.setChecked(Quality3D.shadowSize > 0);
				water.setChecked(Quality3D.waterLevel >= 2);
				effects.setChecked(Quality3D.postEffects);
				fps.setChecked(Quality3D.fpsLimit == 30);
				refreshing[0] = false;
			}
		};
		for (int i = 0; i < levels.length; i++) {
			final String l = levels[i];
			VisTextButton b = new VisTextButton(names[i]);
			b.addListener(new ChangeListener() {
				@Override
				public void changed(ChangeEvent event, Actor actor) {
					Quality3D.setLevel(l);
					Quality3D.save();
					refresh.run();
				}
			});
			w.add(b).width(170);
		}
		w.row();
		w.add(new VisLabel(t(ar, "Resolution", "الدقة"))).colspan(2);
		w.add(scale).colspan(2).width(300).row();
		w.add(new VisLabel(t(ar, "View distance", "مسافة الرؤية"))).colspan(2);
		w.add(dist).colspan(2).width(300).row();
		w.add(shadows).colspan(4).row();
		w.add(water).colspan(4).row();
		w.add(effects).colspan(4).row();
		w.add(fps).colspan(4).row();
		refresh.run();
		ChangeListener custom = new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if (refreshing[0]) return;
				if (actor == scale) Quality3D.renderScale = scale.getValue();
				else if (actor == dist) Quality3D.viewDistance = dist.getValue();
				else if (actor == shadows) Quality3D.shadowSize = shadows.isChecked() ? Math.max(1024, Quality3D.shadowSize) : 0;
				else if (actor == water) Quality3D.waterLevel = water.isChecked() ? 2 : 1;
				else if (actor == effects) Quality3D.postEffects = effects.isChecked();
				else if (actor == fps) Quality3D.fpsLimit = fps.isChecked() ? 30 : 60;
				else return;
				Quality3D.custom();
				Quality3D.save();
				level.setText(t(ar, "Quality: ", "الجودة: ") + Quality3D.level);
			}
		};
		scale.addListener(custom);
		dist.addListener(custom);
		shadows.addListener(custom);
		water.addListener(custom);
		effects.addListener(custom);
		fps.addListener(custom);
		VisTextButton close = new VisTextButton(t(ar, "Done", "تم"));
		close.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				w.fadeOut();
				open = null;
			}
		});
		w.add(close).colspan(4).align(Align.center).width(220).padTop(12);
		w.pack();
		w.centerWindow();
		ui.addActor(w.fadeIn());
	}

	public static void hide() {
		if (open != null) open.fadeOut();
		open = null;
	}
}
