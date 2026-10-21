// The app's content files, read straight from the Android assets at build time.
import aslFile from "../../app/src/main/assets/content/asl_signs.json";
import cardsFile from "../../app/src/main/assets/content/cards.json";
import gesturesFile from "../../app/src/main/assets/content/gestures.json";
import islFile from "../../app/src/main/assets/content/isl_signs.json";
import languagesFile from "../../app/src/main/assets/content/languages.json";
import lexiconFile from "../../app/src/main/assets/content/lexicon.json";
import phrasesFile from "../../app/src/main/assets/content/phrases.json";
import signsFile from "../../app/src/main/assets/content/signs.json";
import subjectUi from "../../app/src/main/assets/content/subject_ui.json";
import triageFile from "../../app/src/main/assets/content/triage.json";
import wordsFile from "../../app/src/main/assets/content/words.json";
import type { Gesture } from "./rig";

export type Text = Record<string, string>;

export interface Question {
  id: string;
  group: string;
  cards: string[];
  text: Text;
  gesture?: string;
}
export interface Group {
  id: string;
  label: Text;
  sensitive?: boolean;
  roles?: string[];
}
export interface Card {
  id: string;
  category: string;
  label: Text;
}

// Retargeted INCLUDE signs and the hand-authored lesson signs (review "draft") both await Deaf review.
export const gestures = [...gesturesFile.gestures, ...signsFile.gestures, ...islFile.gestures] as Gesture[];
const isl = new Map(islFile.gestures.map((g) => [g.id, g]));
/** ASL signs and the one-hand alphabet, for ASL reviewers only (D-39). */
export const aslGestures = aslFile.gestures as Gesture[];
const asl = new Map(aslFile.gestures.map((g) => [g.id, g]));
const drafts = new Set(signsFile.gestures.filter((g) => "review" in g).map((g) => g.id));
export const groups = triageFile.groups as Group[];
export const questions = triageFile.questions as Question[];
export const phrases = phrasesFile.phrases;
export const cards = new Map((cardsFile.cards as Card[]).map((c) => [c.id, c]));
export const languages = languagesFile.languages;
export const uiTexts = subjectUi as Record<string, Text>;

const lexicon = new Map(lexiconFile.entries.map((e) => [e.id, e]));
const wordFor = new Map<string, string>();
for (const w of wordsFile.words as { w: string[]; sign?: string; gesture?: string }[]) {
  for (const id of [w.sign, w.gesture]) if (id && !wordFor.has(id)) wordFor.set(id, w.w[0]);
}

const ISLRTC = "https://huggingface.co/datasets/silentone0725/Indian_Sign_Language_Data.gov_Rencoded/resolve/main/";

/** The original dictionary clip of a sign retargeted from ISLRTC, for the reviewer to compare against. */
export function sourceClip(id: string): string | undefined {
  const rec = isl.get(id)?.rec;
  return rec?.startsWith("ISLRTC/") ? ISLRTC + encodeURI(rec.slice("ISLRTC/".length)) : undefined;
}

/** What a gesture means and how it is made, for the reviewer. */
export function meaning(id: string): { label: string; how: string } {
  const e = lexicon.get(id);
  if (e) return { label: e.label.en, how: e.how.en };
  if (id.startsWith("letter.")) return { label: `Letter ${id.slice(-1).toUpperCase()}`, how: "" };
  if (id.startsWith("digit.")) return { label: `Number ${id.slice(-1)}`, how: "" };
  const a = asl.get(id);
  if (a) return { label: a.word, how: `ASL, ${aslFile.source}${a.rec ? `, recording ${a.rec}` : ""}. Draft until Deaf ASL review.` };
  const s = isl.get(id);
  if (s) return { label: `${s.word} (${s.hi})`, how: `Recorded signer, ${islFile.source}, recording ${s.rec}. Draft until Deaf review.` };
  if (drafts.has(id)) return { label: wordFor.get(id) ?? id, how: "Authored lesson sign. Draft until Deaf review." };
  return { label: wordFor.get(id) ?? id, how: "" };
}

const fileUrls = (files: Record<string, string>) =>
  Object.fromEntries(Object.entries(files).map(([path, url]) => [path.slice(path.indexOf("/content/") + 9), url]));

const cardUrls = fileUrls(
  import.meta.glob<string>("../../app/src/main/assets/content/cards/*.webp", { eager: true, query: "?url", import: "default" }),
);
const audioUrls = fileUrls(
  import.meta.glob<string>("../../app/src/main/assets/content/audio/**/*.ogg", { eager: true, query: "?url", import: "default" }),
);

export const cardUrl = (id: string): string | undefined => cardUrls[`cards/${id}.webp`];
/** Audio for a caption: `folder` is a language code or "greet", `name` the file name without extension. */
export const audioUrl = (folder: string, name: string): string | undefined => audioUrls[`audio/${folder}/${name}.ogg`];
