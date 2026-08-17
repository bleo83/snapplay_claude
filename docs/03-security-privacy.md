# 3. Seguridad, identidad y privacidad

## 3.1 Modelo de amenazas resumido

Activos principales:

- credenciales de Disney, Rappi y otros partners;
- configuraciones publicadas y destinos de QR;
- tokens de atribución y handoff;
- eventos de órdenes y montos;
- contratos, reglas y estados de conciliación;
- datos seudónimos o identificados de usuarios, cuando estén autorizados.

Amenazas prioritarias:

- cambio malicioso del destino de un QR;
- acceso cruzado entre tenants;
- replay o falsificación de webhooks;
- apropiación de una sesión de handoff;
- manipulación de órdenes para generar fees;
- exposición de historial de compra;
- fuga de credenciales de partners;
- abuso automatizado de links públicos;
- modificaciones retroactivas de contratos o experiences;
- insiders con privilegios excesivos.

## 3.2 Autenticación por superficie

| Superficie                    | Autenticación recomendada                                                      |
| ----------------------------- | ------------------------------------------------------------------------------ |
| Backoffice humano             | OIDC/SAML SSO, MFA obligatoria para admin/finance, sesión corta                |
| API server-to-server          | OAuth 2.0 Client Credentials con scopes; mTLS para partners de alto riesgo     |
| Webhooks entrantes            | mTLS o OAuth + firma HMAC del cuerpo + timestamp + event ID                    |
| Webhooks salientes            | Firma HMAC por subscription; rotación con solapamiento de claves               |
| QR público                    | Sin login; código aleatorio de alta entropía, rate limits y redirect allowlist |
| Estado del pedido en pantalla | Token efímero, limitado a una handoff session y sólo milestones permitidos     |
| Soporte interno               | SSO corporativo, JIT, aprobación, motivo y auditoría                           |

## 3.3 Tokens y scopes

Claims mínimas para tokens de servicio:

```json
{
  "iss": "https://identity.snapplay.io",
  "sub": "service-account:rappi-production",
  "aud": "https://api.snapplay.io",
  "org_id": "org_rappi",
  "connection_id": "con_disney_rappi_ar",
  "scope": "catalog:write orders:write orders:read",
  "jti": "01J...",
  "iat": 1786914000,
  "exp": 1786914300
}
```

Scopes propuestos:

```text
organizations:read organizations:write
connections:read connections:write
catalog:read catalog:write
experiences:read experiences:write experiences:publish
links:read links:write
orders:read orders:write
analytics:read
contracts:read contracts:write contracts:approve
settlements:read settlements:calculate settlements:approve
webhooks:read webhooks:write
```

Reglas:

- access tokens de servicio: 5–15 minutos;
- refresh tokens sólo para clientes autorizados y almacenados en vault;
- `aud`, `iss`, firma, expiración, `jti`, org y scopes validados en cada request;
- revocación por service account y rotación sin downtime;
- nunca aceptar `org_id` del body como fuente de autorización.

## 3.4 RBAC del backoffice

| Rol                | Capacidades                                    |
| ------------------ | ---------------------------------------------- |
| Organization Admin | miembros, conexiones y configuración general   |
| Content Manager    | contexts, offers, experiences y preview        |
| Publisher          | aprobar/publicar/pausar versiones              |
| Catalog Viewer     | consultar catálogo y salud de sincronización   |
| Analyst            | analytics autorizados según data policy        |
| Finance            | contratos, statements, conciliación y disputas |
| Auditor            | lectura y audit trail, sin mutaciones          |

Controles de separación de funciones:

- quien edita una regla económica no debe aprobarla si el monto/riesgo supera el umbral;
- publicar a producción puede exigir four-eyes approval;
- secrets no se muestran nuevamente después de crearse;
- impersonación de soporte no permite mutaciones financieras.

## 3.5 Seguridad de webhooks

Headers:

```http
X-SnapPlay-Event-Id: evt_01J...
X-SnapPlay-Timestamp: 1786914000
X-SnapPlay-Signature: v1=hex(hmac_sha256(secret, timestamp + "." + raw_body))
```

Validación:

1. Leer el body crudo antes de parsearlo.
2. Rechazar timestamps con diferencia mayor a cinco minutos.
3. Resolver la clave por subscription/connection y verificar en tiempo constante.
4. Validar schema y tamaño máximo.
5. Insertar `event_id` con unique constraint.
6. Persistir payload cifrado o la proyección mínima según política.
7. Responder `202` y procesar asíncronamente.

Entrega saliente:

- retry exponencial con jitter;
- dead-letter queue después del límite;
- panel de reintento manual;
- el mismo event ID y body en cada retry;
- endpoint de replay protegido y auditado;
- rotación de secret con `v1` y `v2` simultáneos durante la ventana.

## 3.6 Smart links y redirects

- Código aleatorio de al menos 96 bits efectivos; no secuencial.
- Dominios y esquemas de destino registrados por connection.
- Ningún destino arbitrario proviene de parámetros de query.
- Payload firmado entre Core y Edge.
- `Cache-Control: no-store` en redirects con atribución.
- Tracking token opaco, específico por partner y no reversible.
- Expiración de handoff sessions y nonce donde el provider lo admita.
- Rate limiting por IP, ASN, link y device signals sin crear fingerprint invasivo.
- Detección de bots separada de métricas humanas.
- Kill switch por link, experience, connection u organización.

## 3.7 Secretos y cifrado

- Credenciales únicamente en Secret Manager/Vault; la base almacena referencias.
- Envelope encryption con KMS para payloads sensibles.
- TLS 1.2+ externo y TLS interno donde la plataforma lo permita.
- Backups cifrados y restauraciones probadas.
- Claves separadas por environment y, para datos de alta sensibilidad, por dominio.
- Rotación periódica y ante incidentes.
- Imágenes del catálogo se referencian o cachean en un bucket controlado; se escanean y no se confía en MIME/extensión del provider.

## 3.8 Perfiles de intercambio de datos

La preferencia “no compartir datos” debe convertirse en una política explícita. No puede impedir que Snap Play reciba el mínimo comprobante de conversión si el fee depende de la ejecución.

### `AGGREGATED`

Los aliados ven métricas agrupadas. Snap Play recibe por orden:

- `provider_order_ref` seudonimizado;
- tracking/handoff token;
- estado cobrable;
- monto, moneda y timestamp necesarios para fees;
- refund/cancel status.

No se comparte line item ni identificador persistente del usuario.

### `PSEUDONYMOUS`

Además de lo anterior:

- `publisher_user_ref` seudónimo y específico de la relación;
- categorías o SKUs autorizados de la tienda/campaña;
- métricas por usuario seudónimo.

No se comparten nombre, email, teléfono, dirección ni cuenta Rappi.

### `IDENTIFIED_WITH_CONSENT`

Sólo cuando exista base legal, consentimiento explícito, contrato y propósito definido. La vinculación debe ser proporcionada por un identity broker o flujo OAuth; Snap Play no intenta inferir identidades.

### `BILLING_ONLY`

Caso especial para una parte que no desea compartir analítica con la otra. Snap Play conserva únicamente la evidencia necesaria para:

- determinar que la transacción ocurrió;
- calcular su propio fee;
- calcular revenue share si existe;
- soportar auditoría y disputa.

Los dashboards del aliado contrario muestran totales permitidos, no detalle.

## 3.9 Matriz de campos

| Campo                 |    Aggregated |   Pseudonymous |                         Identified |                   Billing only |
| --------------------- | ------------: | -------------: | ---------------------------------: | -----------------------------: |
| Conteo de órdenes     |            Sí |             Sí |                                 Sí |              Total contractual |
| Monto/moneda          |      Agregado |      Por orden |                          Por orden |            Por orden en ledger |
| SKU/categoría         | No o agregado | Si se autoriza |                     Si se autoriza | Sólo si afecta regla económica |
| Publisher user ref    |            No |             Sí |                                 Sí |                             No |
| Rappi account ID      |            No |             No |       Token vinculado, no ID crudo |                             No |
| Nombre/email/teléfono |            No |             No | Sólo si indispensable y consentido |                             No |
| Dirección             |            No |             No |                     No por defecto |                             No |
| Ubicación del courier |            No | Sólo milestone |                     Sólo milestone |                             No |

## 3.10 Privacidad y retención

- Purpose limitation por connection y contract.
- Consent receipt versionado con texto/política, timestamp y fuente.
- Seudónimos diferentes por relación para evitar correlación entre partners.
- Retención diferenciada: eventos operativos cortos; ledger según obligaciones fiscales/contractuales.
- Borrado o anonimización del vínculo de usuario sin destruir registros financieros obligatorios.
- Data subject requests con búsqueda por identity mapping autorizado.
- Residencia regional configurable.
- DLP y clasificación de columnas.
- Exports con watermark, expiración y auditoría.

Antes del piloto se requiere revisión legal específica de las jurisdicciones, especialmente para perfiles por usuario, alcohol, menores, datos de Disney y cruce con datos de compras.

## 3.11 Audit trail

Eventos administrativos append-only:

- actor humano o service account;
- organización y rol;
- acción y recurso;
- versión previa y posterior redactada;
- request/trace ID;
- timestamp y origen;
- motivo/aprobación para acciones financieras;
- hash encadenado o export periódico a almacenamiento inmutable.

Se auditan especialmente: cambios de destino, publicación, secrets, políticas de datos, contratos, ajustes y aprobación de settlements.

## 3.12 Controles de seguridad previos a producción

- Threat model y data-flow review con Disney y Rappi.
- Pen test de backoffice, APIs y resolver.
- Revisión de open redirect, SSRF, IDOR/tenant breakout y replay.
- Prueba de rotación de credenciales y webhook secrets.
- Restore de backup y disaster recovery tabletop.
- Carga y abuso de QR públicos.
- Simulación de webhooks duplicados, atrasados y falsificados.
- Reconciliación de órdenes sin depender exclusivamente del webhook.
- Runbooks de compromiso de credenciales, destino malicioso y discrepancia financiera.
