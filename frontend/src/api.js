// Same-origin "/api" works in dev (Vite proxy) and when the build is served by Spring Boot.
// Set VITE_API_URL to call a backend on another host.
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

export const api = {
  info: () => request("/api/info"),
  session: (id) => request(`/api/session/${encodeURIComponent(id)}`),
  chat: (sessionId, message) => request("/api/chat", { method: "POST", body: JSON.stringify({ sessionId, message }) }),
  runEval: () => request("/api/eval?judge=true", { method: "POST" }),
};

export const rupees = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");
