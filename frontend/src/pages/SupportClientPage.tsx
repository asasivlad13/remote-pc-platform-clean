import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
    ArrowLeft,
    Check,
    CheckCircle2,
    ClipboardCopy,
    Download,
    FileText,
    Headphones,
    Loader2,
    LogOut,
    Send,
    ShieldCheck,
    X,
} from "lucide-react";
import {
    acceptSupportFile,
    allowSupportControl,
    denySupportControl,
    downloadSupportFile,
    finishSupportSession,
    getSupportChatMessages,
    getSupportFiles,
    getSupportSession,
    rejectSupportFile,
    sendSupportChatMessage,
} from "../features/support/supportApi";
import type {
    SupportChatMessageResponse,
    SupportFileResponse,
    SupportSessionResponse,
} from "../features/support/supportTypes";

export function SupportClientPage() {
    const navigate = useNavigate();
    const { sessionCode = "" } = useParams();

    const endedTimerRef = useRef<number | null>(null);
    const currentUsername = localStorage.getItem("username") || "";

    const [session, setSession] = useState<SupportSessionResponse | null>(null);
    const [messages, setMessages] = useState<SupportChatMessageResponse[]>([]);
    const [files, setFiles] = useState<SupportFileResponse[]>([]);
    const [chatText, setChatText] = useState("");
    const [filesOpen, setFilesOpen] = useState(false);
    const [sessionEnded, setSessionEnded] = useState(false);
    const [notice, setNotice] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(true);

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
        if (!sessionEnded) {
            return;
        }

        if (endedTimerRef.current !== null) {
            window.clearTimeout(endedTimerRef.current);
        }

        endedTimerRef.current = window.setTimeout(() => {
            navigate("/pcs", { replace: true });
        }, 1800);
    }, [sessionEnded, navigate]);

    async function loadAll(showLoading = true) {
        if (!sessionCode) {
            setError("Код сессии не указан");
            setLoading(false);
            return;
        }

        if (sessionEnded) {
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
            const message = e instanceof Error ? e.message : "Не удалось загрузить сессию";

            if (looksLikeFinishedSession(message)) {
                handleRemoteSessionEnded();
                return;
            }

            setError(message);
        } finally {
            setLoading(false);
        }
    }

    function handleRemoteSessionEnded() {
        setSessionEnded(true);
        setError("");
        setNotice("");
        setFilesOpen(false);
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

    async function allowControl() {
        try {
            const result = await allowSupportControl(sessionCode);
            setSession(result);
            showNotice("Управление разрешено");
            await loadAll(false);
        } catch (e) {
            const message = e instanceof Error ? e.message : "Не удалось разрешить управление";

            if (looksLikeFinishedSession(message)) {
                handleRemoteSessionEnded();
                return;
            }

            showNotice(message);
        }
    }

    async function denyControl() {
        try {
            const result = await denySupportControl(sessionCode);
            setSession(result);
            showNotice("Управление запрещено");
            await loadAll(false);
        } catch (e) {
            const message = e instanceof Error ? e.message : "Не удалось запретить управление";

            if (looksLikeFinishedSession(message)) {
                handleRemoteSessionEnded();
                return;
            }

            showNotice(message);
        }
    }

    async function finishSession() {
        if (!confirm("Завершить сессию технической поддержки?")) {
            return;
        }

        try {
            await finishSupportSession(sessionCode);
        } catch {
            // даже если сервер уже завершил сессию, локально тоже закрываем страницу
        }

        setSessionEnded(true);
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
            const message = e instanceof Error ? e.message : "Не удалось отправить сообщение";

            if (looksLikeFinishedSession(message)) {
                handleRemoteSessionEnded();
                return;
            }

            showNotice(message);
        }
    }

    async function acceptFile(file: SupportFileResponse) {
        try {
            await acceptSupportFile(sessionCode, file.id);
            showNotice("Файл принят");
            setFiles(await getSupportFiles(sessionCode));
        } catch (e) {
            const message = e instanceof Error ? e.message : "Не удалось принять файл";

            if (looksLikeFinishedSession(message)) {
                handleRemoteSessionEnded();
                return;
            }

            showNotice(message);
        }
    }

    async function rejectFile(file: SupportFileResponse) {
        try {
            await rejectSupportFile(sessionCode, file.id);
            showNotice("Файл отклонён");
            setFiles(await getSupportFiles(sessionCode));
        } catch (e) {
            const message = e instanceof Error ? e.message : "Не удалось отклонить файл";

            if (looksLikeFinishedSession(message)) {
                handleRemoteSessionEnded();
                return;
            }

            showNotice(message);
        }
    }

    async function downloadFile(file: SupportFileResponse) {
        try {
            await downloadSupportFile(sessionCode, file.id, getSupportFileName(file));
        } catch (e) {
            const message = e instanceof Error ? e.message : "Не удалось скачать файл";

            if (looksLikeFinishedSession(message)) {
                handleRemoteSessionEnded();
                return;
            }

            showNotice(message);
        }
    }

    return (
        <main className="min-h-screen bg-slate-100 text-slate-950">
            <div className="grid min-h-screen grid-cols-[270px_minmax(0,1fr)] max-xl:grid-cols-1">
                <SupportClientSidebar
                    roleLabel="Клиент"
                    onFilesClick={() => setFilesOpen(true)}
                />

                <section className="min-w-0 p-6">
                    <header className="mb-6 rounded-[32px] border border-slate-300 bg-white p-6 shadow-sm">
                        <div className="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <div className="mb-3 inline-flex items-center gap-2 rounded-xl bg-emerald-50 px-3 py-1 text-xs font-black uppercase tracking-wide text-emerald-700">
                                    <ShieldCheck size={15} />
                                    Клиент техподдержки
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

                                    <span className="rounded-full bg-slate-100 px-3 py-1.5 text-sm font-black text-slate-700">
                                        Оператор: {session?.operatorUsername || "—"}
                                    </span>

                                    <span className="rounded-full bg-slate-100 px-3 py-1.5 text-sm font-black text-slate-700">
                                        Ваш ПК: {session?.clientPcName || "—"}
                                    </span>
                                </div>
                            </div>

                            <div className="flex flex-wrap items-center gap-3">
                                <button
                                    type="button"
                                    onClick={() => navigate("/pcs", { replace: true })}
                                    className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-slate-300 bg-white px-6 text-sm font-black text-slate-700 shadow-sm transition hover:bg-slate-50"
                                >
                                    <ArrowLeft size={20} />
                                    Рабочий стол
                                </button>

                                <button
                                    type="button"
                                    onClick={finishSession}
                                    className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-red-200 bg-white px-6 text-sm font-black text-red-600 shadow-sm transition hover:bg-red-50"
                                >
                                    <LogOut size={20} />
                                    Завершить помощь
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
                        <div className="grid grid-cols-[minmax(0,1fr)_420px] gap-6 max-2xl:grid-cols-1">
                            <section className="rounded-[30px] border border-slate-300 bg-white p-6 shadow-sm">
                                <h2 className="text-2xl font-black text-slate-950">
                                    Разрешения клиента
                                </h2>

                                <p className="mt-3 text-sm font-semibold leading-6 text-slate-500">
                                    Оператор может смотреть ваш экран, но управление вашим ПК
                                    будет доступно только после вашего разрешения.
                                </p>

                                <div className="mt-6 grid grid-cols-2 gap-3 max-md:grid-cols-1">
                                    <button
                                        type="button"
                                        onClick={allowControl}
                                        disabled={session?.status !== "ACTIVE" || !session?.controlRequested || session?.controlAllowed}
                                        className={
                                            session?.controlAllowed
                                                ? "inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 px-6 text-sm font-black text-emerald-700"
                                                : "inline-flex h-14 items-center justify-center gap-3 rounded-2xl bg-emerald-600 px-6 text-sm font-black text-white shadow-lg shadow-emerald-600/20 transition hover:bg-emerald-700 disabled:bg-slate-200 disabled:text-slate-400 disabled:shadow-none"
                                        }
                                    >
                                        <Check size={20} />
                                        {session?.controlAllowed
                                            ? "Управление разрешено"
                                            : session?.controlRequested
                                                ? "Разрешить управление"
                                                : "Нет запроса управления"}
                                    </button>

                                    <button
                                        type="button"
                                        onClick={denyControl}
                                        disabled={session?.status !== "ACTIVE" || (!session?.controlRequested && !session?.controlAllowed)}
                                        className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-red-200 bg-white px-6 text-sm font-black text-red-600 shadow-sm transition hover:bg-red-50 disabled:border-slate-200 disabled:text-slate-300 disabled:hover:bg-white"
                                    >
                                        <X size={20} />
                                        {session?.controlAllowed ? "Запретить управление" : "Отклонить запрос"}
                                    </button>
                                </div>

                                <div className="mt-6 rounded-3xl border border-blue-200 bg-blue-50 p-5 text-sm font-bold leading-6 text-blue-800">
                                    {session?.controlAllowed
                                        ? "Вы разрешили оператору управление этим ПК. Можно запретить управление в любой момент."
                                        : session?.controlRequested
                                            ? "Оператор запросил управление вашим ПК. Разрешайте управление только если доверяете оператору."
                                            : "Оператор может смотреть ваш экран. Управление появится только после вашего разрешения."}
                                </div>
                            </section>

                            <ChatPanel
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

            <SupportClientFilesModal
                open={filesOpen}
                files={files}
                onClose={() => setFilesOpen(false)}
                onAccept={acceptFile}
                onReject={rejectFile}
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

function SupportClientSidebar({
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
                <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-600 text-white">
                    <Headphones size={23} />
                </div>

                <div className="text-2xl font-black">
                    Remo<span className="text-blue-400">Desk</span>
                </div>
            </div>

            <nav className="grid gap-2">
                <button
                    type="button"
                    onClick={onFilesClick}
                    className="flex h-14 items-center gap-4 rounded-2xl bg-emerald-600 px-4 text-left font-black text-white"
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

function SupportClientFilesModal({
                                     open,
                                     files,
                                     onClose,
                                     onAccept,
                                     onReject,
                                     onDownload,
                                 }: {
    open: boolean;
    files: SupportFileResponse[];
    onClose: () => void;
    onAccept: (file: SupportFileResponse) => void;
    onReject: (file: SupportFileResponse) => void;
    onDownload: (file: SupportFileResponse) => void;
}) {
    const currentUsername = localStorage.getItem("username") || "";

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
                        <h2 className="text-3xl font-black text-slate-950">Файлы от оператора</h2>
                        <p className="mt-2 text-sm font-semibold text-slate-500">
                            Здесь отображаются файлы, которые оператор отправил вам в рамках сессии.
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

                <div className="grid max-h-[560px] gap-3 overflow-y-auto p-7">
                    {files.length === 0 ? (
                        <EmptyState text="Файлов пока нет." />
                    ) : (
                        files.map((file) => {
                            const isRecipient = file.recipientUsername === currentUsername || !file.recipientUsername;
                            const canAccept = file.status === "PENDING" && isRecipient;
                            const canDownload = file.status === "ACCEPTED";

                            return (
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

                                    <div className="mt-3 flex flex-wrap gap-2">
                                        {canAccept && (
                                            <button
                                                type="button"
                                                onClick={() => onAccept(file)}
                                                className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-black text-white transition hover:bg-emerald-700"
                                            >
                                                Принять
                                            </button>
                                        )}

                                        {canAccept && (
                                            <button
                                                type="button"
                                                onClick={() => onReject(file)}
                                                className="rounded-xl border border-red-200 bg-white px-4 py-2 text-xs font-black text-red-600 transition hover:bg-red-50"
                                            >
                                                Отклонить
                                            </button>
                                        )}

                                        {canDownload && (
                                            <button
                                                type="button"
                                                onClick={() => onDownload(file)}
                                                className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2 text-xs font-black text-slate-700 transition hover:bg-slate-100"
                                            >
                                                <Download size={14} />
                                                Скачать
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })
                    )}
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
                       messages,
                       chatText,
                       currentUsername,
                       onChatTextChange,
                       onSend,
                   }: {
    messages: SupportChatMessageResponse[];
    chatText: string;
    currentUsername: string;
    onChatTextChange: (value: string) => void;
    onSend: () => void;
}) {
    return (
        <section className="rounded-[30px] border border-slate-300 bg-white p-5 shadow-sm">
            <h2 className="mb-5 text-xl font-black text-slate-950">Чат с оператором</h2>

            <div className="mb-5 grid max-h-[520px] min-h-[420px] gap-4 overflow-y-auto rounded-2xl bg-slate-100 p-4">
                {messages.length === 0 ? (
                    <EmptyState text="Сообщений пока нет." />
                ) : (
                    messages.map((message, index) => {
                        const mine = message.mine === true || message.senderUsername === currentUsername;
                        const author = mine ? "Вы" : message.senderUsername || "Оператор";

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
                                                ? "rounded-2xl bg-emerald-600 px-4 py-3 text-sm font-bold leading-6 text-white shadow-sm"
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
                    className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-lg shadow-emerald-600/20 transition hover:bg-emerald-700"
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
            <Loader2 className="mx-auto mb-4 animate-spin text-emerald-600" size={42} />
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
                <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-[28px] bg-emerald-50 text-emerald-700">
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

function looksLikeFinishedSession(message: string): boolean {
    const value = message.toLowerCase();

    return (
        value.includes("заверш") ||
        value.includes("finished") ||
        value.includes("ended") ||
        value.includes("closed") ||
        value.includes("cancelled") ||
        value.includes("canceled") ||
        value.includes("not active") ||
        value.includes("inactive")
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
    if (status === "PENDING") return "ожидает вашего подтверждения";
    if (status === "ACCEPTED") return "принят";
    if (status === "REJECTED") return "отклонён";
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