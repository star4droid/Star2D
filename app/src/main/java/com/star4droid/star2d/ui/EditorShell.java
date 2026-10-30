package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.tutorial.Tut;
import com.star4droid.star2d.ui.panels.AddItemPanel;
import com.star4droid.star2d.ui.panels.AiPanel;
import com.star4droid.star2d.ui.panels.ElementsPanel;
import com.star4droid.star2d.ui.panels.EventsPanel;
import com.star4droid.star2d.ui.panels.FilesPanel;
import com.star4droid.star2d.ui.panels.JointsPanel;
import com.star4droid.star2d.ui.panels.PropertiesPanel;
import com.star4droid.star2d.ui.panels.SettingsPanel;
import com.star4droid.star2d.ui.panels.VariablesPanel;

import java.util.ArrayList;

/**
 * Native Android editor UI placed on top of the LibGDX scene canvas.
 * Only the canvas itself stays LibGDX; every bar, panel and dialog is Android.
 * The shell hides itself while the game runs.
 */
public class EditorShell extends FrameLayout implements ControlLayer.NativeUi, TestApp.NativeHooks {

    /** Things only the activity can do. */
    public interface Host {
        void openJava(String path);
        /** Import files (system picker) into a folder, then call done. */
        void importFiles(String targetDir, Runnable done);
        /** Open a visual script in the block editor; false = use the legacy editor. */
        boolean openBlocks(String file, String hints);
        /** Open a block script and fly to the block at this position. */
        void openBlocksAt(String file, int block);
        /** Open a Java file at a line (highlighting the error there). */
        void openJavaAt(String path, int line, int col, int endCol, String message);
    }

    private final Activity activity;
    private final Host host;
    public final PanelHost panels;
    private final FrameLayout chrome;

    public final ElementsPanel elements;
    public final PropertiesPanel properties;
    public final EventsPanel events;
    public final JointsPanel joints;
    public final VariablesPanel variables;
    public final FilesPanel files;
    public final AiPanel ai;
    public final com.star4droid.star2d.ui.panels.GitPanel git;
    public final SettingsPanel settings;
    public final AddItemPanel addItem;
    public final com.star4droid.star2d.ui.panels.RemotePanel remote;
    public final com.star4droid.star2d.ui.panels.AiHubPanel aiHub;
    private RemoteOverlay remoteOverlay;
    /** Dimming layers over the editing bars; shown while an AI works (see applyRemoteLock). */
    private final ArrayList<View> shields = new ArrayList<>();
    private final com.star4droid.star2d.remote.RemoteSession.Listener sessionListener = this::applyRemoteLock;

    private ImageView undoBtn, redoBtn, lockBtn;
    private final ArrayList<ImageView> toolButtons = new ArrayList<>();
    private final ArrayList<ImageView> railButtons = new ArrayList<>();
    private final ArrayList<PanelHost.Panel> railPanels = new ArrayList<>();
    private LinearLayout tabsRow, axisGroup;
    private TextView titleView, indexingChip;
    private String tool = "grid", axis = "none";
    private boolean playing = false, projectOpen = false;
    /** A full-screen LibGDX tool (the font editor) is open: the whole shell is hidden meanwhile, closer = what the Back key calls. */
    private boolean toolOpen = false;
    private Runnable toolCloser;
    private AlertDialog playDialog;
    private boolean indexing = false;
    /** The editor shell of the open editor activity (for tutorials). */
    public static EditorShell current;
    private TextView playStatus;
    private ProgressBar playProgress;
    private BuildConsole console;
    private ImageView consoleBtn;
    private LinearLayout loadingBox;
    private ProgressBar loadingBar;
    private TextView loadingText;
    private java.util.List<com.star4droid.star2d.CodeEditor.smart.BuildError> pendingErrors;
    private boolean errorsHandled;
    /** Prefab edit mode: the prefab is open in a hidden temp scene (see openPrefab). */
    private volatile String prefabPath, prefabTemp, prefabReturn;
    private volatile boolean prefabBusy;
    private volatile String projectPathCache;

    public EditorShell(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        Tr.init(activity);
        com.star4droid.star2d.ui.behaviors.BehaviorText.prewarm(activity); // behavior catalog + icons, off the UI thread
        setLayoutDirection(Ui.isRtl(activity) ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);

        chrome = new FrameLayout(activity);
        addView(chrome, Ui.match());
        remoteOverlay = new RemoteOverlay(activity);
        addView(remoteOverlay, Ui.match());
        panels = new PanelHost(activity);
        addView(panels, Ui.match());
        panels.setOnVisibilityChanged(this::updateRailSelection);

        elements = new ElementsPanel(this);
        properties = new PropertiesPanel(this);
        events = new EventsPanel(this);
        joints = new JointsPanel(this);
        variables = new VariablesPanel(this);
        files = new FilesPanel(this);
        ai = new AiPanel(this);
        git = new com.star4droid.star2d.ui.panels.GitPanel(this);
        settings = new SettingsPanel(this);
        addItem = new AddItemPanel(this);
        remote = new com.star4droid.star2d.ui.panels.RemotePanel(this);
        aiHub = new com.star4droid.star2d.ui.panels.AiHubPanel(this, remote, ai);
        com.star4droid.star2d.remote.RemoteSession.addListener(sessionListener);
        com.star4droid.star2d.remote.RemoteClient.get().addListener((st, d) -> applyRemoteLock());

        console = new BuildConsole(activity);
        console.setOpener(this::openError);
        console.setOnVisibilityChange(this::updateConsoleChip);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        addView(console, cl);
        buildLoadingBar();
        current = this;
        ControlLayer.nativeUi = this;
        TestApp.nativeHooks = this;
        setVisibility(GONE);
        build();
    }

    /** Thin bar + label shown while a big scene is being created (see LibgdxEditor.loadChunked). */
    private void buildLoadingBar() {
        Context c = activity;
        loadingBox = Ui.column(c);
        loadingBox.setBackgroundColor(0xE617191D);
        loadingBox.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        loadingText = Ui.text(c, Tr.t("ui_loading_scene", "Loading scene…"), 12, Ui.TEXT_2);
        loadingBox.addView(loadingText, Ui.matchWrap());
        loadingBar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        loadingBar.setMax(1000);
        loadingBar.getProgressDrawable().setColorFilter(Ui.ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
        loadingBox.addView(loadingBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 4)));
        loadingBox.setVisibility(GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        addView(loadingBox, lp);
    }

    @Override
    public void onSceneLoading(float progress) {
        Ui.ui(() -> {
            if (loadingBox == null) return;
            if (progress < 0f) {
                loadingBox.setVisibility(GONE);
                return;
            }
            int pct = Math.round(progress * 100f);
            loadingBar.setProgress(Math.round(progress * 1000f));
            loadingText.setText(Tr.t("ui_loading_scene", "Loading scene…") + " " + pct + "%");
            loadingBox.setVisibility(VISIBLE);
            loadingBox.bringToFront();
        });
    }

    public Activity activity() { return activity; }
    public Host host() { return host; }

    // =============================================================== layout

    /** Rebuild all bars (rotation, icon-size or language change). */
    public void build() {
        Context c = activity;
        if (console != null) console.rebuild();
        chrome.removeAllViews();
        shields.clear();
        toolButtons.clear();
        railButtons.clear();
        railPanels.clear();
        boolean land = Ui.isLandscape(c);

        LinearLayout root = Ui.column(c);
        rootCol = root;
        spacerView = null; sceneToolsShield = null; bottomShield = null;
        chrome.addView(root, Ui.match());

        topBarView = buildTopBar(c, land);
        topShield = shield(topBarView, true);
        root.addView(topShield, Ui.matchWrap());
        if (!land && !inPrefabMode()) {
            LinearLayout tools = bar(c);
            tools.setBackgroundColor(0xEC1C1F24);
            addSceneTools(c, tools);
            sceneToolsShield = shield(wrapScroll(c, tools, false), true);
            root.addView(sceneToolsShield, Ui.matchWrap());
        }   // (landscape: the tabs are inside the top bar, buildTopBar made tabsRow; prefab mode: it is null)

        // middle: canvas is visible through here; rails float on the sides
        FrameLayout middle = new FrameLayout(c);
        middleView = middle;
        root.addView(middle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout rail = buildRail(c);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        rlp.setMarginEnd(Ui.dp(c, 6));
        middle.addView(wrapScroll(c, rail, true), rlp);

        if (land) {
            LinearLayout tools = buildTools(c, true);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            tlp.setMarginStart(Ui.dp(c, 6));
            middle.addView(shield(wrapScroll(c, tools, true), false), tlp);
        } else {
            bottomShield = shield(wrapScroll(c, buildTools(c, false), false), true);
            root.addView(bottomShield, Ui.matchWrap());
        }

        indexingChip = Ui.text(c, "  " + Tr.t("indexing", "Indexing files...") + "  ", 12, Ui.TEXT_2);
        indexingChip.setBackground(Ui.rounded(0xCC202328, Ui.dp(c, 12), Ui.BORDER, 1));
        indexingChip.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        indexingChip.setVisibility(GONE);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        ilp.topMargin = Ui.dp(c, 8);
        middle.addView(indexingChip, ilp);

        panels.refreshLayout();
        refreshTabs();
        updateToolSelection();
        updateRailSelection();
        refreshUndoRedo();
        applyRemoteLock();
        buildDebugChip(c);
        applyHideStep();
        if (!fixedListenerAdded) {
            fixedListenerAdded = true;
            getViewTreeObserver().addOnGlobalLayoutListener(this::applyFixedSizes);
        }
        post(this::applyFixedSizes);
    }

    // ============================================== temporary layout debugging

    /** Replace the two weights (middle area, top-bar spacer) by measured fixed sizes, or put the weights back. */
    private void applyFixedSizes() {
        try {
            if (rootCol == null || chrome.getHeight() <= 0) return;
            if (middleView != null && middleView.getParent() == rootCol) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) middleView.getLayoutParams();
                if (fixMiddle) {
                    int others = 0;
                    for (int i = 0; i < rootCol.getChildCount(); i++) {
                        View ch = rootCol.getChildAt(i);
                        if (ch != middleView && ch.getVisibility() != GONE) others += ch.getMeasuredHeight();
                    }
                    int h = Math.max(0, chrome.getHeight() - others);
                    if (lp.weight != 0f || lp.height != h) { lp.weight = 0f; lp.height = h; middleView.setLayoutParams(lp); }
                } else if (lp.weight != 1f || lp.height != 0) { lp.weight = 1f; lp.height = 0; middleView.setLayoutParams(lp); }
            }
            if (spacerView != null && spacerView.getParent() == topBarView && topBarView.getWidth() > 0) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) spacerView.getLayoutParams();
                if (fixSpacer) {
                    int others = topBarView.getPaddingLeft() + topBarView.getPaddingRight();
                    for (int i = 0; i < topBarView.getChildCount(); i++) {
                        View ch = topBarView.getChildAt(i);
                        if (ch != spacerView && ch.getVisibility() != GONE) {
                            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) ch.getLayoutParams();
                            others += ch.getMeasuredWidth() + m.leftMargin + m.rightMargin;
                        }
                    }
                    int w = Math.max(0, topBarView.getWidth() - others);
                    if (lp.weight != 0f || lp.width != w) { lp.weight = 0f; lp.width = w; spacerView.setLayoutParams(lp); }
                } else if (lp.weight != 1f || lp.width != 0) { lp.weight = 1f; lp.width = 0; spacerView.setLayoutParams(lp); }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Long press on Back: undo the fixed sizes one by one, bottom first, then the top. */
    private void revertWeightsStep() {
        revertStep++;
        String msg;
        if (revertStep == 1) { fixMiddle = false; msg = "1/2: middle area back to weight 1"; }
        else if (revertStep == 2) { fixSpacer = false; msg = "2/2: top-bar spacer back to weight 1 (all original now)"; }
        else { fixMiddle = true; fixSpacer = true; revertStep = 0; msg = "back to fixed sizes"; }
        Ui.toast(activity, msg);
        applyFixedSizes();
        if (rootCol != null) rootCol.requestLayout();
    }

    /** Long press on Play: 1) hide the top bar + the bar under it, 2) hide the bottom bar too, 3) show everything. */
    private void hideStepNext() {
        hideStep = (hideStep + 1) % 3;
        applyHideStep();
        Ui.toast(activity, hideStep == 0 ? "all bars shown" : hideStep == 1 ? "top bars hidden" : "top + bottom bars hidden");
    }

    private void applyHideStep() {
        if (topShield != null) topShield.setVisibility(hideStep >= 1 ? GONE : VISIBLE);
        if (sceneToolsShield != null) sceneToolsShield.setVisibility(hideStep >= 1 ? GONE : VISIBLE);
        if (bottomShield != null) bottomShield.setVisibility(hideStep >= 2 ? GONE : VISIBLE);
        if (debugChip != null) debugChip.setVisibility(hideStep >= 1 ? VISIBLE : GONE);
        if (rootCol != null) rootCol.requestLayout();
    }

    /** Small floating Play button, only while the top bar is hidden (so Play's long press stays reachable). */
    private void buildDebugChip(Context c) {
        debugChip = Ui.iconButton(c, R.drawable.ic_s2_play, "Play", v -> play());
        debugChip.setBackground(Ui.ripple(Ui.SUCCESS, Ui.dp(c, 10)));
        debugChip.setOnLongClickListener(v -> { hideStepNext(); return true; });
        debugChip.setVisibility(GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48), Gravity.TOP | Gravity.END);
        lp.setMargins(0, Ui.dp(c, 4), Ui.dp(c, 4), 0);
        chrome.addView(debugChip, lp);
    }

    /** Long press on Save: copy everything about the window and every view of the editor to the clipboard. */
    private void copyDebugInfo() {
        StringBuilder sb = new StringBuilder();
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.content.res.Configuration cf = getResources().getConfiguration();
            sb.append("== Star2D layout debug ==\n");
            sb.append("device: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
                    .append(" sdk=").append(android.os.Build.VERSION.SDK_INT).append(" rom=").append(android.os.Build.DISPLAY).append('\n');
            sb.append("metrics: ").append(dm.widthPixels).append('x').append(dm.heightPixels).append(" density=").append(dm.density).append(" fontScale=").append(cf.fontScale).append('\n');
            sb.append("config: orientation=").append(cf.orientation).append(" screenDp=").append(cf.screenWidthDp).append('x').append(cf.screenHeightDp)
                    .append(" smallestDp=").append(cf.smallestScreenWidthDp).append(" layoutDir=").append(cf.getLayoutDirection()).append('\n');
            sb.append("Ui.isLandscape=").append(Ui.isLandscape(activity)).append(" inMultiWindow=").append(android.os.Build.VERSION.SDK_INT >= 24 && activity.isInMultiWindowMode())
                    .append(" requestedOrientation=").append(activity.getRequestedOrientation()).append('\n');
            sb.append("flags: fixMiddle=").append(fixMiddle).append(" fixSpacer=").append(fixSpacer).append(" hideStep=").append(hideStep).append(" revertStep=").append(revertStep).append('\n');
            View decor = activity.getWindow().getDecorView();
            sb.append("decor: ").append(decor.getWidth()).append('x').append(decor.getHeight()).append('\n');
            android.view.WindowInsets wi = decor.getRootWindowInsets();
            if (wi != null) {
                sb.append("insets(system): ").append(wi.getSystemWindowInsetLeft()).append(',').append(wi.getSystemWindowInsetTop()).append(',')
                        .append(wi.getSystemWindowInsetRight()).append(',').append(wi.getSystemWindowInsetBottom()).append('\n');
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    int[] types = {android.view.WindowInsets.Type.statusBars(), android.view.WindowInsets.Type.navigationBars(), android.view.WindowInsets.Type.ime(),
                            android.view.WindowInsets.Type.captionBar(), android.view.WindowInsets.Type.displayCutout(), android.view.WindowInsets.Type.systemGestures(), android.view.WindowInsets.Type.tappableElement()};
                    String[] names = {"status", "nav", "ime", "caption", "cutout", "gestures", "tappable"};
                    for (int i = 0; i < types.length; i++) sb.append("  ").append(names[i]).append('=').append(wi.getInsets(types[i])).append('\n');
                    sb.append("  imeVisible=").append(wi.isVisible(android.view.WindowInsets.Type.ime())).append('\n');
                }
            }
            android.graphics.Rect vis = new android.graphics.Rect();
            decor.getWindowVisibleDisplayFrame(vis);
            sb.append("visibleDisplayFrame: ").append(vis.toShortString()).append('\n');
            sb.append("-- view tree (from the activity content) --\n");
            View content = activity.findViewById(android.R.id.content);
            dumpView(sb, content, 0);
        } catch (Throwable t) {
            sb.append("dump failed: ").append(android.util.Log.getStackTraceString(t));
        }
        Dialogs.copy(activity, sb.toString());
    }

    private void dumpView(StringBuilder sb, View v, int depth) {
        if (v == null || depth > 14) return;
        for (int i = 0; i < depth; i++) sb.append("  ");
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        sb.append(v.getClass().getSimpleName());
        if (v.getId() != View.NO_ID) sb.append(" #").append(Integer.toHexString(v.getId()));
        sb.append(" screen=(").append(loc[0]).append(',').append(loc[1]).append(") size=").append(v.getWidth()).append('x').append(v.getHeight());
        sb.append(" vis=").append(v.getVisibility() == VISIBLE ? "V" : v.getVisibility() == GONE ? "G" : "I");
        if (v.getAlpha() != 1f) sb.append(" alpha=").append(v.getAlpha());
        if (v.getTranslationX() != 0 || v.getTranslationY() != 0) sb.append(" tr=").append(v.getTranslationX()).append(',').append(v.getTranslationY());
        if (v.getScaleX() != 1f || v.getScaleY() != 1f) sb.append(" scale=").append(v.getScaleX()).append(',').append(v.getScaleY());
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null) {
            sb.append(" lp=").append(lp.width).append('x').append(lp.height);
            if (lp instanceof LinearLayout.LayoutParams) sb.append(" w=").append(((LinearLayout.LayoutParams) lp).weight).append(" g=").append(((LinearLayout.LayoutParams) lp).gravity);
            else if (lp instanceof FrameLayout.LayoutParams) sb.append(" g=").append(((FrameLayout.LayoutParams) lp).gravity);
        }
        if (v instanceof LinearLayout) sb.append(" orient=").append(((LinearLayout) v).getOrientation()).append(" gravity=").append(((LinearLayout) v).getGravity());
        if (v.getPaddingTop() + v.getPaddingBottom() + v.getPaddingLeft() + v.getPaddingRight() > 0)
            sb.append(" pad=").append(v.getPaddingLeft()).append(',').append(v.getPaddingTop()).append(',').append(v.getPaddingRight()).append(',').append(v.getPaddingBottom());
        sb.append('\n');
        if (v instanceof ViewGroup && !(v instanceof android.widget.AbsListView) && !(v instanceof ScrollView && depth > 8)) {
            ViewGroup g = (ViewGroup) v;
            int n = Math.min(g.getChildCount(), 40);
            for (int i = 0; i < n; i++) dumpView(sb, g.getChildAt(i), depth + 1);
        }
    }

    /** Opens the AI panel on its "API" tab (used when an error is sent to the in-editor assistant). */
    public void showAiApi() {
        aiHub.select(ai);
        panels.show(aiHub);
    }

    /** Wraps a bar so it can be dimmed and made untouchable while an AI is working. */
    private View shield(View v, boolean matchWidth) {
        FrameLayout f = new FrameLayout(activity);
        f.addView(v, new FrameLayout.LayoutParams(matchWidth ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View s = new View(activity);
        s.setBackgroundColor(0x99101216);
        s.setClickable(true);
        s.setVisibility(GONE);
        s.setOnClickListener(x -> Ui.toast(activity, Tr.t("rc_locked_toast", "The AI is working. Press “Take control” to edit.")));
        f.addView(s, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shields.add(s);
        return f;
    }

    /** UI thread: reflect "an AI is working" (dim the editing bars, only files stay open) and the connection state on the rail icon. */
    private void applyRemoteLock() {
        Ui.ui(() -> {
            boolean locked = com.star4droid.star2d.remote.RemoteSession.isLocked();
            for (View s : shields) s.setVisibility(locked ? VISIBLE : GONE);
            for (int i = 0; i < railButtons.size(); i++) {
                PanelHost.Panel p = railPanels.get(i);
                boolean allowed = !locked || p == files || p == aiHub;
                railButtons.get(i).setAlpha(allowed ? 1f : 0.35f);
                if (p == aiHub) {
                    boolean on = com.star4droid.star2d.remote.RemoteClient.get().state() == com.star4droid.star2d.remote.RemoteClient.State.CONNECTED;
                    railButtons.get(i).setImageTintList(ColorStateList.valueOf(on ? Ui.SUCCESS : Ui.TEXT));
                }
            }
            if (locked) {
                PanelHost.Panel cur = panels.current();
                if (cur != null && cur != files && cur != aiHub) panels.hide();
            }
            if (remote != null) remote.refresh();
        });
    }

    private View wrapScroll(Context c, LinearLayout content, boolean vertical) {
        ViewGroup scroll = vertical ? new ScrollView(c) : new HorizontalScrollView(c);
        if (scroll instanceof ScrollView) ((ScrollView) scroll).setVerticalScrollBarEnabled(false);
        else ((HorizontalScrollView) scroll).setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        return scroll;
    }

    private LinearLayout bar(Context c) {
        LinearLayout b = Ui.row(c);
        b.setBackgroundColor(0xF0181A1E);
        b.setPadding(Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3));
        b.setClickable(true);
        return b;
    }

    private LinearLayout buildTopBar(Context c, boolean land) {
        LinearLayout top = bar(c);
        final boolean pm = inPrefabMode();
        ImageView back = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, pm ? Tr.t("prefab_leave", "Leave prefab") : Tr.t("exit", "Exit"), v -> exitProject());
        back.setScaleX(Ui.isRtl(c) ? -1 : 1);
        back.setOnLongClickListener(v -> { revertWeightsStep(); return true; });
        Tut.tag(back, "top:exit");
        top.addView(back);

        LinearLayout titleBox = Ui.column(c);
        titleBox.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        titleView = Ui.text(c, "", 15, Ui.TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(1);
        titleBox.addView(titleView);
        if (pm) {
            titleView.setTextColor(PrefabUi.COLOR);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(titleBox, Ui.weight(1));
            tabsRow = null;
        } else if (land) {
            // scene tools inline (scrollable); the scene menu is one icon among them
            tabsRow = null;
            scenesHolder = null;
            LinearLayout inline = Ui.row(c);
            addSceneTools(c, inline);
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(inline);
            top.addView(hs, Ui.weight(1));
        } else {
            tabsRow = null;
            scenesHolder = null;
            spacerView = new View(c);
            top.addView(spacerView, Ui.weight(1));
        }
        undoBtn = Ui.iconButton(c, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> Bridge.gl(() -> Bridge.controls().actionUndo()));
        redoBtn = Ui.iconButton(c, R.drawable.ic_s2_redo, Tr.t("redo", "Redo"), v -> Bridge.gl(() -> Bridge.controls().actionRedo()));
        Tut.tag(undoBtn, "top:undo");
        Tut.tag(redoBtn, "top:redo");
        top.addView(undoBtn);
        top.addView(redoBtn);
        if (pm) {
            top.addView(Ui.button(c, R.drawable.ic_s2_close, Tr.t("cancel", "Cancel"), false, v -> cancelPrefab()),
                    Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 4, 0, 2, 0));
            top.addView(Ui.button(c, R.drawable.ic_s2_check, Tr.t("save", "Save"), true, v -> savePrefab()),
                    Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 2, 0, 4, 0));
            return top;
        }
        ImageView save = Ui.iconButton(c, R.drawable.ic_s2_save, Tr.t("save", "Save"), v -> save());
        Tut.tag(save, "top:save");
        save.setOnLongClickListener(v -> { copyDebugInfo(); return true; });
        top.addView(save);

        // Play: accent pill
        ImageView play = Ui.iconButton(c, R.drawable.ic_s2_play, Tr.t("ui_play", "Play"), v -> play());
        play.setBackground(Ui.ripple(Ui.SUCCESS, Ui.dp(c, 10)));
        LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) play.getLayoutParams();
        plp.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        Tut.tag(play, "top:play");
        play.setOnLongClickListener(v -> { hideStepNext(); return true; });
        top.addView(play);
        return top;
    }

    /** Scene tools as icon buttons: color, lights, orientation, logs, settings, help. */
    private void addSceneTools(Context c, LinearLayout into) {
        if (!inPrefabMode()) {
            ImageView scenes = Ui.iconButton(c, R.drawable.ic_s2_scenes, Tr.t("scenes", "Scenes"), v -> showScenesMenu(v));
            scenes.setImageTintList(ColorStateList.valueOf(0xFF64B5F6));
            Tut.tag(scenes, "tool:scenes");
            into.addView(scenes);
        }
        ImageView color = Ui.iconButton(c, R.drawable.ic_s2_palette, Tr.t("ui_scene_color", "Scene background color"), v -> sceneColor());
        color.setImageTintList(ColorStateList.valueOf(0xFFFFB74D));
        Tut.tag(color, "tool:color");
        into.addView(color);
        ImageView light = Ui.iconButton(c, R.drawable.ic_s2_light, Tr.t("lightSettings", "Light settings"), v -> LightSettings.show(activity));
        light.setImageTintList(ColorStateList.valueOf(0xFFFFEE58));
        into.addView(light);
        ImageView rot = Ui.iconButton(c, R.drawable.ic_s2_screen_rotation, Tr.t("changeOrienation", "Switch portrait / landscape"), v ->
                Dialogs.confirm(activity, Tr.t("changeOrienation", "Change orientation"),
                        Tr.t("editorRestartNote", "The scenes will be saved and reopened."), null,
                        () -> Bridge.gl(() -> Bridge.controls().actionToggleOrientation())));
        into.addView(rot);
        into.addView(Ui.iconButton(c, R.drawable.ic_s2_logs, Tr.t("ui_logs", "Logs"), v -> { files.openLogs(); panels.show(files); }));
        ImageView set = Ui.iconButton(c, R.drawable.ic_s2_settings, Tr.t("settings", "Settings"), v -> panels.show(settings));
        Tut.tag(set, "tool:settings");
        into.addView(set);
        ImageView coll = Ui.iconButton(c, R.drawable.ic_s2_collections, Tr.t("ui_collections", "Collections (free assets)"), v ->
                Bridge.query(() -> Bridge.editor().getProject().getPath(), p -> CollectionsDialog.show(activity, new java.io.File(p), files::refreshList)));
        coll.setImageTintList(ColorStateList.valueOf(0xFF81C784));
        Tut.tag(coll, "tool:collections");
        into.addView(coll);
        ImageView trs = Ui.iconButton(c, R.drawable.ic_s2_text, Tr.t("strings_title", "Game translations"), v ->
                Bridge.query(() -> Bridge.editor().getProject().getPath(), p -> StringsDialog.show(activity, new java.io.File(p))));
        trs.setImageTintList(ColorStateList.valueOf(0xFF4DD0E1));
        into.addView(trs);
        into.addView(Ui.iconButton(c, R.drawable.ic_s2_help, Tr.t("ui_help", "Help"), v -> Help.show(activity)));
    }

    private LinearLayout buildTabsBar(Context c) {
        LinearLayout tabs = Ui.row(c);
        tabs.setBackgroundColor(0xE6141619);
        tabs.setPadding(Ui.dp(c, 6), Ui.dp(c, 3), Ui.dp(c, 6), Ui.dp(c, 3));
        tabs.setClickable(true);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        tabsRow = Ui.row(c);
        hs.addView(tabsRow);
        tabs.addView(hs, Ui.weight(1));
        return tabs;
    }

    private LinearLayout buildRail(Context c) {
        LinearLayout rail = Ui.column(c);
        rail.setBackground(Ui.rounded(0xE6181A1E, Ui.dp(c, 12), Ui.BORDER, 1));
        rail.setPadding(Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4));
        rail.setClickable(true);
        addRail(c, rail, elements);
        addRail(c, rail, properties);
        addRail(c, rail, events);
        if (inPrefabMode()) {
            // a prefab has no joints / variables of its own, and no AI or git while it is being edited
            addRail(c, rail, files);
            return rail;
        }
        addRail(c, rail, joints);
        addRail(c, rail, variables);
        addRail(c, rail, files);
        addRail(c, rail, aiHub);
        addRail(c, rail, git);
        return rail;
    }

    private void addRail(Context c, LinearLayout rail, PanelHost.Panel p) {
        ImageView b = Ui.iconButton(c, p.icon(), p.title(), v -> togglePanel(p));
        Tut.tag(b, "rail:" + p.getClass().getSimpleName().replace("Panel", "").toLowerCase());
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
        lp.setMargins(0, Ui.dp(c, 2), 0, Ui.dp(c, 2));
        rail.addView(b);
        railButtons.add(b);
        railPanels.add(p);
    }

    private LinearLayout buildTools(Context c, boolean vertical) {
        LinearLayout t = vertical ? Ui.column(c) : Ui.row(c);
        if (vertical) {
            t.setBackground(Ui.rounded(0xE6181A1E, Ui.dp(c, 12), Ui.BORDER, 1));
            t.setPadding(Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4));
        } else {
            t.setBackgroundColor(0xF0181A1E);
            t.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 6));
            t.setGravity(Gravity.CENTER_VERTICAL);
        }
        t.setClickable(true);

        // add (accent)
        ImageView add = Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("ui_add_element", "Add element"), v -> panels.toggle(addItem));
        add.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        Tut.tag(add, "tool:add");
        t.addView(add);
        t.addView(gap(c, vertical));

        addTool(c, t, "grid", R.drawable.ic_s2_touch, Tr.t("ui_tool_pan", "Select / pan camera"));
        addTool(c, t, "move", R.drawable.ic_s2_move, Tr.t("ui_tool_move", "Move"));
        addTool(c, t, "scale", R.drawable.ic_s2_scale, Tr.t("ui_tool_scale", "Resize"));
        addTool(c, t, "rotate", R.drawable.ic_s2_rotate, Tr.t("ui_tool_rotate", "Rotate"));

        // axis lock (only meaningful for move/scale)
        axisGroup = vertical ? Ui.column(c) : Ui.row(c);
        TextView axisBtn = Ui.text(c, axisLabel(), 13, Ui.TEXT);
        axisBtn.setTypeface(Typeface.DEFAULT_BOLD);
        axisBtn.setGravity(Gravity.CENTER);
        int size = Ui.dp(c, Ui.buttonDp(c));
        axisBtn.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        axisBtn.setBackground(Ui.toggleBg(c));
        axisBtn.setOnClickListener(v -> {
            axis = axis.equals("none") ? "x" : (axis.equals("x") ? "y" : "none");
            axisBtn.setText(axisLabel());
            axisBtn.setSelected(!axis.equals("none"));
            Bridge.gl(() -> Bridge.controls().actionSetAxisLock(axis));
            Ui.toast(c, axis.equals("none") ? Tr.t("noLock", "Free movement") : (axis.equals("x") ? Tr.t("xLock", "Lock X") : Tr.t("yLock", "Lock Y")));
        });
        axisBtn.setOnLongClickListener(v -> { Ui.toast(c, Tr.t("ui_axis_hint", "Axis lock: free / X / Y")); return true; });
        axisGroup.addView(axisBtn);
        t.addView(axisGroup);

        t.addView(gap(c, vertical));
        t.addView(Ui.iconButton(c, R.drawable.ic_s2_copy, Tr.t("ui_duplicate", "Duplicate"), v -> duplicateSelected()));
        lockBtn = Ui.iconButton(c, R.drawable.ic_s2_lock_open, Tr.t("ui_lock", "Lock / unlock"), v -> toggleLock());
        t.addView(lockBtn);
        ImageView del = Ui.iconButton(c, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v -> deleteSelected());
        del.setImageTintList(ColorStateList.valueOf(0xFFFF8A80));
        t.addView(del);
        t.addView(Ui.iconButton(c, R.drawable.ic_s2_camera, Tr.t("ui_center_camera", "Center camera"), v -> Bridge.gl(() -> Bridge.editor().centerCamera())));
        // build errors: only there when the last build failed; opens / closes the console
        consoleBtn = Ui.iconButton(c, R.drawable.ic_s2_bug, Tr.t("console_title", "Errors"), v -> {
            if (console.getVisibility() == VISIBLE) console.hide();
            else console.reveal();
        });
        consoleBtn.setImageTintList(ColorStateList.valueOf(0xFFFF8A80));
        t.addView(consoleBtn);
        updateConsoleChip();
        return t;
    }

    private View gap(Context c, boolean vertical) {
        View v = new View(c);
        v.setBackgroundColor(Ui.BORDER);
        int s = Ui.dp(c, 1);
        LinearLayout.LayoutParams lp = vertical ? new LinearLayout.LayoutParams(Ui.dp(c, 24), s) : new LinearLayout.LayoutParams(s, Ui.dp(c, 24));
        if (vertical) lp.setMargins(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        else lp.setMargins(Ui.dp(c, 5), 0, Ui.dp(c, 5), 0);
        lp.gravity = Gravity.CENTER;
        v.setLayoutParams(lp);
        return v;
    }

    private String axisLabel() {
        return axis.equals("none") ? "XY" : axis.toUpperCase();
    }

    private void addTool(Context c, LinearLayout t, String mode, int icon, String label) {
        ImageView b = Ui.iconButton(c, icon, label, v -> setTool(mode));
        b.setTag(mode);
        Tut.tag(b, "tool:" + mode);
        t.addView(b);
        toolButtons.add(b);
    }

    private void setTool(String mode) {
        Bridge.query(() -> Bridge.controls().actionSetTouchMode(mode), ok -> {
            tool = ok ? mode : "grid";
            if (!ok) Ui.toast(activity, Tr.t("ui_select_first", "Select an element first"));
            updateToolSelection();
        });
    }

    private void updateToolSelection() {
        for (ImageView b : toolButtons) b.setSelected(tool.equals(b.getTag()));
        if (axisGroup != null) axisGroup.setAlpha(tool.equals("move") || tool.equals("scale") ? 1f : 0.35f);
    }

    private void updateRailSelection() {
        PanelHost.Panel cur = panels.current();
        for (int i = 0; i < railButtons.size(); i++) railButtons.get(i).setSelected(railPanels.get(i) == cur);
    }

    public void togglePanel(PanelHost.Panel p) {
        if (com.star4droid.star2d.remote.RemoteSession.isLocked() && p != files && p != aiHub) {
            Ui.toast(activity, Tr.t("rc_locked_toast", "The AI is working. Press “Take control” to edit."));
            return;
        }
        panels.toggle(p);
    }

    // =============================================================== scene tabs

    private LinearLayout scenesHolder;

    // ---- temporary layout debugging (branch layout-debug-temp)
    private LinearLayout rootCol;
    private FrameLayout middleView;
    private View spacerView, topShield, sceneToolsShield, bottomShield;
    private LinearLayout topBarView;
    private ImageView debugChip;
    private boolean fixMiddle = true, fixSpacer = true;
    private int hideStep = 0, revertStep = 0;
    private boolean fixedListenerAdded;


    public void refreshTabs() {
        if (inPrefabMode()) {
            if (titleView != null) titleView.setText(Tr.t("prefab_editing", "Editing prefab") + ": " + PrefabUi.shortName(prefabPath));
            return;
        }
        if (!Bridge.ready()) return;
        Bridge.query(() -> {
            String cur = Bridge.app().getEditor().getScene();
            if (com.star4droid.star2d.prefab.PrefabStore.isTempScene(cur)) {
                for (LibgdxEditor e : Bridge.app().editors)
                    if (!com.star4droid.star2d.prefab.PrefabStore.isTempScene(e.getScene())) { cur = e.getScene(); break; }
            }
            return cur;
        }, current -> {
            if (titleView != null) titleView.setText(current);
        });
    }

    private void showScenesMenu(View anchor) {
        Bridge.query(() -> Bridge.controls().listScenes(), scenes -> {
            String cur = titleView.getText().toString();
            IconMenu m = new IconMenu(activity);
            m.add(R.drawable.ic_s2_add, Ui.SUCCESS, Tr.t("newScene", "New scene"), () -> sceneDialog(ControlLayer.SceneAction.CREATE, "Scene"));
            m.add(R.drawable.ic_s2_copy, Ui.ACCENT, Tr.t("copyScene", "Copy scene"), () -> sceneDialog(ControlLayer.SceneAction.COPY, cur + "_copy"));
            m.add(R.drawable.ic_s2_edit, 0xFFFFB74D, Tr.t("renameScene", "Rename scene"), () -> {
                if (cur.equalsIgnoreCase("scene1")) Ui.toast(activity, Tr.t("renameMainScene", "The main scene can't be renamed"));
                else sceneDialog(ControlLayer.SceneAction.RENAME, cur);
            });
            m.add(R.drawable.ic_s2_delete, 0xFFFF8A80, Tr.t("deleteScene", "Delete scene"), () -> {
                if (cur.equalsIgnoreCase("scene1")) Ui.toast(activity, Tr.t("deleteMainScene", "The main scene can't be deleted"));
                else Dialogs.confirm(activity, Tr.t("deleteScene", "Delete scene"), Tr.t("areYouSure", "Are you sure?") + "\n" + cur, Tr.t("delete", "Delete"),
                        () -> Bridge.query(() -> Bridge.controls().actionDeleteCurrentScene(), err -> { if (err != null) Ui.toast(activity, err); }));
            });
            m.divider();
            for (String name : scenes)
                m.add(name.equals(cur) ? R.drawable.ic_s2_check : R.drawable.ic_s2_scenes, name.equals(cur) ? Ui.ACCENT : Ui.TEXT_2,
                        Tr.t("openScene", "Open") + ": " + name, () -> Bridge.gl(() -> Bridge.controls().actionOpenScene(name)));
            m.show(anchor);
        });
    }

    private void sceneDialog(ControlLayer.SceneAction action, String initial) {
        String title = action == ControlLayer.SceneAction.CREATE ? Tr.t("newScene", "New scene")
                : action == ControlLayer.SceneAction.COPY ? Tr.t("copyScene", "Copy scene") : Tr.t("renameScene", "Rename scene");
        Dialogs.input(activity, title, Tr.t("name", "Name") + " (A-Z, 0-9, _)", initial, Dialogs::checkName,
                name -> Bridge.query(() -> Bridge.controls().applySceneAction(name, action), err -> {
                    if (err != null) {
                        Ui.toast(activity, err);
                        sceneDialog(action, name);
                    }
                }));
    }

    // =============================================================== actions

    private void exitProject() {
        if (inPrefabMode()) {
            leavePrefab();
            return;
        }
        Dialogs.confirm(activity, Tr.t("exit", "Exit"), Tr.t("exitConfirm", "Close this project and go back?"), Tr.t("exit", "Exit"), () -> {
            panels.hide();
            Bridge.gl(() -> Bridge.app().closeProject());
        });
    }

    // =============================================================== prefab edit mode

    public boolean inPrefabMode() { return prefabTemp != null; }

    /** Path of the prefab being edited (relative to prefabs/, no extension), or null. */
    public String prefabPath() { return prefabPath; }

    /** Folder of the open project (cached; valid after the first editor change). */
    public String projectPath() { return projectPathCache; }

    private void clearPrefabMode() {
        prefabPath = null;
        prefabTemp = null;
        prefabReturn = null;
        prefabBusy = false;
        build();
        refreshAll();
    }

    /**
     * Open a prefab in the editor: it is copied to a hidden scene, shown with only the buttons that make sense for it,
     * and written back to the prefab file with Save (or thrown away with Cancel).
     */
    public void openPrefab(String path) {
        if (!Bridge.ready()) return;
        if (inPrefabMode() || prefabBusy) {
            Ui.toast(activity, Tr.t("prefab_finish_first", "Finish editing the open prefab first"));
            return;
        }
        if (com.star4droid.star2d.remote.RemoteSession.isLocked()) {
            Ui.toast(activity, Tr.t("rc_locked_toast", "The AI is working. Press “Take control” to edit."));
            return;
        }
        prefabBusy = true;
        panels.hide();
        Ui.toast(activity, Tr.t("prefab_opening", "Opening prefab…"));
        Bridge.gl(() -> {
            try {
                LibgdxEditor ed = Bridge.editor();
                String back = ed.getScene();
                Bridge.controls().actionSave();
                String temp = com.star4droid.star2d.prefab.PrefabOps.beginEdit(ed.getProject(), path, back); // same background / size / orientation as the scene it is opened from
                prefabPath = path;
                prefabReturn = back;
                prefabTemp = temp;
                Bridge.app().openSceneInNewEditor(temp);
                Ui.ui(() -> {
                    prefabBusy = false;
                    panels.hide();
                    build();
                    refreshAll();
                    focusPrefabRoot(80);
                });
            } catch (Throwable t) {
                prefabPath = null;
                prefabTemp = null;
                prefabReturn = null;
                Ui.ui(() -> {
                    prefabBusy = false;
                    Ui.toast(activity, PrefabUi.message(t));
                });
            }
        });
    }

    /** Select the prefab root and bring it into view once the temp scene has finished loading. */
    private void focusPrefabRoot(int tries) {
        if (tries <= 0) return;
        Bridge.query(() -> {
            LibgdxEditor ed = Bridge.editor();
            if (ed == null || !com.star4droid.star2d.prefab.PrefabSession.isTemp(ed)) return 1;
            if (ed.isLoading()) return 0;
            com.badlogic.gdx.scenes.scene2d.Actor r = com.star4droid.star2d.prefab.PrefabSession.findRoot(ed);
            if (r != null) {
                ed.selectActor(r);
                ed.centerCamera();
            }
            return 1;
        }, st -> {
            if (st == 0) postDelayed(() -> focusPrefabRoot(tries - 1), 150);
        });
    }

    /** Save the prefab: the temp scene is written, closed, and merged into the prefab file and all its instances. */
    public void savePrefab() {
        if (!inPrefabMode() || prefabBusy) return;
        final String temp = prefabTemp, path = prefabPath, back = prefabReturn;
        prefabBusy = true;
        Ui.toast(activity, Tr.t("prefab_saving", "Saving prefab…"));
        Bridge.gl(() -> {
            try {
                LibgdxEditor ed = Bridge.editor();
                if (!temp.equals(ed.getScene())) throw new IllegalStateException(Tr.t("prefab_not_open", "The prefab is not open in the editor"));
                if (ed.isLoading()) {
                    Ui.ui(() -> {
                        prefabBusy = false;
                        Ui.toast(activity, Tr.t("ui_loading_scene", "Loading scene…"));
                    });
                    return;
                }
                com.star4droid.star2d.Helpers.editor.Project proj = ed.getProject();
                proj.save(ed);
                Bridge.app().closeSceneEditorQuiet(temp, back);
                final String projPath = proj.getPath();
                Ui.ui(() -> commitPrefab(projPath, temp, path));
            } catch (Throwable t) {
                Ui.ui(() -> {
                    prefabBusy = false;
                    Ui.toast(activity, PrefabUi.message(t));
                });
            }
        });
    }

    private void commitPrefab(String projPath, String temp, String path) {
        com.star4droid.star2d.prefab.PrefabEditorSync.runAsync(() -> {
            com.star4droid.star2d.prefab.PrefabOps.commitEdit(new com.star4droid.star2d.Helpers.editor.Project(projPath), temp, path);
            return Boolean.TRUE;
        }, o -> {
            if (o.error != null) {
                // nothing is lost: the temp scene is still on disk, so go back into it
                Ui.toast(activity, PrefabUi.message(o.error));
                Bridge.gl(() -> Bridge.app().openSceneInNewEditor(temp));
                prefabBusy = false;
                return;
            }
            Ui.toast(activity, Tr.t("prefab_saved", "Prefab saved") + ": " + PrefabUi.shortName(path));
            clearPrefabMode();
        });
    }

    /** Cancel: asks first when something was changed. */
    public void cancelPrefab() {
        if (!inPrefabMode() || prefabBusy) return;
        Bridge.query(() -> Bridge.editor() != null && Bridge.editor().canUndo(), dirty -> {
            if (dirty) Dialogs.confirm(activity, Tr.t("prefab_discard", "Discard changes"), Tr.t("prefab_discard_msg", "Close the prefab without saving your changes?"), Tr.t("prefab_discard", "Discard changes"), this::discardPrefab);
            else discardPrefab();
        });
    }

    private void discardPrefab() {
        if (!inPrefabMode() || prefabBusy) return;
        final String temp = prefabTemp, back = prefabReturn;
        prefabBusy = true;
        Bridge.gl(() -> {
            com.star4droid.star2d.Helpers.editor.Project proj = Bridge.editor().getProject();
            Bridge.app().closeSceneEditorQuiet(temp, back);
            new Thread(() -> {
                try { com.star4droid.star2d.prefab.PrefabOps.discardEdit(proj, temp); } catch (Throwable ignored) {}
                try { proj.deleteScene(temp); } catch (Throwable ignored) {}
                Ui.ui(this::clearPrefabMode);
            }).start();
        });
    }

    /** Back key / exit while a prefab is open: save, discard or keep editing. */
    private void leavePrefab() {
        if (!inPrefabMode() || prefabBusy) return;
        new AlertDialog.Builder(activity)
                .setTitle(Tr.t("prefab_leave", "Leave prefab"))
                .setMessage(Tr.t("prefab_leave_msg", "Save the changes to the prefab?"))
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> savePrefab())
                .setNegativeButton(Tr.t("prefab_discard_short", "Discard"), (d, w) -> cancelPrefabNow())
                .setNeutralButton(Tr.t("prefab_continue", "Keep editing"), null)
                .show();
    }

    private void cancelPrefabNow() { discardPrefab(); }

    public void save() {
        Bridge.gl(() -> {
            Bridge.controls().actionSave();
            Ui.toast(activity, Tr.t("saved", "Saved"));
        });
    }

    private void duplicateSelected() {
        Bridge.query(() -> {
            com.badlogic.gdx.scenes.scene2d.Actor sel = Bridge.editor().getSelectedActor();
            if (sel == null) return false;
            com.star4droid.star2d.editor.ui.ItemActions.duplicate(Bridge.app(), sel);
            return true;
        }, ok -> { if (!ok) Ui.toast(activity, Tr.t("ui_select_first", "Select an element first")); else elements.refresh(); });
    }

    private void toggleLock() {
        Bridge.query(() -> Bridge.controls().actionToggleLock(), locked -> {
            if (locked == null) Ui.toast(activity, Tr.t("ui_select_first", "Select an element first"));
            else {
                updateLockIcon(locked);
                Ui.toast(activity, locked ? Tr.t("ui_locked", "Locked") : Tr.t("ui_unlocked", "Unlocked"));
            }
        });
    }

    private void updateLockIcon(boolean locked) {
        if (lockBtn == null) return;
        lockBtn.setImageResource(locked ? R.drawable.ic_s2_lock : R.drawable.ic_s2_lock_open);
        lockBtn.setSelected(locked);
    }

    private void deleteSelected() {
        Bridge.query(() -> {
            com.badlogic.gdx.scenes.scene2d.Actor sel = Bridge.editor().getSelectedActor();
            if (sel != null && com.star4droid.star2d.prefab.PrefabSession.isFixedRoot(Bridge.editor(), sel)) return "\u0000";
            return sel == null ? null : sel.getName();
        }, name -> {
            if (name == null) {
                Ui.toast(activity, Tr.t("ui_select_first", "Select an element first"));
                return;
            }
            if (name.equals("\u0000")) {
                Ui.toast(activity, Tr.t("prefab_root_locked", "The root of a prefab can't be deleted"));
                return;
            }
            Dialogs.confirm(activity, Tr.t("delete", "Delete"), Tr.t("areYouSure", "Are you sure?") + "\n" + name,
                    Tr.t("delete", "Delete"), () -> Bridge.gl(() -> Bridge.controls().actionDeleteSelected()));
        });
    }

    private void sceneColor() {
        Bridge.query(() -> {
            Bridge.controls().beginSceneColor();
            return Bridge.controls().getSceneColorHex();
        }, hex -> ColorPickerDialog.show(activity, Tr.t("ui_scene_color", "Scene background color"), hex,
                preview -> Bridge.gl(() -> Bridge.controls().previewSceneColor(preview)),
                done -> Bridge.gl(() -> {
                    if (done == null) Bridge.controls().cancelSceneColor();
                    else {
                        Bridge.controls().previewSceneColor(done);
                        Bridge.controls().finishSceneColor();
                    }
                })));
    }

    // =============================================================== play

    public void play() {
        if (inPrefabMode()) {
            Ui.toast(activity, Tr.t("prefab_finish_first", "Finish editing the open prefab first"));
            return;
        }
        panels.hide();
        Context c = activity;
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 8));
        playProgress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        playProgress.setIndeterminate(true);
        box.addView(playProgress, Ui.matchWrap());
        playStatus = Ui.text(c, Tr.t("generatingCode", "Generating code..."), 13, Ui.TEXT_2);
        playStatus.setPadding(0, Ui.dp(c, 10), 0, 0);
        playStatus.setTypeface(Typeface.MONOSPACE);
        playStatus.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(c);
        sv.addView(playStatus);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        playDialog = new AlertDialog.Builder(c)
                .setTitle(Tr.t("compiler", "Building your game"))
                .setView(box)
                .setCancelable(false)
                .create();
        playDialog.show();
        Bridge.gl(() -> Bridge.controls().actionPlay());
    }

    private void updateConsoleChip() {
        if (consoleBtn == null) return;
        consoleBtn.setVisibility(console.count() > 0 ? VISIBLE : GONE);
        consoleBtn.setSelected(console.getVisibility() == VISIBLE);
    }

    private void openError(com.star4droid.star2d.CodeEditor.smart.BuildError e) {
        console.hide();
        if (e.target == com.star4droid.star2d.CodeEditor.smart.BuildError.TARGET_BLOCK) {
            host.openBlocksAt(e.targetFile, e.blockIndex);
        } else if (e.target == com.star4droid.star2d.CodeEditor.smart.BuildError.TARGET_JAVA) {
            host.openJavaAt(e.targetFile, e.targetLine, e.targetColumn, e.targetEndColumn, e.message);
        }
    }

    @Override
    public void onBuildErrors(java.util.List<com.star4droid.star2d.CodeEditor.smart.BuildError> errors) {
        pendingErrors = errors == null || errors.isEmpty() ? null : new ArrayList<>(errors);
        recordBuild(errors);
    }

    /** Leaves the result of the build in the project for STATE.md (AI assistants read it through GitHub). */
    private void recordBuild(java.util.List<com.star4droid.star2d.CodeEditor.smart.BuildError> errors) {
        try {
            String path = Bridge.editor().getProject().getPath();
            java.io.File f = new java.io.File(path, com.star4droid.star2d.git.AiDocs.BUILD_LOG);
            f.getParentFile().mkdirs();
            StringBuilder sb = new StringBuilder();
            sb.append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ENGLISH).format(new java.util.Date())).append("\n");
            if (errors == null || errors.isEmpty()) sb.append("No errors reported.");
            else for (com.star4droid.star2d.CodeEditor.smart.BuildError e : errors) {
                String where = e.origin != null && !e.origin.isEmpty() ? e.origin : new java.io.File(e.file == null ? "" : e.file).getName() + ":" + e.line;
                sb.append(e.warning ? "warning " : "error ").append(where).append(": ").append(e.message).append("\n");
            }
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) {
                o.write(sb.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onPlayStatus(int state, String text) {
        Ui.ui(() -> {
            if (playDialog == null) return;
            if (pendingErrors != null && (state == 1 || state == 2)) {
                // structured errors: they go to the console, not into a dialog
                if (!errorsHandled) {
                    errorsHandled = true;
                    java.util.List<com.star4droid.star2d.CodeEditor.smart.BuildError> es = pendingErrors;
                    pendingErrors = null;
                    playDialog.dismiss();
                    playDialog = null;
                    console.show(es, null);
                    updateConsoleChip();
                    errorsHandled = false;
                }
                return;
            }
            switch (state) {
                case 0:
                    if (text != null && !text.isEmpty()) playStatus.setText(text);
                    break;
                case 1:
                    playStatus.setText(text);
                    playStatus.setTextColor(0xFFFF8A80);
                    playProgress.setVisibility(GONE);
                    playDialog.setTitle(Tr.t("ui_build_failed", "Build failed"));
                    break;
                case 2:
                    if (playProgress.getVisibility() == GONE || playStatus.getCurrentTextColor() == 0xFFFF8A80) {
                        // error: let the user read / copy it
                        final String err = playStatus.getText().toString();
                        playDialog.setButton(AlertDialog.BUTTON_POSITIVE, Tr.t("close", "Close"), (d, w) -> d.dismiss());
                        playDialog.setButton(AlertDialog.BUTTON_NEUTRAL, Tr.t("copy", "Copy"), (d, w) -> Dialogs.copy(activity, err));
                        playDialog.setButton(AlertDialog.BUTTON_NEGATIVE, Tr.t("ui_ask_ai", "Ask AI to fix"), (d, w) -> {
                            showAiApi();
                            ai.askToFix(err);
                        });
                        playDialog.dismiss();
                        AlertDialog d = new AlertDialog.Builder(activity)
                                .setTitle(Tr.t("ui_build_failed", "Build failed"))
                                .setMessage(err)
                                .setPositiveButton(Tr.t("close", "Close"), null)
                                .setNeutralButton(Tr.t("copy", "Copy"), (x, w) -> Dialogs.copy(activity, err))
                                .setNegativeButton(Tr.t("ui_ask_ai", "Ask AI to fix"), (x, w) -> {
                                    showAiApi();
                                    ai.askToFix(err);
                                })
                                .create();
                        d.show();
                        playDialog = null;
                    }
                    break;
                case 3:
                    console.clear();
                    playDialog.dismiss();
                    playDialog = null;
                    break;
            }
        });
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        Ui.ui(() -> {
            playing = isPlaying;
            if (playDialog != null && isPlaying) {
                playDialog.dismiss();
                playDialog = null;
            }
            updateVisibility();
            if (!isPlaying) refreshAll();
        });
    }

    // =============================================================== hooks from the editor

    @Override
    public void onSelectionChanged() {
        Ui.ui(() -> {
            elements.onEditorChanged();
            properties.onSelectionChanged();
            events.onSelectionChanged();
            Bridge.query(() -> {
                com.badlogic.gdx.scenes.scene2d.Actor a = Bridge.editor().getSelectedActor();
                return a != null && "true".equals(com.star4droid.star2d.Helpers.PropertySet.getPropertySet(a).getString("lock"));
            }, this::updateLockIcon);
        });
    }

    @Override
    public void onUndoRedoChanged() {
        Ui.ui(() -> {
            refreshUndoRedo();
            properties.onSelectionChanged();
            elements.onEditorChanged();
        });
    }

    private void refreshUndoRedo() {
        if (!Bridge.ready() || undoBtn == null) return;
        Bridge.query(() -> new boolean[]{Bridge.editor().canUndo(), Bridge.editor().canRedo()}, r -> {
            undoBtn.setAlpha(r[0] ? 1f : 0.35f);
            redoBtn.setAlpha(r[1] ? 1f : 0.35f);
        });
    }

    @Override
    public void onScenesChanged() {
        Ui.ui(this::refreshAll);
    }

    @Override
    public void onIndexing(boolean indexing) {
        Ui.ui(() -> {
            this.indexing = indexing;
            if (indexingChip != null) indexingChip.setVisibility(indexing ? VISIBLE : GONE);
        });
    }

    @Override
    public void onShowText(String title, String text) {
        Ui.ui(() -> Dialogs.longText(activity, title, text));
    }

    @Override
    public void onEditorChanged() {
        Ui.ui(() -> {
            projectOpen = true;
            updateVisibility();
            if (Bridge.ready()) Bridge.query(() -> new Object[]{Bridge.editor().getProject().getPath(), Bridge.app().hasTempSceneOpen()}, r -> {
                projectPathCache = (String) r[0];
                // the hidden scene vanished behind our back (project reloaded): leave prefab mode
                if (inPrefabMode() && !prefabBusy && !(Boolean) r[1]) clearPrefabMode();
            });
            refreshAll();
            // keep the chosen tool for the new editor
            Bridge.gl(() -> Bridge.controls().actionSetTouchMode(tool));
        });
    }

    @Override
    public boolean openVisual(String file, String hints) {
        if (!host.openBlocks(file, hints)) return false;
        return true;
    }

    @Override
    public void onBackKey() {
        Ui.ui(() -> {
            if (!onBack()) exitProject();
        });
    }

    @Override
    public void showText(String text) {
        Ui.ui(() -> {
            AlertDialog d = Dialogs.longText(activity, Tr.t("ui_runtime_error", "Game error"), text);
            d.setButton(AlertDialog.BUTTON_NEGATIVE, Tr.t("ui_ask_ai", "Ask AI to fix"), (x, w) -> {
                showAiApi();
                ai.askToFix(text);
            });
        });
    }

    /** Called by the activity when the project is closed. */
    public void onProjectClosed() {
        com.star4droid.star2d.ui.tutorial.Tutorial.onProjectClosed();
        projectOpen = false;
        prefabPath = null;
        prefabTemp = null;
        prefabReturn = null;
        prefabBusy = false;
        toolOpen = false;
        toolCloser = null;
        panels.hide();
        updateVisibility();
    }

    public void refreshAll() {
        refreshTabs();
        refreshUndoRedo();
        elements.onEditorChanged();
        properties.onSelectionChanged();
        events.onSelectionChanged();
        joints.refresh();
        variables.refresh();
        files.onEditorChanged();
    }

    private void updateVisibility() {
        setVisibility(projectOpen && !playing && !toolOpen ? VISIBLE : GONE);
    }

    /** A full-screen LibGDX tool opened / closed (see TestApp.NativeHooks): hide the editor chrome while it is open, restore it on close. */
    @Override
    public void onFullscreenTool(boolean open, Runnable closer) {
        Ui.ui(() -> {
            toolOpen = open;
            toolCloser = open ? closer : null;
            updateVisibility();
            if (!open) {
                try {
                    files.refreshList(); // the tool may have created / changed files
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public boolean isPlaying() { return playing; }
    public boolean isProjectOpen() { return projectOpen; }
    public boolean isIndexing() { return indexing; }
    public boolean isBuilding() { return playDialog != null; }

    /** Back key: close the panel first. Returns true when consumed. */
    public boolean onBack() {
        if (toolOpen && toolCloser != null) {
            Runnable closer = toolCloser;
            Bridge.gl(closer);
            return true;
        }
        if (getVisibility() != VISIBLE) return false;
        if (com.star4droid.star2d.ui.tutorial.Tutorial.onBack(activity)) return true;
        if (panels.current() != null) {
            if (panels.current() instanceof FilesPanel && files.goUp()) return true;
            panels.hide();
            return true;
        }
        if (inPrefabMode()) {
            leavePrefab();
            return true;
        }
        return false;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        post(this::build);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (current == this && activity.isFinishing()) current = null;
    }

    /** True when the Gdx context is alive. */
    public static boolean gdxReady() {
        return Gdx.app != null;
    }
}
