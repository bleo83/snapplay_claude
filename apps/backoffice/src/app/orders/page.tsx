import { Download } from "lucide-react";
import { PageHeader, StatusPill } from "@/components/ui";
import { getOrders } from "@/lib/api";
import { formatDate, formatMoney } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function OrdersPage() {
  const orders = await getOrders();
  return (
    <div className="page">
      <PageHeader
        eyebrow="Atribución y fulfillment"
        title="Órdenes"
        description="Ventas originadas por una handoff session de Snap Play. La visibilidad se limita según la política de datos."
        action={
          <button className="button">
            <Download size={15} /> Exportar
          </button>
        }
      />
      <section className="panel table-panel">
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Orden</th>
                <th>Experience</th>
                <th>Usuario</th>
                <th>Estado</th>
                <th>Items</th>
                <th>Elegible</th>
                <th>Total</th>
                <th>Fecha</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id}>
                  <td>
                    <strong>{order.providerOrderRef}</strong>
                  </td>
                  <td>{order.experienceName}</td>
                  <td>
                    <code>{order.publisherUserRef ?? "—"}</code>
                  </td>
                  <td>
                    <StatusPill status={order.status} />
                  </td>
                  <td>{order.itemCount}</td>
                  <td>
                    {formatMoney(
                      order.eligibleItemValueMinor ?? 0,
                      order.currency,
                    )}
                  </td>
                  <td>
                    <strong>
                      {formatMoney(order.orderTotalMinor, order.currency)}
                    </strong>
                  </td>
                  <td>{formatDate(order.placedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
