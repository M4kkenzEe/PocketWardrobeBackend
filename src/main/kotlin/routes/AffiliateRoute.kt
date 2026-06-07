package com.example.routes

import com.example.services.AffiliateService
import com.example.services.Marketplace
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.net.MalformedURLException
import java.net.URL

@Serializable
private data class AffiliateLinkRequest(
    @SerialName("product_url") val productUrl: String,
    val marketplace: String? = null
)

@Serializable
private data class AffiliateLinkResponse(
    @SerialName("affiliate_url") val affiliateUrl: String
)

@Serializable
private data class AffiliateError(val error: String)

fun Application.affiliate() {
    val affiliateService: AffiliateService by inject()

    routing {
        route("/api/v1") {
            rateLimit(RateLimitName("affiliate")) {
                authenticate {
                    post("/affiliate/link") {
                        val request = call.receive<AffiliateLinkRequest>()

                        val url = request.productUrl.trim()

                        if (!isValidUrl(url)) {
                            return@post call.respond(
                                HttpStatusCode.BadRequest,
                                AffiliateError(error = "invalid_url")
                            )
                        }

                        val marketplace = resolveMarketplace(url, request.marketplace)
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                AffiliateError(error = "unknown_marketplace")
                            )

                        val affiliateUrl = affiliateService.buildAffiliateUrl(url, marketplace)
                        call.respond(AffiliateLinkResponse(affiliateUrl = affiliateUrl))
                    }
                }
            }
        }
    }

    log.info("✓ Affiliate routes configured at /api/v1/affiliate/link")
}

private fun isValidUrl(url: String): Boolean = try {
    val parsed = URL(url)
    parsed.protocol in listOf("http", "https") && parsed.host.isNotBlank()
} catch (_: MalformedURLException) {
    false
}

private fun resolveMarketplace(url: String, marketplaceParam: String?): Marketplace? {
    if (!marketplaceParam.isNullOrBlank()) {
        return Marketplace.fromString(marketplaceParam)
    }
    return Marketplace.detectByUrl(url)
}
