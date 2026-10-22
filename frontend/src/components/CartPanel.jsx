import { rupees } from "../api.js";

export default function CartPanel({ cart, onReview }) {
  const items = cart?.items ?? [];
  return (
    <section>
      <h2>Your cart</h2>
      {items.length === 0 ? (
        <p className="empty">Empty. Ask for a dish and say “add it”.</p>
      ) : (
        <>
          <p className="empty">{cart.restaurant}</p>
          {items.map((i) => (
            <div className="line" key={i.itemId}><span>{i.quantity} × {i.name}</span><span>{rupees(i.lineTotal)}</span></div>
          ))}
          <div className="total"><span>Item total</span><span>{rupees(cart.itemTotal)}</span></div>
          <button className="btn primary block" onClick={onReview}>Review and order</button>
        </>
      )}
    </section>
  );
}
