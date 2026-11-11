import React, { useState } from 'react';
import { KOTLIN_FILES, KotlinFileItem } from '../data/kotlinCode';
import { Code2, Copy, Check, Download, FileCode, Layers, ShieldCheck, Cpu } from 'lucide-react';

export const CodeViewer: React.FC = () => {
  const [selectedFileId, setSelectedFileId] = useState<string>(KOTLIN_FILES[0].id);
  const [copied, setCopied] = useState(false);

  const currentFile = KOTLIN_FILES.find((f) => f.id === selectedFileId) || KOTLIN_FILES[0];

  const handleCopy = () => {
    navigator.clipboard.writeText(currentFile.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const element = document.createElement('a');
    const file = new Blob([currentFile.code], { type: 'text/plain' });
    element.href = URL.createObjectURL(file);
    element.download = currentFile.name;
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
  };

  return (
    <div className="flex flex-col h-full bg-slate-950/90 rounded-2xl border border-slate-800 shadow-2xl overflow-hidden">
      {/* Header and File Navigation Tabs */}
      <div className="bg-slate-900 border-b border-slate-800 p-3">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Code2 className="w-5 h-5 text-sky-400" />
            <h2 className="font-bold text-sm text-slate-100">
              Kotlin Native Architecture Studio
            </h2>
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-sky-950 border border-sky-800 text-sky-300 font-mono">
              Production Android Ready
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleDownload}
              className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-1.5 transition-colors border border-slate-700"
            >
              <Download className="w-3.5 h-3.5 text-slate-400" />
              <span>Download</span>
            </button>

            <button
              onClick={handleCopy}
              className="px-3 py-1.5 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold flex items-center gap-1.5 shadow-md shadow-sky-950/60 transition-all active:scale-95"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-300" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied File!' : 'Copy File'}</span>
            </button>
          </div>
        </div>

        {/* File Tabs */}
        <div className="flex flex-wrap gap-1.5">
          {KOTLIN_FILES.map((file) => {
            const isSelected = file.id === selectedFileId;
            return (
              <button
                key={file.id}
                onClick={() => setSelectedFileId(file.id)}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-mono transition-all border ${
                  isSelected
                    ? 'bg-slate-800 text-sky-300 border-sky-500/50 shadow-sm'
                    : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:bg-slate-900 hover:text-slate-200'
                }`}
              >
                <FileCode className="w-3.5 h-3.5" />
                <span>{file.name}</span>
                <span className="text-[9px] px-1.5 py-0.2 rounded bg-slate-900 text-slate-500">
                  {file.layer.split(' ')[0]}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* File Metadata & Architectural Summary Bar */}
      <div className="px-4 py-2.5 bg-slate-920 border-b border-slate-850 flex items-center justify-between text-xs">
        <div className="flex items-center gap-2 text-slate-300 font-mono text-[11px]">
          <span className="text-slate-500 font-sans">Target Path:</span>
          <span className="text-sky-300 bg-slate-900 px-2 py-0.5 rounded border border-slate-800">
            {currentFile.path}
          </span>
        </div>
        <p className="text-slate-400 text-xs max-w-xl truncate ml-4 hidden md:block">
          {currentFile.description}
        </p>
      </div>

      {/* Code Display Area */}
      <div className="flex-1 overflow-y-auto p-4 bg-slate-950 font-mono text-xs leading-relaxed text-slate-300 select-text">
        <pre className="whitespace-pre overflow-x-auto selection:bg-sky-900 selection:text-sky-100">
          <code>{currentFile.code}</code>
        </pre>
      </div>

      {/* Engineering Blueprint Footnote */}
      <div className="px-4 py-2 bg-slate-900 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-400">
        <div className="flex items-center gap-4">
          <span className="flex items-center gap-1 text-emerald-400 font-medium">
            <ShieldCheck className="w-3.5 h-3.5" /> Thread-Safe Mutex & Coroutine Scopes
          </span>
          <span className="flex items-center gap-1 text-sky-400 font-medium hidden sm:flex">
            <Cpu className="w-3.5 h-3.5" /> Low-Level ADB TCP 5555 Framing
          </span>
          <span className="flex items-center gap-1 text-amber-400 font-medium hidden md:flex">
            <Layers className="w-3.5 h-3.5" /> Preferences DataStore Reactive Flow
          </span>
        </div>
        <span className="font-mono text-[10px] text-slate-500">
          Kotlin 2.0 • Compose BOM 2024.10.01
        </span>
      </div>
    </div>
  );
};
