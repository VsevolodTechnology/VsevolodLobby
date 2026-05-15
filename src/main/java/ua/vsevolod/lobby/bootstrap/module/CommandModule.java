package ua.vsevolod.lobby.bootstrap.module;

import ua.vsevolod.lobby.bootstrap.server.Module;
import ua.vsevolod.lobby.command.admin.DeopCommand;
import ua.vsevolod.lobby.command.admin.GamemodeCommand;
import ua.vsevolod.lobby.command.admin.OpCommand;
import ua.vsevolod.lobby.command.admin.RamBarCommand;
import ua.vsevolod.lobby.command.admin.StopCommand;
import ua.vsevolod.lobby.command.admin.TpsBarCommand;
import ua.vsevolod.lobby.command.admin.VersionCommand;
import ua.vsevolod.lobby.command.lobby.SpawnCommand;
import ua.vsevolod.lobby.feature.admin.OpsStore;
import ua.vsevolod.lobby.feature.admin.VersionGate;

public class CommandModule implements Module {

    @Override
    public void load() {
        OpsStore.load();
        VersionGate.load();
        new SpawnCommand();
        new GamemodeCommand();
        new StopCommand();
        new OpCommand();
        new DeopCommand();
        new TpsBarCommand();
        new RamBarCommand();
        new VersionCommand();
    }
}

