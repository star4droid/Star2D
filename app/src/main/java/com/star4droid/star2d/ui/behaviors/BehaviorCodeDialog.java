package com.star4droid.star2d.ui.behaviors;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.BehaviorTemplate;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The (&lt;&gt;) dialog of a behavior: its Java source, read only, with "Close" and "Add as a new behavior" (a copy in the
 * project under a new class name, which the user can edit). The code scrolls in both directions inside the dialog; the two
 * buttons are a fixed bar under it, so they stay on screen whatever the length of the code or the size of the screen.
 */
final class BehaviorCodeDialog {
	/** Sources of the built-in behaviors, packed into the assets by the build (app/build.gradle: copyBehaviorSources). */
	static final String ASSET_DIR = "behavior_src";

	private BehaviorCodeDialog() {}

	/** The small round code button shown beside the (i) button. */
	static ImageView button(Context c, int sizeDp) {
		ImageView b = new ImageView(c);
		int size = Ui.dp(c, sizeDp);
		int pad = Ui.dp(c, sizeDp / 4f);
		b.setImageResource(R.drawable.ic_s2_code);
		b.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_2));
		b.setPadding(pad, pad, pad, pad);
		b.setScaleType(ImageView.ScaleType.FIT_CENTER);
		b.setBackground(Ui.ripple(0, size / 2f));
		b.setLayoutParams(new LinearLayout.LayoutParams(size, size));
		b.setContentDescription(Tr.t("bhv_code", "Code"));
		return b;
	}

	private static String simpleName(BehaviorMeta m) {
		String n = m.fqn == null ? "" : m.fqn;
		if (n.isEmpty() && m.file != null && !m.file.isEmpty()) n = new File(m.file).getName().replace(".java", "");
		int d = n.lastIndexOf('.');
		if (d >= 0) n = n.substring(d + 1);
		int s = n.indexOf('$');
		if (s >= 0) n = n.substring(0, s);
		return n.isEmpty() ? m.id : n;
	}

	/** The source of the behavior, or null when this build does not carry it. */
	static String source(Context c, BehaviorMeta m) {
		try {
			if (!m.builtin && m.file != null && !m.file.isEmpty()) return BehaviorCatalog.readFile(new File(m.file));
			InputStream in = c.getAssets().open(ASSET_DIR + "/" + simpleName(m) + ".java");
			try {
				ByteArrayOutputStream o = new ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
				return o.toString("UTF-8");
			} finally {
				in.close();
			}
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * @param projectPath the open project ("" = none: the copy button is hidden)
	 * @param added runs after a copy was written (the list of behaviors is read again)
	 */
	static void show(final Context c, final String projectPath, final BehaviorMeta m, final Runnable added) {
		final String src = source(c, m);
		final Dialog dlg = new Dialog(c);
		dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

		LinearLayout root = Ui.column(c);
		root.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(c, 16), Ui.BORDER, Ui.dp(c, 1)));
		root.setLayoutDirection(Ui.isRtl(c) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

		// header
		LinearLayout head = Ui.row(c);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 10));
		head.addView(BehaviorIcons.view(c, m.icon, 34, Ui.ACCENT));
		LinearLayout ht = Ui.column(c);
		ht.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
		TextView title = Ui.text(c, BehaviorText.name(m), 16.5f, Ui.TEXT);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setSingleLine(true);
		title.setEllipsize(android.text.TextUtils.TruncateAt.END);
		ht.addView(title);
		TextView sub = Ui.text(c, simpleName(m) + ".java  ·  " + (m.builtin ? Tr.t("bhv_builtin", "Built-in") : Tr.t("bhv_project", "Project")), 11.5f, Ui.TEXT_3);
		sub.setSingleLine(true);
		sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
		ht.addView(sub);
		head.addView(ht, Ui.weight(1));
		root.addView(head, Ui.matchWrap());
		root.addView(Ui.divider(c));

		// code (takes what is left between the header and the buttons)
		TextView code = Ui.text(c, "", 12, 0xFFD6DCE4);
		code.setTypeface(Typeface.MONOSPACE);
		code.setTextIsSelectable(true);
		code.setHorizontallyScrolling(true);
		code.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 14));
		code.setTextDirection(View.TEXT_DIRECTION_LTR);
		code.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
		if (src == null) {
			code.setTypeface(Typeface.DEFAULT);
			code.setHorizontallyScrolling(false);
			code.setText(Tr.t("bhv_code_missing", "The code of this behavior is not included in this version of the app."));
		} else code.setText(highlight(src));
		HorizontalScrollView hs = new HorizontalScrollView(c);
		hs.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
		hs.addView(code, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		ScrollView sv = new ScrollView(c);
		sv.setBackgroundColor(0xFF15181C);
		sv.addView(hs, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		root.addView(Ui.divider(c));

		// buttons: always on screen (fixed bar, equal widths, text wraps instead of being cut)
		LinearLayout bar = Ui.row(c);
		bar.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 12));
		TextView close = barButton(c, Tr.t("close", "Close"), false);
		close.setOnClickListener(v -> dlg.dismiss());
		bar.addView(close, Ui.weight(1));
		final boolean canCopy = src != null && projectPath != null && !projectPath.isEmpty();
		if (canCopy) {
			TextView add = barButton(c, Tr.t("bhv_code_add", "Add as a new behavior"), true);
			add.setOnClickListener(v -> copy(c, projectPath, m, src, () -> {
				dlg.dismiss();
				if (added != null) added.run();
			}));
			LinearLayout.LayoutParams lp = Ui.weight(1.6f);
			lp.setMarginStart(Ui.dp(c, 8));
			bar.addView(add, lp);
		}
		root.addView(bar, Ui.matchWrap());

		dlg.setContentView(root);
		dlg.show();
		Window w = dlg.getWindow();
		if (w != null) {
			w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
			android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
			boolean land = Ui.isLandscape(c);
			int width = Math.min(dm.widthPixels - Ui.dp(c, 24), Ui.dp(c, land ? 760 : 560));
			int height = Math.min(dm.heightPixels - Ui.dp(c, land ? 32 : 96), Ui.dp(c, 640));
			w.setLayout(width, height);
			ViewGroup.LayoutParams lp = root.getLayoutParams();
			if (lp != null) { lp.width = ViewGroup.LayoutParams.MATCH_PARENT; lp.height = ViewGroup.LayoutParams.MATCH_PARENT; root.setLayoutParams(lp); }
		}
	}

	private static TextView barButton(Context c, String label, boolean primary) {
		TextView t = Ui.text(c, label, 14, primary ? Color.WHITE : Ui.TEXT);
		t.setTypeface(primary ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
		t.setGravity(Gravity.CENTER);
		t.setMaxLines(2);
		t.setMinHeight(Ui.dp(c, 44));
		t.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
		t.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),
				primary ? Ui.rounded(Ui.ACCENT, Ui.dp(c, 10)) : Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, Ui.dp(c, 1)), null));
		t.setClickable(true);
		return t;
	}

	/** Asks the class name and writes the copy into the project behaviors. */
	private static void copy(final Context c, final String projectPath, final BehaviorMeta m, final String src, final Runnable done) {
		final String old = simpleName(m);
		String first = "My" + old;
		int k = 2;
		while (new File(BehaviorCatalog.projectDir(projectPath), first + ".java").exists()) first = "My" + old + (k++);
		Dialogs.input(c, Tr.t("bhv_code_add", "Add as a new behavior"), Tr.t("bhv_new_hint", "Class name, starts with a capital letter (e.g. Wobble)"), first, v -> {
			String n = v.trim();
			if (!BehaviorTemplate.validName(n)) return Tr.t("bhv_bad_name", "Use letters and digits, starting with a capital letter");
			if (n.equals(old) || new File(BehaviorCatalog.projectDir(projectPath), n + ".java").exists()) return Tr.t("ui_name_used", "Name already used");
			return null;
		}, name -> {
			String n = name.trim();
			try {
				File f = new File(BehaviorCatalog.projectDir(projectPath), n + ".java");
				f.getParentFile().mkdirs();
				FileOutputStream o = new FileOutputStream(f);
				try {
					o.write(asProjectBehavior(src, old, n).getBytes("UTF-8"));
				} finally {
					o.close();
				}
				Ui.toast(c, Tr.t("bhv_code_added", "Added to the project behaviors") + ": " + n);
				if (done != null) done.run();
			} catch (Throwable t) {
				Ui.toast(c, t.getMessage() == null ? t.toString() : t.getMessage());
			}
		});
	}

	/**
	 * The same source as a project behavior: the project package, the class renamed everywhere, and the classes of the
	 * built-in library (which a built-in reached without an import, being in the same package) imported.
	 */
	static String asProjectBehavior(String src, String oldName, String newName) {
		String s = src;
		Matcher pk = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(s);
		String lib = "com.star4droid.template.behaviors.lib";
		if (pk.find()) {
			String was = pk.group(1);
			String head = "package " + BehaviorCatalog.PROJECT_PACKAGE + ";";
			if (!was.equals(BehaviorCatalog.PROJECT_PACKAGE) && was.equals(lib)) head += "\n\nimport " + lib + ".*;";
			s = s.substring(0, pk.start()) + head + s.substring(pk.end());
		} else s = "package " + BehaviorCatalog.PROJECT_PACKAGE + ";\n\n" + s;
		if (!oldName.equals(newName)) s = s.replaceAll("\\b" + Pattern.quote(oldName) + "\\b", Matcher.quoteReplacement(newName));
		return s;
	}

	private static final Pattern TOKENS = Pattern.compile("(/\\*[\\s\\S]*?\\*/|//[^\\n]*)|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')|(@\\w+)"
			+ "|\\b(package|import|public|private|protected|static|final|class|interface|enum|extends|implements|void|int|float|double|boolean|long|short|byte|char"
			+ "|new|return|if|else|for|while|do|switch|case|default|break|continue|try|catch|finally|throw|throws|this|super|null|true|false|instanceof|abstract|synchronized|volatile|transient)\\b"
			+ "|\\b(\\d+(?:\\.\\d+)?[fFdDlL]?)\\b");

	/** Light colors for comments, texts, annotations, keywords and numbers (one pass). */
	private static CharSequence highlight(String src) {
		SpannableString sp = new SpannableString(src);
		if (src.length() > 200_000) return sp;
		Matcher mt = TOKENS.matcher(src);
		while (mt.find()) {
			int color = mt.group(1) != null ? 0xFF6A9955 : mt.group(2) != null ? 0xFFCE9178 : mt.group(3) != null ? 0xFFDCDCAA : mt.group(4) != null ? 0xFF569CD6 : 0xFFB5CEA8;
			sp.setSpan(new ForegroundColorSpan(color), mt.start(), mt.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		return sp;
	}
}
