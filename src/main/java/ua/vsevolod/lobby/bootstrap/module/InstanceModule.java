package ua.vsevolod.lobby.bootstrap.module;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.world.DimensionType;
import ua.vsevolod.lobby.bootstrap.server.Module;

import java.util.Base64;

public class InstanceModule implements Module {

    public static InstanceContainer lobby;

    @Override
    public void load() {

        lobby = MinecraftServer.getInstanceManager()
                .createInstanceContainer(DimensionType.THE_END);

        byte[] worldBytes = Base64.getDecoder().decode(WorldData.WORLD);
        lobby.setChunkLoader(new ByteArrayWorldLoader(worldBytes));
    }
}
