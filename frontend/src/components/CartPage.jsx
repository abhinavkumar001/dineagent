import { useState } from "react";
import { rupees } from "../api.js";

export default function CartPage({ cart, cartActions, notify, info, checkout }) {
  const [placing, setPlacing] = useState(false);
  const items = cart?.items ?? [];
  const online = info?.paymentsEnabled;

  const place = async () => {
    setPlacing(true);
    try {
      await checkout();
    } catch (e) {
      notify(e.message, e.message === "Payment cancelled" ? "ok" : "error");
    } finally {
      setPlacing(false);
    }
  };

  if (items.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-icon" aria-hidden="true">🛒</div>
        <h1>Your cart is empty</h1>
        <p className="muted">Add dishes from a restaurant, or ask DineAgent to pick for you.</p>
        <a className="btn primary" href="#/">See restaurants near you</a>
      </div>
    );
  }

  return (
    <div className="cart-page">
      <section className="cart-card">
        <div className="cart-head">
          <h1>{cart.restaurant}</h1>
          <a className="link-btn" href={`#/restaurant/${cart.restaurantId}`}>Add more items</a>
        </div>
        {items.map((i) => (
          <div className="cart-line" key={i.itemId}>
            <span>{i.name}</span>
            <div className="stepper small" role="group" aria-label={`${i.name} quantity`}>
              <button onClick={() => cartActions.setQuantity(i.itemId, i.quantity - 1)} aria-label="Remove one">−</button>
              <span>{i.quantity}</span>
              <button onClick={() => cartActions.setQuantity(i.itemId, i.quantity + 1)} aria-label="Add one">+</button>
            </div>
            <span className="amount">{rupees(i.lineTotal)}</span>
          </div>
        ))}
        <button className="link-btn danger" onClick={cartActions.clear}>Clear cart</button>
      </section>

      <section className="cart-card bill">
        {cart.freeDeliveryGap > 0 && (
          <p className="nudge">Add {rupees(cart.freeDeliveryGap)} more for <b>free delivery</b></p>
        )}
        <h2>Bill details</h2>
        <div className="bill-line"><span>Item total</span><span>{rupees(cart.itemTotal)}</span></div>
        <div className="bill-line"><span>Delivery fee</span><span>{cart.deliveryFee ? rupees(cart.deliveryFee) : "FREE"}</span></div>
        <div className="bill-line total"><span>To pay</span><span>{rupees(cart.toPay)}</span></div>
        <button className="btn primary block" onClick={place} disabled={placing}>
          {placing ? (online ? "Waiting for payment…" : "Placing order…")
            : online ? `Pay ${rupees(cart.toPay)}` : `Place order · ${rupees(cart.toPay)}`}
        </button>
        <p className="muted small pay-note">
          {online ? <>Secure payment by <b>Razorpay</b> (test mode). UPI, cards and net banking.</> : "Pay on delivery. Add Razorpay keys to enable online payment."}
        </p>
        <p className="muted small">Cancel within 60 seconds for a full refund.</p>
      </section>
    </div>
  );
}
