package ru.qatools.selenoud

import groovy.transform.CompileStatic
import ru.qatools.selenoud.docker.ToFileDockerLogCollector
/**
 * @author Ilya Sadykov
 */
@CompileStatic
interface LogCollector {
    String DEFAULT = ToFileDockerLogCollector.name

    void collect(String containerName, InputStream inputStream)

    InputStream get(String containerName)
}