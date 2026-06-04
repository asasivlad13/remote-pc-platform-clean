import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ArrowLeft, Volume1, Volume2, VolumeX } from "lucide-react";
import {
    createMobileRemoteSocket,
    type MobileRemoteSocketStatus,
} from "../features/mobileRemote/mobileRemoteSocket";

export function PresentationRemotePage() {
    const navigate = useNavigate();
    const [params] = useSearchParams();

    const pcId = Number(params.get("pcId") || 0);
    const pcName = params.get("pcName") || "ПК";

    const remoteRef = useRef<ReturnType<typeof createMobileRemoteSocket> | null>(null);
    const [status, setStatus] = useState<MobileRemoteSocketStatus>("connecting");

    const statusText = useMemo(() => {
        if (status === "connected") return `🟢 Подключено к ${pcName}`;
        if (status === "error") return "🔴 Ошибка соединения";
        if (status === "closed") return "🟡 Соединение закрыто";
        return "🟡 Подключение...";
    }, [status, pcName]);

    useEffect(() => {
        if (!pcId) {
            navigate("/pcs");
            return;
        }

        const remote = createMobileRemoteSocket({
            pcId,
            pcName,
            mode: "Пульт презентации",
            onStatusChange: setStatus,
        });

        remoteRef.current = remote;

        return () => {
            remote.close();
        };
    }, [pcId, pcName, navigate]);

    function sendKey(keyCode: number, modifiers: Record<string, boolean> = {}) {
        const remote = remoteRef.current;

        if (!remote) {
            alert("Соединение ещё не установлено");
            return;
        }

        const payload = {
            keyCode,
            ctrl: modifiers.ctrl === true,
            alt: modifiers.alt === true,
            shift: modifiers.shift === true,
        };

        const ok = remote.sendCommand("KEY_PRESS", payload);

        window.setTimeout(() => {
            remote.sendCommand("KEY_RELEASE", payload);
        }, 60);

        if (!ok) {
            alert("Соединение ещё не установлено");
        }
    }

    return (
        <main className="min-h-screen bg-slate-950 p-3 text-white">
            <section className="mx-auto grid min-h-[calc(100vh-24px)] max-w-4xl gap-3 md:grid-cols-[1.1fr_1fr]">
                <div className="flex flex-col rounded-[34px] border border-white/10 bg-white/10 p-5 shadow-2xl shadow-black/30 backdrop-blur">
                    <header className="mb-5">
                        <button
                            type="button"
                            onClick={() => navigate(-1)}
                            className="mb-4 inline-flex h-12 items-center gap-2 rounded-2xl border border-white/10 bg-white/10 px-5 text-sm font-black text-white active:scale-95"
                        >
                            <ArrowLeft size={20} />
                            Назад
                        </button>

                        <h1 className="text-3xl font-black">
                            Пульт презентации
                        </h1>

                        <p className="mt-2 text-sm font-semibold text-slate-300">
                            {statusText}
                        </p>
                    </header>

                    <div className="grid flex-1 grid-cols-2 gap-3">
                        <BigButton label="Назад" icon="⬅️" onClick={() => sendKey(37)} />
                        <BigButton primary label="Вперёд" icon="➡️" onClick={() => sendKey(39)} />
                    </div>
                </div>

                <div className="grid gap-3">
                    <Panel title="Показ">
                        <div className="grid grid-cols-2 gap-3">
                            <ActionButton label="▶ С начала" onClick={() => sendKey(116)} />
                            <ActionButton label="🔄 С текущего" onClick={() => sendKey(116, { shift: true })} />
                            <ActionButton label="⏹ Выход" onClick={() => sendKey(27)} />
                            <ActionButton label="⚫ Чёрный" onClick={() => sendKey(66)} />
                            <ActionButton label="⚪ Белый" onClick={() => sendKey(87)} />
                            <ActionButton label="⏮ В начало" onClick={() => sendKey(36)} />
                        </div>
                    </Panel>

                    <Panel title="Звук">
                        <div className="grid grid-cols-3 gap-3">
                            <ActionButton icon={<Volume1 size={22} />} label="Тише" onClick={() => sendKey(174)} />
                            <ActionButton icon={<Volume2 size={22} />} label="Громче" onClick={() => sendKey(175)} />
                            <ActionButton icon={<VolumeX size={22} />} label="Mute" onClick={() => sendKey(173)} />
                        </div>
                    </Panel>

                    <Panel title="Подсказка">
                        <div className="space-y-2 text-sm font-semibold leading-6 text-slate-300">
                            <p>Открой презентацию на удалённом ПК.</p>
                            <p>Сделай окно PowerPoint, PDF или браузера активным.</p>
                            <p>Кнопки работают как обычные клавиши на клавиатуре.</p>
                        </div>
                    </Panel>
                </div>
            </section>
        </main>
    );
}

function BigButton({
                       label,
                       icon,
                       primary,
                       onClick,
                   }: {
    label: string;
    icon: string;
    primary?: boolean;
    onClick: () => void;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={
                primary
                    ? "flex min-h-[180px] flex-col items-center justify-center gap-4 rounded-[32px] border border-blue-300/30 bg-blue-600/60 text-2xl font-black text-white shadow-xl shadow-blue-600/20 active:scale-[0.98]"
                    : "flex min-h-[180px] flex-col items-center justify-center gap-4 rounded-[32px] border border-white/10 bg-white/10 text-2xl font-black text-white active:scale-[0.98]"
            }
        >
            <span className="text-5xl">{icon}</span>
            {label}
        </button>
    );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
    return (
        <section className="rounded-[28px] border border-white/10 bg-white/10 p-5 shadow-xl shadow-black/20 backdrop-blur">
            <h2 className="mb-4 text-xl font-black text-white">{title}</h2>
            {children}
        </section>
    );
}

function ActionButton({
                          label,
                          icon,
                          onClick,
                      }: {
    label: string;
    icon?: React.ReactNode;
    onClick: () => void;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className="flex min-h-[68px] items-center justify-center gap-2 rounded-2xl border border-white/10 bg-white/10 px-3 text-sm font-black text-white active:scale-[0.98]"
        >
            {icon}
            {label}
        </button>
    );
}
