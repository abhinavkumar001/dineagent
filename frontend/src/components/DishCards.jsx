import { rupees } from "../api.js";

export default function DishCards({ dishes, onAdd, disabled }) {
  return (
    <div className="dishes">
      {dishes.slice(0, 6).map((d) => (
        <div className="dish" key={d.itemId}>
          <h3><span className={`veg ${d.veg ? "" : "non"}`} title={d.veg ? "Veg" : "Non-veg"} />{d.name}</h3>
          <span className="where">{d.restaurant}, {d.deliveryMinutes} min, {d.rating}★</span>
          {d.spiceLevel > 0 && (
            <span className="spice" aria-label={`Spice level ${d.spiceLevel} of 3`}>{"🌶".repeat(d.spiceLevel)}</span>
          )}
          <div className="row">
            <span className="price">{rupees(d.price)}</span>
            <button className="btn" disabled={disabled} onClick={() => onAdd(d)}>Add</button>
          </div>
        </div>
      ))}
    </div>
  );
}
