export type RemoteMode = 'INFRARED' | 'WI_FI';

export type RemoteSkin = 'classic-ir' | 'modern-streaming' | 'minimalist';

export type ConnectionStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'AUTH_REQUIRED';

export type IrProtocol =
  | 'NEC'
  | 'NEC_EXT'
  | 'SAMSUNG'
  | 'SONY_SIRC'
  | 'RC5'
  | 'RC6'
  | 'PANASONIC'
  | 'RCA';

export interface TvBrandProfile {
  id: string;
  name: string;
  modelSeries: string;
  protocol: IrProtocol;
  carrierFrequencyHz: number;
  address: number;
  subAddress?: number;
  powerCmd: number;
  sourceCmd: number;
  volUpCmd: number;
  volDownCmd: number;
  muteCmd: number;
  digitCodes: Record<number, number>;
  description: string;
  source: string;
  color: string;
}

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
  brandName?: string;
  protocol?: string;
}
