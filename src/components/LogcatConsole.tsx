import React, { useState, useEffect, useRef } from 'react';
import { LogEntry } from '../types';
import { Terminal, Trash2, Copy, Check, Filter } from 'lucide-react';

interface LogcatConsoleProps {
  logs: LogEntry[];
  onClearLogs: () => void;
}

export const LogcatConsole: React.FC<LogcatConsoleProps> = ({ logs, onClearLogs }) => {
  const [selectedTag, setSelectedTag] = useState<string>('ALL');
  const [copied, setCopied] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [logs]);

  const filteredLogs = logs.filter((log) => {
    if (selectedTag === 'ALL') return true;
    return log.tag === selectedTag;
  });

  const handleCopy = () => {
    const text = filteredLogs
      .map((l) => `${l.timestamp} ${l.level}/${l.tag}: ${l.message}`)
      .join('\n');
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getLevelColor = (level: LogEntry['level']) => {
    switch (level) {
      case 'E':
        return 'text-rose-400 font-bold';
      case 'W':
        return 'text-amber-400 font-medium';
      case 'I':
        return 'text-emerald-400 font-medium';
      case 'D':
        return 'text-sky-300';
      case 'V':
        return 'text-slate-400';
    }
  };

  const getTagBadgeColor = (tag: LogEntry['tag']) => {
    switch (tag) {
      case 'FireTvManager':
        return 'bg-blue-900/60 text-blue-300 border-blue-700/50';
      case 'InfraredManager':
        return 'bg-rose-900/60 text-rose-300 border-rose-700/50';
      case 'RemoteViewModel':
        return 'bg-purple-900/60 text-purple-300 border-purple-700/50';
      case 'AdbProtocol':
        return 'bg-emerald-900/60 text-emerald-300 border-emerald-700/50';
    }
  };

  return (
    <div className="flex flex-col h-full bg-slate-950/90 rounded-2xl border border-slate-800 shadow-xl overflow-hidden font-mono text-xs">
      {/* Logcat Top Bar */}
      <div className="flex items-center justify-between px-4 py-2.5 bg-slate-900 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <Terminal className="w-4 h-4 text-emerald-400" />
          <span className="font-semibold text-slate-200">Android Logcat • ADB TCP/IP & IR Driver</span>
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400">
            {filteredLogs.length} events
          </span>
        </div>

        {/* Tag Filters */}
        <div className="flex items-center gap-1.5">
          <div className="flex items-center bg-slate-950 p-0.5 rounded-lg border border-slate-800 text-[10px]">
            {['ALL', 'FireTvManager', 'InfraredManager', 'RemoteViewModel'].map((tag) => (
              <button
                key={tag}
                onClick={() => setSelectedTag(tag)}
                className={`px-2 py-0.5 rounded-md transition-colors ${
                  selectedTag === tag
                    ? 'bg-sky-600 text-white font-bold'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {tag === 'FireTvManager' ? 'ADB' : tag === 'InfraredManager' ? 'IR' : tag === 'RemoteViewModel' ? 'VM' : 'ALL'}
              </button>
            ))}
          </div>

          <button
            onClick={handleCopy}
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"
            title="Copy logs"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          </button>

          <button
            onClick={onClearLogs}
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-rose-400 transition-colors"
            title="Clear logs"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Logcat Output Window */}
      <div
        ref={scrollRef}
        className="flex-1 p-3 overflow-y-auto space-y-1.5 bg-slate-950 font-mono text-[11px] leading-relaxed select-text"
      >
        {filteredLogs.length === 0 ? (
          <div className="h-full flex items-center justify-center text-slate-600">
            No logs captured yet. Press remote buttons or initiate ADB connection.
          </div>
        ) : (
          filteredLogs.map((log) => (
            <div key={log.id} className="flex items-start gap-2 group hover:bg-slate-900/50 p-0.5 rounded">
              <span className="text-slate-500 shrink-0 text-[10px] select-none">{log.timestamp}</span>
              <span className={`px-1 rounded text-[9px] shrink-0 font-bold ${getLevelColor(log.level)}`}>
                {log.level}
              </span>
              <span className={`px-1.5 py-0.2 rounded border text-[9px] shrink-0 font-semibold ${getTagBadgeColor(log.tag)}`}>
                {log.tag}
              </span>
              <span className={`break-all ${getLevelColor(log.level)}`}>{log.message}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
