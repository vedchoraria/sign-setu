// Port of the drawing in app/src/main/java/app/signsetu/ui/avatar/Avatar.kt, still (no idle sway or blink).
import { CENTER, L, R, elbowAt, lerp, shoulder, type Build, type Face, type HandPose, type HandShape, type Pose, type Side } from "./rig";

type Ctx = CanvasRenderingContext2D;
type Pt = [number, number];

interface Look {
  skin: string;
  skinShade: string;
  line: string;
  outline: string;
  hair: string;
  hairShine: string;
  top: string;
  topShade: string;
  trim: string;
  trousers: string;
  iris: string;
  lip: string;
  blush: string;
  longSleeves: boolean;
}

export const ChildLook: Look = {
  skin: "#C98D5E",
  skinShade: "#AE7345",
  line: "#7C4A27",
  outline: "#4A2A14",
  hair: "#2A1F1B",
  hairShine: "#4A3A33",
  top: "#2E9C93",
  topShade: "#227E77",
  trim: "#F6BE4B",
  trousers: "#3D4A6B",
  iris: "#3A2416",
  lip: "#8C3A32",
  blush: "#F07C6444",
  longSleeves: false,
};
export const AdultLook: Look = {
  skin: "#BC7E52",
  skinShade: "#9F6842",
  line: "#6E4123",
  outline: "#3E2412",
  hair: "#1E1816",
  hairShine: "#3A302C",
  top: "#3B4A6E",
  topShade: "#2A3654",
  trim: "#8394C2",
  trousers: "#2B3448",
  iris: "#2E1D12",
  lip: "#7E3A33",
  blush: "#F07C6422",
  longSleeves: true,
};

/** The part of the 200-unit character space shown, as the body AvatarView in Avatar.kt. */
const VIEW = { left: -10, top: 0, width: 220, height: 258 };

const WHITE = "#FFFFFF";
const HALO = "rgba(255,255,255,0.35)";
const PH = 12;
const LENGTHS = [9.2, 10.2, 9.6, 7.7];
const WIDTHS = [3.1, 3.2, 3.0, 2.6];
const TAPER = [1, 0.92, 0.84];
const rad = (d: number) => (d * Math.PI) / 180;
const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi);

function mix(a: string, b: string, f: number): string {
  const p = (c: string) => [1, 3, 5, 7].map((i) => parseInt(c.slice(i, i + 2) || "ff", 16));
  const [x, y] = [p(a), p(b)];
  const v = x.map((c, i) => Math.round(lerp(c, y[i], f)));
  return `rgba(${v[0]},${v[1]},${v[2]},${v[3] / 255})`;
}
const alpha = (c: string, a: number) => mix(c, c, 0).replace(/,[^,]*\)$/, `,${a})`);

function stroke(ctx: Ctx, color: string, width: number, cap: CanvasLineCap = "round", a = 1) {
  ctx.globalAlpha = a;
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.lineCap = cap;
  ctx.stroke();
  ctx.globalAlpha = 1;
}
function fill(ctx: Ctx, color: string | CanvasGradient, a = 1) {
  ctx.globalAlpha = a;
  ctx.fillStyle = color;
  ctx.fill();
  ctx.globalAlpha = 1;
}
function line(ctx: Ctx, color: string, a: Pt, b: Pt, width: number, cap: CanvasLineCap = "round", al = 1) {
  ctx.beginPath();
  ctx.moveTo(a[0], a[1]);
  ctx.lineTo(b[0], b[1]);
  stroke(ctx, color, width, cap, al);
}
/** Oval in a box, as Compose drawOval(topLeft, size). */
function oval(ctx: Ctx, x: number, y: number, w: number, h: number) {
  ctx.beginPath();
  ctx.ellipse(x + w / 2, y + h / 2, Math.abs(w / 2), Math.abs(h / 2), 0, 0, Math.PI * 2);
}
/** Arc in a box, as Compose drawArc(start, sweep, useCenter, topLeft, size). */
function arc(ctx: Ctx, start: number, sweep: number, x: number, y: number, w: number, h: number, center = false) {
  ctx.beginPath();
  if (center) ctx.moveTo(x + w / 2, y + h / 2);
  ctx.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, rad(start), rad(start + sweep));
  if (center) ctx.closePath();
}
function roundRect(ctx: Ctx, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}
function circle(ctx: Ctx, r: number, c: Pt) {
  ctx.beginPath();
  ctx.arc(c[0], c[1], Math.max(r, 0), 0, Math.PI * 2);
}
const lerpPt = (a: Pt, b: Pt, f: number): Pt => [lerp(a[0], b[0], f), lerp(a[1], b[1], f)];

/** Draws [pose] for [b] on the whole canvas, fitting the view to its height. */
export function drawCharacter(ctx: Ctx, b: Build, look: Look, pose: Pose, child: boolean) {
  const { width, height } = ctx.canvas;
  ctx.clearRect(0, 0, width, height);
  const unit = height / VIEW.height;
  ctx.save();
  ctx.translate((width - VIEW.width * unit) / 2 - VIEW.left * unit, -VIEW.top * unit);
  ctx.scale(unit, unit);
  // A raised hand lifts its own shoulder, and the body leans a little towards where the hands are.
  const liftR = pose.shrug * 7 + 3 * clamp((b.shoulderY - pose.r.y) / 90, 0, 1);
  const liftL = pose.shrug * 7 + 3 * clamp((b.shoulderY - pose.l.y) / 90, 0, 1);
  const dl = (0.04 * (pose.r.x + pose.l.x - 200)) / 100;
  const lean = pose.lean + dl;
  const tilt = pose.tilt - 1.5 * dl;
  const bodyDy = pose.hunch * 6;
  drawNeck(ctx, b, look, lean * 7, bodyDy);
  ctx.save();
  ctx.translate(lean * 6, bodyDy);
  drawTorso(ctx, b, look, liftR, liftL, child);
  ctx.restore();
  ctx.save();
  ctx.translate(lean * 8 + pose.turn * 2, bodyDy + pose.nod * 6 + pose.hunch * 10);
  const pivot: Pt = [b.headX, b.neckTop + 4];
  ctx.translate(pivot[0], pivot[1]);
  ctx.rotate(rad(tilt + pose.hunch * 8));
  ctx.translate(-pivot[0], -pivot[1]);
  drawHead(ctx, b, look, pose, child);
  ctx.restore();
  const arms: [HandPose, Side, number][] = [
    [pose.r, R, liftR],
    [pose.l, L, liftL],
  ];
  // The hand placed on the other one draws last, in front.
  if (pose.rFront) arms.reverse();
  for (const [h, side, lift] of arms) drawArm(ctx, b, look, h, side, [lean * 6, bodyDy - lift]);
  ctx.restore();
}

function drawTorso(ctx: Ctx, b: Build, look: Look, liftR: number, liftL: number, child: boolean) {
  const c = CENTER;
  const sh = b.shoulderHalf;
  const sy = b.shoulderY;
  const nb = b.neckWidth / 2 + 2;
  ctx.beginPath();
  ctx.moveTo(c - sh - 3, 262);
  ctx.lineTo(c - sh - 3, 228);
  ctx.bezierCurveTo(c - sh - 3, 205, c - sh - 12, sy + 60, c - sh - 12, sy + 30);
  ctx.lineTo(c - sh - 12, sy + 6 - liftR);
  ctx.quadraticCurveTo(c - sh - 12, sy - 6 - liftR, c - sh, sy - 6 - liftR);
  ctx.quadraticCurveTo(c - sh * 0.55, sy - 15 - liftR, c - nb, sy - 17 - liftR);
  ctx.lineTo(c + nb, sy - 17 - liftL);
  ctx.quadraticCurveTo(c + sh * 0.55, sy - 15 - liftL, c + sh, sy - 6 - liftL);
  ctx.quadraticCurveTo(c + sh + 12, sy - 6 - liftL, c + sh + 12, sy + 6 - liftL);
  ctx.lineTo(c + sh + 12, sy + 30);
  ctx.bezierCurveTo(c + sh + 12, sy + 60, c + sh + 3, 205, c + sh + 3, 228);
  ctx.lineTo(c + sh + 3, 262);
  ctx.closePath();
  const g = ctx.createLinearGradient(0, sy - 20, 0, 240);
  g.addColorStop(0, mix(look.top, WHITE, 0.14));
  g.addColorStop(0.5, look.top);
  g.addColorStop(1, look.topShade);
  fill(ctx, g);
  stroke(ctx, look.line, 1.1, "butt", 0.22);
  oval(ctx, c - sh - 6, sy + 26, 14, 60);
  fill(ctx, look.topShade, 0.55);
  oval(ctx, c + sh - 8, sy + 26, 14, 60);
  fill(ctx, look.topShade, 0.55);
  const w = sh + 3;
  if (child) {
    ctx.fillStyle = look.topShade;
    ctx.fillRect(c - w, 236, w * 2, 3);
    ctx.fillStyle = look.trousers;
    ctx.fillRect(c - w, 239, w * 2, 23);
  } else {
    ctx.fillStyle = look.trousers;
    ctx.fillRect(c - w, 241, w * 2, 21);
    ctx.fillStyle = "#2B2F3A";
    ctx.fillRect(c - w, 236, w * 2, 5);
    roundRect(ctx, c - 3, 236, 6, 5, 1);
    fill(ctx, look.trim);
  }
  const collar = sy - 17 - (liftR + liftL) / 2;
  const chest = mix(look.skin, look.skinShade, 0.45);
  if (child) {
    arc(ctx, 0, 180, c - 12, collar - 9, 24, 18, true);
    fill(ctx, chest);
    arc(ctx, 10, 160, c - 13, collar - 10, 26, 21);
    stroke(ctx, look.trim, 3.6);
  } else {
    ctx.beginPath();
    ctx.moveTo(c - 10, collar + 0.5);
    ctx.lineTo(c, collar + 14);
    ctx.lineTo(c + 10, collar + 0.5);
    fill(ctx, chest);
    stroke(ctx, look.trim, 3);
  }
}

function drawNeck(ctx: Ctx, b: Build, look: Look, dx: number, dy: number) {
  const c = CENTER + dx;
  const w = b.neckWidth;
  const top = b.neckTop + dy;
  const base = b.shoulderY - 10 + dy;
  roundRect(ctx, c - w / 2, top, w, base - top, 4);
  fill(ctx, look.skinShade);
  roundRect(ctx, c - w / 2 + 1.5, top, w * 0.62, base - top, 4);
  fill(ctx, look.skin);
  oval(ctx, c - w / 2, top - 2, w, 11);
  fill(ctx, look.skinShade, 0.9);
}

function drawHead(ctx: Ctx, b: Build, look: Look, pose: Pose, child: boolean) {
  const f = pose.face;
  const hx = b.headX;
  const hy = b.headY;
  const fx = hx + pose.turn * 7;
  const fy = hy + pose.nod * 2.5;

  const earY = hy + (child ? 5 : 3);
  for (const s of [-1, 1]) {
    const ex = hx + s * (b.headRx - 1) - pose.turn * 3;
    oval(ctx, ex - 6.5, earY - 8.5, 13, 17);
    fill(ctx, look.skin);
    oval(ctx, ex - 3 + s * 0.5, earY - 5, 6, 10);
    fill(ctx, look.skinShade, 0.7);
  }
  const lx = hx - b.headRx * 0.28;
  const ly = hy - b.headRy * 0.32;
  const light = ctx.createRadialGradient(lx, ly, 0, lx, ly, b.headRy * 1.35);
  light.addColorStop(0, mix(look.skin, WHITE, 0.13));
  light.addColorStop(0.5, look.skin);
  light.addColorStop(1, mix(look.skin, look.skinShade, 0.7));
  oval(ctx, hx - b.headRx, hy - b.headRy, b.headRx * 2, b.headRy * 2);
  fill(ctx, light);
  stroke(ctx, look.line, 1.1, "butt", 0.28);

  drawHair(ctx, b, look, child, pose.turn);

  // Brows carry the question and worry.
  const browY = fy - (child ? 15 : 13) - f.browRaise * 5 + f.browFrown * 2;
  const browGap = child ? 7 : 6;
  const browLen = child ? 13 : 12;
  for (const s of [-1, 1]) {
    const inner: Pt = [fx + s * browGap, browY - f.browWorry * 4 + f.browFrown * 3.5];
    const outer: Pt = [fx + s * (browGap + browLen), browY + f.browWorry * 1.5 - f.browFrown + 1];
    const archH = (child ? 3.2 : 2.4) * (1 - f.browFrown);
    ctx.beginPath();
    ctx.moveTo(inner[0], inner[1]);
    ctx.quadraticCurveTo((inner[0] + outer[0]) / 2, Math.min(inner[1], outer[1]) - archH, outer[0], outer[1]);
    stroke(ctx, look.hair, child ? 3.4 : 3.2);
  }

  const open = clamp(f.eyeOpen * (1 - pose.gaze * 0.18), 0.05, 1.2);
  const eyeY = fy - 1 + pose.gaze * (child ? 2.2 : 0.6);
  for (const s of [-1, 1]) {
    const ex = fx + s * (child ? 15 : 13);
    if (f.eyeSmile > 0.55 || open < 0.18) {
      ctx.beginPath();
      ctx.moveTo(ex - 6, eyeY + 1);
      ctx.quadraticCurveTo(ex, eyeY - 6 * (open < 0.18 ? -0.3 : 1), ex + 6, eyeY + 1);
      stroke(ctx, look.iris, 2.6);
      continue;
    }
    const o = Math.min(open, 1);
    if (child) {
      const rx = 6.2;
      const ry = 7.6 * open;
      const iris = ctx.createRadialGradient(ex, eyeY + 2.6, 0, ex, eyeY + 2.6, rx * 1.3);
      iris.addColorStop(0, mix(look.iris, "#9A6A45", 0.55));
      iris.addColorStop(1, look.iris);
      oval(ctx, ex - rx, eyeY - ry, rx * 2, ry * 2);
      fill(ctx, iris);
      if (open > 0.5) {
        arc(ctx, 215, 110, ex - rx - 0.6, eyeY - ry - 0.8, rx * 2 + 1.2, ry * 2 + 1.2);
        stroke(ctx, look.hair, 1.2);
      }
      circle(ctx, 2.1 * o, [ex + 2.2, eyeY - 2.6 * open]);
      fill(ctx, WHITE);
      circle(ctx, o, [ex - 2.2, eyeY + 2.8 * open]);
      fill(ctx, WHITE);
    } else {
      const rx = 5.2;
      const ry = 3.7 * open;
      oval(ctx, ex - rx, eyeY - ry, rx * 2, ry * 2);
      fill(ctx, "#F6EFE6");
      const irisY = eyeY + 0.3 + pose.gaze * 1.2;
      const ir = Math.min(3.4, ry + 0.2);
      circle(ctx, ir, [ex, irisY]);
      fill(ctx, mix(look.iris, "#8A5A36", 0.35));
      circle(ctx, ir * 0.55, [ex, irisY]);
      fill(ctx, look.iris);
      circle(ctx, 1, [ex + 1.1, irisY - 1.2]);
      fill(ctx, WHITE);
      arc(ctx, 195, 150, ex - rx - 0.4, eyeY - ry - 0.6, rx * 2 + 0.8, ry * 2 + 1.2);
      stroke(ctx, look.hair, 1.7);
    }
    if (f.eyeSmile > 0.1) {
      oval(ctx, ex - 7, eyeY + 7.5 - f.eyeSmile * 6, 14, 8);
      fill(ctx, look.skin);
    }
  }

  if (child) {
    oval(ctx, fx - 31, fy + 8, 14, 8);
    fill(ctx, look.blush);
    oval(ctx, fx + 17, fy + 8, 14, 8);
    fill(ctx, look.blush);
  }
  const noseY = fy + (child ? 8 : 9);
  ctx.beginPath();
  ctx.moveTo(fx - 2.5, noseY + 2);
  ctx.quadraticCurveTo(fx, noseY + 4.5, fx + 2.5, noseY + 2);
  stroke(ctx, look.line, 1.6, "round", 0.7);
  if (!child) line(ctx, look.skinShade, [fx + 1, noseY - 9], [fx + 2, noseY + 1], 1.6);

  drawMouth(ctx, fx, fy + (child ? 19 : 20), f, look, child);
}

function drawHair(ctx: Ctx, b: Build, look: Look, child: boolean, turn: number) {
  const hx = b.headX + turn * 2;
  const top = b.headY - b.headRy;
  const rx = b.headRx;
  const y = b.headY;
  ctx.beginPath();
  if (child) {
    ctx.moveTo(hx - rx - 1, y + 4);
    ctx.bezierCurveTo(hx - rx - 6, top - 20, hx + rx + 6, top - 20, hx + rx + 1, y + 4);
    ctx.lineTo(hx + rx - 3, y - 12);
    ctx.quadraticCurveTo(hx + rx - 12, y - 10, hx + 14, y - 20);
    ctx.quadraticCurveTo(hx + 8, y - 13, hx - 2, y - 20);
    ctx.quadraticCurveTo(hx - 10, y - 13, hx - 17, y - 21);
    ctx.quadraticCurveTo(hx - rx + 10, y - 10, hx - rx + 3, y - 12);
    ctx.closePath();
  } else {
    ctx.moveTo(hx - rx - 1, y - 2);
    ctx.bezierCurveTo(hx - rx - 5, top - 19, hx + rx + 5, top - 19, hx + rx + 1, y - 2);
    ctx.lineTo(hx + rx - 2, y - 12);
    ctx.quadraticCurveTo(hx + 14, y - 27, hx - 7, y - 23);
    ctx.quadraticCurveTo(hx - rx + 6, y - 21, hx - rx + 2, y - 10);
    ctx.closePath();
  }
  const g = ctx.createLinearGradient(0, top - (child ? 20 : 19), 0, y);
  g.addColorStop(0, look.hairShine);
  g.addColorStop(0.5, look.hair);
  g.addColorStop(1, look.hair);
  fill(ctx, g);
  if (child) {
    ctx.beginPath();
    ctx.moveTo(hx - 2, top + 2);
    ctx.quadraticCurveTo(hx + 4, top - 11, hx + 12, top - 6);
    ctx.quadraticCurveTo(hx + 5, top - 4, hx + 4, top + 3);
    ctx.closePath();
    fill(ctx, look.hair);
    arc(ctx, 215, 50, hx - rx * 0.55, top + 5, rx * 0.9, 22);
    stroke(ctx, look.hairShine, 2.6);
  } else {
    arc(ctx, 200, 55, hx - rx * 0.7, top - 4, rx * 1.1, 26);
    stroke(ctx, look.hairShine, 2);
  }
}

function drawMouth(ctx: Ctx, x: number, y: number, f: Face, look: Look, child: boolean) {
  const w = child ? 8.5 : 7.5;
  if (f.mouthRound > 0.5) {
    oval(ctx, x - 3.6, y - 4, 7.2, 8.5);
    fill(ctx, look.lip);
    return;
  }
  const s = f.smile;
  const corner = y - s * 2.2;
  ctx.beginPath();
  if (f.mouthOpen > 0.04) {
    const open = f.mouthOpen * 9;
    ctx.moveTo(x - w, corner);
    ctx.quadraticCurveTo(x, y + s * 3 - 1, x + w, corner);
    ctx.quadraticCurveTo(x, y + s * 6.5 + open, x - w, corner);
    ctx.closePath();
    fill(ctx, look.lip);
    oval(ctx, x - w * 0.45, y + s * 2 + open * 0.45, w * 0.9, open * 0.45 + 1);
    fill(ctx, "#E58A86");
  } else {
    ctx.moveTo(x - w, corner);
    ctx.quadraticCurveTo(x, y + s * 6.5, x + w, corner);
    stroke(ctx, look.lip, 2.4);
  }
}

/** A bone as a quad tapering from width [wa] at [a] to [wb] at [b]. */
function quad(ctx: Ctx, a: Pt, b: Pt, wa: number, wb: number, color: string) {
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  const len = Math.max(Math.hypot(dx, dy), 1e-3);
  const nx = -dy / len;
  const ny = dx / len;
  ctx.beginPath();
  ctx.moveTo(a[0] + (nx * wa) / 2, a[1] + (ny * wa) / 2);
  ctx.lineTo(b[0] + (nx * wb) / 2, b[1] + (ny * wb) / 2);
  ctx.lineTo(b[0] - (nx * wb) / 2, b[1] - (ny * wb) / 2);
  ctx.lineTo(a[0] - (nx * wa) / 2, a[1] - (ny * wa) / 2);
  ctx.closePath();
  fill(ctx, color);
}
function dot(ctx: Ctx, color: string, r: number, c: Pt) {
  circle(ctx, r, c);
  fill(ctx, color);
}

function drawArm(ctx: Ctx, b: Build, look: Look, h: HandPose, side: Side, shift: Pt) {
  const [sx0, sy0] = shoulder(b, side);
  const s: Pt = [sx0 + shift[0], sy0 + shift[1]];
  const e = elbowAt(h, s[0], s[1], b, side);
  const w: Pt = [h.x, h.y];
  const aw = b.armWidth;
  const wrist = 0.62 * 13 * b.handScale;
  const angle = Number.isNaN(h.angle) ? (Math.atan2(w[0] - e[0], -(w[1] - e[1])) * 180) / Math.PI : h.angle;
  const hand = layoutHand(h, side);
  const rim = alpha(look.outline, 0.9);
  const frame = (draw: () => void) => {
    ctx.save();
    ctx.translate(w[0], w[1]);
    ctx.rotate(rad(angle));
    ctx.scale(b.handScale, b.handScale);
    draw();
    ctx.restore();
  };

  const fore = look.longSleeves ? lerpPt(e, w, 0.15) : e;
  const foreW = aw * 0.95;
  quad(ctx, fore, w, foreW + 3, wrist + 3, HALO);
  dot(ctx, HALO, wrist / 2 + 1.5, w);
  frame(() => drawHandHalo(ctx, hand));
  if (look.longSleeves) {
    quad(ctx, fore, w, foreW + 1.6, wrist + 1.6, rim);
    dot(ctx, rim, wrist / 2 + 0.8, w);
    quad(ctx, fore, w, foreW, wrist, look.skin);
    dot(ctx, look.skin, wrist / 2, w);
    const sleeve = mix(look.top, WHITE, 0.1);
    const cuff = lerpPt(e, w, 0.22);
    quad(ctx, s, e, aw * 1.2 + 4.4, aw + 4.4, look.topShade);
    dot(ctx, look.topShade, aw / 2 + 2.2, e);
    quad(ctx, e, cuff, aw + 4.4, foreW + 4.4, look.topShade);
    quad(ctx, s, e, aw * 1.2 + 2, aw + 2, sleeve);
    dot(ctx, sleeve, aw / 2 + 1, e);
    quad(ctx, e, cuff, aw + 2, foreW + 2, sleeve);
    const len = Math.max(Math.hypot(w[0] - e[0], w[1] - e[1]), 1e-3);
    const band = lerpPt(e, w, Math.max(0.22 - 4 / len, 0.05));
    quad(ctx, band, cuff, foreW + 4.4, foreW + 4.4, look.topShade);
    quad(ctx, band, cuff, foreW + 3, foreW + 3, look.trim);
  } else {
    quad(ctx, s, e, aw * 1.2 + 2.4, aw + 2.4, rim);
    dot(ctx, rim, aw / 2 + 1.2, e);
    quad(ctx, e, w, foreW + 1.6, wrist + 1.6, rim);
    dot(ctx, rim, wrist / 2 + 0.8, w);
    quad(ctx, s, e, aw * 1.2, aw, look.skin);
    dot(ctx, look.skin, aw / 2, e);
    quad(ctx, e, w, foreW, wrist, look.skin);
    dot(ctx, look.skin, wrist / 2, w);
    const sleeveEnd = lerpPt(s, e, 0.5);
    line(ctx, look.topShade, s, sleeveEnd, aw * 1.2 + 4);
    line(ctx, look.top, s, sleeveEnd, aw * 1.2 + 2.5);
    line(ctx, look.topShade, lerpPt(s, e, 0.45), sleeveEnd, aw * 1.2 + 2.5, "butt", 0.5);
  }
  if (h.prop === "stick") {
    line(ctx, "#6B4423", [w[0] + 1, w[1] - 6], [w[0] + 4, 262], 5);
    line(ctx, "#8A5A33", [w[0] + 1, w[1] - 6], [w[0] + 4, 262], 2);
  }
  frame(() => drawHand(ctx, look, h.shape, hand));
}

interface Seg {
  a: Pt;
  b: Pt;
  width: number;
  behind: boolean;
  shade: number;
}
interface Hand {
  frontal: boolean;
  palmFacing: boolean;
  thumbSide: number;
  pw: number;
  nearThumb: boolean;
  palmSide: number;
  /** Index to pinky, then the thumb. */
  fingers: Seg[][];
}

const fingerCurl = (s: HandShape, f: number) => [s.index, s.middle, s.ring, s.pinky, s.thumb][f];

function layoutHand(h: HandPose, side: Side): Hand {
  const roll = clamp(h.roll, -1, 1);
  const shape = h.shape;
  const hand: Hand = {
    frontal: Math.abs(roll) >= 0.35,
    palmFacing: roll > 0,
    thumbSide: 1,
    pw: 13,
    nearThumb: roll >= 0,
    palmSide: -side,
    fingers: [],
  };
  if (hand.frontal) {
    const thumbSide = hand.palmFacing ? -side : side;
    const wf = lerp(0.6, 1, clamp((Math.abs(roll) - 0.35) / 0.65, 0, 1));
    const pw = 13 * wf;
    hand.thumbSide = thumbSide;
    hand.pw = pw;
    for (let f = 0; f < 4; f++) {
      const bx = thumbSide * (pw / 2 - 1.8 - (f * (pw - 3.6)) / 3);
      const dir = rad(shape.spread * (f - 1.5) * -9 * thumbSide);
      hand.fingers.push(
        frontalChain([bx, -PH + 1.5], dir, LENGTHS[f], WIDTHS[f] * (0.82 + 0.18 * wf), fingerCurl(shape, f), hand.palmFacing, false),
      );
    }
    const beside = shape.thumbAcross < 0.5;
    let deg = 52 - shape.thumbAcross * 112;
    if (beside) deg = Math.max(deg, shape.spread * 13.5 + 28);
    hand.fingers.push(
      frontalChain(
        [thumbSide * (pw / 2 - 0.5), beside ? -2 : -3],
        rad(thumbSide * deg),
        8.8,
        3.7,
        shape.thumb * 0.6,
        hand.palmFacing,
        true,
      ),
    );
  } else {
    const ps = hand.palmSide;
    for (let f = 0; f < 4; f++) {
      const depth = hand.nearThumb ? 3 - f : f;
      const dir = rad(shape.spread * (f - 1.5) * 4);
      const base: Pt = [ps * (0.6 - f * 0.35) * (hand.nearThumb ? 1 : -1), -PH + 1.2 + f * 0.8];
      hand.fingers.push(sideChain(base, dir, LENGTHS[f], 3.2, fingerCurl(shape, f), ps, 1, (3 - depth) * 0.12));
    }
    hand.fingers.push(sideChain([ps * 2.4, -3.5], rad(ps * (28 + shape.thumbAcross * 30)), 8.6, 3.5, shape.thumb, ps, 0.5, 0));
  }
  return hand;
}

function frontalChain(base: Pt, dir: number, length: number, width: number, curl: number, palmFacing: boolean, thumb: boolean): Seg[] {
  const fractions = thumb ? [0.55, 0.45] : [0.46, 0.3, 0.24];
  const bends = thumb ? [40, 80] : [85, 100, 72];
  const ux = Math.sin(dir);
  const uy = -Math.cos(dir);
  let p = base;
  let phi = 0;
  return fractions.map((fr, j) => {
    phi += bends[j] * curl;
    const len = length * fr * Math.cos(rad(phi));
    const next: Pt = [p[0] + ux * len, p[1] + uy * len];
    // Folded segments face away from a back-of-hand view, so they go behind the palm.
    const seg = { a: p, b: next, width: width * TAPER[j], behind: !palmFacing && phi > 95 && !thumb, shade: phi > 95 ? 0.18 : 0 };
    p = next;
    return seg;
  });
}

function sideChain(
  base: Pt,
  dir: number,
  length: number,
  width: number,
  curl: number,
  palmSide: number,
  scale: number,
  shade: number,
): Seg[] {
  const fractions = [0.46, 0.3, 0.24];
  const bends = [80, 95, 70];
  let p = base;
  let a = dir;
  return fractions.map((fr, j) => {
    a += rad(palmSide * bends[j] * curl);
    const len = length * fr * scale;
    const next: Pt = [p[0] + Math.sin(a) * len, p[1] - Math.cos(a) * len];
    const seg = { a: p, b: next, width: width * TAPER[j], behind: false, shade };
    p = next;
    return seg;
  });
}

function drawFinger(ctx: Ctx, look: Look, segs: Seg[], tone: string) {
  const rim = alpha(look.outline, 0.9);
  for (const s of segs) line(ctx, rim, s.a, s.b, s.width + 1.6);
  for (const s of segs) line(ctx, s.shade > 0 ? mix(tone, look.skinShade, Math.min(s.shade * 2.5, 1)) : tone, s.a, s.b, s.width);
}

function thenar(ctx: Ctx, hand: Hand, color: string, grow: number) {
  const cx = hand.thumbSide * hand.pw * 0.32;
  const w = hand.pw * 0.45;
  oval(ctx, cx - w / 2 - grow, -7.5 - grow, w + grow * 2, 7 + grow * 2);
  fill(ctx, color);
}

function drawHandHalo(ctx: Ctx, hand: Hand) {
  if (hand.frontal) {
    roundRect(ctx, -hand.pw / 2 - 1.5, -PH - 1.5, hand.pw + 3, PH + 3, 5.5);
    fill(ctx, HALO);
    thenar(ctx, hand, HALO, 1.5);
  } else {
    roundRect(ctx, -4.7, -PH - 1.5, 9.4, PH + 3, 4.5);
    fill(ctx, HALO);
  }
  for (const segs of hand.fingers) for (const s of segs) line(ctx, HALO, s.a, s.b, s.width + 3);
}

function crease(ctx: Ctx, look: Look, s: Seg, al: number) {
  const dx = s.b[0] - s.a[0];
  const dy = s.b[1] - s.a[1];
  const len = Math.hypot(dx, dy);
  if (len < 0.3) return;
  const half = (0.55 * s.width) / 2;
  const n: Pt = [(-dy / len) * half, (dx / len) * half];
  line(ctx, look.skinShade, [s.a[0] - n[0], s.a[1] - n[1]], [s.a[0] + n[0], s.a[1] + n[1]], 0.35, "round", al);
}

function nail(ctx: Ctx, look: Look, s: Seg) {
  const len = Math.hypot(s.b[0] - s.a[0], s.b[1] - s.a[1]);
  if (len < 0.5) return;
  const nw = 0.6 * s.width;
  let t0 = 0.35 + nw / 2 / len;
  let t1 = 0.92 - nw / 2 / len;
  if (t1 < t0) t0 = t1 = (t0 + t1) / 2;
  const p0 = lerpPt(s.a, s.b, t0);
  const p1 = lerpPt(s.a, s.b, t1);
  line(ctx, look.skinShade, p0, p1, nw + 0.5, "round", 0.6);
  line(ctx, mix(look.skin, "#FFE9DA", 0.55), p0, p1, nw);
  circle(ctx, 0.18 * s.width, lerpPt(s.a, s.b, 0.8));
  fill(ctx, WHITE, 0.5);
}

function drawHand(ctx: Ctx, look: Look, shape: HandShape, hand: Hand) {
  const rim = alpha(look.outline, 0.9);
  const palmTone = mix(look.skin, "#F3CFAE", 0.45);
  const thumb = hand.fingers[4];
  if (!hand.frontal) {
    roundRect(ctx, -4, -PH - 0.8, 8, PH + 1.6, 3.8);
    fill(ctx, rim);
    roundRect(ctx, -3.2, -PH, 6.4, PH, 3);
    fill(ctx, look.skin);
    roundRect(ctx, hand.palmSide > 0 ? 2 : -3.2, -PH + 1, 1.2, PH - 2, 0.6);
    fill(ctx, palmTone);
    if (!hand.nearThumb) drawFinger(ctx, look, thumb, look.skin);
    for (let d = 0; d < 4; d++) drawFinger(ctx, look, hand.fingers[hand.nearThumb ? 3 - d : d], look.skin);
    if (hand.nearThumb) drawFinger(ctx, look, thumb, look.skin);
    return;
  }
  const across = shape.thumbAcross > 0.5;
  const pw = hand.pw;
  if (!hand.palmFacing) {
    if (!across) {
      drawFinger(ctx, look, thumb, look.skin);
      if (shape.thumb < 0.5) nail(ctx, look, thumb[1]);
    }
    for (let f = 0; f < 4; f++)
      drawFinger(
        ctx,
        look,
        hand.fingers[f].filter((s) => s.behind),
        look.skin,
      );
    thenar(ctx, hand, rim, 0.8);
    thenar(ctx, hand, look.skin, 0);
    roundRect(ctx, -pw / 2 - 0.8, -PH - 0.8, pw + 1.6, PH + 1.6, 4.8);
    fill(ctx, rim);
    const back = ctx.createLinearGradient(0, -PH, 0, 0);
    back.addColorStop(0, look.skin);
    back.addColorStop(0.5, look.skin);
    back.addColorStop(1, mix(look.skin, look.skinShade, 0.45));
    roundRect(ctx, -pw / 2, -PH, pw, PH, 4);
    fill(ctx, back);
    // Seen from the back, a thumb across the palm is hidden behind the fingers.
    if (across) drawFinger(ctx, look, thumb, look.skin);
    for (let f = 3; f >= 0; f--)
      drawFinger(
        ctx,
        look,
        hand.fingers[f].filter((s) => !s.behind),
        look.skin,
      );
    for (let f = 0; f < 4; f++) {
      const segs = hand.fingers[f];
      const c = fingerCurl(shape, f);
      const base = segs[0].a;
      if (c > 0.45) {
        dot(ctx, look.skin, 1.9, [base[0], -PH + 1]);
        stroke(ctx, look.line, 0.5, "butt", 0.5);
      }
      dot(ctx, mix(look.skin, WHITE, 0.18), 0.45 * segs[0].width, [base[0], c > 0.45 ? -PH + 1 : base[1]]);
      if (c < 0.35) {
        crease(ctx, look, segs[1], 0.55);
        crease(ctx, look, segs[2], 0.55);
        nail(ctx, look, segs[2]);
      }
    }
  } else {
    if (!across) drawFinger(ctx, look, thumb, palmTone);
    thenar(ctx, hand, rim, 0.8);
    thenar(ctx, hand, palmTone, 0);
    roundRect(ctx, -pw / 2 - 0.8, -PH - 0.8, pw + 1.6, PH + 1.6, 4.8);
    fill(ctx, rim);
    roundRect(ctx, -pw / 2, -PH, pw, PH, 4);
    fill(ctx, palmTone);
    arc(ctx, 200, 110, -pw * 0.35, -PH * 0.75, pw * 0.7, PH * 0.9);
    stroke(ctx, look.skinShade, 0.9, "butt", 0.8);
    for (let f = 3; f >= 0; f--) {
      const segs = hand.fingers[f];
      drawFinger(ctx, look, segs, palmTone);
      if (fingerCurl(shape, f) < 0.6) {
        crease(ctx, look, segs[1], 0.4);
        crease(ctx, look, segs[2], 0.4);
        const w = segs[2].width;
        const c = lerpPt(segs[2].a, segs[2].b, 0.6);
        oval(ctx, c[0] - 0.3 * w, c[1] - 0.4 * w, 0.6 * w, 0.8 * w);
        fill(ctx, mix(palmTone, WHITE, 0.15));
      }
    }
    if (across) drawFinger(ctx, look, thumb, palmTone);
  }
}
