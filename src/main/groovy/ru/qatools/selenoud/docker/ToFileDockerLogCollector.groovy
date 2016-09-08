package ru.qatools.selenoud.docker

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import ru.qatools.selenoud.LogCollector

import java.nio.file.Path
import java.nio.file.Paths

import static com.google.common.base.Charsets.UTF_8
import static org.apache.commons.io.FileUtils.forceMkdir
import static org.apache.commons.io.IOUtils.copy
import static ru.qatools.selenoud.util.Util.prop
/**
 * @author Ilya Sadykov
 */
@Slf4j('LOG')
@CompileStatic
class ToFileDockerLogCollector implements LogCollector {
    private final String DIR = prop('logs.dir', '/tmp/selenoud/logs')

    ToFileDockerLogCollector() {
        LOG.info("Initializing containers logs to file collector for dir ${DIR}...")
        forceMkdir(new File(DIR))
    }

    @Override
    void collect(String containerName, InputStream inputStream) {
        LOG.info("Collecting logs for container ${containerName}...")
        copy(new InputStreamReader(inputStream, UTF_8), new FileOutputStream(pathOf(containerName).toFile()))
    }

    @Override
    InputStream get(String containerName) {
        pathOf(containerName).newInputStream()
    }

    protected Path pathOf(String containerName) {
        Paths.get(DIR, containerName)
    }
}
