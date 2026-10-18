// Verdicts live in this browser only; the JSON export is the review record.

export type Verdict = "A" | "C" | "R";
/** Sections of the export: gesture, phrase (pid.card), question, ui, greeting. */
export type Section = "gesture" | "phrase" | "question" | "ui" | "greeting";

export interface Review {
  section: Section;
  id: string;
  role: string;
  verdict: Verdict | null;
  notes: string;
}

export const FORMAT = "signsetu-content-review";
const STORE = "signsetu.reviews.v1";

export const keyOf = (r: Pick<Review, "section" | "id" | "role">) => `${r.role}|${r.section}|${r.id}`;

export function load(): Record<string, Review> {
  try {
    return JSON.parse(localStorage.getItem(STORE) ?? "{}");
  } catch {
    return {};
  }
}

export function save(all: Record<string, Review>) {
  try {
    localStorage.setItem(STORE, JSON.stringify(all));
  } catch {
    // Private windows can refuse storage; the export still works for this visit.
  }
}

export function exportJson(all: Record<string, Review>, now = new Date()): string {
  const reviews = Object.values(all)
    .filter((r) => r.verdict || r.notes.trim())
    .sort((a, b) => keyOf(a).localeCompare(keyOf(b)));
  return JSON.stringify({ format: FORMAT, version: 1, exported: now.toISOString(), reviews }, null, 2);
}
