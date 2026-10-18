import { expect, it } from "vite-plus/test";
import { flags } from "./flags";

const q = (text: string, extra: Partial<Parameters<typeof flags>[0]> = {}) =>
  flags({ text, cardCategories: [], sensitive: false, ...extra });

it("passes a plain open question", () => {
  expect(q("Can you show me what happened?")).toEqual([]);
});

it("flags presupposing, either/or, tag and stacked questions", () => {
  expect(q("Has someone hurt you?")).toEqual([expect.stringMatching(/presuppose a person/)]);
  expect(q("Where is the knife?")).toEqual([expect.stringMatching(/"the"/)]);
  expect(q("Was it day or night?")[0]).toMatch(/Either/);
  // "or" inside a word is not a choice.
  expect(q("Is your door shut?")).toEqual([]);
  expect(q("You were at home, weren't you?")[0]).toMatch(/Tag/);
  expect(q("Are you hurt? Where?")[0]).toMatch(/stacked/);
});

it("flags body cards only in sensitive questions, and open-palm gestures", () => {
  expect(q("Where does it hurt?", { cardCategories: ["body"] })).toEqual([]);
  expect(q("Where does it hurt?", { cardCategories: ["body"], sensitive: true })).toHaveLength(1);
  expect(q("Okay?", { gesture: "you.palm" })[0]).toMatch(/open palm/);
});
