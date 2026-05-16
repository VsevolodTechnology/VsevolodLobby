package ua.vsevolod.lobby.feature.lobby.interaction.npc.config;

import net.minestom.server.coordinate.Pos;

public record NpcPosition(double x, double y, double z, float yaw, float pitch) {
    public Pos toPos() {
        return new Pos(x, y, z, yaw, pitch);
    }

    public static NpcPosition from(Pos pos) {
        return new NpcPosition(pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch());
    }
}
