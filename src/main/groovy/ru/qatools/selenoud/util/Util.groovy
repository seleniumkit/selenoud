package ru.qatools.selenoud.util

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import ratpack.exec.Promise

import static java.lang.Class.forName

/**
 * @author Ilya Sadykov
 */
@CompileStatic
enum Util {
    public static final String PREFIX = prop('prefix', 'wd/hub/')

    static <T> T prop(String name, String defVal = null) {
        if (defVal == null && !System.getProperties().containsKey(name)) {
            throw new RuntimeException("Could not find property ${name}!")
        }
        System.getProperty(name, defVal) as T
    }

    static int intProp(String name, String defVal = 0) {
        prop(name, "${defVal}") as int
    }

    static Map fromJson(InputStream json) {
        new JsonSlurper().parse(json) as Map
    }

    static Map fromJson(String json) {
        new JsonSlurper().parse(json.toCharArray()) as Map
    }

    static String toJson(Object object) {
        JsonOutput.toJson(object)
    }

    static <T> Promise<T> promise(T value) {
        Promise.value(value)
    }

    static <T> T newInstanceOf(String propName, String defaultVal) {
        forName(prop(propName, defaultVal) as String).newInstance() as T
    }

    /**
     * Returns a free port number on localhost.
     * Heavily inspired from org.eclipse.jdt.launching.SocketUtil (to avoid a dependency to JDT just because of this).
     * Slightly improved with close() missing in JDT. And throws exception instead of returning -1.
     *
     * @return a free port number on localhost
     * @throws IllegalStateException if unable to find a free port
     */
    static synchronized int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
            } catch (IOException ignored) {
                // Ignore IOException on close()
            }
            return port;
        } catch (IOException ignored) {
            // Ignore IOException on open
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Ignore IOException on close()
                }
            }
        }
        throw new IllegalStateException("Could not find a free TCP/IP port to start embedded Jetty HTTP Server on");
    }
}