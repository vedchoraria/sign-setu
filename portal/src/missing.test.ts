import { expect, it } from "vite-plus/test";
import { isMissing, reviewerLang, reviewerRoles } from "./missing";

const langs = [
  { code: "hi", english: "Hindi" },
  { code: "bn", english: "Bengali" },
];

it("finds the language a reviewer role covers", () => {
  expect(reviewerLang("Language reviewer (Bengali)", langs)).toBe("bn");
  expect(reviewerLang("Language reviewer (English)", langs)).toBe("en");
  expect(reviewerLang("ISL reviewer", langs)).toBeUndefined();
});

it("marks a caption missing only for the reviewer's own language", () => {
  expect(isMissing({ en: "Are you hurt?", hi: "क्या आपको चोट लगी है?" }, "bn")).toBe(true);
  expect(isMissing({ en: "Are you hurt?", bn: " " }, "bn")).toBe(true);
  expect(isMissing({ en: "Are you hurt?" }, "en")).toBe(false);
  expect(isMissing({ en: "Are you hurt?" }, undefined)).toBe(false);
});

it("lists every reviewer role once, English included", () => {
  const all = reviewerRoles([...langs, { code: "en", english: "English" }]);
  expect(new Set(all).size).toBe(all.length);
  expect(all).toContain("Language reviewer (English)");
});
