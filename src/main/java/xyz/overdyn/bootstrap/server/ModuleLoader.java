package xyz.overdyn.bootstrap.server;

import java.util.ArrayList;
import java.util.List;

public class ModuleLoader {

    private final List<Module> modules = new ArrayList<>();

    public void register(Module module) {
        modules.add(module);
    }

    public void loadAll() {
        modules.forEach(Module::load);
    }
}