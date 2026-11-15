package com.star4droid.star2d.git.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.git.GitException;
import com.star4droid.star2d.git.GitHubApi;
import com.star4droid.star2d.git.GitHubSession;
import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Home-screen "clone" page: the user's repositories (searchable) or any repository by address. */
public final class CloneDialog {
    public interface Done { void on(File project); }
    public interface Pick { void on(String owner, String name); }

    private final Activity a;
    private final File projectsDir;
    private final Done done;
    private Pick pick;
    private String pickTitle;
    private FullPage page;
    private ProgressBar loading;
    private LinearLayout list;
    private TextView status;
    private EditText search;
    private final List<GitHubApi.Repo> repos = new ArrayList<>();
    private int nextPage = 1;
    private boolean more, busy;

    private static String T(String k, String d) { return Tr.t(k, d); }

    public static void show(Activity a, File projectsDir, Done done) {
        new CloneDialog(a, projectsDir, done).open();
    }

    /** Same page, but only chooses a repository (to connect an open project to it). */
    public static void pick(Activity a, String title, Pick pick) {
        CloneDialog d = new CloneDialog(a, null, null);
        d.pick = pick;
        d.pickTitle = title;
        d.open();
    }

    private CloneDialog(Activity a, File projectsDir, Done done) {
        this.a = a;
        this.projectsDir = projectsDir;
        this.done = done;
    }

    private void open() {
        LinearLayout root = Ui.column(a);
        loading = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true);
        loading.setVisibility(View.INVISIBLE);
        root.addView(loading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 3)));

        ScrollView sv = new ScrollView(a);
        LinearLayout body = Ui.column(a);
        int pad = Ui.dp(a, 16);
        body.setPadding(pad, pad, pad, pad);
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // ---- from URL
        body.addView(Ui.sectionLabel(a, T("gh_clone_url", "Copy from address")));
        LinearLayout urlRow = Ui.row(a);
        urlRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText url = Ui.input(a, "", "https://github.com/owner/name");
        url.setSingleLine(true);
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        urlRow.addView(url, Ui.weight(1));
        View go = Ui.button(a, R.drawable.ic_s2_download, pick != null ? T("gh_use", "Use") : T("gh_clone", "Clone"), true, v -> {
            String[] on = GitHubApi.parseRepoUrl(url.getText().toString().trim());
            if (on == null) {
                url.setError(T("gh_bad_url", "Not a GitHub repository address"));
                return;
            }
            cloneFromUrl(on[0], on[1]);
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.setMargins(Ui.dp(a, 8), 0, 0, 0);
        urlRow.addView(go, gp);
        body.addView(urlRow, Ui.matchWrap());
        TextView hint = Ui.text(a, T("gh_clone_url_hint", "Public repositories work without signing in."), 11.5f, Ui.TEXT_3);
        hint.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 8));
        body.addView(hint);

        // ---- my repositories
        body.addView(Ui.sectionLabel(a, T("gh_my_repos", "Your repositories")));
        status = Ui.text(a, "", 13, Ui.TEXT_2);
        status.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        search = Ui.input(a, "", T("gh_search_repos", "Search..."));
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable s) { render(); }
        });
        body.addView(search, Ui.matchWrap());
        body.addView(status, Ui.matchWrap());
        list = Ui.column(a);
        body.addView(list, Ui.matchWrap());

        page = new FullPage(a, pick != null ? pickTitle : T("gh_clone_title", "Clone from GitHub")).content(root).show();
        if (GitHubSession.connected(a)) {
            loadMore();
        } else {
            search.setVisibility(View.GONE);
            status.setText(T("gh_clone_need_connect", "Connect your account to see your repositories (private ones too)."));
            View b = Ui.button(a, R.drawable.ic_s2_git, T("gh_connect_github", "Connect to GitHub"), true, v -> GitHubConnect.show(a, () -> {
                if (GitHubSession.connected(a)) {
                    search.setVisibility(View.VISIBLE);
                    list.removeAllViews();
                    status.setText("");
                    loadMore();
                }
            }));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = Ui.dp(a, 6);
            list.addView(b, bp);
        }
    }

    private void loadMore() {
        if (busy) return;
        busy = true;
        loading.setVisibility(View.VISIBLE);
        final int p = nextPage;
        GitHubSession.async(() -> GitHubSession.api(a).repos(p), (res, err) -> {
            busy = false;
            loading.setVisibility(View.INVISIBLE);
            if (err != null) {
                status.setText(T("gh_load_failed", "Could not load repositories") + ": " + err.getMessage());
                return;
            }
            repos.addAll(res);
            more = res.size() >= 100;
            nextPage = p + 1;
            render();
        });
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (final GitHubApi.Repo r : repos) {
            if (!q.isEmpty() && !r.fullName.toLowerCase(Locale.ROOT).contains(q)
                    && (r.description == null || !r.description.toLowerCase(Locale.ROOT).contains(q))) continue;
            list.addView(card(r), cardParams());
            shown++;
        }
        if (more) {
            View b = Ui.button(a, 0, T("gh_load_more", "Load more"), false, v -> loadMore());
            list.addView(b, cardParams());
        }
        status.setText(shown == 0 && !busy ? T("gh_no_repos", "No repositories found") : "");
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(a, 8);
        return lp;
    }

    private View card(final GitHubApi.Repo r) {
        LinearLayout c = Ui.column(a);
        c.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        c.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(a, 12)));
        LinearLayout top = Ui.row(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(a, r.fullName, 15, Ui.TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(name, Ui.weight(1));
        TextView tag = Ui.text(a, r.isPrivate ? "🔒 " + T("gh_private", "Private") : "🌐 " + T("gh_public", "Public"), 11.5f, Ui.TEXT_3);
        top.addView(tag);
        c.addView(top);
        if (r.description != null && !r.description.isEmpty()) {
            TextView d = Ui.text(a, r.description, 12.5f, Ui.TEXT_2);
            d.setPadding(0, Ui.dp(a, 3), 0, 0);
            d.setMaxLines(2);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            c.addView(d);
        }
        StringBuilder meta = new StringBuilder();
        if (r.fork) meta.append("⑂ ").append(T("gh_fork", "Fork")).append("   ");
        if (!r.canPush) meta.append("👁 ").append(T("gh_read_only", "Read only")).append("   ");
        if (r.empty) meta.append(T("gh_empty", "Empty"));
        if (meta.length() > 0) {
            TextView m = Ui.text(a, meta.toString().trim(), 11.5f, Ui.TEXT_3);
            m.setPadding(0, Ui.dp(a, 4), 0, 0);
            c.addView(m);
        }
        c.setOnClickListener(v -> cloneFromUrl(r.owner, r.name));
        return c;
    }

    private static String uniqueName(File dir, String base) {
        String n = UploadForm.sanitize(base);
        String cand = n;
        int i = 2;
        while (new File(dir, cand).exists()) cand = n + (i++);
        return cand;
    }

    private void cloneFromUrl(final String owner, final String name) {
        if (pick != null) {
            page.close();
            pick.on(owner, name);
            return;
        }
        final String dirName = uniqueName(projectsDir, name);
        final File dir = new File(projectsDir, dirName);
        final BusyDialog busyDlg = new BusyDialog(a, T("gh_cloning", "Cloning") + " " + owner + "/" + name, true).show();
        GitHubSession.background(() -> {
            String fail = null;
            boolean cancelled = false;
            try {
                busyDlg.message(T("gh_connecting", "Connecting..."));
                com.star4droid.star2d.git.ProjectGit.prepare(a);
                String token = GitHubSession.token(a);
                GitRepo.clone(GitHubApi.cloneUrl(owner, name), dir, token, busyDlg).close();
                for (String p : new String[]{"sounds", "anims", "images", "files", "icon"}) new File(dir, p).mkdirs();
            } catch (GitException e) {
                cancelled = busyDlg.cancelled();
                fail = e.getMessage() == null ? e.toString() : e.getMessage();
                deleteTree(dir);
            } catch (Throwable e) {
                fail = e.toString();
                deleteTree(dir);
            }
            final String f = fail;
            final boolean c = cancelled;
            Ui.ui(() -> {
                busyDlg.dismiss();
                if (f != null) {
                    if (!c) Snack.show(T("gh_clone_failed", "Clone failed") + ": " + f);
                    return;
                }
                boolean looksLikeProject = new File(dir, "scenes").isDirectory() || new File(dir, "Events").isDirectory();
                Snack.show(looksLikeProject ? T("gh_cloned", "Project copied")
                        : T("gh_cloned_not_project", "Copied, but this does not look like a Star3D project"));
                page.close();
                done.on(dir);
            });
        });
    }

    public static void deleteTree(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File c : k) deleteTree(c);
        f.delete();
    }
}
