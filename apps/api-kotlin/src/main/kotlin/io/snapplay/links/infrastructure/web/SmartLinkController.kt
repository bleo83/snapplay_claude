package io.snapplay.links.infrastructure.web

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import io.snapplay.common.NotFoundException
import io.snapplay.identity.PrincipalResolver
import io.snapplay.links.application.port.input.CreateSmartLinkCommand
import io.snapplay.links.application.port.input.CreateSmartLinkUseCase
import io.snapplay.links.application.port.input.ListSmartLinksUseCase
import io.snapplay.links.application.port.output.SmartLinkRepository
import io.snapplay.links.domain.SmartLink
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayOutputStream
import java.util.UUID

data class CreateSmartLinkRequest(
    val experienceId: UUID,
    @field:NotBlank
    @field:Size(min = 3, max = 120)
    @field:Pattern(regexp = "^[a-z0-9][a-z0-9.\\-]*[a-z0-9]$", message = "Must be lowercase alphanumeric with dots or hyphens")
    val placementKey: String,
)

data class SmartLinkListResponse(
    val items: List<SmartLink>,
    val nextCursor: String?,
)

@RestController
@RequestMapping("/v1/smart-links")
class SmartLinkController(
    private val principalResolver: PrincipalResolver,
    private val listSmartLinksUseCase: ListSmartLinksUseCase,
    private val createSmartLinkUseCase: CreateSmartLinkUseCase,
    private val smartLinkRepository: SmartLinkRepository,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam cursor: String? = null,
    ): SmartLinkListResponse {
        val safeLimit = limit.coerceIn(1, 200)
        val principal = principalResolver.resolve()
        val result = listSmartLinksUseCase.list(principal, safeLimit, cursor)
        return SmartLinkListResponse(items = result.items, nextCursor = result.nextCursor)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateSmartLinkRequest,
    ): SmartLink {
        val principal = principalResolver.resolve()
        return createSmartLinkUseCase.create(
            CreateSmartLinkCommand(
                experienceId = request.experienceId,
                placementKey = request.placementKey,
            ),
            principal,
        )
    }

    @GetMapping("/{shortCode}/qr", produces = [MediaType.IMAGE_PNG_VALUE])
    fun qr(
        @PathVariable shortCode: String,
    ): ResponseEntity<ByteArray> {
        val principal = principalResolver.resolve()
        val link =
            smartLinkRepository.findByShortCode(principal.organizationId, shortCode)
                ?: throw NotFoundException("Smart link '$shortCode' not found")

        val hints = mapOf(EncodeHintType.MARGIN to 2)
        val matrix = QRCodeWriter().encode(link.url, BarcodeFormat.QR_CODE, 400, 400, hints)

        val out = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(matrix, "PNG", out)

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header("Content-Disposition", "attachment; filename=\"$shortCode.png\"")
            .body(out.toByteArray())
    }
}
