import { describe, expect, it } from "vite-plus/test";
import { nextTabIndex } from "./tabs";

describe("review tabs keyboard navigation", () => {
  it("moves right and left, wrapping at both ends", () => {
    expect(nextTabIndex("ArrowRight", 1, 4)).toBe(2);
    expect(nextTabIndex("ArrowRight", 3, 4)).toBe(0);
    expect(nextTabIndex("ArrowLeft", 2, 4)).toBe(1);
    expect(nextTabIndex("ArrowLeft", 0, 4)).toBe(3);
  });

  it("jumps to the first and last tab", () => {
    expect(nextTabIndex("Home", 2, 4)).toBe(0);
    expect(nextTabIndex("End", 1, 4)).toBe(3);
  });

  it("leaves other keys alone, including Enter and Tab", () => {
    for (const key of ["Enter", " ", "Tab", "ArrowUp", "Escape"]) {
      expect(nextTabIndex(key, 1, 4)).toBeNull();
    }
  });

  it("does not divide by zero on an empty tab list", () => {
    expect(nextTabIndex("ArrowRight", 0, 0)).toBeNull();
  });
});
