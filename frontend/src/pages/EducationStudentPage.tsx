import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
    ClipboardCopy,
    FileText,
    LogOut,
    Monitor,
    Send,
    Share2,
    Users,
} from "lucide-react";
import {
    getEducationChatMessages,
    getEducationSession,
    getMyEducationParticipantStatus,
    leaveEducationSession,
    requestEducationControl,
    requestEducationScreenShare,
    sendEducationChatMessage,
} from "../features/education/educationApi";
import {
    buildTeacherPc,
    formatEducationTime,
    getEducationTitle,
    getInitials,
    getParticipantDisplayName,
    getParticipantStatus,
} from "../features/education/educationMappers";
import type {
    EducationChatMessageResponse,
    EducationParticipantResponse,
    EducationSessionResponse,
} from "../features/education/educationTypes";
import { EducationStreamPanel } from "../features/education/components/EducationStreamPanel";
import { EducationFilesModal } from "../features/education/components/EducationFilesModal";
import { useRemoteSocket } from "../features/remote/useRemoteSocket";
import { getUserDisplayName } from "../features/profile/userDisplayName";

export function EducationStudentPage() {
    const navigate = useNavigate();
    const params = useParams();
    const [searchParams] = useSearchParams();

    const sessionCode = params.sessionCode || searchParams.get("code") || "";
    const currentUsername = localStorage.getItem("username") || "";
    const currentDisplayName = getUserDisplayName(currentUsername || "Студент");

    const [session, setSession] = useState<EducationSessionResponse | null>(null);
    const [myParticipant, setMyParticipant] = useState<EducationParticipantResponse | null>(null);
    const [messages, setMessages] = useState<EducationChatMessageResponse[]>([]);
    const [chatText, setChatText] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const [selectedQuality, setSelectedQuality] = useState("1280x720");
    const [filesModalOpen, setFilesModalOpen] = useState(false);
    const [sessionEnded, setSessionEnded] = useState(false);

    // Главное изменение: разрешение управления есть, но студент сам выбирает режим.
    const [controlModeEnabled, setControlModeEnabled] = useState(false);

    const { sendSettings } = useRemoteSocket();

    const title = getEducationTitle(session);
    const teacherPc = useMemo(() => buildTeacherPc(session), [session]);

    const participants = useMemo(
        () => buildStudentVisibleParticipants(session, currentUsername, currentDisplayName, myParticipant),
        [session, currentUsername, currentDisplayName, myParticipant],
    );

    const controlAllowed = isControlAllowed(myParticipant);
    const controlRequested = isControlRequested(myParticipant) && !controlAllowed;
    const activeControlEnabled = controlAllowed && controlModeEnabled;

    useEffect(() => {
        void loadData();

        const timer = window.setInterval(() => {
            void loadData(false);
        }, 3000);

        return () => window.clearInterval(timer);
    }, [sessionCode]);

    useEffect(() => {
        if (!controlAllowed && controlModeEnabled) {
            setControlModeEnabled(false);
        }
    }, [controlAllowed, controlModeEnabled]);

    async function loadData(showLoading = true) {
        if (!sessionCode) {
            setError("Код учебной сессии не указан");
            setLoading(false);
            return;
        }

        try {
            if (showLoading) {
                setLoading(true);
            }

            setError("");

            const [sessionResult, messagesResult, myParticipantResult] = await Promise.all([
                getEducationSession(sessionCode),
                getEducationChatMessages(sessionCode),
                getMyEducationParticipantStatus(sessionCode).catch(() => null),
            ]);

            if (isSessionFinished(sessionResult)) {
                setSessionEnded(true);
                setSession(sessionResult);
                setMessages([]);
                setMyParticipant(null);
                setControlModeEnabled(false);
                return;
            }

            setSessionEnded(false);
            setSession(sessionResult);
            setMessages(messagesResult);
            setMyParticipant(myParticipantResult);
        } catch (e) {
            const message = e instanceof Error ? e.message : "";

            if (
                message.toLowerCase().includes("заверш") ||
                message.toLowerCase().includes("finished") ||
                message.toLowerCase().includes("ended")
            ) {
                setSessionEnded(true);
                setError("");
                setControlModeEnabled(false);
                return;
            }

            setError(message || "Не удалось загрузить учебную сессию");
        } finally {
            setLoading(false);
        }
    }

    function copyCode() {
        void navigator.clipboard.writeText(sessionCode);
        showNotice("Код сессии скопирован");
    }

    function showNotice(text: string) {
        setNotice(text);

        window.setTimeout(() => {
            setNotice("");
        }, 3000);
    }

    async function requestControl() {
        if (controlAllowed) {
            showNotice("Управление уже разрешено преподавателем");
            return;
        }

        if (controlRequested) {
            showNotice("Запрос на управление уже отправлен");
            return;
        }

        try {
            const result = await requestEducationControl(sessionCode);

            setMyParticipant((current) => ({
                ...(current || {}),
                ...result,
                controlRequested: true,
            }));

            showNotice("Запрос на управление отправлен преподавателю");
            await loadData(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось запросить управление");
        }
    }

    async function requestScreenShare() {
        try {
            await requestEducationScreenShare(sessionCode);
            showNotice("Запрос на демонстрацию экрана отправлен преподавателю");
            await loadData(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось запросить демонстрацию экрана");
        }
    }

    async function leaveSession() {
        try {
            await leaveEducationSession(sessionCode);
        } catch {
            // даже если backend не ответил, уводим пользователя на рабочий стол
        }

        navigate("/pcs");
    }

    function changeQuality(resolution: string) {
        setSelectedQuality(resolution);

        if (!teacherPc?.id) {
            showNotice("ПК преподавателя пока не определён");
            return;
        }

        try {
            sendSettings({
                pcId: teacherPc.id,
                resolution,
                profile: "education_student",
                educationCode: sessionCode,
                pcName: teacherPc.name,
            });

            showNotice(`Качество трансляции изменяется на ${getQualityLabel(resolution)}`);
        } catch {
            showNotice("Не удалось отправить настройку качества");
        }
    }

    async function sendMessage() {
        const text = chatText.trim();

        if (!text) {
            return;
        }

        try {
            const saved = await sendEducationChatMessage(sessionCode, text);
            setMessages((current) => [...current, saved]);
            setChatText("");
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось отправить сообщение");
        }
    }

    return (
        <main className="min-h-screen bg-slate-100 text-slate-950">
            <div className="grid min-h-screen grid-cols-[270px_minmax(0,1fr)] max-xl:grid-cols-1">
                <StudentSidebar onFilesClick={() => setFilesModalOpen(true)} />

                <section className="min-w-0 p-6">
                    <header className="mb-6 rounded-[32px] border border-slate-300 bg-white p-6 shadow-sm">
                        <div className="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <div className="mb-3 inline-flex rounded-xl bg-blue-50 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-700">
                                    Учебная сессия
                                </div>

                                <h1 className="text-3xl font-black tracking-tight text-slate-950">
                                    {title}
                                </h1>

                                <div className="mt-4 flex flex-wrap items-center gap-4">
                                    <button
                                        type="button"
                                        onClick={copyCode}
                                        className="inline-flex items-center gap-2 rounded-2xl border border-blue-200 bg-blue-50 px-4 py-2 text-base font-black text-blue-700"
                                    >
                                        Код: {sessionCode || "------"}
                                        <ClipboardCopy size={18} />
                                    </button>

                                    {!sessionEnded && (
                                        <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1.5 text-sm font-black text-emerald-700">
                                            <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />
                                            Подключено
                                        </span>
                                    )}

                                    {!sessionEnded && controlRequested && (
                                        <span className="inline-flex items-center gap-2 rounded-full bg-amber-50 px-3 py-1.5 text-sm font-black text-amber-700">
                                            <span className="h-2.5 w-2.5 rounded-full bg-amber-500" />
                                            Запрос управления отправлен
                                        </span>
                                    )}

                                    {!sessionEnded && controlAllowed && (
                                        <span className="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1.5 text-sm font-black text-blue-700">
                                            <span className="h-2.5 w-2.5 rounded-full bg-blue-500" />
                                            Управление разрешено
                                        </span>
                                    )}

                                    {!sessionEnded && activeControlEnabled && (
                                        <span className="inline-flex items-center gap-2 rounded-full bg-violet-50 px-3 py-1.5 text-sm font-black text-violet-700">
                                            <span className="h-2.5 w-2.5 rounded-full bg-violet-500" />
                                            Режим управления включён
                                        </span>
                                    )}
                                </div>
                            </div>

                            {!sessionEnded && (
                                <div className="flex flex-wrap items-center gap-3">
                                    <button
                                        type="button"
                                        onClick={requestControl}
                                        disabled={controlAllowed || controlRequested}
                                        className={
                                            controlAllowed
                                                ? "inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-blue-200 bg-blue-50 px-6 text-sm font-black text-blue-700 shadow-sm"
                                                : controlRequested
                                                    ? "inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-6 text-sm font-black text-amber-700 shadow-sm"
                                                    : "inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-blue-200 bg-white px-6 text-sm font-black text-slate-800 shadow-sm transition hover:bg-blue-50 hover:text-blue-700"
                                        }
                                    >
                                        <Monitor size={20} />
                                        {controlAllowed
                                            ? "Управление разрешено"
                                            : controlRequested
                                                ? "Запрос отправлен"
                                                : "Запросить управление"}
                                    </button>

                                    {controlAllowed && (
                                        <div className="flex h-14 overflow-hidden rounded-2xl border border-slate-300 bg-white p-1 shadow-sm">
                                            <button
                                                type="button"
                                                onClick={() => setControlModeEnabled(false)}
                                                className={
                                                    !controlModeEnabled
                                                        ? "rounded-xl bg-slate-950 px-5 text-sm font-black text-white"
                                                        : "rounded-xl px-5 text-sm font-black text-slate-600 transition hover:bg-slate-100"
                                                }
                                            >
                                                Просмотр
                                            </button>

                                            <button
                                                type="button"
                                                onClick={() => setControlModeEnabled(true)}
                                                className={
                                                    controlModeEnabled
                                                        ? "rounded-xl bg-blue-600 px-5 text-sm font-black text-white"
                                                        : "rounded-xl px-5 text-sm font-black text-slate-600 transition hover:bg-blue-50 hover:text-blue-700"
                                                }
                                            >
                                                Управлять
                                            </button>
                                        </div>
                                    )}

                                    <button
                                        type="button"
                                        onClick={requestScreenShare}
                                        className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-blue-200 bg-white px-6 text-sm font-black text-slate-800 shadow-sm transition hover:bg-blue-50 hover:text-blue-700"
                                    >
                                        <Share2 size={20} />
                                        Показать экран
                                    </button>

                                    <button
                                        type="button"
                                        onClick={leaveSession}
                                        className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-red-200 bg-white px-6 text-sm font-black text-red-600 shadow-sm transition hover:bg-red-50"
                                    >
                                        <LogOut size={20} />
                                        Выйти
                                    </button>
                                </div>
                            )}
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

                    {sessionEnded ? (
                        <SessionEndedBlock onGoHome={() => navigate("/pcs")} />
                    ) : loading ? (
                        <LoadingBlock text="Загрузка учебной сессии..." />
                    ) : (
                        <div className="grid grid-cols-[minmax(0,1fr)_360px] gap-6 max-2xl:grid-cols-1">
                            <div className="grid gap-5">
                                <EducationStreamPanel
                                    pc={teacherPc}
                                    title="Экран преподавателя"
                                    subtitle={
                                        activeControlEnabled
                                            ? "Вы управляете экраном преподавателя"
                                            : controlAllowed
                                                ? "Управление разрешено, но сейчас включён режим просмотра"
                                                : title
                                    }
                                    showQualityControls
                                    qualityValue={selectedQuality}
                                    onQualityChange={changeQuality}
                                    controlEnabled={activeControlEnabled}
                                    controlPcId={teacherPc?.id || 0}
                                    controlLabel="Управление экраном преподавателя активно"
                                />
                            </div>

                            <aside className="grid content-start gap-5">
                                <ParticipantsPanel
                                    participants={participants}
                                    currentUsername={currentUsername}
                                />

                                <ChatPanel
                                    messages={messages}
                                    chatText={chatText}
                                    currentUsername={currentUsername}
                                    session={session}
                                    participants={participants}
                                    onChatTextChange={setChatText}
                                    onSend={sendMessage}
                                />
                            </aside>
                        </div>
                    )}
                </section>
            </div>

            <EducationFilesModal
                open={filesModalOpen}
                sessionCode={sessionCode}
                participants={participants}
                onClose={() => setFilesModalOpen(false)}
            />
        </main>
    );
}

function StudentSidebar({ onFilesClick }: { onFilesClick: () => void }) {
    const username = getUserDisplayName(localStorage.getItem("username") || "Пользователь");

    return (
        <aside className="flex min-h-screen flex-col border-r border-slate-800 bg-slate-950 p-6 text-white max-xl:hidden">
            <div className="mb-10 flex items-center gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-600 text-white">
                    <Monitor size={23} />
                </div>

                <div className="text-2xl font-black">
                    Remo<span className="text-blue-400">Desk</span>
                </div>
            </div>

            <nav className="grid gap-2">
                <SidebarItem icon={<Monitor size={21} />} label="Сессия" active />
                <SidebarItem icon={<FileText size={21} />} label="Файлы" onClick={onFilesClick} />
            </nav>

            <section className="mt-auto flex items-center gap-3 rounded-[24px] border border-slate-700 bg-slate-900 p-4 shadow-sm">
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-blue-500/20 text-sm font-black text-blue-200">
                    {getInitials(username)}
                </div>

                <div>
                    <div className="font-black text-white">{username}</div>
                    <div className="text-sm font-semibold text-slate-400">Студент</div>
                </div>
            </section>
        </aside>
    );
}

function SidebarItem({
                         icon,
                         label,
                         active,
                         onClick,
                     }: {
    icon: ReactNode;
    label: string;
    active?: boolean;
    onClick?: () => void;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={
                active
                    ? "flex h-14 items-center gap-4 rounded-2xl bg-blue-600 px-4 text-left font-black text-white"
                    : "flex h-14 items-center gap-4 rounded-2xl px-4 text-left font-bold text-slate-300 transition hover:bg-slate-900 hover:text-white"
            }
        >
            {icon}
            {label}
        </button>
    );
}

function ParticipantsPanel({
                               participants,
                               currentUsername,
                           }: {
    participants: EducationParticipantResponse[];
    currentUsername: string;
}) {
    return (
        <section className="rounded-[30px] border border-slate-300 bg-white p-5 shadow-sm">
            <div className="mb-5 flex items-center justify-between">
                <h2 className="text-xl font-black text-slate-950">
                    Участники ({participants.length})
                </h2>

                <Users size={22} className="text-slate-500" />
            </div>

            <div className="grid gap-4">
                {participants.map((participant, index) => (
                    <ParticipantRow
                        key={`${participant.role || "role"}-${participant.username || participant.id || index}`}
                        participant={participant}
                        currentUsername={currentUsername}
                    />
                ))}
            </div>
        </section>
    );
}

function ParticipantRow({
                            participant,
                            currentUsername,
                        }: {
    participant: EducationParticipantResponse;
    currentUsername: string;
}) {
    const status = getParticipantStatus(participant);
    const displayName = getParticipantDisplayName(participant);
    const isTeacher = String(participant.role || "").toUpperCase() === "TEACHER";
    const isMe =
        !isTeacher &&
        Boolean(currentUsername) &&
        String(participant.username || "").toLowerCase() === currentUsername.toLowerCase();

    return (
        <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-blue-100 text-sm font-black text-blue-700">
                {getInitials(displayName)}
            </div>

            <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                    <p className="truncate text-sm font-black text-slate-900">
                        {displayName}
                    </p>

                    {isTeacher && (
                        <span className="rounded-md bg-slate-900 px-2 py-0.5 text-[10px] font-black text-white">
                            Преподаватель
                        </span>
                    )}

                    {isMe && (
                        <span className="rounded-md bg-blue-600 px-2 py-0.5 text-[10px] font-black text-white">
                            Вы
                        </span>
                    )}
                </div>

                <div className={`mt-1 text-xs font-bold ${status.className}`}>
                    ● {isTeacher ? "Ведёт сессию" : status.label}
                </div>
            </div>
        </div>
    );
}

function ChatPanel({
                       messages,
                       chatText,
                       currentUsername,
                       session,
                       participants,
                       onChatTextChange,
                       onSend,
                   }: {
    messages: EducationChatMessageResponse[];
    chatText: string;
    currentUsername: string;
    session: EducationSessionResponse | null;
    participants: EducationParticipantResponse[];
    onChatTextChange: (value: string) => void;
    onSend: () => void;
}) {
    return (
        <section className="rounded-[30px] border border-slate-300 bg-white p-5 shadow-sm">
            <h2 className="mb-5 text-xl font-black text-slate-950">Чат</h2>

            <div className="mb-5 grid max-h-[520px] gap-4 overflow-y-auto pr-1">
                {messages.length === 0 ? (
                    <EmptyState text="Сообщений пока нет." />
                ) : (
                    messages.map((message, index) => {
                        const mine = isMyMessage(message, currentUsername);
                        const author = getMessageAuthor(message, session, participants);
                        const bubbleClass = mine
                            ? "bg-blue-600 text-white"
                            : getForeignMessageColorClass(getMessageSenderKey(message, session, participants));

                        return (
                            <div
                                key={message.id || index}
                                className={mine ? "flex justify-end" : "flex justify-start"}
                            >
                                <div className={mine ? "max-w-[82%] text-right" : "max-w-[82%] text-left"}>
                                    <div
                                        className={
                                            mine
                                                ? "mb-1 flex items-center justify-end gap-2 text-xs font-bold text-slate-400"
                                                : "mb-1 flex items-center gap-2 text-xs font-bold text-slate-400"
                                        }
                                    >
                                        <span>{author}</span>
                                        <span>{formatEducationTime(message.createdAt)}</span>
                                    </div>

                                    <div className={`rounded-2xl px-4 py-3 text-sm font-bold leading-6 shadow-sm ${bubbleClass}`}>
                                        {message.message || message.text || ""}
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

function buildStudentVisibleParticipants(
    session: EducationSessionResponse | null,
    currentUsername: string,
    currentDisplayName: string,
    myParticipant: EducationParticipantResponse | null,
): EducationParticipantResponse[] {
    const result: EducationParticipantResponse[] = [];

    const teacherName =
        session?.teacherDisplayName ||
        session?.teacherFullName ||
        session?.teacherUsername ||
        "Преподаватель";

    result.push({
        id: session?.teacherId,
        username: session?.teacherUsername || "teacher",
        displayName: teacherName,
        role: "TEACHER",
        status: "ONLINE",
        online: true,
    });

    if (myParticipant) {
        result.push({
            ...myParticipant,
            role: myParticipant.role || "STUDENT",
        });
    } else if (currentUsername) {
        result.push({
            username: currentUsername,
            displayName: currentDisplayName || currentUsername,
            role: "STUDENT",
            status: "ONLINE",
            online: true,
        });
    }

    return uniqueParticipants(result);
}

function uniqueParticipants(participants: EducationParticipantResponse[]): EducationParticipantResponse[] {
    const map = new Map<string, EducationParticipantResponse>();

    participants.forEach((participant, index) => {
        const key = `${participant.role || "USER"}-${participant.username || participant.displayName || participant.id || index}`;

        const existing = map.get(key);

        if (!existing) {
            map.set(key, participant);
            return;
        }

        map.set(key, {
            ...existing,
            ...participant,
            hasControl: existing.hasControl || participant.hasControl,
            controlAllowed: existing.controlAllowed || participant.controlAllowed,
            controlRequested: existing.controlRequested || participant.controlRequested,
            screenSharing: existing.screenSharing || participant.screenSharing,
            screenShareRequested: existing.screenShareRequested || participant.screenShareRequested,
        });
    });

    return Array.from(map.values());
}

function SessionEndedBlock({ onGoHome }: { onGoHome: () => void }) {
    return (
        <div className="rounded-[34px] border border-slate-300 bg-white p-10 text-center shadow-sm">
            <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-[28px] bg-red-50 text-red-600">
                <LogOut size={42} />
            </div>

            <h2 className="text-3xl font-black text-slate-950">
                Сессия завершена
            </h2>

            <p className="mx-auto mt-4 max-w-xl text-base font-semibold leading-7 text-slate-600">
                Преподаватель завершил учебную сессию. Подключение к экрану и чату больше недоступно.
            </p>

            <button
                type="button"
                onClick={onGoHome}
                className="mt-7 inline-flex h-14 items-center justify-center rounded-2xl bg-blue-600 px-8 text-base font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700"
            >
                На рабочий стол
            </button>
        </div>
    );
}

function LoadingBlock({ text }: { text: string }) {
    return (
        <div className="rounded-[30px] border border-slate-300 bg-white p-10 text-center shadow-sm">
            <div className="mx-auto mb-4 h-12 w-12 animate-spin rounded-full border-4 border-blue-100 border-t-blue-600" />
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

function isSessionFinished(session: EducationSessionResponse | null): boolean {
    const status = String(session?.status || "").toUpperCase();

    return (
        status === "FINISHED" ||
        status === "CANCELLED" ||
        status === "CANCELED" ||
        status === "ENDED" ||
        status === "CLOSED"
    );
}

function isControlAllowed(participant: EducationParticipantResponse | null): boolean {
    if (!participant) {
        return false;
    }

    const status = String(participant.status || "").toUpperCase();

    return (
        participant.hasControl === true ||
        participant.controlAllowed === true ||
        status === "CONTROL_ALLOWED" ||
        status === "CONTROL_GRANTED" ||
        status === "HAS_CONTROL"
    );
}

function isControlRequested(participant: EducationParticipantResponse | null): boolean {
    if (!participant) {
        return false;
    }

    const status = String(participant.status || "").toUpperCase();

    return (
        participant.controlRequested === true ||
        status === "CONTROL_REQUESTED" ||
        status.includes("CONTROL_REQUEST")
    );
}

function isMyMessage(message: EducationChatMessageResponse, currentUsername: string): boolean {
    const sender = String(
        message.senderUsername ||
        message.username ||
        "",
    ).toLowerCase();

    return Boolean(currentUsername) && sender === currentUsername.toLowerCase();
}

function getMessageAuthor(
    message: EducationChatMessageResponse,
    session: EducationSessionResponse | null,
    participants: EducationParticipantResponse[],
): string {
    return resolveMessageDisplayName(message, session, participants);
}

function getMessageSenderKey(
    message: EducationChatMessageResponse,
    session: EducationSessionResponse | null,
    participants: EducationParticipantResponse[],
): string {
    return (
        message.senderUsername ||
        message.username ||
        resolveMessageDisplayName(message, session, participants) ||
        "unknown"
    );
}

function resolveMessageDisplayName(
    message: EducationChatMessageResponse,
    session: EducationSessionResponse | null,
    participants: EducationParticipantResponse[],
): string {
    if (message.senderDisplayName && message.senderDisplayName.trim()) {
        return message.senderDisplayName.trim();
    }

    if (message.senderFullName && message.senderFullName.trim()) {
        return message.senderFullName.trim();
    }

    const senderUsername = String(message.senderUsername || message.username || "").toLowerCase();

    if (
        senderUsername &&
        session?.teacherUsername &&
        senderUsername === String(session.teacherUsername).toLowerCase()
    ) {
        return (
            session.teacherDisplayName ||
            session.teacherFullName ||
            session.teacherUsername ||
            "Преподаватель"
        );
    }

    if (
        message.senderId !== undefined &&
        session?.teacherId !== undefined &&
        Number(message.senderId) === Number(session.teacherId)
    ) {
        return (
            session.teacherDisplayName ||
            session.teacherFullName ||
            session.teacherUsername ||
            "Преподаватель"
        );
    }

    const participant = participants.find((item) => {
        const sameUsername =
            senderUsername &&
            String(item.username || "").toLowerCase() === senderUsername;

        const sameId =
            message.senderId !== undefined &&
            [item.id, item.userId, item.studentId]
                .filter((value) => value !== undefined && value !== null)
                .some((value) => Number(value) === Number(message.senderId));

        return sameUsername || sameId;
    });

    if (participant) {
        return getParticipantDisplayName(participant);
    }

    return (
        message.displayName ||
        message.senderUsername ||
        message.username ||
        "Пользователь"
    );
}

function getForeignMessageColorClass(senderKey: string): string {
    const classes = [
        "bg-amber-100 text-amber-950 border border-amber-200",
        "bg-emerald-100 text-emerald-950 border border-emerald-200",
        "bg-violet-100 text-violet-950 border border-violet-200",
        "bg-rose-100 text-rose-950 border border-rose-200",
        "bg-cyan-100 text-cyan-950 border border-cyan-200",
        "bg-lime-100 text-lime-950 border border-lime-200",
        "bg-orange-100 text-orange-950 border border-orange-200",
    ];

    return classes[hashString(senderKey) % classes.length];
}

function hashString(value: string): number {
    let hash = 0;

    for (let i = 0; i < value.length; i += 1) {
        hash = (hash * 31 + value.charCodeAt(i)) >>> 0;
    }

    return hash;
}

function getQualityLabel(value: string): string {
    if (value === "854x480") {
        return "480p";
    }

    if (value === "1920x1080") {
        return "1080p";
    }

    return "720p";
}