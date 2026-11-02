const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('wfmusic', {
  preview: url => ipcRenderer.invoke('preview', url),
  download: payload => ipcRenderer.invoke('download', payload),
  library: () => ipcRenderer.invoke('library'),
  fileUrl: file => ipcRenderer.invoke('file-url', file),
  onProgress: callback => ipcRenderer.on('download-progress', (_event, data) => callback(data))
});
