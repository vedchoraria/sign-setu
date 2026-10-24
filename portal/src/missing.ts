import type { Text } from "./content";

/** The language code a "Language reviewer (X)" role reviews; undefined for every other role. */
export function reviewerLang(role: string, languages: { code: string; english: string }[]): string | undefined {
  const name = /^Language reviewer \((.+)\)$/.exec(role)?.[1];
  if (!name) return undefined;
  return name === "English" ? "en" : languages.find((l) => l.english === name)?.code;
}

/** True when the reviewer's language has no caption here, so there is nothing to approve. */
export const isMissing = (text: Text, code: string | undefined): boolean => code !== undefined && !text[code]?.trim();

/** The sign-off table's roles, with one language reviewer per greeting language (English among them). */
export function reviewerRoles(languages: { english: string }[]): string[] {
  return [
    "Child-welfare reviewer",
    "ISL reviewer",
    "ASL reviewer",
    ...languages.map((l) => `Language reviewer (${l.english})`),
    "Content owner",
  ];
}
