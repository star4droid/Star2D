package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.git.AiDocs;
import com.star4droid.star2d.git.GitHubApi;
import com.star4droid.star2d.git.GitHubSession;
import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.git.ui.AiGuide;
import com.star4droid.star2d.git.ui.BranchesPage;
import com.star4droid.star2d.git.ui.ConflictPage;
import com.star4droid.star2d.git.ui.DiffPage;
import com.star4droid.star2d.git.ui.GitContext;
import com.star4droid.star2d.git.ui.GitDialogs;
import com.star4droid.star2d.git.ui.GitHubConnect;
import com.star4droid.star2d.git.ui.HistoryPage;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Git / GitHub for the open project: status, commit, pull, push, history, branches, conflicts and the rest. */
public class GitPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private FrameLayout root;
    private ProgressBar busyBar;
    private LinearLayout content;
    private GitContext g;
    private String projectPath;

    // state of the last refresh
    private static final class Snap {
        String branch = "main", remote;
        boolean hasCommits, merging;
        List<GitRepo.Change> changes = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        int ahead, behind, stashes;
        boolean hasUpstream;
    }

    private Snap snap;
    private GitHubApi.Repo info;
    private String infoFor;
    private final Set<String> deselected = new HashSet<>();
    private String commitMsg = "";
    private boolean loading;

    public GitPanel(EditorShell shell) {
        this.shell = shell;
    }

    private static String T(String k, String d) { return Tr.t(k, d); }

    @Override public String title() { return "Git"; }
    @Override public int icon() { return R.drawable.ic_s2_git; }

    @Override
    public View[] headerActions(Context c) {
        return new View[]{Ui.iconButton(c, R.drawable.ic_s2_refresh, T("refresh", "Refresh"), v -> refresh())};
    }

    @Override
    public View view(Context c) {
        if (root != null) return root;
        root = new FrameLayout(c);
        content = Ui.column(c);
        root.addView(content, Ui.match());
        busyBar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        busyBar.setIndeterminate(true);
        busyBar.setVisibility(View.INVISIBLE);
        root.addView(busyBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 3), Gravity.TOP));
        return root;
    }

    @Override
    public void onShown() {
        refresh();
    }

    // ================================================================ context

    private void withContext(final Runnable then) {
        if (!Bridge.ready()) return;
        // the editor can be gone for a moment (project being closed): then there is nothing to show
        Bridge.query(() -> {
            com.star4droid.star2d.editor.LibgdxEditor ed = Bridge.editor();
            if (ed == null) return "";
            com.star4droid.star2d.Helpers.editor.Project pr = ed.getProject();
            return pr == null || pr.getPath() == null ? "" : pr.getPath();
        }, p -> {
            if (p.isEmpty()) return;
            if (g == null || !p.equals(projectPath)) {
                projectPath = p;
                g = new GitContext(shell.activity(), new File(p));
                g.saver = next -> Bridge.query(() -> {
                    Bridge.controls().actionSave();
                    // remember what each open scene looks like on disk, to reload only what git changes
                    sceneStamps = Bridge.app().openSceneFingerprints();
                    return true;
                }, v -> next.run());
                g.onFilesChanged = this::reloadProject;
                g.onRefresh = this::refresh;
                snap = null;
                info = null;
                deselected.clear();
            }
            then.run();
        });
    }

    /** Scene fingerprints taken right before the last git operation (null = unknown, reload everything). */
    private java.util.HashMap<String, String> sceneStamps;

    /** Files changed on disk: reload only the open scene when it changed, close other changed tabs. */
    private void reloadProject() {
        final java.util.HashMap<String, String> before = sceneStamps;
        sceneStamps = null;
        Bridge.query(() -> Bridge.app().applyExternalChanges(before), report -> {
            if (report != null && !report.isEmpty()) Snack.show(report);
        });
    }

    // ================================================================ refresh

    public void refresh() {
        withContext(() -> {
            loading = true;
            busyBar.setVisibility(View.VISIBLE);
            g.work(r -> {
                Snap s = new Snap();
                s.branch = r.currentBranch();
                s.hasCommits = r.hasCommits();
                s.merging = r.isMerging();
                s.remote = r.hasRemote() ? r.remoteUrl() : null;
                s.changes = r.status();
                for (GitRepo.Change c : new ArrayList<>(s.changes)) {
                    if (c.kind == GitRepo.Kind.CONFLICT) {
                        s.conflicts.add(c.path);
                    }
                }
                if (s.hasCommits) {
                    for (GitRepo.BranchInfo b : r.branches()) {
                        if (b.current) {
                            s.ahead = b.ahead;
                            s.behind = b.behind;
                            s.hasUpstream = b.hasRemote;
                        }
                    }
                    s.stashes = r.stashes().size();
                }
                return s;
            }, s -> {
                snap = s;
                loading = false;
                busyBar.setVisibility(View.INVISIBLE);
                render();
                loadInfo();
            }, false);
        });
    }

    /** Asks GitHub about the connected repository (visibility, permissions, parent) and redraws when it arrives. */
    private void loadInfo() {
        if (snap == null || snap.remote == null || !GitHubSession.connected(shell.activity())) {
            info = null;
            return;
        }
        final String[] on = GitHubApi.parseRepoUrl(snap.remote);
        if (on == null) return;
        final String key = on[0] + "/" + on[1];
        GitHubSession.async(() -> GitHubSession.api(shell.activity()).repo(on[0], on[1]), (r, err) -> {
            if (err == null && r != null) {
                info = r;
                infoFor = key;
                render();
            }
        });
    }

    // ================================================================ rendering

    private void render() {
        if (content == null || snap == null || g == null) return;
        final Context c = content.getContext();
        content.removeAllViews();
        boolean land = Ui.isLandscape(c);
        LinearLayout left = Ui.column(c), right = Ui.column(c);
        int p = Ui.dp(c, 10);
        left.setPadding(p, p, p, p);
        right.setPadding(p, p, p, p);

        left.addView(connectionCard(c), gap(c, 0));
        if (snap.remote != null) left.addView(syncRow(c), gap(c, 8));
        if (snap.merging) left.addView(mergeBanner(c), gap(c, 8));
        (land ? right : left).addView(changesCard(c), gap(c, 8));
        (land ? right : left).addView(commitCard(c), gap(c, 8));
        left.addView(moreCard(c), gap(c, 8));

        if (land) {
            LinearLayout row = Ui.row(c);
            ScrollView a = new ScrollView(c), b = new ScrollView(c);
            a.addView(left);
            b.addView(right);
            row.addView(a, Ui.weight(1));
            row.addView(b, Ui.weight(1));
            content.addView(row, new LinearLayout.LayoutParams(-1, -1));
        } else {
            ScrollView sv = new ScrollView(c);
            sv.addView(left);
            content.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        }
    }

    private static LinearLayout.LayoutParams gap(Context c, int top) {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(c, top);
        return lp;
    }

    private LinearLayout card(Context c) {
        LinearLayout l = Ui.column(c);
        l.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 12));
        l.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 12)));
        return l;
    }

    private TextView chip(Context c, String text, int color, View.OnClickListener l) {
        TextView t = Ui.text(c, text, 12, color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
        t.setBackground(Ui.rounded(0x22FFFFFF, Ui.dp(c, 12)));
        if (l != null) {
            t.setOnClickListener(l);
            t.setClickable(true);
        }
        return t;
    }

    private void addChip(LinearLayout row, TextView chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(Ui.dp(chip.getContext(), 6));
        row.addView(chip, lp);
    }

    private boolean canPush() {
        return info == null || info.canPush;
    }

    // ---------------------------------------------------------------- connection

    private View connectionCard(final Context c) {
        LinearLayout card = card(c);
        boolean signedIn = GitHubSession.connected(c);
        if (snap.remote == null) {
            TextView t = Ui.text(c, T("git_not_on_github", "This project is only on your phone"), 15, Ui.TEXT);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(t);
            TextView d = Ui.text(c, T("git_not_on_github_d",
                    "Put it on GitHub to keep a backup, work from another device, and let Claude or ChatGPT help you edit it."), 12.5f, Ui.TEXT_2);
            d.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
            card.addView(d);
            LinearLayout row = Ui.row(c);
            if (!signedIn) {
                row.addView(btn(c, R.drawable.ic_s2_git, T("gh_connect_github", "Connect to GitHub"), true, v -> GitHubConnect.show(shell.activity(), this::refresh)));
            } else {
                row.addView(btn(c, R.drawable.ic_s2_upload, T("git_create_repo_short", "Create repository"), true, v -> GitDialogs.createRepo(g, this::refresh)));
                row.addView(btn(c, R.drawable.ic_s2_link, T("git_connect_existing_short", "Connect existing"), false, v -> GitDialogs.connectExisting(g, this::refresh)));
            }
            card.addView(row);
            return card;
        }
        String[] on = GitHubApi.parseRepoUrl(snap.remote);
        String name = on == null ? snap.remote : on[0] + "/" + on[1];
        TextView t = Ui.text(c, name, 15, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setOnClickListener(v -> GitDialogs.openUrl(g, "https://github.com/" + name));
        card.addView(t);
        LinearLayout chips = Ui.row(c);
        chips.setPadding(0, Ui.dp(c, 8), 0, 0);
        addChip(chips, chip(c, "⑂ " + snap.branch, Ui.ACCENT, v -> BranchesPage.show(g)));
        if (info != null && infoFor != null && infoFor.equalsIgnoreCase(name)) {
            final GitHubApi.Repo r = info;
            addChip(chips, chip(c, r.isPrivate ? "🔒 " + T("gh_private", "Private") : "🌐 " + T("gh_public", "Public"), Ui.TEXT_2,
                    r.canAdmin ? v -> GitDialogs.visibility(g, r, this::loadInfo) : null));
            if (!r.canPush) addChip(chips, chip(c, "👁 " + T("gh_read_only", "Read only"), Ui.WARNING, null));
        }
        card.addView(chips);
        String sync;
        if (!snap.hasCommits) sync = T("git_no_commits", "No commits yet");
        else if (!snap.hasUpstream) sync = T("git_not_pushed", "This branch is not on GitHub yet");
        else if (snap.ahead == 0 && snap.behind == 0) sync = "✓ " + T("git_in_sync", "Up to date with GitHub");
        else sync = (snap.ahead > 0 ? "↑ " + snap.ahead + " " + T("git_to_send", "to send") + "   " : "")
                    + (snap.behind > 0 ? "↓ " + snap.behind + " " + T("git_to_get", "to get") : "");
        TextView st = Ui.text(c, sync, 12.5f, snap.behind > 0 ? Ui.WARNING : Ui.TEXT_2);
        st.setPadding(0, Ui.dp(c, 8), 0, 0);
        card.addView(st);
        if (!GitHubSession.connected(c)) {
            TextView w = Ui.text(c, T("git_signin_needed", "Sign in to GitHub to pull and push."), 12.5f, Ui.WARNING);
            w.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));
            card.addView(w);
            card.addView(btn(c, R.drawable.ic_s2_git, T("gh_connect_github", "Connect to GitHub"), true, v -> GitHubConnect.show(shell.activity(), this::refresh)));
        }
        return card;
    }

    private LinearLayout btn(Context c, int icon, String label, boolean primary, View.OnClickListener l) {
        LinearLayout b = Ui.button(c, icon, label, primary, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 38));
        lp.setMarginEnd(Ui.dp(c, 8));
        b.setLayoutParams(lp);
        return b;
    }

    // ---------------------------------------------------------------- pull / push

    private View syncRow(final Context c) {
        LinearLayout row = Ui.row(c);
        LinearLayout pull = Ui.button(c, R.drawable.ic_s2_download,
                T("git_pull", "Pull") + (snap.behind > 0 ? "  ↓" + snap.behind : ""), snap.behind > 0, v -> pull());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1);
        lp.setMarginEnd(Ui.dp(c, 6));
        row.addView(pull, lp);
        if (canPush()) {
            LinearLayout push = Ui.button(c, R.drawable.ic_s2_upload,
                    T("git_push", "Push") + (snap.ahead > 0 ? "  ↑" + snap.ahead : ""), snap.ahead > 0 || !snap.hasUpstream, v -> push());
            row.addView(push, new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1));
        } else if (info != null) {
            LinearLayout fork = Ui.button(c, R.drawable.ic_s2_copy, T("git_fork_short", "Make my copy"), true, v -> GitDialogs.fork(g, info, this::refresh));
            row.addView(fork, new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1));
        }
        return row;
    }

    private void pull() {
        if (!GitHubSession.connected(shell.activity())) {
            GitHubConnect.show(shell.activity(), this::refresh);
            return;
        }
        g.workWithProgress(T("git_pulling", "Getting changes..."), true, (r, p) -> r.pull(false, p), o -> {
            report(o);
            refresh();
        });
    }

    private void report(GitRepo.MergeOutcome o) {
        switch (o.outcome) {
            case UP_TO_DATE:
                Snack.show(T("git_up_to_date", "Already up to date"));
                break;
            case FAST_FORWARD:
                Snack.show(T("git_updated", "Updated from GitHub"));
                g.onFilesChanged.run();
                break;
            case MERGED:
                Snack.show(T("git_merged", "Merged"));
                g.onFilesChanged.run();
                break;
            case CONFLICTS:
                Snack.show(T("git_err_conflict", "Conflicts"));
                ConflictPage.show(g);
                break;
            case NO_REMOTE_BRANCH:
                Snack.show(T("git_no_remote_branch", "This branch is not on GitHub yet. Push it first."));
                break;
            case LOCAL_CHANGES:
                new androidx.appcompat.app.AlertDialog.Builder(shell.activity())
                        .setTitle(T("git_err_dirty", "You have unsaved changes"))
                        .setMessage(T("git_dirty_pull", "Your changes would be overwritten by this update. Commit them first, or put them aside with Stash."))
                        .setPositiveButton(T("ok", "OK"), null)
                        .setNeutralButton(T("git_stash", "Stash"), (d, w) -> GitDialogs.stash(g)).show();
                break;
            default:
                new androidx.appcompat.app.AlertDialog.Builder(shell.activity())
                        .setTitle(T("git_cannot_merge", "Cannot merge automatically"))
                        .setMessage(o.message == null ? "" : o.message)
                        .setPositiveButton(T("ok", "OK"), null).show();
        }
    }

    private void push() {
        if (!GitHubSession.connected(shell.activity())) {
            GitHubConnect.show(shell.activity(), this::refresh);
            return;
        }
        g.workWithProgress(T("git_pushing", "Pushing..."), true, (r, p) -> {
            r.push(false, p);
            return true;
        }, ok -> {
            Snack.show(T("git_pushed", "Pushed"));
            refresh();
        });
    }

    // ---------------------------------------------------------------- merge banner

    private View mergeBanner(Context c) {
        LinearLayout card = card(c);
        card.setBackground(Ui.rounded(0x22FFB300, Ui.dp(c, 12), 0x66FFB300, Ui.dp(c, 1)));
        boolean open = !snap.conflicts.isEmpty();
        TextView t = Ui.text(c, open ? T("git_merge_in_progress", "A merge needs your help") + " (" + snap.conflicts.size() + ")"
                : T("git_merge_ready", "Merge ready to finish"), 14.5f, Ui.WARNING);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);
        LinearLayout row = Ui.row(c);
        row.setPadding(0, Ui.dp(c, 8), 0, 0);
        row.addView(btn(c, R.drawable.ic_s2_merge, open ? T("git_resolve", "Resolve conflicts") : T("git_finish_merge", "Finish merge"), true, v -> ConflictPage.show(g)));
        card.addView(row);
        return card;
    }

    // ---------------------------------------------------------------- changes

    private View changesCard(final Context c) {
        LinearLayout card = card(c);
        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int n = 0;
        for (GitRepo.Change ch : snap.changes) if (ch.kind != GitRepo.Kind.CONFLICT) n++;
        TextView t = Ui.text(c, T("git_changes", "Changes") + " (" + n + ")", 15, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(t, Ui.weight(1));
        if (n > 0) {
            TextView all = chip(c, deselected.isEmpty() ? T("git_select_none", "Select none") : T("git_select_all", "Select all"), Ui.ACCENT, v -> {
                if (deselected.isEmpty()) for (GitRepo.Change ch : snap.changes) deselected.add(ch.path);
                else deselected.clear();
                render();
            });
            head.addView(all);
        }
        card.addView(head);
        if (n == 0) {
            TextView ok = Ui.text(c, "✓ " + T("git_clean", "Everything is saved"), 13.5f, Ui.SUCCESS);
            ok.setPadding(0, Ui.dp(c, 8), 0, 0);
            card.addView(ok);
            return card;
        }
        int shown = 0;
        for (final GitRepo.Change ch : snap.changes) {
            if (ch.kind == GitRepo.Kind.CONFLICT) continue;
            if (++shown > 200) {
                TextView more = Ui.text(c, "…", 13, Ui.TEXT_3);
                card.addView(more);
                break;
            }
            LinearLayout row = Ui.row(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(c, 40));
            row.setBackground(Ui.ripple(0, Ui.dp(c, 6)));
            CheckBox cb = new CheckBox(c);
            cb.setChecked(!deselected.contains(ch.path));
            cb.setOnCheckedChangeListener((b, on) -> {
                if (on) deselected.remove(ch.path);
                else deselected.add(ch.path);
            });
            row.addView(cb);
            String sym;
            int col;
            switch (ch.kind) {
                case ADDED: case UNTRACKED: sym = "＋"; col = Ui.SUCCESS; break;
                case DELETED: sym = "－"; col = Ui.DANGER; break;
                default: sym = "•"; col = Ui.WARNING;
            }
            TextView s = Ui.text(c, sym, 14, col);
            s.setPadding(0, 0, Ui.dp(c, 6), 0);
            row.addView(s);
            TextView p = Ui.text(c, ch.path, 12.5f, Ui.TEXT_2);
            p.setMaxLines(2);
            p.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.addView(p, Ui.weight(1));
            row.setOnClickListener(v -> DiffPage.show(g, ch.path, r -> r.diff(ch.path)));
            row.setOnLongClickListener(v -> {
                Dialogs.confirm(c, T("git_discard", "Discard changes"), ch.path + "\n\n" + T("git_discard_q", "This file goes back to the last commit. This cannot be undone."),
                        T("git_discard", "Discard changes"), () -> g.work(r -> {
                            r.discard(ch.path);
                            return true;
                        }, ok -> {
                            g.onFilesChanged.run();
                            refresh();
                        }));
                return true;
            });
            card.addView(row);
        }
        return card;
    }

    // ---------------------------------------------------------------- commit

    private View commitCard(final Context c) {
        LinearLayout card = card(c);
        final EditText msg = Ui.input(c, commitMsg, T("git_commit_hint", "Describe what you changed"));
        msg.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        msg.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            public void onTextChanged(CharSequence s, int a, int b, int d) {}
            public void afterTextChanged(Editable s) { commitMsg = s.toString(); }
        });
        card.addView(msg, Ui.matchWrap());
        LinearLayout row = Ui.row(c);
        row.setPadding(0, Ui.dp(c, 8), 0, 0);
        row.addView(btn(c, R.drawable.ic_s2_commit, T("git_commit", "Commit"), true, v -> commit(msg, false)));
        if (snap.remote != null && canPush() && GitHubSession.connected(c))
            row.addView(btn(c, R.drawable.ic_s2_upload, T("git_commit_push", "Commit & push"), false, v -> commit(msg, true)));
        card.addView(row);
        return card;
    }

    private void commit(final EditText msgView, final boolean thenPush) {
        final String msg = msgView.getText().toString().trim();
        if (snap.merging) {
            ConflictPage.show(g);
            return;
        }
        if (msg.isEmpty()) {
            msgView.setError(T("git_msg_needed", "Write a short message"));
            return;
        }
        final List<String> chosen = new ArrayList<>();
        for (GitRepo.Change ch : snap.changes) if (!deselected.contains(ch.path)) chosen.add(ch.path);
        if (chosen.isEmpty()) {
            Snack.show(T("git_nothing_selected", "Nothing to commit"));
            return;
        }
        g.workWithProgress(thenPush ? T("git_commit_push", "Commit & push") : T("git_commit", "Commit"), true, (r, p) -> {
            File dir = r.dir();
            Set<String> paths = new LinkedHashSet<>(chosen);
            if (AiDocs.enabled(dir)) {
                AiDocs.generate(dir, false);
                for (GitRepo.Change ch : r.status()) {
                    if (ch.path.equals(AiDocs.STATE)) paths.add(AiDocs.STATE);
                }
            }
            List<String> big = r.bigFiles(100L * 1024 * 1024);
            if (!big.isEmpty()) throw new com.star4droid.star2d.git.GitException(com.star4droid.star2d.git.GitException.Kind.OTHER,
                    T("git_big_files", "GitHub does not accept files over 100 MB:") + "\n" + big);
            r.commit(msg, paths);
            if (thenPush) r.push(false, p);
            return true;
        }, ok -> {
            commitMsg = "";
            deselected.clear();
            Snack.show(thenPush ? T("git_pushed", "Pushed") : T("git_committed", "Committed"));
            refresh();
        });
    }

    // ---------------------------------------------------------------- more

    private View moreCard(final Context c) {
        LinearLayout card = card(c);
        TextView t = Ui.text(c, T("git_more_title", "More"), 15, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);
        card.addView(item(c, R.drawable.ic_s2_history, T("git_history", "History"), T("git_history_d", "See every commit as a tree"), v -> HistoryPage.show(g)));
        card.addView(item(c, R.drawable.ic_s2_tree, T("git_branches", "Branches"), snap.branch, v -> BranchesPage.show(g)));
        card.addView(item(c, R.drawable.ic_s2_layers, T("git_stash", "Stash"), snap.stashes > 0 ? snap.stashes + "" : T("git_stash_d", "Put changes aside"), v -> GitDialogs.stash(g)));
        card.addView(item(c, R.drawable.ic_s2_file, T("git_tags", "Tags"), T("git_tags_d", "Mark versions like v1.0"), v -> GitDialogs.tags(g)));
        if (snap.remote != null && canPush() && GitHubSession.connected(c))
            card.addView(item(c, R.drawable.ic_s2_upload, T("git_force_push", "Force push"), T("git_force_d", "Replace GitHub with this version"), v -> GitDialogs.forcePush(g, this::refresh)));
        if (info != null && info.fork && info.parent != null && !info.parent.isEmpty())
            card.addView(item(c, R.drawable.ic_s2_send, T("git_pull_request", "Pull request"), info.parent, v -> GitDialogs.pullRequest(g, info, snap.branch)));
        if (info != null && info.canAdmin)
            card.addView(item(c, info.isPrivate ? R.drawable.ic_s2_lock_open : R.drawable.ic_s2_lock,
                    info.isPrivate ? T("git_make_public", "Make public") : T("git_make_private", "Make private"), null, v -> GitDialogs.visibility(g, info, this::loadInfo)));
        card.addView(item(c, R.drawable.ic_s2_ai, T("git_ai_prepare", "Prepare for Claude / ChatGPT"),
                T("git_ai_prepare_d", "Adds ENGINE.md and STATE.md so an AI understands your game"), v -> prepareAi()));
        card.addView(item(c, R.drawable.ic_s2_help, T("guide_title", "Use Claude or ChatGPT with your game"), null, v -> AiGuide.show(shell.activity())));
        if (snap.remote != null) {
            card.addView(item(c, R.drawable.ic_s2_open_in_new, T("gh_open_github", "Open on GitHub"), null, v -> {
                String[] on = GitHubApi.parseRepoUrl(snap.remote);
                if (on != null) GitDialogs.openUrl(g, "https://github.com/" + on[0] + "/" + on[1]);
            }));
            card.addView(item(c, R.drawable.ic_s2_link, T("git_disconnect", "Disconnect from GitHub repository"), null, v -> GitDialogs.disconnect(g)));
        }
        card.addView(item(c, R.drawable.ic_s2_git, T("gh_title", "GitHub"), GitHubSession.connected(c) ? "@" + (GitHubSession.user(c) == null ? "" : GitHubSession.user(c).login) : T("gh_not_connected", "Not connected"),
                v -> GitHubConnect.show(shell.activity(), this::refresh)));
        if (!snap.changes.isEmpty())
            card.addView(item(c, R.drawable.ic_s2_delete, T("git_discard_all", "Discard all changes"), null, v ->
                    Dialogs.confirm(c, T("git_discard_all", "Discard all changes"), T("git_discard_q", "This file goes back to the last commit. This cannot be undone."),
                            T("git_discard", "Discard changes"), () -> g.work(r -> {
                                r.discardAll();
                                return true;
                            }, ok -> {
                                g.onFilesChanged.run();
                                refresh();
                            }))));
        return card;
    }

    private View item(Context c, int icon, String title, String sub, View.OnClickListener l) {
        LinearLayout row = PanelHost.listRow(c, icon, Ui.TEXT_2, title, sub);
        row.setOnClickListener(l);
        return row;
    }

    private void prepareAi() {
        Dialogs.confirm(shell.activity(), T("git_ai_prepare", "Prepare for Claude / ChatGPT"),
                T("git_ai_prepare_q", "Two Markdown files are added to your project: ENGINE.md explains how Star3D projects work, STATE.md lists your scenes, elements, events and the last build errors. They are updated on every commit."),
                T("ok", "OK"), () -> g.workWithProgress(T("git_working", "Working..."), false, (r, p) -> {
                    AiDocs.generate(r.dir(), true);
                    java.util.ArrayList<String> files = new java.util.ArrayList<>(java.util.Arrays.asList(AiDocs.ENGINE, AiDocs.STATE));
                    files.addAll(AiDocs.docPaths(r.dir()));
                    r.commit("Add ENGINE.md, STATE.md and AI docs for assistants", files);
                    if (r.hasRemote() && canPush() && GitHubSession.connected(shell.activity())) r.push(false, p);
                    return true;
                }, ok -> {
                    Snack.show(T("git_done", "Done"));
                    refresh();
                }));
    }
}
