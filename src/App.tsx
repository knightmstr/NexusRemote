import React, { useState } from 'react';
import { RemoteMode, ConnectionStatus, InstalledApp, LogEntry, IrPulseEvent } from './types';
import { RemoteControl } from './components/RemoteControl';
import { TvSimulator } from './components/TvSimulator';
import { LogcatConsole } from './components/LogcatConsole';
import { CodeViewer } from './components/CodeViewer';
import {
  Smartphone,
  Tv,
  Code2,
  Terminal,
  Layers,
  Radio,
  Wifi,
  Cpu,
  CheckCircle2,
  Activity
} from 'lucide-react';

const INITIAL_APPS: InstalledApp[] = [
  {
    id: 'prime',
    name: 'Prime Video',
    packageName: 'com.amazon.amazonvideo.livingroom',
    color: '#00A8E1',
    iconName: 'Film',
  },
  {
    id: 'netflix',
    name: 'Netflix',
    packageName: 'com.netflix.ninja',
    color: '#E50914',
    iconName: 'Play',
  },
  {
    id: 'youtube',
    name: 'YouTube',
    packageName: 'com.google.android.youtube.tv',
    color: '#FF0000',
    iconName: 'Tv',
  },
  {
    id: 'disney',
    name: 'Disney+',
    packageName: 'com.disney.disneyplus',
    color: '#113CCF',
    iconName: 'Sparkles',
  },
  {
    id: 'plex',
    name: 'Plex',
    packageName: 'com.plexapp.android',
    color: '#E5A00D',
    iconName: 'Server',
  },
  {
    id: 'kodi',
    name: 'Kodi',
    packageName: 'org.xbmc.kodi',
    color: '#17B2E7',
    iconName: 'Grid',
  },
  {
    id: 'spotify',
    name: 'Spotify',
    packageName: 'com.spotify.tv.android',
    color: '#1DB954',
    iconName: 'Music',
  },
];

export default function App() {
  // Navigation tab
  const [activeTab, setActiveTab] = useState<'simulator' | 'code'>('simulator');

  // Unified Remote State
  const [remoteMode, setRemoteMode] = useState<RemoteMode>('WI_FI');
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('CONNECTED');
  const [targetIp, setTargetIp] = useState('192.168.1.150');
  const [hasIrEmitter, setHasIrEmitter] = useState(true);
  const [lastIrPulse, setLastIrPulse] = useState<IrPulseEvent | null>(null);

  // Simulated TV State
  const [isPoweredOn, setIsPoweredOn] = useState(true);
  const [activeSource, setActiveSource] = useState('Fire TV OS');
  const [volume, setVolume] = useState(45);
  const [isMuted, setIsMuted] = useState(false);
  const [focusedTile, setFocusedTile] = useState(0);
  const [activeApp, setActiveApp] = useState<InstalledApp | null>(null);
  const [onScreenText, setOnScreenText] = useState('');
  const [isSearchActive, setIsSearchActive] = useState(false);
  const [lastCommand, setLastCommand] = useState<string | null>('Ready (ADB Connected)');

  // Installed App Dock List
  const [apps, setApps] = useState<InstalledApp[]>(INITIAL_APPS);

  // Logcat Entries
  const [logs, setLogs] = useState<LogEntry[]>([
    {
      id: '1',
      timestamp: '17:12:00.104',
      tag: 'InfraredManager',
      level: 'I',
      message: 'ConsumerIrManager initialized: Carrier range 30000Hz - 60000Hz supported.',
    },
    {
      id: '2',
      timestamp: '17:12:00.120',
      tag: 'RemoteViewModel',
      level: 'D',
      message: 'Preferences DataStore loaded cached target IP: 192.168.1.150',
    },
    {
      id: '3',
      timestamp: '17:12:00.340',
      tag: 'FireTvManager',
      level: 'I',
      message: 'ADB TCP/IP socket connected to 192.168.1.150:5555',
    },
    {
      id: '4',
      timestamp: '17:12:00.365',
      tag: 'AdbProtocol',
      level: 'D',
      message: 'TX A_CNXN (version=0x01000000, maxdata=4096, banner=host::HybridRemoteTV)',
    },
    {
      id: '5',
      timestamp: '17:12:00.412',
      tag: 'AdbProtocol',
      level: 'D',
      message: 'RX A_CNXN (device::ro.product.model=AFTMM;features=shell_v2,cmd)',
    },
    {
      id: '6',
      timestamp: '17:12:00.450',
      tag: 'FireTvManager',
      level: 'I',
      message: 'Fetched 7 third-party packages via \'pm list packages -3\'',
    },
  ]);

  const addLog = (
    tag: LogEntry['tag'],
    level: LogEntry['level'],
    message: string
  ) => {
    const now = new Date();
    const timeStr = `${now.toTimeString().split(' ')[0]}.${String(
      now.getMilliseconds()
    ).padStart(3, '0')}`;
    const newEntry: LogEntry = {
      id: Math.random().toString(36).substring(2, 9),
      timestamp: timeStr,
      tag,
      level,
      message,
    };
    setLogs((prev) => [...prev.slice(-150), newEntry]);
  };

  // Remote Mode Change
  const handleSetRemoteMode = (mode: RemoteMode) => {
    setRemoteMode(mode);
    addLog(
      'RemoteViewModel',
      'I',
      `Switched RemoteModeState to ${mode}. Routing commands via ${
        mode === 'INFRARED' ? 'Hardware Consumer IR Blaster' : 'ADB TCP/IP (port 5555)'
      }`
    );
  };

  // Network Connection Handling
  const handleConnectIp = (ip: string) => {
    setTargetIp(ip);
    setConnectionStatus('CONNECTING');
    addLog('FireTvManager', 'I', `Initiating ADB TCP/IP connection to ${ip}:5555`);
    addLog('RemoteViewModel', 'D', `Persisting target IP '${ip}' to Preferences DataStore`);

    setTimeout(() => {
      // Simulate auth challenge occasionally if not 192.168.1.150
      if (ip === '192.168.1.100' || ip === '192.168.1.1') {
        setConnectionStatus('AUTH_REQUIRED');
        addLog(
          'FireTvManager',
          'W',
          'ADB Handshake received A_AUTH challenge. Displaying authorization dialog on TV.'
        );
      } else {
        setConnectionStatus('CONNECTED');
        addLog('FireTvManager', 'I', `Successfully authenticated with Fire TV at ${ip}:5555`);
        addLog('FireTvManager', 'D', 'Executing: adb shell pm list packages -3');
        setApps(INITIAL_APPS);
      }
    }, 700);
  };

  const handleDisconnectIp = () => {
    setConnectionStatus('DISCONNECTED');
    addLog('FireTvManager', 'I', 'ADB TCP/IP socket closed. State: DISCONNECTED');
  };

  const handleAuthorizeAdb = () => {
    setConnectionStatus('CONNECTED');
    addLog('FireTvManager', 'I', 'User granted RSA authorization on TV screen. Connection status: CONNECTED');
    addLog('FireTvManager', 'D', 'Executing: adb shell pm list packages -3');
    setApps(INITIAL_APPS);
  };

  const handleRejectAdb = () => {
    setConnectionStatus('DISCONNECTED');
    addLog('FireTvManager', 'W', 'User rejected RSA authorization on TV.');
  };

  // DPAD Navigation
  const handleDpadCommand = (keyCode: number, label: string) => {
    if (connectionStatus !== 'CONNECTED') {
      addLog('FireTvManager', 'W', `Cannot send DPAD command: Device is ${connectionStatus}`);
      setLastCommand(`Rejected: Device ${connectionStatus}`);
      return;
    }

    addLog('FireTvManager', 'D', `ADB Shell TX: 'input keyevent ${keyCode}' (${label})`);
    setLastCommand(`ADB: input keyevent ${keyCode} (${label})`);

    // Update Simulated Fire TV State
    if (keyCode === 19) {
      // UP
      setFocusedTile((prev) => (prev >= 4 ? prev - 4 : prev));
    } else if (keyCode === 20) {
      // DOWN
      setFocusedTile((prev) => (prev + 4 < apps.length ? prev + 4 : prev));
    } else if (keyCode === 21) {
      // LEFT
      setFocusedTile((prev) => (prev > 0 ? prev - 1 : 0));
    } else if (keyCode === 22) {
      // RIGHT
      setFocusedTile((prev) => (prev < apps.length - 1 ? prev + 1 : prev));
    } else if (keyCode === 23) {
      // CENTER OK
      if (apps[focusedTile]) {
        setActiveApp(apps[focusedTile]);
        addLog(
          'FireTvManager',
          'I',
          `Launching focused app: ${apps[focusedTile].packageName} via monkey launcher intent`
        );
      }
    } else if (keyCode === 3) {
      // HOME
      setActiveApp(null);
      setOnScreenText('');
      setIsSearchActive(false);
      addLog('FireTvManager', 'I', 'Exited to Fire TV Home Launcher');
    } else if (keyCode === 4) {
      // BACK
      if (activeApp) {
        setActiveApp(null);
        addLog('FireTvManager', 'I', 'Exited active app activity');
      } else if (isSearchActive || onScreenText) {
        setIsSearchActive(false);
        setOnScreenText('');
        addLog('FireTvManager', 'I', 'Exited TV search overlay');
      }
    } else if (keyCode === 82) {
      // MENU
      addLog('FireTvManager', 'I', 'Triggered context menu on TV screen');
    }
  };

  // Dedicated TV Search Activation
  const handleSearchKey = () => {
    if (connectionStatus !== 'CONNECTED') {
      addLog('FireTvManager', 'W', `Cannot activate search: Device is ${connectionStatus}`);
      return;
    }
    setActiveApp(null);
    setIsSearchActive(true);
    addLog('FireTvManager', 'D', 'ADB Shell TX: \'input keyevent 84\' (SEARCH)');
    setLastCommand('ADB: input keyevent 84 (SEARCH)');
  };

  // Keyboard Backspace Action
  const handleBackspace = () => {
    if (connectionStatus !== 'CONNECTED') {
      addLog('FireTvManager', 'W', `Cannot send backspace: Device is ${connectionStatus}`);
      return;
    }
    setOnScreenText((prev) => prev.slice(0, -1));
    addLog('FireTvManager', 'D', 'ADB Shell TX: \'input keyevent 67\' (DEL/BACKSPACE)');
    setLastCommand('ADB: input keyevent 67 (DEL)');
  };

  // Keyboard Enter / Submit Search Action
  const handleEnterKey = () => {
    if (connectionStatus !== 'CONNECTED') {
      addLog('FireTvManager', 'W', `Cannot send enter: Device is ${connectionStatus}`);
      return;
    }
    addLog('FireTvManager', 'D', 'ADB Shell TX: \'input keyevent 66\' (ENTER/SEARCH)');
    setLastCommand('ADB: input keyevent 66 (ENTER)');
  };

  // Quick Search Preset Query Injection
  const handleSendPreset = (preset: string) => {
    if (connectionStatus !== 'CONNECTED') {
      addLog('FireTvManager', 'W', `Cannot send preset: Device is ${connectionStatus}`);
      return;
    }
    setActiveApp(null);
    setIsSearchActive(true);
    setOnScreenText(preset);
    const sanitized = preset.replace(/ /g, '%s');
    addLog('FireTvManager', 'D', `ADB Shell TX: 'input text "${sanitized}"'`);
    addLog('FireTvManager', 'D', 'ADB Shell TX: \'input keyevent 66\' (ENTER)');
    setLastCommand(`ADB Search: "${preset}"`);
  };

  // Virtual Keyboard Character Typing
  const handleKeyChar = (char: string) => {
    if (connectionStatus !== 'CONNECTED') {
      addLog('FireTvManager', 'W', `Cannot type character: Device is ${connectionStatus}`);
      return;
    }
    setIsSearchActive(true);
    setOnScreenText((prev) => prev + char);
    const sanitized = char === ' ' ? '%s' : char;
    addLog('FireTvManager', 'D', `ADB Shell TX: 'input text "${sanitized}"'`);
    setLastCommand(`ADB Type: "${char}"`);
  };

  // Text Injection
  const handleInjectText = (text: string) => {
    if (connectionStatus !== 'CONNECTED') {
      addLog('FireTvManager', 'W', `Cannot inject text: Device is ${connectionStatus}`);
      return;
    }

    setIsSearchActive(true);
    const sanitized = text.replace(/ /g, '%s');
    addLog('FireTvManager', 'D', `ADB Shell TX: 'input text "${sanitized}"'`);
    setOnScreenText((prev) => (prev ? `${prev} ${text}` : text));
    setLastCommand(`ADB: input text "${text}"`);
  };

  // Launch App
  const handleLaunchApp = (app: InstalledApp) => {
    if (connectionStatus !== 'CONNECTED') {
      addLog('FireTvManager', 'W', `Cannot launch app: Device is ${connectionStatus}`);
      return;
    }

    setActiveApp(app);
    addLog(
      'FireTvManager',
      'I',
      `ADB Shell TX: 'monkey -p ${app.packageName} -c android.intent.category.LAUNCHER 1'`
    );
    setLastCommand(`Launch: ${app.name} (${app.packageName})`);
  };

  // IR Commands
  const recordIrPulse = (cmdName: string, patternLength = 68, freq = 38000) => {
    setLastIrPulse({
      commandName: cmdName,
      frequencyHz: freq,
      patternLength,
      timestamp: Date.now(),
    });
    addLog(
      'InfraredManager',
      'I',
      `Transmitted IR pulse: ${freq} Hz, ${patternLength} pulses (NEC 0x04, ${cmdName})`
    );
    setLastCommand(`IR: 38kHz NEC [${cmdName}]`);
  };

  const handleIrPower = () => {
    setIsPoweredOn((prev) => !prev);
    recordIrPulse('CMD_POWER 0x08');
  };

  const handleIrSource = () => {
    const sources = ['Fire TV OS', 'HDMI 1 (PlayStation)', 'HDMI 2 (Cable Box)'];
    const currentIdx = sources.indexOf(activeSource);
    const nextSource = sources[(currentIdx + 1) % sources.length];
    setActiveSource(nextSource);
    recordIrPulse('CMD_SOURCE 0x0B');
  };

  const handleIrVolumeUp = () => {
    setIsMuted(false);
    setVolume((prev) => Math.min(100, prev + 5));
    recordIrPulse('CMD_VOL_UP 0x02');
  };

  const handleIrVolumeDown = () => {
    setVolume((prev) => Math.max(0, prev - 5));
    recordIrPulse('CMD_VOL_DOWN 0x03');
  };

  const handleIrMute = () => {
    setIsMuted((prev) => !prev);
    recordIrPulse('CMD_MUTE 0x09');
  };

  const handleIrDigit = (digit: number) => {
    setOnScreenText((prev) => `${prev}${digit}`);
    recordIrPulse(`CMD_DIGIT_${digit} 0x1${digit}`);
  };

  const handleRefreshApps = () => {
    if (connectionStatus === 'CONNECTED') {
      addLog('FireTvManager', 'D', 'Executing: adb shell pm list packages -3');
      addLog('FireTvManager', 'I', 'Refreshed 7 installed 3rd-party packages');
      setApps(INITIAL_APPS);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-sky-500 selection:text-white">
      {/* Top Main Navigation Bar */}
      <header className="border-b border-slate-800 bg-slate-900/90 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-sky-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-sky-500/20">
              <Radio className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="font-bold text-base text-white tracking-tight">
                  Hybrid Unified TV Remote
                </h1>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                  Android Native • Kotlin
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Low-level Consumer IR Pulse & ADB TCP/IP (Port 5555) Architecture
              </p>
            </div>
          </div>

          {/* Navigation View Switcher */}
          <div className="flex items-center gap-2 bg-slate-950 p-1 rounded-xl border border-slate-800">
            <button
              onClick={() => setActiveTab('simulator')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'simulator'
                  ? 'bg-sky-600 text-white shadow-md'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Smartphone className="w-4 h-4" />
              <span>Interactive Remote & TV</span>
            </button>

            <button
              onClick={() => setActiveTab('code')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'code'
                  ? 'bg-sky-600 text-white shadow-md'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Code2 className="w-4 h-4" />
              <span>Kotlin Architecture Studio</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Body */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 flex flex-col">
        {activeTab === 'simulator' ? (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 flex-1 items-start">
            {/* Left Column: Physical Remote Mockup */}
            <div className="lg:col-span-5 flex flex-col items-center">
              <div className="w-full flex items-center justify-between mb-3 px-1">
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
                  <Smartphone className="w-4 h-4 text-sky-400" />
                  Jetpack Compose Remote Control
                </span>
                <span className="text-[11px] font-mono text-slate-500">
                  Mode: <strong className="text-sky-300">{remoteMode}</strong>
                </span>
              </div>

              <RemoteControl
                remoteMode={remoteMode}
                connectionStatus={connectionStatus}
                targetIp={targetIp}
                hasIrEmitter={hasIrEmitter}
                appDockList={apps}
                lastIrPulse={lastIrPulse}
                onSetRemoteMode={handleSetRemoteMode}
                onConnectIp={handleConnectIp}
                onDisconnectIp={handleDisconnectIp}
                onDpadCommand={handleDpadCommand}
                onInjectText={handleInjectText}
                onLaunchApp={handleLaunchApp}
                onSearchKey={handleSearchKey}
                onBackspace={handleBackspace}
                onEnterKey={handleEnterKey}
                onSendPreset={handleSendPreset}
                onKeyChar={handleKeyChar}
                onIrPower={handleIrPower}
                onIrSource={handleIrSource}
                onIrVolumeUp={handleIrVolumeUp}
                onIrVolumeDown={handleIrVolumeDown}
                onIrMute={handleIrMute}
                onIrDigit={handleIrDigit}
                onRefreshApps={handleRefreshApps}
              />
            </div>

            {/* Right Column: Simulated Fire TV Screen & Android Logcat Console */}
            <div className="lg:col-span-7 flex flex-col gap-6">
              {/* TV Screen Display */}
              <div>
                <div className="flex items-center justify-between mb-2 px-1">
                  <span className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
                    <Tv className="w-4 h-4 text-sky-400" />
                    Simulated Amazon Fire TV Device
                  </span>
                  <div className="flex items-center gap-2 text-xs text-slate-400">
                    <span className="font-mono text-slate-500">ADB TCP IP:</span>
                    <span className="font-mono text-sky-300 font-semibold">{targetIp}:5555</span>
                  </div>
                </div>

                <TvSimulator
                  isPoweredOn={isPoweredOn}
                  activeSource={activeSource}
                  volume={volume}
                  isMuted={isMuted}
                  focusedTile={focusedTile}
                  activeApp={activeApp}
                  onScreenText={onScreenText}
                  isSearchActive={isSearchActive}
                  connectionStatus={connectionStatus}
                  lastCommand={lastCommand}
                  apps={apps}
                  onAuthorizeAdb={handleAuthorizeAdb}
                  onRejectAdb={handleRejectAdb}
                  onSelectTile={(idx) => {
                    setFocusedTile(idx);
                    setActiveApp(apps[idx]);
                    addLog(
                      'FireTvManager',
                      'I',
                      `Launched: ${apps[idx].packageName} via simulated click`
                    );
                  }}
                  onCloseSearch={() => {
                    setIsSearchActive(false);
                    setOnScreenText('');
                  }}
                />
              </div>

              {/* Android Logcat Terminal */}
              <div className="h-72">
                <LogcatConsole
                  logs={logs}
                  onClearLogs={() => setLogs([])}
                />
              </div>
            </div>
          </div>
        ) : (
          /* Architecture & Code Studio View */
          <div className="flex-1 min-h-[640px]">
            <CodeViewer />
          </div>
        )}
      </main>

      {/* Engineering Footer */}
      <footer className="border-t border-slate-900 bg-slate-950 py-3 text-xs text-slate-500 px-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span>Principal Android Hardware & Network Architecture</span>
          <span>•</span>
          <span className="font-mono text-slate-400">Dispatchers.IO • Preferences DataStore • ConsumerIrManager</span>
        </div>
        <div className="font-mono text-[11px] text-slate-500">
          FireTvManager • InfraredManager • RemoteViewModel • RemoteMainScreen
        </div>
      </footer>
    </div>
  );
}
