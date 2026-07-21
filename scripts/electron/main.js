const { app, BrowserWindow, Tray, Menu, ipcMain, nativeTheme, shell, dialog } = require('electron');
const path = require('path');
const { spawn, exec } = require('child_process');
const fs = require('fs');
const Store = require('electron-store');

// ── 配置存储 ───────────────────────────────────────────────────────────────
const store = new Store({
    defaults: {
        windowBounds: { width: 1200, height: 800 },
        autoStart: false,
        minimizeToTray: true,
        startInTray: false,
        lastPort: 8080,
        javaPath: 'java'
    }
});

// ── 全局状态 ───────────────────────────────────────────────────────────────
let mainWindow = null;
let tray = null;
let backendProcess = null;
let isQuitting = false;
const isDev = process.env.ELECTRON_DEV === '1';

// ── 路径工具 ───────────────────────────────────────────────────────────────
const APP_ROOT = isDev ? path.join(__dirname, '../../electron') : __dirname;
const JAR_DIR = isDev
    ? path.join(__dirname, '../../social-sdk-xianyu-manager/target')
    : path.join(process.resourcesPath, 'app');
const DATA_DIR = path.join(app.getPath('userData'));
const LOG_DIR = path.join(DATA_DIR, 'logs');
const JAVA_PATH = store.get('javaPath', 'java');
// launch.sh / launch.bat 脚本路径（与 main.js 同目录）
const LAUNCH_SCRIPT = process.platform === 'win32'
    ? path.join(APP_ROOT, 'launch.bat')
    : path.join(APP_ROOT, 'launch.sh');

// ── 日志工具 ───────────────────────────────────────────────────────────────
function ensureLogDir() {
    if (!fs.existsSync(LOG_DIR)) fs.mkdirSync(LOG_DIR, { recursive: true });
}

function logToFile(level, msg) {
    ensureLogDir();
    const ts = new Date().toISOString();
    const line = `[${ts}] ${level}: ${msg}\n`;
    fs.appendFileSync(path.join(LOG_DIR, 'electron.log'), line);
    console.log(`[${level}] ${msg}`);
}

// ── 查找 JAR 文件 ──────────────────────────────────────────────────────────
function findJar() {
    if (!fs.existsSync(JAR_DIR)) return null;
    const files = fs.readdirSync(JAR_DIR).filter(f => f.endsWith('.jar'));
    // 优先选择包含 spring-boot 的 fat jar（server.jar 或 xianyu-manager）
    const fat = files.find(f => /server\.(jar)$/.test(f)) ||
                files.find(f => /xianyu-manager.*\.jar$/.test(f));
    return fat ? path.join(JAR_DIR, fat) : (files[0] ? path.join(JAR_DIR, files[0]) : null);
}

// ── 启动后端 ───────────────────────────────────────────────────────────────
function startBackend() {
    if (backendProcess) {
        logToFile('INFO', '后端进程已运行');
        return;
    }

    const jarPath = findJar();
    if (!jarPath) {
        logToFile('ERROR', `未找到 JAR 文件，目录: ${JAR_DIR}`);
        dialog.showErrorBox('启动失败', `未找到后端 JAR 文件\n期望目录: ${JAR_DIR}\n\n请先运行 Maven 构建: ./build.sh`);
        return;
    }

    // 确保数据目录存在
    ['data', 'data/openlist', 'chrome-profiles', 'logs'].forEach(d => {
        const p = path.join(DATA_DIR, d);
        if (!fs.existsSync(p)) fs.mkdirSync(p, { recursive: true });
    });

    const port = store.get('lastPort', 8080);
    const args = [
        '-Xmx512m',
        '-Xms256m',
        `-Dserver.port=${port}`,
        '-Dfile.encoding=UTF-8',
        `-Duser.dir=${DATA_DIR}`,
        `-Dspring.web.resources.static-locations=classpath:/static/,file:${DATA_DIR}/uploads/`,
        '-jar', jarPath
    ];

    logToFile('INFO', `启动后端: ${JAVA_PATH} ${args.join(' ')}`);

    backendProcess = spawn(JAVA_PATH, args, {
        cwd: DATA_DIR,
        env: { ...process.env, JAVA_HOME: process.env.JAVA_HOME || '' }
    });

    backendProcess.stdout.on('data', (data) => {
        logToFile('BACKEND-OUT', data.toString().trim());
    });

    backendProcess.stderr.on('data', (data) => {
        logToFile('BACKEND-ERR', data.toString().trim());
    });

    backendProcess.on('exit', (code) => {
        logToFile('INFO', `后端进程退出，代码: ${code}`);
        backendProcess = null;
        if (!isQuitting && code !== 0) {
            dialog.showErrorBox('后端异常退出', `后端进程已退出 (code: ${code})\n请查看日志: ${LOG_DIR}`);
        }
    });
}

// ── 等待后端就绪 ──────────────────────────────────────────────────────────
async function waitForBackend(port, timeout = 60000) {
    const start = Date.now();
    while (Date.now() - start < timeout) {
        try {
            const fetch = require('node-fetch');
            const res = await fetch(`http://127.0.0.1:${port}/actuator/health`);
            if (res.ok) return true;
        } catch (e) {}
        await new Promise(r => setTimeout(r, 1000));
    }
    return false;
}

// ── 创建窗口 ───────────────────────────────────────────────────────────────
function createWindow() {
    const bounds = store.get('windowBounds');

    mainWindow = new BrowserWindow({
        width: bounds.width,
        height: bounds.height,
        minWidth: 900,
        minHeight: 600,
        title: '闲鱼管理器',
        icon: path.join(__dirname, 'icons/icon.png'),
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            nodeIntegration: false,
            contextIsolation: true,
            sandbox: false
        },
        show: false
    });

    mainWindow.once('ready-to-show', () => {
        if (!store.get('startInTray')) {
            mainWindow.show();
        }
    });

    // 保存窗口大小
    mainWindow.on('resize', () => {
        const b = mainWindow.getBounds();
        store.set('windowBounds', { width: b.width, height: b.height });
    });

    // 关闭到托盘
    mainWindow.on('close', (e) => {
        if (!isQuitting && store.get('minimizeToTray')) {
            e.preventDefault();
            mainWindow.hide();
        }
    });

    mainWindow.on('closed', () => {
        mainWindow = null;
    });

    // 外部链接用系统浏览器打开
    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        shell.openExternal(url);
        return { action: 'deny' };
    });
}

// ── 创建托盘 ───────────────────────────────────────────────────────────────
function createTray() {
    const iconPath = path.join(__dirname, 'icons/tray.png');
    tray = new Tray(iconPath);

    const contextMenu = Menu.buildFromTemplate([
        {
            label: '打开主界面',
            click: () => { mainWindow && mainWindow.show(); }
        },
        {
            label: '访问 Web UI',
            click: () => { shell.openExternal(`http://127.0.0.1:${store.get('lastPort', 8080)}`); }
        },
        { type: 'separator' },
        {
            label: '重启后端',
            click: restartBackend
        },
        {
            label: '查看日志',
            click: () => {
                shell.openPath(LOG_DIR);
            }
        },
        {
            label: '设置',
            click: openSettings
        },
        { type: 'separator' },
        {
            label: '退出',
            click: quitApp
        }
    ]);

    tray.setToolTip('闲鱼管理器');
    tray.setContextMenu(contextMenu);
    tray.on('double-click', () => {
        if (mainWindow) mainWindow.show();
    });
}

// ── 设置窗口 ───────────────────────────────────────────────────────────────
function openSettings() {
    const settingsWindow = new BrowserWindow({
        width: 500,
        height: 600,
        parent: mainWindow,
        modal: true,
        resizable: false,
        title: '设置',
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            nodeIntegration: false,
            contextIsolation: true
        }
    });
    settingsWindow.loadFile(path.join(__dirname, 'renderer/settings.html'));
}

// ── 重启后端 ───────────────────────────────────────────────────────────────
function restartBackend() {
    if (backendProcess) {
        backendProcess.kill();
        backendProcess = null;
    }
    startBackend();
}

// ── 退出 ──────────────────────────────────────────────────────────────────
function quitApp() {
    isQuitting = true;
    if (backendProcess) {
        backendProcess.kill();
        backendProcess = null;
    }
    app.quit();
}

// ── IPC 通信 ───────────────────────────────────────────────────────────────
ipcMain.handle('get-config', () => {
    return store.store;
});

ipcMain.handle('set-config', (event, key, value) => {
    store.set(key, value);
});

ipcMain.handle('get-data-path', () => {
    return DATA_DIR;
});

ipcMain.handle('open-data-folder', () => {
    shell.openPath(DATA_DIR);
});

ipcMain.handle('get-backend-status', () => {
    return { running: !!backendProcess, pid: backendProcess?.pid };
});

ipcMain.handle('restart-backend', () => {
    restartBackend();
});

ipcMain.handle('relaunch-app', () => {
    app.relaunch();
    app.quit();
});

// ── 应用就绪 ───────────────────────────────────────────────────────────────
app.whenReady().then(() => {
    logToFile('INFO', `闲鱼管理器启动 | 平台: ${process.platform} | 数据目录: ${DATA_DIR}`);

    createWindow();
    createTray();
    startBackend();

    // 后端就绪后加载
    const port = store.get('lastPort', 8080);
    if (mainWindow) {
        mainWindow.loadFile(path.join(__dirname, 'renderer/loading.html'));
        waitForBackend(port).then(ready => {
            if (ready && mainWindow) {
                mainWindow.loadURL(`http://127.0.0.1:${port}`);
                logToFile('INFO', '后端已就绪，加载 Web UI');
            }
        });
    }
});

app.on('window-all-closed', () => {
    // macOS 保持托盘
    if (process.platform !== 'darwin') {
        // Windows/Linux 如果有托盘则不退出
        if (!tray) app.quit();
    }
});

app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
    else if (mainWindow) mainWindow.show();
});

app.on('before-quit', () => {
    isQuitting = true;
});
