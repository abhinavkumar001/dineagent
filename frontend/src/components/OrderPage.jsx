import { useEffect, useState } from "react";
import { api, rupees } from "../api.js";

const STEPS = [
  ["CONFIRMED", "Order confirmed"], ["PREPARING", "Food is being prepared"],
  ["OUT_FOR_DELIVERY", "Out for delivery"], ["DELIVERED", "Delivered"],
];

export default function OrderPage({ orderId, sessionId, askAssistant, liveOrders }) {
  const [fetched, setFetched] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.order(sessionId, orderId).then(setFetched).catch((e) => setError(e.message));
  }, [orderId, sessionId]);

  // Live updates arrive over Server-Sent Events; the fetch above is only the first paint.
  const order = liveOrders[orderId] ?? fetched;
  if (error && !order) return <p className="error-text">{error}</p>;
  if (!order) return <p className="muted">Loading order…</p>;
  const at = STEPS.findIndex(([s]) => s === order.status);
  const progress = Math.round((order.progress ?? 0) * 100);

  return (
    <div className="order-page">
      <section className="track-card">
        <p className="muted">Order {order.orderId} · {order.restaurant} <span className="live-dot">Live</span></p>
        <h1>{order.status === "DELIVERED" ? "Delivered. Enjoy your meal!" : `Arriving in ${order.minutesLeft} min`}</h1>

        <div className="route" aria-label={`Delivery ${progress}% complete`}>
          <span className="route-end" aria-hidden="true">🍳</span>
          <div className="route-track"><div className="route-fill" style={{ width: `${progress}%` }} />
            <span className="rider" style={{ left: `${progress}%` }} aria-hidden="true">🛵</span></div>
          <span className="route-end" aria-hidden="true">🏠</span>
        </div>

        <ol className="steps">
          {STEPS.map(([key, label], i) => (
            <li key={key} className={i < at || order.status === "DELIVERED" ? "done" : i === at ? "now" : ""}>{label}</li>
          ))}
        </ol>
      </section>
      <section className="cart-card">
        <h2>Order summary</h2>
        {order.items.map((line) => <div className="bill-line" key={line}><span>{line}</span></div>)}
        <div className="bill-line"><span>Item total</span><span>{rupees(order.itemTotal)}</span></div>
        <div className="bill-line"><span>Delivery fee</span><span>{order.deliveryFee ? rupees(order.deliveryFee) : "FREE"}</span></div>
        <div className="bill-line total"><span>{order.paymentMethod === "RAZORPAY" ? "Paid online" : "Pay on delivery"}</span><span>{rupees(order.total)}</span></div>
        {order.paymentId && <p className="muted small">Razorpay payment {order.paymentId}</p>}
        <button className="link-btn" onClick={() => askAssistant(`Can I cancel order ${order.orderId}?`)}>✦ Need help with this order?</button>
      </section>
    </div>
  );
}
