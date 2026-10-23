import { useRef, useState, type KeyboardEvent } from "react";
import { aslGestures, languages } from "./content";
import { exportJson, keyOf, load, save, type Review } from "./reviews";
import { IslReview, LanguageReview, WelfareReview } from "./screens";
import { nextTabIndex } from "./tabs";

// The roles of the sign-off table in the content review record, plus one language reviewer per greeting language.
const ROLES = [
  "Child-welfare reviewer",
  "ISL reviewer",
  "ASL reviewer",
  "Language reviewer (English)",
  ...languages.map((l) => `Language reviewer (${l.english})`),
  "Content owner",
];

const TABS = { isl: "ISL review", asl: "ASL review", language: "Language review", welfare: "Child-welfare review" } as const;
type Tab = keyof typeof TABS;

export default function App() {
  const [tab, setTab] = useState<Tab>("isl");
  const [role, setRole] = useState(ROLES[0]);
  const [reviews, setReviews] = useState(load);
  const tabs = Object.keys(TABS) as Tab[];
  const tabRefs = useRef<Partial<Record<Tab, HTMLButtonElement | null>>>({});

  const onTabKey = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const next = nextTabIndex(event.key, index, tabs.length);
    if (next === null) return;
    event.preventDefault();
    const target = tabs[next];
    setTab(target);
    tabRefs.current[target]?.focus();
  };

  const onChange = (r: Review) => {
    const next = { ...reviews, [keyOf(r)]: r };
    setReviews(next);
    save(next);
  };

  const download = () => {
    const url = URL.createObjectURL(new Blob([exportJson(reviews)], { type: "application/json" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `content-review-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const props = { role, reviews, onChange };
  const done = Object.values(reviews).filter((r) => r.role === role && r.verdict).length;
  return (
    <>
      <header>
        <h1>Sign Setu content review</h1>
        <label>
          Reviewer role{" "}
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            {ROLES.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
        </label>
        <span className="muted">{done} verdicts as this role</span>
        <button onClick={download}>Export JSON</button>
      </header>
      <p className="note">
        Every item stays a candidate until it is approved. Verdicts are kept in this browser only; export them and keep the file as the
        review record.
      </p>
      <div role="tablist" aria-label="Review sections" className="tabs">
        {tabs.map((t, i) => (
          <button
            key={t}
            id={`tab-${t}`}
            role="tab"
            aria-selected={tab === t}
            aria-controls={`panel-${t}`}
            tabIndex={tab === t ? 0 : -1}
            className={tab === t ? "on" : ""}
            ref={(el) => {
              tabRefs.current[t] = el;
            }}
            onClick={() => setTab(t)}
            onKeyDown={(e) => onTabKey(e, i)}
          >
            {TABS[t]}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        {tab === "isl" && <IslReview {...props} />}
        {tab === "asl" && <IslReview key="asl" {...props} items={aslGestures} />}
        {tab === "language" && <LanguageReview {...props} />}
        {tab === "welfare" && <WelfareReview {...props} />}
      </div>
    </>
  );
}
