import { useEffect, useMemo, useRef, useState } from "react";
import type { RefObject } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
    ArrowLeft,
    CheckCircle2,
    ClipboardCopy,
    Download,
    FileText,
    Headphones,
    Loader2,
    LogOut,
    Monitor,
    RefreshCw,
    Send,
    Upload,
    X,
} from "lucide-react";
import type { PcDetailsResponse } from "../features/pcs/pcTypes";
import {
    downloadSupportFile,
    finishSupportSession,
    getSupportChatMessages,
    getSupportFiles,
    getSupportSession,
    requestSupportControl,
    sendSupportChatMessage,
    uploadSupportFile,
} from "../features/support/supportApi";
import type {
    SupportChatMessageResponse,
    SupportFileResponse,
    SupportSessionResponse,
} from "../features/support/supportTypes";
import { EducationStreamPanel } from "../features/education/components/EducationStreamPanel";

export function SupportOperatorPage() {
    const navigate = useNavigate();
    const { sessionCode = "" } = useParams();

    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const endedTimerRef = useRef<number | null>(null);

    const currentUsername = localStorage.getItem("username") || "";

    const [session, setSession] = useState<SupportSessionResponse | null>(null);
    const [messages, setMessages] = useState<SupportChatMessageResponse[]>([]);
    const [files, setFiles] = useState<SupportFileResponse[]>([]);
    const [chatText, setChatText] = useState("");
    const [selectedQuality, setSelectedQuality] = useState("1280x720");
    const [controlModeEnabled, setControlModeEnabled] = useState(false);
    const [filesOpen, setFilesOpen] = useState(false);
    const [sessionEnded, setSessionEnded] = useState(false);
    const [notice, setNotice] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(true);

    const clientPc = useMemo(() => buildSupportClientPc(session), [session]);
    const clientConnected = session?.status === "ACTIVE" && Boolean(session.clientId && session.clientPcId);
    const controlAllowed = session?.controlAllowed === true;
    const controlRequested = session?.controlRequested === true && !controlAllowed;
    const activeControl = controlAllowed && controlModeEnabled;

    useEffect(() => {
        void loadAll();

        const timer = window.setInterval(() => {
            void loadAll(false);
        }, 2500);

        return () => {
            window.clearInterval(timer);

            if (endedTimerRef.current !== null) {
                window.clearTimeout(endedTimerRef.current);
            }
        };
    }, [sessionCode]);

    useEffect(() => {
        if (!controlAllowed && controlModeEnabled) {
            setControlModeEnabled(false);
        }
    }, [controlAllowed, controlModeEnabled]);

    async function loadAll(showLoading = true) {
        if (!sessionCode) {
            setError("Код сессии не указан");
            setLoading(false);
            return;
        }

        try {
            if (showLoading) {
                setLoading(true);
            }

            setError("");

            const sessionResult = await getSupportSession(sessionCode);
            setSession(sessionResult);

            if (isFinishedStatus(sessionResult.status)) {
                handleRemoteSessionEnded();
                return;
            }

            if (sessionResult.status === "ACTIVE") {
                const [messagesResult, filesResult] = await Promise.all([
                    getSupportChatMessages(sessionCode),
                    getSupportFiles(sessionCode),
                ]);

                setMessages(messagesResult);
                setFiles(filesResult);
            }
        } catch (e) {
            setError(e instanceof Error ? e.message : "Не удалось загрузить сессию");
        } finally {
            setLoading(false);
        }
    }

    function handleRemoteSessionEnded() {
        if (sessionEnded) {
            return;
        }

        setSessionEnded(true);
        setControlModeEnabled(false);

        if (endedTimerRef.current !== null) {
            window.clearTimeout(endedTimerRef.current);
        }

        endedTimerRef.current = window.setTimeout(() => {
            navigate("/pcs");
        }, 2500);
    }

    function showNotice(text: string) {
        setNotice(text);

        window.setTimeout(() => {
            setNotice("");
        }, 3000);
    }

    function copyCode() {
        void navigator.clipboard.writeText(sessionCode);
        showNotice("Код скопирован");
    }

    async function handleRequestControl() {
        if (!session || session.status !== "ACTIVE") {
            showNotice("Клиент ещё не подключился");
            return;
        }

        if (controlAllowed) {
            showNotice("Клиент уже разрешил управление");
            return;
        }

        if (controlRequested) {
            showNotice("Запрос управления уже отправлен");
            return;
        }

        try {
            const result = await requestSupportControl(sessionCode);
            setSession(result);
            showNotice("Запрос управления отправлен клиенту");
            await loadAll(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось запросить управление");
        }
    }

    async function handleFinish() {
        if (!confirm("Завершить сессию технической поддержки?")) {
            return;
        }

        try {
            await finishSupportSession(sessionCode);
            setSessionEnded(true);
            window.setTimeout(() => navigate("/pcs"), 900);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось завершить сессию");
        }
    }

    async function sendMessage() {
        const text = chatText.trim();

        if (!text) {
            return;
        }

        try {
            const saved = await sendSupportChatMessage(sessionCode, text);
            setMessages((current) => [...current, saved]);
            setChatText("");
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось отправить сообщение");
        }
    }

    async function uploadFile() {
        const file = fileInputRef.current?.files?.[0];

        if (!file) {
            showNotice("Выберите файл");
            return;
        }

        try {
            await uploadSupportFile({
                sessionCode,
                file,
            });

            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }

            showNotice("Файл отправлен клиенту на подтверждение");
            setFiles(await getSupportFiles(sessionCode));
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось отправить файл");
        }
    }

    async function downloadFile(file: SupportFileResponse) {
        try {
            await downloadSupportFile(sessionCode, file.id, getSupportFileName(file));
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось скачать файл");
        }
    }

    function changeQuality(value: string) {
        setSelectedQuality(value);
        showNotice(`Качество выбрано: ${getQualityLabel(value)}`);
    }

    return (
        <main className="min-h-screen bg-slate-100 text-slate-950">
            <div className="grid min-h-screen grid-cols-[270px_minmax(0,1fr)] max-xl:grid-cols-1">
                <SupportOperatorSidebar
                    roleLabel="Оператор"
                    onFilesClick={() => setFilesOpen(true)}
                />

                <section className="min-w-0 p-6">
                    <header className="mb-6 rounded-[32px] border border-slate-300 bg-white p-6 shadow-sm">
                        <div className="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <div className="mb-3 inline-flex items-center gap-2 rounded-xl bg-blue-50 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-700">
                                    <Headphones size={15} />
                                    Рабочее место оператора
                                </div>

                                <h1 className="text-3xl font-black tracking-tight text-slate-950">
                                    {session?.title || "Сессия технической поддержки"}
                                </h1>

                                <div className="mt-4 flex flex-wrap items-center gap-3">
                                    <button
                                        type="button"
                                        onClick={copyCode}
                                        className="inline-flex items-center gap-2 rounded-2xl border border-blue-200 bg-blue-50 px-4 py-2 text-base font-black text-blue-700"
                                    >
                                        Код: {sessionCode}
                                        <ClipboardCopy size={18} />
                                    </button>

                                    <StatusPill status={session?.status} />

                                    {clientConnected && (
                                        <span className="rounded-full bg-emerald-50 px-3 py-1.5 text-sm font-black text-emerald-700">
                                            Клиент: {session?.clientUsername || "подключён"}
                                        </span>
                                    )}

                                    {controlAllowed && (
                                        <span className="rounded-full bg-blue-50 px-3 py-1.5 text-sm font-black text-blue-700">
                                            Клиент разрешил управление
                                        </span>
                                    )}

                                    {controlRequested && (
                                        <span className="rounded-full bg-amber-50 px-3 py-1.5 text-sm font-black text-amber-700">
                                            Запрос управления отправлен
                                        </span>
                                    )}
                                </div>
                            </div>

                            <div className="flex flex-wrap items-center gap-3">
                                <button
                                    type="button"
                                    onClick={() => void loadAll(false)}
                                    className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-slate-300 bg-white px-6 text-sm font-black text-slate-700 shadow-sm transition hover:bg-slate-50"
                                >
                                    <RefreshCw size={20} />
                                    Обновить
                                </button>

                                <button
                                    type="button"
                                    onClick={() => navigate("/pcs")}
                                    className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-slate-300 bg-white px-6 text-sm font-black text-slate-700 shadow-sm transition hover:bg-slate-50"
                                >
                                    <ArrowLeft size={20} />
                                    Рабочий стол
                                </button>

                                <button
                                    type="button"
                                    onClick={handleFinish}
                                    className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-red-200 bg-white px-6 text-sm font-black text-red-600 shadow-sm transition hover:bg-red-50"
                                >
                                    <LogOut size={20} />
                                    Завершить
                                </button>
                            </div>
                        </div>
                    </header>

                    {notice && (
                        <div className="mb-5 rounded-3xl border border-blue-200 bg-blue-50 px-5 py-4 text-sm font-black text-blue-700 shadow-sm">
                            {notice}
                        </div>
                    )}

                    {error && (
                        <div className="mb-5 rounded-3xl border border-red-200 bg-red-50 px-5 py-4 text-sm font-black text-red-700 shadow-sm">
                            {error}
                        </div>
                    )}

                    {loading ? (
                        <LoadingBlock text="Загрузка сессии техподдержки..." />
                    ) : (
                        <div className="grid grid-cols-[minmax(0,1fr)_380px] gap-6 max-2xl:grid-cols-1">
                            <section className="rounded-[30px] border border-slate-300 bg-white p-5 shadow-sm">
                                <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
                                    <div>
                                        <h2 className="text-xl font-black text-slate-950">
                                            Экран клиента
                                        </h2>
                                        <p className="mt-1 text-sm font-semibold text-slate-500">
                                            {clientConnected
                                                ? "Можно смотреть экран клиента. Управление доступно только после разрешения клиента."
                                                : "Передайте клиенту код и дождитесь подключения."}
                                        </p>
                                    </div>

                                    <div className="flex flex-wrap gap-2">
                                        <button
                                            type="button"
                                            onClick={handleRequestControl}
                                            disabled={!clientConnected || controlAllowed || controlRequested}
                                            className={
                                                controlAllowed
                                                    ? "rounded-2xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm font-black text-blue-700"
                                                    : controlRequested
                                                        ? "rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-black text-amber-700"
                                                        : "rounded-2xl border border-blue-200 bg-white px-4 py-3 text-sm font-black text-slate-800 transition hover:bg-blue-50 hover:text-blue-700 disabled:border-slate-200 disabled:text-slate-300"
                                            }
                                        >
                                            {controlAllowed
                                                ? "Управление разрешено"
                                                : controlRequested
                                                    ? "Запрос отправлен"
                                                    : "Запросить управление"}
                                        </button>

                                        {controlAllowed && (
                                            <div className="flex overflow-hidden rounded-2xl border border-slate-300 bg-white p-1">
                                                <button
                                                    type="button"
                                                    onClick={() => setControlModeEnabled(false)}
                                                    className={
                                                        !controlModeEnabled
                                                            ? "rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white"
                                                            : "rounded-xl px-4 py-2 text-sm font-black text-slate-600 transition hover:bg-slate-100"
                                                    }
                                                >
                                                    Просмотр
                                                </button>

                                                <button
                                                    type="button"
                                                    onClick={() => setControlModeEnabled(true)}
                                                    className={
                                                        controlModeEnabled
                                                            ? "rounded-xl bg-blue-600 px-4 py-2 text-sm font-black text-white"
                                                            : "rounded-xl px-4 py-2 text-sm font-black text-slate-600 transition hover:bg-blue-50 hover:text-blue-700"
                                                    }
                                                >
                                                    Управлять
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                </div>

                                <EducationStreamPanel
                                    pc={clientPc}
                                    title="Экран клиента"
                                    subtitle={
                                        activeControl
                                            ? "Вы управляете ПК клиента"
                                            : controlAllowed
                                                ? "Клиент разрешил управление, но сейчас включён режим просмотра"
                                                : clientConnected
                                                    ? "Только просмотр до разрешения клиента"
                                                    : "Клиент ещё не подключился"
                                    }
                                    showQualityControls
                                    qualityValue={selectedQuality}
                                    onQualityChange={changeQuality}
                                    controlEnabled={activeControl}
                                    controlPcId={clientPc?.id || session?.clientPcId || 0}
                                    controlLabel="Управление ПК клиента активно"
                                    controlProfile="support_operator_view_client"
                                    controlSupportCode={sessionCode}
                                />
                            </section>

                            <ChatPanel
                                title="Чат с клиентом"
                                messages={messages}
                                chatText={chatText}
                                currentUsername={currentUsername}
                                onChatTextChange={setChatText}
                                onSend={sendMessage}
                            />
                        </div>
                    )}
                </section>
            </div>

            <SupportOperatorFilesModal
                open={filesOpen}
                files={files}
                fileInputRef={fileInputRef}
                onClose={() => setFilesOpen(false)}
                onUpload={uploadFile}
                onDownload={downloadFile}
            />

            {sessionEnded && (
                <SessionEndedOverlay
                    text="Сессия технической поддержки завершена. Возвращаем вас на рабочий стол..."
                />
            )}
        </main>
    );
}

function buildSupportClientPc(session: SupportSessionResponse | null): PcDetailsResponse | null {
    if (!session?.clientPcId) {
        return null;
    }

    return {
        id: session.clientPcId,
        name: session.clientPcName || "ПК клиента",
        macAddress: session.clientPcMacAddress || "",
        status: session.clientPcStatus || "ONLINE",
        lastConnection: null,
        screenWidth: session.clientPcScreenWidth || session.clientScreenWidth || session.screenWidth || 1280,
        screenHeight: session.clientPcScreenHeight || session.clientScreenHeight || session.screenHeight || 720,
        webrtcUrl: session.clientPcWebrtcUrl || "",
        streamName: session.clientPcStreamName || "",
    } as PcDetailsResponse;
}

function SupportOperatorSidebar({
                                    roleLabel,
                                    onFilesClick,
                                }: {
    roleLabel: string;
    onFilesClick: () => void;
}) {
    const username = localStorage.getItem("username") || "Пользователь";

    return (
        <aside className="flex min-h-screen flex-col border-r border-slate-800 bg-slate-950 p-6 text-white max-xl:hidden">
            <div className="mb-10 flex items-center gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-600 text-white">
                    <Headphones size={23} />
                </div>

                <div className="text-2xl font-black">
                    Remo<span className="text-blue-400">Desk</span>
                </div>
            </div>

            <nav className="grid gap-2">
                <div className="flex h-14 items-center gap-4 rounded-2xl bg-blue-600 px-4 text-left font-black text-white">
                    <Headphones size={21} />
                    Техподдержка
                </div>

                <button
                    type="button"
                    onClick={onFilesClick}
                    className="flex h-14 items-center gap-4 rounded-2xl px-4 text-left font-bold text-slate-300 transition hover:bg-slate-900 hover:text-white"
                >
                    <FileText size={21} />
                    Файлы
                </button>
            </nav>

            <section className="mt-auto rounded-[24px] border border-slate-700 bg-slate-900 p-4 shadow-sm">
                <div className="font-black text-white">{username}</div>
                <div className="mt-1 text-sm font-semibold text-slate-400">{roleLabel}</div>
            </section>
        </aside>
    );
}

function SupportOperatorFilesModal({
                                       open,
                                       files,
                                       fileInputRef,
                                       onClose,
                                       onUpload,
                                       onDownload,
                                   }: {
    open: boolean;
    files: SupportFileResponse[];
    fileInputRef: RefObject<HTMLInputElement | null>;
    onClose: () => void;
    onUpload: () => void;
    onDownload: (file: SupportFileResponse) => void;
}) {
    if (!open) {
        return null;
    }

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-6 backdrop-blur-sm"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) {
                    onClose();
                }
            }}
        >
            <section className="w-full max-w-3xl overflow-hidden rounded-[34px] border border-slate-300 bg-white shadow-2xl">
                <div className="flex items-start justify-between border-b border-slate-200 px-7 py-6">
                    <div>
                        <h2 className="text-3xl font-black text-slate-950">Файлы</h2>
                        <p className="mt-2 text-sm font-semibold text-slate-500">
                            Отправляйте файлы клиенту и скачивайте принятые файлы.
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={onClose}
                        className="flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-300 bg-white text-slate-500 hover:bg-slate-50"
                    >
                        <X size={22} />
                    </button>
                </div>

                <div className="grid gap-5 p-7">
                    <div className="rounded-3xl border border-blue-200 bg-blue-50 p-5">
                        <input
                            ref={fileInputRef}
                            type="file"
                            className="w-full rounded-2xl border border-blue-200 bg-white px-4 py-3 text-sm font-bold text-slate-700"
                        />

                        <button
                            type="button"
                            onClick={onUpload}
                            className="mt-4 inline-flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-blue-600 px-4 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700"
                        >
                            <Upload size={18} />
                            Отправить файл клиенту
                        </button>
                    </div>

                    <div className="grid max-h-[420px] gap-3 overflow-y-auto pr-1">
                        {files.length === 0 ? (
                            <EmptyState text="Файлов пока нет." />
                        ) : (
                            files.map((file) => (
                                <div
                                    key={file.id}
                                    className="rounded-2xl border border-slate-200 bg-slate-50 p-4"
                                >
                                    <div className="font-black text-slate-950">
                                        📎 {getSupportFileName(file)}
                                    </div>

                                    <div className="mt-1 text-xs font-bold text-slate-500">
                                        {getSupportFileType(file)} · {getSupportFileSize(file)} · {translateFileStatus(file.status)}
                                    </div>

                                    <button
                                        type="button"
                                        onClick={() => onDownload(file)}
                                        className="mt-3 inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2 text-xs font-black text-slate-700 transition hover:bg-slate-100"
                                    >
                                        <Download size={14} />
                                        Скачать
                                    </button>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            </section>
        </div>
    );
}

function StatusPill({ status }: { status?: string }) {
    const label = translateStatus(status);

    if (status === "ACTIVE") {
        return (
            <span className="rounded-full bg-emerald-50 px-3 py-1.5 text-sm font-black text-emerald-700">
                {label}
            </span>
        );
    }

    if (status === "WAITING_CLIENT") {
        return (
            <span className="rounded-full bg-amber-50 px-3 py-1.5 text-sm font-black text-amber-700">
                {label}
            </span>
        );
    }

    return (
        <span className="rounded-full bg-slate-100 px-3 py-1.5 text-sm font-black text-slate-600">
            {label}
        </span>
    );
}

function ChatPanel({
                       title,
                       messages,
                       chatText,
                       currentUsername,
                       onChatTextChange,
                       onSend,
                   }: {
    title: string;
    messages: SupportChatMessageResponse[];
    chatText: string;
    currentUsername: string;
    onChatTextChange: (value: string) => void;
    onSend: () => void;
}) {
    return (
        <section className="rounded-[30px] border border-slate-300 bg-white p-5 shadow-sm">
            <h2 className="mb-5 text-xl font-black text-slate-950">{title}</h2>

            <div className="mb-5 grid max-h-[640px] min-h-[500px] gap-4 overflow-y-auto rounded-2xl bg-slate-100 p-4">
                {messages.length === 0 ? (
                    <EmptyState text="Сообщений пока нет." />
                ) : (
                    messages.map((message, index) => {
                        const mine = message.mine === true || message.senderUsername === currentUsername;
                        const author = mine ? "Вы" : message.senderUsername || "Собеседник";

                        return (
                            <div
                                key={message.id || index}
                                className={mine ? "flex justify-end" : "flex justify-start"}
                            >
                                <div className={mine ? "max-w-[82%] text-right" : "max-w-[82%] text-left"}>
                                    <div className="mb-1 text-xs font-bold text-slate-400">
                                        {author} · {formatSupportTime(message.createdAt, message.createdAtText)}
                                    </div>

                                    <div
                                        className={
                                            mine
                                                ? "rounded-2xl bg-blue-600 px-4 py-3 text-sm font-bold leading-6 text-white shadow-sm"
                                                : "rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm font-bold leading-6 text-slate-800 shadow-sm"
                                        }
                                    >
                                        {message.message || ""}
                                    </div>
                                </div>
                            </div>
                        );
                    })
                )}
            </div>

            <div className="flex items-center gap-2 rounded-2xl border border-slate-300 bg-white p-2">
                <input
                    value={chatText}
                    onChange={(event) => onChatTextChange(event.target.value)}
                    onKeyDown={(event) => {
                        if (event.key === "Enter") {
                            onSend();
                        }
                    }}
                    placeholder="Напишите сообщение..."
                    className="min-w-0 flex-1 border-none bg-transparent px-3 py-2 text-sm font-semibold text-slate-900 outline-none placeholder:text-slate-400"
                />

                <button
                    type="button"
                    onClick={onSend}
                    className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-600 text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700"
                >
                    <Send size={19} />
                </button>
            </div>
        </section>
    );
}

function LoadingBlock({ text }: { text: string }) {
    return (
        <div className="rounded-[30px] border border-slate-300 bg-white p-10 text-center shadow-sm">
            <Loader2 className="mx-auto mb-4 animate-spin text-blue-600" size={42} />
            <p className="font-black text-slate-600">{text}</p>
        </div>
    );
}

function EmptyState({ text }: { text: string }) {
    return (
        <div className="rounded-2xl bg-slate-100 px-4 py-5 text-center text-sm font-bold text-slate-500">
            {text}
        </div>
    );
}

function SessionEndedOverlay({ text }: { text: string }) {
    return (
        <div className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-950/75 p-6 backdrop-blur-md">
            <div className="max-w-xl rounded-[34px] border border-white/10 bg-white p-9 text-center shadow-2xl">
                <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-[28px] bg-blue-50 text-blue-700">
                    <CheckCircle2 size={42} />
                </div>

                <h2 className="text-3xl font-black text-slate-950">
                    Сессия завершена
                </h2>

                <p className="mt-4 text-base font-semibold leading-7 text-slate-600">
                    {text}
                </p>
            </div>
        </div>
    );
}

function isFinishedStatus(status?: string): boolean {
    const value = String(status || "").toUpperCase();

    return (
        value === "FINISHED" ||
        value === "CANCELLED" ||
        value === "CANCELED" ||
        value === "ENDED" ||
        value === "CLOSED"
    );
}

function translateStatus(status?: string): string {
    if (status === "WAITING_CLIENT") return "Ожидание клиента";
    if (status === "ACTIVE") return "Активна";
    if (status === "FINISHED") return "Завершена";
    if (status === "CANCELLED") return "Отменена";
    return status || "—";
}

function translateFileStatus(status?: string): string {
    if (status === "PENDING") return "ожидает подтверждения клиента";
    if (status === "ACCEPTED") return "принят клиентом";
    if (status === "REJECTED") return "отклонён клиентом";
    return status || "—";
}

function getSupportFileName(file: SupportFileResponse): string {
    const name = file.originalFilename || file.filename || file.name;

    if (name && String(name).trim() !== "" && String(name).trim() !== ".") {
        return String(name).trim();
    }

    return "Файл без названия";
}

function getSupportFileType(file: SupportFileResponse): string {
    if (file.contentType && String(file.contentType).trim() !== "") {
        return file.contentType;
    }

    const name = getSupportFileName(file);
    const dotIndex = name.lastIndexOf(".");

    if (dotIndex !== -1 && dotIndex < name.length - 1) {
        return name.slice(dotIndex + 1).toUpperCase();
    }

    return "Неизвестный тип";
}

function getSupportFileSize(file: SupportFileResponse): string {
    if (file.sizeText) {
        return file.sizeText;
    }

    const bytes = Number(file.sizeBytes || 0);

    if (bytes <= 0) return "0 Б";
    if (bytes < 1024) return `${bytes} Б`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} КБ`;

    return `${(bytes / 1024 / 1024).toFixed(1)} МБ`;
}

function formatSupportTime(value?: string, text?: string): string {
    if (text) {
        return text;
    }

    if (!value) {
        return "";
    }

    try {
        return new Date(value).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
        });
    } catch {
        return "";
    }
}

function getQualityLabel(value: string): string {
    if (value === "854x480") return "480p";
    if (value === "1920x1080") return "1080p";
    return "720p";
}