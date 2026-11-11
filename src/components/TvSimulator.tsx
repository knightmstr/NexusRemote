import React from 'react';
import { ConnectionStatus, InstalledApp } from '../types';
import { Tv, ShieldAlert, Check, Search, Wifi, Volume2, VolumeX, Film, Play, Sparkles, X } from 'lucide-react';

interface TvSimulatorProps {
  isPoweredOn: boolean;
  activeSource: string;
  volume: number;
  isMuted: boolean;
  focusedTile: number;
  activeApp: InstalledApp | null;
  onScreenText: string;
  isSearchActive: boolean;
  connectionStatus: ConnectionStatus;
  lastCommand: string | null;
  apps: InstalledApp[];
  onAuthorizeAdb: () => void;
  onRejectAdb: () => void;
  onSelectTile: (tileIndex: number) => void;
  onCloseSearch: () => void;
}

interface SearchResultItem {
  id: string;
  title: string;
  year: string;
  rating: string;
  quality: string;
  app: string;
  color: string;
}

const SAMPLE_SEARCH_CATALOG: SearchResultItem[] = [
  { id: '1', title: 'Interstellar', year: '2014', rating: '98% Match', quality: '4K UHD • HDR10', app: 'Prime Video', color: '#00A8E1' },
  { id: '2', title: 'Stranger Things', year: '2025', rating: '96% Match', quality: '4K Dolby Vision', app: 'Netflix', color: '#E50914' },
  { id: '3', title: 'The Mandalorian', year: '2024', rating: '94% Match', quality: '4K UHD • IMAX', app: 'Disney+', color: '#113CCF' },
  { id: '4', title: 'Top Gun: Maverick', year: '2023', rating: '95% Match', quality: '4K UHD • Dolby Atmos', app: 'Prime Video', color: '#00A8E1' },
  { id: '5', title: 'Cyberpunk Edgerunners', year: '2023', rating: '92% Match', quality: '1080p HD', app: 'Netflix', color: '#E50914' },
  { id: '6', title: 'Formula 1: Drive to Survive', year: '2025', rating: '90% Match', quality: '4K UHD', app: 'Netflix', color: '#E50914' },
];

export const TvSimulator: React.FC<TvSimulatorProps> = ({
  isPoweredOn,
  activeSource,
  volume,
  isMuted,
  focusedTile,
  activeApp,
  onScreenText,
  isSearchActive,
  connectionStatus,
  lastCommand,
  apps,
  onAuthorizeAdb,
  onRejectAdb,
  onSelectTile,
  onCloseSearch,
}) => {
  const filteredCatalog = SAMPLE_SEARCH_CATALOG.filter((item) =>
    onScreenText
      ? item.title.toLowerCase().includes(onScreenText.toLowerCase()) ||
        item.app.toLowerCase().includes(onScreenText.toLowerCase()) ||
        onScreenText.toLowerCase().includes('movie') ||
        onScreenText.toLowerCase().includes('4k')
      : true
  );

  return (
    <div className="flex flex-col h-full bg-slate-950/80 rounded-2xl border border-slate-800 shadow-2xl overflow-hidden backdrop-blur-md">
      {/* TV Bezel Top Bar */}
      <div className="flex items-center justify-between px-4 py-2.5 bg-slate-900 border-b border-slate-800/80 text-xs">
        <div className="flex items-center gap-2">
          <Tv className="w-4 h-4 text-sky-400" />
          <span className="font-semibold text-slate-200">Fire TV Edition 4K UHD</span>
          <span className="text-[10px] px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">
            {activeSource}
          </span>
        </div>

        <div className="flex items-center gap-3">
          {/* Connection Status Pill on TV */}
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-mono bg-slate-800/80">
            <span
              className={`w-2 h-2 rounded-full ${
                connectionStatus === 'CONNECTED'
                  ? 'bg-emerald-400 animate-pulse'
                  : connectionStatus === 'CONNECTING'
                  ? 'bg-sky-400 animate-ping'
                  : connectionStatus === 'AUTH_REQUIRED'
                  ? 'bg-amber-400 animate-bounce'
                  : 'bg-rose-500'
              }`}
            />
            <span
              className={
                connectionStatus === 'CONNECTED'
                  ? 'text-emerald-300'
                  : connectionStatus === 'AUTH_REQUIRED'
                  ? 'text-amber-300'
                  : 'text-slate-400'
              }
            >
              ADB:5555 {connectionStatus}
            </span>
          </div>

          {/* Volume Indicator */}
          <div className="flex items-center gap-1 text-slate-300">
            {isMuted ? (
              <VolumeX className="w-3.5 h-3.5 text-rose-400" />
            ) : (
              <Volume2 className="w-3.5 h-3.5 text-sky-400" />
            )}
            <span className="font-mono text-[11px]">{isMuted ? 'MUTE' : `${volume}%`}</span>
          </div>
        </div>
      </div>

      {/* Main TV Display Screen */}
      <div className="relative flex-1 bg-black overflow-hidden flex flex-col justify-between min-h-[350px]">
        {!isPoweredOn ? (
          // TV Screen Off State
          <div className="flex-1 flex flex-col items-center justify-center bg-zinc-950 text-slate-600">
            <Tv className="w-16 h-16 stroke-1 text-slate-800 mb-2" />
            <p className="text-sm font-mono tracking-wider">TELEVISION STANDBY (0.5W)</p>
            <p className="text-xs text-slate-700 mt-1">Press IR Power or send ADB keyevent 26 to turn on</p>
          </div>
        ) : (
          // TV Screen On State
          <div className="relative flex-1 flex flex-col p-4 bg-gradient-to-br from-slate-900 via-indigo-950/40 to-slate-950">
            {/* Header: Fire TV Profile & Search Bar */}
            <div className="flex items-center justify-between gap-4 mb-4">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-amber-500 to-rose-500 flex items-center justify-center font-bold text-white text-xs shadow-md">
                  FTV
                </div>
                <div>
                  <div className="text-xs font-semibold text-slate-200">Living Room Fire TV</div>
                  <div className="text-[10px] text-slate-400">Fire OS 8.1.0 • Android 11</div>
                </div>
              </div>

              {/* TV Search / Input Bar displaying Injected Text */}
              <div
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border transition-all max-w-sm w-full ${
                  isSearchActive || onScreenText
                    ? 'bg-slate-850 border-sky-400 ring-2 ring-sky-500/30 shadow-lg shadow-sky-500/10'
                    : 'bg-slate-800/80 border-slate-700'
                }`}
              >
                <Search className={`w-3.5 h-3.5 shrink-0 ${isSearchActive || onScreenText ? 'text-sky-400' : 'text-slate-400'}`} />
                <div className="text-xs font-mono text-slate-200 truncate flex-1">
                  {onScreenText ? (
                    <span className="text-sky-300 font-semibold">{onScreenText}</span>
                  ) : (
                    <span className="text-slate-500">
                      {isSearchActive ? 'Search query active: type on remote...' : 'Search movies, apps, shows...'}
                    </span>
                  )}
                </div>
                {(isSearchActive || onScreenText) && (
                  <span className="w-1.5 h-3.5 bg-sky-400 animate-pulse inline-block" />
                )}
                {onScreenText && (
                  <button
                    onClick={onCloseSearch}
                    className="text-slate-400 hover:text-slate-200 p-0.5 rounded"
                  >
                    <X className="w-3 h-3" />
                  </button>
                )}
              </div>
            </div>

            {/* If Search is Active or Text has been Injected */}
            {(isSearchActive || onScreenText) && !activeApp ? (
              <div className="flex-1 flex flex-col justify-start overflow-hidden">
                <div className="flex items-center justify-between text-xs font-bold text-slate-300 mb-2">
                  <div className="flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                    <span>
                      {onScreenText ? `Search Results for "${onScreenText}"` : 'Fire TV Global Universal Search'}
                    </span>
                  </div>
                  <span className="text-[10px] text-slate-400 font-normal">
                    Press <kbd className="px-1 py-0.2 bg-slate-800 rounded">BACK</kbd> on remote to return
                  </span>
                </div>

                <div className="grid grid-cols-3 gap-2.5 overflow-y-auto pr-1">
                  {filteredCatalog.slice(0, 6).map((item) => (
                    <div
                      key={item.id}
                      className="group p-2.5 rounded-xl bg-slate-900/90 border border-slate-800 hover:border-sky-400 hover:bg-slate-850 transition-all flex flex-col justify-between"
                    >
                      <div>
                        <div className="flex items-center justify-between mb-1">
                          <span
                            className="text-[9px] font-bold px-1.5 py-0.2 rounded text-white"
                            style={{ backgroundColor: item.color }}
                          >
                            {item.app}
                          </span>
                          <span className="text-[10px] text-emerald-400 font-medium font-mono">{item.rating}</span>
                        </div>
                        <h4 className="text-xs font-bold text-slate-100 truncate group-hover:text-sky-300">
                          {item.title}
                        </h4>
                        <p className="text-[10px] text-slate-400 font-mono mt-0.5">{item.year} • {item.quality}</p>
                      </div>

                      <div className="mt-2 flex items-center justify-between text-[10px] text-sky-400 font-semibold pt-1 border-t border-slate-800">
                        <span className="flex items-center gap-1">
                          <Play className="w-2.5 h-2.5 fill-sky-400" /> Watch Now
                        </span>
                        <span className="text-slate-500">ADB intent</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : activeApp ? (
              // If an App is currently launched
              <div className="flex-1 rounded-xl bg-slate-900/90 border border-slate-700/80 p-5 flex flex-col items-center justify-center relative overflow-hidden shadow-inner">
                <div
                  className="w-16 h-16 rounded-2xl flex items-center justify-center text-white text-2xl font-black shadow-xl mb-3"
                  style={{ backgroundColor: activeApp.color }}
                >
                  {activeApp.name.slice(0, 1)}
                </div>
                <h3 className="text-lg font-bold text-slate-100">{activeApp.name}</h3>
                <p className="text-xs font-mono text-slate-400 mt-1">{activeApp.packageName}</p>
                <div className="mt-4 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs flex items-center gap-1.5">
                  <Check className="w-3.5 h-3.5" />
                  <span>Activity Active • Full Screen Intent</span>
                </div>
                <p className="text-[11px] text-slate-500 mt-3">
                  Press <kbd className="px-1.5 py-0.5 bg-slate-800 rounded text-slate-300">HOME</kbd> or <kbd className="px-1.5 py-0.5 bg-slate-800 rounded text-slate-300">BACK</kbd> on remote to exit
                </p>
              </div>
            ) : (
              // Fire TV Home Launcher Grid
              <div className="flex-1 flex flex-col justify-center">
                <div className="text-xs font-bold text-slate-300 tracking-wider uppercase mb-2 flex items-center justify-between">
                  <span>Recently Used & Quick Launch</span>
                  <span className="text-[10px] text-slate-500 font-normal">Use D-Pad to navigate, OK to launch</span>
                </div>

                <div className="grid grid-cols-4 gap-3">
                  {apps.map((app, index) => {
                    const isFocused = focusedTile === index;
                    return (
                      <div
                        key={app.id}
                        onClick={() => onSelectTile(index)}
                        className={`group cursor-pointer rounded-xl p-3 flex flex-col items-center justify-center transition-all duration-200 relative ${
                          isFocused
                            ? 'bg-slate-800/90 border-2 border-sky-400 shadow-lg shadow-sky-500/20 scale-105 z-10 ring-4 ring-sky-500/20'
                            : 'bg-slate-900/60 border border-slate-800 hover:border-slate-700 hover:bg-slate-850'
                        }`}
                      >
                        <div
                          className="w-10 h-10 rounded-xl flex items-center justify-center text-white font-bold text-sm shadow-md mb-1.5"
                          style={{ backgroundColor: app.color }}
                        >
                          {app.name.slice(0, 1)}
                        </div>
                        <span className="text-xs font-medium text-slate-200 text-center truncate w-full">
                          {app.name}
                        </span>
                        {isFocused && (
                          <span className="absolute -top-1 -right-1 flex h-3 w-3">
                            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-sky-400 opacity-75"></span>
                            <span className="relative inline-flex rounded-full h-3 w-3 bg-sky-500"></span>
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* ADB RSA Authorization Modal (when in AUTH_REQUIRED state) */}
            {connectionStatus === 'AUTH_REQUIRED' && (
              <div className="absolute inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-30">
                <div className="bg-slate-900 border border-amber-500/60 rounded-2xl max-w-sm w-full p-5 shadow-2xl text-slate-200">
                  <div className="flex items-center gap-3 text-amber-400 mb-2">
                    <ShieldAlert className="w-6 h-6 shrink-0" />
                    <h4 className="font-bold text-base text-white">Allow USB / Network debugging?</h4>
                  </div>
                  <p className="text-xs text-slate-300 leading-relaxed mb-3">
                    The host computer <span className="font-mono text-amber-300">HybridRemoteTV</span> is requesting ADB TCP connection.
                  </p>
                  <div className="bg-slate-950 p-2.5 rounded-lg font-mono text-[10px] text-slate-400 break-all border border-slate-800 mb-4">
                    RSA Key Fingerprint:
                    <br />
                    <span className="text-slate-300">4A:8C:31:7E:29:BF:99:12:00:DF:EA:5C:88:21:49:01</span>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={onRejectAdb}
                      className="flex-1 px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-300 transition-colors"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={onAuthorizeAdb}
                      className="flex-1 px-3 py-2 rounded-lg bg-amber-500 hover:bg-amber-400 text-xs font-bold text-slate-950 transition-colors flex items-center justify-center gap-1.5 shadow-lg shadow-amber-500/20"
                    >
                      <Check className="w-4 h-4" />
                      Always Allow
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Bottom TV Hardware Command Bar */}
        <div className="px-4 py-2 bg-slate-950 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-400">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-slate-700" />
            <span>Last Command:</span>
            <span className="font-mono text-sky-400 font-medium">
              {lastCommand || 'Idle / Listening for remote signals'}
            </span>
          </div>
          <div className="font-mono text-[10px] text-slate-500">
            TCP PORT 5555 • IR 38kHz NEC
          </div>
        </div>
      </div>
    </div>
  );
};
