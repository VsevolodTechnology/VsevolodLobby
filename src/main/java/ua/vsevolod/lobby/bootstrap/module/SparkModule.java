package ua.vsevolod.lobby.bootstrap.module;

import ua.vsevolod.lobby.bootstrap.server.Module;
import ua.vsevolod.lobby.integration.spark.SparkService;

import java.nio.file.Path;

public class SparkModule implements Module {

    @Override
    public void load() {
        SparkService.init(Path.of("storage", "spark"));
    }
}
