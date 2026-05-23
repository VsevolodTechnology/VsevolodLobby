package ua.vsevolod.lobby.util;

import ua.vsevolod.lobby.config.SocialsConfig;

/**
 * Central placeholder resolver. Every surface that renders user-configured text (welcome,
 * holograms, tab, menus, server cards, click-action messages…) goes through {@link #apply}
 * so a single call gives access to all brand placeholders ({@code {discord}},
 * {@code {discord-short}}, etc.) — see {@link SocialsConfig#resolveAll(String)}.
 */
public final class Placeholders {
    private Placeholders() {}

    public static String apply(String text) {
        if (text == null || text.indexOf('{') < 0) return text;
        return SocialsConfig.get().resolveAll(text);
    }
}
