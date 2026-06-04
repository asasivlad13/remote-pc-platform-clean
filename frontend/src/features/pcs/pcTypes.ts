export type PcStatus = "ONLINE" | "OFFLINE" | "SLEEP" | "SOFT_SLEEP" | string;

export type PcResponseDto = {
    id: number;
    name: string;
    macAddress: string;
    status: PcStatus | null;
    lastConnection: string | null;
};

export type PcDetailsResponse = {
    id: number;
    name: string;
    macAddress: string;
    status: PcStatus | null;
    lastConnection: string | null;
    screenWidth: number | null;
    screenHeight: number | null;
    webrtcUrl: string | null;
    streamName: string | null;
};