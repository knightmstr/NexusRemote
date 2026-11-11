export type RemoteMode = 'INFRARED' | 'WI_FI';

export type ConnectionStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'AUTH_REQUIRED';

export interface InstalledApp {
  id: string;
  name: string;
  packageName: string;
  color: string;
  iconName: string;
}

export interface LogEntry {
  id: string;
  timestamp: string;
  tag: 'FireTvManager' | 'InfraredManager' | 'RemoteViewModel' | 'AdbProtocol';
  level: 'V' | 'D' | 'I' | 'W' | 'E';
  message: string;
}

export interface IrPulseEvent {
  frequencyHz: number;
  patternLength: number;
  commandName: string;
  timestamp: number;
}
