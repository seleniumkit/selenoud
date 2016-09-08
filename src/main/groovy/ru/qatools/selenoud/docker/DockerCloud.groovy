package ru.qatools.selenoud.docker

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerException
import com.spotify.docker.client.LogStream
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.ContainerCreation
import com.spotify.docker.client.messages.HostConfig
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import ru.qatools.selenoud.AbstractCloud
import ru.qatools.selenoud.Container

import static com.spotify.docker.client.DefaultDockerClient.DEFAULT_UNIX_ENDPOINT
import static com.spotify.docker.client.DockerClient.LogsParam.stderr
import static com.spotify.docker.client.DockerClient.LogsParam.stdout
import static com.spotify.docker.client.messages.ContainerConfig.builder
import static java.lang.Boolean.parseBoolean
import static java.lang.Integer.parseInt
import static org.codehaus.groovy.runtime.IOGroovyMethods.withCloseable
import static ru.qatools.selenoud.util.Util.*

/**
 * @author Ilya Sadykov
 */
@Singleton(strict = false)
@Slf4j('LOG')
@CompileStatic
class DockerCloud extends AbstractCloud {
    private final ThreadLocal<DockerClient> docker = new ThreadLocal<>()
    private final int CONTAINER_PORT = intProp('container.port', '4455')
    private final String ENDPOINT = prop('docker.endpoint', DEFAULT_UNIX_ENDPOINT)
    private final boolean ENABLE_PULL = parseBoolean(prop('docker.pull.enabled', 'false') as String)
    private final String NETWORK_MODE = prop('docker.network', 'bridge')
    private final String SELF_HOST = prop('host', '172.17.0.1'),
                         SELF_PORT = prop('port', '4444'),
                         CLOUD_HOST = prop('cloud.host', 'localhost')

    private DockerCloud() {
        Thread.start {
            LOG.info("Pulling all images within the images provider...")
            imagesProvider.names().each { docker().pull(it) }
            LOG.info("All images have been pulled")
        }
    }

    @Override
    Container launchContainer(String browserName, String browserVersion, String name) {
        final String image = imagesProvider.image(browserName, browserVersion).image
        if (!image) {
            throw new RuntimeException("Image for $browserName:$browserVersion not found in mapping!")
        }
        if (ENABLE_PULL) {
            docker().pull(image)
        }
        final isNetworkHost = NETWORK_MODE == 'host'
        final exposedPort = (isNetworkHost ? findFreePort() : CONTAINER_PORT) as int
        ContainerConfig config = builder().env(imagesProvider.env(SELF_HOST, SELF_PORT, name, exposedPort) as String[])
                .exposedPorts("$exposedPort/tcp")
                .hostConfig(HostConfig.builder()
                .publishAllPorts(true)
                .binds(['/dev/urandom:/dev/random'])
                .portBindings([:])
                .networkMode(NETWORK_MODE)
                .build())
                .image(image)
                .build()
        ContainerCreation creation = docker().createContainer(config, name)
        docker().startContainer(creation.id())
        final info = docker().inspectContainer(creation.id())
        final ports = info.networkSettings().ports()
        final hostPort = (isNetworkHost || !ports) ? exposedPort :
                parseInt(ports.get("${exposedPort}/tcp".toString()).get(0).hostPort())
        new Container(id: creation.id(), name: name, browser: browserName,
                version: browserVersion, port: hostPort, host: "${CLOUD_HOST}:${hostPort}" as String)
    }

    @Override
    void removeContainer(String containerName) {
        try {
            docker().stopContainer(containerName, 10)
            LOG.info("Collecting logs for container ${containerName}...")
            withCloseable(docker().logs(containerName, stderr(), stdout()) as LogStream) { LogStream ls ->
                logCollector.collect(containerName, new LogStreamInputStream(ls))
            }
            docker().removeContainer(containerName)
        } catch (DockerException e) {
            LOG.info("Failed to remove container ${containerName}: ${e.message}")
        }
    }

    private DockerClient docker() {
        if (docker.get()) {
            return docker.get()
        }
        docker.set(DefaultDockerClient.builder().uri(ENDPOINT).build())
        docker.get()
    }
}
