import { useEffect, useState } from "react";
import { api, rupees } from "../api.js";

const LABEL = { CONFIRMED: "Confirmed", PREPARING: "Preparing", OUT_FOR_DELIVERY: "Out for delivery", DELIVERED: "Delivered" };

export default function OrdersPage({ sessionId }) {
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState("");
  useEffect(() => { api.orders(sessionId).then(setOrders).catch((e) => setError(e.message)); }, [sessionId]);

  return (
    <div className="orders-page">
      <h1>Your orders</h1>
      {error && <p className="error-text">{error}</p>}
      {orders?.length === 0 && <p className="muted">No orders yet. <a href="#/">Find something to eat</a>.</p>}
      {orders?.map((o) => (
        <a key={o.orderId} className="order-row" href={`#/order/${o.orderId}`}>
          <div><b>{o.restaurant}</b><p className="muted">{o.items.join(", ")}</p></div>
          <div className="order-right"><span className={`status s-${o.status.toLowerCase()}`}>{LABEL[o.status]}</span><b>{rupees(o.total)}</b></div>
        </a>
      ))}
    </div>
  );
}
