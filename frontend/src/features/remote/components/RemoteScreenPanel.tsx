import { useEffect, useRef, useState } from "react";
import type { DragEvent, MouseEvent, PointerEvent, WheelEvent } from "react";
import axios from "axios";
import { Monitor, Upload, Wifi } from "lucide-react";
import type { PcDetailsResponse } from "../../pcs/pcTypes";
import { uploadFileToPc } from "../remoteApi";
import { useGStreamerWebRtc } from "../useGStreamerWebRtc";

export type RemoteScreenDisplayMode = "fit" | "fill" | "original";

type RemoteCommandPayload = {
    x?: number;
    y?: number;
    screenWidth?: number;
    screenHeight?: number;
    relativeX?: number;
    relativeY?: number;
    button?: number;
    delta?: number;
    keyCode?: number;
    ctrl?: boolean;
    alt?: boolean;
    shift?: boolean;
};

type RemoteScreenPanelProps = {
    pc: PcDetailsResponse | null;
    pcId: number;
    pcName: string;
    loading: boolean;
    error: string;
    socketStatus: string;
    commandMessage: string;
    screenUploadProgress: number | null;
    displayMode: RemoteScreenDisplayMode;
    controlEnabled: boolean;
    onRemoteCommand: (action: string, data?: RemoteCommandPayload) => void;
    onCommandMessageChange: (message: string) => void;
    onScreenUploadProgressChange: (progress: number | null) => void;
};

type RemotePoint = {
    x: number;
    y: number;
    screenWidth: number;
    screenHeight: number;
    relativeX: number;
    relativeY: number;
};

type MobileControlMode = "touch" | "joystick";

type MobileControlSettings = {
    mode: MobileControlMode;
    joystickSize: number;
    opacity: number;
    joystickX: number;
    joystickY: number;
    buttonsX: number;
    buttonsY: number;
    cursorSize: number;
    joystickSpeed: number;
};

type MobileTouchState = {
    active: boolean;
    pointerId: number | null;
    startX: number;
    startY: number;
    moved: boolean;
    longPressFired: boolean;
};

const DEFAULT_MOBILE_SETTINGS: MobileControlSettings = {
    mode: "touch",
    joystickSize: 150,
    opacity: 0.72,
    joystickX: 16,
    joystickY: 76,
    buttonsX: 84,
    buttonsY: 72,
    cursorSize: 24,
    joystickSpeed: 14,
};

export function RemoteScreenPanel({
                                      pc,
                                      pcId,
                                      pcName,
                                      loading,
                                      error,
                                      socketStatus,
                                      commandMessage,
                                      screenUploadProgress,
                                      displayMode,
                                      controlEnabled,
                                      onRemoteCommand,
                                      onCommandMessageChange,
                                      onScreenUploadProgressChange,
                                  }: RemoteScreenPanelProps) {
    const screenTargetRef = useRef<HTMLDivElement | null>(null);
    const [dragOverScreen, setDragOverScreen] = useState(false);
    const [isMobileDevice, setIsMobileDevice] = useState(() => isMobileControlDevice());
    const [mobileSettings, setMobileSettings] = useState<MobileControlSettings>(() => readMobileControlSettings());
    const [mobileCursor, setMobileCursor] = useState(() => {
        const width = pc?.screenWidth || 1920;
        const height = pc?.screenHeight || 1080;

        return {
            x: Math.round(width / 2),
            y: Math.round(height / 2),
        };
    });
    const [joystickKnob, setJoystickKnob] = useState({ x: 0, y: 0 });

    const lastMouseMoveAtRef = useRef(0);
    const lastClickAtRef = useRef(0);
    const lastWheelAtRef = useRef(0);

    const mobileCursorRef = useRef(mobileCursor);
    const mobileTouchRef = useRef<MobileTouchState>({
        active: false,
        pointerId: null,
        startX: 0,
        startY: 0,
        moved: false,
        longPressFired: false,
    });
    const mobileLongPressTimerRef = useRef<number | null>(null);
    const mobileLastTapAtRef = useRef(0);

    const joystickZoneRef = useRef<HTMLDivElement | null>(null);
    const joystickActiveRef = useRef(false);
    const joystickVectorRef = useRef({ x: 0, y: 0 });
    const joystickTimerRef = useRef<number | null>(null);

    const {
        videoRef,
        videoStatus,
        videoError,
        fps,
    } = useGStreamerWebRtc({
        pc,
        pcId,
        pcName,
    });

    const mobileControlActive = controlEnabled && isMobileDevice;
    const mobileJoystickActive = mobileControlActive && mobileSettings.mode === "joystick";
    const mobileTouchActive = mobileControlActive && mobileSettings.mode === "touch";

    useEffect(() => {
        mobileCursorRef.current = mobileCursor;
    }, [mobileCursor]);

    useEffect(() => {
        const size = getRemoteTargetSize();

        setMobileCursor((current) => ({
            x: clampNumber(current.x, 0, size.width - 1),
            y: clampNumber(current.y, 0, size.height - 1),
        }));
    }, [pc?.screenWidth, pc?.screenHeight]);

    useEffect(() => {
        function refreshMobileState() {
            setIsMobileDevice(isMobileControlDevice());
            setMobileSettings(readMobileControlSettings());
        }

        window.addEventListener("resize", refreshMobileState);
        window.addEventListener("orientationchange", refreshMobileState);
        window.addEventListener("storage", refreshMobileState);

        refreshMobileState();

        return () => {
            window.removeEventListener("resize", refreshMobileState);
            window.removeEventListener("orientationchange", refreshMobileState);
            window.removeEventListener("storage", refreshMobileState);
        };
    }, []);

    useEffect(() => {
        if (!mobileJoystickActive) {
            stopJoystickLoop();
            setJoystickKnob({ x: 0, y: 0 });
        }

        if (!mobileControlActive) {
            clearMobileLongPressTimer();
            mobileTouchRef.current.active = false;
        }
    }, [mobileControlActive, mobileJoystickActive]);

    useEffect(() => {
        function handleDocumentKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape" && document.fullscreenElement) {
                void document.exitFullscreen();
                return;
            }

            if (!controlEnabled) {
                return;
            }

            if (isUiTarget(event.target)) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();

            onRemoteCommand("KEY_PRESS", {
                keyCode: event.keyCode,
                ctrl: event.ctrlKey,
                alt: event.altKey,
                shift: event.shiftKey,
            });
        }

        function handleDocumentKeyUp(event: KeyboardEvent) {
            if (!controlEnabled) {
                return;
            }

            if (isUiTarget(event.target)) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();

            onRemoteCommand("KEY_RELEASE", {
                keyCode: event.keyCode,
            });
        }

        document.addEventListener("keydown", handleDocumentKeyDown);
        document.addEventListener("keyup", handleDocumentKeyUp);

        return () => {
            document.removeEventListener("keydown", handleDocumentKeyDown);
            document.removeEventListener("keyup", handleDocumentKeyUp);
        };
    }, [controlEnabled, onRemoteCommand]);

    useEffect(() => {
        return () => {
            stopJoystickLoop();
            clearMobileLongPressTimer();
        };
    }, []);

    async function uploadDroppedFile(file: File) {
        if (!pcId || Number.isNaN(pcId)) {
            onCommandMessageChange("ПК не выбран");
            return;
        }

        try {
            onScreenUploadProgressChange(0);
            onCommandMessageChange(`Отправка файла: ${file.name}`);

            const result = await uploadFileToPc(
                pcId,
                file,
                onScreenUploadProgressChange,
            );

            onCommandMessageChange(`Файл отправлен: ${result.fileName}`);
        } catch (e) {
            if (axios.isAxiosError(e)) {
                const message =
                    e.response?.data?.message ||
                    e.response?.data?.error ||
                    e.response?.data ||
                    e.message;

                onCommandMessageChange(`Ошибка отправки файла: ${message}`);
            } else {
                onCommandMessageChange("Не удалось отправить файл");
            }
        } finally {
            setTimeout(() => {
                onScreenUploadProgressChange(null);
            }, 1500);
        }
    }

    function handleScreenDrop(event: DragEvent<HTMLDivElement>) {
        event.preventDefault();
        setDragOverScreen(false);

        const file = event.dataTransfer.files?.[0];

        if (!file) {
            onCommandMessageChange("Файл не выбран");
            return;
        }

        void uploadDroppedFile(file);
    }

    function handleScreenDragOver(event: DragEvent<HTMLDivElement>) {
        event.preventDefault();
        setDragOverScreen(true);
    }

    function handleScreenDragLeave(event: DragEvent<HTMLDivElement>) {
        event.preventDefault();

        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
            setDragOverScreen(false);
        }
    }

    function handleMouseMove(event: MouseEvent<HTMLDivElement>) {
        if (!controlEnabled || mobileControlActive) {
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

        onRemoteCommand("MOUSE_MOVE", point);
    }

    function handleLeftClick(event: MouseEvent<HTMLDivElement>) {
        if (!controlEnabled || mobileControlActive) {
            return;
        }

        const now = Date.now();

        if (now - lastClickAtRef.current < 120) {
            return;
        }

        lastClickAtRef.current = now;

        event.preventDefault();

        const point = translateMouseCoords(event.clientX, event.clientY);

        if (point) {
            onRemoteCommand("MOUSE_MOVE", point);
            onRemoteCommand("MOUSE_CLICK", {
                button: 1,
            });
        }
    }

    function handleContextMenu(event: MouseEvent<HTMLDivElement>) {
        if (!controlEnabled || mobileControlActive) {
            return;
        }

        event.preventDefault();

        const point = translateMouseCoords(event.clientX, event.clientY);

        if (point) {
            onRemoteCommand("MOUSE_MOVE", point);
            onRemoteCommand("MOUSE_CLICK", {
                button: 3,
            });
        }
    }

    function handleWheel(event: WheelEvent<HTMLDivElement>) {
        if (!controlEnabled) {
            return;
        }

        const now = Date.now();

        if (now - lastWheelAtRef.current < 50) {
            return;
        }

        lastWheelAtRef.current = now;

        event.preventDefault();

        onRemoteCommand("MOUSE_WHEEL", {
            delta: event.deltaY > 0 ? 1 : -1,
        });
    }

    function handleMobilePointerDown(event: PointerEvent<HTMLDivElement>) {
        if (!mobileTouchActive || event.pointerType === "mouse") {
            return;
        }

        const point = translateMouseCoords(event.clientX, event.clientY);

        if (!point) {
            return;
        }

        try {
            event.currentTarget.setPointerCapture(event.pointerId);
        } catch {
            // ignore
        }

        mobileTouchRef.current = {
            active: true,
            pointerId: event.pointerId,
            startX: event.clientX,
            startY: event.clientY,
            moved: false,
            longPressFired: false,
        };

        sendMobileMouseMove(point);

        clearMobileLongPressTimer();

        mobileLongPressTimerRef.current = window.setTimeout(() => {
            const state = mobileTouchRef.current;

            if (!state.active) {
                return;
            }

            state.longPressFired = true;
            clickMobileMouse(3);
            onCommandMessageChange("ПКМ: долгое нажатие");

            window.setTimeout(() => {
                onCommandMessageChange("");
            }, 1200);
        }, 620);

        event.preventDefault();
        event.stopPropagation();
    }

    function handleMobilePointerMove(event: PointerEvent<HTMLDivElement>) {
        if (!mobileTouchActive || event.pointerType === "mouse") {
            return;
        }

        const state = mobileTouchRef.current;

        if (!state.active || state.pointerId !== event.pointerId) {
            return;
        }

        const movedDistance = Math.hypot(
            event.clientX - state.startX,
            event.clientY - state.startY,
        );

        if (movedDistance > 8) {
            state.moved = true;
            clearMobileLongPressTimer();
        }

        const point = translateMouseCoords(event.clientX, event.clientY);

        if (point) {
            sendMobileMouseMove(point);
        }

        event.preventDefault();
        event.stopPropagation();
    }

    function handleMobilePointerUp(event: PointerEvent<HTMLDivElement>) {
        if (!mobileTouchActive || event.pointerType === "mouse") {
            return;
        }

        const state = mobileTouchRef.current;

        if (!state.active || state.pointerId !== event.pointerId) {
            return;
        }

        clearMobileLongPressTimer();

        try {
            event.currentTarget.releasePointerCapture(event.pointerId);
        } catch {
            // ignore
        }

        if (!state.moved && !state.longPressFired) {
            const now = Date.now();
            const doubleTap = now - mobileLastTapAtRef.current < 320;

            clickMobileMouse(1);

            if (doubleTap) {
                window.setTimeout(() => clickMobileMouse(1), 75);
            }

            mobileLastTapAtRef.current = now;
        }

        mobileTouchRef.current.active = false;
        mobileTouchRef.current.pointerId = null;

        event.preventDefault();
        event.stopPropagation();
    }

    function handleMobilePointerCancel(event: PointerEvent<HTMLDivElement>) {
        if (!mobileTouchActive) {
            return;
        }

        clearMobileLongPressTimer();
        mobileTouchRef.current.active = false;
        mobileTouchRef.current.pointerId = null;

        event.preventDefault();
        event.stopPropagation();
    }

    function clearMobileLongPressTimer() {
        if (mobileLongPressTimerRef.current !== null) {
            window.clearTimeout(mobileLongPressTimerRef.current);
            mobileLongPressTimerRef.current = null;
        }
    }

    function sendMobileMouseMove(point: RemotePoint) {
        setMobileCursor({ x: point.x, y: point.y });
        mobileCursorRef.current = { x: point.x, y: point.y };
        onRemoteCommand("MOUSE_MOVE", point);
    }

    function sendMobileMouseMoveByCoordinates(x: number, y: number) {
        const size = getRemoteTargetSize();

        const nextX = clampNumber(x, 0, size.width - 1);
        const nextY = clampNumber(y, 0, size.height - 1);

        setMobileCursor({ x: nextX, y: nextY });
        mobileCursorRef.current = { x: nextX, y: nextY };

        onRemoteCommand("MOUSE_MOVE", {
            x: Math.round(nextX),
            y: Math.round(nextY),
            screenWidth: size.width,
            screenHeight: size.height,
            relativeX: nextX / size.width,
            relativeY: nextY / size.height,
        });
    }

    function clickMobileMouse(button: number) {
        const size = getRemoteTargetSize();
        const point = mobileCursorRef.current;

        onRemoteCommand("MOUSE_MOVE", {
            x: Math.round(point.x),
            y: Math.round(point.y),
            screenWidth: size.width,
            screenHeight: size.height,
            relativeX: point.x / size.width,
            relativeY: point.y / size.height,
        });

        onRemoteCommand("MOUSE_CLICK", {
            button,
            x: Math.round(point.x),
            y: Math.round(point.y),
            screenWidth: size.width,
            screenHeight: size.height,
            relativeX: point.x / size.width,
            relativeY: point.y / size.height,
        });
    }

    function wheelMobileMouse(delta: number) {
        const size = getRemoteTargetSize();
        const point = mobileCursorRef.current;

        onRemoteCommand("MOUSE_WHEEL", {
            delta,
            x: Math.round(point.x),
            y: Math.round(point.y),
            screenWidth: size.width,
            screenHeight: size.height,
            relativeX: point.x / size.width,
            relativeY: point.y / size.height,
        });
    }

    function handleJoystickPointerDown(event: PointerEvent<HTMLDivElement>) {
        if (!mobileJoystickActive) {
            return;
        }

        joystickActiveRef.current = true;

        try {
            event.currentTarget.setPointerCapture(event.pointerId);
        } catch {
            // ignore
        }

        updateJoystickVector(event);
        startJoystickLoop();

        event.preventDefault();
        event.stopPropagation();
    }

    function handleJoystickPointerMove(event: PointerEvent<HTMLDivElement>) {
        if (!mobileJoystickActive || !joystickActiveRef.current) {
            return;
        }

        updateJoystickVector(event);
        event.preventDefault();
        event.stopPropagation();
    }

    function handleJoystickPointerUp(event: PointerEvent<HTMLDivElement>) {
        joystickActiveRef.current = false;
        joystickVectorRef.current = { x: 0, y: 0 };
        setJoystickKnob({ x: 0, y: 0 });
        stopJoystickLoop();

        try {
            event.currentTarget.releasePointerCapture(event.pointerId);
        } catch {
            // ignore
        }

        event.preventDefault();
        event.stopPropagation();
    }

    function updateJoystickVector(event: PointerEvent<HTMLDivElement>) {
        const zone = joystickZoneRef.current;

        if (!zone) {
            return;
        }

        const rect = zone.getBoundingClientRect();
        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;
        const radius = rect.width * 0.36;

        let dx = event.clientX - centerX;
        let dy = event.clientY - centerY;

        const distance = Math.hypot(dx, dy);

        if (distance > radius) {
            dx = (dx / distance) * radius;
            dy = (dy / distance) * radius;
        }

        joystickVectorRef.current = {
            x: clampNumber(dx / radius, -1, 1),
            y: clampNumber(dy / radius, -1, 1),
        };

        setJoystickKnob({ x: dx, y: dy });
    }

    function startJoystickLoop() {
        if (joystickTimerRef.current !== null) {
            return;
        }

        joystickTimerRef.current = window.setInterval(() => {
            const vector = joystickVectorRef.current;

            if (!joystickActiveRef.current) {
                return;
            }

            const size = getRemoteTargetSize();
            const current = mobileCursorRef.current;
            const speed = mobileSettings.joystickSpeed * Math.max(1, Math.max(size.width, size.height) / 1400);

            sendMobileMouseMoveByCoordinates(
                current.x + vector.x * speed,
                current.y + vector.y * speed,
            );
        }, 33);
    }

    function stopJoystickLoop() {
        if (joystickTimerRef.current !== null) {
            window.clearInterval(joystickTimerRef.current);
            joystickTimerRef.current = null;
        }

        joystickActiveRef.current = false;
        joystickVectorRef.current = { x: 0, y: 0 };
    }

    function getRemoteTargetSize() {
        const video = videoRef.current;

        return {
            width: pc?.screenWidth || video?.videoWidth || 1920,
            height: pc?.screenHeight || video?.videoHeight || 1080,
        };
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

        const targetWidth = pc?.screenWidth || video.videoWidth || 1920;
        const targetHeight = pc?.screenHeight || video.videoHeight || 1080;

        const renderedRect = getRenderedVideoRect(
            videoBox,
            targetWidth,
            targetHeight,
            displayMode,
        );

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

    const hasWebRtcData = Boolean(pc?.webrtcUrl && pc?.streamName);
    const remoteSize = getRemoteTargetSize();
    const cursorStyle = getMobileCursorStyle();

    function getMobileCursorStyle(): React.CSSProperties {
        const fallback: React.CSSProperties = {
            left: `${clampNumber(mobileCursor.x / remoteSize.width, 0, 1) * 100}%`,
            top: `${clampNumber(mobileCursor.y / remoteSize.height, 0, 1) * 100}%`,
            width: mobileSettings.cursorSize,
            height: mobileSettings.cursorSize,
            opacity: mobileSettings.opacity,
        };

        const container = screenTargetRef.current;
        const video = videoRef.current;

        if (!container || !video) {
            return fallback;
        }

        const containerRect = container.getBoundingClientRect();
        const videoBox = video.getBoundingClientRect();

        if (videoBox.width <= 0 || videoBox.height <= 0) {
            return fallback;
        }

        const renderedRect = getRenderedVideoRect(
            videoBox,
            remoteSize.width,
            remoteSize.height,
            displayMode,
        );

        const left = renderedRect.left - containerRect.left +
            clampNumber(mobileCursor.x / remoteSize.width, 0, 1) * renderedRect.width;
        const top = renderedRect.top - containerRect.top +
            clampNumber(mobileCursor.y / remoteSize.height, 0, 1) * renderedRect.height;

        return {
            left,
            top,
            width: mobileSettings.cursorSize,
            height: mobileSettings.cursorSize,
            opacity: mobileSettings.opacity,
        };
    }

    return (
        <section className="overflow-hidden rounded-[32px] border border-slate-200 bg-slate-950 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-4 border-b border-white/10 bg-slate-900 px-5 py-4">
                <div>
                    <h2 className="font-black text-white">Экран ПК</h2>
                    <p className="mt-1 text-sm text-slate-400">
                        {controlEnabled
                            ? isMobileDevice
                                ? mobileSettings.mode === "joystick"
                                    ? "Телефонное управление: джойстик и кнопки мыши поверх трансляции"
                                    : "Телефонное управление: касайтесь экрана как сенсорного дисплея"
                                : "Управление включено: мышь, клики, прокрутка и клавиатура активны"
                            : "Перетащите файл на экран, чтобы отправить его на ПК"}
                    </p>
                </div>

                <div className="flex flex-wrap items-center gap-3">
                    <StatusPill
                        label={getUserVideoStatus(videoStatus)}
                        kind={getVideoStatusKind(videoStatus)}
                    />

                    <div
                        className={
                            controlEnabled
                                ? "rounded-full bg-emerald-500/10 px-3 py-1.5 text-sm font-bold text-emerald-300"
                                : "rounded-full bg-slate-500/10 px-3 py-1.5 text-sm font-bold text-slate-300"
                        }
                    >
                        {controlEnabled ? "Управление активно" : "Только просмотр"}
                    </div>

                    {mobileControlActive && (
                        <div className="rounded-full bg-violet-500/10 px-3 py-1.5 text-sm font-bold text-violet-300">
                            {mobileSettings.mode === "joystick" ? "🕹 Джойстик" : "☝️ Сенсор"}
                        </div>
                    )}

                    <div className="flex items-center gap-2 rounded-full bg-blue-500/10 px-3 py-1.5 text-sm font-bold text-blue-300">
                        <Wifi size={16} />
                        {socketStatus === "connected" ? "Подключено" : "Соединение..."}
                    </div>

                    {fps !== null && (
                        <div className="rounded-full bg-emerald-500/10 px-3 py-1.5 text-sm font-bold text-emerald-300">
                            FPS: {fps}
                        </div>
                    )}
                </div>
            </div>

            <div
                id="remote-screen-fullscreen-target"
                ref={screenTargetRef}
                onDrop={handleScreenDrop}
                onDragOver={handleScreenDragOver}
                onDragLeave={handleScreenDragLeave}
                onMouseMove={handleMouseMove}
                onClick={handleLeftClick}
                onContextMenu={handleContextMenu}
                onWheel={handleWheel}
                onPointerDown={handleMobilePointerDown}
                onPointerMove={handleMobilePointerMove}
                onPointerUp={handleMobilePointerUp}
                onPointerCancel={handleMobilePointerCancel}
                tabIndex={0}
                className={
                    controlEnabled
                        ? "relative flex min-h-[560px] cursor-crosshair items-center justify-center overflow-hidden bg-black p-4 outline-none"
                        : "relative flex min-h-[560px] items-center justify-center overflow-hidden bg-black p-4 outline-none"
                }
                style={{
                    touchAction: mobileControlActive ? "none" : undefined,
                    userSelect: mobileControlActive ? "none" : undefined,
                }}
            >
                {dragOverScreen && (
                    <div className="absolute inset-4 z-30 flex items-center justify-center rounded-[28px] border-2 border-dashed border-blue-300 bg-blue-500/20 backdrop-blur-sm">
                        <div className="rounded-[28px] bg-white px-8 py-7 text-center shadow-2xl">
                            <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-3xl bg-blue-100 text-blue-700">
                                <Upload size={34} />
                            </div>

                            <div className="text-2xl font-black text-slate-950">
                                Отпустите файл здесь
                            </div>

                            <div className="mt-2 text-sm font-semibold text-slate-500">
                                Файл будет отправлен на {pcName}
                            </div>
                        </div>
                    </div>
                )}

                {loading ? (
                    <div className="text-center">
                        <div className="mx-auto mb-5 h-14 w-14 animate-spin rounded-full border-4 border-blue-900 border-t-blue-400" />
                        <p className="font-bold text-slate-300">
                            Загрузка информации о ПК...
                        </p>
                    </div>
                ) : error ? (
                    <div className="rounded-3xl border border-red-400/30 bg-red-500/10 px-6 py-5 text-center text-red-200">
                        {error}
                    </div>
                ) : hasWebRtcData ? (
                    <video
                        ref={videoRef}
                        autoPlay
                        playsInline
                        muted
                        className={getVideoClassName(displayMode)}
                    />
                ) : (
                    <div className="text-center">
                        <div className="mx-auto mb-6 flex h-24 w-24 items-center justify-center rounded-[32px] bg-white/10 text-blue-300">
                            <Monitor size={52} />
                        </div>

                        <h3 className="text-3xl font-black text-white">
                            Нет трансляции
                        </h3>

                        <p className="mx-auto mt-4 max-w-xl text-slate-400">
                            Агент подключён, но данные WebRTC пока не получены.
                        </p>
                    </div>
                )}

                {mobileControlActive && (
                    <div className="pointer-events-none absolute left-1/2 top-5 z-30 -translate-x-1/2 rounded-full border border-white/10 bg-slate-950/75 px-4 py-2 text-xs font-black text-white shadow-lg backdrop-blur">
                        {mobileSettings.mode === "joystick"
                            ? "🕹 Джойстик включён"
                            : "☝️ Сенсорное управление"}
                    </div>
                )}

                {mobileControlActive && (
                    <div
                        className="pointer-events-none absolute z-50 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-emerald-500 shadow-[0_0_18px_rgba(16,185,129,.9)]"
                        style={cursorStyle}
                    >
                        <div className="absolute left-1/2 top-1/2 h-1.5 w-1.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-white" />
                    </div>
                )}

                {mobileJoystickActive && (
                    <MobileJoystickOverlay
                        settings={mobileSettings}
                        joystickZoneRef={joystickZoneRef}
                        joystickKnob={joystickKnob}
                        onJoystickPointerDown={handleJoystickPointerDown}
                        onJoystickPointerMove={handleJoystickPointerMove}
                        onJoystickPointerUp={handleJoystickPointerUp}
                        onLeftClick={() => clickMobileMouse(1)}
                        onRightClick={() => clickMobileMouse(3)}
                        onDoubleClick={() => {
                            clickMobileMouse(1);
                            window.setTimeout(() => clickMobileMouse(1), 90);
                        }}
                        onWheelUp={() => wheelMobileMouse(-1)}
                        onWheelDown={() => wheelMobileMouse(1)}
                    />
                )}

                {(commandMessage || screenUploadProgress !== null || videoError) && (
                    <div className="absolute bottom-6 left-1/2 z-20 w-[min(760px,calc(100%-48px))] -translate-x-1/2 rounded-3xl border border-white/10 bg-slate-950/85 p-4 shadow-2xl backdrop-blur-md">
                        {videoError && (
                            <div className="mb-3 rounded-2xl border border-red-400/25 bg-red-500/10 px-5 py-3 text-sm font-bold text-red-200">
                                {videoError}
                            </div>
                        )}

                        {commandMessage && (
                            <div className="rounded-2xl border border-blue-400/25 bg-blue-500/10 px-5 py-3 text-sm font-bold text-blue-200">
                                {commandMessage}
                            </div>
                        )}

                        {screenUploadProgress !== null && (
                            <div className="mt-3 rounded-2xl border border-blue-400/25 bg-blue-500/10 p-4">
                                <div className="mb-2 flex justify-between text-sm font-bold text-blue-200">
                                    <span>Загрузка файла</span>
                                    <span>{screenUploadProgress}%</span>
                                </div>

                                <div className="h-3 overflow-hidden rounded-full bg-white/10">
                                    <div
                                        className="h-full rounded-full bg-blue-400 transition-all"
                                        style={{ width: `${screenUploadProgress}%` }}
                                    />
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </section>
    );
}

function MobileJoystickOverlay({
                                   settings,
                                   joystickZoneRef,
                                   joystickKnob,
                                   onJoystickPointerDown,
                                   onJoystickPointerMove,
                                   onJoystickPointerUp,
                                   onLeftClick,
                                   onRightClick,
                                   onDoubleClick,
                                   onWheelUp,
                                   onWheelDown,
                               }: {
    settings: MobileControlSettings;
    joystickZoneRef: React.RefObject<HTMLDivElement | null>;
    joystickKnob: { x: number; y: number };
    onJoystickPointerDown: (event: PointerEvent<HTMLDivElement>) => void;
    onJoystickPointerMove: (event: PointerEvent<HTMLDivElement>) => void;
    onJoystickPointerUp: (event: PointerEvent<HTMLDivElement>) => void;
    onLeftClick: () => void;
    onRightClick: () => void;
    onDoubleClick: () => void;
    onWheelUp: () => void;
    onWheelDown: () => void;
}) {
    const joystickStyle: React.CSSProperties = {
        width: settings.joystickSize,
        height: settings.joystickSize,
        opacity: settings.opacity,
        left: `${settings.joystickX}%`,
        top: `${settings.joystickY}%`,
        transform: "translate(-50%, -50%)",
    };

    const buttonsStyle: React.CSSProperties = {
        opacity: settings.opacity,
        left: `${settings.buttonsX}%`,
        top: `${settings.buttonsY}%`,
        transform: "translate(-50%, -50%)",
    };

    return (
        <>
            <div
                ref={joystickZoneRef}
                onPointerDown={onJoystickPointerDown}
                onPointerMove={onJoystickPointerMove}
                onPointerUp={onJoystickPointerUp}
                onPointerCancel={onJoystickPointerUp}
                className="absolute z-50 touch-none rounded-full border-2 border-white/30 bg-slate-950/70 shadow-2xl backdrop-blur-md"
                style={joystickStyle}
            >
                <div className="absolute inset-[16%] rounded-full border-2 border-dashed border-white/20" />
                <div className="absolute inset-[34%] rounded-full bg-white/10" />

                <div
                    className="absolute left-1/2 top-1/2 h-[42%] w-[42%] rounded-full border-4 border-white/85 bg-blue-500 shadow-xl"
                    style={{
                        transform: `translate(calc(-50% + ${joystickKnob.x}px), calc(-50% + ${joystickKnob.y}px))`,
                    }}
                />

                <div className="pointer-events-none absolute inset-x-0 -bottom-7 text-center text-[11px] font-black text-white/80">
                    курсор
                </div>
            </div>

            <div
                className="absolute z-50 grid w-[136px] gap-2 touch-none"
                style={buttonsStyle}
                onPointerDown={(event) => {
                    event.preventDefault();
                    event.stopPropagation();
                }}
            >
                <button
                    type="button"
                    onPointerDown={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        onLeftClick();
                    }}
                    className="h-16 rounded-3xl bg-blue-600 text-base font-black text-white shadow-lg shadow-blue-600/30 active:scale-95"
                >
                    ЛКМ
                </button>

                <button
                    type="button"
                    onPointerDown={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        onRightClick();
                    }}
                    className="h-16 rounded-3xl border border-white/20 bg-slate-950/70 text-base font-black text-white shadow-lg backdrop-blur active:scale-95"
                >
                    ПКМ
                </button>

                <button
                    type="button"
                    onPointerDown={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        onDoubleClick();
                    }}
                    className="h-12 rounded-2xl border border-white/20 bg-slate-950/60 text-xs font-black text-white shadow-lg backdrop-blur active:scale-95"
                >
                    2× ЛКМ
                </button>

                <div className="grid grid-cols-2 gap-2">
                    <button
                        type="button"
                        onPointerDown={(event) => {
                            event.preventDefault();
                            event.stopPropagation();
                            onWheelUp();
                        }}
                        className="h-11 rounded-2xl border border-white/20 bg-slate-950/60 text-xs font-black text-white active:scale-95"
                    >
                        ↑
                    </button>

                    <button
                        type="button"
                        onPointerDown={(event) => {
                            event.preventDefault();
                            event.stopPropagation();
                            onWheelDown();
                        }}
                        className="h-11 rounded-2xl border border-white/20 bg-slate-950/60 text-xs font-black text-white active:scale-95"
                    >
                        ↓
                    </button>
                </div>
            </div>
        </>
    );
}

function readMobileControlSettings(): MobileControlSettings {
    const raw =
        localStorage.getItem("remoteMobileScreenControlSettingsV2") ||
        localStorage.getItem("mobileControlSettingsV1") ||
        localStorage.getItem("remoteMobileControlSettingsV1") ||
        localStorage.getItem("mobileRemoteControlSettingsV1");

    let parsed: Record<string, unknown> = {};

    if (raw) {
        try {
            parsed = JSON.parse(raw) as Record<string, unknown>;
        } catch {
            parsed = {};
        }
    }

    const mode = readModeFromString(
        parsed.mode ||
        parsed.controlMode ||
        localStorage.getItem("remoteMobileScreenControlModeV1") ||
        localStorage.getItem("mobileControlMode") ||
        localStorage.getItem("mobileRemoteControlMode") ||
        localStorage.getItem("remoteMobileControlMode"),
    );

    const joystickSize = clampNumber(
        parsed.joystickSize ||
        parsed.size ||
        localStorage.getItem("mobileJoystickSize") ||
        localStorage.getItem("mobileControlJoystickSize") ||
        localStorage.getItem("remoteMobileJoystickSize"),
        110,
        260,
        DEFAULT_MOBILE_SETTINGS.joystickSize,
    );

    const opacityPercent = clampNumber(
        parsed.controlsOpacity ||
        parsed.opacityPercent ||
        parsed.opacity ||
        localStorage.getItem("mobileControlsOpacity") ||
        localStorage.getItem("mobileControlOpacity") ||
        localStorage.getItem("remoteMobileControlsOpacity"),
        25,
        100,
        DEFAULT_MOBILE_SETTINGS.opacity * 100,
    );

    return {
        mode: mode || DEFAULT_MOBILE_SETTINGS.mode,
        joystickSize,
        opacity: opacityPercent / 100,
        joystickX: clampNumber(parsed.joystickX || localStorage.getItem("mobileJoystickX"), 5, 45, DEFAULT_MOBILE_SETTINGS.joystickX),
        joystickY: clampNumber(parsed.joystickY || localStorage.getItem("mobileJoystickY"), 45, 92, DEFAULT_MOBILE_SETTINGS.joystickY),
        buttonsX: clampNumber(parsed.mouseButtonsX || parsed.buttonsX || localStorage.getItem("mobileMouseButtonsX"), 55, 95, DEFAULT_MOBILE_SETTINGS.buttonsX),
        buttonsY: clampNumber(parsed.mouseButtonsY || parsed.buttonsY || localStorage.getItem("mobileMouseButtonsY"), 38, 92, DEFAULT_MOBILE_SETTINGS.buttonsY),
        cursorSize: clampNumber(parsed.cursorSize || localStorage.getItem("mobileCursorSize"), 14, 42, DEFAULT_MOBILE_SETTINGS.cursorSize),
        joystickSpeed: clampNumber(parsed.joystickSpeed || localStorage.getItem("mobileJoystickSpeed"), 4, 30, DEFAULT_MOBILE_SETTINGS.joystickSpeed),
    };
}

function readModeFromString(value?: unknown): MobileControlMode | undefined {
    const normalized = String(value || "").toLowerCase();

    if (normalized === "joystick" || normalized === "joystick_mouse") {
        return "joystick";
    }

    if (normalized === "touch" || normalized === "touch_screen") {
        return "touch";
    }

    return undefined;
}

function isMobileControlDevice(): boolean {
    if (typeof window === "undefined") {
        return false;
    }

    const userAgent = navigator.userAgent || "";
    const platform = navigator.platform || "";

    const isIPhoneOrIPad =
        /iPhone|iPad|iPod/i.test(userAgent) ||
        (platform === "MacIntel" && navigator.maxTouchPoints > 1);

    const isAndroid = /Android/i.test(userAgent);
    const hasTouch = navigator.maxTouchPoints > 0;
    const smallViewport =
        window.innerWidth <= 1100 ||
        window.screen.width <= 1100 ||
        window.screen.height <= 1100;

    const coarsePointer = window.matchMedia?.("(pointer: coarse)")?.matches === true;
    const hoverNone = window.matchMedia?.("(hover: none)")?.matches === true;

    return isIPhoneOrIPad || isAndroid || (hasTouch && smallViewport) || coarsePointer || hoverNone;
}

function getRenderedVideoRect(
    videoBox: DOMRect,
    targetWidth: number,
    targetHeight: number,
    displayMode: RemoteScreenDisplayMode,
) {
    if (displayMode === "fill") {
        return getCoverRect(videoBox, targetWidth, targetHeight);
    }

    return getContainRect(videoBox, targetWidth, targetHeight);
}

function getContainRect(videoBox: DOMRect, targetWidth: number, targetHeight: number) {
    const sourceAspect = targetWidth / targetHeight;
    const boxAspect = videoBox.width / videoBox.height;

    let width = videoBox.width;
    let height = videoBox.height;

    if (boxAspect > sourceAspect) {
        height = videoBox.height;
        width = height * sourceAspect;
    } else {
        width = videoBox.width;
        height = width / sourceAspect;
    }

    return {
        left: videoBox.left + (videoBox.width - width) / 2,
        top: videoBox.top + (videoBox.height - height) / 2,
        width,
        height,
    };
}

function getCoverRect(videoBox: DOMRect, targetWidth: number, targetHeight: number) {
    const sourceAspect = targetWidth / targetHeight;
    const boxAspect = videoBox.width / videoBox.height;

    let width = videoBox.width;
    let height = videoBox.height;

    if (boxAspect > sourceAspect) {
        width = videoBox.width;
        height = width / sourceAspect;
    } else {
        height = videoBox.height;
        width = height * sourceAspect;
    }

    return {
        left: videoBox.left + (videoBox.width - width) / 2,
        top: videoBox.top + (videoBox.height - height) / 2,
        width,
        height,
    };
}

function getVideoClassName(displayMode: RemoteScreenDisplayMode): string {
    const base = "remote-screen-video rounded-[24px] bg-black";

    if (displayMode === "fill") {
        return `${base} h-[560px] w-full object-cover`;
    }

    if (displayMode === "original") {
        return `${base} max-h-[560px] max-w-full object-contain`;
    }

    return `${base} h-[560px] w-full object-contain`;
}

function StatusPill({
                        label,
                        kind,
                    }: {
    label: string;
    kind: "good" | "warn" | "bad";
}) {
    const className =
        kind === "good"
            ? "bg-emerald-500/10 text-emerald-300"
            : kind === "warn"
                ? "bg-amber-500/10 text-amber-300"
                : "bg-red-500/10 text-red-300";

    return (
        <div className={`rounded-full px-3 py-1.5 text-sm font-bold ${className}`}>
            {label}
        </div>
    );
}

function getVideoStatusKind(status: string): "good" | "warn" | "bad" {
    if (status === "playing" || status === "connected") {
        return "good";
    }

    if (status === "error" || status === "closed") {
        return "bad";
    }

    return "warn";
}

function getUserVideoStatus(status: string): string {
    if (status === "playing" || status === "connected") {
        return "Трансляция активна";
    }

    if (status === "error" || status === "closed") {
        return "Ошибка трансляции";
    }

    return "Подключение видео";
}

function isUiTarget(target: EventTarget | null): boolean {
    if (!(target instanceof HTMLElement)) {
        return false;
    }

    const tagName = target.tagName.toLowerCase();

    return (
        tagName === "input" ||
        tagName === "textarea" ||
        tagName === "select" ||
        tagName === "button" ||
        target.isContentEditable
    );
}

function clampNumber(value: number, min: number, max: number, fallback?: number): number {
    if (!Number.isFinite(value)) {
        return fallback ?? min;
    }

    return Math.max(min, Math.min(max, value));
}
