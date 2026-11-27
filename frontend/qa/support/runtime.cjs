const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { chromium } = require('playwright-core');

const artifactRoot = path.resolve(process.env.ZAYLOPE_QA_ARTIFACTS || path.join(__dirname, '..', 'artifacts'));
function artifactPath(name) {
  const clean = String(name).replace(/^qa[\\/]/, '');
  const destination = path.resolve(artifactRoot, clean);
  if (destination !== artifactRoot && !destination.startsWith(artifactRoot + path.sep)) throw new Error('QA artifact must stay inside the configured artifact directory');
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  return destination;
}
function browserExecutable() {
  const configured = process.env.CHROME_PATH || process.env.BROWSER_EXECUTABLE_PATH;
  if (configured) {
    if (!fs.existsSync(configured)) throw new Error('Configured browser executable does not exist');
    return configured;
  }
  const candidates = [chromium.executablePath()];
  const cache = process.env.PLAYWRIGHT_BROWSERS_PATH || (process.platform === 'win32'
    ? path.join(process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local'), 'ms-playwright')
    : process.platform === 'darwin' ? path.join(os.homedir(), 'Library', 'Caches', 'ms-playwright') : path.join(os.homedir(), '.cache', 'ms-playwright'));
  if (fs.existsSync(cache)) for (const entry of fs.readdirSync(cache).filter(name => name.startsWith('chromium-')).sort((a,b) => b.localeCompare(a, undefined, {numeric:true}))) {
    for (const relative of ['chrome-win64/chrome.exe','chrome-win/chrome.exe','chrome-linux64/chrome','chrome-linux/chrome','chrome-mac/Chromium.app/Contents/MacOS/Chromium']) candidates.push(path.join(cache, entry, relative));
  }
  const found = candidates.find(candidate => fs.existsSync(candidate));
  if (!found) throw new Error('Chromium is unavailable. Install a compatible browser or set CHROME_PATH. No browser is downloaded automatically.');
  return found;
}
function qaUrl(suffix = '') { return (process.env.ZAYLOPE_QA_ORIGIN || 'http://localhost:5173').replace(/\/$/, '') + suffix; }
module.exports = { artifactPath, artifactRoot, browserExecutable, qaUrl };
