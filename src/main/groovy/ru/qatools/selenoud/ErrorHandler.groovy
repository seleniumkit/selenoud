package ru.qatools.selenoud

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import ratpack.error.ServerErrorHandler
import ratpack.handling.Context

/**
 * @author Ilya Sadykov
 */
@Slf4j('LOG')
@Singleton
@CompileStatic
class ErrorHandler implements ServerErrorHandler {

    @Override
    void error(Context ctx, Throwable e) {
        ctx.request.maybeGet(BrowserContext).ifPresent {
            LOG.error('[{}:{}] [SESSION_ERROR] [{}]: {}', it?.browser, it?.version, it?.name, "${e.class.name} " +
                    "(${ctx.request.rawUri} : ${e.message})", e)
        }
        ctx.with {
            response.status(500)
            response.send("${e.class.name} : ${e.message}")
        }
    }
}
