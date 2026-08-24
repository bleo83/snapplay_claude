package io.snapplay.catalog

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
    val source: String,
)

@RestController
@RequestMapping("/v1/catalog")
class CatalogController(
    private val principalResolver: PrincipalResolver,
    private val catalogRepository: CatalogRepository,
    private val props: SnapPlayProperties,
) {
    @GetMapping("/products")
    fun products(
        @RequestParam q: String?,
        @RequestParam category: String?,
        @RequestParam status: String?,
    ): ProductListResponse {
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
        val items = catalogRepository.findProducts(principal.organizationId, filters)
        return ProductListResponse(
            items = items,
            total = items.size,
            source = if (props.demo) "demo" else "supabase",
        )
    }
}
