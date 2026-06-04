import { useEffect, useRef, useState } from "react";
import type { MouseEvent, WheelEvent } from "react";
import { Maximize2, Minimize2, Monitor, MousePointer2, Wifi } from "lucide-react";
import type { PcDetailsResponse } from "../../pcs/pcTypes";
import { useGStreamerWebRtc } from "../../remote/useGStreamerWebRtc";
import { useRemoteSocket } from "../../remote/useRemoteSocket";

type EducationStreamPanelProps = {
    pc: PcDetailsResponse | null;
    title: string;
    subtitle: string;

    showQualityControls?: boolean;
    qualityValue?: string;
    onQualityChange?: (value: string) => void;

    controlEnabled?: boolean;
    controlPcId?: number;
    controlLabel?: string;

    controlProfile?: string;
    controlEducationCode?: string;
    controlSupportCode?: string;
};

type RemotePoint = {
    x: number;
    y: number;
    screenWidth: number;
    screenHeight: number;
    relativeX: number;
    relativeY: number;
};

const QUALITY_OPTIONS = [
    {
        value: "854x480",
        label: "480p",
    },
    {
        value: "1280x720",
        label: "720p",
    },
    {
        value: "1920x1080",
        label: "1080p",
    },
];

export function EducationStreamPanel({
                                         pc,
                                         title,
                                         subtitle,
                                         showQualityControls = false,
                                         qualityValue = "1280x720",
                                         onQualityChange,
                                         controlEnabled = false,
                                         controlPcId,
                                         controlLabel = "Управление активно",
                                         controlProfile,
                                         controlEducationCode,
                                         controlSupportCode,
                                     }: EducationStreamPanelProps) {
    const [isFullscreen, setIsFullscreen] = useState(false);
    const [controlMessage, setControlMessage] = useState("");

    const stablePc = useStableStreamPc(pc);
    const streamTargetRef = useRef<HTMLDivElement | null>(null);
    const lastMouseMoveAtRef = useRef(0);
    const lastClickAtRef = useRef(0);
    const lastWheelAtRef = useRef(0);

    const {
        status: socketStatus,
        sendMouseMove,
        sendMouseDown,
        sendMouseWheel,
        sendKeyDown,
        sendKeyUp,
    } = useRemoteSocket();

    const {
        videoRef,
        videoStatus,
        videoError,
        fps,
    } = useGStreamerWebRtc({
        pc: stablePc,
        pcId: stablePc?.id || 0,
        pcName: stablePc?.name || title,
    });

    const targetPcId = controlPcId || stablePc?.id || 0;
    const hasStream = Boolean(stablePc?.webrtcUrl && stablePc?.streamName);
    const canSendControl = controlEnabled && targetPcId > 0;

    const resolvedControlProfile = controlProfile || getEducationControlProfile();
    const resolvedEducationCode = controlEducationCode || getEducationCodeFromUrl();
    const resolvedSupportCode = controlSupportCode;
    const resolvedPcName = stablePc?.name || title;

    useEffect(() => {
        function handleFullscreenChange() {
            setIsFullscreen(document.fullscreenElement?.id === "education-stream-target");
        }

        document.addEventListener("fullscreenchange", handleFullscreenChange);
        handleFullscreenChange();

        return () => {
            document.removeEventListener("fullscreenchange", handleFullscreenChange);
        };
    }, []);

    useEffect(() => {
        function handleDocumentKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape" && document.fullscreenElement) {
                void document.exitFullscreen();
                return;
            }

            if (!canSendControl) {
                return;
            }

            if (isUiTarget(event.target)) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();

            try {
                sendKeyDown({
                    pcId: targetPcId,
                    key: event.key,
                    code: event.code,
                    keyCode: event.keyCode,
                    ctrlKey: event.ctrlKey,
                    altKey: event.altKey,
                    shiftKey: event.shiftKey,
                    metaKey: event.metaKey,
                    profile: resolvedControlProfile,
                    educationCode: resolvedEducationCode,
                    supportCode: resolvedSupportCode,
                    pcName: resolvedPcName,
                });
            } catch {
                showControlMessage("WebSocket управления не подключён");
            }
        }

        function handleDocumentKeyUp(event: KeyboardEvent) {
            if (!canSendControl) {
                return;
            }

            if (isUiTarget(event.target)) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();

            try {
                sendKeyUp({
                    pcId: targetPcId,
                    key: event.key,
                    code: event.code,
                    keyCode: event.keyCode,
                    ctrlKey: event.ctrlKey,
                    altKey: event.altKey,
                    shiftKey: event.shiftKey,
                    metaKey: event.metaKey,
                    profile: resolvedControlProfile,
                    educationCode: resolvedEducationCode,
                    supportCode: resolvedSupportCode,
                    pcName: resolvedPcName,
                });
            } catch {
                showControlMessage("WebSocket управления не подключён");
            }
        }

        document.addEventListener("keydown", handleDocumentKeyDown);
        document.addEventListener("keyup", handleDocumentKeyUp);

        return () => {
            document.removeEventListener("keydown", handleDocumentKeyDown);
            document.removeEventListener("keyup", handleDocumentKeyUp);
        };
    }, [
        canSendControl,
        sendKeyDown,
        sendKeyUp,
        stablePc?.name,
        targetPcId,
        title,
        resolvedControlProfile,
        resolvedEducationCode,
        resolvedSupportCode,
        resolvedPcName,
    ]);

    function handleFullscreen() {
        const element = document.getElementById("education-stream-target");

        if (!element) {
            return;
        }

        if (!document.fullscreenElement) {
            void element.requestFullscreen();
            return;
        }

        void document.exitFullscreen();
    }

    function showControlMessage(message: string) {
        setControlMessage(message);

        window.setTimeout(() => {
            setControlMessage("");
        }, 3000);
    }

    function focusControlArea() {
        streamTargetRef.current?.focus();
    }

    function handleMouseMove(event: MouseEvent<HTMLDivElement>) {
        if (!canSendControl) {
            return;
        }

        const now = Date.now();

        if (now - lastMouseMoveAtRef.current < 16) {
            return;
        }

        lastMouseMoveAtRef.current = now;

        const point = translateMouseCoords(event.clientX, event.clientY);

        if (!point) {
            return;
        }

        try {
            sendMouseMove({
                pcId: targetPcId,
                ...point,
                profile: resolvedControlProfile,
                educationCode: resolvedEducationCode,
                supportCode: resolvedSupportCode,
                pcName: resolvedPcName,
            });
        } catch {
            showControlMessage("WebSocket управления не подключён");
        }
    }

    function handleLeftClick(event: MouseEvent<HTMLDivElement>) {
        if (!canSendControl) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();
        focusControlArea();

        const now = Date.now();

        if (now - lastClickAtRef.current < 120) {
            return;
        }

        lastClickAtRef.current = now;

        const point = translateMouseCoords(event.clientX, event.clientY);

        if (!point) {
            return;
        }

        try {
            sendMouseMove({
                pcId: targetPcId,
                ...point,
                profile: resolvedControlProfile,
                educationCode: resolvedEducationCode,
                supportCode: resolvedSupportCode,
                pcName: resolvedPcName,
            });

            sendMouseDown({
                pcId: targetPcId,
                ...point,
                button: 1,
                profile: resolvedControlProfile,
                educationCode: resolvedEducationCode,
                supportCode: resolvedSupportCode,
                pcName: resolvedPcName,
            });
        } catch {
            showControlMessage("WebSocket управления не подключён");
        }
    }

    function handleContextMenu(event: MouseEvent<HTMLDivElement>) {
        if (!canSendControl) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();
        focusControlArea();

        const point = translateMouseCoords(event.clientX, event.clientY);

        if (!point) {
            return;
        }

        try {
            sendMouseMove({
                pcId: targetPcId,
                ...point,
                profile: resolvedControlProfile,
                educationCode: resolvedEducationCode,
                supportCode: resolvedSupportCode,
                pcName: resolvedPcName,
            });

            sendMouseDown({
                pcId: targetPcId,
                ...point,
                button: 3,
                profile: resolvedControlProfile,
                educationCode: resolvedEducationCode,
                supportCode: resolvedSupportCode,
                pcName: resolvedPcName,
            });
        } catch {
            showControlMessage("WebSocket управления не подключён");
        }
    }

    function handleWheel(event: WheelEvent<HTMLDivElement>) {
        if (!canSendControl) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();

        const now = Date.now();

        if (now - lastWheelAtRef.current < 50) {
            return;
        }

        lastWheelAtRef.current = now;

        const point = translateMouseCoords(event.clientX, event.clientY);

        if (!point) {
            return;
        }

        try {
            sendMouseWheel({
                pcId: targetPcId,
                ...point,
                delta: event.deltaY > 0 ? 1 : -1,
                deltaX: event.deltaX,
                deltaY: event.deltaY,
                profile: resolvedControlProfile,
                educationCode: resolvedEducationCode,
                supportCode: resolvedSupportCode,
                pcName: resolvedPcName,
            });
        } catch {
            showControlMessage("WebSocket управления не подключён");
        }
    }

    function translateMouseCoords(clientX: number, clientY: number): RemotePoint | null {
        const video = videoRef.current;

        if (!video) {
            return null;
        }

        const videoBox = video.getBoundingClientRect();

        if (videoBox.width <= 0 || videoBox.height <= 0) {
            return null;
        }

        const targetWidth = stablePc?.screenWidth || video.videoWidth || 1920;
        const targetHeight = stablePc?.screenHeight || video.videoHeight || 1080;

        const renderedRect = getRenderedVideoRect(videoBox, targetWidth, targetHeight);

        const localX = clientX - renderedRect.left;
        const localY = clientY - renderedRect.top;

        if (
            localX < 0 ||
            localY < 0 ||
            localX > renderedRect.width ||
            localY > renderedRect.height
        ) {
            return null;
        }

        const relativeX = localX / renderedRect.width;
        const relativeY = localY / renderedRect.height;

        const remoteX = Math.round(relativeX * targetWidth);
        const remoteY = Math.round(relativeY * targetHeight);

        return {
            x: Math.max(0, Math.min(remoteX, targetWidth - 1)),
            y: Math.max(0, Math.min(remoteY, targetHeight - 1)),
            screenWidth: targetWidth,
            screenHeight: targetHeight,
            relativeX,
            relativeY,
        };
    }

    return (
        <section className="overflow-hidden rounded-[30px] border border-slate-300 bg-white shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-200 bg-slate-950 px-5 py-4">
                <div>
                    <h2 className="text-xl font-black text-white">
                        {title}
                    </h2>

                    <p className="mt-1 text-sm font-semibold text-slate-300">
                        {subtitle}
                    </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                    {showQualityControls && (
                        <div className="flex rounded-2xl bg-white/10 p-1">
                            {QUALITY_OPTIONS.map((option) => (
                                <button
                                    key={option.value}
                                    type="button"
                                    onClick={() => onQualityChange?.(option.value)}
                                    className={
                                        qualityValue === option.value
                                            ? "rounded-xl bg-blue-500 px-3 py-2 text-xs font-black text-white shadow-sm"
                                            : "rounded-xl px-3 py-2 text-xs font-black text-slate-300 transition hover:bg-white/10 hover:text-white"
                                    }
                                >
                                    {option.label}
                                </button>
                            ))}
                        </div>
                    )}

                    {canSendControl && (
                        <span className="inline-flex items-center gap-2 rounded-full bg-emerald-500/15 px-3 py-1.5 text-xs font-black text-emerald-200">
                            <MousePointer2 size={14} />
                            {controlLabel}
                        </span>
                    )}

                    <StatusPill status={videoStatus} />

                    <span
                        className={
                            socketStatus === "connected"
                                ? "rounded-full bg-blue-500/15 px-3 py-1.5 text-xs font-black text-blue-200"
                                : "rounded-full bg-amber-500/15 px-3 py-1.5 text-xs font-black text-amber-200"
                        }
                    >
                        WS {socketStatus}
                    </span>

                    {fps !== null && (
                        <span className="rounded-full bg-emerald-500/15 px-3 py-1.5 text-xs font-black text-emerald-200">
                            FPS {fps}
                        </span>
                    )}

                    <button
                        type="button"
                        onClick={handleFullscreen}
                        className="flex h-11 w-11 items-center justify-center rounded-2xl border border-white/10 bg-white/10 text-white transition hover:bg-white/20"
                    >
                        {isFullscreen ? <Minimize2 size={19} /> : <Maximize2 size={19} />}
                    </button>
                </div>
            </div>

            <div
                id="education-stream-target"
                ref={streamTargetRef}
                tabIndex={0}
                onMouseMove={handleMouseMove}
                onClick={handleLeftClick}
                onContextMenu={handleContextMenu}
                onWheel={handleWheel}
                className={
                    canSendControl
                        ? "relative m-5 flex aspect-video cursor-crosshair items-center justify-center overflow-hidden rounded-[26px] bg-black shadow-inner outline-none ring-2 ring-emerald-500/40"
                        : "relative m-5 flex aspect-video items-center justify-center overflow-hidden rounded-[26px] bg-black shadow-inner outline-none"
                }
            >
                {hasStream ? (
                    <video
                        ref={videoRef}
                        autoPlay
                        playsInline
                        muted
                        className="h-full w-full bg-black object-contain"
                    />
                ) : (
                    <div className="text-center">
                        <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-[28px] bg-white/10 text-blue-300">
                            <Monitor size={44} />
                        </div>

                        <h3 className="text-2xl font-black text-white">
                            Ожидание демонстрации
                        </h3>

                        <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-slate-400">
                            Трансляция экрана пока недоступна.
                        </p>
                    </div>
                )}

                {canSendControl && (
                    <div className="absolute left-5 top-5 z-20 rounded-2xl border border-emerald-300/25 bg-emerald-500/15 px-4 py-2 text-sm font-black text-emerald-100 backdrop-blur">
                        Управление активно: кликайте по экрану
                    </div>
                )}

                {videoError && (
                    <div className="absolute bottom-5 left-1/2 z-20 w-[min(720px,calc(100%-40px))] -translate-x-1/2 rounded-2xl border border-red-400/25 bg-red-500/15 px-5 py-3 text-sm font-bold text-red-100 backdrop-blur">
                        {videoError}
                    </div>
                )}

                {controlMessage && (
                    <div className="absolute bottom-5 left-1/2 z-30 w-[min(720px,calc(100%-40px))] -translate-x-1/2 rounded-2xl border border-amber-400/25 bg-amber-500/15 px-5 py-3 text-sm font-bold text-amber-100 backdrop-blur">
                        {controlMessage}
                    </div>
                )}

                {isFullscreen && (
                    <button
                        type="button"
                        onClick={handleFullscreen}
                        className="absolute right-5 top-5 z-30 rounded-2xl bg-white/90 px-4 py-2 text-sm font-black text-slate-900 shadow-lg"
                    >
                        Выйти из полного экрана
                    </button>
                )}
            </div>
        </section>
    );
}

function useStableStreamPc(pc: PcDetailsResponse | null): PcDetailsResponse | null {
    const [stablePc, setStablePc] = useState<PcDetailsResponse | null>(pc);
    const stablePcRef = useRef<PcDetailsResponse | null>(pc);

    useEffect(() => {
        const currentKey = getStreamKey(stablePcRef.current);
        const nextKey = getStreamKey(pc);

        if (currentKey !== nextKey) {
            stablePcRef.current = pc;
            setStablePc(pc);
        }
    }, [pc]);

    return stablePc;
}

function getStreamKey(pc: PcDetailsResponse | null): string {
    if (!pc) {
        return "empty";
    }

    return [
        pc.id,
        pc.webrtcUrl,
        pc.streamName,
        pc.screenWidth,
        pc.screenHeight,
    ].join("|");
}

function getRenderedVideoRect(
    videoBox: DOMRect,
    targetWidth: number,
    targetHeight: number,
): {
    left: number;
    top: number;
    width: number;
    height: number;
} {
    const containerRatio = videoBox.width / videoBox.height;
    const videoRatio = targetWidth / targetHeight;

    if (containerRatio > videoRatio) {
        const width = videoBox.height * videoRatio;
        const left = videoBox.left + (videoBox.width - width) / 2;

        return {
            left,
            top: videoBox.top,
            width,
            height: videoBox.height,
        };
    }

    const height = videoBox.width / videoRatio;
    const top = videoBox.top + (videoBox.height - height) / 2;

    return {
        left: videoBox.left,
        top,
        width: videoBox.width,
        height,
    };
}

function StatusPill({ status }: { status: string }) {
    if (status === "playing" || status === "connected") {
        return (
            <span className="inline-flex items-center gap-2 rounded-full bg-emerald-500/15 px-3 py-1.5 text-xs font-black text-emerald-200">
                <Wifi size={14} />
                Трансляция активна
            </span>
        );
    }

    if (status === "error" || status === "failed") {
        return (
            <span className="rounded-full bg-red-500/15 px-3 py-1.5 text-xs font-black text-red-200">
                Ошибка трансляции
            </span>
        );
    }

    if (status === "connecting") {
        return (
            <span className="rounded-full bg-amber-500/15 px-3 py-1.5 text-xs font-black text-amber-200">
                Подключение...
            </span>
        );
    }

    return (
        <span className="rounded-full bg-slate-500/15 px-3 py-1.5 text-xs font-black text-slate-200">
            Ожидание
        </span>
    );
}

function getEducationCodeFromUrl(): string | undefined {
    const params = new URLSearchParams(window.location.search);
    const fromQuery = params.get("educationCode") || params.get("code");

    if (fromQuery) {
        return fromQuery;
    }

    const studentMatch = window.location.pathname.match(/\/education\/student\/([^/]+)/);
    const teacherMatch = window.location.pathname.match(/\/education\/teacher\/([^/]+)/);

    return studentMatch?.[1] || teacherMatch?.[1] || undefined;
}

function getEducationControlProfile(): string {
    if (window.location.pathname.includes("/education/student")) {
        return "education_student";
    }

    if (window.location.pathname.includes("/education/teacher")) {
        return "education_teacher_view_student";
    }

    if (window.location.pathname.includes("/support/operator")) {
        return "support_operator_view_client";
    }

    return "personal";
}

function isUiTarget(target: EventTarget | null): boolean {
    if (!(target instanceof HTMLElement)) {
        return false;
    }

    const tag = target.tagName.toLowerCase();

    return (
        tag === "input" ||
        tag === "textarea" ||
        tag === "select" ||
        tag === "button" ||
        target.isContentEditable
    );
}