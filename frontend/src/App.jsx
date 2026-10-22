import { useCallback, useEffect, useState } from "react";
import { api } from "./api.js";
import Header from "./components/Header.jsx";
import Home from "./components/Home.jsx";
import RestaurantPage from "./components/RestaurantPage.jsx";
import SearchPage from "./components/SearchPage.jsx";
import CartPage from "./components/CartPage.jsx";
import OrderPage from "./components/OrderPage.jsx";
import OrdersPage from "./components/OrdersPage.jsx";
import Assistant from "./components/Assistant.jsx";

const SESSION_KEY = "dineagent.session";

function loadSessionId() {
  let id = null;
  try { id = localStorage.getItem(SESSION_KEY); } catch { /* storage unavailable */ }
  if (!id) {
    id = crypto.randomUUID();
    try { localStorage.setItem(SESSION_KEY, id); } catch { /* storage unavailable */ }
  }
  return id;
}

/** Tiny hash router: #/, #/restaurant/r1, #/search?q=…, #/cart, #/orders, #/order/DA1001 */
function parseRoute() {
  const [path, query = ""] = window.location.hash.replace(/^#/, "").split("?");
  const parts = path.split("/").filter(Boolean);
  return { page: parts[0] || "home", id: parts[1], params: Object.fromEntries(new URLSearchParams(query)) };
}

export default function App() {
  const [sessionId] = useState(loadSessionId);
  const [route, setRoute] = useState(parseRoute);
  const [cart, setCart] = useState(null);
  const [info, setInfo] = useState(null);
  const [assistantOpen, setAssistantOpen] = useState(false);
  const [assistantSeed, setAssistantSeed] = useState(null);
  const [toast, setToast] = useState(null);

  useEffect(() => {
    const onHash = () => { setRoute(parseRoute()); window.scrollTo(0, 0); };
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  useEffect(() => {
    api.info().then(setInfo).catch(() => setInfo({ offline: true }));
    api.cart(sessionId).then(setCart).catch(() => {});
  }, [sessionId]);

  const notify = useCallback((text, kind = "ok") => {
    setToast({ text, kind, at: Date.now() });
    setTimeout(() => setToast((t) => (t && Date.now() - t.at >= 2900 ? null : t)), 3000);
  }, []);

  const cartActions = {
    add: async (item) => {
      try { setCart(await api.addToCart(sessionId, item.id)); notify(`${item.name} added`); }
      catch (e) { notify(e.message, "error"); }
    },
    setQuantity: async (itemId, quantity) => {
      try { setCart(await api.setQuantity(sessionId, itemId, quantity)); }
      catch (e) { notify(e.message, "error"); }
    },
    clear: async () => {
      try { setCart(await api.clearCart(sessionId)); } catch (e) { notify(e.message, "error"); }
    },
    quantityOf: (itemId) => cart?.items?.find((i) => i.itemId === itemId)?.quantity ?? 0,
  };

  const askAssistant = (text) => {
    setAssistantSeed({ text, at: Date.now() });
    setAssistantOpen(true);
  };

  const ctx = { sessionId, cart, cartActions, notify, askAssistant, setCart };
  let page;
  switch (route.page) {
    case "restaurant": page = <RestaurantPage id={route.id} {...ctx} />; break;
    case "search": page = <SearchPage query={route.params.q ?? ""} {...ctx} />; break;
    case "cart": page = <CartPage {...ctx} />; break;
    case "orders": page = <OrdersPage {...ctx} />; break;
    case "order": page = <OrderPage orderId={route.id} {...ctx} />; break;
    default: page = <Home {...ctx} />;
  }

  return (
    <>
      <Header cart={cart} info={info} onAssistant={() => setAssistantOpen(true)} />
      <main className="page">{page}</main>

      {cart?.count > 0 && route.page !== "cart" && (
        <a className="cart-bar" href="#/cart">
          <span>{cart.count} item{cart.count > 1 ? "s" : ""} | {cart.restaurant}</span>
          <b>View cart</b>
        </a>
      )}

      {!assistantOpen && (
        <button className="ask-fab" onClick={() => setAssistantOpen(true)}>
          <span aria-hidden="true">✦</span> Ask DineAgent
        </button>
      )}
      <Assistant open={assistantOpen} seed={assistantSeed} sessionId={sessionId} info={info}
                 onClose={() => setAssistantOpen(false)} onCart={setCart} />

      {toast && <div className={`toast ${toast.kind}`} role="status">{toast.text}</div>}
    </>
  );
}
