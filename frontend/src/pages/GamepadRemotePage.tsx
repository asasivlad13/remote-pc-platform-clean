import { useEffect, useRef, useState } from "react";
import type { PointerEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Maximize2 } from "lucide-react";
import { createMobileRemoteSocket } from "../features/mobileRemote/mobileRemoteSocket";
import {
    GAMEPAD_LAYOUT_STORAGE_KEY,
    loadGamepadLayout,
    type GamepadLayout,
    type GamepadLayoutItem,
} from "../features/mobileRemote/gamepadLayout";

type GamepadState = {
    lx: number;
    ly: number;
    rx: number;
    ry: number;
    lt: number;
    rt: number;
    a: boolean;
    b: boolean;
    x: boolean;
    y: boolean;
    lb: boolean;
    rb: boolean;
    back: boolean;
    start: boolean;
    guide: boolean;
    ls: boolean;
    rs: boolean;
    dpadUp: boolean;
    dpadDown: boolean;
    dpadLeft: boolean;
    dpadRight: boolean;
};

const initialState: GamepadState = {
    lx: 0,
    ly: 0,
    rx: 0,
    ry: 0,
    lt: 0,
    rt: 0,
    a: false,
    b: false,
    x: false,
    y: false,
    lb: false,
    rb: false,
    back: false,
    start: false,
    guide: false,
    ls: false,
    rs: false,
    dpadUp: false,
    dpadDown: false,
    dpadLeft: false,
    dpadRight: false,
};

export function GamepadRemotePage() {
    const navigate = useNavigate();
    const [params] = useSearchParams();

    const pcId = Number(params.get("pcId") || 0);
    const pcName = params.get("pcName") || "ПК";

    const remoteRef = useRef<ReturnType<typeof createMobileRemoteSocket> | null>(null);
    const stateRef = useRef<GamepadState>({ ...initialState });
    const timerRef = useRef<number | null>(null);

    const leftStickRef = useRef<HTMLDivElement | null>(null);
    const rightStickRef = useRef<HTMLDivElement | null>(null);

    const [layout, setLayout] = useState<GamepadLayout>(() => loadGamepadLayout());
    const [connectedText, setConnectedText] = useState("🟡 Подключение...");
    const [leftKnob, setLeftKnob] = useState({ x: 0, y: 0 });
    const [rightKnob, setRightKnob] = useState({ x: 0, y: 0 });
    const [pressed, setPressed] = useState<Record<string, boolean>>({});

    useEffect(() => {
        function reloadLayout(event?: StorageEvent) {
            if (event && event.key && event.key !== GAMEPAD_LAYOUT_STORAGE_KEY) {
                return;
            }

            setLayout(loadGamepadLayout());
        }

        window.addEventListener("storage", reloadLayout);

        return () => {
            window.removeEventListener("storage", reloadLayout);
        };
    }, []);

    useEffect(() => {
        if (!pcId) {
            navigate("/pcs");
            return;
        }

        const remote = createMobileRemoteSocket({
            pcId,
            pcName,
            mode: "Xbox-геймпад",
            onStatusChange: (status) => {
                if (status === "connected") {
                    setConnectedText(`🟢 Подключено к ${pcName}`);
                    remote.sendCommand("GAMEPAD_CONNECT");
                    startSending();
                    return;
                }

                if (status === "error") {
                    setConnectedText("🔴 Ошибка соединения");
                    return;
                }

                if (status === "closed") {
                    setConnectedText("🟡 Соединение закрыто");
                    stopSending();
                    return;
                }

                setConnectedText("🟡 Подключение...");
            },
        });

        remoteRef.current = remote;

        return () => {
            stopSending();
            remote.sendCommand("GAMEPAD_DISCONNECT");
            remote.close();
        };
    }, [pcId, pcName, navigate]);

    function startSending() {
        if (timerRef.current !== null) {
            return;
        }

        timerRef.current = window.setInterval(() => {
            remoteRef.current?.sendCommand(
                "GAMEPAD_STATE",
                stateRef.current as unknown as Record<string, unknown>,
            );
        }, 33);
    }

    function stopSending() {
        if (timerRef.current !== null) {
            window.clearInterval(timerRef.current);
            timerRef.current = null;
        }

        stateRef.current = { ...initialState };
        setPressed({});
        setLeftKnob({ x: 0, y: 0 });
        setRightKnob({ x: 0, y: 0 });
    }

    function requestFullscreenMode() {
        const element = document.documentElement;

        if (document.fullscreenElement) {
            return;
        }

        void element.requestFullscreen?.().catch(() => {});
    }

    function setButton(name: keyof GamepadState, value: boolean) {
        stateRef.current = {
            ...stateRef.current,
            [name]: value,
        };

        setPressed((current) => ({
            ...current,
            [name]: value,
        }));
    }

    function setTrigger(name: "lt" | "rt", value: number) {
        stateRef.current = {
            ...stateRef.current,
            [name]: value,
        };

        setPressed((current) => ({
            ...current,
            [name]: value > 0,
        }));
    }

    function updateStick(
        event: PointerEvent<HTMLDivElement>,
        side: "left" | "right",
    ) {
        const zone = side === "left" ? leftStickRef.current : rightStickRef.current;

        if (!zone) {
            return;
        }

        const rect = zone.getBoundingClientRect();
        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;

        let dx = event.clientX - centerX;
        let dy = event.clientY - centerY;

        const max = rect.width * 0.34;
        const distance = Math.hypot(dx, dy);

        if (distance > max) {
            dx = (dx / distance) * max;
            dy = (dy / distance) * max;
        }

        const normalizedX = Math.max(-1, Math.min(1, dx / max));
        const normalizedY = Math.max(-1, Math.min(1, dy / max));

        if (side === "left") {
            stateRef.current = {
                ...stateRef.current,
                lx: normalizedX,
                ly: normalizedY,
            };
            setLeftKnob({ x: dx, y: dy });
        } else {
            stateRef.current = {
                ...stateRef.current,
                rx: normalizedX,
                ry: normalizedY,
            };
            setRightKnob({ x: dx, y: dy });
        }
    }

    function resetStick(side: "left" | "right") {
        if (side === "left") {
            stateRef.current = {
                ...stateRef.current,
                lx: 0,
                ly: 0,
            };
            setLeftKnob({ x: 0, y: 0 });
        } else {
            stateRef.current = {
                ...stateRef.current,
                rx: 0,
                ry: 0,
            };
            setRightKnob({ x: 0, y: 0 });
        }
    }

    function goBack() {
        stopSending();
        remoteRef.current?.sendCommand("GAMEPAD_DISCONNECT");
        navigate(-1);
    }

    return (
        <main className="relative h-[100dvh] w-screen touch-none overflow-hidden bg-slate-950 text-white">
            <div className="fixed inset-0 z-[100] hidden items-center justify-center bg-slate-950 p-6 text-center portrait:flex">
                <div className="rounded-[30px] border border-white/10 bg-slate-900 p-8">
                    <div className="mb-4 text-6xl">📱↔️</div>
                    <h2 className="text-2xl font-black">Поверните телефон горизонтально</h2>
                    <p className="mt-3 text-base font-semibold text-slate-300">
                        Геймпад работает только в горизонтальном режиме.
                    </p>
                </div>
            </div>

            <header
                className="absolute left-2 right-2 top-1 z-30 grid items-center gap-2"
                style={{
                    gridTemplateColumns: "auto minmax(0, 1fr) auto",
                    height: "clamp(36px, 10dvh, 46px)",
                    paddingTop: "env(safe-area-inset-top)",
                }}
            >
                <button
                    type="button"
                    onClick={goBack}
                    className="h-full rounded-2xl border border-white/10 bg-white/10 px-3 text-[clamp(11px,2.7dvh,14px)] font-black active:scale-95"
                >
                    Назад
                </button>

                <div className="min-w-0">
                    <div className="truncate text-[clamp(13px,3.2dvh,16px)] font-black">
                        Xbox-геймпад
                    </div>
                    <div className="truncate text-[clamp(10px,2.5dvh,12px)] font-semibold text-slate-400">
                        {connectedText}
                    </div>
                </div>

                <button
                    type="button"
                    onClick={requestFullscreenMode}
                    className="flex h-full aspect-square items-center justify-center rounded-2xl border border-white/10 bg-white/10 active:scale-95"
                >
                    <Maximize2 size={18} />
                </button>
            </header>

            <section
                className="absolute inset-x-2 bottom-2 overflow-hidden rounded-[28px] border border-white/5 bg-slate-950"
                style={{
                    top: "calc(clamp(42px, 12dvh, 56px) + env(safe-area-inset-top))",
                    paddingBottom: "env(safe-area-inset-bottom)",
                }}
            >
                <ControlButton
                    item={layout.lb}
                    label="LB"
                    pressed={pressed.lb}
                    onDown={(value) => setButton("lb", value)}
                />

                <TriggerButton
                    item={layout.lt}
                    label="LT"
                    pressed={pressed.lt}
                    onChange={(value) => setTrigger("lt", value)}
                />

                <ControlButton
                    item={layout.rb}
                    label="RB"
                    pressed={pressed.rb}
                    onDown={(value) => setButton("rb", value)}
                />

                <TriggerButton
                    item={layout.rt}
                    label="RT"
                    pressed={pressed.rt}
                    onChange={(value) => setTrigger("rt", value)}
                />

                <ControlButton
                    item={layout.back}
                    label="Back"
                    pressed={pressed.back}
                    onDown={(value) => setButton("back", value)}
                />

                <ControlButton
                    item={layout.guide}
                    label="Xbox"
                    pressed={pressed.guide}
                    onDown={(value) => setButton("guide", value)}
                />

                <ControlButton
                    item={layout.start}
                    label="Start"
                    pressed={pressed.start}
                    onDown={(value) => setButton("start", value)}
                />

                <Stick
                    item={layout.leftStick}
                    refElement={leftStickRef}
                    knob={leftKnob}
                    side="left"
                    onMove={updateStick}
                    onEnd={resetStick}
                />

                <Stick
                    item={layout.rightStick}
                    refElement={rightStickRef}
                    knob={rightKnob}
                    side="right"
                    onMove={updateStick}
                    onEnd={resetStick}
                />

                <Dpad
                    item={layout.dpad}
                    pressed={pressed}
                    onDown={setButton}
                />

                <FaceButtons
                    item={layout.faceButtons}
                    pressed={pressed}
                    onDown={setButton}
                />

                <div
                    className="pointer-events-none absolute z-0 text-center text-[clamp(8px,2dvh,11px)] font-semibold leading-tight text-slate-600"
                    style={{
                        left: "50%",
                        top: "52%",
                        transform: "translate(-50%, -50%)",
                    }}
                >
                    GAMEPAD_STATE
                    <br />
                    30 FPS
                </div>
            </section>
        </main>
    );
}

function ControlButton({
                           item,
                           label,
                           pressed,
                           onDown,
                       }: {
    item: GamepadLayoutItem;
    label: string;
    pressed?: boolean;
    onDown: (value: boolean) => void;
}) {
    return (
        <button
            type="button"
            onPointerDown={(event) => {
                onDown(true);
                event.preventDefault();
            }}
            onPointerUp={(event) => {
                onDown(false);
                event.preventDefault();
            }}
            onPointerLeave={() => onDown(false)}
            onPointerCancel={() => onDown(false)}
            className={`absolute z-20 rounded-2xl border border-white/10 px-1 text-[clamp(9px,2.3dvh,14px)] font-black ${
                pressed ? "scale-95 bg-blue-500" : "bg-white/10"
            }`}
            style={layoutStyle(item, false)}
        >
            {label}
        </button>
    );
}

function TriggerButton({
                           item,
                           label,
                           pressed,
                           onChange,
                       }: {
    item: GamepadLayoutItem;
    label: string;
    pressed?: boolean;
    onChange: (value: number) => void;
}) {
    return (
        <button
            type="button"
            onPointerDown={(event) => {
                onChange(1);
                event.preventDefault();
            }}
            onPointerUp={(event) => {
                onChange(0);
                event.preventDefault();
            }}
            onPointerLeave={() => onChange(0)}
            onPointerCancel={() => onChange(0)}
            className={`absolute z-20 rounded-2xl border border-white/10 px-1 text-[clamp(9px,2.3dvh,14px)] font-black ${
                pressed ? "scale-95 bg-blue-500" : "bg-white/10"
            }`}
            style={layoutStyle(item, false)}
        >
            {label}
        </button>
    );
}

function Stick({
                   item,
                   refElement,
                   knob,
                   side,
                   onMove,
                   onEnd,
               }: {
    item: GamepadLayoutItem;
    refElement: React.RefObject<HTMLDivElement | null>;
    knob: { x: number; y: number };
    side: "left" | "right";
    onMove: (event: PointerEvent<HTMLDivElement>, side: "left" | "right") => void;
    onEnd: (side: "left" | "right") => void;
}) {
    return (
        <div
            ref={refElement}
            onPointerDown={(event) => {
                event.currentTarget.setPointerCapture(event.pointerId);
                onMove(event, side);
                event.preventDefault();
            }}
            onPointerMove={(event) => {
                if (event.buttons === 0) {
                    return;
                }

                onMove(event, side);
                event.preventDefault();
            }}
            onPointerUp={(event) => {
                onEnd(side);
                event.preventDefault();
            }}
            onPointerCancel={() => onEnd(side)}
            className="absolute z-10 rounded-full border-2 border-white/20 bg-white/10 shadow-2xl"
            style={layoutStyle(item, true)}
        >
            <div className="absolute inset-[21%] rounded-full border-2 border-dashed border-white/15" />
            <div
                style={{
                    transform: `translate(calc(-50% + ${knob.x}px), calc(-50% + ${knob.y}px))`,
                }}
                className={
                    side === "left"
                        ? "absolute left-1/2 top-1/2 h-[34%] w-[34%] rounded-full border-[clamp(2px,0.9dvh,4px)] border-white/80 bg-blue-500"
                        : "absolute left-1/2 top-1/2 h-[34%] w-[34%] rounded-full border-[clamp(2px,0.9dvh,4px)] border-white/80 bg-emerald-500"
                }
            />
        </div>
    );
}

function Dpad({
                  item,
                  pressed,
                  onDown,
              }: {
    item: GamepadLayoutItem;
    pressed: Record<string, boolean>;
    onDown: (name: keyof GamepadState, value: boolean) => void;
}) {
    return (
        <div
            className="absolute z-20 grid grid-cols-3 grid-rows-3 gap-1"
            style={layoutStyle(item, true)}
        >
            <ClusterButton extraClass="col-start-2 row-start-1" label="▲" name="dpadUp" pressed={pressed.dpadUp} onDown={onDown} />
            <ClusterButton extraClass="col-start-1 row-start-2" label="◀" name="dpadLeft" pressed={pressed.dpadLeft} onDown={onDown} />
            <ClusterButton extraClass="col-start-3 row-start-2" label="▶" name="dpadRight" pressed={pressed.dpadRight} onDown={onDown} />
            <ClusterButton extraClass="col-start-2 row-start-3" label="▼" name="dpadDown" pressed={pressed.dpadDown} onDown={onDown} />
        </div>
    );
}

function FaceButtons({
                         item,
                         pressed,
                         onDown,
                     }: {
    item: GamepadLayoutItem;
    pressed: Record<string, boolean>;
    onDown: (name: keyof GamepadState, value: boolean) => void;
}) {
    return (
        <div
            className="absolute z-20"
            style={layoutStyle(item, true)}
        >
            <FaceButton className="left-[34%] top-0 bg-amber-500" label="Y" name="y" pressed={pressed.y} onDown={onDown} />
            <FaceButton className="left-0 top-[34%] bg-blue-500" label="X" name="x" pressed={pressed.x} onDown={onDown} />
            <FaceButton className="right-0 top-[34%] bg-red-500" label="B" name="b" pressed={pressed.b} onDown={onDown} />
            <FaceButton className="bottom-0 left-[34%] bg-emerald-500" label="A" name="a" pressed={pressed.a} onDown={onDown} />
        </div>
    );
}

function ClusterButton({
                           label,
                           name,
                           pressed,
                           extraClass = "",
                           onDown,
                       }: {
    label: string;
    name: keyof GamepadState;
    pressed?: boolean;
    extraClass?: string;
    onDown: (name: keyof GamepadState, value: boolean) => void;
}) {
    return (
        <button
            type="button"
            onPointerDown={(event) => {
                onDown(name, true);
                event.preventDefault();
            }}
            onPointerUp={(event) => {
                onDown(name, false);
                event.preventDefault();
            }}
            onPointerLeave={() => onDown(name, false)}
            onPointerCancel={() => onDown(name, false)}
            className={`${extraClass} rounded-xl border border-white/10 px-1 text-[clamp(9px,2.3dvh,13px)] font-black ${
                pressed ? "scale-95 bg-blue-500" : "bg-white/10"
            }`}
        >
            {label}
        </button>
    );
}

function FaceButton({
                        label,
                        name,
                        pressed,
                        className,
                        onDown,
                    }: {
    label: string;
    name: keyof GamepadState;
    pressed?: boolean;
    className: string;
    onDown: (name: keyof GamepadState, value: boolean) => void;
}) {
    return (
        <button
            type="button"
            onPointerDown={(event) => {
                onDown(name, true);
                event.preventDefault();
            }}
            onPointerUp={(event) => {
                onDown(name, false);
                event.preventDefault();
            }}
            onPointerLeave={() => onDown(name, false)}
            onPointerCancel={() => onDown(name, false)}
            className={`absolute h-[32%] w-[32%] rounded-full border-[clamp(2px,0.9dvh,4px)] border-white/70 text-[clamp(14px,4dvh,22px)] font-black shadow-2xl ${
                pressed ? "scale-90 brightness-125" : ""
            } ${className}`}
        >
            {label}
        </button>
    );
}

function layoutStyle(item: GamepadLayoutItem, square: boolean): React.CSSProperties {
    return {
        left: `${item.x}%`,
        top: `${item.y}%`,
        width: `${item.w}%`,
        height: square ? undefined : `${item.h}%`,
        aspectRatio: square ? "1 / 1" : undefined,
        transform: "translate(-50%, -50%)",
    };
}
