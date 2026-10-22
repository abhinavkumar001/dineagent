import { useEffect, useState } from "react";
import { wikiPhoto } from "../photos.js";

/**
 * Drawn top-down food illustrations, one style per kind of dish, so every dish has a picture
 * without needing photos. If a real photo exists at /images/<itemId>.jpg it is shown instead.
 */

// Small seeded random generator so each dish always gets the same garnish layout.
function rng(seed) {
  let h = 2166136261;
  for (const c of seed) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return () => { h = Math.imul(h ^ (h >>> 15), 2246822507); h = Math.imul(h ^ (h >>> 13), 3266489909); return ((h ^= h >>> 16) >>> 0) / 4294967296; };
}

function scatter(r, n, cx, cy, radius, draw) {
  return Array.from({ length: n }, (_, i) => {
    const a = r() * Math.PI * 2, d = Math.sqrt(r()) * radius;
    return draw(cx + Math.cos(a) * d, cy + Math.sin(a) * d, i, r);
  });
}

export function artType(item) {
  const t = new Set(item.tags ?? []);
  const n = (item.name ?? "").toLowerCase();
  if (t.has("biryani")) return "biryani";
  if (t.has("pizza")) return "pizza";
  if (t.has("dosa")) return "dosa";
  if (t.has("idli")) return "idli";
  if (t.has("noodles")) return "noodles";
  if (t.has("momos")) return "momos";
  if (t.has("soup")) return "soup";
  if (t.has("wrap")) return "wrap";
  if (t.has("pasta")) return "pasta";
  if (t.has("coffee")) return "coffee";
  if (n.includes("smoothie")) return "smoothie";
  if (n.includes("brownie")) return "brownie";
  if (n.includes("gulab")) return "jamun";
  if (n.includes("rasmalai")) return "rasmalai";
  if (n.includes("raita")) return "raita";
  if (n.includes("naan") || n.includes("garlic bread")) return "naan";
  if (n.includes("parotta")) return "parotta";
  if (t.has("kebab")) return "kebab";
  if (t.has("fish")) return "fish";
  if (t.has("salad") || n.includes("bowl")) return "salad";
  if (t.has("dal")) return "dal";
  if (t.has("chole")) return "chole";
  if (n.includes("mushroom")) return "mushroom";
  if (t.has("rice")) return "rice";
  if (n.includes("chilli paneer") || n.includes("manchurian")) return "stirfry";
  if (t.has("chicken") || t.has("mutton")) return "meatcurry";
  return "curry";
}

const BG = {
  biryani: "#f6e2c8", pizza: "#f4dccb", dosa: "#f3ecc6", idli: "#e9efe3", noodles: "#f5e6c8", momos: "#f2e5dc",
  soup: "#f6d9cf", wrap: "#e6efdb", pasta: "#f4dbd2", coffee: "#eadfd4", smoothie: "#f8e7c4", brownie: "#eadcd3",
  jamun: "#f3e1d0", rasmalai: "#f6edd2", raita: "#e3efe6", naan: "#f2e5cf", parotta: "#f2e5cf", kebab: "#f0dccd",
  fish: "#f3e5cb", salad: "#e2efdc", dal: "#f4e6c8", chole: "#f1dfcb", mushroom: "#ebe2d3", rice: "#f5e3c8",
  stirfry: "#f3dccf", curry: "#f6dfcc", meatcurry: "#f4dccb",
};

const leaf = (x, y, k, r, color = "#3f8a3a") => (
  <ellipse key={`l${k}`} cx={x} cy={y} rx={3.2} ry={1.7} fill={color} transform={`rotate(${r() * 180} ${x} ${y})`} />
);

function Plate({ children, rim = "#ffffff", r = 62 }) {
  return (
    <>
      <circle cx="100" cy="82" r={r + 3} fill="rgba(0,0,0,.08)" />
      <circle cx="100" cy="80" r={r} fill={rim} />
      <circle cx="100" cy="80" r={r - 9} fill="#f7f7f4" />
      {children}
    </>
  );
}

function Bowl({ fill, children, r = 46, cx = 100, cy = 80 }) {
  return (
    <>
      <circle cx={cx} cy={cy + 3} r={r + 4} fill="rgba(0,0,0,.1)" />
      <circle cx={cx} cy={cy} r={r + 4} fill="#e9e6df" />
      <circle cx={cx} cy={cy} r={r} fill={fill} />
      {children}
    </>
  );
}

function draw(type, r) {
  switch (type) {
    case "meatcurry": return <Bowl fill="#b8451f">
      <path d="M70 72 q30 -18 60 6" stroke="#f0c9a0" strokeWidth="5" fill="none" opacity=".7" strokeLinecap="round" />
      {scatter(r, 8, 100, 82, 28, (x, y, i) => <path key={i} d={`M${x - 8} ${y} q2 -9 10 -7 q8 3 6 10 q-6 7 -16 -3 z`} fill="#7a3416" />)}
      {scatter(r, 12, 100, 80, 38, (x, y, i) => leaf(x, y, i, r))}
    </Bowl>;
    case "curry": case "stirfry": {
      const gravy = type === "stirfry" ? "#9c4a22" : "#d4602a";
      return <Bowl fill={gravy}>
        <path d="M70 72 q30 -18 60 6" stroke="#f3d7b8" strokeWidth="5" fill="none" opacity=".8" strokeLinecap="round" />
        {scatter(r, 9, 100, 82, 30, (x, y, i) => <rect key={i} x={x - 6} y={y - 6} width="12" height="11" rx="2.5" fill={type === "stirfry" ? "#e9c27a" : "#f6e6c4"} transform={`rotate(${r() * 60} ${x} ${y})`} />)}
        {type === "stirfry" && scatter(r, 7, 100, 80, 34, (x, y, i) => <rect key={`c${i}`} x={x - 5} y={y - 2} width="10" height="4" rx="2" fill="#4c9a3a" />)}
        {scatter(r, 12, 100, 80, 38, (x, y, i) => leaf(x, y, i, r))}
      </Bowl>;
    }
    case "dal": return <Bowl fill="#7b4526">
      <path d="M72 80 q14 -14 28 0 t28 0" stroke="#f7edd5" strokeWidth="5" fill="none" strokeLinecap="round" />
      {scatter(r, 10, 100, 80, 36, (x, y, i) => leaf(x, y, i, r))}
      <circle cx="112" cy="68" r="5" fill="#f6dc8a" />
    </Bowl>;
    case "chole": return <>
      <Bowl fill="#8a4a24" r={34} cx={74} cy={84}>{scatter(r, 14, 74, 84, 26, (x, y, i) => <circle key={i} cx={x} cy={y} r="4" fill="#d9ad6c" />)}</Bowl>
      <circle cx="140" cy="70" r="34" fill="#e8c27e" /><circle cx="140" cy="70" r="26" fill="#f0d39a" />
      <circle cx="146" cy="104" r="22" fill="#e8c27e" />
    </>;
    case "biryani": return <Plate>
      <circle cx="100" cy="80" r="44" fill="#f4e3b5" />
      {scatter(r, 70, 100, 80, 42, (x, y, i) => <ellipse key={i} cx={x} cy={y} rx="3.2" ry="1.1" fill={["#fffaf0", "#f3b232", "#e88a1a", "#fff5d6"][i % 4]} transform={`rotate(${r() * 180} ${x} ${y})`} />)}
      {scatter(r, 14, 100, 80, 36, (x, y, i) => <path key={`o${i}`} d={`M${x} ${y} q4 -3 8 0`} stroke="#8b4b1e" strokeWidth="2" fill="none" />)}
      <ellipse cx="96" cy="78" rx="13" ry="10" fill="#b5652e" /><circle cx="122" cy="94" r="7" fill="#fff8ea" />
      {scatter(r, 6, 100, 80, 30, (x, y, i) => leaf(x, y, `m${i}`, r, "#2f7d3a"))}
    </Plate>;
    case "rice": return <Plate>
      <circle cx="100" cy="80" r="42" fill="#d98b3a" />
      {scatter(r, 50, 100, 80, 40, (x, y, i) => <ellipse key={i} cx={x} cy={y} rx="2.6" ry="1" fill={i % 3 ? "#f1b864" : "#a3521c"} transform={`rotate(${r() * 180} ${x} ${y})`} />)}
      {scatter(r, 10, 100, 80, 34, (x, y, i) => <circle key={`b${i}`} cx={x} cy={y} r="2.5" fill="#f3d27a" />)}
      {scatter(r, 8, 100, 80, 34, (x, y, i) => leaf(x, y, i, r))}
    </Plate>;
    case "pizza": return <>
      <circle cx="100" cy="82" r="66" fill="rgba(0,0,0,.08)" />
      <circle cx="100" cy="80" r="64" fill="#d9a05b" /><circle cx="100" cy="80" r="56" fill="#c8402b" />
      {scatter(r, 12, 100, 80, 46, (x, y, i) => <ellipse key={i} cx={x} cy={y} rx={8 + r() * 6} ry={6 + r() * 4} fill="#f7e2a3" opacity=".95" />)}
      {[0, 60, 120].map((a) => <line key={a} x1="100" y1="80" x2={100 + 64 * Math.cos(a * Math.PI / 180)} y2={80 + 64 * Math.sin(a * Math.PI / 180)} stroke="#a8722f" strokeWidth="1.5" />)}
      {[0, 60, 120].map((a) => <line key={`b${a}`} x1="100" y1="80" x2={100 - 64 * Math.cos(a * Math.PI / 180)} y2={80 - 64 * Math.sin(a * Math.PI / 180)} stroke="#a8722f" strokeWidth="1.5" />)}
      {scatter(r, 7, 100, 80, 40, (x, y, i) => <ellipse key={`l${i}`} cx={x} cy={y} rx="6" ry="3.5" fill="#2f8a3c" transform={`rotate(${r() * 180} ${x} ${y})`} />)}
    </>;
    case "dosa": return <Plate r={66}>
      <rect x="40" y="60" width="122" height="34" rx="17" fill="#e1a043" transform="rotate(-12 100 77)" />
      <rect x="44" y="64" width="114" height="20" rx="10" fill="#f0c56d" transform="rotate(-12 100 74)" />
      <circle cx="74" cy="112" r="13" fill="#e9e6df" /><circle cx="74" cy="112" r="10" fill="#d2742c" />
      <circle cx="104" cy="118" r="11" fill="#e9e6df" /><circle cx="104" cy="118" r="8" fill="#f5f1e4" />
      <circle cx="130" cy="113" r="11" fill="#e9e6df" /><circle cx="130" cy="113" r="8" fill="#7fb35a" />
    </Plate>;
    case "idli": return <Plate>
      {[[80, 66], [118, 64], [98, 98]].map(([x, y], i) => <g key={i}><ellipse cx={x} cy={y + 2} rx="19" ry="15" fill="rgba(0,0,0,.08)" /><ellipse cx={x} cy={y} rx="19" ry="15" fill="#fbfaf3" /></g>)}
      <circle cx="134" cy="102" r="14" fill="#e3b46a" /><circle cx="134" cy="102" r="9" fill="#efce8f" />
      <circle cx="66" cy="102" r="10" fill="#e9e6df" /><circle cx="66" cy="102" r="7" fill="#d2742c" />
    </Plate>;
    case "noodles": return <Bowl fill="#f0d39a" r={50}>
      {Array.from({ length: 9 }, (_, i) => <path key={i} d={`M${62 + i * 2} ${58 + i * 6} q10 -8 20 0 t20 0 t20 0 t20 0`} stroke="#e6b45c" strokeWidth="3" fill="none" strokeLinecap="round" />)}
      {scatter(r, 16, 100, 80, 40, (x, y, i) => <rect key={`v${i}`} x={x - 4} y={y - 1.5} width="8" height="3" rx="1.5" fill={["#e8692b", "#3f9a3a", "#d63b3b", "#f3d34a"][i % 4]} transform={`rotate(${r() * 180} ${x} ${y})`} />)}
    </Bowl>;
    case "momos": return <Plate>
      {[[76, 66], [104, 58], [128, 74], [84, 96], [114, 98]].map(([x, y], i) => <g key={i}>
        <circle cx={x} cy={y + 2} r="15" fill="rgba(0,0,0,.08)" /><circle cx={x} cy={y} r="15" fill="#fbf4e6" />
        <path d={`M${x - 8} ${y - 4} q8 -6 16 0 M${x - 6} ${y - 8} l2 6 M${x} ${y - 10} l0 7 M${x + 6} ${y - 8} l-2 6`} stroke="#e8dcc4" strokeWidth="1.5" fill="none" />
      </g>)}
      <circle cx="140" cy="110" r="11" fill="#e9e6df" /><circle cx="140" cy="110" r="8" fill="#d23b26" />
    </Plate>;
    case "soup": return <Bowl fill="#cf4a2c" r={48}>
      <path d="M80 76 q20 -14 40 4" stroke="#fbeee2" strokeWidth="4" fill="none" strokeLinecap="round" />
      {[[92, 92], [110, 70], [86, 66]].map(([x, y], i) => <ellipse key={i} cx={x} cy={y} rx="7" ry="4" fill="#2f8a3c" transform={`rotate(${i * 50} ${x} ${y})`} />)}
      {scatter(r, 10, 100, 80, 34, (x, y, i) => <circle key={`p${i}`} cx={x} cy={y} r="1.2" fill="#3a1a10" />)}
    </Bowl>;
    case "salad": case "smoothie": {
      const base = type === "smoothie" ? "#f6b93b" : "#7fbf5a";
      return <Bowl fill={base} r={50}>
        {type === "salad" ? <>
          {scatter(r, 18, 100, 80, 40, (x, y, i) => <ellipse key={i} cx={x} cy={y} rx="9" ry="5" fill={i % 2 ? "#4f9d3a" : "#9ad06a"} transform={`rotate(${r() * 180} ${x} ${y})`} />)}
          {scatter(r, 6, 100, 80, 34, (x, y, i) => <circle key={`t${i}`} cx={x} cy={y} r="6" fill="#e0402f" />)}
          {scatter(r, 6, 100, 80, 34, (x, y, i) => <g key={`c${i}`}><circle cx={x} cy={y} r="6" fill="#cfe7b5" /><circle cx={x} cy={y} r="3" fill="#9fcf7a" /></g>)}
          {scatter(r, 8, 100, 80, 32, (x, y, i) => <circle key={`k${i}`} cx={x} cy={y} r="3" fill="#e8cf97" />)}
        </> : <>
          {scatter(r, 7, 100, 72, 26, (x, y, i) => <path key={i} d={`M${x - 8} ${y} a8 8 0 0 1 16 0 z`} fill="#fff1b0" stroke="#f2cf55" />)}
          {scatter(r, 30, 112, 96, 22, (x, y, i) => <circle key={`g${i}`} cx={x} cy={y} r="2.2" fill={i % 2 ? "#b5772f" : "#dca45a"} />)}
          {scatter(r, 6, 84, 98, 16, (x, y, i) => <circle key={`s${i}`} cx={x} cy={y} r="2" fill="#3a2a20" />)}
        </>}
      </Bowl>;
    }
    case "wrap": return <Plate>
      {[[78, 82, -20], [124, 78, 20]].map(([x, y, a], i) => <g key={i} transform={`rotate(${a} ${x} ${y})`}>
        <rect x={x - 20} y={y - 28} width="40" height="56" rx="14" fill="#e9c98f" />
        <ellipse cx={x} cy={y - 24} rx="17" ry="8" fill="#f4ead2" />
        {scatter(r, 10, x, y - 24, 12, (px, py, k) => <circle key={k} cx={px} cy={py} r="2.6" fill={["#e0402f", "#3f9a3a", "#f3d34a", "#d9b48a"][k % 4]} />)}
      </g>)}
    </Plate>;
    case "pasta": return <Plate>
      <circle cx="100" cy="80" r="42" fill="#c8402b" />
      {scatter(r, 22, 100, 80, 34, (x, y, i) => <rect key={i} x={x - 7} y={y - 3} width="14" height="6" rx="2" fill="#f2c56a" stroke="#d9a440" transform={`rotate(${r() * 180} ${x} ${y})`} />)}
      {scatter(r, 6, 100, 80, 30, (x, y, i) => leaf(x, y, i, r, "#2f8a3c"))}
    </Plate>;
    case "coffee": return <>
      <circle cx="96" cy="84" r="52" fill="rgba(0,0,0,.08)" />
      <circle cx="96" cy="82" r="50" fill="#b9bcbf" /><circle cx="96" cy="82" r="44" fill="#d7d9db" />
      <circle cx="96" cy="78" r="26" fill="#9a9da1" /><circle cx="96" cy="78" r="22" fill="#8a5a3a" /><circle cx="96" cy="78" r="18" fill="#c9a17a" />
      <path d="M88 74 q8 -6 16 0" stroke="#e8d3b8" strokeWidth="2" fill="none" />
    </>;
    case "brownie": return <Plate>
      <rect x="66" y="52" width="68" height="56" rx="6" fill="#5a3322" /><rect x="70" y="56" width="60" height="48" rx="5" fill="#6e4130" />
      <path d="M70 70 q15 10 30 0 t30 4" stroke="#3a1f14" strokeWidth="5" fill="none" strokeLinecap="round" />
      <circle cx="116" cy="62" r="9" fill="#fbf3e6" />
    </Plate>;
    case "jamun": case "rasmalai": return <Bowl fill={type === "jamun" ? "#e8b25a" : "#f6e3a3"} r={48}>
      {(type === "jamun" ? [[88, 76], [114, 86]] : [[86, 78], [114, 80]]).map(([x, y], i) => <g key={i}>
        <circle cx={x} cy={y + 2} r="17" fill="rgba(0,0,0,.1)" />
        <circle cx={x} cy={y} r="17" fill={type === "jamun" ? "#7a3417" : "#fbf6ea"} />
        <circle cx={x - 5} cy={y - 6} r="4" fill={type === "jamun" ? "#a2502a" : "#ffffff"} />
      </g>)}
      {scatter(r, 10, 100, 80, 38, (x, y, i) => <rect key={`n${i}`} x={x - 2.5} y={y - 1} width="5" height="2" rx="1" fill={i % 2 ? "#6aa84f" : "#d9a35a"} transform={`rotate(${r() * 180} ${x} ${y})`} />)}
    </Bowl>;
    case "raita": return <Bowl fill="#f4f2ea">
      {scatter(r, 26, 100, 80, 38, (x, y, i) => <rect key={i} x={x - 2} y={y - 1} width="4" height="2" rx="1" fill={i % 3 ? "#8fc06a" : "#4f9d3a"} transform={`rotate(${r() * 180} ${x} ${y})`} />)}
      {scatter(r, 8, 100, 80, 30, (x, y, i) => <circle key={`s${i}`} cx={x} cy={y} r="1.5" fill="#b85a2a" />)}
    </Bowl>;
    case "naan": case "parotta": return <Plate>
      {type === "naan" ? <>
        <path d="M58 92 q-6 -40 40 -46 q48 -4 50 34 q2 34 -46 40 q-38 4 -44 -28 z" fill="#e3b26a" />
        {scatter(r, 14, 100, 82, 34, (x, y, i) => <ellipse key={i} cx={x} cy={y} rx="5" ry="3" fill="#b8742f" opacity=".75" />)}
        {scatter(r, 10, 100, 82, 30, (x, y, i) => leaf(x, y, i, r))}
      </> : <>
        <circle cx="100" cy="80" r="44" fill="#e1b06a" />
        {[36, 28, 20, 12].map((rr) => <circle key={rr} cx="100" cy="80" r={rr} fill="none" stroke="#c48a42" strokeWidth="2" />)}
      </>}
    </Plate>;
    case "kebab": return <Plate>
      {[66, 88].map((y, i) => <g key={i}>
        <line x1="48" y1={y} x2="152" y2={y + 4} stroke="#9a9a9a" strokeWidth="2" />
        {Array.from({ length: 4 }, (_, k) => <rect key={k} x={60 + k * 22} y={y - 8} width="20" height="16" rx="7" fill={k % 2 ? "#8c4a24" : "#a95a2c"} />)}
      </g>)}
      {[[72, 112], [96, 116]].map(([x, y], i) => <circle key={i} cx={x} cy={y} r="9" fill="none" stroke="#c88aa8" strokeWidth="3" />)}
      <path d="M118 108 a12 12 0 0 1 24 0 z" fill="#f3e46a" />
    </Plate>;
    case "fish": return <Plate>
      {[[88, 74, -10], [114, 92, 15]].map(([x, y, a], i) => <ellipse key={i} cx={x} cy={y} rx="30" ry="14" fill={i ? "#c86a2a" : "#d97b32"} transform={`rotate(${a} ${x} ${y})`} />)}
      {scatter(r, 14, 100, 82, 30, (x, y, i) => <circle key={i} cx={x} cy={y} r="1.6" fill="#7a2f12" />)}
      <path d="M126 58 a12 12 0 0 1 24 0 z" fill="#f3e46a" />
      {[[70, 106], [86, 112]].map(([x, y], i) => <circle key={`o${i}`} cx={x} cy={y} r="8" fill="none" stroke="#c88aa8" strokeWidth="3" />)}
    </Plate>;
    case "mushroom": return <Plate>
      <circle cx="100" cy="80" r="42" fill="#7a4a2a" />
      {scatter(r, 14, 100, 80, 32, (x, y, i) => <path key={i} d={`M${x - 7} ${y + 2} a7 6 0 0 1 14 0 l-4 0 l0 5 l-6 0 l0 -5 z`} fill="#d8b48a" transform={`rotate(${r() * 360} ${x} ${y})`} />)}
      {scatter(r, 10, 100, 80, 36, (x, y, i) => leaf(x, y, `c${i}`, r, "#1f5f2a"))}
      {scatter(r, 20, 100, 80, 38, (x, y, i) => <circle key={`p${i}`} cx={x} cy={y} r="1.3" fill="#1a1a1a" />)}
    </Plate>;
    default: return <Plate />;
  }
}

export function DishArt({ item, className = "" }) {
  const type = artType(item);
  const r = rng(item.id ?? item.name ?? "x");
  return (
    <svg className={`dish-art ${className}`} viewBox="22 10 156 140" role="img" aria-label={item.name}
         style={{ background: BG[type] }} preserveAspectRatio="xMidYMid slice">
      {draw(type, r)}
    </svg>
  );
}

/** Real photo if /images/<id>.jpg exists, otherwise the drawn illustration. */
/**
 * Picks the best available picture for a dish:
 *   1. your own photo at /images/<itemId>.jpg
 *   2. the dish's Wikipedia photo (free licence, see photos.js)
 *   3. the drawn illustration
 */
export function DishImage({ item, className = "" }) {
  const [stage, setStage] = useState("local");
  const [photo, setPhoto] = useState(null);

  useEffect(() => {
    setStage("local");
    setPhoto(null);
  }, [item.id]);

  useEffect(() => {
    if (stage !== "wiki") return;
    let alive = true;
    wikiPhoto(item.id).then((p) => {
      if (!alive) return;
      if (p) setPhoto(p);
      else setStage("art");
    });
    return () => { alive = false; };
  }, [stage, item.id]);

  // The hosted preview can't load outside images; it shows illustrations only.
  if (!item.id || stage === "art" || window.__DINEAGENT_NO_PHOTOS__) {
    return <DishArt item={item} className={className} />;
  }
  if (stage === "local") {
    return <img className={`dish-photo ${className}`} src={`/images/${item.id}.jpg`} alt={item.name}
                loading="lazy" onError={() => setStage("wiki")} />;
  }
  if (!photo) return <DishArt item={item} className={className} />; // while the photo loads
  return (
    <img className={`dish-photo ${className}`} src={photo.src} alt={item.name} title={`Photo: ${photo.credit}`}
         loading="lazy" referrerPolicy="no-referrer" onError={() => setStage("art")} />
  );
}
