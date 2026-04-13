package ua.vsevolod.lobby.bootstrap.module;

import ua.vsevolod.lobby.bootstrap.server.Module;
import ua.vsevolod.lobby.command.admin.GamemodeCommand;
import ua.vsevolod.lobby.command.admin.StopCommand;
import ua.vsevolod.lobby.command.lobby.SpawnCommand;

public class CommandModule implements Module {

    @Override
    public void load() {
        new SpawnCommand();
        new GamemodeCommand();
        new StopCommand();
    }
}

