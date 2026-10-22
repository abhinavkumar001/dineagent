// Same-origin "/api" works in dev (Vite proxy). Set VITE_API_URL to call a backend on another host.
const BASE = import.meta.env.VITE_API_URL || "";

async function request(path, options = {}) {
  let res;
  try {
    res = await fetch(BASE + path, { headers: { "Content-Type": "application/json" }, ...options });
  } catch {
    throw new Error("Can’t reach the server. Check that the backend is running on port 8080.");
  }
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.error || `Request failed (${res.status})`);
  return data;
}

const json = (method, body) => ({ method, body: JSON.stringify(body) });
const enc = encodeURIComponent;

export const api = {
  info: () => request("/api/info"),
  restaurants: ({ cuisine = "", pureVeg = false, sort = "relevance" } = {}) =>
    request(`/api/restaurants?cuisine=${enc(cuisine)}&pureVeg=${pureVeg}&sort=${sort}`),
  restaurant: (id) => request(`/api/restaurants/${enc(id)}`),
  search: (params) => request("/api/search?" + new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "")))),
  cart: (sid) => request(`/api/cart/${enc(sid)}`),
  addToCart: (sid, itemId, quantity = 1) => request(`/api/cart/${enc(sid)}/items`, json("POST", { itemId, quantity })),
  setQuantity: (sid, itemId, quantity) => request(`/api/cart/${enc(sid)}/items/${enc(itemId)}`, json("PUT", { quantity })),
  clearCart: (sid) => request(`/api/cart/${enc(sid)}`, { method: "DELETE" }),
  placeOrder: (sid) => request("/api/orders", json("POST", { sessionId: sid })),
  orders: (sid) => request(`/api/orders?sessionId=${enc(sid)}`),
  order: (sid, id) => request(`/api/orders/${enc(id)}?sessionId=${enc(sid)}`),
  chat: (sid, message) => request("/api/chat", json("POST", { sessionId: sid, message })),
  runEval: () => request("/api/eval?judge=true", { method: "POST" }),
};

export const rupees = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");

const DISH_EMOJI = [
  ["biryani", "🍚"], ["pizza", "🍕"], ["pasta", "🍝"], ["noodles", "🍜"], ["momos", "🥟"], ["soup", "🍲"],
  ["salad", "🥗"], ["wrap", "🌯"], ["dosa", "🥞"], ["idli", "🍘"], ["coffee", "☕"], ["dessert", "🍨"],
  ["kebab", "🍢"], ["fish", "🐟"], ["chicken", "🍗"], ["paneer", "🧀"], ["bread", "🫓"], ["breakfast", "🥭"],
  ["rice", "🍛"], ["curry", "🍛"], ["dal", "🥣"], ["chole", "🍛"], ["mushroom", "🍄"],
];
export const dishEmoji = (item) => (DISH_EMOJI.find(([t]) => item.tags?.includes(t)) ?? [null, "🍽"])[1];

export const CUISINES = [
  { name: "North Indian", emoji: "🍛" }, { name: "South Indian", emoji: "🥞" }, { name: "Biryani", emoji: "🍚" },
  { name: "Chinese", emoji: "🥡" }, { name: "Italian", emoji: "🍕" }, { name: "Healthy", emoji: "🥗" },
  { name: "Desserts", emoji: "🍨" },
];
export const cuisineEmoji = (c) => CUISINES.find((x) => x.name === c)?.emoji ?? "🍽";
