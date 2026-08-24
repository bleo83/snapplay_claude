package io.snapplay.catalog.infrastructure.web

import io.snapplay.catalog.application.port.input.ListProductsUseCase
import io.snapplay.catalog.application.port.output.ProductFilters
import io.snapplay.catalog.domain.CatalogProduct
import io.snapplay.catalog.domain.ProductStatus
import io.snapplay.common.ValidationException
import io.snapplay.config.SnapPlayProperties
import io.snapplay.identity.PrincipalResolver
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class ProductListResponse(
    val items: List<CatalogProduct>,
    val total: Int,
    val nextCursor: String?,
    val source: String,
)

@RestController
@RequestMapping("/v1/catalog")
class CatalogController(
    private val principalResolver: PrincipalResolver,
    private val listProductsUseCase: ListProductsUseCase,
    private val props: SnapPlayProperties,
) {
    @GetMapping("/products")
    fun products(
        @RequestParam q: String?,
        @RequestParam category: String?,
        @RequestParam status: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam cursor: String? = null,
    ): ProductListResponse {
        val safeLimit = limit.coerceIn(1, 200)
        val principal = principalResolver.resolve()
        val filters =
            ProductFilters(
                q = q?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() },
                status =
                    status?.let {
                        runCatching { ProductStatus.valueOf(it) }.getOrElse {
                            throw ValidationException("Invalid status '$status'. Must be ACTIVE or INACTIVE")
                        }
                    },
            )
        val result = listProductsUseCase.list(principal, filters, safeLimit, cursor)
        return ProductListResponse(
            items = result.items,
            total = result.items.size,
            nextCursor = result.nextCursor,
            source = if (props.demo) "demo" else "supabase",
        )
    }
}
