package com.star4droid.star2d.ui;

/**
 * Single source of truth for the community / support links, shared by the home screen,
 * the support page and the periodic support dialog.
 */
public final class CommunityLinks {
    private CommunityLinks() {}

    /** Optimism (OP Mainnet) USDT wallet used for donations. */
    public static final String USDT_OPTIMISM_ADDRESS = "0x85E67D4a52Cc028C3D4d3194cbbd04a9cC886D77";
    public static final String DEV_WHATSAPP = "https://wa.me/249929354280";
    public static final String DEV_TELEGRAM = "https://t.me/str4droid1";

    /** Set to true once the Telegram community link is available. */
    public static final boolean TELEGRAM_COMMUNITY_ENABLED = false;

    /** {id, English title, English description, url, emoji}. */
    public static String[][] all() {
        java.util.ArrayList<String[]> l = new java.util.ArrayList<>();
        l.add(new String[]{"facebook", "Facebook Group", "Join the Star3D developers community on Facebook", "https://facebook.com/groups/995354408402399/", "👥"});
        l.add(new String[]{"whatsapp", "WhatsApp Group", "Live chat with developers and get quick assistance", "https://chat.whatsapp.com/Bxu3kM0b1oE9UL0iZhti4E?mode=ems_copy_c", "💬"});
        if (TELEGRAM_COMMUNITY_ENABLED)
            l.add(new String[]{"telegram", "Telegram Channel", "News, releases, asset packs, and updates", "https://t.me/+pE4VREnP04s5NDNk", "✈️"});
        l.add(new String[]{"discord", "Discord Community", "Discuss game dev, share scripts, and collaborate", "https://discord.gg/xNeKWxKnwM", "🎮"});
        l.add(new String[]{"github", "GitHub Repository", "Star2D Engine open source code and issue tracking", "https://github.com/star4droid/Star2D/", "💻"});
        l.add(new String[]{"youtube", "YouTube Channel", "Game engine tutorials, guides, and feature showcases", "https://youtube.com/@star4droid?si=wNDqN_fbRIzf3KSQ", "▶️"});
        return l.toArray(new String[0][]);
    }
}
