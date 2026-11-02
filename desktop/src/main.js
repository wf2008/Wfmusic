const { app, BrowserWindow, ipcMain, dialog, session } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { spawn } = require('child_process');

let win;
const libraryDir = () => path.join(app.getPath('music'), 'Wfmusic');
const binDir = path.join(__dirname, '..', 'bin');
const exe = name => process.platform === 'win32' ? `${name}.exe` : name;
const tool = name => {
  const bundled = path.join(binDir, exe(name));
  if (fs.existsSync(bundled)) return bundled;
  return name;
};
function ensureLibrary() { fs.mkdirSync(libraryDir(), { recursive: true }); }
function send(channel, payload) { if (win && !win.isDestroyed()) win.webContents.send(channel, payload); }
function run(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { windowsHide: true });
    let out = '', err = '';
    child.stdout.on('data', d => { out += d.toString(); });
    child.stderr.on('data', d => { err += d.toString(); });
    child.on('error', reject);
    child.on('close', code => code === 0 ? resolve(out) : reject(new Error(err.trim() || `Process exited ${code}`)));
  });
}
function safeName(name) { return (name || 'Untitled').replace(/[<>:"/\\|?*\x00-\x1F]/g, '_').replace(/\s+/g, ' ').trim().slice(0, 120) || 'Untitled'; }
function listTracks() {
  ensureLibrary();
  return fs.readdirSync(libraryDir()).filter(n => /\.(mp3|m4a|aac|ogg|opus|wav|flac)$/i.test(n)).map(name => {
    const file = path.join(libraryDir(), name), metaPath = `${file}.json`;
    let meta = { title: path.parse(name).name, artist: 'Unknown artist', duration: 0 };
    try { meta = { ...meta, ...JSON.parse(fs.readFileSync(metaPath, 'utf8')) }; } catch (_) {}
    return { ...meta, name, path: file, size: fs.statSync(file).size };
  }).sort((a,b) => a.title.localeCompare(b.title));
}
async function preview(url) {
  const raw = await run(tool('yt-dlp'), ['--dump-single-json', '--skip-download', '--no-playlist', '--no-warnings', url]);
  const info = JSON.parse(raw);
  return { title: info.title || 'Untitled', artist: info.artist || info.uploader || info.channel || 'Unknown artist', duration: info.duration || 0, thumbnail: info.thumbnail || '' };
}
async function download(url, meta) {
  ensureLibrary();
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'wfmusic-'));
  try {
    send('download-progress', { text: 'Downloading the best audio stream…', percent: 15 });
    const sourceTemplate = path.join(temp, '%(title)s.%(ext)s');
    await run(tool('yt-dlp'), ['--no-playlist', '--no-warnings', '--retries', '5', '--fragment-retries', '5', '--concurrent-fragments', '1', '-f', 'bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best', '-o', sourceTemplate, url]);
    const source = fs.readdirSync(temp).map(n => path.join(temp, n)).find(f => !f.endsWith('.part') && !f.endsWith('.ytdl'));
    if (!source) throw new Error('yt-dlp did not produce an audio file');
    const target = path.join(libraryDir(), `${safeName(meta.title)}.mp3`);
    send('download-progress', { text: 'Converting locally to MP3…', percent: 60 });
    await run(tool('ffmpeg'), ['-y', '-i', source, '-vn', '-codec:a', 'libmp3lame', '-b:a', '192k', '-metadata', `title=${meta.title}`, '-metadata', `artist=${meta.artist}`, '-metadata', 'album=Wfmusic downloads', target]);
    fs.writeFileSync(`${target}.json`, JSON.stringify(meta));
    send('download-progress', { text: 'Saved to your Wfmusic library.', percent: 100 });
    return listTracks().find(t => t.path === target) || { ...meta, path: target, name: path.basename(target) };
  } finally { fs.rmSync(temp, { recursive: true, force: true }); }
}
function createWindow() {
  win = new BrowserWindow({ width: 1180, height: 800, minWidth: 900, minHeight: 620, backgroundColor: '#0b0d12', title: 'Wfmusic', webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false } });
  win.loadFile(path.join(__dirname, 'index.html'));
}
ipcMain.handle('preview', (_, url) => preview(url));
ipcMain.handle('download', (_, { url, meta }) => download(url, meta));
ipcMain.handle('library', () => listTracks());
ipcMain.handle('choose-folder', async () => dialog.showOpenDialog(win, { properties: ['openDirectory'] }));
ipcMain.handle('file-url', (_, filePath) => `file://${filePath.replace(/\\/g, '/')}`);
app.whenReady().then(() => { ensureLibrary(); session.defaultSession.setPermissionRequestHandler((_wc, _permission, callback) => callback(false)); createWindow(); app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); }); });
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
