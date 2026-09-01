-- SNA-28: Attribution funnel view — scans → redirects → orders → delivered.
-- Attribution is locked to the experience_version_id captured at scan time;
-- publishing a new version never reatributes historical sessions.
-- GMV columns are NULL when the connection's data_sharing_mode = 'AGGREGATED'.

CREATE VIEW v_attribution_funnel AS
SELECT
    hs.id                                                      AS session_id,
    sl.organization_id,
    hs.connection_id,
    sl.experience_id,
    hs.experience_version_id,
    sl.id                                                      AS smart_link_id,
    sl.placement_key,
    ev.store_selection ->> 'provider_store_id'                 AS provider_store_id,
    ev.store_selection ->> 'provider_category_id'              AS provider_category_id,
    hs.data_sharing_mode,
    hs.status                                                  AS session_status,
    hs.created_at                                              AS scanned_at,
    po.id                                                      AS order_id,
    po.status                                                  AS order_status,
    po.currency,
    -- GMV suppressed for AGGREGATED connections; visible for PSEUDONYMOUS / BILLING_ONLY
    CASE WHEN hs.data_sharing_mode = 'AGGREGATED' THEN NULL
         ELSE po.order_total_minor
    END                                                        AS order_total_minor,
    po.placed_at,
    po.delivered_at
FROM handoff_sessions hs
JOIN smart_links sl ON sl.id = hs.smart_link_id
JOIN experience_versions ev ON ev.id = hs.experience_version_id
LEFT JOIN provider_orders po ON po.handoff_id = hs.id;

COMMENT ON VIEW v_attribution_funnel IS
    'One row per handoff session. order_* columns are NULL when no order was linked. '
    'order_total_minor is suppressed (NULL) for AGGREGATED connections.';
