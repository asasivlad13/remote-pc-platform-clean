import { useCallback, useEffect, useRef, useState } from "react";

type RemoteSocketStatus = "connecting" | "connected" | "disconnected" | "error";

type SendCommandPayload = {
    pcId: number;
    action: string;
    profile?: string;
    educationCode?: string;
    supportCode?: string;
    pcName?: string;
    [key: string]: unknown;
};

type SendSettingsPayload = {
    pcId: number;
    resolution: string;
    profile?: string;
    educationCode?: string;
    supportCode?: string;
    pcName?: string;
};

type SendPointerPayload = {
    pcId: number;
    x: number;
    y: number;
    relativeX: number;
    relativeY: number;
    screenWidth?: number;
    screenHeight?: number;
    profile?: string;
    educationCode?: string;
    supportCode?: string;
    pcName?: string;
};

type SendMouseButtonPayload = SendPointerPayload & {
    button: number;
};

type SendWheelPayload = SendPointerPayload & {
    deltaX?: number;
    deltaY?: number;
    delta?: number;
};

type SendKeyboardPayload = {
    pcId: number;
    key?: string;
    code?: string;
    keyCode?: number;
    ctrlKey?: boolean;
    altKey?: boolean;
    shiftKey?: boolean;
    metaKey?: boolean;
    ctrl?: boolean;
    alt?: boolean;
    shift?: boolean;
    profile?: string;
    educationCode?: string;
    supportCode?: string;
    pcName?: string;
};

type SendMetricsPayload = {
    pcId: number;
    fps?: number | null;
    latency?: number | null;
    mode?: string;
    profile?: string;
    educationCode?: string;
    supportCode?: string;
    pcName?: string;
};

type RemoteVideoMetricsEvent = CustomEvent<{
    pcId?: number;
    pcName?: string;
    fps?: number | null;
    latency?: number | null;
}>;

function getAuthToken(): string {
    return localStorage.getItem("token") || "";
}

function getUsername(): string {
    return localStorage.getItem("username") || "unknown";
}

function getBackendWebSocketUrl(): string {
    const explicitUrl = import.meta.env.VITE_BACKEND_WS_URL as string | undefined;

    if (explicitUrl && explicitUrl.trim()) {
        const cleanUrl = explicitUrl.trim().replace(/\?token=.*$/i, "");
        return cleanUrl;
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const hostname = window.location.hostname;

    return `${protocol}//${hostname}:8080/ws/client`;
}

function inferEducationCode(): string | undefined {
    const params = new URLSearchParams(window.location.search);
    const fromQuery = params.get("educationCode") || params.get("code");

    if (fromQuery) {
        return fromQuery;
    }

    const studentMatch = window.location.pathname.match(/\/education\/student\/([^/]+)/);
    const teacherMatch = window.location.pathname.match(/\/education\/teacher\/([^/]+)/);

    return studentMatch?.[1] || teacherMatch?.[1] || undefined;
}

function inferProfile(explicitProfile?: string): string {
    if (explicitProfile && explicitProfile.trim()) {
        return explicitProfile.trim();
    }

    if (window.location.pathname.includes("/education/student")) {
        return "education_student";
    }

    if (window.location.pathname.includes("/education/teacher")) {
        return "education_teacher_view_student";
    }

    return "personal";
}

function normalizeCommandAction(action: string): string {
    if (action === "KEY_DOWN") {
        return "KEY_PRESS";
    }

    if (action === "KEY_UP") {
        return "KEY_RELEASE";
    }

    if (action === "MOUSE_DOWN" || action === "MOUSE_UP") {
        return "MOUSE_CLICK";
    }

    return action;
}

export function useRemoteSocket() {
    const socketRef = useRef<WebSocket | null>(null);
    const reconnectTimerRef = useRef<number | null>(null);
    const disposedRef = useRef(false);
    const watchedKeyRef = useRef<string>("");

    const [status, setStatus] = useState<RemoteSocketStatus>("connecting");
    const [lastMessage, setLastMessage] = useState<string | null>(null);

    useEffect(() => {
        disposedRef.current = false;

        function connect() {
            if (disposedRef.current) {
                return;
            }

            const wsUrl = getBackendWebSocketUrl();

            console.log("REMOTE_SOCKET_V5_NO_QUERY_TOKEN:", wsUrl);

            setStatus("connecting");

            const socket = new WebSocket(wsUrl);
            socketRef.current = socket;

            socket.onopen = () => {
                watchedKeyRef.current = "";
                setStatus("connected");
                console.log("REMOTE_SOCKET_CONNECTED");
            };

            socket.onmessage = (event) => {
                if (typeof event.data === "string") {
                    setLastMessage(event.data);

                    try {
                        const json = JSON.parse(event.data) as {
                            type?: string;
                            message?: string;
                            action?: string;
                        };

                        if (json.type === "command_denied") {
                            console.warn(
                                "Command denied:",
                                json.message || json.action || event.data,
                            );
                        }
                    } catch {
                        // обычное текстовое сообщение
                    }
                }
            };

            socket.onerror = () => {
                setStatus("error");
            };

            socket.onclose = () => {
                socketRef.current = null;
                watchedKeyRef.current = "";

                if (disposedRef.current) {
                    return;
                }

                setStatus("disconnected");

                if (reconnectTimerRef.current === null) {
                    reconnectTimerRef.current = window.setTimeout(() => {
                        reconnectTimerRef.current = null;
                        connect();
                    }, 1500);
                }
            };
        }

        connect();

        return () => {
            disposedRef.current = true;

            if (reconnectTimerRef.current !== null) {
                window.clearTimeout(reconnectTimerRef.current);
                reconnectTimerRef.current = null;
            }

            try {
                if (socketRef.current?.readyState === WebSocket.OPEN) {
                    socketRef.current.send(JSON.stringify({ type: "stop" }));
                }
            } catch {
                // ignore
            }

            socketRef.current?.close();
            socketRef.current = null;
        };
    }, []);

    const sendJson = useCallback((payload: unknown) => {
        const socket = socketRef.current;

        if (!socket || socket.readyState !== WebSocket.OPEN) {
            throw new Error("WebSocket is not connected");
        }

        socket.send(JSON.stringify(payload));
    }, []);

    const ensureWatch = useCallback(
        ({
             pcId,
             pcName,
             profile,
             educationCode,
             supportCode,
             mode,
         }: {
            pcId: number;
            pcName?: string;
            profile?: string;
            educationCode?: string;
            supportCode?: string;
            mode?: string;
        }) => {
            const socket = socketRef.current;

            if (!socket || socket.readyState !== WebSocket.OPEN) {
                throw new Error("WebSocket is not connected");
            }

            const normalizedProfile = inferProfile(profile);
            const resolvedEducationCode = educationCode || inferEducationCode();
            const watchKey = [
                pcId,
                normalizedProfile,
                resolvedEducationCode || "",
                supportCode || "",
            ].join("|");

            if (watchedKeyRef.current === watchKey) {
                return;
            }

            socket.send(
                JSON.stringify({
                    type: "watch",
                    pcId,
                    token: getAuthToken(),
                    pcName: pcName || "Remote PC",
                    profile: normalizedProfile,
                    platform: navigator.platform,
                    browser: navigator.userAgent,
                    mode: mode || "React control",
                    educationCode: resolvedEducationCode,
                    supportCode,
                    username: getUsername(),
                }),
            );

            watchedKeyRef.current = watchKey;
        },
        [],
    );

    const sendCommand = useCallback(
        (payload: SendCommandPayload) => {
            const profile = inferProfile(payload.profile);
            const educationCode = payload.educationCode || inferEducationCode();
            const action = normalizeCommandAction(payload.action);

            ensureWatch({
                pcId: payload.pcId,
                pcName: payload.pcName,
                profile,
                educationCode,
                supportCode: payload.supportCode,
                mode: profile === "education_student" ? "Учебное управление" : "Control",
            });

            sendJson({
                type: "command",
                ...payload,
                action,
                profile,
                educationCode,
                supportCode: payload.supportCode,
            });
        },
        [ensureWatch, sendJson],
    );

    const sendSettings = useCallback(
        (payload: SendSettingsPayload) => {
            const profile = inferProfile(payload.profile);
            const educationCode = payload.educationCode || inferEducationCode();

            ensureWatch({
                pcId: payload.pcId,
                pcName: payload.pcName,
                profile,
                educationCode,
                supportCode: payload.supportCode,
                mode: "Settings",
            });

            sendJson({
                type: "settings",
                ...payload,
                profile,
                educationCode,
                supportCode: payload.supportCode,
            });
        },
        [ensureWatch, sendJson],
    );

    const sendMouseMove = useCallback(
        (payload: SendPointerPayload) => {
            sendCommand({
                pcId: payload.pcId,
                action: "MOUSE_MOVE",
                x: payload.x,
                y: payload.y,
                relativeX: payload.relativeX,
                relativeY: payload.relativeY,
                screenWidth: payload.screenWidth,
                screenHeight: payload.screenHeight,
                profile: payload.profile,
                educationCode: payload.educationCode,
                supportCode: payload.supportCode,
                pcName: payload.pcName,
            });
        },
        [sendCommand],
    );

    const sendMouseDown = useCallback(
        (payload: SendMouseButtonPayload) => {
            sendCommand({
                pcId: payload.pcId,
                action: "MOUSE_CLICK",
                button: payload.button,
                x: payload.x,
                y: payload.y,
                relativeX: payload.relativeX,
                relativeY: payload.relativeY,
                screenWidth: payload.screenWidth,
                screenHeight: payload.screenHeight,
                profile: payload.profile,
                educationCode: payload.educationCode,
                supportCode: payload.supportCode,
                pcName: payload.pcName,
            });
        },
        [sendCommand],
    );

    const sendMouseUp = useCallback(
        (_payload: SendMouseButtonPayload) => {
            // Старый агент работает через MOUSE_CLICK, отдельный MOUSE_UP не нужен.
        },
        [],
    );

    const sendMouseWheel = useCallback(
        (payload: SendWheelPayload) => {
            const delta =
                typeof payload.delta === "number"
                    ? payload.delta
                    : (payload.deltaY || 0) > 0
                        ? 1
                        : -1;

            sendCommand({
                pcId: payload.pcId,
                action: "MOUSE_WHEEL",
                delta,
                x: payload.x,
                y: payload.y,
                relativeX: payload.relativeX,
                relativeY: payload.relativeY,
                screenWidth: payload.screenWidth,
                screenHeight: payload.screenHeight,
                profile: payload.profile,
                educationCode: payload.educationCode,
                supportCode: payload.supportCode,
                pcName: payload.pcName,
            });
        },
        [sendCommand],
    );

    const sendKeyDown = useCallback(
        (payload: SendKeyboardPayload) => {
            sendCommand({
                pcId: payload.pcId,
                action: "KEY_PRESS",
                keyCode:
                    payload.keyCode ||
                    keyToKeyCode(payload.key || "", payload.code || ""),
                key: payload.key,
                code: payload.code,
                ctrl: payload.ctrl ?? payload.ctrlKey ?? false,
                alt: payload.alt ?? payload.altKey ?? false,
                shift: payload.shift ?? payload.shiftKey ?? false,
                metaKey: payload.metaKey ?? false,
                profile: payload.profile,
                educationCode: payload.educationCode,
                supportCode: payload.supportCode,
                pcName: payload.pcName,
            });
        },
        [sendCommand],
    );

    const sendKeyUp = useCallback(
        (payload: SendKeyboardPayload) => {
            sendCommand({
                pcId: payload.pcId,
                action: "KEY_RELEASE",
                keyCode:
                    payload.keyCode ||
                    keyToKeyCode(payload.key || "", payload.code || ""),
                key: payload.key,
                code: payload.code,
                ctrl: payload.ctrl ?? payload.ctrlKey ?? false,
                alt: payload.alt ?? payload.altKey ?? false,
                shift: payload.shift ?? payload.shiftKey ?? false,
                metaKey: payload.metaKey ?? false,
                profile: payload.profile,
                educationCode: payload.educationCode,
                supportCode: payload.supportCode,
                pcName: payload.pcName,
            });
        },
        [sendCommand],
    );


    const sendMetrics = useCallback(
        (payload: SendMetricsPayload) => {
            const fps = typeof payload.fps === "number" && Number.isFinite(payload.fps)
                ? payload.fps
                : null;
            const latency = typeof payload.latency === "number" && Number.isFinite(payload.latency)
                ? payload.latency
                : null;

            if (fps === null && latency === null) {
                return;
            }

            const profile = inferProfile(payload.profile);
            const educationCode = payload.educationCode || inferEducationCode();

            ensureWatch({
                pcId: payload.pcId,
                pcName: payload.pcName,
                profile,
                educationCode,
                supportCode: payload.supportCode,
                mode: payload.mode || "Control",
            });

            sendJson({
                type: "metrics",
                pcId: payload.pcId,
                fps: fps ?? 0,
                latency: latency ?? 0,
                mode: payload.mode || "Control",
                profile,
                educationCode,
                supportCode: payload.supportCode,
            });
        },
        [ensureWatch, sendJson],
    );

    useEffect(() => {
        function handleRemoteVideoMetrics(event: Event) {
            const customEvent = event as RemoteVideoMetricsEvent;
            const detail = customEvent.detail;

            if (!detail || !detail.pcId) {
                return;
            }

            try {
                sendMetrics({
                    pcId: detail.pcId,
                    pcName: detail.pcName,
                    fps: detail.fps,
                    latency: detail.latency,
                    mode: "Control",
                });
            } catch {
                // Метрики не критичны: если WS ещё не готов, просто пропускаем измерение.
            }
        }

        window.addEventListener("remote-video-metrics", handleRemoteVideoMetrics);

        return () => {
            window.removeEventListener("remote-video-metrics", handleRemoteVideoMetrics);
        };
    }, [sendMetrics]);
    return {
        status,
        lastMessage,
        sendCommand,
        sendSettings,
        sendMouseMove,
        sendMouseDown,
        sendMouseUp,
        sendMouseWheel,
        sendKeyDown,
        sendKeyUp,
        sendMetrics,
    };
}

function keyToKeyCode(key: string, code: string): number {
    if (key.length === 1) {
        return key.toUpperCase().charCodeAt(0);
    }

    const map: Record<string, number> = {
        Enter: 13,
        Escape: 27,
        Esc: 27,
        Backspace: 8,
        Tab: 9,
        Space: 32,
        " ": 32,
        Delete: 46,
        Insert: 45,
        Home: 36,
        End: 35,
        PageUp: 33,
        PageDown: 34,
        ArrowLeft: 37,
        ArrowUp: 38,
        ArrowRight: 39,
        ArrowDown: 40,
        Shift: 16,
        Control: 17,
        Alt: 18,
        Meta: 91,
    };

    if (map[key]) {
        return map[key];
    }

    const functionKey = key.match(/^F(\d{1,2})$/);

    if (functionKey) {
        const n = Number(functionKey[1]);

        if (n >= 1 && n <= 12) {
            return 111 + n;
        }
    }

    const digitCode = code.match(/^Digit(\d)$/);

    if (digitCode) {
        return digitCode[1].charCodeAt(0);
    }

    const keyCode = code.match(/^Key([A-Z])$/);

    if (keyCode) {
        return keyCode[1].charCodeAt(0);
    }

    return 0;
}