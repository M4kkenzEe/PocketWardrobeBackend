package plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.forwardedheaders.*

fun Application.configureForwardedHeaders() {
    install(XForwardedHeaders) {
        // nginx appends the real client IP to the X-Forwarded-For the client sent, so only the last value
        // is trustworthy. Assumes nginx is the only proxy: behind a CDN (e.g. Cloudflare) revisit this.
        useLastProxy()
    }
}
