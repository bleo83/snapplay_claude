package io.snapplay.catalog.infrastructure.client

import io.snapplay.catalog.application.port.output.RappiCatalogClient
import io.snapplay.catalog.domain.RappiCatalogPage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoRappiCatalogClient : RappiCatalogClient {
    private val pages = CopyOnWriteArrayList<RappiCatalogPage>()
    private var callCount = 0

    fun reset() {
        pages.clear()
        callCount = 0
    }

    fun enqueuePages(vararg newPages: RappiCatalogPage) {
        pages.addAll(newPages)
    }

    override fun fetchStores(
        connectionId: UUID,
        cursor: String?,
    ): RappiCatalogPage {
        val index = callCount++
        return pages.getOrElse(index) { RappiCatalogPage(emptyList(), null) }
    }
}
