import React, { useState } from 'react';
import { RemoteMode, ConnectionStatus, InstalledApp, IrPulseEvent, TvBrandProfile } from '../types';
import {
  Power,
  Tv,
  Wifi,
  WifiOff,
  Radio,
  Volume2,
  VolumeX,
  Volume1,
  Send,
  RefreshCw,
  ChevronUp,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ArrowLeft,
  Home,
  Menu,
  Sparkles,
  Search,
  Keyboard,
  Delete,
  CornerDownLeft,
  X,
  Sliders,
  Check,
  ChevronRight as ChevronArrowRight
} from 'lucide-react';

interface RemoteControlProps {
  remoteMode: RemoteMode;
  connectionStatus: ConnectionStatus;
  targetIp: string;
  hasIrEmitter: boolean;
  selectedBrand: TvBrandProfile;
  availableBrands: TvBrandProfile[];
  appDockList: InstalledApp[];
  lastIrPulse: IrPulseEvent | null;
  onSetRemoteMode: (mode: RemoteMode) => void;
  onSelectBrand: (brand: TvBrandProfile) => void;
  onConnectIp: (ip: string) => void;
  onDisconnectIp: () => void;
  onDpadCommand: (keyCode: number, label: string) => void;
  onInjectText: (text: string) => void;
  onLaunchApp: (app: InstalledApp) => void;
  onSearchKey: () => void;
  onBackspace: () => void;
  onEnterKey: () => void;
  onSendPreset: (query: string) => void;
  onKeyChar: (char: string) => void;
  onIrPower: () => void;
  onIrSource: () => void;
  onIrVolumeUp: () => void;
  onIrVolumeDown: () => void;
  onIrMute: () => void;
  onIrDigit: (digit: number) => void;
  onRefreshApps: () => void;
}

const SEARCH_PRESETS = [
  '4K Movies',
  'Netflix Originals',
  'Top Music',
  'Documentaries',
  'Trending News'
];

export const RemoteControl: React.FC<RemoteControlProps> = ({
  remoteMode,
  connectionStatus,
  targetIp,
  hasIrEmitter,
  selectedBrand,
  availableBrands,
  appDockList,
  lastIrPulse,
  onSetRemoteMode,
  onSelectBrand,
  onConnectIp,
  onDisconnectIp,
  onDpadCommand,
  onInjectText,
  onLaunchApp,
  onSearchKey,
  onBackspace,
  onEnterKey,
  onSendPreset,
  onKeyChar,
  onIrPower,
  onIrSource,
  onIrVolumeUp,
  onIrVolumeDown,
  onIrMute,
  onIrDigit,
  onRefreshApps,
}) => {
  const [inputText, setInputText] = useState('');
  const [showIpConfig, setShowIpConfig] = useState(false);
  const [showBrandSelector, setShowBrandSelector] = useState(false);
  const [editingIp, setEditingIp] = useState(targetIp);
  const [isPressingLed, setIsPressingLed] = useState(false);
  const [showVirtualKeyboard, setShowVirtualKeyboard] = useState(false);

  const triggerLedPulse = () => {
    setIsPressingLed(true);
    setTimeout(() => setIsPressingLed(false), 300);
  };

  const handleIrAction = (action: () => void) => {
    triggerLedPulse();
    action();
  };

  const handleTextSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputText.trim()) {
      onInjectText(inputText.trim());
      setInputText('');
    }
  };

  const handleVirtualKey = (char: string) => {
    setInputText((prev) => prev + char);
    onKeyChar(char);
  };

  const handleVirtualBackspace = () => {
    setInputText((prev) => prev.slice(0, -1));
    onBackspace();
  };

  return (
    <div className="relative mx-auto w-full max-w-[360px] bg-gradient-to-b from-slate-900 via-slate-920 to-slate-950 rounded-[44px] p-5 border-4 border-slate-800 shadow-[0_25px_60px_-15px_rgba(0,0,0,0.9)] flex flex-col items-center select-none text-slate-100 ring-1 ring-slate-700/50">
      {/* Top Infrared LED Blaster Emitter */}
      <div className="relative -mt-8 mb-3 flex flex-col items-center">
        <div
          className={`w-6 h-3 rounded-t-full transition-all duration-200 border-2 border-slate-700/80 ${
            isPressingLed || (lastIrPulse && Date.now() - lastIrPulse.timestamp < 350)
              ? 'bg-rose-500 shadow-[0_0_24px_8px_rgba(244,63,94,0.9)] scale-110'
              : 'bg-slate-800'
          }`}
        />
        <div className="text-[9px] tracking-widest text-slate-500 uppercase font-mono mt-1">
          Consumer IR Blaster • {selectedBrand.carrierFrequencyHz / 1000} kHz
        </div>
      </div>

      {/* Top Remote Header: Status & TV Brand Badges */}
      <div className="w-full bg-slate-900/90 rounded-2xl p-2 border border-slate-800/80 mb-3 text-xs flex items-center justify-between gap-1.5">
        {/* Universal TV Brand Selector Button */}
        <button
          onClick={() => {
            setShowBrandSelector(!showBrandSelector);
            setShowIpConfig(false);
          }}
          title="Select Television Brand and Model"
          className="flex items-center gap-1.5 px-2.5 py-1 rounded-xl bg-slate-800 hover:bg-slate-750 border border-slate-700/70 text-[10px] text-slate-200 font-semibold transition-all group"
        >
          <span
            className={`w-2 h-2 rounded-full ${
              hasIrEmitter ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]' : 'bg-slate-600'
            }`}
          />
          <Tv className="w-3 h-3 text-sky-400 group-hover:scale-110 transition-transform" />
          <span className="font-bold tracking-tight text-white">{selectedBrand.name.split(' ')[0]}</span>
          <span className="text-[9px] text-sky-300 font-mono">
            {selectedBrand.carrierFrequencyHz / 1000}k
          </span>
        </button>

        {/* Wi-Fi ADB Connection Pill */}
        <button
          onClick={() => {
            setShowIpConfig(!showIpConfig);
            setShowBrandSelector(false);
          }}
          className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono border transition-all ${
            connectionStatus === 'CONNECTED'
              ? 'bg-emerald-950/80 border-emerald-500/50 text-emerald-300 hover:bg-emerald-900/80'
              : connectionStatus === 'CONNECTING'
              ? 'bg-sky-950/80 border-sky-500/50 text-sky-300 hover:bg-sky-900/80'
              : connectionStatus === 'AUTH_REQUIRED'
              ? 'bg-amber-950/80 border-amber-500/50 text-amber-300 hover:bg-amber-900/80'
              : 'bg-rose-950/60 border-rose-500/40 text-rose-300 hover:bg-rose-900/60'
          }`}
        >
          {connectionStatus === 'CONNECTED' ? (
            <Wifi className="w-3 h-3 text-emerald-400" />
          ) : (
            <WifiOff className="w-3 h-3 text-rose-400" />
          )}
          <span>{targetIp || 'Set IP'}</span>
        </button>
      </div>

      {/* Expandable Universal TV Brand Selector Modal / Dropdown */}
      {showBrandSelector && (
        <div className="w-full bg-slate-900 p-3 rounded-2xl border border-sky-500/40 shadow-xl mb-3 text-xs max-h-72 overflow-y-auto">
          <div className="font-bold text-slate-100 mb-2 flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-sky-400">
              <Tv className="w-4 h-4" />
              <span>Universal TV Remote Setup</span>
            </span>
            <button
              onClick={() => setShowBrandSelector(false)}
              className="text-slate-400 hover:text-white p-0.5 rounded"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
          <p className="text-[10px] text-slate-400 mb-2.5">
            Select your TV brand & series to calibrate IR pulse frequency and waveform protocol:
          </p>
          <div className="flex flex-col gap-1.5">
            {availableBrands.map((brand) => {
              const isSelected = brand.id === selectedBrand.id;
              return (
                <button
                  key={brand.id}
                  onClick={() => {
                    onSelectBrand(brand);
                    setShowBrandSelector(false);
                  }}
                  className={`flex items-center justify-between p-2 rounded-xl text-left transition-all border ${
                    isSelected
                      ? 'bg-sky-950/80 border-sky-500 text-white shadow-md'
                      : 'bg-slate-850 hover:bg-slate-800 border-slate-750 text-slate-300'
                  }`}
                >
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-xs text-white">{brand.name}</span>
                      <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-sky-300 border border-slate-700">
                        {brand.carrierFrequencyHz / 1000} kHz
                      </span>
                      <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-purple-950/60 text-purple-300 border border-purple-800/40">
                        {brand.protocol}
                      </span>
                    </div>
                    <div className="text-[10px] text-slate-400 truncate mt-0.5">
                      {brand.modelSeries}
                    </div>
                  </div>
                  {isSelected && <Check className="w-4 h-4 text-sky-400 shrink-0 ml-2" />}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Expandable IP Config Dialog */}
      {showIpConfig && (
        <div className="w-full bg-slate-850 p-3 rounded-2xl border border-slate-700/80 mb-3 text-xs">
          <div className="font-semibold text-slate-200 mb-1 flex items-center justify-between">
            <span>ADB TCP/IP Target Setup</span>
            <span className="text-[10px] text-slate-400 font-mono">Port 5555</span>
          </div>
          <div className="flex gap-2 mt-2">
            <input
              type="text"
              value={editingIp}
              onChange={(e) => setEditingIp(e.target.value)}
              placeholder="192.168.1.150"
              className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white font-mono focus:outline-none focus:border-sky-500"
            />
            {connectionStatus === 'CONNECTED' ? (
              <button
                onClick={() => {
                  onDisconnectIp();
                  setShowIpConfig(false);
                }}
                className="px-2.5 py-1.5 bg-rose-600 hover:bg-rose-500 text-white rounded-lg font-semibold text-[11px]"
              >
                Disconnect
              </button>
            ) : (
              <button
                onClick={() => {
                  onConnectIp(editingIp);
                  setShowIpConfig(false);
                }}
                className="px-3 py-1.5 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold text-[11px]"
              >
                Connect
              </button>
            )}
          </div>
        </div>
      )}

      {/* MASTER MODE TOGGLE: INFRARED vs WI-FI */}
      <div className="w-full p-1 bg-slate-950 rounded-2xl border border-slate-800 flex items-center mb-4">
        <button
          onClick={() => onSetRemoteMode('INFRARED')}
          className={`flex-1 py-2 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
            remoteMode === 'INFRARED'
              ? 'bg-slate-800 text-rose-400 shadow-md shadow-rose-950/40 border border-rose-500/30'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <Radio className="w-3.5 h-3.5" />
          <span>UNIVERSAL IR</span>
        </button>

        <button
          onClick={() => onSetRemoteMode('WI_FI')}
          className={`flex-1 py-2 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
            remoteMode === 'WI_FI'
              ? 'bg-sky-600 text-white shadow-md shadow-sky-900/50'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <Wifi className="w-3.5 h-3.5" />
          <span>FIRE TV (ADB)</span>
        </button>
      </div>

      {/* DYNAMIC MODE CONTROLS */}
      {remoteMode === 'INFRARED' ? (
        // 1. INFRARED MATRIX PANEL
        <div className="w-full flex flex-col items-center">
          {/* Universal TV Active Model Badge & Selector Trigger */}
          <button
            onClick={() => setShowBrandSelector(true)}
            className="w-full bg-slate-900 hover:bg-slate-850 border border-slate-800 hover:border-sky-500/50 rounded-2xl p-2.5 mb-3 flex items-center justify-between text-left transition-all group"
          >
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-xl bg-sky-500/10 border border-sky-500/30 flex items-center justify-center text-sky-400">
                <Tv className="w-4 h-4" />
              </div>
              <div>
                <div className="text-xs font-bold text-white flex items-center gap-1.5">
                  <span>{selectedBrand.name}</span>
                  <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-sky-950 text-sky-300 border border-sky-800">
                    {selectedBrand.carrierFrequencyHz / 1000} kHz
                  </span>
                </div>
                <div className="text-[10px] text-slate-400">
                  {selectedBrand.modelSeries}
                </div>
              </div>
            </div>
            <div className="flex items-center gap-1 text-[10px] font-semibold text-sky-400 group-hover:text-sky-300">
              <span>Change TV</span>
              <ChevronArrowRight className="w-3.5 h-3.5" />
            </div>
          </button>

          {/* Top Row: Power & Source */}
          <div className="w-full flex items-center justify-between px-2 mb-3">
            <button
              onClick={() => handleIrAction(onIrPower)}
              title={`Power Toggle (${selectedBrand.carrierFrequencyHz / 1000}kHz ${selectedBrand.protocol})`}
              className="flex flex-col items-center group"
            >
              <div className="w-13 h-13 rounded-full bg-rose-600 hover:bg-rose-500 active:scale-95 transition-all flex items-center justify-center shadow-lg shadow-rose-900/50 border border-rose-400/30">
                <Power className="w-5 h-5 text-white" />
              </div>
              <span className="text-[10px] font-bold text-slate-400 mt-1">POWER</span>
            </button>

            {/* Carrier Frequency Pulse Visualizer */}
            <div className="px-3 py-1 rounded-full bg-slate-900 border border-slate-800 text-[10px] font-mono text-slate-400 text-center">
              <div>{selectedBrand.carrierFrequencyHz / 1000}.0 kHz</div>
              <div className="text-[8px] text-rose-400 font-bold">{selectedBrand.protocol}</div>
            </div>

            <button
              onClick={() => handleIrAction(onIrSource)}
              title="Source / Input Toggle"
              className="flex flex-col items-center group"
            >
              <div className="w-13 h-13 rounded-full bg-slate-800 hover:bg-slate-700 active:scale-95 transition-all flex items-center justify-center shadow-md border border-amber-500/30">
                <Tv className="w-5 h-5 text-amber-400" />
              </div>
              <span className="text-[10px] font-bold text-slate-400 mt-1">INPUT</span>
            </button>
          </div>

          {/* Volume & Mute Row */}
          <div className="w-full bg-slate-900/70 rounded-2xl p-2.5 border border-slate-800/80 flex items-center justify-around mb-4">
            <button
              onClick={() => handleIrAction(onIrVolumeDown)}
              className="flex flex-col items-center p-2 rounded-xl hover:bg-slate-800 active:scale-95 transition-all text-slate-300"
            >
              <Volume1 className="w-5 h-5" />
              <span className="text-[9px] font-bold mt-0.5">VOL -</span>
            </button>

            <button
              onClick={() => handleIrAction(onIrMute)}
              className="flex flex-col items-center p-2 rounded-xl hover:bg-rose-950/60 active:scale-95 transition-all text-rose-400"
            >
              <VolumeX className="w-5 h-5" />
              <span className="text-[9px] font-bold mt-0.5">MUTE</span>
            </button>

            <button
              onClick={() => handleIrAction(onIrVolumeUp)}
              className="flex flex-col items-center p-2 rounded-xl hover:bg-slate-800 active:scale-95 transition-all text-slate-300"
            >
              <Volume2 className="w-5 h-5" />
              <span className="text-[9px] font-bold mt-0.5">VOL +</span>
            </button>
          </div>

          {/* Full 0-9 Numeric Keypad Layout */}
          <div className="w-full bg-slate-900/90 rounded-3xl p-3 border border-slate-800 flex flex-col items-center mb-4">
            <div className="text-[10px] font-bold tracking-widest text-slate-500 uppercase mb-2">
              Numeric Keypad Matrix ({selectedBrand.name})
            </div>
            <div className="grid grid-cols-3 gap-2.5 w-full max-w-[240px]">
              {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((digit) => (
                <button
                  key={digit}
                  onClick={() => handleIrAction(() => onIrDigit(digit))}
                  className="h-11 rounded-2xl bg-slate-800 hover:bg-slate-700 active:bg-slate-600 active:scale-95 font-semibold text-base text-slate-100 border border-slate-700/60 shadow-sm transition-all"
                >
                  {digit}
                </button>
              ))}
              <button
                onClick={() => handleIrAction(() => onIrDigit(0))}
                className="h-11 rounded-2xl bg-slate-850 hover:bg-slate-800 text-slate-400 font-bold text-xs border border-slate-700/40"
              >
                PREV
              </button>
              <button
                onClick={() => handleIrAction(() => onIrDigit(0))}
                className="h-11 rounded-2xl bg-slate-800 hover:bg-slate-700 active:bg-slate-600 active:scale-95 font-semibold text-base text-slate-100 border border-slate-700/60 shadow-sm transition-all"
              >
                0
              </button>
              <button
                onClick={() => handleIrAction(() => onIrDigit(0))}
                className="h-11 rounded-2xl bg-slate-850 hover:bg-slate-800 text-slate-400 font-bold text-xs border border-slate-700/40"
              >
                ENT
              </button>
            </div>
          </div>
        </div>
      ) : (
        // 2. WI-FI (ADB) MODE PANEL
        <div className="w-full flex flex-col items-center">
          {/* Massive 5-Way D-Pad Gesture Surface */}
          <div className="relative w-56 h-56 rounded-full bg-gradient-to-tr from-slate-950 via-slate-900 to-slate-850 border-4 border-slate-700/60 shadow-2xl flex items-center justify-center my-1">
            {/* UP BUTTON */}
            <button
              onClick={() => onDpadCommand(19, 'DPAD_UP')}
              title="adb shell input keyevent 19"
              className="absolute top-2 left-1/2 -translate-x-1/2 w-14 h-12 flex items-center justify-center text-slate-300 hover:text-white hover:bg-slate-700/50 rounded-t-2xl active:scale-95 transition-all"
            >
              <ChevronUp className="w-8 h-8" />
            </button>

            {/* DOWN BUTTON */}
            <button
              onClick={() => onDpadCommand(20, 'DPAD_DOWN')}
              title="adb shell input keyevent 20"
              className="absolute bottom-2 left-1/2 -translate-x-1/2 w-14 h-12 flex items-center justify-center text-slate-300 hover:text-white hover:bg-slate-700/50 rounded-b-2xl active:scale-95 transition-all"
            >
              <ChevronDown className="w-8 h-8" />
            </button>

            {/* LEFT BUTTON */}
            <button
              onClick={() => onDpadCommand(21, 'DPAD_LEFT')}
              title="adb shell input keyevent 21"
              className="absolute left-2 top-1/2 -translate-y-1/2 w-12 h-14 flex items-center justify-center text-slate-300 hover:text-white hover:bg-slate-700/50 rounded-l-2xl active:scale-95 transition-all"
            >
              <ChevronLeft className="w-8 h-8" />
            </button>

            {/* RIGHT BUTTON */}
            <button
              onClick={() => onDpadCommand(22, 'DPAD_RIGHT')}
              title="adb shell input keyevent 22"
              className="absolute right-2 top-1/2 -translate-y-1/2 w-12 h-14 flex items-center justify-center text-slate-300 hover:text-white hover:bg-slate-700/50 rounded-r-2xl active:scale-95 transition-all"
            >
              <ChevronRight className="w-8 h-8" />
            </button>

            {/* CENTER OK / SELECT BUTTON */}
            <button
              onClick={() => onDpadCommand(23, 'DPAD_CENTER')}
              title="adb shell input keyevent 23"
              className="w-20 h-20 rounded-full bg-gradient-to-tr from-sky-600 to-blue-500 hover:from-sky-500 hover:to-blue-400 active:scale-90 transition-all shadow-xl shadow-sky-950/60 flex items-center justify-center font-black text-sm text-white tracking-widest border border-sky-300/40"
            >
              OK
            </button>
          </div>

          {/* Navigation Row: Back, Home, Search, Menu */}
          <div className="w-full flex items-center justify-between px-2 my-3">
            <button
              onClick={() => onDpadCommand(4, 'BACK')}
              className="flex flex-col items-center group"
            >
              <div className="w-11 h-11 rounded-full bg-slate-800 hover:bg-slate-700 active:scale-95 transition-all flex items-center justify-center border border-slate-700">
                <ArrowLeft className="w-4 h-4 text-slate-300 group-hover:text-white" />
              </div>
              <span className="text-[9px] font-semibold text-slate-400 mt-1">BACK</span>
            </button>

            <button
              onClick={() => onDpadCommand(3, 'HOME')}
              className="flex flex-col items-center group"
            >
              <div className="w-11 h-11 rounded-full bg-slate-800 hover:bg-slate-700 active:scale-95 transition-all flex items-center justify-center border border-slate-700">
                <Home className="w-4 h-4 text-sky-400 group-hover:text-sky-300" />
              </div>
              <span className="text-[9px] font-semibold text-slate-400 mt-1">HOME</span>
            </button>

            <button
              onClick={onSearchKey}
              title="adb shell input keyevent 84 (SEARCH)"
              className="flex flex-col items-center group"
            >
              <div className="w-11 h-11 rounded-full bg-sky-950/80 hover:bg-sky-900 border border-sky-500/50 active:scale-95 transition-all flex items-center justify-center shadow-md shadow-sky-950/40">
                <Search className="w-4 h-4 text-sky-300 group-hover:text-white" />
              </div>
              <span className="text-[9px] font-bold text-sky-400 mt-1">SEARCH</span>
            </button>

            <button
              onClick={() => onDpadCommand(82, 'MENU')}
              className="flex flex-col items-center group"
            >
              <div className="w-11 h-11 rounded-full bg-slate-800 hover:bg-slate-700 active:scale-95 transition-all flex items-center justify-center border border-slate-700">
                <Menu className="w-4 h-4 text-slate-300 group-hover:text-white" />
              </div>
              <span className="text-[9px] font-semibold text-slate-400 mt-1">MENU</span>
            </button>
          </div>

          {/* Quick Search Preset Chips */}
          <div className="w-full flex items-center gap-1.5 overflow-x-auto pb-1 mb-2 scrollbar-none">
            {SEARCH_PRESETS.map((preset) => (
              <button
                key={preset}
                onClick={() => onSendPreset(preset)}
                className="shrink-0 px-2 py-0.5 rounded-full bg-slate-900 hover:bg-slate-800 border border-slate-800 hover:border-sky-500/50 text-[10px] text-slate-300 font-medium transition-all"
              >
                {preset}
              </button>
            ))}
          </div>

          {/* Persistent Floating Keyboard & Search Input Field */}
          <div className="w-full bg-slate-900/90 rounded-2xl p-1.5 border border-slate-800 flex flex-col gap-1.5 mb-2">
            <form onSubmit={handleTextSubmit} className="flex items-center gap-1">
              <button
                type="button"
                onClick={onSearchKey}
                title="Activate TV Search Bar"
                className="p-1.5 rounded-lg text-slate-400 hover:text-sky-300 transition-colors"
              >
                <Search className="w-3.5 h-3.5" />
              </button>

              <input
                type="text"
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                placeholder="Type or search on TV..."
                className="flex-1 bg-transparent px-2 py-1 text-xs text-white placeholder-slate-500 focus:outline-none font-mono"
              />

              {inputText && (
                <button
                  type="button"
                  onClick={() => setInputText('')}
                  className="p-1 rounded text-slate-500 hover:text-slate-300"
                >
                  <X className="w-3 h-3" />
                </button>
              )}

              <button
                type="button"
                onClick={onBackspace}
                title="adb keyevent 67 (DEL)"
                className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition-colors"
              >
                <Delete className="w-3.5 h-3.5" />
              </button>

              <button
                type="button"
                onClick={() => setShowVirtualKeyboard(!showVirtualKeyboard)}
                title="Toggle Virtual Keyboard"
                className={`p-1.5 rounded-lg transition-colors ${
                  showVirtualKeyboard ? 'text-sky-400 bg-slate-800' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Keyboard className="w-3.5 h-3.5" />
              </button>

              <button
                type="submit"
                className="px-2.5 py-1.5 rounded-xl bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold flex items-center gap-1 shrink-0 transition-all active:scale-95"
              >
                <Send className="w-3 h-3" />
                <span>Send</span>
              </button>
            </form>

            {/* Expandable Virtual Touch Keyboard */}
            {showVirtualKeyboard && (
              <div className="pt-2 border-t border-slate-800/80 flex flex-col gap-1 items-center font-mono">
                {/* Row 1 */}
                <div className="flex gap-1 justify-center w-full">
                  {'QWERTYUIOP'.split('').map((char) => (
                    <button
                      key={char}
                      onClick={() => handleVirtualKey(char)}
                      className="flex-1 h-7 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold active:scale-95 transition-all"
                    >
                      {char}
                    </button>
                  ))}
                </div>

                {/* Row 2 */}
                <div className="flex gap-1 justify-center w-[92%]">
                  {'ASDFGHJKL'.split('').map((char) => (
                    <button
                      key={char}
                      onClick={() => handleVirtualKey(char)}
                      className="flex-1 h-7 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold active:scale-95 transition-all"
                    >
                      {char}
                    </button>
                  ))}
                </div>

                {/* Row 3 */}
                <div className="flex gap-1 justify-center w-[80%]">
                  {'ZXCVBNM'.split('').map((char) => (
                    <button
                      key={char}
                      onClick={() => handleVirtualKey(char)}
                      className="flex-1 h-7 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold active:scale-95 transition-all"
                    >
                      {char}
                    </button>
                  ))}
                </div>

                {/* Bottom Row: Backspace, Space, Enter */}
                <div className="flex gap-1 w-full mt-1">
                  <button
                    onClick={handleVirtualBackspace}
                    className="flex-1 h-7 rounded bg-rose-950/70 hover:bg-rose-900/80 text-rose-300 text-[10px] font-bold flex items-center justify-center gap-0.5"
                  >
                    <Delete className="w-3 h-3" />
                    <span>DEL</span>
                  </button>

                  <button
                    onClick={() => handleVirtualKey(' ')}
                    className="flex-[2.5] h-7 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold"
                  >
                    SPACE
                  </button>

                  <button
                    onClick={onEnterKey}
                    className="flex-[1.5] h-7 rounded bg-sky-600 hover:bg-sky-500 text-white text-[10px] font-bold flex items-center justify-center gap-0.5 shadow-md shadow-sky-950/40"
                  >
                    <CornerDownLeft className="w-3 h-3" />
                    <span>ENTER</span>
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* APP DOCK INTEGRATION: Horizontal Quick-Launch Dock */}
      <div className="w-full pt-2 border-t border-slate-800/80 mt-1">
        <div className="flex items-center justify-between mb-2 px-1">
          <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
            <Sparkles className="w-3 h-3 text-amber-400" />
            <span>Fire TV App Dock</span>
          </div>
          <button
            onClick={onRefreshApps}
            title="adb shell pm list packages -3"
            className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors"
          >
            <RefreshCw className="w-3 h-3" />
          </button>
        </div>

        {/* Horizontal LazyRow-Style Quick-Launch Scroll */}
        <div className="w-full overflow-x-auto pb-1 scrollbar-thin scrollbar-thumb-slate-700">
          <div className="flex items-center gap-2">
            {appDockList.map((app) => (
              <button
                key={app.id}
                onClick={() => onLaunchApp(app)}
                title={`Launch: ${app.packageName}`}
                className="group flex flex-col items-center shrink-0 w-16 p-1.5 rounded-xl bg-slate-900/80 hover:bg-slate-800 border border-slate-800 hover:border-sky-500/50 transition-all active:scale-95"
              >
                <div
                  className="w-9 h-9 rounded-xl flex items-center justify-center font-bold text-white text-xs shadow-md mb-1 transition-transform group-hover:scale-105"
                  style={{ backgroundColor: app.color }}
                >
                  {app.name.slice(0, 1)}
                </div>
                <span className="text-[10px] font-medium text-slate-300 truncate w-full text-center">
                  {app.name}
                </span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
