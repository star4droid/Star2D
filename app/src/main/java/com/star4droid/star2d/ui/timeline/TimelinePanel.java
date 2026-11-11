package com.star4droid.star2d.ui.timeline;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TimelineSession;
import com.star4droid.star2d.editor.items.Editor3DItem;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.IconMenu;
import com.star4droid.star2d.ui.NumberPad;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.TextDialog;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.template.anim.Interp;
import com.star4droid.template.anim.Timeline;

import java.util.ArrayList;

/**
 * Timeline panel (bottom sheet, side sheet in landscape): animations made of keys. Pick or create a timeline, select an
 * element, turn on Record, move the playhead and change the element on the canvas (or with ◆ in its properties): keys are
 * added by themselves. Drag keys to change their time, long-press a key for its transition (with a curve preview), copy or
 * delete. Play / scrub previews it on the canvas; the scene keeps its own values. Saved to timelines/&lt;name&gt;.s2tl.
 */
public class TimelinePanel implements PanelHost.Panel {
	/** A timeline is open (the Properties panel shows ◆ key buttons). */
	public static volatile boolean editing;
	private static String lastName = "";

	private final EditorShell shell;
	private LinearLayout root, emptyBox, editBox;
	private TimelineView view;
	private TextView nameChip, durChip, loopChip, timeText, hint;
	private ImageView playBtn, recBtn, undoBtn, redoBtn;
	private String selfName = "";

	public TimelinePanel(EditorShell shell) {
		this.shell = shell;
	}

	@Override
	public String title() {
		return Tr.t("tl_title", "Timeline");
	}

	@Override
	public int icon() {
		return R.drawable.ic_s2_timeline;
	}

	@Override
	public View[] headerActions(Context c) {
		return new View[]{Ui.iconButton(c, R.drawable.ic_s2_help, Tr.t("help", "Help"), v -> Dialogs.longText(c, Tr.t("tl_title", "Timeline"), Tr.t("tl_help",
				"1. Pick or create a timeline (its name is used by the blocks \"Play timeline\").\n"
						+ "2. Select an element and press ● Record.\n"
						+ "3. Move the playhead (drag on the ruler) and change the element: move, rotate, resize it on the canvas, or press ◆ next to a property in its Properties. A key is added at the playhead.\n"
						+ "4. Drag a key to change its time. Long-press a key: transition (smooth, bounce, elastic...), value, copy, delete.\n"
						+ "5. ▶ previews it. The scene keeps its own values: they come back when you close the timeline.\n\n"
						+ "Settings (⋮): length, once / loop / ping-pong, play when the scene starts, relative values, and the element tracks marked @self (the timeline can then play on any element: \"Play timeline on element\").\n"
						+ "Long-press the ruler to add a marker: \"When a timeline marker is passed\" runs there (sounds, effects).")))};
	}

	@Override
	public View view(Context c) {
		if (root != null) return root;
		root = Ui.column(c);
		root.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 6));
		// ---- nothing open
		emptyBox = Ui.column(c);
		TextView t = Ui.text(c, Tr.t("tl_empty", "Animations made of keys: an element moves, turns, grows, fades or changes color over time. Open a timeline or make a new one."), 13, Ui.TEXT_3);
		t.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 8));
		emptyBox.addView(t);
		emptyBox.addView(Ui.button(c, R.drawable.ic_s2_add, Tr.t("tl_new", "New timeline"), true, v -> newTimeline(c)), Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 40)), c, 2, 2, 2, 6));
		emptyBox.addView(Ui.button(c, R.drawable.ic_s2_folder_open, Tr.t("tl_open", "Open a timeline"), false, v -> pickTimeline(c)), Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 40)), c, 2, 2, 2, 2));
		root.addView(emptyBox, Ui.matchWrap());
		// ---- editing
		editBox = Ui.column(c);
		LinearLayout top = Ui.row(c);
		top.setGravity(Gravity.CENTER_VERTICAL);
		nameChip = chip(c, "", v -> timelineMenu(c, v));
		nameChip.setTypeface(Typeface.DEFAULT_BOLD);
		top.addView(nameChip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
		durChip = chip(c, "", v -> editDuration(c));
		top.addView(durChip, Ui.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f), c, 6, 0, 0, 0));
		loopChip = chip(c, "", v -> pickLoop(c));
		top.addView(loopChip, Ui.margins(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), c, 6, 0, 0, 0));
		top.addView(Ui.iconButton(c, R.drawable.ic_s2_close, Tr.t("tl_close", "Close the timeline"), v -> closeTimeline()));
		editBox.addView(top, Ui.matchWrap());
		LinearLayout bar = Ui.row(c);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
		bar.addView(Ui.iconButton(c, R.drawable.ic_s2_skip, Tr.t("tl_start", "To the start"), v -> gl(s -> {
			s.setPlaying(false);
			s.seek(0);
		})));
		playBtn = Ui.iconButton(c, R.drawable.ic_s2_play, Tr.t("tl_play", "Play / pause"), v -> gl(s -> s.setPlaying(!s.playing)));
		bar.addView(playBtn);
		recBtn = Ui.iconButton(c, R.drawable.ic_s2_record, Tr.t("tl_record", "Record: changes become keys"), v -> gl(s -> s.setRecord(!s.record)));
		bar.addView(recBtn);
		timeText = Ui.text(c, "0.00 s", 13, Ui.TEXT);
		timeText.setTypeface(Typeface.MONOSPACE);
		timeText.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), 0);
		timeText.setOnClickListener(v -> editTime(c));
		bar.addView(timeText, Ui.weight(1));
		bar.addView(Ui.iconButton(c, R.drawable.ic_s2_keyframe, Tr.t("tl_add_key", "Add a key for the selected element"), v -> addKeyMenu(c)));
		undoBtn = Ui.iconButton(c, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> gl(TimelineSession::undo));
		redoBtn = Ui.iconButton(c, R.drawable.ic_s2_redo, Tr.t("redo", "Redo"), v -> gl(TimelineSession::redo));
		bar.addView(undoBtn);
		bar.addView(redoBtn);
		bar.addView(Ui.iconButton(c, R.drawable.ic_s2_settings, Tr.t("tl_settings", "Timeline settings"), v -> settings(c)));
		editBox.addView(bar, Ui.matchWrap());
		view = new TimelineView(c, Ui.isRtl(c));
		view.listener = new TimelineView.Listener() {
			public void seek(float t) {
				gl(s -> {
					s.setPlaying(false);
					s.seek(t);
				});
			}

			public void moveKey(int track, int key, float t) {
				gl(s -> s.moveKey(track, key, t));
			}

			public void selectKey(int track, int key) {
				view.selTrack = track;
				view.selKey = key;
				view.invalidate();
				// the element of the track becomes the selected one
				if (view.tl != null && track >= 0 && track < view.tl.tracks.size()) {
					Timeline.Track tr = view.tl.tracks.get(track);
					String el = tr.element.equals("@self") || tr.element.isEmpty() ? selfName : tr.element;
					Bridge.gl(() -> {
						Actor a = Bridge.editor().findActor(el);
						if (a != null && a != Bridge.editor().getSelectedActor()) Bridge.editor().selectActor(a);
					});
				}
			}

			public void keyMenu(int track, int key, float rx, float ry) {
				TimelinePanel.this.keyMenu(c, track, key, rx, ry);
			}

			public void trackMenu(int track, float rx, float ry) {
				TimelinePanel.this.trackMenu(c, track, rx, ry);
			}

			public void rulerMenu(float t, float rx, float ry) {
				Dialogs.input(c, Tr.t("tl_add_marker", "Add a marker"), Tr.t("tl_marker_msg", "\"When a timeline marker is passed\" runs here with this name (sound, effect, damage...)."),
						"hit", v -> v.trim().isEmpty() ? Tr.t("ui_required", "Required") : null, name -> gl(s -> s.addMarker(t, name.trim())));
			}

			public void markerMenu(int marker, float rx, float ry) {
				IconMenu m = new IconMenu(c);
				m.add(R.drawable.ic_s2_edit, Tr.t("rename", "Rename"), () -> Dialogs.input(c, Tr.t("rename", "Rename"), "",
						view.tl.markers.get(marker).name, v -> v.trim().isEmpty() ? Tr.t("ui_required", "Required") : null, name -> gl(s -> s.renameMarker(marker, name.trim()))));
				m.add(R.drawable.ic_s2_delete, Ui.DANGER, Tr.t("delete", "Delete"), () -> gl(s -> s.deleteMarker(marker)));
				m.showAt(view, rx, ry);
			}
		};
		editBox.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 220)));
		hint = Ui.text(c, "", 12, Ui.TEXT_3);
		hint.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), 0);
		editBox.addView(hint, Ui.matchWrap());
		root.addView(editBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		// the strip takes the rest of the sheet's height
		root.addOnLayoutChangeListener((v, l, tp, r, b, ol, ot, or, ob) -> {
			int avail = b - tp - view.getTop() - (hint.getVisibility() == View.VISIBLE ? hint.getHeight() : 0) - Ui.dp(c, 6);
			int min = Ui.dp(c, 120);
			ViewGroup.LayoutParams lp = view.getLayoutParams();
			int want = Math.max(min, avail);
			if (Math.abs(lp.height - want) > 4 && avail > 0) {
				lp.height = want;
				view.post(() -> view.setLayoutParams(lp));
			}
		});
		showEditing(false);
		return root;
	}

	private TextView chip(Context c, String s, View.OnClickListener l) {
		TextView t = Ui.text(c, s, 13, Ui.TEXT);
		t.setPadding(Ui.dp(c, 10), Ui.dp(c, 7), Ui.dp(c, 8), Ui.dp(c, 7));
		t.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
		t.setMaxLines(1);
		t.setEllipsize(android.text.TextUtils.TruncateAt.END);
		t.setOnClickListener(l);
		return t;
	}

	private void showEditing(boolean on) {
		emptyBox.setVisibility(on ? View.GONE : View.VISIBLE);
		editBox.setVisibility(on ? View.VISIBLE : View.GONE);
	}

	interface S {
		void run(TimelineSession s);
	}

	/** Runs on the GL thread with the open session. */
	private void gl(S work) {
		Bridge.gl(() -> {
			TimelineSession s = Bridge.editor().timeline;
			if (s != null) work.run(s);
		});
	}

	@Override
	public void onShown() {
		if (!Bridge.ready()) return;
		Bridge.query(() -> Bridge.editor().timeline != null, open -> {
			if (Boolean.TRUE.equals(open)) attach();
			else showEditing(false);
		});
	}

	// ------------------------------------------------------------------ session

	private void open(String name) {
		lastName = name;
		Bridge.query(() -> {
			TimelineSession s = TimelineSession.open(Bridge.editor(), name);
			return s != null;
		}, ok -> attach());
	}

	/** Listens to the open session and shows it. */
	private void attach() {
		editing = true;
		showEditing(true);
		Bridge.gl(() -> {
			TimelineSession s = Bridge.editor().timeline;
			if (s == null) return;
			s.listener = (ss, changed) -> {
				final String json = changed || view.tl == null ? ss.tl.toJson() : null;
				final float t = ss.time;
				final boolean p = ss.playing, r = ss.record, u = ss.canUndo(), re = ss.canRedo(), warn = ss.warn;
				final String target = ss.tl.target == null ? "" : ss.tl.target;
				final boolean closed = Bridge.editor().timeline != ss;
				ss.warn = false;
				Ui.ui(() -> sync(json, t, p, r, u, re, warn, target, closed));
			};
			s.listener.changed(s, true);
		});
	}

	private void sync(String json, float t, boolean playing, boolean record, boolean canUndo, boolean canRedo, boolean warn, String target, boolean closed) {
		if (view == null) return;
		Context c = view.getContext();
		if (closed) {
			editing = false;
			showEditing(false);
			return;
		}
		if (json != null) {
			view.tl = Timeline.parse(json);
			if (view.selTrack >= view.tl.tracks.size()) view.selTrack = -1;
			nameChip.setText(view.tl.name + "  ▾");
			durChip.setText(fmt(view.tl.duration) + " s");
			loopChip.setText(loopLabel(view.tl.loop));
		}
		selfName = target;
		view.selfName = target;
		view.time = t;
		view.record = record;
		if (playing) view.follow();
		view.invalidate();
		timeText.setText(String.format(java.util.Locale.ROOT, "%.2f / %.2f s", t, view.tl == null ? 0f : view.tl.duration));
		playBtn.setImageResource(playing ? R.drawable.ic_s2_pause : R.drawable.ic_s2_play);
		recBtn.setImageTintList(ColorStateList.valueOf(record ? Ui.DANGER : Ui.TEXT));
		undoBtn.setAlpha(canUndo ? 1f : 0.35f);
		redoBtn.setAlpha(canRedo ? 1f : 0.35f);
		boolean empty = view.tl == null || view.tl.tracks.isEmpty();
		hint.setVisibility(empty || record ? View.VISIBLE : View.GONE);
		hint.setText(record ? Tr.t("tl_rec_hint", "Recording: move the playhead, then move / turn / resize the element: a key is added at the playhead.")
				: Tr.t("tl_start_hint", "Select an element, press ● Record, move the playhead and change the element: keys are added by themselves."));
		if (warn) Ui.toast(c, Tr.t("tl_warn_record", "This property is animated: turn on ● Record to change its key (the timeline shows its own value)."));
	}

	private void closeTimeline() {
		Bridge.query(() -> {
			TimelineSession s = Bridge.editor().timeline;
			if (s != null) {
				s.listener = null;
				s.close();
			}
			return Boolean.TRUE;
		}, ok -> {
			editing = false;
			showEditing(false);
		});
	}

	// ------------------------------------------------------------------ timelines

	private void newTimeline(Context c) {
		Bridge.query(() -> TimelineSession.names(Bridge.editor()), names -> {
			int n = 1;
			while (names.contains("anim" + n)) n++;
			Dialogs.input(c, Tr.t("tl_new", "New timeline"), Tr.t("tl_name_msg", "Name used by the blocks (letters, digits, _)."), "anim" + n, v -> {
				String err = Dialogs.checkName(v.trim());
				if (err != null) return err;
				return names.contains(v.trim()) ? Tr.t("ui_name_used", "Name already used") : null;
			}, v -> open(v.trim()));
		});
	}

	private void pickTimeline(Context c) {
		Bridge.query(() -> TimelineSession.names(Bridge.editor()), names -> {
			if (names.isEmpty()) {
				newTimeline(c);
				return;
			}
			String[] arr = names.toArray(new String[0]);
			Dialogs.choose(c, Tr.t("tl_open", "Open a timeline"), arr, i -> open(arr[i]));
		});
	}

	private void timelineMenu(Context c, View anchor) {
		IconMenu m = new IconMenu(c);
		m.add(R.drawable.ic_s2_folder_open, Tr.t("tl_open", "Open a timeline"), () -> pickTimeline(c));
		m.add(R.drawable.ic_s2_add, Tr.t("tl_new", "New timeline"), () -> newTimeline(c));
		m.add(R.drawable.ic_s2_edit, Tr.t("rename", "Rename"), () -> renameTimeline(c));
		m.add(R.drawable.ic_s2_copy, Tr.t("tl_duplicate", "Duplicate"), () -> duplicateTimeline(c));
		m.add(R.drawable.ic_s2_delete, Ui.DANGER, Tr.t("delete", "Delete"), () -> deleteTimeline(c));
		m.show(anchor);
	}

	private void renameTimeline(Context c) {
		if (view.tl == null) return;
		String old = view.tl.name;
		Bridge.query(() -> TimelineSession.names(Bridge.editor()), names -> Dialogs.input(c, Tr.t("rename", "Rename"), Tr.t("tl_rename_msg", "Blocks that play it by its old name must be changed too."), old, v -> {
			String err = Dialogs.checkName(v.trim());
			if (err != null) return err;
			return !v.trim().equals(old) && names.contains(v.trim()) ? Tr.t("ui_name_used", "Name already used") : null;
		}, v -> {
			String nn = v.trim();
			if (nn.equals(old)) return;
			Bridge.query(() -> {
				LibgdxEditor ed = Bridge.editor();
				TimelineSession s = ed.timeline;
				if (s == null) return Boolean.FALSE;
				String json = s.tl.toJson();
				s.listener = null;
				s.close();
				new java.io.File(TimelineSession.folder(ed) + old + Timeline.EXT).renameTo(new java.io.File(TimelineSession.folder(ed) + nn + Timeline.EXT));
				com.badlogic.gdx.Gdx.files.absolute(TimelineSession.folder(ed) + nn + Timeline.EXT).writeString(json.replace("\"name\": \"" + old + "\"", "\"name\": \"" + nn + "\""), false, "UTF-8");
				return Boolean.TRUE;
			}, ok -> open(nn));
		}));
	}

	private void duplicateTimeline(Context c) {
		if (view.tl == null) return;
		String old = view.tl.name;
		Bridge.query(() -> TimelineSession.names(Bridge.editor()), names -> {
			int n = 2;
			while (names.contains(old + n)) n++;
			Dialogs.input(c, Tr.t("tl_duplicate", "Duplicate"), "", old + n, v -> {
				String err = Dialogs.checkName(v.trim());
				if (err != null) return err;
				return names.contains(v.trim()) ? Tr.t("ui_name_used", "Name already used") : null;
			}, v -> {
				String nn = v.trim();
				Bridge.query(() -> {
					LibgdxEditor ed = Bridge.editor();
					TimelineSession s = ed.timeline;
					if (s == null) return Boolean.FALSE;
					Timeline copy = s.tl.copy();
					copy.name = nn;
					com.badlogic.gdx.Gdx.files.absolute(TimelineSession.folder(ed) + nn + Timeline.EXT).writeString(copy.toJson(), false, "UTF-8");
					return Boolean.TRUE;
				}, ok -> open(nn));
			});
		});
	}

	private void deleteTimeline(Context c) {
		if (view.tl == null) return;
		String name = view.tl.name;
		Dialogs.confirm(c, Tr.t("delete", "Delete"), name + "\n" + Tr.t("areYouSure", "Are you sure?"), Tr.t("delete", "Delete"), () -> Bridge.query(() -> {
			LibgdxEditor ed = Bridge.editor();
			TimelineSession s = ed.timeline;
			if (s != null) {
				s.listener = null;
				s.close();
			}
			new java.io.File(TimelineSession.folder(ed) + name + Timeline.EXT).delete();
			return Boolean.TRUE;
		}, ok -> {
			editing = false;
			showEditing(false);
		}));
	}

	// ------------------------------------------------------------------ settings

	private static String fmt(float v) {
		return v == (int) v ? String.valueOf((int) v) : String.valueOf(Math.round(v * 100f) / 100f);
	}

	private static String loopLabel(int loop) {
		return loop == Timeline.LOOP ? "⟳ " + Tr.t("tl_loop", "Loop") : loop == Timeline.PING_PONG ? "⇄ " + Tr.t("tl_ping_pong", "Ping-pong") : "→ " + Tr.t("tl_once", "Once");
	}

	private void editDuration(Context c) {
		if (view.tl == null) return;
		NumberPad.show(c, Tr.t("tl_length", "Length (seconds)"), fmt(view.tl.duration), v -> {
			float d;
			try {
				d = Float.parseFloat(v.trim());
			} catch (Exception e) {
				return;
			}
			final float dd = d;
			gl(s -> s.settings(dd, s.tl.loop, s.tl.relative, s.tl.playOnStart, s.tl.target));
		}, null);
	}

	private void editTime(Context c) {
		NumberPad.show(c, Tr.t("tl_time", "Playhead (seconds)"), fmt(view.time), v -> {
			try {
				float t = Float.parseFloat(v.trim());
				gl(s -> s.seek(t));
			} catch (Exception ignored) {
			}
		}, null);
	}

	private void pickLoop(Context c) {
		String[] items = {loopLabel(Timeline.ONCE), loopLabel(Timeline.LOOP), loopLabel(Timeline.PING_PONG)};
		Dialogs.choose(c, Tr.t("tl_loop_mode", "Plays"), items, i -> gl(s -> s.settings(s.tl.duration, i, s.tl.relative, s.tl.playOnStart, s.tl.target)));
	}

	private void settings(Context c) {
		if (view.tl == null) return;
		Timeline tl = view.tl;
		LinearLayout box = Ui.column(c);
		box.setPadding(Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 18), 0);
		SwitchCompat start = new SwitchCompat(c);
		start.setText(Tr.t("tl_play_start", "Play when the scene starts"));
		start.setTextColor(Ui.TEXT);
		start.setChecked(tl.playOnStart);
		box.addView(start, Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 4));
		SwitchCompat rel = new SwitchCompat(c);
		rel.setText(Tr.t("tl_relative", "Relative values (added to where the element is)"));
		rel.setTextColor(Ui.TEXT);
		rel.setChecked(tl.relative);
		box.addView(rel, Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 4));
		box.addView(Ui.text(c, Tr.t("tl_self", "Element of the @self tracks (the timeline can then play on any element):"), 12.5f, Ui.TEXT_2));
		final String[] target = {tl.target == null ? "" : tl.target};
		TextView tgt = chip(c, target[0].isEmpty() ? "—" : target[0], null);
		tgt.setOnClickListener(v -> Bridge.query(() -> new ArrayList<>(Bridge.editor().getBodiesList()), names -> {
			String[] arr = new String[names.size() + 1];
			arr[0] = "— " + Tr.t("ui_none", "None");
			for (int i = 0; i < names.size(); i++) arr[i + 1] = names.get(i);
			Dialogs.choose(c, Tr.t("tl_self_title", "@self element"), arr, i -> {
				target[0] = i == 0 ? "" : names.get(i - 1);
				tgt.setText(target[0].isEmpty() ? "—" : target[0]);
			});
		}));
		box.addView(tgt, Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 8));
		TextView info = Ui.text(c, Tr.t("tl_settings_info", "Blocks: \"Play timeline\" (its own elements) or \"Play timeline on element\" (@self tracks move that element)."), 11.5f, Ui.TEXT_3);
		box.addView(info);
		new AlertDialog.Builder(c)
				.setTitle(Tr.t("tl_settings", "Timeline settings"))
				.setView(box)
				.setPositiveButton(Tr.t("ok", "OK"), (d, w) -> {
					final boolean ps = start.isChecked(), rl = rel.isChecked();
					final String tg = target[0];
					gl(s -> s.settings(s.tl.duration, s.tl.loop, rl, ps, tg));
				})
				.setNegativeButton(Tr.t("cancel", "Cancel"), null)
				.show();
	}

	// ------------------------------------------------------------------ keys

	/** ◆: the properties of the selected element; the chosen one gets a key at the playhead with its current value. */
	private void addKeyMenu(Context c) {
		Bridge.query(() -> {
			Actor a = Bridge.editor().getSelectedActor();
			if (!(a instanceof EditorItem)) return null;
			PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
			ArrayList<String> props = new ArrayList<>();
			boolean d3 = a instanceof Editor3DItem;
			for (String p : d3 ? TimelineSession.AUTO_3D : TimelineSession.AUTO_2D) if (d3 || ps.containsKey(p)) props.add(p);
			if (!d3) props.add("Opacity");
			for (com.star4droid.star2d.behaviors.ObjectVarsGen.Var v : com.star4droid.star2d.behaviors.ObjectVarsGen.parse(ps.getString(com.star4droid.star2d.behaviors.ObjectVarsGen.PROPERTY))) {
				String t = com.star4droid.star2d.behaviors.ObjectVarsGen.normType(v.type);
				if (t.equals("int") || t.equals("float") || t.equals("double") || t.equals("String") || t.equals("boolean")) props.add("var:" + v.name);
			}
			return new Object[]{a.getName(), props};
		}, r -> {
			if (r == null) {
				Ui.toast(c, Tr.t("tl_select_element", "Select an element first (tap it on the canvas)."));
				return;
			}
			String el = (String) r[0];
			ArrayList<String> props = (ArrayList<String>) r[1];
			String[] labels = new String[props.size()];
			for (int i = 0; i < labels.length; i++) labels[i] = props.get(i).startsWith("var:") ? props.get(i) : Tr.t(props.get(i));
			Dialogs.choose(c, Tr.t("tl_add_key", "Add a key") + " · " + el, labels, i -> {
				String p = props.get(i);
				if (p.startsWith("var:")) {
					Dialogs.input(c, p, Tr.t("tl_var_value", "Value at the playhead"), "0", v -> null, v -> gl(s -> s.setKey(el, p, s.time, v.trim(), null)));
				} else gl(s -> s.keyNow(el, p));
			});
		});
	}

	private void keyMenu(Context c, int track, int key, float rx, float ry) {
		if (view.tl == null || track < 0 || track >= view.tl.tracks.size()) return;
		Timeline.Track tr = view.tl.tracks.get(track);
		if (key < 0 || key >= tr.keys.size()) return;
		Timeline.Key k = tr.keys.get(key);
		IconMenu m = new IconMenu(c);
		m.add(R.drawable.ic_s2_timeline, Tr.t("tl_transition", "Transition") + ": " + Tr.t("ease_" + k.interp, k.interp), () -> chooseInterp(c, track, key, k.interp));
		m.add(R.drawable.ic_s2_edit, Tr.t("tl_value", "Value") + ": " + k.v, () -> editValue(c, track, key, tr.property, k.v));
		m.add(R.drawable.ic_s2_copy, Tr.t("copy", "Copy"), () -> gl(s -> s.copyKey(track, key)));
		m.add(R.drawable.ic_s2_paste, Tr.t("tl_paste_here", "Paste at the playhead"), () -> gl(s -> s.pasteKey(track)));
		m.add(R.drawable.ic_s2_delete, Ui.DANGER, Tr.t("delete", "Delete"), () -> gl(s -> s.deleteKey(track, key)));
		m.showAt(view, rx, ry);
	}

	private void trackMenu(Context c, int track, float rx, float ry) {
		if (view.tl == null || track < 0 || track >= view.tl.tracks.size()) return;
		Timeline.Track tr = view.tl.tracks.get(track);
		String el = tr.element.equals("@self") || tr.element.isEmpty() ? selfName : tr.element;
		IconMenu m = new IconMenu(c);
		m.add(R.drawable.ic_s2_keyframe, Tr.t("tl_key_here", "Key at the playhead"), () -> gl(s -> s.keyNow(el, tr.property)));
		m.add(R.drawable.ic_s2_paste, Tr.t("tl_paste_here", "Paste at the playhead"), () -> gl(s -> s.pasteKey(track)));
		m.add(R.drawable.ic_s2_delete, Ui.DANGER, Tr.t("tl_delete_track", "Delete this track"), () -> gl(s -> s.deleteTrack(track)));
		m.showAt(view, rx, ry);
	}

	/** Transitions with a preview of their curve. */
	private void chooseInterp(Context c, int track, int key, String current) {
		GridLayout g = new GridLayout(c);
		g.setColumnCount(Ui.isLandscape(c) ? 5 : 3);
		g.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), 0);
		final AlertDialog[] dlg = new AlertDialog[1];
		for (String name : Interp.NAMES) {
			LinearLayout tile = Ui.column(c);
			tile.setGravity(Gravity.CENTER_HORIZONTAL);
			tile.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 6));
			tile.setBackground(Ui.ripple(name.equals(current) ? 0x332196F3 : 0, Ui.dp(c, 8)));
			tile.addView(new CurveView(c, name, name.equals(current)), new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 52)));
			TextView t = Ui.text(c, Tr.t("ease_" + name, name), 11.5f, name.equals(current) ? Ui.ACCENT : Ui.TEXT_2);
			t.setGravity(Gravity.CENTER);
			tile.addView(t);
			tile.setOnClickListener(v -> {
				gl(s -> s.setInterp(track, key, name));
				if (dlg[0] != null) dlg[0].dismiss();
			});
			GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
			lp.width = 0;
			lp.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
			g.addView(tile, lp);
		}
		ScrollView sv = new ScrollView(c);
		sv.addView(g);
		TextView note = Ui.text(c, Tr.t("tl_transition_note", "How the value moves from the previous key to this one."), 12, Ui.TEXT_3);
		LinearLayout col = Ui.column(c);
		note.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), 0);
		col.addView(note);
		col.addView(sv);
		dlg[0] = new AlertDialog.Builder(c).setTitle(Tr.t("tl_transition", "Transition")).setView(col).setNegativeButton(Tr.t("cancel", "Cancel"), null).show();
	}

	private void editValue(Context c, int track, int key, String prop, String value) {
		if (NumberPad.isNumber(value)) {
			NumberPad.show(c, prop, value, v -> gl(s -> s.setKeyValue(track, key, v.trim())), () -> TextDialog.show(c, prop, "", value, v -> gl(s -> s.setKeyValue(track, key, v))));
		} else if (prop.equals("Tint") || prop.equals("Color")) {
			com.star4droid.star2d.ui.ColorPickerDialog.show(c, prop, value, null, hex -> {
				if (hex != null) gl(s -> s.setKeyValue(track, key, hex));
			});
		} else {
			TextDialog.show(c, prop, "", value, v -> gl(s -> s.setKeyValue(track, key, v)));
		}
	}

	/** Properties panel: ◆ next to a property adds a key with its current value. */
	public static void keyFromProperties(String property) {
		Bridge.gl(() -> {
			LibgdxEditor ed = Bridge.editor();
			TimelineSession s = ed.timeline;
			Actor a = ed.getSelectedActor();
			if (s == null || a == null || a.getName() == null) return;
			s.keyNow(a.getName(), property);
		});
	}
}
