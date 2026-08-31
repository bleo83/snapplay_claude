package io.snapplay.rappi.mock

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Minimal Rappi store landing page for mock/E2E testing.
 *
 * Mimics the URL format produced by RappiDeepLinkAdapter so that a full
 * handoff flow (QR scan → redirect → landing → order event) works locally.
 *
 * Not for production — active only when snapplay.rappi-mock-enabled=true.
 */
@RestController
@RequestMapping("/mock/rappi")
@ConditionalOnProperty(name = ["snapplay.rappi-mock-enabled"], havingValue = "true")
class RappiMockLandingController(
    private val catalog: RappiMockCatalog,
) {
    @GetMapping("/tiendas/{storeId}", produces = [MediaType.TEXT_HTML_VALUE])
    fun landing(
        @PathVariable storeId: String,
        @RequestParam(required = false) categoriaId: String?,
        @RequestParam(name = "snp_tk", required = false) trackingToken: String?,
    ): ResponseEntity<String> {
        val store = catalog.findStore(storeId)
        if (store == null) {
            return ResponseEntity.notFound().build()
        }

        val category = categoriaId?.let { catalog.findCategory(storeId, it) }
        val categoryError =
            if (categoriaId != null && category == null) {
                "<p class=\"error\">Category <strong>$categoriaId</strong> does not belong to this store. " +
                    "Valid categories: ${store.categories.joinToString { "${it.id} (${it.name})" }}</p>"
            } else {
                ""
            }

        val html =
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <title>Rappi Mock — ${escHtml(store.name)}</title>
              <style>
                body { font-family: system-ui, sans-serif; max-width: 600px; margin: 48px auto; padding: 0 16px; }
                .badge { display:inline-block; background:#e5f9ee; color:#166534; font-size:.75rem;
                         font-weight:600; padding:2px 8px; border-radius:9999px; margin-bottom:16px; }
                h1 { margin-top:8px; }
                .meta { background:#f9fafb; border:1px solid #e5e7eb; border-radius:8px;
                        padding:16px; margin:24px 0; }
                .meta dl { display:grid; grid-template-columns:max-content 1fr; gap:4px 16px; margin:0; }
                .meta dt { font-weight:600; color:#6b7280; }
                .error { color:#b91c1c; background:#fef2f2; border:1px solid #fca5a5;
                         border-radius:6px; padding:12px; }
                fieldset { border:1px solid #e5e7eb; border-radius:8px; padding:16px; margin-top:24px; }
                legend { font-weight:600; padding:0 8px; }
                label { display:block; margin:8px 0 2px; font-size:.875rem; color:#374151; }
                select, input[type=number] { width:100%; padding:6px 8px; border:1px solid #d1d5db;
                                             border-radius:6px; margin-bottom:8px; }
                button { background:#16a34a; color:white; border:none; border-radius:6px;
                         padding:10px 20px; cursor:pointer; font-size:.875rem; margin-top:8px; }
                button:hover { background:#15803d; }
                #result { margin-top:16px; background:#f0fdf4; border:1px solid #bbf7d0;
                          border-radius:8px; padding:12px; font-family:monospace; font-size:.8rem;
                          white-space:pre-wrap; display:none; }
              </style>
            </head>
            <body>
              <span class="badge">🛒 Rappi Mock</span>
              <h1>${escHtml(store.name)}</h1>
              $categoryError
              <div class="meta">
                <dl>
                  <dt>Store ID</dt><dd>${escHtml(storeId)}</dd>
                  <dt>Store Name</dt><dd>${escHtml(store.name)}</dd>
                  <dt>Category ID</dt><dd>${escHtml(categoriaId ?: "—")}</dd>
                  <dt>Category Name</dt><dd>${escHtml(category?.name ?: "—")}</dd>
                  <dt>Tracking Token</dt><dd><code>${escHtml(trackingToken ?: "—")}</code></dd>
                </dl>
              </div>

              <fieldset>
                <legend>Emit Order Event</legend>
                <label for="scenario">Scenario</label>
                <select id="scenario">
                  <option value="DELIVERED">DELIVERED</option>
                  <option value="CANCELLED">CANCELLED</option>
                  <option value="PARTIAL_REFUND">PARTIAL_REFUND</option>
                  <option value="DUPLICATE">DUPLICATE (same order_id)</option>
                  <option value="OUT_OF_ORDER">OUT_OF_ORDER (occurred_at −10 min)</option>
                </select>
                <label for="latency">Artificial latency (ms)</label>
                <input type="number" id="latency" value="0" min="0" max="30000" step="100">
                <label>
                  <input type="checkbox" id="invalidSig"> Use invalid HMAC signature
                </label>
                <button onclick="emitOrder()">Emit order event →</button>
                <pre id="result"></pre>
              </fieldset>

              <script>
                async function emitOrder() {
                  const scenarioRaw = document.getElementById('scenario').value;
                  const scenario = scenarioRaw.split(' ')[0]; // strip display suffix
                  const body = {
                    storeId: '${escJs(storeId)}',
                    categoryId: '${escJs(categoriaId ?: "")}',
                    trackingToken: '${escJs(trackingToken ?: "")}',
                    scenario,
                    latencyMs: parseInt(document.getElementById('latency').value) || 0,
                    invalidSignature: document.getElementById('invalidSig').checked
                  };
                  const result = document.getElementById('result');
                  result.style.display = 'block';
                  result.textContent = 'Emitting…';
                  try {
                    const resp = await fetch('/mock/rappi/orders/emit', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify(body)
                    });
                    const json = await resp.json();
                    result.textContent = JSON.stringify(json, null, 2);
                  } catch (e) {
                    result.textContent = 'Error: ' + e.message;
                  }
                }
              </script>
            </body>
            </html>
            """.trimIndent()

        return ResponseEntity.ok(html)
    }

    private fun escHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun escJs(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
}
