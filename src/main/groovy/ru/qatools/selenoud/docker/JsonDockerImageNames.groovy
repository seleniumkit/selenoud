package ru.qatools.selenoud.docker

import com.google.gson.Gson
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import ru.qatools.selenoud.ImageNameProvider
import ru.qatools.selenoud.util.Util

import java.nio.file.Path
import java.nio.file.WatchService

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import static org.codehaus.groovy.runtime.IOGroovyMethods.withCloseable
import static ru.qatools.selenoud.util.Util.prop

/**
 * @author Ilya Sadykov
 */
@Slf4j('LOG')
@CompileStatic
class JsonDockerImageNames implements ImageNameProvider {
    private static final Gson GSON = new Gson()
    private static final String CONFIG_FILE = prop('imagesFile', Util.getResource('/images.json').path) as String
    ImageConfig config

    JsonDockerImageNames() {
        final File configFile = new File(CONFIG_FILE)
        reloadConfiguration(configFile)
        Thread.start {
            withCloseable(configFile.toPath().parent.fileSystem.newWatchService()) { WatchService ws ->
                configFile.toPath().parent.register(ws, ENTRY_MODIFY)
                for (def key = ws.take(); key; key = ws.take()) {
                    key.pollEvents().collect { ((Path) it.context()) }
                            .findAll { it.fileName == configFile.toPath().fileName }
                            .each { reloadConfiguration(configFile) }; key.reset()
                }
            }
        }
    }

    @Override
    ImageConfig.Image image(String browserName, String browserVersion) {
        config.images[imageFullName(browserName, browserVersion)]
    }

    @Override
    Collection<String> names() {
        config.images.values().collect { it.image }
    }

    @Override
    List<String> env(String hubHost, String hubPort, String name, int port) {
        config.environment.collect {
            it.replace('$hubHost', hubHost)
                    .replace('$hubPort', hubPort)
                    .replace('$name', name)
                    .replace('$port', "$port")
        }
    }

    private static String imageFullName(String browserName, String browserVersion) {
        "${browserName}${browserVersion ? ":$browserVersion" : ''}"
    }

    protected void reloadConfiguration(File configFile) {
        LOG.info("Loading images configuration from file $configFile.path...")
        try {
            config = GSON.fromJson(configFile.newReader(), ImageConfig)
        } catch (Exception e) {
            LOG.error("Failed to read images mapping from file $configFile.path", e)
            config = new ImageConfig()
        }
        def mapping = config.images.entrySet().collect { "'${it.key}' -> '${it.value}'" }.join("\n\t")
        def environment = config.environment.join("\n\t")
        LOG.info("Configuration loaded! \n Images mapping: \n\t${mapping} \n Environment: \n\t${environment}")
    }
}
