import { useState } from "react";
import { aslGestures, languages } from "./content";
import { exportJson, keyOf, load, save, type Review } from "./reviews";
import { IslReview, LanguageReview, WelfareReview } from "./screens";

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
        Every item stays a candidate until it is approved. Verdicts are kept in this browser only; export them and pass the file to{" "}
        <code>tools/content/review_record.py --reviews</code>.
      </p>
      <div role="tablist" className="tabs">
        {(Object.keys(TABS) as Tab[]).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? "on" : ""} onClick={() => setTab(t)}>
            {TABS[t]}
          </button>
        ))}
      </div>
      {tab === "isl" && <IslReview {...props} />}
      {tab === "asl" && <IslReview key="asl" {...props} items={aslGestures} />}
      {tab === "language" && <LanguageReview {...props} />}
      {tab === "welfare" && <WelfareReview {...props} />}
    </>
  );
}
