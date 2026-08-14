export type ChargerStatusCode = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 9;

export type ChargerStatusType =
  | 'UNKNOWN'
  | 'COMM_ERROR'
  | 'AVAILABLE'
  | 'CHARGING'
  | 'SUSPENDED'
  | 'MAINTENANCE'
  | 'RESERVED'
  | 'UNCONFIRMED';

export interface RawKecoChargerItem {
  statNm: string;
  statId: string;
  chgerId: string;
  chgerType: string;
  addr: string;
  location?: string;
  lat: string;
  lng: string;
  useTime?: string;
  busiId: string;
  bnm: string;
  bcall?: string;
  stat: string;
  statUpdDt?: string;
  lastTsdt?: string;
  lastTedt?: string;
  nowTsdt?: string;
  output?: string;
  method?: string;
  zcode?: string;
  zscode?: string;
  kind?: string;
  kindDetail?: string;
  parkingFree?: string;
  note?: string;
  delYn?: string;
  delDetail?: string;
  limitYn?: string;
  limitDetail?: string;
}

export interface RawKecoApiResponse {
  resultCode: string;
  resultMsg: string;
  totalCount: number;
  pageNo: number;
  numOfRows: number;
  items?: {
    item: RawKecoChargerItem[];
  };
}

export interface Charger {
  statId: string;
  chgerId: string;
  typeCode: string;
  typeName: string;
  outputKw?: string;
  method?: string;
  status: ChargerStatusType;
  statusCode: number;
  statusUpdatedAt?: string; // ISO string
  lastChargeStartedAt?: string;
  lastChargeEndedAt?: string;
  isDeleted: boolean;
  location?: string;        // e.g. '105동 지하 1층 주차장 출입구 옆'
  chargerCode?: string;     // e.g. '11050-8' or physical terminal hardware number
  // KEPCO enriched fields
  chargeTp?: string;    // 1:완속, 2:급속
  cpStat?: string;      // 1:충전가능 2:충전중 3:고장/점검 4:통신장애 5:통신미연결 6:충전종료 7:계획정지
  cpTp?: string;        // 1:B타입(5핀) 2:C타입(5핀) 3:BC타입(5핀) 4:BC타입(7핀) 5:C차데모 6:AC3상 7:DC콤보 8:DC차데모+DC콤보
}

export interface ChargerStation {
  statId: string;
  name: string;
  address: string;
  addressDetail?: string;
  lat: number;
  lng: number;
  useTime?: string;
  operatorName: string;
  operatorCall?: string;
  parkingFree?: boolean;
  note?: string;
  zcode?: string;
  zscode?: string;
  updatedAt: string;
  chargers: Charger[];
  // KEPCO enriched fields
  carType?: string;        // 지원차종 (콤마 구분)
  rapidCnt?: number;       // 급속충전기 대수
  slowCnt?: number;        // 완속충전기 대수
  summary: {
    total: number;
    available: number;
    charging: number;
    maintenance: number;
    unknown: number;
  };
}

export interface ChargerBatchStatusRequest {
  keys: Array<{ statId: string; chgerId: string }>;
}

export interface ChargerBatchStatusResponse {
  results: Record<
    string,
    {
      statId: string;
      chgerId: string;
      status: ChargerStatusType;
      statusCode: number;
      statusUpdatedAt?: string;
      fetchedAt: string;
    }
  >;
}
