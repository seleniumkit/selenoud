package ru.qatools.selenoud

import groovy.transform.CompileStatic
import ru.qatools.selenoud.docker.ImageConfig
import ru.qatools.selenoud.docker.JsonDockerImageNames
/**
 * @author Ilya Sadykov
 */
@CompileStatic
interface ImageNameProvider {
    String DEFAULT = JsonDockerImageNames.name;

    ImageConfig.Image image(String browserName, String browserVersion)

    List<String> env(String hubHost, String hubPort, String name, int port)

    Collection<String> names()
}
