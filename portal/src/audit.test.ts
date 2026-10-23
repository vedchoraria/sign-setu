// Headless stand-in for scrubbing the review portal by hand: every gesture on the visual-audit checklist is played on
// both characters and its wrist and fingertips must stay inside the body view. Silhouette judgement still needs eyes;
// geometry, aliasing and draw order do not.
import { describe, expect, it } from "vite-plus/test";
import { gestures, aslGestures } from "./content";
import { VIEW } from "./draw";
import { AdultBuild, ChildBuild, L, R, Timeline, point, type Build, type Pose, type Side } from "./rig";

const byId = new Map(gestures.map((g) => [g.id, g]));
const aslById = new Map(aslGestures.map((g) => [g.id, g]));
const BUILDS: [string, Build][] = [
  ["adult", AdultBuild],
  ["child", ChildBuild],
];
const PARTS = ["wrist", "thumb", "index", "middle", "ring", "pinky"] as const;
const VIEWED = ["palm_opp", "thumb_opp", "index_opp", "middle_opp", "ring_opp", "pinky_opp", "wrist_opp"];

const letters = Array.from({ length: 26 }, (_, i) => `letter.${String.fromCharCode(97 + i)}`);
const aslLetters = Array.from({ length: 26 }, (_, i) => `asl.letter.${String.fromCharCode(97 + i)}`);
const aksharas = [...byId.keys()].filter((id) => id.startsWith("letter.hi."));
/** The legal and medical words the review brief names, as words.json plays them. */
const WORDS = ["law", "court", "truth", "bail", "case", "judge", "statement", "throat"].map((w) => `isl.${w}`);
const CHECKLIST = [...letters, ...aksharas, ...WORDS];
/** Aksharas the app spells for पानी, and the Latin letters they reuse. */
const ALIASES: [string, string][] = [
  ["क", "k"],
  ["प", "p"],
  ["आ", "a"],
  ["न", "n"],
  ["ई", "i"],
];

function points(p: Pose, side: Side, b: Build): number[][] {
  const h = side === R ? p.r : p.l;
  return PARTS.map((part) => {
    const [x, y] = point(h, part, side, b.handScale);
    return [x, y];
  });
}

function* steps(t: Timeline) {
  for (let s = 0; s <= t.duration + 1e-6; s += 0.05) yield s;
}

describe("review portal checks", () => {
  it("has every audited gesture in the content", () => {
    expect(CHECKLIST.filter((id) => !byId.has(id))).toEqual([]);
    expect(aksharas).toHaveLength(49);
    expect(ALIASES.every(([ak, lat]) => byId.has(`letter.hi.${ak}`) && byId.has(`letter.${lat}`))).toBe(true);
  });

  it("keeps every checklist sign inside the body view on both characters", () => {
    const outside: string[] = [];
    for (const [name, b] of BUILDS) {
      for (const id of CHECKLIST) {
        const t = new Timeline(byId.get(id)!, b);
        for (const s of steps(t)) {
          const p = t.pose(s);
          for (const side of [R, L]) {
            for (const [x, y] of points(p, side, b)) {
              const inView = x >= VIEW.left && x <= VIEW.left + VIEW.width && y >= VIEW.top && y <= VIEW.top + VIEW.height;
              if (!inView) outside.push(`${id} ${name} ${s.toFixed(2)}s ${side === R ? "R" : "L"} (${x.toFixed(1)}, ${y.toFixed(1)})`);
            }
          }
        }
      }
    }
    expect(outside.slice(0, 8)).toEqual([]);
  });

  it("draws an akshara with valid sign keys and matches unmodified base letters", () => {
    // Every akshara must produce valid key poses
    for (const ak of aksharas) {
      const g = byId.get(ak);
      expect(g).toBeDefined();
      expect(g!.keys.length).toBeGreaterThan(0);
    }
    // Base unextended aksharas match their Latin letter counterpart exactly
    const EXACT_ALIASES: [string, string][] = [
      ["क", "k"],
      ["प", "p"],
      ["न", "n"],
    ];
    for (const [name, b] of BUILDS) {
      for (const [ak, lat] of EXACT_ALIASES) {
        const hi = new Timeline(byId.get(`letter.hi.${ak}`)!, b);
        const en = new Timeline(byId.get(`letter.${lat}`)!, b);
        expect(hi.duration, `${name} ${ak}/${lat} duration`).toBeCloseTo(en.duration, 6);
        for (const s of steps(hi)) {
          for (const side of [R, L]) {
            points(hi.pose(s), side, b).forEach(([x, y], i) => {
              const [ex, ey] = points(en.pose(s), side, b)[i];
              expect({ name, ak, s, side, i, x, y }).toEqual({ name, ak, s, side, i, x: ex, y: ey });
            });
          }
        }
      }
    }
  });

  it("draws the hand placed on the other one in front, and only then casts a shadow", () => {
    for (const [, b] of BUILDS) {
      // Fingerspelling places the right hand on the left one at a fingertip or the palm.
      for (const id of ["letter.a", "letter.z", "letter.hi.क"]) {
        const g = byId.get(id)!;
        expect(g.keys.every((k) => VIEWED.includes(String(k.R?.at)))).toBe(true);
        const p = new Timeline(g, b).pose(0.5);
        expect(p.rFront, id).toBe(true);
        expect(p.handsTouch, id).toBe(true);
      }
      // A one-handed sign keeps its resting left hand out of the way, so nothing is layered.
      const there = new Timeline(byId.get("isl.there")!, b).pose(0.5);
      expect(there.rFront).toBe(false);
      expect(there.handsTouch).toBe(false);
    }
  });

  it("verifies ASL one-handed alphabet stays inside the body view on both characters", () => {
    expect(aslLetters.every((id) => aslById.has(id))).toBe(true);
    const outside: string[] = [];
    for (const [name, b] of BUILDS) {
      for (const id of aslLetters) {
        const t = new Timeline(aslById.get(id)!, b);
        for (const s of steps(t)) {
          const p = t.pose(s);
          for (const side of [R, L]) {
            for (const [x, y] of points(p, side, b)) {
              const inView = x >= VIEW.left && x <= VIEW.left + VIEW.width && y >= VIEW.top && y <= VIEW.top + VIEW.height;
              if (!inView) outside.push(`${id} ${name} ${s.toFixed(2)}s ${side === R ? "R" : "L"} (${x.toFixed(1)}, ${y.toFixed(1)})`);
            }
          }
        }
      }
    }
    expect(outside).toEqual([]);
  });
});
