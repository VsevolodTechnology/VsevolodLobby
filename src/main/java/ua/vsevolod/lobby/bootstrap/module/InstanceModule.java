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

//        try {
//            byte[] worldBytes = WorldBytes.fromWorldFolder(Path.of("worlds/lobby"));
//            String base64 = Base64.getEncoder().encodeToString(worldBytes);
//
//            int chunkSize = 30000;
//
//            StringBuilder out = new StringBuilder();
//            out.append("public final class WorldData {\n\n");
//            out.append("    public static final String WORLD;\n\n");
//            out.append("    static {\n");
//            out.append("        WORLD = new StringBuilder()\n");
//
//            for (int i = 0; i < base64.length(); i += chunkSize) {
//                int end = Math.min(i + chunkSize, base64.length());
//                String part = base64.substring(i, end);
//                out.append("                .append(\"").append(part).append("\")\n");
//            }
//
//            out.append("                .toString();\n");
//            out.append("    }\n\n");
//            out.append("    private WorldData() {\n");
//            out.append("    }\n");
//            out.append("}\n");
//
//            Files.writeString(Path.of("WorldData.java"), out.toString());
//
//            System.out.println("Generated WorldData.java");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        };

        byte[] worldBytes = Base64.getDecoder().decode(WorldData.WORLD);

        lobby.setChunkLoader(new ByteArrayWorldLoader(worldBytes));

//        lobby.setChunkLoader(
//                new AnvilLoader(LobbyConfig.Settings.WORLD_MAP_PATH)
//        );
    }
}
