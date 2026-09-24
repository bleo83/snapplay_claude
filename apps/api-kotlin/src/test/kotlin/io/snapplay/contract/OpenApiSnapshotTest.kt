package io.snapplay.contract

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.parser.core.models.ParseOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.File

/**
 * Snapshot diff guardrail for the public API contract (SNA-14).
 *
 * ## What this test does
 *
 * [OpenApiContractTest] asserts things about the spec that springdoc generates *right now*. Because
 * that spec is derived from the controllers and DTOs, it moves whenever the code moves: rename a DTO
 * field and the spec renames with it, so code and spec agree by construction and no drift is ever
 * detectable.
 *
 * This test compares the generated spec against a **committed snapshot** of the previously released
 * contract (`src/test/resources/openapi/openapi-snapshot.json`) and fails on the changes that
 * `docs/adr-api-compatibility.md` classifies as breaking:
 *
 * 1. a path present in the snapshot but missing from the generated spec (*removing an endpoint*);
 * 2. an operation (GET/POST/PATCH/PUT/DELETE) removed from a path that still exists;
 * 3. a property removed or renamed in a response schema (*removing / renaming a field*);
 * 4. an enum value removed (*narrowing an enum*), in responses, request bodies or parameters;
 * 5. a request field or parameter that was optional in the snapshot and is now required, or a brand
 *    new required request field.
 *
 * Additive changes — new paths, new operations, new optional fields, new enum values — are explicitly
 * non-breaking per the ADR and **must** keep this test green.
 *
 * ## Updating the snapshot
 *
 * The snapshot is never rewritten automatically. When a change to the contract is intended, regenerate
 * it deliberately so that the new contract shows up as a reviewable diff in the pull request:
 *
 * ```
 * ./gradlew test --tests '*OpenApiSnapshotTest' -Dopenapi.snapshot.update=true
 * git add src/test/resources/openapi/openapi-snapshot.json
 * ```
 *
 * The file is written pretty-printed with recursively sorted object keys, so regenerating it produces
 * a minimal, readable diff.
 *
 * ## When this test fails
 *
 * A failure means the change is breaking for Rappi and the backoffice. Do not regenerate the snapshot
 * to silence it — follow the deprecation process in `docs/adr-api-compatibility.md` (mark deprecated,
 * ship `Deprecation`/`Sunset` headers, wait out the notice period), and only then remove the field or
 * endpoint and regenerate the snapshot in the removal PR.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiSnapshotTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private lateinit var live: OpenAPI
    private lateinit var snapshot: OpenAPI
    private var snapshotText: String? = null

    @BeforeAll
    fun loadSpecs() {
        val liveJson =
            mockMvc
                .perform(get("/api/openapi.json"))
                .andExpect(status().isOk())
                .andReturn()
                .response
                .contentAsString
        live = parse(liveJson, "generated spec")

        if (System.getProperty(UPDATE_PROPERTY) == "true") {
            val target = resolveSnapshotFile()
            target.parentFile.mkdirs()
            target.writeText(canonicalize(liveJson))
            println("[OpenApiSnapshotTest] snapshot rewritten: ${target.absolutePath} — review and commit the diff.")
            snapshot = live
            snapshotText = null
            return
        }

        val text =
            javaClass.classLoader.getResourceAsStream(SNAPSHOT_RESOURCE)?.bufferedReader()?.readText()
                ?: error(
                    "Missing OpenAPI snapshot resource '$SNAPSHOT_RESOURCE'. " +
                        "Generate it with: ./gradlew test --tests '*OpenApiSnapshotTest' -D$UPDATE_PROPERTY=true",
                )
        snapshotText = text
        snapshot = parse(text, "committed snapshot")
    }

    // ── Snapshot hygiene ──────────────────────────────────────────────────────

    @Test
    fun `snapshot file is stored in canonical, stable key order`() {
        val text = snapshotText ?: return
        assertThat(text)
            .withFailMessage(
                "The committed snapshot is not in canonical form (pretty-printed, object keys sorted recursively). " +
                    "Hand-editing it makes future diffs unreviewable. Regenerate it with: " +
                    "./gradlew test --tests '*OpenApiSnapshotTest' -D$UPDATE_PROPERTY=true",
            ).isEqualTo(canonicalize(text))
    }

    // ── Rule 1: removing an endpoint ──────────────────────────────────────────

    @Test
    fun `no path documented in the snapshot has been removed`() {
        val removed = (snapshot.paths.orEmpty().keys - live.paths.orEmpty().keys).sorted()
        assertThat(removed)
            .withFailMessage(
                "%s\n\nThe following path(s) are documented in the committed snapshot but absent from the generated spec:\n%s",
                violationHeader("Removing an endpoint"),
                removed.joinToString("\n") { "  - $it" },
            ).isEmpty()
    }

    // ── Rule 2: removing an operation from a surviving path ───────────────────

    @Test
    fun `no operation documented in the snapshot has been removed from an existing path`() {
        val liveOps = operationsOf(live).keys
        val removed =
            operationsOf(snapshot)
                .keys
                .filter { it.path in live.paths.orEmpty().keys && it !in liveOps }
                .sortedBy { it.describe() }

        assertThat(removed)
            .withFailMessage(
                "%s\n\nThe following operation(s) were removed from a path that still exists:\n%s",
                violationHeader("Removing an endpoint"),
                removed.joinToString("\n") { "  - ${it.describe()}" },
            ).isEmpty()
    }

    // ── Rule 3: removing or renaming a response field ─────────────────────────

    @Test
    fun `no response property documented in the snapshot has been removed or renamed`() {
        val liveFields = responseFieldsOf(live)
        val liveOps = operationsOf(live).keys
        val violations = mutableListOf<String>()

        responseFieldsOf(snapshot).forEach { (slot, fields) ->
            // Whole-operation removals are reported by the endpoint rules; do not duplicate them here.
            if (slot.operation !in liveOps) return@forEach
            val current = liveFields[slot]
            if (current == null) {
                violations += "${slot.describe()} is no longer documented at all"
                return@forEach
            }
            (fields.keys - current.keys).sorted().forEach { field ->
                violations += "${slot.describe()}: property `$field` was removed or renamed"
            }
        }

        assertThat(violations.sorted())
            .withFailMessage(
                "%s\n\nA response property that consumers rely on disappeared from the generated spec. " +
                    "A rename shows up here as one removal plus a silently added field:\n%s",
                violationHeader("Removing a field from a response / Renaming a field"),
                violations.sorted().joinToString("\n") { "  - $it" },
            ).isEmpty()
    }

    // ── Rule 4: narrowing an enum ─────────────────────────────────────────────

    @Test
    fun `no enum value documented in the snapshot has been removed`() {
        val liveOps = operationsOf(live).keys
        val liveFields = responseFieldsOf(live) + requestFieldsOf(live) + parameterFieldsOf(live)
        val snapshotFields = responseFieldsOf(snapshot) + requestFieldsOf(snapshot) + parameterFieldsOf(snapshot)
        val violations = mutableListOf<String>()

        snapshotFields.forEach { (slot, fields) ->
            if (slot.operation !in liveOps) return@forEach
            val current = liveFields[slot] ?: return@forEach
            fields.forEach { (field, info) ->
                val before = info.enumValues ?: return@forEach
                val after = current[field]?.enumValues ?: return@forEach
                val lost = (before - after).sorted()
                if (lost.isNotEmpty()) {
                    violations += "${slot.describe()}: enum `$field` no longer accepts ${lost.joinToString(", ")}"
                }
            }
        }

        assertThat(violations.sorted())
            .withFailMessage(
                "%s\n\nRemoving an enum value breaks consumers that still send or switch on it:\n%s",
                violationHeader("Narrowing an enum (removing a value)"),
                violations.sorted().joinToString("\n") { "  - $it" },
            ).isEmpty()
    }

    // ── Rule 5: making an optional request field required ─────────────────────

    @Test
    fun `no optional request field documented in the snapshot has become required`() {
        val liveFields = requestFieldsOf(live) + parameterFieldsOf(live)
        val snapshotFields = requestFieldsOf(snapshot) + parameterFieldsOf(snapshot)
        val violations = mutableListOf<String>()

        liveFields.forEach { (slot, fields) ->
            // Slots absent from the snapshot belong to brand new operations, which are additive.
            val before = snapshotFields[slot] ?: return@forEach
            fields.forEach { (field, info) ->
                if (!info.required) return@forEach
                val previous = before[field]
                when {
                    previous == null && before.isNotEmpty() ->
                        violations += "${slot.describe()}: new field `$field` is required — existing clients never send it"
                    previous != null && !previous.required ->
                        violations += "${slot.describe()}: field `$field` was optional and is now required"
                }
            }
        }

        assertThat(violations.sorted())
            .withFailMessage(
                "%s\n\nRequests that were valid against the committed contract would now be rejected:\n%s",
                violationHeader("Making an optional request field required"),
                violations.sorted().joinToString("\n") { "  - $it" },
            ).isEmpty()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parse(
        json: String,
        label: String,
    ): OpenAPI {
        val result = OpenAPIParser().readContents(json, null, ParseOptions())
        return result.openAPI ?: error("Could not parse the $label as OpenAPI: ${result.messages}")
    }

    private fun resolveSnapshotFile(): File {
        val file = File(SNAPSHOT_PATH).absoluteFile
        check(File("build.gradle.kts").isFile) {
            "Expected the test working directory to be the api-kotlin module root, but it is ${File(".").absolutePath}. " +
                "Cannot safely write $SNAPSHOT_PATH."
        }
        return file
    }
}

// ── Contract surface extraction ───────────────────────────────────────────────

private const val SNAPSHOT_RESOURCE = "openapi/openapi-snapshot.json"
private const val SNAPSHOT_PATH = "src/test/resources/openapi/openapi-snapshot.json"
private const val UPDATE_PROPERTY = "openapi.snapshot.update"
private const val ADR = "docs/adr-api-compatibility.md"
private const val MAX_SCHEMA_DEPTH = 12

private fun violationHeader(rule: String): String =
    "BREAKING API CHANGE — ADR rule \"$rule\" ($ADR).\n" +
        "If this change is intended, follow the deprecation process in the ADR and regenerate the snapshot in the removal PR " +
        "with: ./gradlew test --tests '*OpenApiSnapshotTest' -D$UPDATE_PROPERTY=true"

/** A single HTTP operation: `GET /v1/experiences`. */
private data class OperationKey(val path: String, val method: String) {
    fun describe(): String = "$method $path"
}

/**
 * A schema-bearing slot of an operation: one response status, the request body, or the parameter list.
 * Media type is deliberately not part of the identity — springdoc reports a wildcard media type for
 * controllers that do not declare `produces`, and narrowing that to `application/json` is not a contract
 * change. Fields from every media type of a slot are merged instead.
 */
private data class Slot(val operation: OperationKey, val kind: String) {
    fun describe(): String = "${operation.describe()} → $kind"
}

private data class FieldInfo(val required: Boolean, val enumValues: Set<String>?)

private fun operationsOf(spec: OpenAPI): Map<OperationKey, Operation> =
    spec.paths
        .orEmpty()
        .flatMap { (path, item) ->
            listOfNotNull(
                item.get?.let { OperationKey(path, "GET") to it },
                item.post?.let { OperationKey(path, "POST") to it },
                item.patch?.let { OperationKey(path, "PATCH") to it },
                item.put?.let { OperationKey(path, "PUT") to it },
                item.delete?.let { OperationKey(path, "DELETE") to it },
            )
        }.toMap()

private fun responseFieldsOf(spec: OpenAPI): Map<Slot, Map<String, FieldInfo>> =
    buildMap {
        operationsOf(spec).forEach { (key, operation) ->
            operation.responses.orEmpty().forEach { (code, response) ->
                val content = response.content.orEmpty()
                if (content.isEmpty()) return@forEach
                put(Slot(key, "response $code"), mergeFields(content.values.map { flatten(it.schema, spec) }))
            }
        }
    }

private fun requestFieldsOf(spec: OpenAPI): Map<Slot, Map<String, FieldInfo>> =
    buildMap {
        operationsOf(spec).forEach { (key, operation) ->
            val content = operation.requestBody?.content.orEmpty()
            if (content.isEmpty()) return@forEach
            put(Slot(key, "request body"), mergeFields(content.values.map { flatten(it.schema, spec) }))
        }
    }

private fun parameterFieldsOf(spec: OpenAPI): Map<Slot, Map<String, FieldInfo>> =
    buildMap {
        operationsOf(spec).forEach { (key, operation) ->
            val parameters: List<Parameter> = operation.parameters.orEmpty()
            if (parameters.isEmpty()) return@forEach
            put(
                Slot(key, "parameter"),
                parameters.associate { parameter ->
                    val schema = resolveSchema(parameter.schema, spec)
                    parameter.name to FieldInfo(parameter.required == true, enumValuesOf(schema))
                },
            )
        }
    }

private fun flatten(
    schema: Schema<*>?,
    spec: OpenAPI,
): Map<String, FieldInfo> = buildMap { flattenInto(schema, spec, "", emptySet(), this, 0) }

/** Unions the field maps of every media type declared for one slot. */
private fun mergeFields(perMediaType: List<Map<String, FieldInfo>>): Map<String, FieldInfo> =
    buildMap {
        perMediaType.forEach { fields ->
            fields.forEach { (path, info) -> put(path, get(path)?.let { merge(it, info) } ?: info) }
        }
    }

/**
 * Walks a (possibly `$ref`-ed) schema and records every reachable property as a dotted path —
 * `items[].id`, `destination.providerStoreId` — together with whether it is required and the enum
 * values it accepts. `$ref` cycles are broken via [seen]; [depth] bounds pathological nesting.
 */
private fun flattenInto(
    raw: Schema<*>?,
    spec: OpenAPI,
    prefix: String,
    seen: Set<String>,
    out: MutableMap<String, FieldInfo>,
    depth: Int,
) {
    if (raw == null || depth > MAX_SCHEMA_DEPTH) return
    val refName = refNameOf(raw)
    if (refName != null && refName in seen) return
    val schema = resolveSchema(raw, spec) ?: return
    val nextSeen = if (refName == null) seen else seen + refName

    schema.items?.let { flattenInto(it, spec, "$prefix[]", nextSeen, out, depth + 1) }
    (schema.allOf.orEmpty() + schema.oneOf.orEmpty() + schema.anyOf.orEmpty()).forEach {
        flattenInto(it, spec, prefix, nextSeen, out, depth + 1)
    }

    val required = schema.required.orEmpty().toSet()
    schema.properties.orEmpty().forEach { (name, property) ->
        val path = if (prefix.isEmpty()) name else "$prefix.$name"
        val info = FieldInfo(required = name in required, enumValues = enumValuesOf(resolveSchema(property, spec)))
        out[path] = out[path]?.let { existing -> merge(existing, info) } ?: info
        flattenInto(property, spec, path, nextSeen, out, depth + 1)
    }
}

/** Merges the same property reached through several composed schemas (`allOf`/`oneOf`/`anyOf`). */
private fun merge(
    a: FieldInfo,
    b: FieldInfo,
): FieldInfo =
    FieldInfo(
        required = a.required || b.required,
        enumValues = if (a.enumValues == null || b.enumValues == null) null else a.enumValues + b.enumValues,
    )

private fun enumValuesOf(schema: Schema<*>?): Set<String>? = schema?.enum?.mapNotNull { it?.toString() }?.toSet()?.ifEmpty { null }

private fun refNameOf(schema: Schema<*>?): String? = schema?.`$ref`?.substringAfterLast('/')

private fun resolveSchema(
    schema: Schema<*>?,
    spec: OpenAPI,
): Schema<*>? {
    var current = schema ?: return null
    var hops = 0
    while (current.`$ref` != null) {
        if (hops++ > MAX_SCHEMA_DEPTH) return null
        current = spec.components?.schemas?.get(current.`$ref`.substringAfterLast('/')) ?: return null
    }
    return current
}

// ── Canonical JSON ────────────────────────────────────────────────────────────

/**
 * Renders the spec pretty-printed with object keys sorted recursively, so that two runs of springdoc
 * produce byte-identical snapshots and a genuine contract change produces a small, readable diff.
 * Array order is preserved — it is meaningful in OpenAPI (`enum`, `required`, `parameters`).
 */
private fun canonicalize(json: String): String {
    val mapper = ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    val tree = mapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
    val printer =
        DefaultPrettyPrinter()
            .withObjectIndenter(DefaultIndenter("  ", "\n"))
            .withArrayIndenter(DefaultIndenter("  ", "\n"))
    return mapper.writer(printer).writeValueAsString(tree) + "\n"
}
