import { useEffect, useMemo, useRef, useState } from "react";
import type { PointerEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ArrowLeft, HelpCircle } from "lucide-react";
import {
    createMobileRemoteSocket,
    type MobileRemoteSocketStatus,
} from "../features/mobileRemote/mobileRemoteSocket";

type Mode = "touch" | "scroll";

function getApiBaseUrl(): string {
    const explicitUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

    if (explicitUrl && explicitUrl.trim()) {
        return explicitUrl.trim();
    }

    return `${window.location.protocol}//${window.location.hostname}:8080`;
}

export function TouchpadRemotePage() {
    const navigate = useNavigate();
    const [params] = useSearchParams();

    const pcId = Number(params.get("pcId") || 0);
    const pcName = params.get("pcName") || "ПК";

    const padRef = useRef<HTMLDivElement | null>(null);
    const remoteRef = useRef<ReturnType<typeof createMobileRemoteSocket> | null>(null);

    const pointerActiveRef = useRef(false);
    const lastClientXRef = useRef(0);
    const lastClientYRef = useRef(0);

    const [status, setStatus] = useState<MobileRemoteSocketStatus>("connecting");
    const [mode, setMode] = useState<Mode>("touch");
    const [helpOpen, setHelpOpen] = useState(false);

    const [screenSize, setScreenSize] = useState({
        width: 1920,
        height: 1080,
    });

    const [virtualPoint, setVirtualPoint] = useState({
        x: 960,
        y: 540,
    });

    const screenSizeRef = useRef(screenSize);
    const virtualPointRef = useRef(virtualPoint);

    useEffect(() => {
        screenSizeRef.current = screenSize;
    }, [screenSize]);

    useEffect(() => {
        virtualPointRef.current = virtualPoint;
    }, [virtualPoint]);

    const statusText = useMemo(() => {
        if (status === "connected") return `🟢 ${pcName}`;
        if (status === "error") return "🔴 Ошибка соединения";
        if (status === "closed") return "🟡 Соединение закрыто";
        return "🟡 Подключение...";
    }, [status, pcName]);

    useEffect(() => {
        if (!pcId) {
            navigate("/pcs");
            return;
        }

        void loadPcInfo();

        const remote = createMobileRemoteSocket({
            pcId,
            pcName,
            mode: "Тачпад",
            onStatusChange: setStatus,
        });

        remoteRef.current = remote;

        return () => {
            remote.close();
        };
    }, [pcId, pcName, navigate]);

    async function loadPcInfo() {
        try {
            const token = localStorage.getItem("token") || "";
            const response = await fetch(`${getApiBaseUrl()}/pcs/${pcId}`, {
                headers: token ? { Authorization: `Bearer ${token}` } : {},
            });

            if (!response.ok) {
                return;
            }

            const pc = await response.json();

            const width = Number(pc.screenWidth || 1920);
            const height = Number(pc.screenHeight || 1080);

            setScreenSize({ width, height });
            setVirtualPoint({ x: width / 2, y: height / 2 });
            sendMouseMove(width / 2, height / 2, width, height);
        } catch {
            // ignore
        }
    }

    function sendMouseMove(
        x: number,
        y: number,
        customWidth?: number,
        customHeight?: number,
    ) {
        const width = customWidth || screenSizeRef.current.width;
        const height = customHeight || screenSizeRef.current.height;

        const clampedX = Math.max(0, Math.min(width - 1, x));
        const clampedY = Math.max(0, Math.min(height - 1, y));

        setVirtualPoint({
            x: clampedX,
            y: clampedY,
        });

        remoteRef.current?.sendCommand("MOUSE_MOVE", {
            x: Math.round(clampedX),
            y: Math.round(clampedY),
            screenWidth: width,
            screenHeight: height,
            relativeX: clampedX / width,
            relativeY: clampedY / height,
        });
    }

    function clickMouse(button: number) {
        const point = virtualPointRef.current;
        const size = screenSizeRef.current;

        remoteRef.current?.sendCommand("MOUSE_CLICK", {
            x: Math.round(point.x),
            y: Math.round(point.y),
            button,
            screenWidth: size.width,
            screenHeight: size.height,
            relativeX: point.x / size.width,
            relativeY: point.y / size.height,
        });
    }

    function scrollBy(delta: number) {
        if (delta === 0) {
            return;
        }

        remoteRef.current?.sendCommand("MOUSE_WHEEL", {
            delta,
        });
    }

    function pointerDown(event: PointerEvent<HTMLDivElement>) {
        pointerActiveRef.current = true;
        event.currentTarget.setPointerCapture(event.pointerId);

        lastClientXRef.current = event.clientX;
        lastClientYRef.current = event.clientY;

        event.preventDefault();
    }

    function pointerMove(event: PointerEvent<HTMLDivElement>) {
        if (!pointerActiveRef.current) {
            return;
        }

        const pad = padRef.current;

        if (!pad) {
            return;
        }

        const dx = event.clientX - lastClientXRef.current;
        const dy = event.clientY - lastClientYRef.current;

        lastClientXRef.current = event.clientX;
        lastClientYRef.current = event.clientY;

        if (mode === "scroll") {
            scrollBy(Math.round(-dy / 14));
            event.preventDefault();
            return;
        }

        const size = screenSizeRef.current;
        const point = virtualPointRef.current;

        const multiplier = 1.5;
        const remoteDx = dx * multiplier * (size.width / Math.max(1, pad.clientWidth)) * 2.2;
        const remoteDy = dy * multiplier * (size.height / Math.max(1, pad.clientHeight)) * 2.2;

        sendMouseMove(point.x + remoteDx, point.y + remoteDy);
        event.preventDefault();
    }

    function pointerUp(event: PointerEvent<HTMLDivElement>) {
        pointerActiveRef.current = false;

        try {
            event.currentTarget.releasePointerCapture(event.pointerId);
        } catch {
            // ignore
        }

        event.preventDefault();
    }

    const dotStyle = {
        left: `${(virtualPoint.x / screenSize.width) * 100}%`,
        top: `${(virtualPoint.y / screenSize.height) * 100}%`,
    };

    return (
        <main className="h-screen overflow-hidden bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 p-2 text-white">
            <section className="grid h-full grid-rows-[auto_1fr_auto] gap-2">
                <header className="grid min-h-[54px] grid-cols-[auto_1fr_auto] items-center gap-2">
                    <button
                        type="button"
                        onClick={() => navigate(-1)}
                        className="h-12 rounded-2xl border border-white/10 bg-white/10 px-4 text-sm font-black active:scale-95"
                    >
                        <ArrowLeft size={19} />
                    </button>

                    <div className="min-w-0">
                        <h1 className="truncate text-xl font-black">
                            Тачпад
                        </h1>
                        <p className="truncate text-xs font-semibold text-slate-300">
                            {statusText}
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={() => setHelpOpen(true)}
                        className="h-12 rounded-2xl border border-white/10 bg-white/10 px-4 text-sm font-black active:scale-95"
                    >
                        <HelpCircle size={19} />
                    </button>
                </header>

                <div
                    ref={padRef}
                    onPointerDown={pointerDown}
                    onPointerMove={pointerMove}
                    onPointerUp={pointerUp}
                    onPointerCancel={() => {
                        pointerActiveRef.current = false;
                    }}
                    className={
                        mode === "scroll"
                            ? "relative min-h-0 touch-none overflow-hidden rounded-[28px] border border-emerald-400/30 bg-slate-950/80"
                            : "relative min-h-0 touch-none overflow-hidden rounded-[28px] border border-blue-400/30 bg-slate-950/80"
                    }
                >
                    <div className="pointer-events-none absolute left-5 top-5 text-sm font-black text-white/30">
                        {mode === "scroll"
                            ? "Scroll: двигайте пальцем вверх/вниз"
                            : "Водите пальцем — курсор двигается на ПК"}
                    </div>

                    <div className="pointer-events-none absolute bottom-5 left-5 rounded-2xl border border-white/10 bg-white/5 px-4 py-2 text-xs font-black text-white/35">
                        Это отдельный тачпад-пульт, не трансляция экрана
                    </div>

                    <div
                        style={dotStyle}
                        className="pointer-events-none absolute h-7 w-7 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-emerald-500 shadow-xl"
                    />
                </div>

                <footer className="grid gap-2 pb-[env(safe-area-inset-bottom)]">
                    <div className="grid grid-cols-2 gap-2">
                        <button
                            type="button"
                            onClick={() => setMode("touch")}
                            className={
                                mode === "touch"
                                    ? "h-12 rounded-2xl bg-emerald-600 text-sm font-black"
                                    : "h-12 rounded-2xl border border-white/10 bg-white/10 text-sm font-black"
                            }
                        >
                            ☝️ Тачпад
                        </button>

                        <button
                            type="button"
                            onClick={() => setMode("scroll")}
                            className={
                                mode === "scroll"
                                    ? "h-12 rounded-2xl bg-emerald-600 text-sm font-black"
                                    : "h-12 rounded-2xl border border-white/10 bg-white/10 text-sm font-black"
                            }
                        >
                            🖱 Scroll
                        </button>
                    </div>

                    <div className="grid grid-cols-3 gap-2">
                        <button
                            type="button"
                            onClick={() => clickMouse(1)}
                            className="h-14 rounded-2xl bg-blue-600 text-sm font-black active:scale-95"
                        >
                            ЛКМ
                        </button>

                        <button
                            type="button"
                            onClick={() => clickMouse(3)}
                            className="h-14 rounded-2xl border border-white/10 bg-white/10 text-sm font-black active:scale-95"
                        >
                            ПКМ
                        </button>

                        <button
                            type="button"
                            onClick={() => {
                                clickMouse(1);
                                window.setTimeout(() => clickMouse(1), 90);
                            }}
                            className="h-14 rounded-2xl border border-white/10 bg-white/10 text-sm font-black active:scale-95"
                        >
                            2× ЛКМ
                        </button>
                    </div>
                </footer>
            </section>

            {helpOpen && (
                <div className="fixed inset-3 z-50 overflow-auto rounded-[28px] border border-blue-400/30 bg-slate-950 p-6 shadow-2xl">
                    <h2 className="text-2xl font-black">
                        Как работает тачпад
                    </h2>

                    <div className="mt-5 space-y-4 text-sm font-semibold leading-7 text-slate-300">
                        <p>
                            Этот режим относится к многофункциональному пульту. Он не показывает трансляцию экрана, а работает как обычный тачпад ноутбука.
                        </p>

                        <p>
                            В режиме <b className="text-white">Тачпад</b> двигайте пальцем по большой области. Курсор на удалённом ПК будет двигаться относительно движения пальца.
                        </p>

                        <p>
                            В режиме <b className="text-white">Scroll</b> движение пальца вверх/вниз отправляет прокрутку колеса мыши.
                        </p>

                        <p>
                            Кнопки снизу отправляют ЛКМ, ПКМ и двойной ЛКМ.
                        </p>

                        <p className="rounded-2xl border border-amber-400/20 bg-amber-500/10 px-4 py-3 text-amber-200">
                            Управление поверх реальной трансляции экрана находится в личном удалённом доступе, когда на телефоне открыта трансляция и включён Control ON.
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={() => setHelpOpen(false)}
                        className="mt-6 h-12 w-full rounded-2xl bg-blue-600 text-sm font-black active:scale-95"
                    >
                        Понятно
                    </button>
                </div>
            )}
        </main>
    );
}
