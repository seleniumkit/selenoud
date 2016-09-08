package ru.qatools.selenoud.util

import groovy.transform.CompileStatic
import ratpack.exec.Promise
import ratpack.func.Action
import ratpack.http.Request
import ratpack.http.Response
import ratpack.http.TypedData
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.http.client.RequestSpec

import static java.time.Duration.ofSeconds
import static ratpack.stream.Streams.publish
import static ru.qatools.selenoud.util.Util.fromJson
import static ru.qatools.selenoud.util.Util.intProp

/**
 * @author Ilya Sadykov
 */
@CompileStatic
enum RequestUtil {
    public static final int MAX_READ_TIMEOUT = intProp('limit.readTimeoutSec', '60')
    public static final int MAX_CONN_TIMEOUT = intProp('limit.connectTimeoutSec', '10')

    static void proxyToUrl(String fullUrl, Request request, HttpClient client,
                           Promise<TypedData> body, Action<ReceivedResponse> handler) {
        final url = new URL(fullUrl)
        body.then { TypedData readBody ->
            client.request(url.toURI(), { RequestSpec spec ->
                spec.headers.copy(request.headers)
                spec.headers.remove('Host')
                spec.headers.add('Host', url.host)
                spec.method(request.method.name)
                spec.readTimeout(ofSeconds(MAX_READ_TIMEOUT))
                spec.connectTimeout(ofSeconds(MAX_CONN_TIMEOUT))
                spec.body.buffer(readBody.buffer)

            } as Action).then(handler)
        }
    }

    static void proxyToUrl(String fullUrl, Request request, Response response, HttpClient client, Promise<TypedData> body,
                           Action<ReceivedResponse> handler = { ReceivedResponse stream -> stream.forwardTo(response) }) {
        proxyToUrl(fullUrl, request, client, body, handler)
    }

    static Map getCaps(TypedData body) {
        fromJson(body.text).desiredCapabilities as Map
    }

    static void inputStreamToResponse(InputStream is, Response response) {
        response.sendStream(publish(new InputStreamIterable(is)))
    }
}
