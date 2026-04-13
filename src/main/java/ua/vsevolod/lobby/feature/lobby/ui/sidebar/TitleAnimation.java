package ua.vsevolod.lobby.feature.lobby.ui.sidebar;

import ua.vsevolod.lobby.config.LobbyConfig;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class TitleAnimation {

    private final List<String> frames = LobbyConfig.Animation.TITLE;
    private final AtomicInteger index = new AtomicInteger(0);

    public String nextFrame() {
        int i = index.getAndUpdate(old -> (old + 1) % frames.size());
        return frames.get(i);
    }
}
