package xyz.overdyn.bootstrap.module;

import xyz.overdyn.bootstrap.server.Module;
import xyz.overdyn.integration.spark.SparkService;

import java.nio.file.Path;

public class SparkModule implements Module {

    @Override
    public void load() {
        SparkService.init(Path.of("spark"));
    }
}
