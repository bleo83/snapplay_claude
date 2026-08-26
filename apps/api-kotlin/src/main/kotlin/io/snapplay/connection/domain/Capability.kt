package io.snapplay.connection.domain

/**
 * Typed capabilities a connector adapter may declare.
 * Use cases validate that a connector supports every capability declared by a new connection,
 * keeping connector-specific logic out of the domain.
 */
enum class Capability {
    /** Build app and web deep links to a specific provider store + category (required for MVP). */
    STORE_CATEGORY_DEEPLINK,

    /** Pull and sync the provider's store/category catalog into Snap Play. */
    CATALOG_SYNC,

    /** Receive signed order-event webhooks from the provider. */
    ORDER_WEBHOOK_INGRESS,

    /** Push allowed order-status milestones to the content organisation. */
    ORDER_STATUS_MILESTONES,

    /** Pull provider orders via API daily for reconciliation. */
    DAILY_RECONCILIATION,
}
