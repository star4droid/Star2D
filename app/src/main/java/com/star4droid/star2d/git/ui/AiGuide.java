package com.star4droid.star2d.git.ui;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;

/**
 * Step by step, animated guide: let Claude or ChatGPT edit the project through GitHub.
 * Tapping a step opens it (with its small animation); the Next button walks through them.
 */
public final class AiGuide {
    private AiGuide() {}

    private static final class Step {
        final int art;
        final String url, title, text, actionLabel, actionUrl;

        Step(int art, String url, String title, String text, String actionLabel, String actionUrl) {
            this.art = art;
            this.url = url;
            this.title = title;
            this.text = text;
            this.actionLabel = actionLabel;
            this.actionUrl = actionUrl;
        }
    }

    private static String T(String k, String d) {
        return Tr.t(k, d);
    }

    private static ArrayList<Step> claude() {
        ArrayList<Step> s = new ArrayList<>();
        s.add(new Step(GuideArt.ACCOUNT, null, T("guide_s_account", "You need a GitHub account"),
                T("guide_d_account", "Everything goes through GitHub. If you don't have an account yet, create one (it's free), then connect it in Star3D."),
                T("guide_create_account", "Create a GitHub account"), "https://github.com/signup"));
        s.add(new Step(GuideArt.WEBSITE, "claude.ai", T("guide_s_open_claude", "Open Claude on the website"),
                T("guide_d_open_claude", "Use claude.ai in your phone's browser and sign in. The plugins are added from the website."),
                T("guide_open_site", "Open claude.ai"), "https://claude.ai"));
        s.add(new Step(GuideArt.PLUS, null, T("guide_s_plus", "Tap the + button in the chat"),
                T("guide_d_plus", "It is at the left of the message box."), null, null));
        s.add(new Step(GuideArt.MENU, null, T("guide_s_plugins", "Choose “Plugins”"),
                T("guide_d_plugins", "In the menu that opens, tap Plugins."), null, null));
        s.add(new Step(GuideArt.INSTALL_CLAUDE, null, T("guide_s_install_claude", "Install “GitHub integration”"),
                T("guide_d_install_claude", "Install the one named GitHub integration and sign in to your GitHub account. There is another one named only “GitHub”: do not install it."),
                null, null));
        s.add(new Step(GuideArt.ASK, null, T("guide_s_ask", "Ask it to change your project"),
                T("guide_d_ask", "Write your repository name (owner/name) and what you want, for example “in me/MyGame add an enemy to scene1”. Claude edits the files and pushes them."),
                null, null));
        s.add(new Step(GuideArt.PULL, null, T("guide_s_pull", "Pull the changes in Star3D"),
                T("guide_d_pull", "Open your project, then the Git panel, then tap Pull. The scene reloads with the changes. You can undo anything from the history."),
                null, null));
        return s;
    }

    private static ArrayList<Step> chatgpt() {
        ArrayList<Step> s = new ArrayList<>();
        s.add(new Step(GuideArt.ACCOUNT, null, T("guide_s_account", "You need a GitHub account"),
                T("guide_d_account", "Everything goes through GitHub. If you don't have an account yet, create one (it's free), then connect it in Star3D."),
                T("guide_create_account", "Create a GitHub account"), "https://github.com/signup"));
        s.add(new Step(GuideArt.WEBSITE, "chatgpt.com", T("guide_s_open_gpt", "Open ChatGPT"),
                T("guide_d_open_gpt", "Use chatgpt.com in your browser (or the app) and sign in."),
                T("guide_open_site2", "Open chatgpt.com"), "https://chatgpt.com"));
        s.add(new Step(GuideArt.INSTALL_GPT, null, T("guide_s_install_gpt", "Plugins section: install GitHub"),
                T("guide_d_install_gpt", "In the Plugins section install GitHub, then connect your GitHub account and allow it to see your repository."),
                null, null));
        s.add(new Step(GuideArt.ASK, null, T("guide_s_ask", "Ask it to change your project"),
                T("guide_d_ask", "Write your repository name (owner/name) and what you want, for example “in me/MyGame add an enemy to scene1”. Claude edits the files and pushes them."),
                null, null));
        s.add(new Step(GuideArt.PULL, null, T("guide_s_pull", "Pull the changes in Star3D"),
                T("guide_d_pull", "Open your project, then the Git panel, then tap Pull. The scene reloads with the changes. You can undo anything from the history."),
                null, null));
        return s;
    }

    public static void show(Activity a) {
        final ScrollView sv = new ScrollView(a);
        final LinearLayout root = Ui.column(a);
        root.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 24));
        sv.addView(root);

        TextView intro = Ui.text(a, T("guide_intro", "Let an AI edit your game for you. It changes the files in your GitHub repository, and you pull them into Star3D."), 14, Ui.TEXT_2);
        intro.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), Ui.dp(a, 10));
        root.addView(intro);

        // tabs
        final LinearLayout tabs = Ui.row(a);
        tabs.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(a, 12), Ui.BORDER, Ui.dp(a, 1)));
        tabs.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        final TextView[] tabViews = new TextView[2];
        String[] names = {"Claude", "ChatGPT"};
        for (int i = 0; i < 2; i++) {
            TextView t = Ui.text(a, names[i], 15, Ui.TEXT);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
            tabs.addView(t, Ui.weight(1));
            tabViews[i] = t;
        }
        root.addView(tabs, Ui.matchWrap());

        final LinearLayout stepsBox = Ui.column(a);
        LayoutTransition lt = new LayoutTransition();
        lt.enableTransitionType(LayoutTransition.CHANGING);
        lt.setDuration(240);
        stepsBox.setLayoutTransition(lt);
        LinearLayout.LayoutParams sp = Ui.matchWrap();
        sp.topMargin = Ui.dp(a, 12);
        root.addView(stepsBox, sp);

        // private-repo note
        LinearLayout note = Ui.column(a);
        note.setBackground(Ui.rounded(0x22FFB300, Ui.dp(a, 12), 0x66FFB300, Ui.dp(a, 1)));
        note.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        TextView nt = Ui.text(a, "⚠  " + T("guide_private_title", "Private repositories"), 14, Ui.WARNING);
        nt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        note.addView(nt);
        TextView nd = Ui.text(a, T("guide_private_note", "We are not sure that Claude or ChatGPT can reach a private repository on their free plans. If the AI says it can't see your repository, make it public from the Git panel (you can switch it back to private afterwards)."), 13, Ui.TEXT_2);
        nd.setPadding(0, Ui.dp(a, 6), 0, 0);
        note.addView(nd);
        LinearLayout.LayoutParams np = Ui.matchWrap();
        np.topMargin = Ui.dp(a, 14);
        root.addView(note, np);

        final int[] tab = {0};
        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            for (int i = 0; i < 2; i++) {
                boolean on = tab[0] == i;
                tabViews[i].setBackground(on ? Ui.rounded(Ui.ACCENT, Ui.dp(a, 9)) : null);
                tabViews[i].setTextColor(on ? 0xFFFFFFFF : Ui.TEXT_2);
            }
            buildSteps(a, stepsBox, tab[0] == 0 ? claude() : chatgpt());
        };
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            tabViews[i].setOnClickListener(v -> {
                if (tab[0] == idx) return;
                tab[0] = idx;
                rebuild[0].run();
            });
        }
        rebuild[0].run();

        new FullPage(a, T("guide_title", "Use with Claude or ChatGPT")).content(sv).show();
    }

    private static void buildSteps(final Activity a, final LinearLayout box, final ArrayList<Step> steps) {
        box.removeAllViews();
        final View[] bodies = new View[steps.size()];
        final TextView[] badges = new TextView[steps.size()];
        final GuideArt[] arts = new GuideArt[steps.size()];
        final int[] open = {-1};
        final Runnable[] openStep = new Runnable[1];
        final int[] pending = {0};

        for (int i = 0; i < steps.size(); i++) {
            final int idx = i;
            final Step st = steps.get(i);
            LinearLayout card = Ui.column(a);
            card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(a, 14), Ui.BORDER, Ui.dp(a, 1)));
            LinearLayout head = Ui.row(a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
            TextView badge = Ui.text(a, String.valueOf(i + 1), 14, 0xFFFFFFFF);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            badge.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(a, 15)));
            head.addView(badge, new LinearLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30)));
            badges[i] = badge;
            TextView title = Ui.text(a, st.title, 15, Ui.TEXT);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams tl = Ui.weight(1);
            tl.setMarginStart(Ui.dp(a, 12));
            head.addView(title, tl);
            card.addView(head, Ui.matchWrap());

            LinearLayout body = Ui.column(a);
            body.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), Ui.dp(a, 12));
            body.setVisibility(View.GONE);
            GuideArt art = new GuideArt(a);
            art.setKind(st.art, st.url);
            body.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 170)));
            arts[i] = art;
            TextView desc = Ui.text(a, st.text, 14, Ui.TEXT_2);
            desc.setPadding(Ui.dp(a, 2), Ui.dp(a, 10), Ui.dp(a, 2), 0);
            body.addView(desc);
            if (st.actionLabel != null) {
                View b = Ui.button(a, 0, st.actionLabel, false, v -> {
                    try {
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(st.actionUrl)));
                    } catch (Exception ignored) {
                    }
                });
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bp.topMargin = Ui.dp(a, 10);
                body.addView(b, bp);
            }
            LinearLayout nav = Ui.row(a);
            nav.setGravity(Gravity.END);
            if (idx > 0) {
                nav.addView(Ui.button(a, 0, Tr.t("back", "Back"), false, v -> {
                    open[0] = idx - 1;
                    openStep[0].run();
                }));
            }
            View next = Ui.button(a, 0, idx == steps.size() - 1 ? T("guide_done", "Done") : T("guide_next", "Next"), true, v -> {
                if (idx == steps.size() - 1) {
                    open[0] = -1;
                    pending[0] = steps.size();
                } else {
                    open[0] = idx + 1;
                    pending[0] = Math.max(pending[0], idx + 1);
                }
                openStep[0].run();
            });
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.setMarginStart(Ui.dp(a, 8));
            nav.addView(next, nlp);
            LinearLayout.LayoutParams navp = Ui.matchWrap();
            navp.topMargin = Ui.dp(a, 12);
            body.addView(nav, navp);
            card.addView(body, Ui.matchWrap());
            bodies[i] = body;
            head.setOnClickListener(v -> {
                open[0] = open[0] == idx ? -1 : idx;
                openStep[0].run();
            });
            LinearLayout.LayoutParams cp = Ui.matchWrap();
            cp.topMargin = Ui.dp(a, 8);
            box.addView(card, cp);
        }
        openStep[0] = () -> {
            for (int i = 0; i < bodies.length; i++) {
                boolean on = open[0] == i;
                bodies[i].setVisibility(on ? View.VISIBLE : View.GONE);
                boolean done = i < pending[0] && !on;
                badges[i].setText(done ? "✓" : String.valueOf(i + 1));
                badges[i].setBackground(Ui.rounded(on ? Ui.ACCENT : (done ? Ui.SUCCESS : Ui.SURFACE_3), Ui.dp(a, 15)));
            }
        };
        open[0] = 0;
        openStep[0].run();
    }
}
