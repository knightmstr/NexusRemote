import React, { useState } from 'react';
import {
  RemoteMode,
  RemoteSkin,
  ConnectionStatus,
  InstalledApp,
  IrPulseEvent,
  TvBrandProfile,
} from '../types';
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
  Check,
  ChevronRight as ChevronArrowRight,
  Palette,
} from 'lucide-react';

interface RemoteControlProps {
  remoteMode: RemoteMode;
  skin?: RemoteSkin;
  connectionStatus: ConnectionStatus;
  targetIp: string;
  hasIrEmitter: boolean;
  selectedBrand: TvBrandProfile;
  availableBrands: TvBrandProfile[];
  appDockList: InstalledApp[];
  lastIrPulse: IrPulseEvent | null;
  onSetRemoteMode: (mode: RemoteMode) => void;
  onChangeSkin?: (skin: RemoteSkin) => void;
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
  'Trending News',
];

const SKIN_OPTIONS: { id: RemoteSkin; label: string; shortLabel: string }[] = [
  { id: 'classic-ir', label: 'Classic Infrared', shortLabel: 'Classic IR' },
  { id: 'modern-streaming', label: 'Modern Streaming', shortLabel: 'Modern' },
  { id: 'minimalist', label: 'Minimalist', shortLabel: 'Minimalist' },
];

export const RemoteControl: React.FC<RemoteControlProps> = ({
  remoteMode,
  skin,
  connectionStatus,
  targetIp,
  hasIrEmitter,
  selectedBrand,
  availableBrands,
  appDockList,
  lastIrPulse,
  onSetRemoteMode,
  onChangeSkin,
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
  const [internalSkin, setInternalSkin] = useState<RemoteSkin>('modern-streaming');
  const activeSkin: RemoteSkin = skin ?? internalSkin;

  const [inputText, setInputText] = useState('');
  const [brandQuery, setBrandQuery] = useState('');
  const [showIpConfig, setShowIpConfig] = useState(false);
  const [showBrandSelector, setShowBrandSelector] = useState(false);
  const [editingIp, setEditingIp] = useState(targetIp);
  const [isPressingLed, setIsPressingLed] = useState(false);
  const [showVirtualKeyboard, setShowVirtualKeyboard] = useState(false);

  const normalizedQuery = brandQuery.trim().toLowerCase();
  const filteredBrands = normalizedQuery
    ? availableBrands.filter(
        (b) =>
          b.name.toLowerCase().includes(normalizedQuery) ||
          b.modelSeries.toLowerCase().includes(normalizedQuery) ||
          b.protocol.toLowerCase().includes(normalizedQuery)
      )
    : availableBrands;

  const handleSkinSelect = (nextSkin: RemoteSkin) => {
    if (onChangeSkin) {
      onChangeSkin(nextSkin);
    } else {
      setInternalSkin(nextSkin);
    }
  };

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

  const isEmitterLit =
    isPressingLed || (lastIrPulse && Date.now() - lastIrPulse.timestamp < 350);

  return (
    <div className="w-full flex flex-col items-center gap-3">
      {/* CSS Skin Switcher Bar */}
      <div
        role="group"
        aria-label="Remote visual skin"
        className="w-full max-w-[360px] bg-slate-900/90 border border-slate-800 rounded-2xl p-1 flex items-center gap-1"
      >
        <span
          className="px-2 text-slate-400 flex items-center shrink-0"
          title="Remote Visual Style"
        >
          <Palette className="w-3.5 h-3.5 text-sky-400" />
        </span>
        {SKIN_OPTIONS.map((option) => {
          const isSelected = activeSkin === option.id;
          return (
            <button
              key={option.id}
              type="button"
              onClick={() => handleSkinSelect(option.id)}
              aria-pressed={isSelected}
              title={option.label}
              className={`flex-1 py-1.5 px-2 rounded-xl text-[11px] font-semibold whitespace-nowrap shrink-0 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 ${
                isSelected
                  ? 'bg-sky-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70'
              }`}
            >
              {option.shortLabel}
            </button>
          );
        })}
      </div>

      {/* Skinned Hardware Remote Chassis */}
      <div
        data-remote-skin={activeSkin}
        className="rc-chassis relative mx-auto w-full max-w-[360px] p-5 flex flex-col items-center select-none"
      >
        {/* Top Infrared LED Blaster Emitter */}
        <div className="relative -mt-8 mb-3 flex flex-col items-center">
          <div
            className={`rc-emitter w-6 h-3 rounded-t-full ${
              isEmitterLit ? 'rc-emitter--active' : ''
            }`}
          />
          <div className="rc-text-muted text-[9px] tracking-widest uppercase font-mono mt-1 tabular-nums">
            Consumer IR Blaster · {selectedBrand.carrierFrequencyHz / 1000} kHz
          </div>
        </div>

        {/* Top Remote Header: Brand Selector & Wi-Fi Connection */}
        <div className="rc-panel w-full p-2 mb-3 text-xs flex items-center justify-between gap-1.5">
          <button
            type="button"
            onClick={() => {
              setShowBrandSelector(!showBrandSelector);
              setShowIpConfig(false);
            }}
            title="Select Television Brand and Model"
            className="rc-surface-btn flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl text-[10px] font-semibold group whitespace-nowrap shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-400"
          >
            <span
              className={`w-2 h-2 rounded-full ${
                hasIrEmitter
                  ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]'
                  : 'bg-slate-500'
              }`}
            />
            <Tv className="w-3 h-3 text-sky-500 group-hover:scale-110 transition-transform" />
            <span className="rc-text-primary font-bold tracking-tight">
              {selectedBrand.name.split(' ')[0]}
            </span>
            <span className="rc-text-secondary text-[9px] font-mono tabular-nums">
              {selectedBrand.carrierFrequencyHz / 1000}k
            </span>
          </button>

          <button
            type="button"
            onClick={() => {
              setShowIpConfig(!showIpConfig);
              setShowBrandSelector(false);
            }}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-full text-[10px] font-mono border transition-all whitespace-nowrap shrink-0 tabular-nums focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 ${
              connectionStatus === 'CONNECTED'
                ? 'rc-pill-online'
                : connectionStatus === 'CONNECTING'
                ? 'bg-sky-950/80 border-sky-500/50 text-sky-300'
                : connectionStatus === 'AUTH_REQUIRED'
                ? 'rc-pill-auth'
                : 'rc-pill-offline'
            }`}
          >
            {connectionStatus === 'CONNECTED' ? (
              <Wifi className="w-3 h-3" />
            ) : (
              <WifiOff className="w-3 h-3" />
            )}
            <span>{targetIp || 'Set IP'}</span>
          </button>
        </div>

        {/* Expandable Universal TV Brand Selector Drawer */}
        {showBrandSelector && (
          <div className="rc-panel w-full p-3 shadow-xl mb-3 text-xs max-h-80 overflow-y-auto">
            <div className="rc-text-primary font-bold mb-1.5 flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <Tv className="w-4 h-4 text-sky-500" />
                <span>Universal TV Database ({availableBrands.length})</span>
              </span>
              <button
                type="button"
                onClick={() => {
                  setShowBrandSelector(false);
                  setBrandQuery('');
                }}
                className="rc-text-secondary hover:opacity-80 p-1 rounded"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
            <p className="rc-text-secondary text-[10px] mb-2">
              Verified profiles sourced from probonopd/irdb &amp; Flipper-IRDB:
            </p>
            <div className="relative mb-2.5">
              <Search className="w-3.5 h-3.5 rc-text-muted absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              <input
                type="text"
                value={brandQuery}
                onChange={(e) => setBrandQuery(e.target.value)}
                placeholder="Filter brand, model, or protocol..."
                className="rc-surface-btn w-full rounded-xl pl-8 pr-7 py-1.5 text-[11px] focus:outline-none focus:ring-2 focus:ring-sky-500"
              />
              {brandQuery && (
                <button
                  type="button"
                  onClick={() => setBrandQuery('')}
                  className="rc-text-muted hover:opacity-80 absolute right-2 top-1/2 -translate-y-1/2"
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </div>
            <div className="flex flex-col gap-1.5">
              {filteredBrands.length === 0 ? (
                <div className="rc-text-muted text-center py-4 text-[11px]">
                  No TV models matching &ldquo;{brandQuery}&rdquo;
                </div>
              ) : (
                filteredBrands.map((brand) => {
                  const isSelected = brand.id === selectedBrand.id;
                  return (
                    <button
                      key={brand.id}
                      type="button"
                      onClick={() => {
                        onSelectBrand(brand);
                        setShowBrandSelector(false);
                        setBrandQuery('');
                      }}
                      className={`flex items-center justify-between p-2 rounded-xl text-left transition-all ${
                        isSelected ? 'rc-seg-wifi-active shadow-md' : 'rc-surface-btn'
                      }`}
                    >
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-xs truncate">{brand.name}</span>
                          <span className="text-[9px] font-mono opacity-85 tabular-nums whitespace-nowrap">
                            {brand.carrierFrequencyHz / 1000} kHz · {brand.protocol}
                          </span>
                        </div>
                        <div className="text-[10px] opacity-80 truncate mt-0.5">
                          {brand.modelSeries}
                        </div>
                        <div className="text-[9px] font-mono opacity-60 truncate mt-0.5">
                          {brand.source}
                        </div>
                      </div>
                      {isSelected && <Check className="w-4 h-4 shrink-0 ml-2" />}
                    </button>
                  );
                })
              )}
            </div>
          </div>
        )}

        {/* Expandable IP Config Drawer */}
        {showIpConfig && (
          <div className="rc-panel w-full p-3 mb-3 text-xs">
            <div className="rc-text-primary font-semibold mb-1 flex items-center justify-between">
              <span>ADB TCP/IP Target Setup</span>
              <span className="rc-text-muted text-[10px] font-mono">Port 5555</span>
            </div>
            <div className="flex gap-2 mt-2">
              <input
                type="text"
                value={editingIp}
                onChange={(e) => setEditingIp(e.target.value)}
                placeholder="192.168.1.150"
                className="rc-surface-btn flex-1 rounded-lg px-2.5 py-1.5 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-sky-500"
              />
              {connectionStatus === 'CONNECTED' ? (
                <button
                  type="button"
                  onClick={() => {
                    onDisconnectIp();
                    setShowIpConfig(false);
                  }}
                  className="px-2.5 py-1.5 bg-rose-600 hover:bg-rose-500 text-white rounded-lg font-semibold text-[11px] whitespace-nowrap shrink-0"
                >
                  Disconnect
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => {
                    onConnectIp(editingIp);
                    setShowIpConfig(false);
                  }}
                  className="px-3 py-1.5 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold text-[11px] whitespace-nowrap shrink-0"
                >
                  Connect
                </button>
              )}
            </div>
          </div>
        )}

        {/* Master Mode Toggle: Universal IR vs Fire TV (ADB) */}
        <div className="rc-seg-track w-full p-1 flex items-center gap-1 mb-4">
          <button
            type="button"
            onClick={() => onSetRemoteMode('INFRARED')}
            className={`flex-1 py-2 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 whitespace-nowrap ${
              remoteMode === 'INFRARED' ? 'rc-seg-ir-active shadow-sm' : 'rc-seg-idle'
            }`}
          >
            <Radio className="w-3.5 h-3.5" />
            <span>UNIVERSAL IR</span>
          </button>

          <button
            type="button"
            onClick={() => onSetRemoteMode('WI_FI')}
            className={`flex-1 py-2 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 whitespace-nowrap ${
              remoteMode === 'WI_FI' ? 'rc-seg-wifi-active shadow-sm' : 'rc-seg-idle'
            }`}
          >
            <Wifi className="w-3.5 h-3.5" />
            <span>FIRE TV (ADB)</span>
          </button>
        </div>

        {/* Dynamic Mode Controls */}
        {remoteMode === 'INFRARED' ? (
          <div className="w-full flex flex-col items-center">
            {/* Active Universal TV Model Selector Trigger */}
            <button
              type="button"
              onClick={() => setShowBrandSelector(true)}
              className="rc-panel w-full p-2.5 mb-3 flex items-center justify-between text-left transition-all group hover:opacity-95"
            >
              <div className="flex items-center gap-2 min-w-0">
                <div className="rc-surface-btn w-7 h-7 rounded-xl flex items-center justify-center text-sky-500 shrink-0">
                  <Tv className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <div className="rc-text-primary text-xs font-bold flex items-center gap-1.5">
                    <span className="truncate">{selectedBrand.name}</span>
                    <span className="rc-badge text-[9px] font-mono px-1.5 py-0.5 rounded tabular-nums shrink-0">
                      {selectedBrand.carrierFrequencyHz / 1000} kHz
                    </span>
                  </div>
                  <div className="rc-text-secondary text-[10px] truncate">
                    {selectedBrand.modelSeries}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-1 text-[10px] font-semibold text-sky-500 shrink-0 ml-2">
                <span>Change TV</span>
                <ChevronArrowRight className="w-3.5 h-3.5" />
              </div>
            </button>

            {/* Top Row: Power, Carrier Readout, Source/Input */}
            <div className="w-full flex items-center justify-between px-2 mb-3">
              <button
                type="button"
                onClick={() => handleIrAction(onIrPower)}
                title={`Power Toggle (${selectedBrand.carrierFrequencyHz / 1000}kHz ${selectedBrand.protocol})`}
                className="flex flex-col items-center group"
              >
                <div className="rc-power-key w-13 h-13 rounded-full flex items-center justify-center">
                  <Power className="w-5 h-5" />
                </div>
                <span className="rc-text-secondary text-[10px] font-bold mt-1">POWER</span>
              </button>

              <div className="rc-panel px-3 py-1 rounded-full text-[10px] font-mono text-center tabular-nums">
                <div className="rc-text-secondary">
                  {selectedBrand.carrierFrequencyHz / 1000}.0 kHz
                </div>
                <div className="text-[8px] font-bold text-rose-500">{selectedBrand.protocol}</div>
              </div>

              <button
                type="button"
                onClick={() => handleIrAction(onIrSource)}
                title="Source / Input Toggle"
                className="flex flex-col items-center group"
              >
                <div className="rc-input-key w-13 h-13 rounded-full flex items-center justify-center">
                  <Tv className="w-5 h-5" />
                </div>
                <span className="rc-text-secondary text-[10px] font-bold mt-1">INPUT</span>
              </button>
            </div>

            {/* Volume & Mute Row */}
            <div className="rc-panel w-full p-2 flex items-center justify-around mb-4">
              <button
                type="button"
                onClick={() => handleIrAction(onIrVolumeDown)}
                className="rc-text-secondary hover:opacity-80 flex flex-col items-center p-2 rounded-xl active:scale-95 transition-all"
              >
                <Volume1 className="w-5 h-5" />
                <span className="text-[9px] font-bold mt-0.5">VOL -</span>
              </button>

              <button
                type="button"
                onClick={() => handleIrAction(onIrMute)}
                className="flex flex-col items-center p-2 rounded-xl text-rose-500 hover:opacity-80 active:scale-95 transition-all"
              >
                <VolumeX className="w-5 h-5" />
                <span className="text-[9px] font-bold mt-0.5">MUTE</span>
              </button>

              <button
                type="button"
                onClick={() => handleIrAction(onIrVolumeUp)}
                className="rc-text-secondary hover:opacity-80 flex flex-col items-center p-2 rounded-xl active:scale-95 transition-all"
              >
                <Volume2 className="w-5 h-5" />
                <span className="text-[9px] font-bold mt-0.5">VOL +</span>
              </button>
            </div>

            {/* 0-9 Numeric Keypad Matrix */}
            <div className="rc-panel w-full p-3 flex flex-col items-center mb-4">
              <div className="rc-text-muted text-[10px] font-bold tracking-widest uppercase mb-2 text-center">
                Numeric Keypad Matrix ({selectedBrand.name})
              </div>
              <div className="grid grid-cols-3 gap-2.5 w-full max-w-[240px]">
                {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((digit) => (
                  <button
                    key={digit}
                    type="button"
                    onClick={() => handleIrAction(() => onIrDigit(digit))}
                    className="rc-key h-11 font-semibold text-base tabular-nums"
                  >
                    {digit}
                  </button>
                ))}
                <button
                  type="button"
                  onClick={() => handleIrAction(() => onIrDigit(0))}
                  className="rc-surface-btn h-11 rounded-2xl rc-text-secondary font-bold text-xs"
                >
                  PREV
                </button>
                <button
                  type="button"
                  onClick={() => handleIrAction(() => onIrDigit(0))}
                  className="rc-key h-11 font-semibold text-base tabular-nums"
                >
                  0
                </button>
                <button
                  type="button"
                  onClick={() => handleIrAction(() => onIrDigit(0))}
                  className="rc-surface-btn h-11 rounded-2xl rc-text-secondary font-bold text-xs"
                >
                  ENT
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div className="w-full flex flex-col items-center">
            {/* 5-Way D-Pad Wheel */}
            <div className="rc-dpad relative w-56 h-56 rounded-full flex items-center justify-center my-1">
              <button
                type="button"
                onClick={() => onDpadCommand(19, 'DPAD_UP')}
                title="adb shell input keyevent 19"
                className="rc-dpad-arrow absolute top-2 left-1/2 -translate-x-1/2 w-14 h-12 flex items-center justify-center rounded-t-2xl active:scale-95"
              >
                <ChevronUp className="w-8 h-8" />
              </button>

              <button
                type="button"
                onClick={() => onDpadCommand(20, 'DPAD_DOWN')}
                title="adb shell input keyevent 20"
                className="rc-dpad-arrow absolute bottom-2 left-1/2 -translate-x-1/2 w-14 h-12 flex items-center justify-center rounded-b-2xl active:scale-95"
              >
                <ChevronDown className="w-8 h-8" />
              </button>

              <button
                type="button"
                onClick={() => onDpadCommand(21, 'DPAD_LEFT')}
                title="adb shell input keyevent 21"
                className="rc-dpad-arrow absolute left-2 top-1/2 -translate-y-1/2 w-12 h-14 flex items-center justify-center rounded-l-2xl active:scale-95"
              >
                <ChevronLeft className="w-8 h-8" />
              </button>

              <button
                type="button"
                onClick={() => onDpadCommand(22, 'DPAD_RIGHT')}
                title="adb shell input keyevent 22"
                className="rc-dpad-arrow absolute right-2 top-1/2 -translate-y-1/2 w-12 h-14 flex items-center justify-center rounded-r-2xl active:scale-95"
              >
                <ChevronRight className="w-8 h-8" />
              </button>

              <button
                type="button"
                onClick={() => onDpadCommand(23, 'DPAD_CENTER')}
                title="adb shell input keyevent 23"
                className="rc-ok-key w-20 h-20 rounded-full flex items-center justify-center font-black text-sm tracking-widest"
              >
                OK
              </button>
            </div>

            {/* Navigation Row: Back, Home, Search, Menu */}
            <div className="w-full flex items-center justify-between px-2 my-3">
              <button
                type="button"
                onClick={() => onDpadCommand(4, 'BACK')}
                className="flex flex-col items-center group"
              >
                <div className="rc-key w-11 h-11 rounded-full flex items-center justify-center">
                  <ArrowLeft className="w-4 h-4" />
                </div>
                <span className="rc-text-secondary text-[9px] font-semibold mt-1">BACK</span>
              </button>

              <button
                type="button"
                onClick={() => onDpadCommand(3, 'HOME')}
                className="flex flex-col items-center group"
              >
                <div className="rc-key w-11 h-11 rounded-full flex items-center justify-center text-sky-500">
                  <Home className="w-4 h-4" />
                </div>
                <span className="rc-text-secondary text-[9px] font-semibold mt-1">HOME</span>
              </button>

              <button
                type="button"
                onClick={onSearchKey}
                title="adb shell input keyevent 84 (SEARCH)"
                className="flex flex-col items-center group"
              >
                <div className="rc-search-key w-11 h-11 rounded-full flex items-center justify-center">
                  <Search className="w-4 h-4" />
                </div>
                <span className="text-[9px] font-bold text-sky-500 mt-1">SEARCH</span>
              </button>

              <button
                type="button"
                onClick={() => onDpadCommand(82, 'MENU')}
                className="flex flex-col items-center group"
              >
                <div className="rc-key w-11 h-11 rounded-full flex items-center justify-center">
                  <Menu className="w-4 h-4" />
                </div>
                <span className="rc-text-secondary text-[9px] font-semibold mt-1">MENU</span>
              </button>
            </div>

            {/* Quick Search Preset Chips */}
            <div className="w-full flex items-center gap-1.5 overflow-x-auto pb-1 mb-2 scrollbar-none">
              {SEARCH_PRESETS.map((preset) => (
                <button
                  key={preset}
                  type="button"
                  onClick={() => onSendPreset(preset)}
                  className="rc-surface-btn shrink-0 px-2.5 py-1 rounded-full text-[10px] font-medium whitespace-nowrap"
                >
                  {preset}
                </button>
              ))}
            </div>

            {/* Persistent Floating Keyboard & Search Input Field */}
            <div className="rc-panel w-full p-1.5 flex flex-col gap-1.5 mb-2">
              <form onSubmit={handleTextSubmit} className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={onSearchKey}
                  title="Activate TV Search Bar"
                  className="rc-text-secondary hover:opacity-80 p-1.5 rounded-lg transition-colors"
                >
                  <Search className="w-3.5 h-3.5" />
                </button>

                <input
                  type="text"
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  placeholder="Type or search on TV..."
                  className="rc-text-primary flex-1 bg-transparent px-2 py-1 text-xs focus:outline-none font-mono placeholder:opacity-50"
                />

                {inputText && (
                  <button
                    type="button"
                    onClick={() => setInputText('')}
                    className="rc-text-muted hover:opacity-80 p-1 rounded"
                  >
                    <X className="w-3 h-3" />
                  </button>
                )}

                <button
                  type="button"
                  onClick={onBackspace}
                  title="adb keyevent 67 (DEL)"
                  className="rc-text-secondary hover:text-rose-500 p-1.5 rounded-lg transition-colors"
                >
                  <Delete className="w-3.5 h-3.5" />
                </button>

                <button
                  type="button"
                  onClick={() => setShowVirtualKeyboard(!showVirtualKeyboard)}
                  title="Toggle Virtual Keyboard"
                  className={`p-1.5 rounded-lg transition-colors ${
                    showVirtualKeyboard ? 'rc-seg-wifi-active' : 'rc-text-secondary'
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

              {showVirtualKeyboard && (
                <div className="pt-2 border-t border-current/10 flex flex-col gap-1 items-center font-mono">
                  <div className="flex gap-1 justify-center w-full">
                    {'QWERTYUIOP'.split('').map((char) => (
                      <button
                        key={char}
                        type="button"
                        onClick={() => handleVirtualKey(char)}
                        className="rc-key flex-1 h-7 text-xs font-bold"
                      >
                        {char}
                      </button>
                    ))}
                  </div>

                  <div className="flex gap-1 justify-center w-[92%]">
                    {'ASDFGHJKL'.split('').map((char) => (
                      <button
                        key={char}
                        type="button"
                        onClick={() => handleVirtualKey(char)}
                        className="rc-key flex-1 h-7 text-xs font-bold"
                      >
                        {char}
                      </button>
                    ))}
                  </div>

                  <div className="flex gap-1 justify-center w-[80%]">
                    {'ZXCVBNM'.split('').map((char) => (
                      <button
                        key={char}
                        type="button"
                        onClick={() => handleVirtualKey(char)}
                        className="rc-key flex-1 h-7 text-xs font-bold"
                      >
                        {char}
                      </button>
                    ))}
                  </div>

                  <div className="flex gap-1 w-full mt-1">
                    <button
                      type="button"
                      onClick={handleVirtualBackspace}
                      className="rc-kbd-del flex-1 h-7 rounded text-[10px] font-bold flex items-center justify-center gap-0.5"
                    >
                      <Delete className="w-3 h-3" />
                      <span>DEL</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => handleVirtualKey(' ')}
                      className="rc-key flex-[2.5] h-7 text-xs font-semibold"
                    >
                      SPACE
                    </button>

                    <button
                      type="button"
                      onClick={onEnterKey}
                      className="flex-[1.5] h-7 rounded bg-sky-600 hover:bg-sky-500 text-white text-[10px] font-bold flex items-center justify-center gap-0.5 shadow-sm"
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

        {/* Bottom App Launch Dock */}
        <div className="w-full pt-2 border-t border-current/10 mt-1">
          <div className="flex items-center justify-between mb-2 px-1">
            <div className="rc-text-secondary text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
              <Sparkles className="w-3 h-3 text-amber-400" />
              <span>Fire TV App Dock</span>
            </div>
            <button
              type="button"
              onClick={onRefreshApps}
              title="adb shell pm list packages -3"
              className="rc-text-secondary hover:opacity-80 p-1 rounded-lg transition-colors"
            >
              <RefreshCw className="w-3 h-3" />
            </button>
          </div>

          <div className="w-full overflow-x-auto pb-1 scrollbar-thin scrollbar-thumb-slate-700">
            <div className="flex items-center gap-2">
              {appDockList.map((app) => (
                <button
                  key={app.id}
                  type="button"
                  onClick={() => onLaunchApp(app)}
                  title={`Launch: ${app.packageName}`}
                  className="rc-panel group flex flex-col items-center shrink-0 w-16 p-1.5 transition-all active:scale-95 hover:opacity-90"
                >
                  <div
                    className="w-9 h-9 rounded-xl flex items-center justify-center font-bold text-white text-xs shadow-md mb-1 transition-transform group-hover:scale-105"
                    style={{ backgroundColor: app.color }}
                  >
                    {app.name.slice(0, 1)}
                  </div>
                  <span className="rc-text-secondary text-[10px] font-medium truncate w-full text-center">
                    {app.name}
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
