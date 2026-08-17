# Snap Play — piloto ejecutable Disney × Rappi

Este repositorio contiene la especificación y una primera implementación ejecutable de Snap Play como orquestador entre plataformas de contenido y plataformas de comercio/delivery.

El piloto inicial es Disney × Rappi Turbo, pero el dominio se diseñó para soportar después otros streamings, broadcasters, CTV, YouTube, transmisiones en vivo, social live y plataformas educativas, sin incorporar dependencias específicas de Disney o Rappi en el núcleo.

## Decisiones principales

1. El QR es estable y siempre apunta primero a Snap Play (`go.snapplay.io`).
2. Snap Play registra la atribución, evalúa reglas y crea un handoff; Rappi conserva autenticación, carrito, pago, pedido y fulfillment.
3. La integración recomendada con Rappi es una `storefront session`: Snap Play envía `store_id` y los productos/bundles permitidos; Rappi devuelve un deep link firmado.
4. Si Rappi inicialmente sólo admite `store_id`, el piloto funciona mediante tiendas o colecciones temáticas, sin bloquear la evolución posterior.
5. Los estados de pedidos ingresan a Snap Play mediante webhooks HTTPS firmados. Kafka se usa sólo dentro de Snap Play, no como contrato directo entre empresas.
6. La compartición comercial de datos es configurable en tres niveles: agregada, seudónima o identificada. Snap Play conserva siempre el mínimo evento facturable exigido por el contrato.
7. Revenue share y fees se calculan sobre reglas versionadas e inmutables; cada pedido conserva la versión contractual aplicada.
8. La fuente de verdad transaccional es PostgreSQL. Redis se usa para resolución rápida, rate limiting e idempotencia; un bus de eventos desacopla procesamiento y analítica.

## Archivos

- [`docs/01-pilot-product-flow.md`](docs/01-pilot-product-flow.md): alcance funcional, experiencia Disney, catálogo Rappi, QR, handoff y decisiones del piloto.
- [`docs/02-backend-architecture.md`](docs/02-backend-architecture.md): diagramas, componentes, flujos, tecnologías, eventos, observabilidad y despliegue.
- [`docs/03-security-privacy.md`](docs/03-security-privacy.md): autenticación, autorización, webhooks, secretos, privacidad y perfiles de intercambio de datos.
- [`docs/04-contracts-settlement.md`](docs/04-contracts-settlement.md): contratos, reglas económicas, ledger, conciliación, refunds y disputas.
- [`api/openapi.yaml`](api/openapi.yaml): API REST de backoffice, catálogo, experiencias, links, resolución, órdenes, analytics, contratos y settlement.
- [`api/rappi-adapter-contract.yaml`](api/rappi-adapter-contract.yaml): contrato propuesto para las APIs que Rappi expondría a Snap Play.
- [`api/asyncapi.yaml`](api/asyncapi.yaml): eventos y webhooks entre Rappi, Snap Play y Disney.
- [`database/schema.sql`](database/schema.sql): esquema PostgreSQL inicial, índices y restricciones de integridad.

## Arquitectura recomendada para el MVP

Un **monolito modular** para el control plane y los procesos de negocio, acompañado por un servicio pequeño e independiente para resolver QR/deep links en el edge. Esto evita microservicios prematuros, pero mantiene aislado el camino que necesita menor latencia y mayor disponibilidad.

## Qué ya se puede ejecutar

- Backoffice Next.js con resumen, catálogo, experiences, smart links, órdenes, conexiones, contratos y conciliación.
- API Fastify con aislamiento por organización, catálogo, creación de borradores, órdenes y resolución de smart links.
- Modo demo sin credenciales y datos fake de Disney, Rappi Turbo, Toy Story, Moana, ESPN y Hulu.
- Supabase Auth para el acceso real, JWT validado por la API y membresías/roles por organización.
- Migración PostgreSQL con RLS, políticas de datos, versiones de contrato/experience, handoffs y órdenes.
- Ingreso de CloudEvents de Rappi con HMAC SHA-256, ventana anti-replay, idempotencia y validación de transiciones.
- CI de GitHub para typecheck, lint, tests y build.

## Ejecutar ahora, sin credenciales

Requiere Node.js 20.9 o superior.

```bash
npm install
npm run dev
```

El comando levanta el backoffice (normalmente `http://localhost:3000`) y la API en `http://localhost:4000`. Si el puerto 3000 está ocupado, Next.js informa el puerto alternativo; en ese caso ajustá `SNAPPLAY_BACKOFFICE_URL` en `.env.local` para que el QR vuelva a la aplicación correcta.

El archivo `.env.local` incluido localmente está ignorado por Git y mantiene `SNAPPLAY_DEMO_MODE=true`. No contiene credenciales reales.

## Conectar un proyecto Supabase de desarrollo

1. Copiar `.env.example` a `.env.local` para la API y `apps/backoffice/.env.example` a `apps/backoffice/.env.local` para la UI. Cargar URL y `SUPABASE_SECRET_KEY` sólo en el archivo raíz; URL y publishable key públicas en el archivo del backoffice. La secret key nunca debe usar el prefijo `NEXT_PUBLIC_`. Los proyectos legacy también pueden usar `SUPABASE_SERVICE_ROLE_KEY`.
2. Cambiar `SNAPPLAY_DEMO_MODE=false` y `NEXT_PUBLIC_SNAPPLAY_DEMO_MODE=false`.
3. Vincular y aplicar la base:

```bash
npx supabase login
npx supabase link --project-ref TU_PROJECT_REF
npx supabase db push --include-seed
```

4. Crear el usuario de desarrollo después de definir `SNAPPLAY_DEMO_USER_EMAIL` y `SNAPPLAY_DEMO_USER_PASSWORD`:

```bash
npm run seed:auth
```

La alternativa totalmente local usa `npx supabase start`, pero requiere Docker Desktop o un runtime compatible. Las migraciones y el seed ya están preparados en `supabase/`.

## Seguridad de credenciales

No pegar secrets en un chat, issue, commit o captura. Cargarlos únicamente en `.env.local` (ignorado), en GitHub Actions Secrets o en el gestor de secretos del entorno. Si una clave se expone, debe rotarse antes de continuar.

## Validación

```bash
npm run typecheck
npm run lint
npm test
npm run build
```

## Próximo paso sugerido

Validar con Disney y Rappi tres contratos técnicos antes de escribir el backend:

1. Cómo Rappi expone catálogo, disponibilidad y `store_id`.
2. Qué capacidad de deep link/storefront/cart session puede implementar.
3. Qué eventos de pedido puede devolver, con qué identificador de atribución y en qué momento considera una venta cobrable.
