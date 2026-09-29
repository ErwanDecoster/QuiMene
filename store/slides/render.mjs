// Slides des fiches App Store et Play Store (doc 10 « Captures des stores ») : chaque capture de
// store-screenshots/ est posée dans un cadre d'appareil, sous un titre traduit (captions.json),
// puis rendue en PNG par Chrome sans interface ; plus l'image de présentation du Play Store. Aucune dépendance : Node 22+ (WebSocket natif) et
// Google Chrome suffisent.
//
//   node store/slides/render.mjs <dossier des captures> <dossier des slides>

import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const [input, output] = process.argv.slice(2).map((dir) => path.resolve(dir));
if (!input || !output) {
  console.error('usage : node store/slides/render.mjs <captures> <slides>');
  process.exit(2);
}

const CHROME = process.env.CHROME ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

// Formats exigés par chaque store. App Store : tailles exactes des captures iPhone 6,9" et
// iPad 13". Play Store : 9:16, le format que la Play Console met en avant, pour les trois
// appareils. Les échelles sont des fractions de la largeur de la slide.
const FORMATS = [
  { store: 'app-store', device: 'iphone-6.9', platform: 'ios', frame: 'iphone', width: 1320, height: 2868,
    titleScale: 0.08, subtitleScale: 0.038, textWidth: 0.86, deviceScale: 0.8 },
  { store: 'app-store', device: 'ipad-13', platform: 'ios', frame: 'ipad', width: 2064, height: 2752,
    titleScale: 0.056, subtitleScale: 0.027, textWidth: 0.74, deviceScale: 0.9 },
  { store: 'play-store', device: 'phone', platform: 'android', frame: 'android-phone', width: 1440, height: 2560,
    titleScale: 0.078, subtitleScale: 0.037, textWidth: 0.86, deviceScale: 0.72, statusBarRatio: 24 / 400 },
  { store: 'play-store', device: 'tablet-7', platform: 'android', frame: 'android-tablet', width: 1440, height: 2560,
    titleScale: 0.078, subtitleScale: 0.037, textWidth: 0.86, deviceScale: 0.92, statusBarRatio: 24 / 600 },
  { store: 'play-store', device: 'tablet-10', platform: 'android', frame: 'android-tablet', width: 1440, height: 2560,
    titleScale: 0.078, subtitleScale: 0.037, textWidth: 0.86, deviceScale: 0.92, statusBarRatio: 24 / 800 },
];

const captions = JSON.parse(readFileSync(path.join(here, 'captions.json'), 'utf8'));
// Wordmark vectorisé de l'app (charte §11.2), recoloré par le texte de la slide.
const wordmark = readFileSync(
  path.join(repo, 'apple/QuiMeneKit/Sources/DesignSystem/Resources/Assets.xcassets/logo/wordmark.imageset/wordmark.svg'),
  'utf8',
).replace(/fill="#000000"/g, 'fill="currentColor"').replace('<svg ', '<svg aria-hidden="true" ');

class DevTools {
  constructor(url) {
    this.socket = new WebSocket(url);
    this.nextID = 0;
    this.pending = new Map();
    this.socket.addEventListener('message', (event) => {
      const message = JSON.parse(event.data);
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      if (message.error) pending.reject(new Error(message.error.message));
      else pending.resolve(message.result);
    });
  }

  opened() {
    return new Promise((resolve, reject) => {
      this.socket.addEventListener('open', resolve, { once: true });
      this.socket.addEventListener('error', reject, { once: true });
    });
  }

  send(method, params = {}) {
    const id = ++this.nextID;
    this.socket.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }

  async evaluate(expression) {
    const { result, exceptionDetails } = await this.send('Runtime.evaluate', {
      expression, awaitPromise: true, returnByValue: true,
    });
    if (exceptionDetails) throw new Error(exceptionDetails.exception?.description ?? exceptionDetails.text);
    return result.value;
  }
}

async function launchChrome() {
  const profile = mkdtempSync(path.join(tmpdir(), 'quimene-slides-'));
  const chrome = spawn(CHROME, [
    '--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`,
    '--hide-scrollbars', '--force-color-profile=srgb', '--no-first-run', 'about:blank',
  ], { stdio: ['ignore', 'ignore', 'pipe'] });
  const browserURL = await new Promise((resolve, reject) => {
    let log = '';
    chrome.stderr.on('data', (chunk) => {
      log += chunk;
      const match = log.match(/DevTools listening on (ws:\/\/\S+)/);
      if (match) resolve(match[1]);
    });
    chrome.on('exit', (code) => reject(new Error(`Chrome s'est arrêté (${code}) :\n${log}`)));
  });
  const { port } = new URL(browserURL);
  const pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const page = new DevTools(pages.find((target) => target.type === 'page').webSocketDebuggerUrl);
  await page.opened();
  // Chrome écrit encore dans son profil juste après le signal : attendre qu'il soit arrêté.
  const close = async () => {
    const exited = new Promise((resolve) => chrome.once('exit', resolve));
    chrome.kill();
    await exited;
    rmSync(profile, { recursive: true, force: true, maxRetries: 5 });
  };
  return { page, close };
}

const { page, close } = await launchChrome();
try {
  await page.send('Page.navigate', { url: pathToFileURL(path.join(here, 'slide.html')).href });
  while (!(await page.evaluate("document.readyState === 'complete' && typeof renderSlide === 'function'"))) {
    await new Promise((resolve) => setTimeout(resolve, 50));
  }

  let count = 0;
  for (const format of FORMATS) {
    const storeDir = path.join(input, format.store);
    if (!existsSync(storeDir)) continue;
    await page.send('Emulation.setDeviceMetricsOverride', {
      width: format.width, height: format.height, deviceScaleFactor: 1, mobile: false,
    });
    for (const locale of readdirSync(storeDir).filter((name) => !name.startsWith('.')).sort()) {
      const sourceDir = path.join(storeDir, locale, format.device);
      if (!existsSync(sourceDir)) continue;
      const texts = captions[locale];
      if (!texts) throw new Error(`Aucune légende pour ${locale} dans captions.json`);
      const screens = readdirSync(sourceDir).filter((name) => name.endsWith('.png')).sort();
      for (const [index, file] of screens.entries()) {
        const screen = path.basename(file, '.png');
        const caption = texts[screen];
        if (!caption) throw new Error(`Aucune légende pour ${locale}/${screen} dans captions.json`);
        await page.evaluate(`renderSlide(${JSON.stringify({
          ...format, ...caption, lockup: index === 0, wordmark,
          image: pathToFileURL(path.join(sourceDir, file)).href,
        })})`);
        const { data } = await page.send('Page.captureScreenshot', {
          format: 'png', clip: { x: 0, y: 0, width: format.width, height: format.height, scale: 1 },
        });
        const destination = path.join(output, format.store, locale, format.device);
        mkdirSync(destination, { recursive: true });
        writeFileSync(path.join(destination, file), Buffer.from(data, 'base64'));
        count += 1;
      }
    }
  }

  // Image de présentation Play Store, une par langue : logo et accroche de la première slide.
  const feature = { width: 1024, height: 500 };
  await page.send('Emulation.setDeviceMetricsOverride', { ...feature, deviceScaleFactor: 1, mobile: false });
  for (const [locale, texts] of Object.entries(captions)) {
    await page.evaluate(`renderFeatureGraphic(${JSON.stringify({ ...feature, tagline: texts['01-partie'].title, wordmark })})`);
    const { data } = await page.send('Page.captureScreenshot', {
      format: 'png', clip: { x: 0, y: 0, ...feature, scale: 1 },
    });
    const destination = path.join(output, 'play-store', locale);
    mkdirSync(destination, { recursive: true });
    writeFileSync(path.join(destination, 'feature-graphic.png'), Buffer.from(data, 'base64'));
    count += 1;
  }
  console.log(`${count} images dans ${output}`);
} finally {
  await close();
}
