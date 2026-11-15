import { WIDTH, HEIGHT } from '../core/constants.js'

/* global PIXI */

/**
 * Byte Club viewer.
 *
 * The whole scene is drawn with Canvas 2D on an offscreen canvas that has the real size of the player's canvas, and is
 * shown through one PIXI sprite. What is drawn depends only on (key frame, progress): every effect is computed from
 * "match time now" minus "match time of the event", with timelines built in handleFrameData.
 *
 * Time unit of the drawing code: a quarter of a turn (25 ms). Key frame n at progress u is the time 4 * (n - 1 + u).
 * Lengths: metres in the world (x along the ring, y up, z away from the camera), 1920x1080 units on screen.
 */

// ---------- viewer options (gear menu of the player): config.js exposes them, the module reads them
export const viewerOptions = {
  shapes: false,
  onChange: null,
  set (key, value) {
    this[key] = value
    if (typeof this.onChange === 'function') {
      try { this.onChange() } catch (e) { /* an option must never break the player */ }
    }
  }
}

// ---------- what the two modules of this file share: the fight view, and whether the overlay above the end screen is there
const shared = { fight: null, overlay: false }

// ---------- data: numbers are packed with these 86 characters (see FightModule.java)
const DIGITS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%()*+,-./:;?@[]^_{|}~'
const BASE = DIGITS.length
const HALF = BASE >> 1
const VALUE = {}
for (let i = 0; i < BASE; i++) VALUE[DIGITS[i]] = i

/** Unpacks the 4 quarter-turns of a turn: each value is the change of a coordinate since the quarter before. */
export function unpack (text, from) {
  const n = from.length
  const out = []
  let last = from
  let k = 0
  for (let q = 0; q < 4; q++) {
    const cur = new Int16Array(n)
    for (let i = 0; i < n; i++) {
      let z = VALUE[text[k++]]
      if (z >= HALF) z = HALF + (z - HALF) * BASE + VALUE[text[k++]]
      cur[i] = last[i] + ((z & 1) ? -((z + 1) >> 1) : (z >> 1))
    }
    out.push(cur)
    last = cur
  }
  return out
}

// ---------- constants
const W = WIDTH
const H = HEIGHT
const K = H / 640                         // the scene was designed on a canvas 640 units high
const FLOOR = Math.round(545 * K)         // screen y of the mat on the fighting line
const S0 = 225                            // zoom (px per metre) the line widths were designed for
const SREF = S0 * K                       // the same zoom in 1080 units
const FONT = 'system-ui, sans-serif'
const NAMES = ['head', 'neck', 'chest', 'waist', 'pelvis', 'lsh', 'lel', 'lfi', 'rsh', 'rel', 'rfi', 'lkn', 'lan', 'lhe', 'lto', 'rkn', 'ran', 'rhe', 'rto']
const PELVIS = 4
// looks: skin 0..7 very light to very dark, hair 0..9 (9 = bald)
const SKIN = ['#f6dccb', '#f0c8a6', '#e8b682', '#d39c6c', '#b98053', '#9e6841', '#7a4c2c', '#573420']
const HAIR = ['#151010', '#2b2116', '#4a3220', '#74512f', '#a57a45', '#d4b06a', '#8a3a1c', '#b4552a', '#a9a9a9', null]
const DEFAULT_LOOK = [[2, 1], [5, 0]]
const DEFAULT_COLORS = ['#e8463c', '#3c7de8']
// camera: D metres in front of the fighting line, H metres above the mat; the mat runs from zf in front to zb behind
const CAM = { D: 7, H: 1.45, zb: 2.3, zf: 0.75 }
const OUTLINE = 'rgba(24,16,22,0.8)'
const OUTLINE_FAR = 'rgba(24,16,22,0.55)'
const GOLD = '#e3b341'
const TAIL = 90                           // quarter-turns of cosmetic time after the very end (crowd, fading effects)
const DEFAULT_POS = [-839, 1625, -907, 1437, -938, 1259, -954, 1080, -954, 940, -885, 1308, -585, 1306, -511, 1576, -942, 1311, -643, 1283, -690, 1559, -748, 540, -754, 90, -804, 30, -584, 30, -968, 490, -1174, 90, -1224, 30, -1004, 30,
  839, 1625, 907, 1437, 938, 1259, 954, 1080, 954, 940, 885, 1308, 585, 1306, 511, 1576, 942, 1311, 643, 1283, 690, 1559, 748, 540, 754, 90, 804, 30, 584, 30, 968, 490, 1174, 90, 1224, 30, 1004, 30]

// ---------- drawing state (one scene is drawn at a time)
let g = null                              // the 2D context being drawn
let PXS = 1                               // real pixels per screen unit, x
let PYS = 1                               // and y
let S = SREF                              // zoom: px per metre on the fighting line
let camX = 0                              // world x at the centre of the screen
let horizonY = FLOOR - CAM.H * SREF
let shX = 0                               // camera shake in screen units
let shY = 0
let mode = 'fill'                         // each body group is drawn twice: outline pass, then fill pass
let outline = null                        // outline pass of an opaque group: its colour, for the limbs (drawn as a wider shape, cheaper than a stroke)
const TONE = [makeTone(DEFAULT_COLORS[0], 0), makeTone(DEFAULT_COLORS[1], 1)]

// ---------- helpers
function rgb (hex) { const n = parseInt(hex.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255] }
const COL = new Map()
/** hex x k (warm shadows: red fades slower than blue), mixed toward white by w, alpha a. */
function col (hex, k = 1, w = 0, a = 1) {
  const key = hex + '|' + k + '|' + w + '|' + a
  let r = COL.get(key)
  if (r !== undefined) return r
  const kk = k < 1 ? [Math.pow(k, 0.85), k, Math.pow(k, 1.15)] : [k, k, k]
  const c = rgb(hex).map((v, i) => Math.round(Math.min(255, v * kk[i]) * (1 - w) + 255 * w))
  r = a >= 1 ? 'rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ')' : 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',' + a + ')'
  if (COL.size > 4000) COL.clear()
  COL.set(key, r)
  return r
}
function hex2 (v) { return (v < 16 ? '0' : '') + v.toString(16) }
/** Same as col without alpha, but gives back a hex colour (usable as an input of col). */
function shade (hex, k = 1, w = 0) {
  const c = rgb(hex).map(v => Math.round(Math.min(255, v * k) * (1 - w) + 255 * w))
  return '#' + hex2(c[0]) + hex2(c[1]) + hex2(c[2])
}
function seed (s) { let x = (s * 9301 + 49297) % 233280; return () => (x = (x * 9301 + 49297) % 233280) / 233280 }
const clamp = (v, a, b) => Math.max(a, Math.min(b, v))
const num = (v, d) => (typeof v === 'number' && isFinite(v) ? v : d)      // a number of the data, or d when it is not one
function rrect (x, y, w, h, r) { if (g.roundRect) g.roundRect(x, y, w, h, r); else g.rect(x, y, w, h) }
function spacing (px) { if ('letterSpacing' in g) g.letterSpacing = px + 'px' }
function paint (style) { if (mode === 'stroke') g.stroke(); else { g.fillStyle = style; g.fill() } }
function group (fn, alpha, stroke) {
  g.save(); g.globalAlpha *= alpha === undefined ? 1 : alpha; g.lineJoin = 'round'; g.lineCap = 'round'
  g.strokeStyle = stroke || OUTLINE; g.lineWidth = 2.6 * S / S0
  outline = alpha === undefined || alpha >= 1 ? g.strokeStyle : null       // a see-through group keeps real strokes
  mode = 'stroke'; fn(); mode = 'fill'; fn()
  g.restore()
}
const X = x => W / 2 + (x - camX) * S     // fighting plane (z = 0)
const Y = y => FLOOR - y * S
function P (x, y, z) {                    // world (m) to screen, z = depth behind the fighting line
  const k = CAM.D / (CAM.D + z)
  return [W / 2 + (x - camX) * S * k, horizonY + (CAM.H - y) * S * k]
}
/**
 * Size of the offscreen canvas of the scene. Normally the size of the player's canvas. One case apart: the host makes that canvas twice
 * as fine as its place on the page (oversampling 2), so on a screen with one device pixel per CSS pixel, four pixels would be painted
 * for each pixel shown. Then the scene is drawn at half the size and shown with each pixel doubled, which the browser folds back into
 * the very same picture: same sharpness, a quarter of the painting. HALF_WHEN_DOUBLED = false goes back to the canvas size always.
 * Returns the size in pixels (w, h) and the pixels per screen unit (kx, ky); with an odd canvas size the last half pixel is cut off.
 */
const HALF_WHEN_DOUBLED = true
function drawingSize (canvasData) {
  const cw = clamp(Math.round(num(canvasData && canvasData.width, W)), 160, W)
  const ch = clamp(Math.round(num(canvasData && canvasData.height, H)), 90, H)
  let half = false
  try {
    const perScreenPixel = num(canvasData && canvasData.oversampling, 1) / Math.max(1, num(window.devicePixelRatio, 1))
    half = HALF_WHEN_DOUBLED && perScreenPixel >= 1.99 && cw >= 320 && ch >= 180
  } catch (e) { half = false }
  if (half) return { w: Math.ceil(cw / 2), h: Math.ceil(ch / 2), kx: cw / 2 / W, ky: ch / 2 / H, doubled: true }
  return { w: cw, h: ch, kx: cw / W, ky: ch / H, doubled: false }
}
/** Sets the transform of the scene: screen units, moved by the camera shake. */
function sceneTransform (sx, sy) { shX = sx; shY = sy; g.setTransform(PXS, 0, 0, PYS, sx * PXS, sy * PYS) }
const FIT = new Map()
/** The text cut with an ellipsis so that it fits in maxW (with the current font). */
function fit (text, maxW) {
  const key = g.font + '|' + maxW + '|' + text
  let r = FIT.get(key)
  if (r !== undefined) return r
  r = text
  if (g.measureText(r).width > maxW) {
    let n = r.length
    while (n > 1 && g.measureText(r.slice(0, n) + '…').width > maxW) n--
    r = r.slice(0, n) + '…'
  }
  if (FIT.size > 3000) FIT.clear()
  FIT.set(key, r)
  return r
}
function makeTone (hex, who) {
  return {
    color: hex,
    skin: SKIN[DEFAULT_LOOK[who][0]],
    hair: HAIR[DEFAULT_LOOK[who][1]],
    brow: HAIR[DEFAULT_LOOK[who][1]],
    bald: false,
    shorts: shade(hex, 0.86),
    trim: who === 0 ? '#f4f4f4' : '#f2cf6a',
    glove: shade(hex, 0.8),
    label: shade(hex, 1, 0.42),
    hp: [shade(hex, 1, 0.26), shade(hex, 0.8)],
    post: shade(hex, 0.88)
  }
}
/** Skin and hair of the two fighters; a missing or out-of-range value falls back to the fighter's default. */
function applyLooks (looks) {
  for (let who = 0; who < 2; who++) {
    const data = Array.isArray(looks) && Array.isArray(looks[who]) ? looks[who] : []
    const pick = j => Number.isInteger(data[j]) && data[j] >= 0 && data[j] <= (j === 0 ? 7 : 9) ? data[j] : DEFAULT_LOOK[who][j]
    const s = pick(0); const h = pick(1); const tone = TONE[who]
    tone.skin = SKIN[s]; tone.bald = h === 9; tone.hair = tone.bald ? null : HAIR[h]; tone.brow = tone.bald ? HAIR[1] : HAIR[h]
  }
}
/** The 19 points of a fighter, in metres, at `local` quarter-turns (0..4) inside a turn. */
function poseOf (turn, local, who) {
  const k = Math.min(3, Math.max(0, Math.floor(local))); const u = clamp(local - k, 0, 1)
  const a = k === 0 ? turn.start : turn.sub[k - 1]; const b = turn.sub[k]; const o = {}
  for (let i = 0; i < 19; i++) {
    const j = (who * 19 + i) * 2
    o[NAMES[i]] = [(a[j] + (b[j] - a[j]) * u) / 1000, (a[j + 1] + (b[j + 1] - a[j + 1]) * u) / 1000]
  }
  return o
}

// ---------- body parts (all take screen points)
function limb (a, b, ra, rb, hex, dim, bulge, bulgeAt, side, facing) {
  const dx = b[0] - a[0]; const dy = b[1] - a[1]; const L = Math.hypot(dx, dy) || 1e-6; const ux = dx / L; const uy = dy / L
  let nx = -uy; let ny = ux
  if ((nx * facing < 0) === (side > 0)) { nx = -nx; ny = -ny }          // muscle bulge toward the fighter's front
  const wide = mode === 'stroke' && outline ? 1.3 * S / S0 : 0            // outline pass: the same shape, wider by half the line
  const RA = ra * S + wide; const RB = rb * S + wide; const BU = bulge * S
  const m = [a[0] + dx * bulgeAt, a[1] + dy * bulgeAt]; const rm = RA + (RB - RA) * bulgeAt
  let style = null
  if (mode === 'fill') {                                                  // lit from above and from the front: a tube gradient across the limb
    let lx = -uy; let ly = ux; if (-ly + 0.45 * lx * facing < 0) { lx = -lx; ly = -ly }
    const R = Math.max(RA, RB) + BU; const mx = (a[0] + b[0]) / 2; const my = (a[1] + b[1]) / 2
    const gr = g.createLinearGradient(mx + lx * R, my + ly * R, mx - lx * R, my - ly * R)
    gr.addColorStop(0, col(hex, 0.9 * dim)); gr.addColorStop(0.3, col(hex, dim, 0.14)); gr.addColorStop(0.72, col(hex, 0.95 * dim)); gr.addColorStop(1, col(hex, 0.58 * dim))
    style = gr
  }
  g.beginPath()                                                           // the body of the limb
  g.moveTo(a[0] + nx * RA, a[1] + ny * RA)
  g.quadraticCurveTo(m[0] + nx * (rm + 2 * BU), m[1] + ny * (rm + 2 * BU), b[0] + nx * RB, b[1] + ny * RB)
  g.lineTo(b[0] - nx * RB, b[1] - ny * RB)
  g.quadraticCurveTo(m[0] - nx * (rm + 0.6 * BU), m[1] - ny * (rm + 0.6 * BU), a[0] - nx * RA, a[1] - ny * RA)
  g.closePath()
  if ((wide || mode === 'fill') && g.globalAlpha >= 1) {                  // opaque: the body and its two round ends as three simple shapes,
    g.fillStyle = wide ? outline : style; g.fill()                        // which a graphics card paints much faster than one shape made of three
    g.beginPath(); g.arc(a[0], a[1], RA, 0, 7); g.fill()
    g.beginPath(); g.arc(b[0], b[1], RB, 0, 7); g.fill()
    return
  }
  // see-through: one outline for the three, all wound the same way so that they add up (one fill, one stroke, no darker overlap)
  const ccw = nx * uy - ny * ux < 0
  g.moveTo(a[0] + RA, a[1]); g.arc(a[0], a[1], RA, 0, ccw ? -7 : 7, ccw); g.closePath()
  g.moveTo(b[0] + RB, b[1]); g.arc(b[0], b[1], RB, 0, ccw ? -7 : 7, ccw); g.closePath()
  if (wide) { g.fillStyle = outline; g.fill() } else paint(style)
}
function ball (c, r, hex, dim) {
  let style = null
  if (mode === 'fill') {
    const gr = g.createRadialGradient(c[0], c[1] - r * 0.4, r * 0.1, c[0], c[1], r * 1.05)
    gr.addColorStop(0, col(hex, dim, 0.16)); gr.addColorStop(0.7, col(hex, dim)); gr.addColorStop(1, col(hex, 0.62 * dim)); style = gr
  }
  g.beginPath(); g.arc(c[0], c[1], r, 0, 7); paint(style)
}
function foot (an, he, to, hex, dim) {
  let ux = to[0] - he[0]; let uy = to[1] - he[1]; const L = Math.hypot(ux, uy) || 1e-6; ux /= L; uy /= L
  let dx = -uy; let dy = ux                                               // toward the sole: the side away from the ankle
  const side = (an[0] - (he[0] + to[0]) / 2) * dx + (an[1] - (he[1] + to[1]) / 2) * dy
  if (side > 0 || (Math.abs(side) < 1e-6 && dy < 0)) { dx = -dx; dy = -dy }
  const at = (q, a, b) => [q[0] + ux * a + dx * b * S, q[1] + uy * a + dy * b * S]   // a px along the foot, b metres toward the sole
  const top = at(an, 0, -0.012); const c1 = at(he, -0.12 * L, -0.035); const heel = at(he, 0, 0.03); const toe = at(to, 0, 0.03)
  const c2 = at(to, 0.18 * L, -0.005); const tip = at(to, 0, -0.032); const c3 = at([(an[0] + to[0]) / 2, (an[1] + to[1]) / 2], 0, -0.035)
  g.beginPath()
  g.moveTo(top[0], top[1])
  g.quadraticCurveTo(c1[0], c1[1], heel[0], heel[1])
  g.lineTo(toe[0], toe[1])
  g.quadraticCurveTo(c2[0], c2[1], tip[0], tip[1])
  g.quadraticCurveTo(c3[0], c3[1], top[0], top[1])
  g.closePath()
  let style = null
  if (mode === 'fill') {
    const y0 = Math.min(an[1], he[1], to[1]) - 0.04 * S; const y1 = Math.max(an[1], he[1], to[1]) + 0.03 * S
    const gr = g.createLinearGradient(0, y0, 0, y1); gr.addColorStop(0, col(hex, dim, 0.1)); gr.addColorStop(1, col(hex, 0.6 * dim)); style = gr
  }
  paint(style)
}
function glove (el, fi, hex, dim) {
  const ang = Math.atan2(fi[1] - el[1], fi[0] - el[0])
  g.save(); g.translate(fi[0], fi[1]); g.rotate(ang)
  const w = 0.14 * S; const h = 0.115 * S; const ux = -Math.sin(ang); const uy = -Math.cos(ang)       // screen-up in the glove's frame
  let style = null
  if (mode === 'fill') {
    const gr = g.createLinearGradient(ux * h * 0.6, uy * h * 0.6, -ux * h * 0.6, -uy * h * 0.6)
    gr.addColorStop(0, col(hex, dim, 0.25)); gr.addColorStop(0.45, col(hex, dim)); gr.addColorStop(1, col(hex, 0.5 * dim)); style = gr
  }
  g.beginPath(); rrect(-w / 2 + 0.012 * S, -h / 2, w, h, 0.045 * S); paint(style)
  if (mode === 'fill') {
    g.fillStyle = col('#f4f1ea', dim); g.fillRect(-w / 2 + 0.002 * S, -h * 0.42, 0.028 * S, h * 0.84)          // cuff
    g.strokeStyle = col(hex, 0.45 * dim); g.lineWidth = 1 * S / S0; g.beginPath(); g.moveTo(-w / 2 + 0.03 * S, -h * 0.42); g.lineTo(-w / 2 + 0.03 * S, h * 0.42); g.stroke()
  }
  g.restore()
  if (mode === 'fill') {                                                  // highlight on top of the fist
    const hc = [fi[0] + Math.cos(ang) * 0.012 * S, fi[1] + Math.sin(ang) * 0.012 * S - h * 0.3]
    g.fillStyle = 'rgba(255,255,255,0.22)'; g.beginPath(); g.ellipse(hc[0], hc[1], 0.032 * S, 0.014 * S, 0, 0, 7); g.fill()
  }
}
function spine (p, facing) {
  const sp = [p.pelvis, p.waist, p.chest, p.neck]
  const depthBack = [0.115, 0.09, 0.11, 0.06]; const depthFront = [0.09, 0.10, 0.125, 0.055]
  const nrm = i => {
    const a = sp[Math.max(0, i - 1)]; const b = sp[Math.min(3, i + 1)]
    const dx = b[0] - a[0]; const dy = b[1] - a[1]; const L = Math.hypot(dx, dy) || 1e-6
    let nx = -dy / L; let ny = dx / L
    if (nx * facing < 0) { nx = -nx; ny = -ny }
    return [nx, ny]
  }
  return {
    front: sp.map((q, i) => { const n = nrm(i); return [q[0] + n[0] * depthFront[i] * S, q[1] + n[1] * depthFront[i] * S] }),
    back: sp.map((q, i) => { const n = nrm(i); return [q[0] - n[0] * depthBack[i] * S, q[1] - n[1] * depthBack[i] * S] })
  }
}
function torso (p, tone, facing, sp) {
  const { front, back } = sp
  limb(p.neck, p.head, 0.058, 0.05, tone.skin, 1, 0.004, 0.5, 1, facing)           // neck
  g.beginPath()
  g.moveTo(back[0][0], back[0][1] + 0.03 * S)
  for (let i = 1; i < 4; i++) { const m = [(back[i - 1][0] + back[i][0]) / 2, (back[i - 1][1] + back[i][1]) / 2]; g.quadraticCurveTo(back[i - 1][0], back[i - 1][1], m[0], m[1]) }
  g.lineTo(back[3][0], back[3][1])
  g.quadraticCurveTo(p.neck[0], p.neck[1] - 0.07 * S, front[3][0], front[3][1])
  for (let i = 3; i > 0; i--) { const m = [(front[i][0] + front[i - 1][0]) / 2, (front[i][1] + front[i - 1][1]) / 2]; g.quadraticCurveTo(front[i][0], front[i][1], m[0], m[1]) }
  g.lineTo(front[0][0], front[0][1] + 0.03 * S)
  g.closePath()
  let style = null
  if (mode === 'fill') {
    const gr = g.createLinearGradient(back[2][0], back[2][1], front[2][0], front[2][1])
    gr.addColorStop(0, col(tone.skin, 0.64)); gr.addColorStop(0.42, col(tone.skin, 1, 0.1)); gr.addColorStop(0.8, col(tone.skin, 0.97)); gr.addColorStop(1, col(tone.skin, 0.8))
    style = gr
  }
  paint(style)
  if (mode === 'fill') {                                                  // pec line and abs, faint
    g.strokeStyle = col(tone.skin, 0.55, 0, 0.45); g.lineWidth = 1.4 * S / S0; g.lineCap = 'round'
    g.beginPath(); g.moveTo(front[2][0] - facing * 0.05 * S, front[2][1] + 0.03 * S)
    g.quadraticCurveTo(front[2][0], front[2][1] + 0.05 * S, front[2][0] - facing * 0.012 * S, front[2][1] - 0.01 * S); g.stroke()
    const c1 = [(front[2][0] + back[2][0]) / 2, (front[2][1] + back[2][1]) / 2]; const c2 = [(front[1][0] + back[1][0]) / 2, (front[1][1] + back[1][1]) / 2]
    g.beginPath(); g.moveTo(c1[0] + facing * 0.012 * S, c1[1] + 0.07 * S); g.lineTo(c2[0] + facing * 0.016 * S, c2[1] + 0.005 * S); g.stroke()
  }
}
function shorts (p, tone, facing, sp) {
  const { front, back } = sp
  g.beginPath()
  g.moveTo(back[1][0], back[1][1] + 0.02 * S); g.lineTo(front[1][0], front[1][1] + 0.02 * S)
  g.lineTo(front[0][0] + facing * 0.01 * S, front[0][1] + 0.12 * S); g.lineTo(back[0][0] - facing * 0.01 * S, back[0][1] + 0.13 * S); g.closePath()
  let style = null
  if (mode === 'fill') {
    const gr = g.createLinearGradient(back[1][0], back[1][1], front[1][0], front[1][1])
    gr.addColorStop(0, col(tone.shorts, 0.6)); gr.addColorStop(0.5, col(tone.shorts, 1.05)); gr.addColorStop(1, col(tone.shorts, 0.85)); style = gr
  }
  paint(style)
  if (mode === 'fill') {
    g.fillStyle = tone.trim; g.beginPath()                               // waistband
    g.moveTo(back[1][0], back[1][1] + 0.02 * S); g.lineTo(front[1][0], front[1][1] + 0.02 * S)
    g.lineTo(front[1][0], front[1][1] + 0.045 * S); g.lineTo(back[1][0], back[1][1] + 0.045 * S); g.closePath(); g.fill()
    const m1 = [(front[1][0] + back[1][0]) / 2, (front[1][1] + back[1][1]) / 2 + 0.045 * S]; const m0 = [(front[0][0] + back[0][0]) / 2, (front[0][1] + back[0][1]) / 2 + 0.125 * S]
    g.strokeStyle = col(tone.trim, 1, 0, 0.8); g.lineWidth = 2 * S / S0; g.lineCap = 'round'                    // side stripe
    g.beginPath(); g.moveTo(m1[0], m1[1]); g.lineTo(m0[0], m0[1]); g.stroke()
  }
}
function head (p, tone, facing) {
  const h = p.head; const n = p.neck
  const ux = (h[0] - n[0]); const uy = (h[1] - n[1]); const L = Math.hypot(ux, uy) || 1
  const up = [ux / L, uy / L]; const fwd = facing > 0 ? [-up[1], up[0]] : [up[1], -up[0]]
  const at = (a, b) => [h[0] + (fwd[0] * a + up[0] * b) * S, h[1] + (fwd[1] * a + up[1] * b) * S]
  const R = 0.098 * S
  let style = null
  if (mode === 'fill') {
    const c = at(0.03, 0.035); const gr = g.createRadialGradient(c[0], c[1], R * 0.1, h[0], h[1], R * 1.08)
    gr.addColorStop(0, col(tone.skin, 1, 0.16)); gr.addColorStop(0.62, col(tone.skin, 1)); gr.addColorStop(1, col(tone.skin, 0.64)); style = gr
  }
  g.beginPath(); g.arc(h[0], h[1], R, 0, 7); paint(style)
  const jaw = at(0.045, -0.055); g.beginPath(); g.ellipse(jaw[0], jaw[1], 0.06 * S, 0.05 * S, 0, 0, 7); paint(style)
  const nose = at(0.098, 0.0); g.beginPath(); g.ellipse(nose[0], nose[1], 0.012 * S, 0.02 * S, 0, 0, 7); paint(style)
  const tu = Math.atan2(up[1], up[0])
  if (!tone.bald) {                                                       // hair over the top and the back of the skull
    g.beginPath()
    if (facing > 0) g.arc(h[0], h[1], 0.102 * S, tu + 4 * Math.PI / 3, tu + 7 * Math.PI / 3); else g.arc(h[0], h[1], 0.102 * S, tu - Math.PI / 3, tu + 2 * Math.PI / 3)
    g.closePath(); paint(tone.hair)
  } else if (mode === 'fill') {                                           // bald: a soft highlight on the scalp
    const c = at(-0.01, 0.06); g.fillStyle = 'rgba(255,255,255,0.2)'; g.beginPath(); g.ellipse(c[0], c[1], 0.04 * S, 0.02 * S, tu + Math.PI / 2, 0, 7); g.fill()
  }
  if (mode === 'fill') {
    const ear = at(-0.012, -0.012); g.fillStyle = col(tone.skin, 0.84); g.beginPath(); g.ellipse(ear[0], ear[1], 0.018 * S, 0.026 * S, 0, 0, 7); g.fill()
    const eye = at(0.062, 0.012); g.fillStyle = '#1b1b1b'; g.beginPath(); g.ellipse(eye[0], eye[1], 0.009 * S, 0.006 * S, 0, 0, 7); g.fill()
    const b1 = at(0.046, 0.032); const b2 = at(0.082, 0.026); g.strokeStyle = col(tone.brow, 1, 0, 0.85); g.lineWidth = 1.6 * S / S0; g.lineCap = 'round'
    g.beginPath(); g.moveTo(b1[0], b1[1]); g.lineTo(b2[0], b2[1]); g.stroke()                                   // brow
    const m1 = at(0.056, -0.036); const m2 = at(0.078, -0.033); g.strokeStyle = col(tone.skin, 0.5, 0, 0.7); g.lineWidth = 1.2 * S / S0
    g.beginPath(); g.moveTo(m1[0], m1[1]); g.lineTo(m2[0], m2[1]); g.stroke()                                   // mouth
  }
}
/** One fighter from its 19 points (metres). `kick`: a high kick is on, the lead leg is drawn see-through. */
function fighter (who, pm, kick) {
  const tone = TONE[who]; const facing = who === 0 ? 1 : -1
  const far = facing > 0 ? 'l' : 'r'; const near = facing > 0 ? 'r' : 'l'     // the fighter facing right shows its right side
  const p = {}; for (const n of NAMES) p[n] = [X(pm[n][0]), Y(pm[n][1])]
  const sp = spine(p, facing); const DIM = 0.7
  const leg = (s, dim) => {
    limb(p.pelvis, p[s + 'kn'], 0.085, 0.055, tone.skin, dim, 0.02, 0.4, 1, facing)
    limb(p[s + 'kn'], p[s + 'an'], 0.05, 0.034, tone.skin, dim, 0.022, 0.3, -1, facing)
    foot(p[s + 'an'], p[s + 'he'], p[s + 'to'], tone.skin, dim)
  }
  const arm = (s, dim) => {
    limb(p[s + 'sh'], p[s + 'el'], 0.056, 0.04, tone.skin, dim, 0.016, 0.45, 1, facing)
    limb(p[s + 'el'], p[s + 'fi'], 0.042, 0.03, tone.skin, dim, 0.012, 0.3, 1, facing)
    glove(p[s + 'el'], p[s + 'fi'], tone.glove, dim)
  }
  g.save(); g.translate(0, -7 * S / S0)                                  // far side: a little further from the camera, so a touch higher
  group(() => arm(far, DIM), 1, OUTLINE_FAR)
  group(() => leg(far, DIM), kick && far === 'l' ? 0.45 : 1, OUTLINE_FAR)
  g.restore()
  group(() => torso(p, tone, facing, sp))
  group(() => leg(near, 1), kick && near === 'l' ? 0.45 : 1)
  group(() => shorts(p, tone, facing, sp))
  group(() => head(p, tone, facing))
  group(() => { ball(p[near + 'sh'], 0.06 * S, tone.skin, 1); arm(near, 1) })
}

// ---------- arena
function buildBackdrop () {                                               // dark hall and the light rig, as wide as the parallax needs
  const PX = 300; const LW = W + 2 * PX
  const c = document.createElement('canvas'); c.width = Math.ceil(LW * PXS); c.height = Math.ceil(CROWD_H * PYS)   // below, the crowd and the floor cover it
  const b = c.getContext('2d', { alpha: false }) || c.getContext('2d'); b.scale(PXS, PYS)   // opaque: copied without blending
  const gr = b.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, '#06070b'); gr.addColorStop(0.42, '#151a27'); gr.addColorStop(1, '#0b0e14')
  b.fillStyle = gr; b.fillRect(0, 0, LW, H)
  for (const fx of [0.3, 0.5, 0.7]) {
    const x = LW * fx; const lg = b.createRadialGradient(x, -150 * K, 40 * K, x, -150 * K, 900 * K)
    lg.addColorStop(0, 'rgba(255,238,205,0.24)'); lg.addColorStop(1, 'rgba(255,238,205,0)')
    b.fillStyle = lg; b.beginPath(); b.moveTo(x - 50 * K, -10); b.lineTo(x + 50 * K, -10); b.lineTo(x + 560 * K, H); b.lineTo(x - 560 * K, H); b.closePath(); b.fill()
  }
  return { img: c, px: PX }
}
/** The dark veil behind the head-up display, drawn once. */
function buildVeil () {
  const c = document.createElement('canvas'); c.width = Math.ceil(W * PXS); c.height = Math.ceil(200 * PYS)
  const b = c.getContext('2d'); b.scale(PXS, PYS)
  const tg = b.createLinearGradient(0, 0, 0, 200); tg.addColorStop(0, 'rgba(5,6,10,0.8)'); tg.addColorStop(1, 'rgba(5,6,10,0)')
  b.fillStyle = tg; b.fillRect(0, 0, W, 200)
  return c
}
/** Draws a cached layer (made at the real pixel size) at a whole pixel: no resampling. */
function blit (img, x, y) {
  g.setTransform(1, 0, 0, 1, 0, 0)
  g.drawImage(img, Math.round((x + shX) * PXS), Math.round((y + shY) * PYS))
  g.setTransform(PXS, 0, 0, PYS, shX * PXS, shY * PYS)
}
// ---------- crowd: pre-rendered rows (three depths, the far ones blurred) and a few live spectators in front
const CROWD_H = 480 * K
const NROWS = 15
const rowY = i => (408 - 280 * Math.pow(i / (NROWS - 1), 0.85)) * K
const rowSc = i => 1 - 0.66 * (i / (NROWS - 1))
const PR = 7.5 * K                        // size unit of a spectator
const SMAX = 410                          // strongest zoom of the camera (see pushCam)
const BARRIER = 424 * K
const BANDS = [
  { k: 0.6, blur: 0, dark: 0.78, rows: [0, 1, 2], top: 318 * K, bottom: 432 * K },
  { k: 0.48, blur: 1.0, dark: 0.46, rows: [3, 4, 5, 6], top: 244 * K, bottom: 356 * K },
  { k: 0.36, blur: 2.0, dark: 0.27, rows: [7, 8, 9, 10, 11, 12, 13, 14], top: 105 * K, bottom: CROWD_H }]
const CLOTH = ['#3b3f4a', '#56606e', '#2f3a4f', '#5b2d2d', '#7a3b3b', '#2e4a3f', '#3f6a57', '#6b5b3e', '#8c7a55', '#b8b2a3', '#d9d4c7', '#4a3b6b', '#2b5c7a', '#8a3d5c', '#c2402e', '#2a63c9', '#e0b94d', '#1f1f24', '#efe7d6', '#5a6b8a']
const OVER = [10, 12, 24, 0.3]            // the crowd sits in the dark, behind the ring lights: this colour is laid over it
const COLD = new Map()
/** col() with the darkness of the crowd already laid over. */
function colD (hex, k = 1, w = 0) {
  const key = hex + '|' + k + '|' + w
  let r = COLD.get(key)
  if (r !== undefined) return r
  const kk = k < 1 ? [Math.pow(k, 0.85), k, Math.pow(k, 1.15)] : [k, k, k]
  const c = rgb(hex).map((v, i) => Math.round((Math.min(255, v * kk[i]) * (1 - w) + 255 * w) * (1 - OVER[3]) + OVER[i] * OVER[3]))
  r = 'rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ')'
  if (COLD.size > 4000) COLD.clear()
  COLD.set(key, r)
  return r
}
function makePerson (rnd) {
  const hairIdx = Math.floor(rnd() * 9); const cl = CLOTH[Math.floor(rnd() * CLOTH.length)]
  return {
    skin: SKIN[Math.floor(rnd() * 8)], hair: HAIR[hairIdx], bald: rnd() < 0.08, longHair: rnd() < 0.3, cloth: cl,
    hat: rnd() < 0.08, hatCol: CLOTH[Math.floor(rnd() * CLOTH.length)], phone: rnd() < 0.05, armsUp: rnd() < 0.04
  }
}
/** One spectator: y is the shoulder line, sc the size, up the raised-arms amount (0..1), face the head turn (-1..1). */
function person (c, x, y, sc, p, dark, up, face, cf) {
  const r = PR * sc; const dk = h => cf(h, dark)
  if (up > 0.03) {                                                        // arms, behind the body (sleeves a little lighter so a wave reads)
    c.strokeStyle = cf(p.cloth, dark * 1.25); c.lineWidth = r * 0.6; c.lineCap = 'round'
    for (const s of [-1, 1]) {
      const hx = x + s * (r * 2.7 - up * r * 1.3); const hy = y + r * 1.4 - up * r * 4.4
      c.beginPath(); c.moveTo(x + s * r * 1.45, y + r * 0.55); c.lineTo(hx, hy); c.stroke()
      c.fillStyle = cf(p.skin, dark * 1.1, 0.08); c.beginPath(); c.arc(hx, hy, r * 0.42, 0, 7); c.fill()
    }
  }
  if (!p.bald && p.longHair) { c.fillStyle = dk(p.hair); c.beginPath(); c.ellipse(x - face * r * 0.15, y - r * 0.8, r * 1.15, r * 1.45, 0, 0, 7); c.fill() }
  c.fillStyle = dk(p.cloth); c.beginPath()                                // shoulders
  c.moveTo(x - r * 2.2, y + r * 2.6); c.quadraticCurveTo(x - r * 2.3, y + r * 0.1, x - r * 0.9, y + r * 0.05)
  c.lineTo(x + r * 0.9, y + r * 0.05); c.quadraticCurveTo(x + r * 2.3, y + r * 0.1, x + r * 2.2, y + r * 2.6); c.closePath(); c.fill()
  c.fillStyle = dk(p.skin); c.fillRect(x - r * 0.36, y - r * 0.6, r * 0.72, r * 0.9)   // neck
  c.beginPath(); c.ellipse(x, y - r * 1.2, r, r * 1.1, 0, 0, 7); c.fill()                // head
  c.fillStyle = cf(p.skin, dark * 1.12, 0.05); c.beginPath(); c.ellipse(x + face * r * 0.28, y - r * 1.1, r * 0.6, r * 0.78, 0, 0, 7); c.fill()  // face, turned toward the ring
  if (!p.bald) { c.fillStyle = dk(p.hair); c.beginPath(); c.ellipse(x - face * r * 0.12, y - r * 1.55, r * 1.04, r * 0.72, 0, Math.PI, 2 * Math.PI); c.fill() }
  if (p.hat) { c.fillStyle = dk(p.hatCol); c.beginPath(); c.ellipse(x, y - r * 1.75, r * 1.08, r * 0.62, 0, Math.PI, 2 * Math.PI); c.fill(); c.fillRect(x - r * 1.35, y - r * 1.8, r * 2.7, r * 0.22) }
  if (p.phone) { c.fillStyle = 'rgba(205,228,255,0.9)'; c.fillRect(x + r * 1.4, y - r * 3.3, r * 0.56, r * 1.0) }
}
/**
 * The rows of the crowd, drawn once at the real pixel size. Each depth is a layer as wide as its parallax needs;
 * x is counted from the centre of the screen (camera at 0). The darkness of the hall is baked in.
 */
function buildCrowd () {
  const rnd = seed(23); const live = []; const bands = []
  for (const band of BANDS) {
    const half = W / 2 + Math.ceil(2.8 * band.k * SMAX) + 64
    const wpx = Math.ceil(2 * half * PXS); const hpx = Math.ceil((band.bottom - band.top) * PYS)
    const c = document.createElement('canvas'); c.width = wpx; c.height = hpx; const b = c.getContext('2d')
    const tmp = document.createElement('canvas'); tmp.width = wpx; tmp.height = hpx; const tb = tmp.getContext('2d')
    const set = x => x.setTransform(PXS, 0, 0, PYS, half * PXS, -band.top * PYS)
    set(b); set(tb)
    const back = band.rows.includes(NROWS - 1)
    if (back) {                                                           // the back layer carries the stands behind everyone
      const sg = b.createLinearGradient(0, 105 * K, 0, CROWD_H); sg.addColorStop(0, 'rgba(14,16,22,0.5)'); sg.addColorStop(0.3, 'rgba(18,20,28,0.96)'); sg.addColorStop(1, '#0d0f14')
      b.fillStyle = sg; b.fillRect(-half, 105 * K, 2 * half, CROWD_H - 105 * K)
    }
    for (const i of band.rows.slice().reverse()) {                        // back rows first
      const y = rowY(i); const sc = rowSc(i); const r = PR * sc; const step = r * 3.1
      for (let x = -half - 40 + rnd() * step; x < half + 40; x += step * (0.75 + rnd() * 0.5)) {
        const p = makePerson(rnd); const yy = y + (rnd() - 0.5) * r * 0.8
        if (i <= 2 && rnd() < 0.3) { live.push({ x, y: yy, sc, p, ph: rnd() * 6.28, ph2: rnd() * 6.28, r: rnd(), zeal: 0.4 + rnd() * 0.6, dark: band.dark, row: i }); continue }
        person(tb, x, yy, sc, p, band.dark, p.armsUp ? 0.9 : 0, clamp(-x / (700 * K), -1, 1), col)
      }
    }
    b.setTransform(1, 0, 0, 1, 0, 0)
    if (band.blur && 'filter' in b) b.filter = 'blur(' + (band.blur * K * PXS).toFixed(2) + 'px)'
    b.drawImage(tmp, 0, 0); b.filter = 'none'
    set(b)
    if (back) {                                                           // the far rows sink into the dark
      const fg = b.createLinearGradient(0, 105 * K, 0, 275 * K); fg.addColorStop(0, 'rgba(8,9,14,0.75)'); fg.addColorStop(1, 'rgba(8,9,14,0)')
      b.fillStyle = fg; b.fillRect(-half, 105 * K, 2 * half, 170 * K)
    }
    b.globalCompositeOperation = 'source-atop'
    b.fillStyle = 'rgba(' + OVER[0] + ',' + OVER[1] + ',' + OVER[2] + ',' + OVER[3] + ')'; b.fillRect(-half, band.top, 2 * half, band.bottom - band.top)
    bands.push({ img: c, half, top: band.top, k: band.k })
  }
  live.sort((a, b) => b.row - a.row)                                      // front rows over the rows behind
  return { bands, live }
}
/** Raised-arms amount and jump of one live spectator, from the reactions going on. */
function armsFor (s, te, active) {
  let up = 0; let bob = 0
  for (const r of active) {
    const age0 = te - r.f
    const dist = Math.abs(s.x - r.xl); const amp = r.ko ? 1 : r.st * Math.max(0, 1 - dist / (650 * K)) * s.zeal
    if (amp < 0.15) continue
    const age = age0 - (r.ko ? 0 : dist / (70 * K)) - s.r * 3
    const u = age < 0 ? 0 : age < 5 ? 1 - Math.pow(1 - age / 5, 2) : age < 5 + r.len ? 1 : age < 15 + r.len ? 1 - (age - 5 - r.len) / 10 : 0
    up = Math.max(up, u * Math.min(1, amp * 1.5))
    if (r.ko && age > 0 && age < r.len) bob = Math.max(bob, Math.abs(Math.sin(age * 0.45 + s.ph)) * 5 * K * s.zeal)
  }
  return [up, bob]
}
function drawCrowd (crowd, reactions, te, midX) {
  for (let i = crowd.bands.length - 1; i >= 0; i--) { const b = crowd.bands[i]; blit(b.img, W / 2 - b.half - camX * b.k * S, b.top) }   // back to front
  const active = []; let lit = 0
  for (const r of reactions) {
    if (te < r.f || te > r.f + r.len + 100) continue
    active.push(r)
    if (r.ko && te < r.f + r.len + 10) lit = Math.min(1, (te - r.f) / 4) * Math.min(1, (r.f + r.len + 10 - te) / 10)   // the house lights come up a little on a knockout
  }
  const ox = W / 2 - camX * 0.6 * S
  for (const s of crowd.live) {                                           // live spectators: idle sway, heads following the fight, reactions
    const sx = ox + s.x + Math.sin(te * 0.045 + s.ph) * 1.6 * K * s.sc
    if (sx < -70 || sx > W + 70) continue
    const ab = active.length ? armsFor(s, te, active) : [0, 0]
    const sy = s.y + Math.sin(te * 0.07 + s.ph2) * 0.8 * K - ab[1]
    person(g, sx, sy, s.sc, s.p, s.dark, ab[0], clamp((midX - sx) / (500 * K), -1, 1), colD)
  }
  const bg = g.createLinearGradient(0, BARRIER, 0, CROWD_H)                // ringside barrier in front of the first row
  bg.addColorStop(0, colD('#2a2f3c')); bg.addColorStop(0.12, colD('#1a1d26')); bg.addColorStop(1, colD('#0e1015'))
  g.fillStyle = bg; g.fillRect(-80, BARRIER, W + 160, CROWD_H - BARRIER + 2)
  if (lit > 0.01) {
    g.save(); g.globalCompositeOperation = 'color-dodge'
    const v = Math.round(255 * (1 - 1 / (1 + 0.32 * lit))); g.fillStyle = 'rgb(' + v + ',' + v + ',' + v + ')'
    g.fillRect(-80, 105 * K, W + 160, CROWD_H - 105 * K); g.restore()
  }
  if (active.length) {
    g.save(); g.globalCompositeOperation = 'lighter'                      // camera flashes
    for (const r of active) {
      for (const fl of r.flashes) {
        if (te < fl.s || te >= fl.s + fl.d) continue
        const a = 1 - (te - fl.s) / fl.d; const fx = W / 2 + fl.x - camX * fl.k * S; const R = fl.size * 7 * K
        const gr = g.createRadialGradient(fx, fl.y, 0, fx, fl.y, R); gr.addColorStop(0, 'rgba(255,255,255,' + 0.95 * a + ')'); gr.addColorStop(0.14, 'rgba(255,250,238,' + 0.8 * a + ')'); gr.addColorStop(0.32, 'rgba(255,240,210,' + 0.3 * a + ')'); gr.addColorStop(1, 'rgba(255,230,180,0)')
        g.fillStyle = gr; g.beginPath(); g.arc(fx, fl.y, R, 0, 7); g.fill()
      }
    }
    g.restore()
  }
}

/**
 * The arena floor beyond the mat. Only what the mat and the apron (drawn later) leave in sight is filled, plus a strip under their seam:
 * every pixel of the canvas must be covered on every frame, or a trace of the frame drawn before would show through.
 */
function floor (XR) {
  const fl = g.createLinearGradient(0, horizonY, 0, H); fl.addColorStop(0, '#252a37'); fl.addColorStop(1, '#111319')
  const y0 = Math.max(horizonY, CROWD_H - 40)                             // above, the crowd covers it
  const A = P(-XR - 0.5, 0, CAM.zb); const B = P(XR + 0.5, 0, CAM.zb)       // back corners of the mat
  const xl = Math.min(A[0], B[0]) + 2; const xr = Math.max(A[0], B[0]) - 2; const yb = Math.max(y0, A[1])
  const yf = P(0, 0, -CAM.zf)[1]                                          // front edge of the mat, where the apron starts
  g.fillStyle = fl
  if (A[1] + 2 > y0) g.fillRect(-80, y0, W + 160, A[1] + 2 - y0)          // the strip behind the mat
  if (xl > -80) g.fillRect(-80, yb, xl + 80, H + 80 - yb)                 // what shows beside the mat when the camera is far to one side
  if (xr < W + 80) g.fillRect(xr, yb, W + 80 - xr, H + 80 - yb)
  g.fillRect(-80, yf - 3, W + 160, 6)                                     // under the seam between the mat and the apron
}
function ring (XR, out) {
  const { zb, zf } = CAM; const XM = XR + 0.5
  const A = P(-XM, 0, zb); const B = P(XM, 0, zb); const C = P(XM, 0, -zf); const D = P(-XM, 0, -zf)
  const quad = (a, b, c, d) => { g.beginPath(); g.moveTo(a[0], a[1]); g.lineTo(b[0], b[1]); g.lineTo(c[0], c[1]); g.lineTo(d[0], d[1]); g.closePath() }
  quad(A, B, C, D)
  const mg = g.createLinearGradient(0, A[1], 0, C[1]); mg.addColorStop(0, '#a39c8d'); mg.addColorStop(0.45, '#d8d0bf'); mg.addColorStop(1, '#cbc2ae')
  g.fillStyle = mg; g.fill()
  const c0 = P(0, 0, 0.5); const SQ = 0.36                               // the spotlight pool on the canvas: a flattened disc of light, on the mat only
  g.save(); g.translate(c0[0], c0[1]); g.scale(1, SQ)
  const sg = g.createRadialGradient(0, 0, 0, 0, 0, 3.4 * S); sg.addColorStop(0, 'rgba(255,250,236,0.55)'); sg.addColorStop(0.5, 'rgba(255,250,236,0.2)'); sg.addColorStop(1, 'rgba(255,250,236,0)')
  g.beginPath(); for (const q of [A, B, C, D]) g.lineTo(q[0] - c0[0], (q[1] - c0[1]) / SQ)
  g.closePath(); g.fillStyle = sg; g.fill(); g.restore()
  g.save(); quad(A, B, C, D); g.clip()
  for (const s of [-1, 1]) {                                              // ring-out zones: tinted, striped, flashing when someone leaves the ring
    quad(P(s * XR, 0, zb), P(s * XM, 0, zb), P(s * XM, 0, -zf), P(s * XR, 0, -zf)); g.fillStyle = 'rgba(190,50,40,' + (0.26 + out[s < 0 ? 0 : 1]) + ')'; g.fill()
    g.fillStyle = 'rgba(150,30,22,0.34)'
    for (let z = -zf - 0.3; z < zb; z += 0.5) { quad(P(s * XR, 0, z), P(s * XM, 0, z + 0.3), P(s * XM, 0, z + 0.52), P(s * XR, 0, z + 0.22)); g.fill() }
  }
  g.strokeStyle = 'rgba(40,30,20,0.1)'; g.lineWidth = K
  for (let x = -Math.floor(XR); x <= Math.floor(XR); x++) { const a = P(x, 0, zb); const b = P(x, 0, -zf); g.beginPath(); g.moveTo(a[0], a[1]); g.lineTo(b[0], b[1]); g.stroke() }
  for (const z of [-0.5, 0.5, 1.0, 1.5, 2.0]) { const a = P(-XM, 0, z); const b = P(XM, 0, z); g.beginPath(); g.moveTo(a[0], a[1]); g.lineTo(b[0], b[1]); g.stroke() }
  g.strokeStyle = 'rgba(40,30,20,0.16)'; g.lineWidth = 2 * K; g.beginPath()   // centre circle
  for (let i = 0; i <= 48; i++) { const an = i / 48 * 2 * Math.PI; const q = P(Math.cos(an) * 0.8, 0, Math.sin(an) * 0.62); if (i) g.lineTo(q[0], q[1]); else g.moveTo(q[0], q[1]) }
  g.stroke()
  g.strokeStyle = 'rgba(176,36,26,0.85)'; g.lineWidth = 4 * K
  for (const s of [-1, 1]) { const a = P(s * XR, 0, zb); const b = P(s * XR, 0, -zf); g.beginPath(); g.moveTo(a[0], a[1]); g.lineTo(b[0], b[1]); g.stroke() }   // ring-out lines
  g.fillStyle = 'rgba(40,30,20,0.36)'; g.font = '700 36px ' + FONT; g.textAlign = 'center'; g.textBaseline = 'middle'
  for (let x = -Math.floor(XR); x <= Math.floor(XR); x++) { const q = P(x, 0, -0.58); g.fillText(x + ' m', q[0], q[1]) }
  g.restore()
  g.strokeStyle = 'rgba(255,255,255,0.4)'; g.lineWidth = 2 * K; g.beginPath(); g.moveTo(D[0], D[1]); g.lineTo(C[0], C[1]); g.stroke()   // front edge
  const ap = g.createLinearGradient(0, C[1], 0, H); ap.addColorStop(0, '#2c3453'); ap.addColorStop(1, '#131727')                   // apron
  g.fillStyle = ap; g.beginPath(); g.moveTo(D[0], D[1]); g.lineTo(C[0], C[1]); g.lineTo(C[0], H + 80); g.lineTo(D[0], H + 80); g.closePath(); g.fill()
  g.strokeStyle = 'rgba(227,179,65,0.5)'; g.lineWidth = 2 * K; g.beginPath(); g.moveTo(D[0], D[1] + 6 * K); g.lineTo(C[0], C[1] + 6 * K); g.stroke()
  const wx = P(0, 0, -zf)[0]; g.font = '900 50px ' + FONT; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillStyle = 'rgba(227,179,65,0.32)'
  spacing(20)
  g.fillText('BYTE CLUB', wx, (C[1] + H) / 2 + 6)
  spacing(0)
  const k = CAM.D / (CAM.D + zb)                                          // corner posts and ropes at the back
  for (const s of [-1, 1]) {
    const base = P(s * XR, 0, zb); const top = P(s * XR, 1.5, zb); const w = 0.14 * S * k; const pc = TONE[s < 0 ? 0 : 1].post
    g.fillStyle = 'rgba(0,0,0,0.25)'; g.beginPath(); g.ellipse(base[0], base[1], w, w * 0.28, 0, 0, 7); g.fill()
    const pg = g.createLinearGradient(base[0] - w / 2, 0, base[0] + w / 2, 0)
    pg.addColorStop(0, col(pc, 0.55)); pg.addColorStop(0.35, col(pc, 1.05, 0.12)); pg.addColorStop(1, col(pc, 0.5))
    g.fillStyle = pg; g.beginPath(); rrect(base[0] - w / 2, top[1], w, base[1] - top[1], w * 0.3); g.fill()
    g.fillStyle = col(pc, 0.75); g.beginPath(); g.ellipse(base[0], top[1], w / 2, w * 0.2, 0, 0, 7); g.fill()
  }
  for (const h of [0.5, 0.92, 1.34]) {
    const a = P(-XR, h, zb); const b = P(XR, h, zb); const sag = 4 * k * K
    const rope = (wdt, color, dy) => { g.strokeStyle = color; g.lineWidth = wdt; g.lineCap = 'round'; g.beginPath(); g.moveTo(a[0], a[1] + dy); g.quadraticCurveTo((a[0] + b[0]) / 2, a[1] + sag * 2 + dy, b[0], b[1] + dy); g.stroke() }
    rope(8 * k * S / S0, 'rgba(20,14,12,0.55)', 2 * K); rope(6.5 * k * S / S0, '#e6dac0', 0); rope(2 * k * S / S0, 'rgba(255,255,255,0.5)', -1.5 * K)
  }
}
function shadows (pm) {
  for (let who = 0; who < 2; who++) {
    const p = pm[who]; const farS = who === 0 ? 'l' : 'r'
    let minx = 1e9; let maxx = -1e9; let miny = 1e9
    for (const n of NAMES) { const q = p[n]; if (q[0] < minx) minx = q[0]; if (q[0] > maxx) maxx = q[0]; if (q[1] < miny) miny = q[1] }
    const cx = X((minx + maxx) / 2); const cy = Y(0) - 3 * S / S0; const rx = ((maxx - minx) / 2 + 0.3) * S; const ry = Math.max(8 * K, rx * 0.17)
    const air = Math.min(1, Math.max(0, (miny - 0.03) / 0.5)); const a0 = 0.5 * (1 - 0.55 * air)
    g.save(); g.translate(cx, cy); g.scale(1, ry / rx)
    const gr = g.createRadialGradient(0, 0, 0, 0, 0, rx); gr.addColorStop(0, 'rgba(8,6,14,' + a0 + ')'); gr.addColorStop(0.55, 'rgba(8,6,14,' + a0 * 0.45 + ')'); gr.addColorStop(1, 'rgba(8,6,14,0)')
    g.fillStyle = gr; g.beginPath(); g.arc(0, 0, rx, 0, 7); g.fill(); g.restore()
    for (const s of ['l', 'r']) {                                        // contact shadow under each foot near the floor
      const an = p[s + 'an']; const he = p[s + 'he']; const to = p[s + 'to']; const h = Math.min(an[1], he[1], to[1]) - 0.03
      if (h < 0.2) {
        const a = 0.32 * (1 - Math.max(0, h) / 0.2); const fx = X((he[0] + to[0]) / 2); const fy = Y(0) + (s === farS ? -7 : 0) * S / S0
        g.fillStyle = 'rgba(8,6,14,' + a + ')'; g.beginPath(); g.ellipse(fx, fy, 0.17 * S, 0.04 * S, 0, 0, 7); g.fill()
      }
    }
  }
}

// ---------- texts over the scene
/** The move of each fighter above its head. texts[who]: { text, muted } or null. */
function labels (pm, texts) {
  g.font = '800 36px ' + FONT; spacing(1.5); g.textAlign = 'center'; g.textBaseline = 'middle'
  const L = []
  for (let who = 0; who < 2; who++) {
    if (!texts[who]) continue
    const tw = g.measureText(texts[who].text).width + 34
    const hx = clamp(X(pm[who].head[0]), tw / 2 + 8, W - tw / 2 - 8); const hy = Math.max(204, Y(pm[who].head[1]) - 0.2 * S)
    L.push({ who, x: hx, y: hy, w: tw, t: texts[who] })
  }
  if (L.length === 2 && Math.abs(L[0].y - L[1].y) < 56) {                 // side by side: push them apart
    const over = (L[0].w + L[1].w) / 2 + 10 - Math.abs(L[0].x - L[1].x)
    if (over > 0) { const s = L[0].x <= L[1].x ? -1 : 1; L[0].x += s * over / 2; L[1].x -= s * over / 2 }
  }
  for (const l of L) {
    g.fillStyle = 'rgba(10,8,14,0.66)'; g.beginPath(); rrect(l.x - l.w / 2, l.y - 52, l.w, 52, 12); g.fill()
    g.fillStyle = l.t.muted ? 'rgba(225,225,232,0.78)' : TONE[l.who].label
    g.fillText(l.t.text, l.x, l.y - 25)
  }
  spacing(0)
}

// ---------- fast gloves and feet: afterimages behind the fighter, a streak over it
const TIPS = [['lel', 'lfi', 'g'], ['rel', 'rfi', 'g'], ['lan', 'lto', 'f'], ['ran', 'rto', 'f']]
/** Speed (m/s) of a point during the quarter-turn being played. */
function tipSpeed (turn, local, who, name) {
  const k = Math.min(3, Math.max(0, Math.floor(local))); const a = k === 0 ? turn.start : turn.sub[k - 1]; const b = turn.sub[k]
  const j = (who * 19 + NAMES.indexOf(name)) * 2
  return Math.hypot(b[j] - a[j], b[j + 1] - a[j + 1]) / 1000 / 0.025
}
function afterimages (view, te, turn, local, fade) {
  for (let who = 0; who < 2; who++) {
    const tone = TONE[who]; const far = who === 0 ? 'l' : 'r'
    for (const [a, b, kind] of TIPS) {
      const v = tipSpeed(turn, local, who, b); if (v < 3.5) continue
      const str = Math.min(1, (v - 3.5) / 3) * fade; const isFar = b[0] === far; const oy = isFar ? -7 * S / S0 : 0; const dim = isFar ? 0.7 : 1
      for (const [k, al] of [[1.5, 0.42], [3, 0.26], [4.5, 0.14]]) {
        const q = view.pointsAt(te - k, who); const sc = nm => [X(q[nm][0]), Y(q[nm][1]) + oy]
        g.save(); g.globalAlpha = al * str; mode = 'fill'
        if (kind === 'g') glove(sc(a), sc(b), tone.glove, dim); else foot(sc(a), sc(b.replace('to', 'he')), sc(b), tone.skin, dim)
        g.restore()
      }
    }
  }
}
function trails (view, te, turn, local, fade) {
  g.lineCap = 'round'; g.lineJoin = 'round'
  for (let who = 0; who < 2; who++) {
    for (const [, b, kind] of TIPS) {
      const v = tipSpeed(turn, local, who, b); if (v < 3) continue
      const far = b[0] === (who === 0 ? 'l' : 'r'); const oy = far ? -7 * S / S0 : 0
      const st = Math.min(1, (v - 3) / 3.5) * fade; const R = (kind === 'g' ? 0.06 : 0.05) * S; const hex = kind === 'g' ? TONE[who].glove : TONE[who].skin
      const N = 6; const pts = []
      for (let k = N; k >= 0; k--) { const q = view.pointsAt(te - k, who)[b]; pts.push([X(q[0]), Y(q[1]) + oy]) }
      for (let i = 1; i < pts.length; i++) {
        const u = i / N; const al = Math.round(st * 0.5 * u * u * (far ? 0.6 : 1) * 50) / 50
        if (al <= 0) continue
        g.strokeStyle = col(hex, 1, 0.55, al); g.lineWidth = 2 * R * (0.2 + 0.6 * u)
        g.beginPath(); g.moveTo(pts[i - 1][0], pts[i - 1][1]); g.lineTo(pts[i][0], pts[i][1]); g.stroke()
        g.strokeStyle = 'rgba(255,255,255,' + al * 0.7 + ')'; g.lineWidth = R * 0.35 * u
        g.beginPath(); g.moveTo(pts[i - 1][0], pts[i - 1][1]); g.lineTo(pts[i][0], pts[i][1]); g.stroke()
      }
    }
  }
}

// ---------- effects at the contact points
function hitEffect (e, age) {
  const x = X(e.x); const y = Y(e.y); const k = Math.min(1, e.dmg / 15); const big = e.dmg >= 8; const none = e.dmg <= 0
  const rnd = seed(Math.round(e.t * 10) * 31 + e.who); const z = S / S0
  if (age < 6) {                                                          // flash and sparks
    const r = (10 + 26 * k) * z * (1 + age * 0.25); const a = (1 - age / 6) * (none ? 0.55 : 1)
    const gr = g.createRadialGradient(x, y, 0, x, y, r)
    gr.addColorStop(0, 'rgba(255,255,255,' + 0.95 * a + ')'); gr.addColorStop(0.4, 'rgba(255,222,120,' + 0.7 * a + ')'); gr.addColorStop(1, 'rgba(255,160,40,0)')
    g.fillStyle = gr; g.beginPath(); g.arc(x, y, r, 0, 7); g.fill()
    if (!none) {
      g.strokeStyle = 'rgba(255,236,160,' + a + ')'; g.lineCap = 'round'
      const nn = 5 + Math.round(5 * k)
      for (let i = 0; i < nn; i++) {
        const an = rnd() * 6.283; const len = (14 + 40 * k) * (0.6 + rnd() * 0.6) * (1 + age * 0.35) * z
        g.lineWidth = (1.5 + 2.5 * k) * K * (1 - age / 6)
        g.beginPath(); g.moveTo(x + Math.cos(an) * len * 0.35, y + Math.sin(an) * len * 0.35); g.lineTo(x + Math.cos(an) * len, y + Math.sin(an) * len); g.stroke()
      }
    }
  }
  if (big && age < 12) {                                                  // shockwave ring on heavy hits
    const r = (20 + age * 9) * z; const a = (1 - age / 12) * 0.7
    g.strokeStyle = 'rgba(255,255,255,' + a + ')'; g.lineWidth = (3 * (1 - age / 12) + 1) * K; g.beginPath(); g.arc(x, y, r, 0, 7); g.stroke()
  }
  if (big && age < 2) { g.fillStyle = 'rgba(255,255,255,' + 0.14 * k * (1 - age / 2) + ')'; g.fillRect(-60, -60, W + 120, H + 120) }
  // the damage number pops at the impact, then drifts up and toward the attacker's side, away from the head that was hit
  const drift = (Math.min(age, 14) * 2 + Math.max(0, age - 14) * 0.6) * K; const a = age < 16 ? 1 : 1 - (age - 16) / 10
  const size0 = none ? 36 : 36 + 24 * k; const size = age < 2 ? size0 * (1.5 - age * 0.25) : size0
  let hop = 0                                                             // this number steps aside for each newer hit close by, just before it lands
  if (e.push) for (const t of e.push) { const o = clamp((e.t + age - t) / 2 + 1, 0, 1); hop += o * (2 - o) }
  const tx = clamp(x + (e.who === 0 ? -1 : 1) * (12 * z + drift * 1.2 + hop * size0 * 1.9), 150, W - 150)
  const ty = Math.max(250, y - (16 + 10 * k) * z - drift - hop * 22)
  g.textAlign = 'center'; g.textBaseline = 'alphabetic'; g.lineJoin = 'round'
  if (!none) {
    g.font = '900 ' + size.toFixed(1) + 'px ' + FONT
    g.lineWidth = 4 * K; g.strokeStyle = 'rgba(20,10,10,' + a + ')'; g.strokeText('−' + e.dmg, tx, ty)
    g.fillStyle = big ? 'rgba(255,120,60,' + a + ')' : 'rgba(255,236,170,' + a + ')'; g.fillText('−' + e.dmg, tx, ty)
  }
  if (age < 18) {                                                         // what touched what, and how fast
    const left = (e.cut === undefined ? Infinity : e.cut) - e.t - age       // a newer hit close by: this line is gone when the new one lands
    const a2 = Math.min(1, a) * (age < 12 ? 1 : 1 - (age - 12) / 6) * (none ? 0.8 : 1) * clamp(left / 2, 0, 1)
    if (a2 > 0.01) {
      g.font = '600 36px ' + FONT; g.lineWidth = 5; g.strokeStyle = 'rgba(20,10,10,' + a2 * 0.9 + ')'; g.fillStyle = 'rgba(255,255,255,' + a2 + ')'
      const s2 = e.with + ' → ' + e.on + ' · ' + e.v.toFixed(1) + ' m/s' + (none ? ' · no damage' : '')
      g.strokeText(s2, tx, ty + 42); g.fillText(s2, tx, ty + 42)
    }
  }
}
/** A strike stopped by the guard: a cold spark, no number. */
function blockEffect (e, age) {
  const x = X(e.x); const y = Y(e.y); const z = S / S0
  if (age < 8) {
    const a = 1 - age / 8; const r = (9 + age * 3.4) * z
    const gr = g.createRadialGradient(x, y, 0, x, y, 15 * z); gr.addColorStop(0, 'rgba(255,255,255,' + 0.9 * a + ')'); gr.addColorStop(0.5, 'rgba(150,205,255,' + 0.5 * a + ')'); gr.addColorStop(1, 'rgba(120,180,255,0)')
    g.fillStyle = gr; g.beginPath(); g.arc(x, y, 15 * z, 0, 7); g.fill()
    g.strokeStyle = 'rgba(175,220,255,' + 0.9 * a + ')'; g.lineWidth = (2.5 * a + 1) * K; g.beginPath(); g.arc(x, y, r, 0, 7); g.stroke()
    g.strokeStyle = 'rgba(235,245,255,' + a + ')'; g.lineWidth = 2.5 * K * a; g.lineCap = 'round'
    for (let i = 0; i < 4; i++) {
      const an = Math.PI / 4 + i * Math.PI / 2; const r0 = r * 0.55; const r1 = r * 1.5
      g.beginPath(); g.moveTo(x + Math.cos(an) * r0, y + Math.sin(an) * r0); g.lineTo(x + Math.cos(an) * r1, y + Math.sin(an) * r1); g.stroke()
    }
  }
  const a = age < 9 ? 1 : 1 - (age - 9) / 5
  g.font = '800 36px ' + FONT; g.textAlign = 'center'; g.textBaseline = 'alphabetic'; g.lineJoin = 'round'; spacing(1.5)
  const tx = clamp(x, 80, W - 80)                                        // above the spark, or under it when a hit close by needs the room above
  const ty = e.low ? Math.min(H - 150, y + 26 * z + 20 + age * 1.4 * K) : Math.max(250, y - 26 * z - age * 1.4 * K)
  g.lineWidth = 5; g.strokeStyle = 'rgba(10,14,24,' + a * 0.9 + ')'; g.strokeText('BLOCK', tx, ty)
  g.fillStyle = 'rgba(185,225,255,' + a + ')'; g.fillText('BLOCK', tx, ty)
  spacing(0)
}
function effects (view, tf) {
  for (const e of view.blocks) { const age = tf - e.t; if (age >= 0 && age < 14) blockEffect(e, age) }
  for (const e of view.hits) { const age = tf - e.t; if (age >= 0 && age < 26) hitEffect(e, age) }
}
/** A balance step starts: three chevrons on the mat, in the direction of the step. */
function stepCues (view, tf) {
  for (const e of view.steps) {
    const age = tf - e.t; if (age < 0 || age >= 10) continue
    const turn = view.turns[e.n]; if (!turn) continue
    const q = poseOf(turn, 0, e.who).pelvis; const wd = e.dir * (e.who === 0 ? 1 : -1); const z = S / S0
    const a = age < 1.5 ? age / 1.5 : 1 - (age - 1.5) / 8.5
    const x0 = X(q[0]) + wd * (0.3 * S + age * 4 * z); const y0 = FLOOR + 20 * z
    g.lineCap = 'round'; g.lineJoin = 'round'; g.lineWidth = 5 * z
    for (let i = 0; i < 3; i++) {
      const cx = x0 + wd * i * 22 * z
      g.strokeStyle = col(TONE[e.who].color, 0.8, 0, Math.round(a * (1 - i * 0.25) * 20) / 20)
      g.beginPath(); g.moveTo(cx - wd * 10 * z, y0 - 8 * z); g.lineTo(cx, y0); g.lineTo(cx - wd * 10 * z, y0 + 8 * z); g.stroke()
    }
  }
}

// ---------- banners
const BAN = { y: 1002, h: 56, life: 36 }  // a fall or a ring-out: a band at the bottom, clear of the fighters
const FIN = { y: 800, h: 80 }             // the result, in the part of the screen the end screen of the player leaves free
/** o: { alpha, color, big, cost, costColor, small } */
function banner (y, o) {
  g.save(); g.globalAlpha = o.alpha
  g.fillStyle = 'rgba(8,6,12,0.94)'; g.fillRect(0, y - BAN.h, W, 2 * BAN.h)
  g.fillStyle = o.color; g.fillRect(0, y - BAN.h, W, 5); g.fillRect(0, y + BAN.h - 5, W, 5)
  g.textBaseline = 'middle'; g.font = '900 46px ' + FONT; spacing(3)
  const cy = o.small ? y - 16 : y + 2
  const gap = o.cost ? 44 : 0
  const big = fit(o.big, W - 500); const w1 = g.measureText(big).width; const w2 = o.cost ? g.measureText(o.cost).width : 0
  let x = W / 2 - (w1 + gap + w2) / 2
  g.textAlign = 'left'; g.fillStyle = '#ffffff'; g.fillText(big, x, cy)
  if (o.cost) { x += w1 + gap; g.fillStyle = o.costColor; g.fillText(o.cost, x, cy) }
  spacing(0)
  if (o.small) { g.textAlign = 'center'; g.font = '600 36px ' + FONT; g.fillStyle = 'rgba(255,255,255,0.8)'; g.fillText(o.small, W / 2, y + 32) }
  g.restore()
}
/** The result of the match. o: { alpha, color, big, small } */
function finalBanner (o) {
  const y = FIN.y
  g.save(); g.globalAlpha = o.alpha
  g.fillStyle = 'rgba(8,6,12,0.8)'; g.fillRect(0, y - FIN.h, W, 2 * FIN.h)
  g.fillStyle = o.color; g.fillRect(0, y - FIN.h, W, 6); g.fillRect(0, y + FIN.h - 6, W, 6)
  g.textAlign = 'center'; g.textBaseline = 'middle'; g.font = '900 62px ' + FONT; spacing(4)
  g.fillStyle = '#ffffff'; g.fillText(fit(o.big, W - 160), W / 2, o.small ? y - 16 : y + 2)
  spacing(0)
  if (o.small) { g.font = '600 36px ' + FONT; g.fillStyle = 'rgba(255,255,255,0.85)'; g.fillText(fit(o.small, W - 160), W / 2, y + 40) }
  g.restore()
}

// ---------- SHAPES option: what the engine really collides
const SHAPE_BODY = [79, 227, 255]
const SHAPE_STRIKER = [255, 210, 60]
const STRIKERS = ['glove', 'foot', 'shin', 'knee']
function shapesOverlay (pm, shapes, bones, kinds) {
  g.save(); g.lineJoin = 'round'; g.lineCap = 'round'
  const ok = i => Number.isInteger(i) && i >= 0 && i < 19
  for (let who = 0; who < 2; who++) {
    const q = NAMES.map(nm => [X(pm[who][nm][0]), Y(pm[who][nm][1])])
    for (let pass = 0; pass < 2; pass++) {                                // the striking shapes over the others
      for (const sh of shapes) {
        if (!Array.isArray(sh) || !ok(sh[0]) || !ok(sh[1])) continue
        const striker = STRIKERS.includes(kinds[sh[3]]); if (striker !== (pass === 1)) continue
        const a = q[sh[0]]; const b = q[sh[1]]; const r = Math.max(1, (sh[2] || 0) / 1000 * S); const c = striker ? SHAPE_STRIKER : SHAPE_BODY
        g.beginPath()
        if (sh[0] === sh[1]) g.arc(a[0], a[1], r, 0, 2 * Math.PI); else {
          const an = Math.atan2(b[1] - a[1], b[0] - a[0])
          g.arc(a[0], a[1], r, an + Math.PI / 2, an + 3 * Math.PI / 2); g.arc(b[0], b[1], r, an - Math.PI / 2, an + Math.PI / 2); g.closePath()
        }
        g.fillStyle = 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',' + (striker ? 0.3 : 0.14) + ')'; g.fill()
        g.strokeStyle = 'rgba(8,10,16,0.85)'; g.lineWidth = 6.5; g.stroke()
        g.strokeStyle = 'rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ')'; g.lineWidth = 3.5; g.stroke()
      }
    }
    g.strokeStyle = 'rgba(255,255,255,0.75)'; g.lineWidth = 2; g.beginPath()                  // bones
    for (const bn of bones) { if (!Array.isArray(bn) || !ok(bn[0]) || !ok(bn[1])) continue; g.moveTo(q[bn[0]][0], q[bn[0]][1]); g.lineTo(q[bn[1]][0], q[bn[1]][1]) }
    g.stroke()
    for (const pt of q) {                                                 // the 19 points
      g.beginPath(); g.arc(pt[0], pt[1], 6, 0, 7); g.fillStyle = 'rgba(8,10,16,0.9)'; g.fill()
      g.beginPath(); g.arc(pt[0], pt[1], 4, 0, 7); g.fillStyle = '#ffffff'; g.fill()
    }
  }
  g.restore()
}
function shapesLegend () {
  const items = [['body shapes', SHAPE_BODY], ['striking shapes', SHAPE_STRIKER], ['points', null]]
  g.font = '700 36px ' + FONT; g.textAlign = 'left'; g.textBaseline = 'middle'
  let w = 30; for (const it of items) w += 46 + g.measureText(it[0]).width + 30
  const x0 = 24; const y0 = 196
  g.fillStyle = 'rgba(8,8,14,0.72)'; g.beginPath(); rrect(x0, y0, w - 14, 58, 12); g.fill()
  let x = x0 + 18
  for (const [text, c] of items) {
    if (c) {
      g.fillStyle = 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',0.25)'; g.strokeStyle = 'rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ')'; g.lineWidth = 3.5
      g.beginPath(); rrect(x, y0 + 19, 34, 20, 10); g.fill(); g.stroke()
    } else {
      g.beginPath(); g.arc(x + 17, y0 + 29, 7, 0, 7); g.fillStyle = 'rgba(8,10,16,0.9)'; g.fill()
      g.beginPath(); g.arc(x + 17, y0 + 29, 5, 0, 7); g.fillStyle = '#ffffff'; g.fill()
    }
    g.fillStyle = '#f3efe6'; g.fillText(text, x + 46, y0 + 30)
    x += 46 + g.measureText(text).width + 30
  }
}

// ---------- head-up display
const HUD = { pad: 28, av: 96, gap: 20, bw: 620, bh: 34, sk: 14, y: 64 }
function avatarBox (who) { return { x: who === 1 ? W - HUD.pad - HUD.av : HUD.pad, y: 22, s: HUD.av } }
/** One player's side. o: { name, hp, ghost, st (percent or null), msg, msgAlpha, avatar } */
function hudSide (who, o) {
  const right = who === 1; const tone = TONE[who]; const d = right ? -1 : 1
  const { pad, bw, bh, sk, y } = HUD
  const av = o.avatar ? HUD.av : 0; const gap = o.avatar ? HUD.gap : 0
  const xo = right ? W - pad - av - gap : pad + av + gap                 // outer end of the bars
  const sh = yy => d * sk * (1 - (yy - y) / bh)                          // the bars lean toward the centre
  const quad = (xa, xb, ya, yb) => { g.beginPath(); g.moveTo(xa + sh(ya), ya); g.lineTo(xb + sh(ya), ya); g.lineTo(xb + sh(yb), yb); g.lineTo(xa + sh(yb), yb); g.closePath() }
  const at = (v, full) => xo + d * full * clamp(v, 0, 100) / 100         // 0 at the outer end
  if (o.avatar) {                                                        // frame of the avatar (the picture itself is a sprite above)
    const b = avatarBox(who)
    g.fillStyle = tone.color; g.beginPath(); rrect(b.x - 4, b.y - 4, b.s + 8, b.s + 8, 10); g.fill()
    g.fillStyle = '#0b0d13'; g.fillRect(b.x, b.y, b.s, b.s)
  }
  // HP
  quad(at(0, bw), at(100, bw), y, y + bh); g.fillStyle = 'rgba(0,0,0,0.62)'; g.fill()
  if (o.ghost > o.hp) { quad(at(o.hp, bw), at(o.ghost, bw), y, y + bh); g.fillStyle = 'rgba(255,225,190,0.6)'; g.fill() }   // recent loss, draining
  if (o.hp > 0) {
    const hg = g.createLinearGradient(0, y, 0, y + bh); hg.addColorStop(0, tone.hp[0]); hg.addColorStop(1, tone.hp[1])
    quad(at(0, bw), at(o.hp, bw), y, y + bh); g.fillStyle = hg; g.fill()
    quad(at(0, bw), at(o.hp, bw), y, y + 9); g.fillStyle = 'rgba(255,255,255,0.2)'; g.fill()
  }
  g.strokeStyle = 'rgba(0,0,0,0.38)'; g.lineWidth = 2; g.beginPath()
  for (let i = 1; i < 10; i++) { const c = at(i * 10, bw); g.moveTo(c + sh(y), y); g.lineTo(c + sh(y + bh), y + bh) }
  g.stroke()
  quad(at(0, bw), at(100, bw), y, y + bh); g.strokeStyle = 'rgba(255,255,255,0.3)'; g.lineWidth = 2; g.stroke()
  // name and HP number
  g.textBaseline = 'alphabetic'; g.fillStyle = '#f3efe6'; g.font = '800 36px ' + FONT
  g.textAlign = right ? 'left' : 'right'
  const hpText = Math.max(0, Math.round(o.hp)) + ' HP'
  g.fillText(hpText, at(100, bw) + sh(y), y - 11)
  const hpW = g.measureText(hpText).width
  spacing(1.5); g.textAlign = right ? 'right' : 'left'
  g.fillText(fit(o.name, bw - hpW - 40), xo + sh(y), y - 11)
  spacing(0)
  let ym = y + bh + 9
  if (o.st !== null) {                                                    // stamina
    const sw = bw - 112; const hh = 12
    quad(at(0, sw), at(100, sw), ym, ym + hh); g.fillStyle = 'rgba(0,0,0,0.62)'; g.fill()
    if (o.st > 0) { quad(at(0, sw), at(o.st, sw), ym, ym + hh); g.fillStyle = GOLD; g.fill() }
    g.font = '700 36px ' + FONT; g.textBaseline = 'middle'; g.textAlign = right ? 'right' : 'left'; g.fillStyle = 'rgba(240,206,120,0.95)'
    g.fillText(Math.round(o.st) + ' %', at(100, sw) + d * 16 + sh(ym + hh / 2), ym + hh / 2 + 1)
    ym += hh + 18
  }
  if (o.msg && o.msgAlpha > 0.01) {                                       // the bot's message: one calm line
    g.font = 'italic 600 36px ' + FONT; g.textBaseline = 'middle'; g.textAlign = right ? 'right' : 'left'
    const txt = fit(o.msg, bw - 10); const tw = g.measureText(txt).width + 32; const x0 = xo + sh(ym + 23) - d * 4
    g.globalAlpha = o.msgAlpha
    g.fillStyle = 'rgba(8,8,14,0.6)'; g.beginPath(); rrect(right ? x0 - tw : x0, ym, tw, 46, 10); g.fill()
    g.fillStyle = tone.label; g.fillText(txt, x0 + d * 16, ym + 24)
    g.globalAlpha = 1
  }
}
function hudTurn (n, total, te) {
  g.fillStyle = 'rgba(0,0,0,0.62)'; g.beginPath(); rrect(W / 2 - 170, 16, 340, 64, 12); g.fill()
  g.strokeStyle = 'rgba(255,255,255,0.24)'; g.lineWidth = 2; g.stroke()
  g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillStyle = '#f3efe6'; g.font = '800 36px ' + FONT; spacing(1.5)
  g.fillText('TURN ' + n + ' / ' + total, W / 2, 49)
  spacing(0)
  g.fillStyle = 'rgba(255,255,255,0.6)'; g.font = '600 36px ' + FONT; g.fillText((te * 0.025).toFixed(1) + ' s', W / 2, 106)
}

// ---------- the module
export class FightModule {
  constructor (assets) {
    this.players = []
    this.global = {}
    this.pos0 = Int16Array.from(DEFAULT_POS)
    this.base = this.pos0
    this.count = 0
    this.turns = []                       // one entry per key frame; turns[0] is the starting pose
    this.hits = []; this.falls = []; this.blocks = []; this.steps = []
    this.hpLine = [[], []]                // HP changes of each fighter: { t, hp, before }
    this.msgs = [[], []]                  // messages of each bot: { n, text }
    this.camS = []; this.camX = []        // eased camera, one sample per quarter-turn
    this.camFast = 0                      // samples left of the quick return after a reset
    this.looks = null
    this.end = null                       // { n, winner, how }
    this.stamina = false
    this.maxTurns = 250
    this.ringX = 3
    this.cur = null; this.progress = 0; this.tail = 0
    this.canvas = null; this.ctx = null; this.texture = null; this.sprite = null
    this.layers = null
    this.reactions = []; this.reactionsFor = -1
    this.dirty = true
    this.errors = 0
    this.perf = { frames: 0, ms: 0, max: 0, flush: false, lap: null }   // drawing time; flush and lap are profiling hooks, off in normal use
    shared.fight = this; shared.overlay = false
    viewerOptions.onChange = () => { this.dirty = true }
  }

  static get moduleName () { return 'fight' }

  report (e) { if (this.errors++ < 5) console.error('Byte Club viewer:', e) }

  handleGlobalData (players, globalData) {
    try {
      this.players = Array.isArray(players) ? players : []
      const gd = this.global = globalData || {}
      if (Array.isArray(gd.pos) && gd.pos.length === 76) this.pos0 = Int16Array.from(gd.pos)
      this.base = this.pos0
      this.stamina = !!gd.stamina
      this.maxTurns = gd.turns || 250
      this.ringX = (gd.ring || 3000) / 1000
      for (let who = 0; who < 2; who++) {
        const c = this.players[who] && this.players[who].color
        const hex = typeof c === 'number' ? '#' + ('000000' + (c & 0xffffff).toString(16)).slice(-6) : DEFAULT_COLORS[who]
        TONE[who] = makeTone(hex, who)
      }
    } catch (e) { this.report(e) }
  }

  handleFrameData (frameInfo, data) {
    const n = this.count++
    let turn = { n }
    try { turn = this.addTurn(n, data || {}) } catch (e) { this.report(e) }
    return turn
  }

  /** Stores key frame n (n >= 1: turn n) and extends the timelines. */
  addTurn (n, data) {
    const prev = this.turns[n - 1]
    const turn = { n, start: this.base, sub: [this.base, this.base, this.base, this.base], hp: prev ? prev.hp : [100, 100], st: prev ? prev.st : [1000, 1000], m: [null, null], reset: false, end: null }
    this.turns[n] = turn
    if (n === 0) { this.pushCam(this.base); return turn }
    const t0 = 4 * (n - 1)
    if (typeof data.p === 'string' && data.p.length >= 4 * this.base.length) turn.sub = unpack(data.p, this.base)
    if (Array.isArray(data.h)) turn.hp = [Number(data.h[0]) || 0, Number(data.h[1]) || 0]
    if (Array.isArray(data.s)) turn.st = [Number(data.s[0]) || 0, Number(data.s[1]) || 0]
    if (Array.isArray(data.m)) turn.m = [data.m[0], data.m[1]]
    if (Array.isArray(data.t)) for (let who = 0; who < 2; who++) if (typeof data.t[who] === 'string' && data.t[who].trim() !== '') this.msgs[who].push({ n, text: data.t[who] })
    if (Array.isArray(data.l)) this.looks = data.l
    const kinds = Array.isArray(this.global.kinds) ? this.global.kinds : []
    const kind = i => (typeof kinds[i] === 'string' ? kinds[i] : '?')
    const step = v => clamp(num(v, 39), 0, 39)                            // physics step of the turn, 40 in a turn
    const losses = []
    for (const e of Array.isArray(data.e) ? data.e : []) {
      if (!Array.isArray(e)) continue
      if (e[0] === 0) {
        const hit = { t: t0 + (step(e[1]) + 1) / 10, who: e[2] ? 1 : 0, with: kind(e[3]), on: kind(e[4]), dmg: num(e[5], 0), x: num(e[6], 0) / 1000, y: num(e[7], 1000) / 1000, v: num(e[8], 0) / 10, n }
        this.hits.push(hit)
        if (hit.dmg > 0) losses.push([hit.t, 1 - hit.who, hit.dmg])
      } else if (e[0] === 1) {
        const fall = { t: t0 + (step(e[1]) + 1) / 10, who: e[2] ? 1 : 0, out: e[3] === 1, free: e[4] === 1, point: e[5], n }
        this.falls.push(fall)
        if (!fall.free) losses.push([fall.t, fall.who, 20])
      } else if (e[0] === 2) {
        this.steps.push({ t: t0, who: e[1] ? 1 : 0, dir: e[2] < 0 ? -1 : 1, n })
      } else if (e[0] === 3) {
        this.blocks.push({ t: t0 + (step(e[1]) + 1) / 10, who: e[2] ? 1 : 0, x: num(e[3], 0) / 1000, y: num(e[4], 1000) / 1000, n })
      }
    }
    // HP line: each loss at the moment of its event, what the events do not explain at the end of the turn
    losses.sort((a, b) => a[0] - b[0])
    const hp = prev ? prev.hp.slice() : [100, 100]
    for (const [t, who, dmg] of losses) { this.hpLine[who].push({ t, hp: hp[who] - dmg, before: hp[who] }); hp[who] -= dmg }
    for (let who = 0; who < 2; who++) if (hp[who] !== turn.hp[who]) this.hpLine[who].push({ t: t0 + 4, hp: turn.hp[who], before: hp[who] })
    if (Array.isArray(data.x)) { this.end = { n, winner: data.x[0], how: data.x[1] }; turn.end = this.end }
    turn.reset = !!data.r
    for (let q = 0; q < 4; q++) this.pushCam(turn.sub[q])
    if (turn.reset) this.camFast = 5
    this.base = turn.reset ? this.pos0 : turn.sub[3]
    return turn
  }

  /** One more sample of the eased camera (zoom from the distance between the fighters, centre between them). */
  pushCam (pts) {
    const x0 = pts[PELVIS * 2] / 1000; const x1 = pts[(19 + PELVIS) * 2] / 1000; const d = Math.abs(x0 - x1)
    const tS = Math.max(250, Math.min(clamp(445 - 40 * d, 340, 410), W / (d + 1.9)))
    const tX = clamp((x0 + x1) / 2, -2.8, 2.8)
    const i = this.camS.length - 1
    if (i < 0) { this.camS.push(tS); this.camX.push(tX) } else {
      const fast = this.camFast > 0                                       // right after a reset the camera goes back quickly
      if (fast) this.camFast--
      this.camS.push(this.camS[i] + (tS - this.camS[i]) * (fast ? 0.45 : 0.074))
      this.camX.push(this.camX[i] + (tX - this.camX[i]) * (fast ? 0.5 : 0.175))
    }
  }

  setCamera (te) {
    const n = this.camS.length
    if (!n) { S = SREF; camX = 0 } else {
      const q = clamp(te, 0, n - 1); const i = Math.min(n - 1, Math.floor(q)); const j = Math.min(n - 1, i + 1); const f = q - i
      S = this.camS[i] + (this.camS[j] - this.camS[i]) * f
      camX = this.camX[i] + (this.camX[j] - this.camX[i]) * f
    }
    horizonY = FLOOR - CAM.H * S
  }

  /** HP of a fighter at time te, and the "ghost" of what it has just lost (held a moment, then drained). */
  hpAt (who, te) {
    const line = this.hpLine[who]
    let hp = 100; let ghost = 0
    for (let i = 0; i < line.length && line[i].t <= te; i++) hp = line[i].hp
    for (let i = line.length - 1; i >= 0; i--) {
      const s = line[i]; if (s.t > te) continue
      const age = te - s.t; if (age > 110) break
      if (s.before > s.hp) ghost = Math.max(ghost, s.before - Math.max(0, age - 8) * 1.1)
    }
    hp = Math.max(0, hp)
    return [hp, Math.max(hp, Math.min(100, ghost))]
  }

  messageAt (who, n, u) {
    const list = this.msgs[who]
    for (let i = list.length - 1; i >= 0; i--) {
      if (list[i].n > n) continue
      const age = n + u - list[i].n                                       // turns since the message was said
      return age < 15 ? { text: list[i].text, alpha: age < 12 ? 1 : 1 - (age - 12) / 3 } : null
    }
    return null
  }

  moveText (m) {
    if (m === -1) return { text: 'JOINTS', muted: false }
    const name = Array.isArray(this.global.moves) ? this.global.moves[m] : null
    if (typeof name !== 'string') return null
    return { text: name.replace(/_/g, ' '), muted: false }
  }

  /** The 19 points of a fighter (metres) at any match time, in quarter-turns. A time on a turn boundary belongs to the turn that ends. */
  pointsAt (te, who) {
    const N = this.turns.length - 1
    if (N < 1) return poseOf(this.turns[0], 0, who)
    if (!(te >= 0)) te = 0
    const n = clamp(Math.ceil(te / 4), 1, N)
    return poseOf(this.turns[n], clamp(te - 4 * (n - 1), 0, 4), who)
  }

  /** The event that took the last HP of a knockout: a hit or a paid fall. */
  lastLoss () {
    let last = null
    for (const h of this.hits) if (h.dmg > 0 && (!last || h.t >= last.t)) last = h
    for (const f of this.falls) if (!f.free && (!last || f.t >= last.t)) last = f
    return last
  }

  name (who) { return String((this.players[who] && this.players[who].name) || ('Player ' + (who + 1))).toUpperCase() }

  /** Quarter-turns since the fighters were last put back at their starting places, and the turn that ended so. */
  lastReset (n, local) {
    for (let k = 1; k <= 3 && n - k >= 1; k++) if (this.turns[n - k].reset) return { age: local + 4 * (k - 1), turn: this.turns[n - k] }
    return { age: 1e9, turn: null }
  }

  shakeAt (t) {
    let sx = 0; let sy = 0
    for (const e of this.hits) {
      const age = t - e.t; if (age < 0 || age >= 10 || e.dmg < 5) continue
      const A = (Math.min(e.dmg, 20) - 4) * 0.9 * (1 - age / 10) * S / S0
      sx += A * Math.sin(age * 7.3 + e.t); sy += A * 0.6 * Math.cos(age * 5.1 + e.t)
    }
    for (const e of this.falls) {
      const age = t - e.t; if (age < 0 || age >= 12) continue
      const A = 7 * K * (1 - age / 12); sy += A * Math.sin(age * 5.5); sx += A * 0.3 * Math.cos(age * 4)
    }
    return [sx, sy]
  }

  /** Extra red of the two ring-out zones (left, right) while someone has just left the ring. */
  outFlash (t) {
    const a = [0, 0]
    for (const e of this.falls) {
      if (!e.out) continue
      const age = t - e.t; if (age < 0 || age >= BAN.life) continue
      const side = this.pointsAt(e.t, e.who).pelvis[0] < 0 ? 0 : 1
      a[side] = Math.max(a[side], 0.42 * (1 - age / BAN.life) * (0.62 + 0.38 * Math.cos(age * 0.8)))
    }
    return a
  }

  /** The banners of the falls and ring-outs going on: at most one for each fighter. */
  bannersAt (t) {
    const list = []
    for (let i = this.falls.length - 1; i >= 0 && list.length < 2; i--) {
      const e = this.falls[i]; const age = t - e.t
      if (age < 0) continue
      if (age >= BAN.life) break
      if (list.some(b => b.who === e.who)) continue
      const points = Array.isArray(this.global.points) ? this.global.points : []
      const part = typeof points[e.point] === 'string' ? points[e.point].toLowerCase().replace(/_/g, ' ') : null
      list.push({
        who: e.who,
        alpha: Math.min(1, age / 3) * (age > BAN.life - 8 ? (BAN.life - age) / 8 : 1),
        color: TONE[e.who].label,
        big: this.name(e.who) + (e.out ? ' RING OUT' : ' DOWN'),
        cost: e.free ? 'no HP lost' : '−20 HP',
        costColor: e.free ? 'rgba(255,255,255,0.72)' : '#ff8a5c',
        small: e.out ? 'left the ring' : part ? part + ' on the mat' : null
      })
    }
    return list.reverse()
  }

  /**
   * The result of the match: { alpha, color, big, small } or null. It comes in at the moment that decides the match (the
   * knockout, the end of the last turn, the start of the turn of an elimination); with "early", it is complete at the end
   * of the last turn at the latest (for when nothing can be drawn above the end screen).
   */
  finalAt (n, u, early) {
    const e = this.end; const N = this.turns.length - 1
    if (!e || n !== N || N < 1) return null
    const tf = 4 * (n - 1 + u) + this.tail
    const ko = e.how === 1 ? this.lastLoss() : null
    let t0 = e.how === 2 ? 4 * (N - 1) : ko ? Math.min(ko.t, 4 * N) : 4 * N
    if (early) t0 = Math.min(t0, 4 * N - 3)
    const alpha = clamp((tf - t0) / 3, 0, 1)
    if (alpha <= 0) return null
    const hp = who => Math.max(0, Math.round(this.turns[N].hp[who]))
    const w = e.winner === 0 || e.winner === 1 ? e.winner : -1
    const o = { alpha, color: w < 0 ? GOLD : TONE[w].label, big: '', small: null }
    // how: 0 time, 1 knockout, 2 eliminated, 3 no contact for too long, 4 an error of the referee
    const why = e.how === 3 ? 'No contact' : 'Time'
    if (e.how === 4) {
      o.big = 'DRAW'; o.small = 'the referee stopped the match'
    } else if (e.how === 2) {
      if (w < 0) { o.big = 'BOTH PLAYERS ELIMINATED'; o.small = 'no winner' } else { o.big = this.name(1 - w) + ' ELIMINATED'; o.small = this.name(w) + ' wins' }
    } else if (w < 0) {
      o.big = 'DRAW'; o.small = e.how === 1 ? 'double knockout' : hp(0) === hp(1) ? why + ': ' + hp(0) + ' HP each' : why + ': ' + hp(0) + ' HP to ' + hp(1)
    } else if (e.how === 1) {
      o.big = this.name(w) + ' WINS BY KNOCKOUT'; o.small = hp(w) + ' HP left'
    } else {
      o.big = why.toUpperCase() + ': ' + this.name(w) + ' WINS'; o.small = hp(w) + ' HP to ' + hp(1 - w)
    }
    return o
  }

  /**
   * Texts of events that land close together: the number of a hit steps aside for each newer hit, its small line gives way to the new one,
   * and a "BLOCK" next to a hit goes under its spark. Decided from the data alone, so that a frame always looks the same.
   */
  layoutTexts () {
    const close = (a, b, dx, dy) => Math.abs(a.x - b.x) < dx && Math.abs(a.y - b.y) < dy
    for (const h of this.hits) {
      h.cut = Infinity; h.push = []
      for (const o of this.hits) {
        if (!(o.t > h.t)) continue
        if (o.t - h.t < 26 && close(h, o, 0.6, 0.45)) h.push.push(o.t)
        if (o.t - h.t < 18 && o.t < h.cut && close(h, o, 0.8, 0.3)) h.cut = o.t
      }
    }
    for (const b of this.blocks) {
      b.low = false
      for (const h of this.hits) if (h.t > b.t - 26 && h.t < b.t + 14 && close(b, h, 0.75, 0.45)) { b.low = true; break }
    }
  }

  /** Crowd reactions: hits of 8+ HP, falls, ring-outs, and the knockout (bigger, it runs past the end). */
  buildReactions () {
    const list = []
    const ko = this.end && this.end.how === 1 ? this.lastLoss() : null
    const add = (ev, kind, st, x) => {
      const isKo = kind === 'ko'
      const r = { f: ev.t, st, ko: isKo, len: isKo ? 70 : kind === 'hit' ? 8 + 6 * st : 14, xl: x * 0.6 * SREF, flashes: [] }
      const rnd = seed(Math.round(ev.t * 10) * 7 + 3)
      const nF = isKo ? 170 : kind === 'hit' ? 6 + Math.round(8 * st) : 12; const spread = (isKo ? 900 : 300) * K; const span = isKo ? 85 : 18
      for (let i = 0; i < nF; i++) {
        const row = Math.floor(rnd() * 14); const k = row < 3 ? 0.6 : row < 7 ? 0.48 : 0.36; const gauss = (rnd() + rnd() + rnd() - 1.5) * 1.6
        r.flashes.push({ x: x * k * SREF + gauss * spread, y: rowY(row) - 14 * K * rowSc(row) + (rnd() - 0.5) * 10 * K, k, s: ev.t + rnd() * span, d: 2 + rnd() * 2, size: (1.6 + rnd() * 2) * (0.5 + rowSc(row)) * (isKo ? 1.4 : 1) })
      }
      list.push(r)
    }
    for (const h of this.hits) {
      if (h === ko) add(h, 'ko', 1, h.x); else if (h.dmg >= 8) add(h, 'hit', 0.5 + 0.5 * Math.min(1, (h.dmg - 8) / 7), h.x)
    }
    for (const f of this.falls) add(f, f === ko ? 'ko' : 'down', f === ko ? 1 : 0.8, this.pointsAt(f.t, f.who).pelvis[0])
    list.sort((a, b) => a.f - b.f)
    return list
  }

  reinitScene (container, canvasData) {
    try {
      const size = drawingSize(canvasData); const cw = size.w; const ch = size.h
      if (this.texture) { try { this.texture.destroy(true) } catch (e) { /* already gone */ } }
      this.canvas = document.createElement('canvas'); this.canvas.width = cw; this.canvas.height = ch
      this.ctx = this.canvas.getContext('2d')
      const exact = size.doubled && PIXI.SCALE_MODES && PIXI.SCALE_MODES.NEAREST !== undefined   // doubled pixels must stay whole
      this.texture = exact ? PIXI.Texture.from(this.canvas, { scaleMode: PIXI.SCALE_MODES.NEAREST }) : PIXI.Texture.from(this.canvas)
      this.sprite = new PIXI.Sprite(this.texture)
      this.kx = size.kx; this.ky = size.ky
      this.sprite.scale.set(1 / size.kx, 1 / size.ky)
      container.addChild(this.sprite)
      this.avatars = [false, false]
      for (let who = 0; who < 2; who++) {                                 // avatars: sprites above the drawing (their pixels may not be copied to a 2D canvas)
        const tex = this.players[who] && this.players[who].avatar
        if (!tex || !tex.valid) continue
        const b = avatarBox(who); const sp = new PIXI.Sprite(tex)
        sp.position.set(b.x, b.y); sp.width = b.s; sp.height = b.s
        container.addChild(sp)
        this.avatars[who] = true
      }
      this.layers = null
      this.dirty = true
    } catch (e) { this.report(e) }
  }

  updateScene (previousData, currentData, progress, speed) {
    this.cur = currentData
    this.progress = progress
    this.dirty = true
  }

  animateScene (step) {
    try {
      const N = this.turns.length - 1
      const atEnd = !!this.cur && N >= 1 && this.cur.n === N && this.progress >= 1
      if (atEnd) {
        if (this.tail < TAIL) { this.tail = Math.min(TAIL, this.tail + (step > 0 ? step : 0) / 25); this.dirty = true }
      } else if (this.tail !== 0) { this.tail = 0; this.dirty = true }
      if (this.dirty) this.render()
    } catch (e) { this.report(e) }
  }

  render () {
    if (!this.canvas || !this.ctx || !this.texture) return
    const t0 = performance.now()
    this.dirty = false
    g = this.ctx
    PXS = this.kx > 0 ? this.kx : this.canvas.width / W; PYS = this.ky > 0 ? this.ky : this.canvas.height / H
    g.save()                                                             // every frame starts from the same state of the context (line ends and joins, fonts...):
    try {                                                                // nothing left by the frame drawn before may change a pixel
      this.drawScene()
      g.restore()
    } catch (e) {
      this.report(e)
      this.canvas.width = this.canvas.width                              // back to a clean state for the next frame
    }
    mode = 'fill'
    g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'
    if (this.perf.flush) { try { g.getImageData(0, 0, 1, 1) } catch (e) { /* measuring only */ } }   // makes the browser paint now, so that the time below holds it
    this.texture.update()
    const dt = performance.now() - t0
    this.perf.frames++; this.perf.ms += dt; if (dt > this.perf.max) this.perf.max = dt
  }

  drawScene () {
    const N = this.turns.length - 1
    sceneTransform(0, 0)
    if (N < 0) { g.fillStyle = '#0b0e14'; g.fillRect(0, 0, W, H); return }
    const n = clamp((this.cur && this.cur.n) | 0, 0, N); const u = clamp(this.progress || 0, 0, 1)
    const turn = this.turns[n]; const local = 4 * u
    const te = n === 0 ? 0 : 4 * (n - 1 + u)
    const tf = te + this.tail                                             // effects and crowd go on a little after the end
    if (!this.layers) this.layers = { backdrop: buildBackdrop(), crowd: buildCrowd(), veil: buildVeil(), scratch: null }
    if (this.reactionsFor !== this.count) { this.reactions = this.buildReactions(); this.layoutTexts(); this.reactionsFor = this.count }
    applyLooks(this.looks)
    this.setCamera(te)
    const pm = [poseOf(turn, local, 0), poseOf(turn, local, 1)]
    const moves = Array.isArray(this.global.moves) ? this.global.moves : []
    const bans = this.bannersAt(tf)
    const fin = shared.overlay ? null : this.finalAt(n, u, true)

    // the scene, moved by the camera shake
    const sh = this.shakeAt(tf)
    if (sh[0] || sh[1]) { g.fillStyle = '#06070b'; g.fillRect(0, 0, W, H) }
    sceneTransform(sh[0], sh[1])
    const lap = this.perf.lap                                             // profiling hook, null in normal use
    if (lap) lap('start')
    const bd = this.layers.backdrop
    blit(bd.img, -bd.px - camX * 0.2 * S, 0)
    floor(this.ringX)
    if (lap) lap('backdrop + floor')
    drawCrowd(this.layers.crowd, this.reactions, tf, X((pm[0].pelvis[0] + pm[1].pelvis[0]) / 2))
    if (lap) lap('crowd')
    ring(this.ringX, this.outFlash(tf))
    stepCues(this, tf)
    shadows(pm)
    if (lap) lap('ring + shadows')
    const reset = this.lastReset(n, local)
    if (reset.turn && reset.age < 10) this.resetGhost(reset.turn, 0.55 * Math.pow(1 - reset.age / 10, 1.5))
    const fade = clamp(1 - this.tail / 8, 0, 1)
    const fast = n > 0 && te >= 7 && reset.age >= 6.5 && fade > 0
    if (fast) afterimages(this, te, turn, local, fade)
    if (lap) lap('ghost + afterimages')
    for (let who = 0; who < 2; who++) fighter(who, pm[who], n > 0 && moves[turn.m[who]] === 'HIGH_KICK')
    if (lap) lap('fighters')
    if (fast) trails(this, te, turn, local, fade)
    if (viewerOptions.shapes) shapesOverlay(pm, Array.isArray(this.global.shapes) ? this.global.shapes : [], Array.isArray(this.global.bones) ? this.global.bones : [], Array.isArray(this.global.kinds) ? this.global.kinds : [])
    effects(this, tf)
    if (n > 0) labels(pm, [this.moveText(turn.m[0]), this.moveText(turn.m[1])])
    if (lap) lap('trails + effects + labels')

    // the head-up display, still
    sceneTransform(0, 0)
    blit(this.layers.veil, 0, 0)
    const prev = this.turns[Math.max(0, n - 1)]
    for (let who = 0; who < 2; who++) {
      const [hp, ghost] = this.hpAt(who, tf)
      const st = this.stamina ? (prev.st[who] + (turn.st[who] - prev.st[who]) * u) / 10 : null
      const msg = this.messageAt(who, n, u)
      hudSide(who, { name: this.name(who), hp, ghost, st, msg: msg && msg.text, msgAlpha: msg ? msg.alpha : 0, avatar: this.avatars && this.avatars[who] })
    }
    hudTurn(n, this.maxTurns, te)
    for (let i = 0; i < bans.length; i++) banner(BAN.y - (bans.length - 1 - i) * (2 * BAN.h + 8), bans[i])
    if (fin) finalBanner(fin)
    if (viewerOptions.shapes) shapesLegend()
    if (lap) lap('hud + banners')
  }

  /** Right after a reset: a pale image of where the fighters were, fading. */
  resetGhost (turn, alpha) {
    if (alpha <= 0.01) return
    const cw = this.canvas.width; const ch = this.canvas.height
    let sc = this.layers.scratch
    if (!sc || sc.canvas.width !== cw || sc.canvas.height !== ch) {
      const c = document.createElement('canvas'); c.width = cw; c.height = ch
      sc = this.layers.scratch = { canvas: c, ctx: c.getContext('2d') }
    }
    const main = g
    try {
      g = sc.ctx
      g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'; g.clearRect(0, 0, cw, ch)
      g.setTransform(PXS, 0, 0, PYS, shX * PXS, shY * PYS)
      for (let who = 0; who < 2; who++) fighter(who, poseOf(turn, 4, who), false)
      g.setTransform(1, 0, 0, 1, 0, 0); g.globalCompositeOperation = 'source-atop'; g.fillStyle = 'rgba(226,234,255,0.6)'; g.fillRect(0, 0, cw, ch)
      g.globalCompositeOperation = 'source-over'
    } finally { g = main; mode = 'fill' }
    g.save(); g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = alpha; g.drawImage(sc.canvas, 0, 0); g.restore()
  }
}

/**
 * The result of the match, drawn above everything: listed after the end screen in config.js, this module puts the line
 * "NAME wins by knockout" over the dark veil of the end screen, under its ranking.
 */
export class FightOverlayModule {
  constructor (assets) {
    this.count = 0
    this.cur = null; this.progress = 0
    this.canvas = null; this.ctx = null; this.texture = null; this.sprite = null
    this.dirty = true; this.shown = ''
    this.errors = 0
  }

  static get moduleName () { return 'fightOverlay' }

  report (e) { if (this.errors++ < 5) console.error('Byte Club viewer (overlay):', e) }

  handleFrameData (frameInfo, data) { return { n: this.count++ } }

  reinitScene (container, canvasData) {
    try {
      const cw = clamp(Math.round((canvasData && canvasData.width) || W), 160, W)
      const ch = clamp(Math.round((canvasData && canvasData.height) || H), 90, H)
      if (this.texture) { try { this.texture.destroy(true) } catch (e) { /* already gone */ } }
      this.scale = [cw / W, ch / H]
      this.canvas = document.createElement('canvas'); this.canvas.width = cw; this.canvas.height = Math.ceil((2 * FIN.h + 8) * ch / H)
      this.ctx = this.canvas.getContext('2d')
      this.texture = PIXI.Texture.from(this.canvas)
      this.sprite = new PIXI.Sprite(this.texture)
      this.sprite.scale.set(W / cw, H / ch)
      this.sprite.position.set(0, FIN.y - FIN.h - 4)
      this.sprite.visible = false
      container.addChild(this.sprite)
      this.dirty = true; this.shown = ''
      shared.overlay = true
      if (shared.fight) shared.fight.dirty = true
    } catch (e) { this.report(e); shared.overlay = false }
  }

  updateScene (previousData, currentData, progress, speed) {
    this.cur = currentData
    this.progress = progress
    this.dirty = true
  }

  animateScene (step) {
    if (!this.sprite || !this.ctx) return
    try {
      const fight = shared.fight
      const o = fight && this.cur ? fight.finalAt(this.cur.n | 0, clamp(this.progress || 0, 0, 1), false) : null
      const key = o ? [o.alpha.toFixed(3), o.color, o.big, o.small].join('|') : ''
      if (key === this.shown) return
      this.shown = key
      this.sprite.visible = !!o
      if (!o) return
      g = this.ctx; PXS = this.scale[0]; PYS = this.scale[1]
      g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.clearRect(0, 0, this.canvas.width, this.canvas.height)
      g.setTransform(PXS, 0, 0, PYS, 0, -(FIN.y - FIN.h - 4) * PYS)
      finalBanner(o)
      g.setTransform(1, 0, 0, 1, 0, 0)
      this.texture.update()
    } catch (e) {
      this.report(e)
      shared.overlay = false                                              // the fight module draws the result itself
      if (this.sprite) this.sprite.visible = false
      if (shared.fight) shared.fight.dirty = true
    }
  }
}
