package ua.vsevolod.lobby.feature.lobby.ui.sidebar;

import java.util.List;

/**
 * Snapshot of {@code config/sidebar.yml}.
 *
 * <p>Placeholders:</p>
 * <ul>
 *   <li>{@code {ping}} — receiving player's ping</li>
 *   <li>{@code {frame}} — current animation frame substituted into {@link #titleFrameTemplate}</li>
 *   <li>{@code {world}}, {@code {status}}, {@code {count}} — within server-line templates</li>
 * </ul>
 */
public record SidebarConfig(
        boolean enabled,
        long titleAnimationIntervalMs,
        String titleFrameTemplate,
        List<String> titleFrames,
        long refreshIntervalMs,
        String welcomeText,
        List<String> descriptionLines,
        String modesHeader,
        String pingTemplate,
        String serverLineTemplate,
        String statusOnline,
        String statusSoon,
        String statusOffline
) {
}
