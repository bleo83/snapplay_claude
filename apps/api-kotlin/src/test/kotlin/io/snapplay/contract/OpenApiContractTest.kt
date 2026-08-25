package io.snapplay.contract

import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.parser.core.models.ParseOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Contract tests for SNA-14.
 *
 * Two complementary assertions:
 * 1. Spec validity — the OpenAPI document served by springdoc is a valid, parseable
 *    OpenAPI 3.x spec with no errors, and documents every expected endpoint.
 * 2. Response shape — each endpoint's actual response contains the fields declared
 *    in the spec schema, catching drift between implementation and contract.
 *
 * The spec grows automatically as new controllers are added; tests for new endpoints
 * should be added here in the same PR that introduces those controllers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiContractTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private lateinit var spec: OpenAPI

    @BeforeAll
    fun loadAndParseSpec() {
        val specJson =
            mockMvc
                .perform(get("/api/openapi.json"))
                .andExpect(status().isOk())
                .andReturn()
                .response
                .contentAsString

        val result = OpenAPIParser().readContents(specJson, null, ParseOptions().apply { isResolve = true })
        assertThat(result.messages)
            .withFailMessage("OpenAPI spec has parse errors: %s", result.messages)
            .isEmpty()
        spec = result.openAPI
    }

    // ── Spec structure ────────────────────────────────────────────────────────

    @Test
    fun `spec is valid OpenAPI 3 document`() {
        assertThat(spec.openapi).startsWith("3.")
        assertThat(spec.info.title).isNotBlank()
        assertThat(spec.paths).isNotEmpty()
    }

    @Test
    fun `spec documents all expected paths`() {
        val paths = spec.paths.keys
        assertThat(paths).contains(
            "/v1/organization",
            "/v1/experiences",
            "/v1/catalog/products",
            "/health",
        )
    }

    @Test
    fun `all documented operations have at least one response defined`() {
        val operations: List<Pair<String, Operation>> =
            spec.paths.flatMap { (path, item) ->
                listOfNotNull(
                    item.get?.let { path to it },
                    item.post?.let { path to it },
                    item.patch?.let { path to it },
                    item.put?.let { path to it },
                    item.delete?.let { path to it },
                )
            }
        operations.forEach { (path, op) ->
            assertThat(op.responses)
                .withFailMessage("Operation $path has no responses defined")
                .isNotEmpty()
        }
    }

    // ── Organization ──────────────────────────────────────────────────────────

    @Test
    fun `GET organization is documented with response schema`() {
        val schema =
            spec.paths["/v1/organization"]?.get
                ?.responses?.get("200")?.content?.values?.firstOrNull()?.schema
        assertThat(schema).withFailMessage("GET /v1/organization 200 schema not documented").isNotNull()

        mockMvc
            .perform(get("/v1/organization"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.legalName").exists())
            .andExpect(jsonPath("$.displayName").exists())
            .andExpect(jsonPath("$.organizationType").exists())
            .andExpect(jsonPath("$.country").exists())
            .andExpect(jsonPath("$.defaultCurrency").exists())
            .andExpect(jsonPath("$.status").exists())
    }

    @Test
    fun `PATCH organization is documented with response schema`() {
        val schema =
            spec.paths["/v1/organization"]?.patch
                ?.responses?.get("200")?.content?.values?.firstOrNull()?.schema
        assertThat(schema).withFailMessage("PATCH /v1/organization 200 schema not documented").isNotNull()

        mockMvc
            .perform(
                patch("/v1/organization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "legalName": "Disney Streaming Services Argentina S.A.",
                          "displayName": "Disney Argentina",
                          "country": "AR",
                          "defaultCurrency": "ARS",
                          "timezone": "America/Argentina/Buenos_Aires"
                        }
                        """.trimIndent(),
                    ),
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.legalName").exists())
            .andExpect(jsonPath("$.status").exists())
    }

    // ── Experiences ───────────────────────────────────────────────────────────

    @Test
    fun `GET experiences is documented and returns paginated items`() {
        val schema =
            spec.paths["/v1/experiences"]?.get
                ?.responses?.get("200")?.content?.values?.firstOrNull()?.schema
        assertThat(schema).withFailMessage("GET /v1/experiences 200 schema not documented").isNotNull()

        mockMvc
            .perform(get("/v1/experiences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[0].id").exists())
            .andExpect(jsonPath("$.items[0].name").exists())
            .andExpect(jsonPath("$.items[0].status").exists())
            .andExpect(jsonPath("$.items[0].handoffMode").exists())
    }

    @Test
    fun `POST experience is documented and rejects invalid payload with Problem Details`() {
        // Validate the operation is in the spec and that validation errors conform to RFC 7807
        // without mutating shared demo state (avoid affecting ExperienceControllerTest counts)
        val operation = spec.paths["/v1/experiences"]?.post
        assertThat(operation).withFailMessage("POST /v1/experiences not documented").isNotNull()

        mockMvc
            .perform(
                post("/v1/experiences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"AB","contextTitle":"x","handoffMode":"STORE_DEEPLINK","productCount":0,"startsAt":"2026-09-01T00:00:00Z"}"""),
            )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.title").exists())
    }

    // ── Catalog ───────────────────────────────────────────────────────────────

    @Test
    fun `GET catalog products is documented and returns paginated items`() {
        val schema =
            spec.paths["/v1/catalog/products"]?.get
                ?.responses?.get("200")?.content?.values?.firstOrNull()?.schema
        assertThat(schema).withFailMessage("GET /v1/catalog/products 200 schema not documented").isNotNull()

        mockMvc
            .perform(get("/v1/catalog/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
    }

    // ── Health ────────────────────────────────────────────────────────────────

    @Test
    fun `GET health is documented and returns 200`() {
        val operation = spec.paths["/health"]?.get
        assertThat(operation).withFailMessage("GET /health not documented").isNotNull()

        mockMvc
            .perform(get("/health"))
            .andExpect(status().isOk())
    }

    // ── Error shape ───────────────────────────────────────────────────────────

    @Test
    fun `validation errors return RFC 7807 Problem Details shape`() {
        mockMvc
            .perform(
                patch("/v1/organization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"legalName":"X","displayName":"Disney","country":"argentina","defaultCurrency":"ARS","timezone":"UTC"}""",
                    ),
            )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.type").exists())
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.request_id").exists())
    }
}
