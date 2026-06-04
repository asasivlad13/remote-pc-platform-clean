export type ConnectionHistoryItem = {
    id?: number | string;

    sessionId?: string;
    sessionCode?: string;
    educationCode?: string;
    supportCode?: string;

    username?: string;
    pcName?: string;
    pcId?: number | string;

    clientIp?: string;
    clientInfo?: string;

    mode?: string;

    connectedAt?: string;
    disconnectedAt?: string | null;
    timestamp?: string;

    durationSeconds?: number | null;

    avgFps?: number | null;
    avgLatency?: number | null;

    filesSent?: number | null;
    filesReceived?: number | null;
    filesTotal?: number | null;

    issues?: string | null;
};