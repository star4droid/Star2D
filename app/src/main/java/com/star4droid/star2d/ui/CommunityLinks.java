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

    /** {id, English title, English description, url}. */
    public static String[][] all() {
        return new String[][]{
                {"facebook", "Facebook Group", "Join the Star3D developers community on Facebook", "https://facebook.com/groups/995354408402399/"},
                {"whatsapp", "WhatsApp Group", "Live chat with developers and get quick assistance", "https://chat.whatsapp.com/Bxu3kM0b1oE9UL0iZhti4E?mode=ems_copy_c"},
                {"telegram", "Telegram Channel", "News, releases, asset packs, and updates", "https://t.me/+Qpd4IZQR-WM3YzM8"},
                {"discord", "Discord Community", "Discuss game dev, share scripts, and collaborate", "https://discord.gg/xNeKWxKnwM"},
                {"github", "GitHub Repository", "Star2D Engine open source code and issue tracking", "https://github.com/star4droid/Star2D/"},
                {"youtube", "YouTube Channel", "Game engine tutorials, guides, and feature showcases", "https://youtube.com/@star4droid?si=wNDqN_fbRIzf3KSQ"},
        };
    }

    /** {brand icon drawable, brand color} of a link id. */
    public static int[] icon(String id) {
        switch (id) {
            case "facebook": return new int[]{com.star4droid.star2d.evo.R.drawable.ic_brand_facebook, 0xFF0866FF};
            case "whatsapp": return new int[]{com.star4droid.star2d.evo.R.drawable.ic_brand_whatsapp, 0xFF25D366};
            case "telegram": return new int[]{com.star4droid.star2d.evo.R.drawable.ic_brand_telegram, 0xFF26A5E4};
            case "discord": return new int[]{com.star4droid.star2d.evo.R.drawable.ic_brand_discord, 0xFF5865F2};
            case "github": return new int[]{com.star4droid.star2d.evo.R.drawable.ic_brand_github, 0xFFE6EDF3};
            default: return new int[]{com.star4droid.star2d.evo.R.drawable.ic_brand_youtube, 0xFFFF0000};
        }
    }
}
