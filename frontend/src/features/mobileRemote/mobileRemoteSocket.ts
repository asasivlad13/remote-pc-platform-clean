export type MobileRemoteSocketStatus =
    | "connecting"
    | "connected"
    | "closed"
    | "error";

export type MobileRemoteSocketOptions = {
    pcId: number;
    pcName: string;
    mode: string;
    onStatusChange?: (status: MobileRemoteSocketStatus) => void;
    onMessage?: (message: unknown) => void;
};

function getBackendWebSocketUrl(): string {
    const explicitUrl = import.meta.env.VITE_BACKEND_WS_URL as string | undefined;

    if (explicitUrl && explicitUrl.trim()) {
        return explicitUrl.trim();
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const hostname = window.location.hostname;

    return `${protocol}//${hostname}:8080/ws/client`;
}

export function createMobileRemoteSocket(options: MobileRemoteSocketOptions) {
    const token = localStorage.getItem("token") || "";
    const socket = new WebSocket(getBackendWebSocketUrl());

    socket.onopen = () => {
        options.onStatusChange?.("connected");

        socket.send(
            JSON.stringify({
                type: "watch",
                pcId: options.pcId,
                token,
                pcName: options.pcName,
                profile: "personal",
                platform: navigator.platform,
                browser: navigator.userAgent,
                mode: options.mode,
            }),
        );
    };

    socket.onclose = () => {
        options.onStatusChange?.("closed");
    };

    socket.onerror = () => {
        options.onStatusChange?.("error");
    };

    socket.onmessage = (event) => {
        try {
            options.onMessage?.(JSON.parse(event.data));
        } catch {
            options.onMessage?.(event.data);
        }
    };

    options.onStatusChange?.("connecting");

    function sendCommand(action: string, data: Record<string, unknown> = {}) {
        if (socket.readyState !== WebSocket.OPEN) {
            return false;
        }

        socket.send(
            JSON.stringify({
                type: "command",
                pcId: options.pcId,
                action,
                profile: "personal",
                ...data,
            }),
        );

        return true;
    }

    function close() {
        try {
            if (socket.readyState === WebSocket.OPEN) {
                socket.send(JSON.stringify({ type: "stop" }));
            }
        } catch {
            // ignore
        }

        socket.close();
    }

    return {
        socket,
        sendCommand,
        close,
    };
}
