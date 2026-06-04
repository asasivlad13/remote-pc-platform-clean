import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
    ArrowLeft,
    Check,
    ChevronDown,
    FolderOpen,
    Maximize2,
    Monitor,
    MousePointer2,
    Power,
} from "lucide-react";
import { getPcById } from "../features/pcs/pcApi";
import type { PcDetailsResponse } from "../features/pcs/pcTypes";
import { useRemoteSocket } from "../features/remote/useRemoteSocket";
import { RemoteFileTransferModal } from "../features/remote/components/RemoteFileTransferModal";
import {
    RemoteScreenPanel,
    type RemoteScreenDisplayMode,
} from "../features/remote/components/RemoteScreenPanel";

const DISPLAY_MODE_OPTIONS: Array<{
    value: RemoteScreenDisplayMode;
    label: string;
    description: string;
}> = [
    {
        value: "fit",
        label: "По размеру окна",
        description: "Экран полностью помещается в область просмотра.",
    },
    {
        value: "fill",
        label: "Заполнить область",
        description: "Изображение заполняет всю область, края могут обрезаться.",
    },
    {
        value: "original",
        label: "Оригинальный размер",
        description: "Изображение показывается без растягивания.",
    },
];

const RESOLUTION_OPTIONS = [
    {
        value: "854x480",
        label: "480p",
        description: "Минимальная нагрузка, удобно для слабой сети.",
    },
    {
        value: "1280x720",
        label: "720p",
        description: "Оптимально для стабильной работы.",
    },
    {
        value: "1920x1080",
        label: "1080p",
        description: "Чёткая картинка для обычного монитора.",
    },
    {
        value: "2560x1440",
        label: "1440p",
        description: "Высокая детализация, нужна хорошая сеть.",
    },
    {
        value: "3840x2160",
        label: "4K",
        description: "Максимальное качество, высокая нагрузка.",
    },
];

type RemoteCommandData = {
    [key: string]: unknown;
};

export function RemoteAccessPage() {
    const navigate = useNavigate();
    const { pcId } = useParams();
    const [searchParams] = useSearchParams();

    const [pc, setPc] = useState<PcDetailsResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [commandMessage, setCommandMessage] = useState("");
    const [fileModalOpen, setFileModalOpen] = useState(false);
    const [screenUploadProgress, setScreenUploadProgress] = useState<number | null>(null);
    const [displayMode, setDisplayMode] = useState<RemoteScreenDisplayMode>("fit");
    const [controlEnabled, setControlEnabled] = useState(false);
    const [selectedResolution, setSelectedResolution] = useState("1280x720");

    const {
        status: socketStatus,
        sendCommand,
        sendSettings,
    } = useRemoteSocket();

    const pcNameFromUrl = searchParams.get("pcName");
    const profile = searchParams.get("profile") || "personal";

    const pcName = pc?.name || pcNameFromUrl || "Remote PC";
    const numericPcId = Number(pcId);

    useEffect(() => {
        async function loadPc() {
            if (!pcId) {
                setError("ПК не выбран");
                setLoading(false);
                return;
            }

            try {
                const result = await getPcById(Number(pcId));
                setPc(result);

                if (result.screenWidth && result.screenHeight) {
                    const currentResolution = `${result.screenWidth}x${result.screenHeight}`;
                    const exists = RESOLUTION_OPTIONS.some(
                        (option) => option.value === currentResolution,
                    );

                    if (exists) {
                        setSelectedResolution(currentResolution);
                    }
                }
            } catch {
                setError("Не удалось загрузить информацию о ПК");
            } finally {
                setLoading(false);
            }
        }

        void loadPc();
    }, [pcId]);

    function handleDisconnect() {
        navigate("/pcs");
    }

    function handleFullscreen() {
        const screenElement = document.getElementById("remote-screen-fullscreen-target");

        if (!screenElement) {
            setCommandMessage("Область трансляции не найдена");
            return;
        }

        if (!document.fullscreenElement) {
            void screenElement.requestFullscreen();
            return;
        }

        void document.exitFullscreen();
    }

    const sendRemoteCommand = useCallback(
        (action: string, data: RemoteCommandData = {}) => {
            if (!numericPcId || Number.isNaN(numericPcId)) {
                setCommandMessage("ПК не выбран");
                return;
            }

            try {
                sendCommand({
                    pcId: numericPcId,
                    action,
                    profile,
                    ...data,
                });
            } catch {
                setCommandMessage("WebSocket не подключён. Команда не отправлена.");
            }
        },
        [numericPcId, profile, sendCommand],
    );

    function toggleControl() {
        setControlEnabled((current) => {
            const next = !current;

            setCommandMessage(
                next
                    ? "Управление включено. Двигайте мышью по экрану, кликайте и вводите с клавиатуры."
                    : "Управление выключено. Сейчас доступен только просмотр.",
            );

            setTimeout(() => {
                setCommandMessage("");
            }, 3500);

            return next;
        });
    }

    function applyResolution() {
        if (!numericPcId || Number.isNaN(numericPcId)) {
            setCommandMessage("ПК не выбран");
            return;
        }

        try {
            sendSettings({
                pcId: numericPcId,
                resolution: selectedResolution,
            });

            setCommandMessage(`Применяем разрешение ${selectedResolution}. Трансляция может переподключиться.`);

            setTimeout(() => {
                setCommandMessage("");
            }, 6000);
        } catch {
            setCommandMessage("WebSocket не подключён. Настройки не отправлены.");
        }
    }

    return (
        <main className="h-screen overflow-hidden bg-[#f8fafc] text-slate-950 max-xl:h-auto max-xl:overflow-visible">
            <header className="border-b border-slate-200 bg-white px-6 py-4">
                <div className="flex flex-wrap items-center justify-between gap-4">
                    <div className="flex items-center gap-4">
                        <button
                            type="button"
                            onClick={() => navigate("/pcs")}
                            className="flex h-12 w-12 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-600 transition hover:bg-slate-50"
                        >
                            <ArrowLeft size={22} />
                        </button>

                        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-600 text-white shadow-lg shadow-blue-600/20">
                            <Monitor size={24} />
                        </div>

                        <div>
                            <h1 className="text-xl font-black text-slate-950">
                                {pcName}
                            </h1>
                            <p className="text-sm font-medium text-slate-500">
                                Личный удалённый доступ
                            </p>
                        </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-3">
                        <SocketBadge status={socketStatus} />
                        <StatusBadge status={pc?.status || null} />

                        <button
                            type="button"
                            className={
                                controlEnabled
                                    ? "top-action-button active-control"
                                    : "top-action-button"
                            }
                            onClick={toggleControl}
                        >
                            <MousePointer2 size={18} />
                            {controlEnabled ? "Control ON" : "Control OFF"}
                        </button>

                        <button
                            type="button"
                            className="top-action-button"
                            onClick={() => setFileModalOpen(true)}
                        >
                            <FolderOpen size={18} />
                            Files
                        </button>

                        <button
                            type="button"
                            className="top-action-button"
                            onClick={handleFullscreen}
                        >
                            <Maximize2 size={18} />
                            Fullscreen
                        </button>

                        <button
                            type="button"
                            className="top-action-button danger"
                            onClick={handleDisconnect}
                        >
                            <Power size={18} />
                            Disconnect
                        </button>
                    </div>
                </div>
            </header>

            <section className="grid h-[calc(100vh-81px)] grid-cols-[minmax(0,1fr)_360px] gap-6 overflow-hidden p-6 max-xl:h-auto max-xl:grid-cols-1 max-xl:overflow-visible">
                <div className="min-h-0">
                    <RemoteScreenPanel
                        pc={pc}
                        pcId={numericPcId}
                        pcName={pcName}
                        loading={loading}
                        error={error}
                        socketStatus={socketStatus}
                        commandMessage={commandMessage}
                        screenUploadProgress={screenUploadProgress}
                        displayMode={displayMode}
                        controlEnabled={controlEnabled}
                        onRemoteCommand={sendRemoteCommand}
                        onCommandMessageChange={setCommandMessage}
                        onScreenUploadProgressChange={setScreenUploadProgress}
                    />
                </div>

                <aside className="grid max-h-full content-start gap-5 overflow-y-auto pr-1 max-xl:max-h-none max-xl:overflow-visible max-xl:pr-0">
                    <section className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                        <h2 className="text-lg font-black">Информация о ПК</h2>

                        <div className="mt-5 grid gap-4">
                            <InfoRow label="ID" value={pc?.id ?? "—"} />
                            <InfoRow label="Name" value={pc?.name ?? pcName} />
                            <InfoRow label="Status" value={pc?.status ?? "—"} />
                            <InfoRow
                                label="Screen"
                                value={
                                    pc?.screenWidth && pc?.screenHeight
                                        ? `${pc.screenWidth} × ${pc.screenHeight}`
                                        : "—"
                                }
                            />
                        </div>
                    </section>

                    <section className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                        <h2 className="text-lg font-black">Разрешение трансляции</h2>

                        <p className="mt-2 text-sm leading-6 text-slate-500">
                            Меняет качество видеопотока. После применения трансляция может кратко переподключиться.
                        </p>

                        <div className="mt-5">
                            <ResolutionDropdown
                                value={selectedResolution}
                                onChange={setSelectedResolution}
                            />
                        </div>

                        <button
                            type="button"
                            onClick={applyResolution}
                            className="mt-5 inline-flex h-12 w-full items-center justify-center rounded-2xl bg-blue-600 px-5 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700"
                        >
                            Применить разрешение
                        </button>
                    </section>

                    <section className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                        <h2 className="text-lg font-black">Отображение экрана</h2>

                        <p className="mt-2 text-sm leading-6 text-slate-500">
                            Меняет только вид изображения в браузере.
                        </p>

                        <div className="mt-5 grid gap-3">
                            {DISPLAY_MODE_OPTIONS.map((option) => (
                                <button
                                    key={option.value}
                                    type="button"
                                    onClick={() => setDisplayMode(option.value)}
                                    className={
                                        displayMode === option.value
                                            ? "rounded-2xl border-2 border-blue-500 bg-blue-50 p-4 text-left"
                                            : "rounded-2xl border border-slate-200 bg-white p-4 text-left transition hover:bg-slate-50"
                                    }
                                >
                                    <div
                                        className={
                                            displayMode === option.value
                                                ? "font-black text-blue-700"
                                                : "font-black text-slate-900"
                                        }
                                    >
                                        {option.label}
                                    </div>

                                    <div className="mt-1 text-sm leading-5 text-slate-500">
                                        {option.description}
                                    </div>
                                </button>
                            ))}
                        </div>
                    </section>
                </aside>
            </section>

            {!Number.isNaN(numericPcId) && (
                <RemoteFileTransferModal
                    open={fileModalOpen}
                    pcId={numericPcId}
                    pcName={pcName}
                    onClose={() => setFileModalOpen(false)}
                />
            )}
        </main>
    );
}

function ResolutionDropdown({
                                value,
                                onChange,
                            }: {
    value: string;
    onChange: (value: string) => void;
}) {
    const [open, setOpen] = useState(false);
    const selected = RESOLUTION_OPTIONS.find((option) => option.value === value) || RESOLUTION_OPTIONS[1];

    return (
        <div className="relative">
            <button
                type="button"
                onClick={() => setOpen((current) => !current)}
                className="flex min-h-14 w-full items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-left transition hover:border-blue-300 hover:bg-blue-50"
            >
                <div>
                    <div className="font-black text-slate-950">
                        {selected.label} — {selected.value}
                    </div>
                    <div className="mt-1 text-sm text-slate-500">
                        {selected.description}
                    </div>
                </div>

                <ChevronDown
                    size={20}
                    className={open ? "shrink-0 rotate-180 text-blue-600 transition" : "shrink-0 text-slate-400 transition"}
                />
            </button>

            {open && (
                <div className="absolute left-0 right-0 top-[calc(100%+10px)] z-40 overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-2xl">
                    {RESOLUTION_OPTIONS.map((option) => {
                        const active = option.value === value;

                        return (
                            <button
                                key={option.value}
                                type="button"
                                onClick={() => {
                                    onChange(option.value);
                                    setOpen(false);
                                }}
                                className={
                                    active
                                        ? "flex w-full items-start justify-between gap-3 bg-blue-50 px-4 py-4 text-left"
                                        : "flex w-full items-start justify-between gap-3 px-4 py-4 text-left transition hover:bg-slate-50"
                                }
                            >
                                <div>
                                    <div className={active ? "font-black text-blue-700" : "font-black text-slate-900"}>
                                        {option.label} — {option.value}
                                    </div>

                                    <div className="mt-1 text-sm leading-5 text-slate-500">
                                        {option.description}
                                    </div>
                                </div>

                                {active && <Check size={20} className="mt-1 shrink-0 text-blue-600" />}
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

function SocketBadge({ status }: { status: string }) {
    if (status === "connected") {
        return (
            <span className="rounded-full bg-blue-50 px-4 py-2 text-sm font-black text-blue-700">
                ● Подключено
            </span>
        );
    }

    if (status === "connecting") {
        return (
            <span className="rounded-full bg-amber-50 px-4 py-2 text-sm font-black text-amber-700">
                ● Соединение
            </span>
        );
    }

    return (
        <span className="rounded-full bg-red-50 px-4 py-2 text-sm font-black text-red-700">
            ● Нет связи
        </span>
    );
}

function StatusBadge({ status }: { status: string | null }) {
    const normalized = String(status || "UNKNOWN").toUpperCase();

    if (normalized === "ONLINE") {
        return (
            <span className="rounded-full bg-emerald-50 px-4 py-2 text-sm font-black text-emerald-700">
                ● Online
            </span>
        );
    }

    if (normalized === "SLEEP" || normalized === "SOFT_SLEEP") {
        return (
            <span className="rounded-full bg-amber-50 px-4 py-2 text-sm font-black text-amber-700">
                ● Sleep
            </span>
        );
    }

    return (
        <span className="rounded-full bg-slate-100 px-4 py-2 text-sm font-black text-slate-500">
            ● Offline
        </span>
    );
}

function InfoRow({ label, value }: { label: string; value: ReactNode }) {
    return (
        <div className="rounded-2xl bg-slate-50 px-4 py-3">
            <div className="text-xs font-black uppercase tracking-wide text-slate-400">
                {label}
            </div>

            <div className="mt-1 break-words text-sm font-bold text-slate-800">
                {value}
            </div>
        </div>
    );
}