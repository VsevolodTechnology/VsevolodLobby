package ua.vsevolod.lobby.bootstrap.module;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.world.DimensionType;
import ua.vsevolod.lobby.bootstrap.server.Module;

import java.nio.file.Paths;

/**
 * Creates the lobby instance and loads its terrain from {@code world/region/*.mca} directly
 * via Minestom's {@code AnvilLoader} — no zip, no tempdir, no per-restart filesystem churn.
 *
 * <p>One world, one folder. To change the lobby map, replace the {@code world/region/}
 * directory on the server.</p>
 */
public class InstanceModule implements Module {

    public static InstanceContainer lobby;

    @Override
    public void load() {
        lobby = MinecraftServer.getInstanceManager()
                .createInstanceContainer(DimensionType.THE_END);
        // AnvilLoader expects the world root (containing a `region/` subfolder).
        lobby.setChunkLoader(new AnvilLoader(Paths.get("world")));
    }
}
