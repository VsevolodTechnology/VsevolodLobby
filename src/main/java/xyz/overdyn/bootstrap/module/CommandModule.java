package xyz.overdyn.bootstrap.module;

import xyz.overdyn.bootstrap.server.Module;
import xyz.overdyn.command.admin.GamemodeCommand;
import xyz.overdyn.command.admin.StopCommand;
import xyz.overdyn.command.lobby.SpawnCommand;

public class CommandModule implements Module {

    @Override
    public void load() {
        new SpawnCommand();
        new GamemodeCommand();
        new StopCommand();
    }
}

