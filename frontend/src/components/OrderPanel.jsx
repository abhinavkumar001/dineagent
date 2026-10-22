import { rupees } from "../api.js";

const STEPS = ["CONFIRMED", "PREPARING", "OUT_FOR_DELIVERY", "DELIVERED"];
const LABEL = { CONFIRMED: "Confirmed", PREPARING: "Preparing", OUT_FOR_DELIVERY: "Out for delivery", DELIVERED: "Delivered" };

export default function OrderPanel({ order }) {
  if (!order) return null;
  const at = STEPS.indexOf(order.status);
  return (
    <section>
      <h2>Your order</h2>
      <div className="line"><b>{order.orderId}</b><span>{order.restaurant}</span></div>
      <div className="order-status" aria-label={`Status: ${LABEL[order.status]}`}>
        {STEPS.map((s, i) => <span key={s} className={i <= at ? "done" : ""} />)}
      </div>
      <div className="line">
        <span>{LABEL[order.status]}</span>
        <span>{order.status === "DELIVERED" ? "Enjoy!" : `${order.minutesLeft} min left`}</span>
      </div>
      <div className="total"><span>Paid</span><span>{rupees(order.total)}</span></div>
    </section>
  );
}
