package com.star4droid.star2d.Views;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.MainActivity;
import com.star4droid.star2d.editor.ui.sub.EditorField;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Ui;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native Android home screen (Projects / Templates / Community / Settings).
 * It is placed on top of the LibGDX editor surface and hidden while a project is open.
 */
public class ProjectsHomeView extends FrameLayout {

    /** Actions that need the hosting activity / the LibGDX app. */
    public interface Host {
        /** Open the project located at the given absolute path. */
        void openProject(File projectDir);
        void exportProject(File projectDir);
        void backupProject(File projectDir);
        void importProject();
        void openDonate();
    }

    private static final String[] LANG_NAMES = {"English", "العربيه", "Français", "Português", "Русский", "Español"};
    private static final String[] LANG_CODES = {"en", "ar", "fr", "br", "ru", "es"};
    private static final String[] COMPILERS = {"javac", "ecj"};

    private final Activity activity;
    private final Host host;
    private final File projectsDir;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private TextView[] tabs;
    private View[] pages;
    private LinearLayout projectsList;
    private TextView projectsCount, projectsEmpty;
    private EditText search;

    public ProjectsHomeView(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        this.projectsDir = new File(activity.getFilesDir(), "projects");
        com.star4droid.star2d.ui.Tr.init(activity);
        LayoutInflater.from(activity).inflate(R.layout.home_screen, this, true);
        translateTree(this);
        setLayoutDirection(Ui.isRtl(activity) ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
        setupTabs();
        setupLearn();
        setupProjects();
        setupTemplates();
        setupCommunity();
        setupSettings();
        findViewById(R.id.home_gear).setOnClickListener(v -> selectTab(4));
        View blocksBtn = findViewById(R.id.home_blocks);
        blocksBtn.setContentDescription(T("home_block_creator", "Block creator"));
        blocksBtn.setOnClickListener(v -> com.star4droid.star2d.ui.blocks.BlockCreatorActivity.open(activity));
        androidx.appcompat.widget.TooltipCompat.setTooltipText(blocksBtn, T("home_block_creator", "Block creator"));
        View cloneBtn = findViewById(R.id.home_clone);
        cloneBtn.setContentDescription(T("gh_clone_title", "Clone from GitHub"));
        cloneBtn.setOnClickListener(v -> com.star4droid.star2d.git.ui.CloneDialog.show(activity, projectsDir, dir -> {
            refresh();
            host.openProject(dir);
        }));
        selectTab(0);
        refresh();
        askLanguageOnFirstRun();
    }


    // ---------------------------------------------------------------- i18n

    static String T(String key, String def) {
        return com.star4droid.star2d.ui.Tr.t(key, def);
    }

    /** XML texts -> translated (keys derived from the English text). */
    private static final String[][] XML_KEYS = {
            {"Projects", "home_tab_projects"}, {"Learn", "home_tab_learn"}, {"Templates", "home_tab_templates"},
            {"Community", "home_tab_community"}, {"Settings", "home_tab_settings"}, {"Open", "home_open"},
            {"New project", "home_new_project"}, {"Search projects...", "home_search"}, {"No projects yet", "home_no_projects"},
            {"Templates & Examples", "home_templates_title"}, {"Jumpstart your game with ready-to-run starter projects and engine features", "home_templates_sub"},
            {"Learn by making games", "home_learn_title"}, {"(Experimental)", "home_experimental"}, {"Interactive tutorials guide every tap, step by step", "home_learn_sub"},
            {"Community & Support", "home_community_title"}, {"Connect with the Star2D developer community across the globe", "home_community_sub"},
            {"💖 Support Engine Development", "home_support"}, {"Star2D is developed with passion. Help keep updates and features coming!", "home_support_sub"},
            {"Donate", "home_donate"}, {"Engine Settings", "home_settings_title"}, {"Configure editor behaviors, code generation, and preferences", "home_settings_sub"},
            {"EDITOR & COMPILER", "home_editor_compiler"}, {"Interface Language", "home_language"}, {"Java Compiler Backend", "home_compiler"},
            {"Script Code Completion", "codeCompletion"}, {"Automatic Scene Saving", "autoSave"}, {"Persist Undo/Redo History", "saveUndoRedo"},
            {"+ Use Template", "home_use_template"}, {"Join", "home_join"},
    };

    static void translateTree(View v) {
        if (v instanceof TextView) {
            String t = ((TextView) v).getText().toString();
            for (String[] k : XML_KEYS) if (k[0].equals(t)) { ((TextView) v).setText(T(k[1], t)); break; }
            if (v instanceof EditText) {
                CharSequence h = ((EditText) v).getHint();
                if (h != null) for (String[] k : XML_KEYS) if (k[0].contentEquals(h)) ((EditText) v).setHint(T(k[1], k[0]));
            }
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) translateTree(g.getChildAt(i));
        }
    }

    // ---------------------------------------------------------------- learn (interactive tutorials)

    private void setupLearn() {
        LinearLayout list = findViewById(R.id.learn_list);
        for (com.star4droid.star2d.ui.tutorial.TutorialCatalog.Info t : com.star4droid.star2d.ui.tutorial.TutorialCatalog.all(activity)) {
            View card = LayoutInflater.from(activity).inflate(R.layout.home_item_template, list, false);
            ((TextView) card.findViewById(R.id.template_title)).setText(t.title);
            ((TextView) card.findViewById(R.id.template_tag)).setText(t.tag);
            ((TextView) card.findViewById(R.id.template_desc)).setText(t.description);
            TextView start = card.findViewById(R.id.template_use);
            start.setText(T("home_start_lesson", "▶ Start lesson"));
            start.setOnClickListener(v -> com.star4droid.star2d.ui.tutorial.TutorialCatalog.start(activity, t.id, projectsDir, dir -> {
                refresh();
                host.openProject(dir);
            }));
            list.addView(card);
        }
    }

    // ---------------------------------------------------------------- tabs

    private void setupTabs() {
        tabs = new TextView[]{
                findViewById(R.id.tab_projects), findViewById(R.id.tab_learn), findViewById(R.id.tab_templates),
                findViewById(R.id.tab_community), findViewById(R.id.tab_settings)};
        pages = new View[]{
                findViewById(R.id.page_projects), findViewById(R.id.page_learn), findViewById(R.id.page_templates),
                findViewById(R.id.page_community), findViewById(R.id.page_settings)};
        // icons only (the name is shown as a tooltip / for accessibility)
        int[] icons = {R.drawable.ic_s2_folder, R.drawable.ic_s2_school, R.drawable.ic_s2_widgets, R.drawable.ic_s2_group, R.drawable.ic_s2_settings};
        String[][] names = {{"home_tab_projects", "Projects"}, {"home_tab_learn", "Learn"}, {"home_tab_templates", "Templates"},
                {"home_tab_community", "Community"}, {"home_tab_settings", "Settings"}};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            TextView t = tabs[i];
            String label = T(names[i][0], names[i][1]);
            t.setText("");
            t.setContentDescription(label);
            if (android.os.Build.VERSION.SDK_INT >= 26) t.setTooltipText(label);
            t.setCompoundDrawablesWithIntrinsicBounds(0, icons[i], 0, 0);
            t.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
            t.setMinWidth(0);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, t.getLayoutParams().height, 1f);
            lp.setMargins(4, 0, 4, 0);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> selectTab(index));
            t.setOnLongClickListener(v -> { toast(label); return true; });
        }
    }

    private void selectTab(int index) {
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == index;
            tabs[i].setBackgroundResource(selected ? R.drawable.home_tab_selected : 0);
            tabs[i].setTextColor(color(selected ? R.color.home_text : R.color.home_text_secondary));
            tabs[i].setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color(selected ? R.color.home_text : R.color.home_text_secondary)));
            tabs[i].setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            pages[i].setVisibility(selected ? VISIBLE : GONE);
        }
    }

    // ------------------------------------------------------------ projects

    private void setupProjects() {
        projectsList = findViewById(R.id.projects_list);
        projectsCount = findViewById(R.id.projects_count);
        projectsEmpty = findViewById(R.id.projects_empty);
        search = findViewById(R.id.projects_search);
        findViewById(R.id.btn_new_project).setOnClickListener(v -> createNewProject());
        findViewById(R.id.btn_open).setOnClickListener(v -> host.importProject());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
    }

    /** Reload the projects list (safe to call from any thread). */
    public void refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ui.post(this::refresh);
            return;
        }
        File[] files = projectsDir.listFiles(File::isDirectory);
        List<File> all = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        java.util.Collections.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        projectsCount.setText(String.format(T("home_count", "%d project(s) in workspace"), all.size()));

        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        projectsList.removeAllViews();
        SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.ENGLISH);
        LayoutInflater inflater = LayoutInflater.from(activity);
        int shown = 0;
        for (File project : all) {
            if (!project.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            View row = inflater.inflate(R.layout.home_item_project, projectsList, false);
            ((TextView) row.findViewById(R.id.project_name)).setText(project.getName());
            ((TextView) row.findViewById(R.id.project_date)).setText(format.format(new Date(project.lastModified())));
            row.setOnClickListener(v -> openProject(project));
            View menu = row.findViewById(R.id.project_menu);
            menu.setOnClickListener(v -> showProjectMenu(project, menu));
            projectsList.addView(row);
            shown++;
        }
        projectsEmpty.setText(all.isEmpty() ? T("home_no_projects", "No projects yet") : T("home_no_match", "No matching projects"));
        projectsEmpty.setVisibility(shown == 0 ? VISIBLE : GONE);
    }

    private void openProject(File project) {
        createDefaultPaths(project);
        host.openProject(project);
    }

    private void showProjectMenu(File project, View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, T("open", "Open"));
        menu.getMenu().add(0, 5, 1, T("home_edit_project", "Edit project"));
        menu.getMenu().add(0, 6, 2, T("ui_duplicate", "Duplicate"));
        menu.getMenu().add(0, 2, 3, T("home_export", "Export APK"));
        menu.getMenu().add(0, 3, 4, T("home_backup", "Backup"));
        menu.getMenu().add(0, 4, 5, T("delete", "Delete"));
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 5: ProjectDialogs.edit(activity, project, this::refresh); break;
                case 6: ProjectDialogs.duplicate(activity, project, this::refresh); break;
                case 1: openProject(project); break;
                case 2: host.exportProject(project); break;
                case 3: host.backupProject(project); break;
                case 4: confirmDelete(project); break;
            }
            return true;
        });
        menu.show();
    }

    private void confirmDelete(File project) {
        new AlertDialog.Builder(activity)
                .setTitle(T("home_delete_project", "Delete project"))
                .setMessage(String.format(T("home_delete_msg", "Delete \"%s\"? This can't be undone."), project.getName()))
                .setPositiveButton(T("delete", "Delete"), (d, w) -> io.execute(() -> {
                    boolean ok = deleteRecursive(project);
                    ui.post(() -> {
                        toast(ok ? T("home_deleted", "Project deleted.") : T("ui_failed", "Failed"));
                        refresh();
                    });
                }))
                .setNegativeButton(T("cancel", "Cancel"), null)
                .show();
    }

    private void createNewProject() {
        ProjectDialogs.create(activity, projectsDir, dir -> {
            refresh();
            host.openProject(dir);
        });
    }

    // ----------------------------------------------------------- templates

    private void setupTemplates() {
        LinearLayout list = findViewById(R.id.templates_list);
        LayoutInflater inflater = LayoutInflater.from(activity);
        AssetManager assets = activity.getAssets();
        List<String> examples = new ArrayList<>();
        try {
            String[] names = assets.list("files/examples");
            if (names != null)
                for (String n : names)
                    if (!n.endsWith(".txt")) examples.add(n);
        } catch (Exception ignored) {}

        // Known templates first, in the designed order; any other example after them.
        String[] order = {"StarValley", "platformer", "CarsExample", "Paul", "example1"};
        List<String> sorted = new ArrayList<>();
        for (String o : order) if (examples.remove(o)) sorted.add(o);
        sorted.addAll(examples);

        for (String folder : sorted) {
            String[] info = templateInfo(folder);
            View card = inflater.inflate(R.layout.home_item_template, list, false);
            ((TextView) card.findViewById(R.id.template_title)).setText(T("tpl_" + folder + "_title", info[0]));
            ((TextView) card.findViewById(R.id.template_tag)).setText(T("tpl_" + folder + "_tag", info[1]));
            ((TextView) card.findViewById(R.id.template_desc)).setText(T("tpl_" + folder + "_desc", info[2]));
            ((TextView) card.findViewById(R.id.template_use)).setText(T("home_use_template", "+ Use Template"));
            card.findViewById(R.id.template_use).setOnClickListener(v -> useTemplate(folder));
            list.addView(card);
        }
    }

    /** {title, tag, description} for an example folder. */
    private String[] templateInfo(String folder) {
        switch (folder) {
            case "StarValley":
                return new String[]{"Star Valley RPG", "RPG / Adventure",
                        "Drive your vehicle with on-screen joystick, collect collectibles, Day/Night lighting system and compass navigation."};
            case "platformer":
                return new String[]{"2D Platformer", "Action / Platformer",
                        "Classic 2D platformer starter kit with jump physics, obstacle colliders, and level mechanics."};
            case "CarsExample":
                return new String[]{"Vehicle Physics", "Simulation / Physics",
                        "2D car steering simulation utilizing Box2D joint physics, friction, and suspension tuning."};
            case "Paul":
                return new String[]{"Character Demo", "Platformer",
                        "Animated player character controller demonstrating state machine actions and sprite animations."};
            case "example1":
                return new String[]{"2D Starter Core", "Starter",
                        "Clean empty scene with 2D camera viewport, physics world, gravity and joints ready to build on."};
            default:
                return new String[]{folder, "Example", readAssetText("files/examples/" + folder + ".txt")};
        }
    }

    private void useTemplate(String folder) {
        askProjectName(T("home_use_template", "Use template"), folder, name -> {
            File target = new File(projectsDir, name);
            toast(T("home_creating", "Creating project..."));
            io.execute(() -> {
                try {
                    copyAssetDir(activity.getAssets(), "files/examples/" + folder, target);
                    createDefaultPaths(target);
                    ui.post(() -> {
                        toast(T("home_created", "Project created") + ": " + name);
                        search.setText("");
                        selectTab(0);
                        refresh();
                    });
                } catch (Exception | Error e) {
                    deleteRecursive(target);
                    ui.post(() -> {
                        toast(T("ui_failed", "Failed") + ": " + e);
                        refresh();
                    });
                }
            });
        });
    }

    // ----------------------------------------------------------- community

    private void setupCommunity() {
        LinearLayout list = findViewById(R.id.community_list);
        findViewById(R.id.btn_donate).setOnClickListener(v -> host.openDonate());
        String[][] links = com.star4droid.star2d.ui.CommunityLinks.all();
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (String[] link : links) {
            View row = inflater.inflate(R.layout.home_item_community, list, false);
            ((TextView) row.findViewById(R.id.community_title)).setText(T("home_c_" + link[0], link[1]));
            ((TextView) row.findViewById(R.id.community_desc)).setText(T("home_c_" + link[0] + "_desc", link[2]));
            ((TextView) row.findViewById(R.id.community_join)).setText(T("home_join", "Join"));
            row.findViewById(R.id.community_join).setOnClickListener(v -> openUrl(link[3]));
            list.addView(row);
        }
    }

    private void openUrl(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(T("home_no_app", "No app found to open the link."));
        }
    }

    // ------------------------------------------------------------ settings

    private void setupSettings() {
        SharedPreferences prefs = prefs();

        Spinner language = findViewById(R.id.setting_language);
        language.setAdapter(spinnerAdapter(LANG_NAMES));
        String currentLang = prefs.getString("lang", "en");
        language.setSelection(Math.max(0, Arrays.asList(LANG_CODES).indexOf(currentLang)));
        language.setOnItemSelectedListener(new SimpleSelection(position -> {
            String code = LANG_CODES[position];
            if (code.equals(prefs().getString("lang", "en"))) return;
            prefs().edit().putString("lang", code).putBoolean("langSelected", true).commit();
            askRestart();
        }));

        Spinner compiler = findViewById(R.id.setting_compiler);
        compiler.setAdapter(spinnerAdapter(COMPILERS));
        compiler.setSelection(Math.max(0, Arrays.asList(COMPILERS).indexOf(prefs.getString("compiler", "javac"))));
        compiler.setOnItemSelectedListener(new SimpleSelection(position ->
                prefs().edit().putString("compiler", COMPILERS[position]).apply()));

        bindSwitch(R.id.setting_completion, "Auto Completion");
        bindSwitch(R.id.setting_autosave, "AutoSave");
        bindSwitch(R.id.setting_undo, "SaveUndoRedo");

        // icon size (small / medium / large) used by the editor toolbars
        LinearLayout card = (LinearLayout) findViewById(R.id.setting_undo).getParent();
        addGitHubCard((LinearLayout) card.getParent(), card);
        TextView label = new TextView(activity);
        label.setText(T("ui_icon_size", "Icon size"));
        label.setTextColor(color(R.color.home_text_secondary));
        label.setTextSize(14);
        label.setPadding(0, dp(14), 0, dp(6));
        card.addView(label);
        String[] sizeKeys = {"small", "medium", "large"};
        String[] sizeNames = {T("ui_small", "Small"), T("ui_medium", "Medium"), T("ui_large", "Large")};
        FrameLayout box = new FrameLayout(activity);
        Spinner sizes = new Spinner(activity);
        sizes.setBackgroundResource(R.drawable.home_input_bg);
        sizes.setAdapter(spinnerAdapter(sizeNames));
        sizes.setSelection(Math.max(0, Arrays.asList(sizeKeys).indexOf(Ui.iconSize(activity))));
        sizes.setOnItemSelectedListener(new SimpleSelection(position ->
                prefs().edit().putString(Ui.PREF_ICON_SIZE, sizeKeys[position]).apply()));
        box.addView(sizes, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        card.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** "GitHub" row of the settings page: shows the account and opens the connection page. */
    private void addGitHubCard(LinearLayout parent, View before) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.home_card_bg);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        android.widget.ImageView icon = new android.widget.ImageView(activity);
        icon.setImageResource(R.drawable.ic_s2_git);
        icon.setColorFilter(color(R.color.home_text));
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, dp(14), 0);
        TextView title = new TextView(activity);
        title.setText(T("gh_title", "GitHub"));
        title.setTextColor(color(R.color.home_text));
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        final TextView sub = new TextView(activity);
        sub.setTextColor(color(R.color.home_text_secondary));
        sub.setTextSize(13);
        texts.addView(title);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = new TextView(activity);
        arrow.setText(Ui.isRtl(activity) ? "‹" : "›");
        arrow.setTextColor(color(R.color.home_text_secondary));
        arrow.setTextSize(24);
        row.addView(arrow);
        final Runnable refreshSub = () -> {
            com.star4droid.star2d.git.GitHubApi.User u = com.star4droid.star2d.git.GitHubSession.user(activity);
            boolean on = com.star4droid.star2d.git.GitHubSession.connected(activity);
            sub.setText(on ? T("gh_connected_as", "Connected as") + " @" + (u == null ? "" : u.login)
                    : T("gh_not_connected", "Not connected"));
            sub.setTextColor(on ? color(R.color.home_accent) : color(R.color.home_text_secondary));
        };
        refreshSub.run();
        row.setOnClickListener(v -> com.star4droid.star2d.git.ui.GitHubConnect.show(activity, refreshSub));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        parent.addView(row, parent.indexOfChild(before), lp);
        // the original card had its own top margin; keep spacing even
        ((LinearLayout.LayoutParams) before.getLayoutParams()).topMargin = dp(14);
    }

    private void bindSwitch(int id, String key) {
        SwitchCompat s = findViewById(id);
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{0xFFFFFFFF, 0xFFBDBDBD}));
        s.setTrackTintList(new ColorStateList(states, new int[]{color(R.color.home_accent), 0xFF555555}));
        s.setChecked(prefs().getBoolean(key, true));
        s.setOnCheckedChangeListener((b, checked) -> prefs().edit().putBoolean(key, checked).apply());
    }

    private ArrayAdapter<String> spinnerAdapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.home_spinner_item, items);
        adapter.setDropDownViewResource(R.layout.home_spinner_item);
        return adapter;
    }

    private void askRestart() {
        new AlertDialog.Builder(activity)
                .setTitle(T("ui_restart", "Restart required"))
                .setMessage(T("langNextTime", "The new language will be applied after restarting Star2D."))
                .setPositiveButton(T("ui_restart_now", "Restart now"), (d, w) -> restartApp())
                .setNegativeButton(T("cancel", "Later"), null)
                .show();
    }

    private void askLanguageOnFirstRun() {
        if (prefs().getBoolean("langSelected", false)) return;
        new AlertDialog.Builder(activity)
                .setTitle("Language / اللغة")
                .setCancelable(false)
                .setItems(LANG_NAMES, (d, which) -> {
                    String code = LANG_CODES[which];
                    boolean changed = !code.equals(prefs().getString("lang", "en"));
                    // commit() (not apply): the process restarts right after
                    prefs().edit().putString("lang", code).putBoolean("langSelected", true).commit();
                    if (changed) restartApp();
                })
                .show();
    }

    private void restartApp() {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finishAffinity();
        Runtime.getRuntime().exit(0);
    }

    // ------------------------------------------------------------- helpers

    private interface NameCallback { void onName(String name); }

    private void askProjectName(String title, String defaultName, NameCallback callback) {
        EditText input = new EditText(activity);
        input.setText(defaultName);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.selectAll();
        FrameLayout container = new FrameLayout(activity);
        int pad = dp(20);
        container.setPadding(pad, dp(8), pad, 0);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(T("home_name_rule", "Project name (A-Z, a-z, 0-9, _):"))
                .setView(container)
                .setPositiveButton(T("home_create", "Create"), null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim().replace(" ", "_");
            if (name.isEmpty()) {
                input.setError(T("emptyError", "Enter a name"));
                return;
            }
            for (char c : name.toCharArray()) {
                if (!EditorField.allowedChars.contains(String.valueOf(c))) {
                    input.setError(T("ui_bad_char", "Not allowed") + ": " + c);
                    return;
                }
            }
            if (new File(projectsDir, name).exists()) {
                input.setError(T("projectConflict", "A project with this name already exists"));
                return;
            }
            dialog.dismiss();
            callback.onName(name);
        }));
        dialog.show();
    }

    private static void createDefaultPaths(File project) {
        for (String path : new String[]{"sounds", "anims", "images", "files", "icon"}) {
            File child = new File(project, path);
            if (!child.exists()) child.mkdirs();
        }
    }

    private static void copyAssetDir(AssetManager assets, String assetPath, File target) throws Exception {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            // A file (asset folders are never empty in the APK).
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = assets.open(assetPath); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            return;
        }
        target.mkdirs();
        for (String child : children)
            copyAssetDir(assets, assetPath + "/" + child, new File(target, child));
    }

    private static boolean deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children) deleteRecursive(child);
        return file.delete() || !file.exists();
    }

    private String readAssetText(String path) {
        try (InputStream in = activity.getAssets().open(path)) {
            byte[] data = new byte[in.available()];
            int read = in.read(data);
            return new String(data, 0, Math.max(read, 0), "UTF-8").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private SharedPreferences prefs() {
        if (EngineSettings.get() == null) EngineSettings.init(activity);
        return EngineSettings.get();
    }

    private int color(int id) {
        return ContextCompat.getColor(activity, id);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public void toast(String message) {
        com.star4droid.star2d.ui.Snack.show(message);
    }

    /** Spinner listener that ignores the initial callback fired by setSelection. */
    private static class SimpleSelection implements AdapterView.OnItemSelectedListener {
        interface OnPick { void pick(int position); }
        private final OnPick onPick;
        private boolean first = true;
        SimpleSelection(OnPick onPick) { this.onPick = onPick; }
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (first) { first = false; return; }
            onPick.pick(position);
        }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
