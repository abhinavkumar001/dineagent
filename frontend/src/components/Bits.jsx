import { rupees } from "../api.js";
import { DishImage } from "./DishArt.jsx";

export function VegMark({ veg }) {
  return <span className={`vegmark ${veg ? "" : "non"}`} role="img" aria-label={veg ? "Veg" : "Non-veg"} />;
}

export function Spice({ level }) {
  if (!level) return null;
  return <span className="spice" aria-label={`Spice level ${level} of 3`}>{"🌶".repeat(level)}</span>;
}

/** ADD button that turns into a − 1 + stepper once the dish is in the cart. */
export function AddButton({ item, cartActions }) {
  const qty = cartActions.quantityOf(item.id);
  if (qty === 0) {
    return <button className="add-btn" onClick={() => cartActions.add(item)}>ADD</button>;
  }
  return (
    <div className="stepper" role="group" aria-label={`${item.name} quantity`}>
      <button onClick={() => cartActions.setQuantity(item.id, qty - 1)} aria-label="Remove one">−</button>
      <span>{qty}</span>
      <button onClick={() => cartActions.setQuantity(item.id, qty + 1)} aria-label="Add one">+</button>
    </div>
  );
}

export function DishRow({ item, restaurant, cartActions }) {
  return (
    <div className="dish-row">
      <div className="dish-info">
        <VegMark veg={item.veg} />
        <h3>{item.name}</h3>
        {restaurant && <a className="dish-from" href={`#/restaurant/${restaurant.id}`}>{restaurant.name}, {restaurant.deliveryMinutes} min</a>}
        <p className="dish-price">{rupees(item.price)} <Spice level={item.spiceLevel} /></p>
        <p className="dish-desc">{item.description}</p>
      </div>
      <div className="dish-media">
        <DishImage item={item} className="dish-thumb" />
        <AddButton item={item} cartActions={cartActions} />
      </div>
    </div>
  );
}

export function Stars({ rating }) {
  return <span className={`rating ${rating >= 4.5 ? "top" : ""}`}>★ {rating.toFixed(1)}</span>;
}
