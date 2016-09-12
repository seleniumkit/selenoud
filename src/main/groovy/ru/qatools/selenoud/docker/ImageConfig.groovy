package ru.qatools.selenoud.docker

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * @author Ilya Sadykov
 */
@CompileStatic
class ImageConfig {
    Map<String, Image> images = [:]
    List<String> environment = []

    @ToString
    @CompileStatic
    static class Image {
        String image, path = "/wd/hub/"
        Long shmSize = 67108864L
    }
}
