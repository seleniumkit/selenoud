package ru.qatools.selenoud

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.netty.util.internal.ConcurrentSet
import ratpack.http.Request
import ratpack.http.Response
import ratpack.http.TypedData
import ratpack.http.client.HttpClient
import ru.qatools.selenoud.util.TimedCountDownLatch

import java.util.concurrent.ConcurrentHashMap

import static java.lang.System.currentTimeMillis
import static java.util.Optional.ofNullable
import static java.util.concurrent.TimeUnit.SECONDS
import static ru.qatools.selenoud.util.RequestUtil.*
import static ru.qatools.selenoud.util.Util.*

/**
 * @author Ilya Sadykov
 */
@Slf4j('LOG')
@CompileStatic
abstract class AbstractCloud implements Cloud {
    protected final int MAX_STARTUP_SEC = intProp('limit.startupSec', '60'),
                        MAX_CONTAINERS = intProp('limit.count', '0'),
                        MAX_INACTIVITY_SEC = intProp('limit.inactivitySec', '120'),
                        WATCHER_INTERVAL_MS = intProp('limit.watcherIntervalMs', '5000')
    protected final Map<String, Container> launchedBySessionId = new ConcurrentHashMap<>()
    protected final Map<String, Container> launched = new ConcurrentHashMap<>()
    protected final Set<String> deleting = new ConcurrentSet<>()
    protected final Map<String, TimedCountDownLatch> launching = new ConcurrentHashMap<>()
    protected final LogCollector logCollector = newInstanceOf('logCollector', LogCollector.DEFAULT)
    protected final ImageNameProvider imagesProvider = newInstanceOf('imagesProvider', ImageNameProvider.DEFAULT)

    AbstractCloud() {
        runWatcher()
    }

    @Override
    def launch(Request request, Response response, HttpClient client) {
        final containerName = UUID.randomUUID().toString()
        synchronized (this) {
            LOG.debug("[SESSION_ATTEMPT_STARTED] [$containerName] [${launched.size()}] [${launching.size()}] [$MAX_CONTAINERS]")
            if (MAX_CONTAINERS > 0 && launched.size() + launching.size() >= MAX_CONTAINERS) {
                response.status(503)
                response.send('Too many containers')
                LOG.debug("[SESSION_ATTEMPT_FAILED] [$containerName] [${launched.size()}] [${launching.size()}] [$MAX_CONTAINERS]: Too many containers")
                return
            }
            launching.put(containerName, new TimedCountDownLatch(1))
        }
        LOG.debug("[SESSION_ATTEMPT_SUCCESS] [$containerName]")
        request.body.then { TypedData body ->
            final caps = getCaps(body)
            final browser = caps.browserName as String
            final version = (caps.version ?: null) as String

            request.add(BrowserContext, new BrowserContext(name: containerName, browser: browser, version: version))

            LOG.info('[{}:{}] [SESSION_ATTEMPTED] [{}]', browser, version, containerName)

            try {
                final container = launchContainer(browser, version, containerName)
                LOG.info('[{}:{}] [CONTAINER_CREATED] [{}] [{}]', browser, version, containerName, container.id)
                createSession(request, response, client, body, waitForNode(container).orElseThrow({
                    new RuntimeException("Failed to launch container and create session for ${containerName}!")
                }))
            } catch (Exception e) {
                LOG.error('[{}:{}] [CONTAINER_ERROR] [{}] : {}', browser, version, containerName, e.message, e)
                safeRemoveContainer(containerName)
                response.status(500)
                response.send('Failed to launch container: {}', e.message)
            }
        }
    }

    @Override
    def onNodeRegistered(Request request, Response response) {
        LOG.trace('[NODE_REGISTER] [{}]', request.rawUri)
        request.body.then {
            def registerMsg = fromJson(it.inputStream)
            def containerName = (registerMsg.configuration as Map)?.url as String
            LOG.debug("[NODE_REGISTERED] [$containerName]")
            if (containerName) {
                launching[containerName]?.countDown()
            }
            response.send(toJson([:]))
        }
    }

    @Override
    def onNodeUp(Request request, Response response) {
        request.body.then {
            def containerName = request.queryParams.get('id')
            LOG.debug("[NODE_UP] [$containerName]")
            if (!launching.containsKey(containerName) && !launched.containsKey(containerName)) {
                LOG.warn("Found lost container: ${containerName}, removing it!")
                safeRemoveContainer(containerName)
            }
        }
    }

    @Override
    def proxy(String sessionId, Request request, Response response, HttpClient client, Closure call = {}) {
        LOG.trace('[PROXY] [{}] [{}]', sessionId, request.rawUri)
        try {
            final container = launchedBySessionId[sessionId]?.updated()
            if (!container) {
                throw new RuntimeException("Session not found ${sessionId}!")
            }
            request.add(BrowserContext, container)
            final origSid = container.originalSessionId
            final uri = request.rawUri?.replaceAll(sessionId, origSid)
            proxyToUrl(container.url(uri), request, response, client, request.body) {
                response.beforeSend { call.call() }
                it.forwardTo(response)
            }
        } catch (Exception e) {
            LOG.error('[PROXY_ERROR] [{}]', sessionId, e)
            response.status(500)
            response.send("Failed to proxy request for session $sessionId: $e.message")
        }
    }

    @Override
    def delete(String sessionId, Request request, Response response, HttpClient client) {
        proxy(sessionId, request, response, client) {
            def container = launchedBySessionId.remove(sessionId)
            LOG.info('[{}:{}] [SESSION_DELETED] [{}] [{}]', container?.browser, container?.version, container?.name, sessionId)
            safeRemoveContainer(container)
            LOG.info('[{}:{}] [NODE_DELETED] [{}] [{}]', container.browser, container.version,
                    container.name, sessionId)
        }
    }

    @Override
    def status() {
        [
                launching: launching.keySet().size(),
                launched : launched.size(),
                max      : MAX_CONTAINERS
        ]
    }

    @Override
    def logs(String sessionId, Request request, Response response) {
        response.contentType("text/plain; charset=utf-8")
        inputStreamToResponse(logCollector.get(sessionId.split(':')[0]), response)
    }

    protected abstract Container launchContainer(String browserName, String browserVersion, String name)

    protected abstract void removeContainer(String containerName)

    protected Optional<Container> waitForNode(Container container) {
        watchSocketOpen(container)
        if (!launching[container.name]?.await(MAX_STARTUP_SEC, SECONDS)) {
            LOG.warn('[{}:{}] [NODE_TIMEOUT] [{}]', container.browser, container.version, container.name)
            safeRemoveContainer(container)
            ofNullable(null as Container)
        } else {
            LOG.info('[{}:{}] [NODE_STARTED] [{}]', container.browser, container.version, container.name)
            container.with { path = imagesProvider.image(browser, version).path }
            ofNullable(container)
        }
    }

    protected Thread watchSocketOpen(Container container) {
        Thread.start {
            final long started = currentTimeMillis()
            boolean success
            try {
                while ((MAX_STARTUP_SEC * 1000 as long) > currentTimeMillis() - started &&
                        !(success = isHttpListen(container.host, container.port))) {
                    sleep 5
                }
                if (success) {
                    launching[container.name]?.countDown()
                }
            } catch (Throwable e) {
                LOG.error('[{}:{}] Failed to wait for socket to open [{}]', container.browser, container.version, container.name, e)
            }
        }
    }

    protected void createSession(Request request, Response response, HttpClient client, TypedData body, Container container) {
        try {
            final url = container.url(request.rawUri)
            LOG.trace('[{}:{}] [CREATING_SESSION] [{}] [{}]', container.browser, container.version, container.name, url)
            proxyToUrl(url, request, response, client, promise(body)) {
                final text = it.body.getText()
                LOG.trace('[{}:{}] [CREATE_RESPONSE] [{}] [{}]', container.browser, container.version, container.name, text)
                final hubResponse = fromJson(text)
                if (it.statusCode == 200) {
                    def sessionId = "${container.name}:${hubResponse.sessionId}" as String
                    launching.remove(container.name)
                    launched[container.name] = launchedBySessionId[sessionId] = container.with {
                        it.sessionId = sessionId
                        it.originalSessionId = hubResponse.sessionId as String
                        it
                    }
                    hubResponse.sessionId = sessionId
                    LOG.info('[{}:{}] [SESSION_CREATED] [{}] [{}]', container.browser, container.version,
                            container.name, sessionId)
                }
                response.send('application/json', toJson(hubResponse))
            }
        } catch (Exception e) {
            LOG.warn('[{}:{}] [SESSION_FAILED] [{}] [{}]', container.browser, container.version, container.name, e.message, e)
            safeRemoveContainer(container)
            response.status(500)
            response.send('Failed to create session: {}', e.message)
        }
    }

    protected void safeRemoveContainer(Container container) {
        safeRemoveContainer(container?.name, container?.sessionId, container?.port)
    }

    protected void safeRemoveContainer(String name, String sessionId = '', int port = 0) {
        try {
            if (name && !deleting.contains(name)) {
                deleting.add(name)
                removeContainer(name)
                launching.remove(name)
                launched.remove(name)
                launchedBySessionId.remove(sessionId ?: '')
            }
        } catch (Exception e) {
            LOG.error('Failed to remove container [{}]', name, e)
        } finally {
            deleting.remove(name ?: '')
        }
    }

    protected void runWatcher() {
        new Timer().schedule({
            try {
                launched.findAll({ name, container ->
                    currentTimeMillis() - container.lastUpdate > MAX_INACTIVITY_SEC * 1000
                }).each { name, container ->
                    LOG.info('[{}:{}] [NODE_EXPIRED] [{}] [{}]', container.browser,
                            container.version, container.name, container.sessionId)
                    safeRemoveContainer(container)
                }
                launching.findAll { name, cd ->
                    currentTimeMillis() - cd.createdTime > MAX_INACTIVITY_SEC * 1000
                }.each { name, cd ->
                    LOG.info('[LAUNCH_EXPIRED] [{}]', name)
                    safeRemoveContainer(name)
                }
            } catch (Exception e) {
                LOG.error("Failed to perform scheduled cleanups...", e)
            }
        } as TimerTask, 0L, WATCHER_INTERVAL_MS)
    }
}