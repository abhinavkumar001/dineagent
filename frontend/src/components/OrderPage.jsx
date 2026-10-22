import { useEffect, useState } from "react";
import { api, rupees } from "../api.js";

const STEPS = [
  ["CONFIRMED", "Order confirmed"], ["PREPARING", "Food is being prepared"],
  ["OUT_FOR_DELIVERY", "Out for delivery"], ["DELIVERED", "Delivered"],
];

export default function OrderPage({ orderId, sessionId, askAssistant }) {
  const [order, setOrder] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let alive = true;
    const load = () => api.order(sessionId, orderId).then((o) => alive && setOrder(o)).catch((e) => alive && setError(e.message));
    load();
    const timer = setInterval(load, 15000); // live tracking
    return () => { alive = false; clearInterval(timer); };
  }, [orderId, sessionId]);

  if (error) return <p className="error-text">{error}</p>;
  if (!order) return <p className="muted">Loading order…</p>;
  const at = STEPS.findIndex(([s]) => s === order.status);

  return (
    <div className="order-page">
      <section className="track-card">
        <p className="muted">Order {order.orderId} · {order.restaurant}</p>
        <h1>{order.status === "DELIVERED" ? "Delivered. Enjoy your meal!" : `Arriving in ${order.minutesLeft} min`}</h1>
        <ol className="steps">
          {STEPS.map(([key, label], i) => (
            <li key={key} className={i < at ? "done" : i === at ? "now" : ""}>{label}</li>
          ))}
        </ol>
      </section>
      <section className="cart-card">
        <h2>Order summary</h2>
        {order.items.map((line) => <div className="bill-line" key={line}><span>{line}</span></div>)}
        <div className="bill-line"><span>Item total</span><span>{rupees(order.itemTotal)}</span></div>
        <div className="bill-line"><span>Delivery fee</span><span>{order.deliveryFee ? rupees(order.deliveryFee) : "FREE"}</span></div>
        <div className="bill-line total"><span>Paid</span><span>{rupees(order.total)}</span></div>
        <button className="link-btn" onClick={() => askAssistant(`Can I cancel order ${order.orderId}?`)}>✦ Need help with this order?</button>
      </section>
    </div>
  );
}
