const { contextBridge, ipcRenderer } = require('electron');

// ── 安全的 IPC 桥 ──────────────────────────────────────────────────────────
contextBridge.exposeInMainWorld('electronAPI', {
    getConfig: () => ipcRenderer.invoke('get-config'),
    setConfig: (key, value) => ipcRenderer.invoke('set-config', key, value),
    getDataPath: () => ipcRenderer.invoke('get-data-path'),
    openDataFolder: () => ipcRenderer.invoke('open-data-folder'),
    getBackendStatus: () => ipcRenderer.invoke('get-backend-status'),
    restartBackend: () => ipcRenderer.invoke('restart-backend'),
    relaunchApp: () => ipcRenderer.invoke('relaunch-app'),

    // 平台信息
    platform: process.platform,
    versions: {
        node: process.versions.node,
        electron: process.versions.electron,
        chrome: process.versions.chrome
    }
});
