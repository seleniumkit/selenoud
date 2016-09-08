package ru.qatools.selenoud

import groovy.transform.Canonical
import groovy.transform.CompileStatic

import static java.lang.System.currentTimeMillis
import static ru.qatools.selenoud.util.Util.PREFIX

/**
 * @author Ilya Sadykov
 */
@Canonical
@CompileStatic
class Container extends BrowserContext {
    String id, host, sessionId, originalSessionId, path
    int port
    long lastUpdate = currentTimeMillis()

    Container updated() {
        lastUpdate = currentTimeMillis(); this
    }

    String url(String rawUri){
        "http://${host}${rawUri?.replaceFirst("/${PREFIX}", path)}"
    }
}
