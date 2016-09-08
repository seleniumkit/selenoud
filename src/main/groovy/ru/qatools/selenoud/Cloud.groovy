package ru.qatools.selenoud

import groovy.transform.CompileStatic
import ratpack.http.Request
import ratpack.http.Response
import ratpack.http.client.HttpClient

/**
 * @author Ilya Sadykov
 */
@CompileStatic
interface Cloud {

    def launch(Request origReq, Response response, HttpClient client)

    def onNodeRegistered(Request request, Response response)

    def onNodeUp(Request request, Response response)

    def proxy(String sessionId, Request request, Response response, HttpClient client)

    def proxy(String sessionId, Request request, Response response, HttpClient client, Closure call)

    def delete(String sessionId, Request request, Response response, HttpClient client)

    def status()

    def logs(String sessionId, Request request, Response response)
}