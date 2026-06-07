package com.example.services

import org.slf4j.LoggerFactory
import java.time.Instant

enum class Marketplace {
    WILDBERRIES,
    OZON,
    LAMODA;

    companion object {
        fun detectByUrl(url: String): Marketplace? = when {
            "wildberries.ru" in url -> WILDBERRIES
            "ozon.ru" in url -> OZON
            "lamoda.ru" in url -> LAMODA
            else -> null
        }

        fun fromString(value: String): Marketplace? = entries.find {
            it.name.equals(value, ignoreCase = true)
        }
    }
}

class AffiliateService(
    private val admitadWbId: String?,
    private val admitadOzonId: String?,
    private val admitadLamodaId: String?
) {
    private val log = LoggerFactory.getLogger(AffiliateService::class.java)

    fun buildAffiliateUrl(productUrl: String, marketplace: Marketplace): String {
        val campaignId = when (marketplace) {
            Marketplace.WILDBERRIES -> admitadWbId
            Marketplace.OZON -> admitadOzonId
            Marketplace.LAMODA -> admitadLamodaId
        }

        if (campaignId == null) {
            log.info("affiliate_skip marketplace=${marketplace.name} ts=${Instant.now()}")
            return productUrl
        }

        log.info("affiliate_redirect marketplace=${marketplace.name} ts=${Instant.now()}")
        val encoded = java.net.URLEncoder.encode(productUrl, "UTF-8")
        return "https://ad.admitad.com/g/$campaignId/?ulp=$encoded"
    }
}
