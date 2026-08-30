#!/usr/bin/env node
// Standalone asset-generation script. Run manually:
//   node scripts/generate-assets.js
// Never imported by the app — sharp is a devDependency for exactly this reason.

const path = require('path');
const sharp = require('sharp');

// Exact values from src/constants/index.js COLORS — confirmed, not placeholders.
const BG_HEX = '#f5f2ee';
const INK_HEX = '#1a1612';
const ACCENT_HEX = '#e8622a';

const ASSETS_DIR = path.join(__dirname, '..', 'assets');

// Receipt glyph defined once in a 200x224 local coordinate system, reused at
// every output size via a scale/translate transform on the wrapping <g>.
const GLYPH_W = 200;
const GLYPH_H = 224;
const GLYPH_PATH_D = [
  'M 34 10',
  'L 166 10',
  'A 14 14 0 0 1 180 24',
  'L 180 200',
  'L 170 214', 'L 160 200', 'L 150 214', 'L 140 200',
  'L 130 214', 'L 120 200', 'L 110 214', 'L 100 200',
  'L 90 214', 'L 80 200', 'L 70 214', 'L 60 200',
  'L 50 214', 'L 40 200', 'L 30 214', 'L 20 200',
  'L 20 24',
  'A 14 14 0 0 1 34 10',
  'Z',
].join(' ');

// lineColor: contrast color for the 3 internal "text" bars — a pure white
// line on the white receipt body would be invisible, so these render in
// ACCENT_HEX against the white body (interpreting "white-on-accent lines" as
// describing the icon's overall white-glyph-on-accent-background style, not
// a literal white-on-white line color).
function glyphGroup({ scale, offsetX, offsetY, bodyColor, lineColor, strokeColor }) {
  const lines = lineColor
    ? [58, 90, 122]
        .map((y) => `<rect x="55" y="${y}" width="90" height="12" rx="6" fill="${lineColor}" />`)
        .join('')
    : '';
  const stroke = strokeColor
    ? ` stroke="${strokeColor}" stroke-width="3" stroke-opacity="0.15"`
    : '';
  return (
    `<g transform="translate(${offsetX} ${offsetY}) scale(${scale})">` +
    `<path d="${GLYPH_PATH_D}" fill="${bodyColor}"${stroke} />${lines}</g>`
  );
}

function fitScale(targetW, targetH, marginFactor = 1) {
  return Math.min(targetW / GLYPH_W, targetH / GLYPH_H) * marginFactor;
}

function glyphOffsets(canvasSize, scale) {
  const w = GLYPH_W * scale;
  const h = GLYPH_H * scale;
  return { offsetX: (canvasSize - w) / 2, offsetY: (canvasSize - h) / 2 };
}

async function writeAsset(name, size, svg) {
  const outPath = path.join(ASSETS_DIR, name);
  await sharp(Buffer.from(svg)).png().toFile(outPath);
  console.log(`Wrote ${name} (${size}x${size}) -> ${outPath}`);
}

async function main() {
  // 1. icon.png — 1024x1024, full bleed. OS applies its own corner mask, so
  // the accent background is safe to run edge to edge.
  {
    const canvas = 1024;
    const scale = (canvas * 0.55) / GLYPH_W;
    const { offsetX, offsetY } = glyphOffsets(canvas, scale);
    const svg =
      `<svg xmlns="http://www.w3.org/2000/svg" width="${canvas}" height="${canvas}">` +
      `<rect width="${canvas}" height="${canvas}" fill="${ACCENT_HEX}" />` +
      glyphGroup({ scale, offsetX, offsetY, bodyColor: '#ffffff', lineColor: ACCENT_HEX, strokeColor: INK_HEX }) +
      `</svg>`;
    await writeAsset('icon.png', canvas, svg);
  }

  // 2. adaptive-icon.png — 1024x1024, transparent. Glyph confined to the
  // center 660x660 (66%) safe zone with an extra 8% margin inside that, since
  // Android's launcher mask can crop right up to the safe-zone edge.
  {
    const canvas = 1024;
    const safeZone = canvas * 0.66;
    const scale = fitScale(safeZone, safeZone, 0.92);
    const { offsetX, offsetY } = glyphOffsets(canvas, scale);
    const svg =
      `<svg xmlns="http://www.w3.org/2000/svg" width="${canvas}" height="${canvas}">` +
      glyphGroup({ scale, offsetX, offsetY, bodyColor: '#ffffff', lineColor: ACCENT_HEX, strokeColor: INK_HEX }) +
      `</svg>`;
    await writeAsset('adaptive-icon.png', canvas, svg);
  }

  // 3. splash-icon.png — 400x400, transparent. Meant to sit via 'contain'
  // resizeMode against app.json's splash backgroundColor (BG_HEX).
  {
    const canvas = 400;
    const scale = (canvas * 0.55) / GLYPH_W;
    const { offsetX, offsetY } = glyphOffsets(canvas, scale);
    const svg =
      `<svg xmlns="http://www.w3.org/2000/svg" width="${canvas}" height="${canvas}">` +
      glyphGroup({ scale, offsetX, offsetY, bodyColor: '#ffffff', lineColor: ACCENT_HEX, strokeColor: INK_HEX }) +
      `</svg>`;
    await writeAsset('splash-icon.png', canvas, svg);
  }

  // 4. favicon.png — 48x48, full bleed, simplified: no internal lines or
  // stroke detail, since both would just be noise at this size.
  {
    const canvas = 48;
    const scale = (canvas * 0.6) / GLYPH_W;
    const { offsetX, offsetY } = glyphOffsets(canvas, scale);
    const svg =
      `<svg xmlns="http://www.w3.org/2000/svg" width="${canvas}" height="${canvas}">` +
      `<rect width="${canvas}" height="${canvas}" fill="${ACCENT_HEX}" />` +
      glyphGroup({ scale, offsetX, offsetY, bodyColor: '#ffffff', lineColor: null, strokeColor: null }) +
      `</svg>`;
    await writeAsset('favicon.png', canvas, svg);
  }

  // BG_HEX is not rasterized into any of these (all glyph canvases are either
  // accent-filled or transparent) — it's the color the *composite* splash
  // screen and Android background layer are expected to use, set in app.json
  // / android-icon-background.png, not baked into these PNGs.
  void BG_HEX;
}

main().catch((err) => {
  console.error('Asset generation failed:', err);
  process.exitCode = 1;
});
