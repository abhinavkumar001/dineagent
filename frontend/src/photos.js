/**
 * Real dish photos from Wikipedia / Wikimedia Commons (free licences).
 * Each dish maps to a Wikipedia article; the browser asks Wikipedia's public REST API for that article's
 * lead image. Results are cached in memory and in localStorage for a week.
 * Dishes without a good article map to null and keep their drawn illustration.
 */
const ARTICLE = {
  i101: "Paneer_tikka_masala", i102: "Dal_makhani", i103: "Butter_chicken", i104: "Seekh_kebab",
  i105: "Naan", i106: "Chole_bhature",
  i201: "Buddha_bowl", i202: "Greek_salad", i203: "Tomato_soup", i204: "Wrap_(food)", i205: "Smoothie",
  i301: "Chicken_curry", i302: "Parotta", i303: null, i304: "Fried_fish",
  i401: "Masala_dosa", i402: "Idli", i403: "Bisi_bele_bath", i404: "Indian_filter_coffee", i405: "Dosa",
  i501: "Chow_mein", i502: "Paneer", i503: "Fried_rice", i504: "Manchurian_(dish)", i505: "Momo_(food)",
  i601: "Pizza_Margherita", i602: "Pepperoni", i603: "Arrabbiata_sauce", i604: "Garlic_bread",
  i701: "Hyderabadi_biryani", i702: "Pulao", i703: "Biryani", i704: "Raita",
  i801: "Gulab_jamun", i802: "Chocolate_brownie", i803: "Ras_malai",
};

const API = "https://en.wikipedia.org/api/rest_v1/page/summary/";
const WEEK_MS = 7 * 24 * 60 * 60 * 1000;
const memory = new Map();

function readStored(title) {
  try {
    const raw = localStorage.getItem("dineagent.photo." + title);
    if (!raw) return undefined;
    const { savedAt, photo } = JSON.parse(raw);
    return Date.now() - savedAt < WEEK_MS ? photo : undefined;
  } catch {
    return undefined;
  }
}

function store(title, photo) {
  try {
    localStorage.setItem("dineagent.photo." + title, JSON.stringify({ savedAt: Date.now(), photo }));
  } catch { /* storage full or unavailable: memory cache still works */ }
}

/** Wikipedia thumbnails look like .../320px-Name.jpg; ask for a 640px version when the original is big enough. */
function sized(thumbnail, originalWidth) {
  if (!originalWidth || originalWidth < 640) return thumbnail;
  return thumbnail.replace(/\/\d+px-/, "/640px-");
}

/** Resolves to { src, page, credit } or null. Never rejects. */
export function wikiPhoto(itemId) {
  const title = ARTICLE[itemId];
  if (!title) return Promise.resolve(null);
  if (memory.has(title)) return memory.get(title);

  const stored = readStored(title);
  if (stored !== undefined) {
    const p = Promise.resolve(stored);
    memory.set(title, p);
    return p;
  }

  const p = fetch(API + encodeURIComponent(title), { headers: { "Api-User-Agent": "DineAgent portfolio demo" } })
    .then((r) => (r.ok ? r.json() : null))
    .then((d) => {
      const thumb = d?.thumbnail?.source;
      const photo = thumb
        ? { src: sized(thumb, d.originalimage?.width), page: d.content_urls?.desktop?.page ?? null,
            credit: `${d.title} · Wikipedia` }
        : null;
      store(title, photo);
      return photo;
    })
    .catch(() => null); // offline or blocked: fall back to the illustration
  memory.set(title, p);
  return p;
}
