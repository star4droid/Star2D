package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.editor.ui.variables.VarModel;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;

/** Scene variables (Events/&lt;scene&gt;/public/variables.json, same Gson VarModel format). */
public class VariablesPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private ScrollView root;
    private LinearLayout list;
    private ArrayList<VarModel> vars = new ArrayList<>();
    private String path;

    private static final String[] TYPES = {"int", "float", "double", "boolean", "String", "long", "PathHolder"};
    private static final String[] ACCESS = {"", "public", "private", "protected"};

    public VariablesPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("ui_variables", "Variables"); }
    @Override public int icon() { return R.drawable.ic_s2_variables; }

    @Override
    public View[] headerActions(Context c) {
        return new View[]{Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("ui_add_variable", "Add variable"), v -> edit(c, -1))};
    }

    @Override
    public View view(Context c) {
        if (root != null) return root;
        root = new ScrollView(c);
        list = Ui.column(c);
        list.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 16));
        root.addView(list);
        return root;
    }

    @Override public void onShown() { refresh(); }

    public void refresh() {
        if (list == null || !Bridge.ready()) return;
        Bridge.query(() -> {
            String p = Bridge.editor().getProject().getVariables(Bridge.editor().getScene());
            FileHandle fh = Gdx.files.absolute(p);
            ArrayList<VarModel> l = null;
            if (fh.exists()) {
                try { l = new Gson().fromJson(fh.readString(), new TypeToken<ArrayList<VarModel>>() {}.getType()); } catch (Exception ignored) {}
            }
            return new Object[]{p, l == null ? new ArrayList<VarModel>() : l};
        }, r -> {
            path = (String) r[0];
            vars = (ArrayList<VarModel>) r[1];
            render();
        });
    }

    private void render() {
        Context c = list.getContext();
        list.removeAllViews();
        TextView hint = Ui.text(c, Tr.t("ui_vars_hint", "Variables store values like score, lives or speed. Use them in blocks and code by name."), 12.5f, Ui.TEXT_3);
        hint.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 8));
        list.addView(hint);
        for (int i = 0; i < vars.size(); i++) {
            final int pos = i;
            VarModel m = vars.get(i);
            TextView type = Ui.text(c, m.getType(), 11.5f, Ui.ACCENT);
            type.setTypeface(Typeface.DEFAULT_BOLD);
            type.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
            type.setBackground(Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(c, 10)));
            LinearLayout row = PanelHost.listRow(c, R.drawable.ic_s2_variables, 0xFF9575CD, m.getName(),
                    (m.getAccessModifer().isEmpty() ? "" : m.getAccessModifer() + " · ") + "= " + (m.getValue().isEmpty() ? "—" : m.getValue()), type);
            row.setOnClickListener(v -> edit(c, pos));
            com.star4droid.star2d.ui.tutorial.Tut.tag(row, "var:" + m.getName());
            row.setOnLongClickListener(v -> {
                PopupMenu pm = new PopupMenu(c, v);
                pm.getMenu().add(0, 1, 0, Tr.t("edit", "Edit"));
                pm.getMenu().add(0, 2, 1, Tr.t("copy", "Copy name"));
                pm.getMenu().add(0, 3, 2, Tr.t("delete", "Delete"));
                pm.setOnMenuItemClickListener(it -> {
                    if (it.getItemId() == 1) edit(c, pos);
                    else if (it.getItemId() == 2) Dialogs.copy(c, m.getName());
                    else Dialogs.confirm(c, Tr.t("delete", "Delete"), m.getName() + "\n" + Tr.t("areYouSure", "Are you sure?"), Tr.t("delete", "Delete"), () -> {
                        vars.remove(pos);
                        save();
                    });
                    return true;
                });
                pm.show();
                return true;
            });
            list.addView(row);
        }
        LinearLayout add = Ui.button(c, R.drawable.ic_s2_add, Tr.t("ui_add_variable", "Add variable"), true, v -> edit(c, -1));
        com.star4droid.star2d.ui.tutorial.Tut.tag(add, "vars:add");
        list.addView(add, Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 4, 10, 4, 0));
        com.star4droid.star2d.ui.behaviors.SceneScriptVars.add(list);
    }

    private void edit(Context c, int pos) {
        VarModel m = pos >= 0 ? vars.get(pos) : new VarModel().setType("int").setValue("0");
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 18), 0);

        col.addView(Ui.text(c, Tr.t("ui_var_type", "Type"), 12.5f, Ui.TEXT_2));
        final String[] type = {m.getType()};
        final String[] access = {m.getAccessModifer()};
        LinearLayout chips = Ui.row(c);
        chips.setGravity(Gravity.START);
        ArrayList<TextView> chipViews = new ArrayList<>();
        EditText customType = Ui.input(c, m.getType(), "int / float / String / ...");
        for (String t : TYPES) {
            TextView ch = Ui.text(c, t, 13, Ui.TEXT);
            ch.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            ch.setBackground(Ui.toggleBg(c));
            ch.setSelected(t.equals(type[0]));
            ch.setOnClickListener(v -> {
                type[0] = t;
                customType.setText(t);
                for (TextView o : chipViews) o.setSelected(o == ch);
            });
            chipViews.add(ch);
            chips.addView(ch);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.addView(chips);
        col.addView(hs);
        col.addView(customType, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));

        col.addView(Ui.text(c, Tr.t("name", "Name"), 12.5f, Ui.TEXT_2));
        EditText name = Ui.input(c, m.getName(), "score");
        com.star4droid.star2d.ui.tutorial.Tut.tag(name, "vardlg:name");
        col.addView(name, Ui.matchWrap());
        col.addView(Ui.text(c, Tr.t("ui_initial_value", "Initial value"), 12.5f, Ui.TEXT_2));
        EditText value = Ui.input(c, m.getValue(), "0");
        com.star4droid.star2d.ui.tutorial.Tut.tag(value, "vardlg:value");
        // String values: a button opens a big text editor; the text is written as a proper Java string literal (quotes, \n, \\ escaped)
        LinearLayout valueRow = Ui.row(c);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final android.widget.ImageView editText = Ui.iconButton(c, R.drawable.ic_s2_edit, Tr.t("text_edit", "Edit text"), v -> {
            String raw = value.getText().toString().trim();
            String plain = com.star4droid.star2d.ui.TextDialog.fromJavaLiteral(raw);
            if (plain == null && !raw.isEmpty()) {
                Ui.toast(c, Tr.t("ui_var_expr", "This value is a Java expression: edit it in the field."));
                return;
            }
            com.star4droid.star2d.ui.TextDialog.show(c, Tr.t("ui_initial_value", "Initial value"), "", plain == null ? "" : plain,
                    nv -> value.setText(nv.isEmpty() ? "" : com.star4droid.star2d.ui.TextDialog.javaLiteral(nv)));
        });
        valueRow.addView(editText);
        editText.setVisibility("String".equals(type[0]) ? View.VISIBLE : View.GONE);
        customType.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable s) { editText.setVisibility("String".equals(s.toString().trim()) ? View.VISIBLE : View.GONE); }
        });
        col.addView(valueRow, Ui.matchWrap());
        // path variables: drawn on the canvas (PathHolder.parse("x1,y1,x2,y2..."))
        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout drawPath = Ui.button(c, R.drawable.ic_s2_navgrid, Tr.t("path_draw", "Draw on the canvas"), false, v -> {
            String n = name.getText().toString().trim();
            String err = Dialogs.checkName(n);
            if (err != null) { name.setError(err); return; }
            for (int i = 0; i < vars.size(); i++)
                if (i != pos && vars.get(i).getName().equals(n)) { name.setError(Tr.t("ui_name_used", "Name already used")); return; }
            if (dlg[0] != null) dlg[0].dismiss();
            shell.panels.hide();
            com.star4droid.star2d.ui.PathDrawPanel.open(shell, n, value.getText().toString(), pts -> {
                m.setType("PathHolder").setName(n).setValue(pathValue(pts)).setAccessModifer(access[0]);
                if (pos < 0 && !vars.contains(m)) vars.add(m);
                save();
                shell.panels.show(this);
            });
        });
        drawPath.setVisibility("PathHolder".equals(type[0]) ? View.VISIBLE : View.GONE);
        col.addView(drawPath, Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 0));
        customType.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable s) { drawPath.setVisibility("PathHolder".equals(s.toString().trim()) ? View.VISIBLE : View.GONE); }
        });
        TextView tip = Ui.text(c, Tr.t("ui_var_tip", "Text values need quotes, e.g. \"Hello\". true/false for boolean."), 11.5f, Ui.TEXT_3);
        tip.setPadding(0, Ui.dp(c, 4), 0, 0);
        col.addView(tip);

        col.addView(Ui.text(c, Tr.t("ui_access", "Access (advanced)"), 12.5f, Ui.TEXT_2));
        TextView acc = Ui.text(c, access[0].isEmpty() ? "—" : access[0], 14, Ui.TEXT);
        acc.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        acc.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
        acc.setOnClickListener(v -> Dialogs.choose(c, Tr.t("ui_access", "Access"), new String[]{"—", "public", "private", "protected"}, i -> {
            access[0] = ACCESS[i];
            acc.setText(i == 0 ? "—" : ACCESS[i]);
        }));
        col.addView(acc, Ui.matchWrap());

        ScrollView sv = new ScrollView(c);
        sv.addView(col);
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(pos >= 0 ? Tr.t("edit", "Edit") : Tr.t("ui_add_variable", "Add variable"))
                .setView(sv)
                .setPositiveButton(Tr.t("save", "Save"), null)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        dlg[0] = d;
        d.setOnShowListener(x -> {
            com.star4droid.star2d.ui.tutorial.Tut.tag(d.getButton(AlertDialog.BUTTON_POSITIVE), "vardlg:save");
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String err = Dialogs.checkName(n);
            if (err != null) { name.setError(err); return; }
            String t = customType.getText().toString().trim();
            if (t.isEmpty()) { customType.setError(Tr.t("ui_required", "Required")); return; }
            for (int i = 0; i < vars.size(); i++)
                if (i != pos && vars.get(i).getName().equals(n)) { name.setError(Tr.t("ui_name_used", "Name already used")); return; }
            String val = value.getText().toString().trim();
            if (t.equals("String") && !val.isEmpty() && !val.startsWith("\"")) val = "\"" + val + "\"";
            if (t.equals("PathHolder")) val = pathValue(val);
            m.setType(t).setName(n).setValue(val).setAccessModifer(access[0]);
            if (pos < 0) vars.add(m);
            d.dismiss();
            save();
        });
        });
        d.show();
    }

    /** Value of a path variable: points "x1,y1,..." (or a PathHolder.parse(...) text) become PathHolder.parse("..."), empty a new path. */
    static String pathValue(String v) {
        String s = v == null ? "" : v.trim();
        if (s.isEmpty() || s.equals("new PathHolder()")) return "new PathHolder()";
        if (!s.matches("[-+0-9.eE,;\\s]*")) {
            if (!s.startsWith("PathHolder.parse(\"")) return s; // an expression of the user
            java.util.regex.Matcher q = java.util.regex.Pattern.compile("\"([^\"]*)\"").matcher(s);
            if (!q.find()) return s;
            s = q.group(1);
        }
        return "PathHolder.parse(\"" + s.replace(" ", "") + "\")";
    }

    private void save() {
        String json = new Gson().toJson(vars);
        String p = path;
        Bridge.gl(() -> {
            Gdx.files.absolute(p).writeString(json, false);
            // keep the LibGDX variables manager in sync
            try { Bridge.controls().getVarsItem().setFileHandle(Gdx.files.absolute(p)); } catch (Throwable ignored) {}
            Ui.ui(this::refresh);
        });
    }
}
