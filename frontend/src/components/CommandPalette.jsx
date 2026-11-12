import { useEffect, useMemo, useRef, useState } from "react";
import Icon from "./Icon";

export default function CommandPalette({ items, onSelect, onClose }) {
  const [query, setQuery] = useState("");
  const [cursor, setCursor] = useState(0);
  const inputRef = useRef(null);

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter((item) => `${item.label} ${item.blurb}`.toLowerCase().includes(q));
  }, [items, query]);

  useEffect(() => { inputRef.current?.focus(); }, []);
  useEffect(() => { setCursor(0); }, [query]);

  function handleKey(event) {
    if (event.key === "Escape") { event.preventDefault(); onClose(); }
    else if (event.key === "ArrowDown") { event.preventDefault(); setCursor((c) => Math.min(c + 1, matches.length - 1)); }
    else if (event.key === "ArrowUp") { event.preventDefault(); setCursor((c) => Math.max(c - 1, 0)); }
    else if (event.key === "Enter" && matches[cursor]) { event.preventDefault(); onSelect(matches[cursor].label); }
  }

  return (
    <div className="palette-backdrop" onMouseDown={onClose}>
      <div className="palette" role="dialog" aria-modal="true" aria-label="Jump to a page"
        onMouseDown={(e) => e.stopPropagation()} onKeyDown={handleKey}>
        <div className="palette-input">
          <Icon name="search" />
          <input ref={inputRef} value={query} onChange={(e) => setQuery(e.target.value)}
            placeholder="Jump to a page" aria-label="Search pages" />
          <kbd>Esc</kbd>
        </div>
        <ul className="palette-list">
          {matches.map((item, index) => (
            <li key={item.label}>
              <button className={index === cursor ? "active" : ""}
                onMouseEnter={() => setCursor(index)} onClick={() => onSelect(item.label)}>
                <Icon name={item.icon} />
                <span><strong>{item.label}</strong><small>{item.blurb}</small></span>
              </button>
            </li>
          ))}
          {matches.length === 0 && <li className="palette-empty">No page matches “{query}”.</li>}
        </ul>
      </div>
    </div>
  );
}
