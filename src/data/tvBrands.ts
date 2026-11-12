import { TvBrandProfile } from '../types';

export const UNIVERSAL_TV_BRANDS: TvBrandProfile[] = [
  {
    id: 'samsung',
    name: 'Samsung',
    modelSeries: 'Neo QLED / OLED / Crystal 4K',
    protocol: 'SAMSUNG',
    carrierFrequencyHz: 38000,
    address: 0x07,
    powerCmd: 0x02,
    sourceCmd: 0x01,
    volUpCmd: 0x07,
    volDownCmd: 0x0B,
    muteCmd: 0x0F,
    digitCodes: {
      0: 0x11, 1: 0x04, 2: 0x05, 3: 0x06,
      4: 0x08, 5: 0x09, 6: 0x0A, 7: 0x0C, 8: 0x0D, 9: 0x0E
    },
    description: 'Samsung 32-bit protocol with 4.5ms pulse leader mark',
    color: '#034EA2'
  },
  {
    id: 'lg',
    name: 'LG Electronics',
    modelSeries: 'webOS OLED / QNED / NanoCell',
    protocol: 'NEC',
    carrierFrequencyHz: 38000,
    address: 0x04,
    powerCmd: 0x08,
    sourceCmd: 0x0B,
    volUpCmd: 0x02,
    volDownCmd: 0x03,
    muteCmd: 0x09,
    digitCodes: {
      0: 0x10, 1: 0x11, 2: 0x12, 3: 0x13,
      4: 0x14, 5: 0x15, 6: 0x16, 7: 0x17, 8: 0x18, 9: 0x19
    },
    description: 'LG standard 38kHz NEC 32-bit (Address 0x04)',
    color: '#A50034'
  },
  {
    id: 'sony',
    name: 'Sony Bravia',
    modelSeries: 'XR OLED / Mini-LED / Google TV',
    protocol: 'SONY_SIRC',
    carrierFrequencyHz: 40000,
    address: 0x01,
    powerCmd: 0x15,
    sourceCmd: 0x25,
    volUpCmd: 0x12,
    volDownCmd: 0x13,
    muteCmd: 0x14,
    digitCodes: {
      0: 0x09, 1: 0x00, 2: 0x01, 3: 0x02,
      4: 0x03, 5: 0x04, 6: 0x05, 7: 0x06, 8: 0x07, 9: 0x08
    },
    description: 'Sony SIRC 40kHz 12-bit pulse width modulation',
    color: '#000000'
  },
  {
    id: 'tcl',
    name: 'TCL',
    modelSeries: 'Roku TV / QLED Google TV Series',
    protocol: 'NEC',
    carrierFrequencyHz: 38000,
    address: 0x40,
    powerCmd: 0x12,
    sourceCmd: 0x30,
    volUpCmd: 0x1A,
    volDownCmd: 0x1E,
    muteCmd: 0x16,
    digitCodes: {
      0: 0x00, 1: 0x01, 2: 0x02, 3: 0x03,
      4: 0x04, 5: 0x05, 6: 0x06, 7: 0x07, 8: 0x08, 9: 0x09
    },
    description: 'TCL universal NEC encoding (Address 0x40)',
    color: '#ED1C24'
  },
  {
    id: 'hisense',
    name: 'Hisense',
    modelSeries: 'ULED / VIDAA / Android TV',
    protocol: 'NEC',
    carrierFrequencyHz: 38000,
    address: 0xF7,
    powerCmd: 0x08,
    sourceCmd: 0x0F,
    volUpCmd: 0x0B,
    volDownCmd: 0x0E,
    muteCmd: 0x0A,
    digitCodes: {
      0: 0x10, 1: 0x11, 2: 0x12, 3: 0x13,
      4: 0x14, 5: 0x15, 6: 0x16, 7: 0x17, 8: 0x18, 9: 0x19
    },
    description: 'Hisense standard NEC 38kHz protocol (Address 0xF7)',
    color: '#009587'
  },
  {
    id: 'vizio',
    name: 'Vizio',
    modelSeries: 'SmartCast / M-Series Quantum',
    protocol: 'NEC',
    carrierFrequencyHz: 38000,
    address: 0x20,
    powerCmd: 0x12,
    sourceCmd: 0x0A,
    volUpCmd: 0x02,
    volDownCmd: 0x03,
    muteCmd: 0x09,
    digitCodes: {
      0: 0x00, 1: 0x01, 2: 0x02, 3: 0x03,
      4: 0x04, 5: 0x05, 6: 0x06, 7: 0x07, 8: 0x08, 9: 0x09
    },
    description: 'Vizio SmartCast universal NEC (Address 0x20)',
    color: '#F36F21'
  },
  {
    id: 'panasonic',
    name: 'Panasonic',
    modelSeries: 'Viera OLED / 4K Pro Studio',
    protocol: 'NEC',
    carrierFrequencyHz: 38000,
    address: 0x48,
    powerCmd: 0x3D,
    sourceCmd: 0x22,
    volUpCmd: 0x20,
    volDownCmd: 0x21,
    muteCmd: 0x32,
    digitCodes: {
      0: 0x10, 1: 0x11, 2: 0x12, 3: 0x13,
      4: 0x14, 5: 0x15, 6: 0x16, 7: 0x17, 8: 0x18, 9: 0x19
    },
    description: 'Panasonic Viera standard encoding (Address 0x48)',
    color: '#004098'
  },
  {
    id: 'philips',
    name: 'Philips',
    modelSeries: 'Ambilight OLED / Android TV',
    protocol: 'RC5',
    carrierFrequencyHz: 36000,
    address: 0x00,
    powerCmd: 0x0C,
    sourceCmd: 0x38,
    volUpCmd: 0x10,
    volDownCmd: 0x11,
    muteCmd: 0x0D,
    digitCodes: {
      0: 0x00, 1: 0x01, 2: 0x02, 3: 0x03,
      4: 0x04, 5: 0x05, 6: 0x06, 7: 0x07, 8: 0x08, 9: 0x09
    },
    description: 'Philips RC-5 bi-phase Manchester 36kHz carrier',
    color: '#0B5EBA'
  },
  {
    id: 'sharp',
    name: 'Sharp',
    modelSeries: 'Aquos 4K UHD / Android TV',
    protocol: 'NEC',
    carrierFrequencyHz: 38000,
    address: 0x55,
    powerCmd: 0x14,
    sourceCmd: 0x1E,
    volUpCmd: 0x04,
    volDownCmd: 0x05,
    muteCmd: 0x0D,
    digitCodes: {
      0: 0x00, 1: 0x01, 2: 0x02, 3: 0x03,
      4: 0x04, 5: 0x05, 6: 0x06, 7: 0x07, 8: 0x08, 9: 0x09
    },
    description: 'Sharp Aquos NEC standard (Address 0x55)',
    color: '#E60012'
  },
  {
    id: 'toshiba',
    name: 'Toshiba',
    modelSeries: 'Fire TV Edition / Smart TV',
    protocol: 'NEC',
    carrierFrequencyHz: 38000,
    address: 0x02,
    powerCmd: 0x08,
    sourceCmd: 0x0B,
    volUpCmd: 0x1A,
    volDownCmd: 0x1E,
    muteCmd: 0x12,
    digitCodes: {
      0: 0x10, 1: 0x11, 2: 0x12, 3: 0x13,
      4: 0x14, 5: 0x15, 6: 0x16, 7: 0x17, 8: 0x18, 9: 0x19
    },
    description: 'Toshiba TV universal encoding (Address 0x02)',
    color: '#CC0000'
  }
];
