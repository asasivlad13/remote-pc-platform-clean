import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
    Check,
    ClipboardCopy,
    FileText,
    LogOut,
    Monitor,
    RefreshCw,
    Send,
    Share2,
    ShieldCheck,
    Users,
    X,
} from "lucide-react";
import {
    approveEducationParticipant,
    finishEducationSession,
    getActiveEducationScreenShare,
    getEducationChatMessages,
    getEducationEvents,
    getEducationParticipants,
    getEducationSession,
    grantEducationControl,
    grantEducationScreenShare,
    rejectEducationControl,
    rejectEducationParticipant,
    rejectEducationScreenShare,
    revokeEducationControl,
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
    ActiveScreenShareResponse,
    EducationChatMessageResponse,
    EducationEventResponse,
    EducationParticipantResponse,
    EducationSessionResponse,
} from "../features/education/educationTypes";
import type { PcDetailsResponse } from "../features/pcs/pcTypes";
import { EducationStreamPanel } from "../features/education/components/EducationStreamPanel";
import { EducationFilesModal } from "../features/education/components/EducationFilesModal";
import { getUserDisplayName } from "../features/profile/userDisplayName";

type TeacherToastKind = "join" | "control" | "screen";

type TeacherToast = {
    id: number;
    kind: TeacherToastKind;
    title: string;
    text: string;
    participantId?: number;
};

export function EducationTeacherPage() {
    const navigate = useNavigate();
    const { sessionCode = "" } = useParams();
    const currentUsername = localStorage.getItem("username") || "";

    const [session, setSession] = useState<EducationSessionResponse | null>(null);
    const [participants, setParticipants] = useState<EducationParticipantResponse[]>([]);
    const [events, setEvents] = useState<EducationEventResponse[]>([]);
    const [messages, setMessages] = useState<EducationChatMessageResponse[]>([]);
    const [chatText, setChatText] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const [participantsModalOpen, setParticipantsModalOpen] = useState(false);
    const [historyModalOpen, setHistoryModalOpen] = useState(false);
    const [filesModalOpen, setFilesModalOpen] = useState(false);
    const [toasts, setToasts] = useState<TeacherToast[]>([]);
    const [activeScreenShare, setActiveScreenShare] = useState<ActiveScreenShareResponse | null>(null);
    const [showTeacherScreen, setShowTeacherScreen] = useState(false);

    const activeNotificationKeysRef = useRef<Set<string>>(new Set());

    const title = getEducationTitle(session);
    const teacherPc = useMemo(() => buildTeacherPc(session), [session]);
    const studentScreenPc = useMemo(() => buildActiveScreenSharePc(activeScreenShare), [activeScreenShare]);
    const filteredEvents = useMemo(() => filterActionEvents(events), [events]);

    const streamPc = showTeacherScreen ? teacherPc : studentScreenPc || teacherPc;
    const activeStudentName = getActiveScreenShareDisplayName(activeScreenShare);

    const streamTitle =
        studentScreenPc && !showTeacherScreen
            ? `Экран студента: ${activeStudentName}`
            : "Экран преподавателя";

    const streamSubtitle =
        studentScreenPc && !showTeacherScreen
            ? "Студент показывает свой экран после разрешения преподавателя"
            : title;

    useEffect(() => {
        void loadData();

        const timer = window.setInterval(() => {
            void loadData(false);
        }, 5000);

        return () => window.clearInterval(timer);
    }, [sessionCode]);

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

            const [
                sessionResult,
                participantsResult,
                eventsResult,
                messagesResult,
                activeScreenShareResult,
            ] = await Promise.all([
                getEducationSession(sessionCode),
                getEducationParticipants(sessionCode),
                getEducationEvents(sessionCode),
                getEducationChatMessages(sessionCode),
                getActiveEducationScreenShare(sessionCode),
            ]);

            setSession(sessionResult);
            setParticipants(participantsResult);
            setEvents(eventsResult);
            setMessages(messagesResult);
            setActiveScreenShare(activeScreenShareResult);

            if (!activeScreenShareResult) {
                setShowTeacherScreen(false);
            }

            processTeacherNotifications(participantsResult);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Не удалось загрузить учебную сессию");
        } finally {
            setLoading(false);
        }
    }

    function processTeacherNotifications(currentParticipants: EducationParticipantResponse[]) {
        const previousActiveKeys = activeNotificationKeysRef.current;
        const nextActiveKeys = new Set<string>();

        currentParticipants.forEach((participant) => {
            const participantKey = getParticipantNotificationKey(participant);
            const displayName = getParticipantDisplayName(participant);

            if (isWaitingJoinRequest(participant)) {
                const key = `join-${participantKey}`;
                nextActiveKeys.add(key);

                if (!previousActiveKeys.has(key)) {
                    addTeacherToast({
                        kind: "join",
                        participantId: participant.id,
                        title: "Новый запрос на вход",
                        text: `${displayName} хочет подключиться к учебной сессии.`,
                    });
                }
            }

            if (isControlRequest(participant) && !isWaitingJoinRequest(participant)) {
                const key = `control-${participantKey}`;
                nextActiveKeys.add(key);

                if (!previousActiveKeys.has(key)) {
                    addTeacherToast({
                        kind: "control",
                        participantId: participant.id,
                        title: "Запрос управления",
                        text: `${displayName} просит разрешить управление.`,
                    });
                }
            }

            if (isScreenShareRequest(participant) && !isWaitingJoinRequest(participant)) {
                const key = `screen-${participantKey}`;
                nextActiveKeys.add(key);

                if (!previousActiveKeys.has(key)) {
                    addTeacherToast({
                        kind: "screen",
                        participantId: participant.id,
                        title: "Запрос показа экрана",
                        text: `${displayName} хочет показать свой экран преподавателю.`,
                    });
                }
            }
        });

        activeNotificationKeysRef.current = nextActiveKeys;
    }

    function addTeacherToast(toast: Omit<TeacherToast, "id">) {
        const id = Date.now() + Math.floor(Math.random() * 1000);

        setToasts((current) => [
            ...current,
            {
                id,
                ...toast,
            },
        ]);

        window.setTimeout(() => {
            removeToast(id);
        }, 9000);
    }

    function removeToast(id: number) {
        setToasts((current) => current.filter((toast) => toast.id !== id));
    }

    function openParticipantsFromToast(id: number) {
        removeToast(id);
        setParticipantsModalOpen(true);
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

    async function finishSession() {
        if (!confirm("Завершить учебную сессию?")) {
            return;
        }

        try {
            await finishEducationSession(sessionCode);
            navigate("/pcs");
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось завершить сессию");
        }
    }

    async function approveParticipant(participantId?: number) {
        if (!participantId) {
            showNotice("Участник не выбран");
            return;
        }

        try {
            await approveEducationParticipant(participantId);
            showNotice("Студент подтверждён");
            await loadData(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось подтвердить студента");
        }
    }

    async function rejectParticipant(participantId?: number) {
        if (!participantId) {
            showNotice("Участник не выбран");
            return;
        }

        try {
            await rejectEducationParticipant(participantId);
            showNotice("Студент отклонён");
            await loadData(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось отклонить студента");
        }
    }

    async function grantControl(participantId?: number) {
        if (!participantId) {
            showNotice("Участник не выбран");
            return;
        }

        try {
            await grantEducationControl(participantId);
            showNotice("Управление разрешено");
            await loadData(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось разрешить управление");
        }
    }

    async function rejectControl(participantId?: number) {
        if (!participantId) {
            showNotice("Участник не выбран");
            return;
        }

        try {
            await rejectEducationControl(participantId);
            showNotice("Запрос управления отклонён");
            await loadData(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось отклонить управление");
        }
    }

    async function revokeControl(participantId?: number) {
        if (!participantId) {
            showNotice("Участник не выбран");
            return;
        }

        try {
            await revokeEducationControl(participantId);
            showNotice("Управление отозвано");
            await loadData(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось отозвать управление");
        }
    }

    async function grantScreenShare(participantId?: number) {
        if (!participantId) {
            showNotice("Участник не выбран");
            return;
        }

        try {
            await grantEducationScreenShare(participantId);
            showNotice("Показ экрана разрешён");
            setShowTeacherScreen(false);
            await loadData(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось разрешить показ экрана");
        }
    }

    async function rejectScreenShare(participantId?: number) {
        if (!participantId) {
            showNotice("Участник не выбран");
            return;
        }

        try {
            await rejectEducationScreenShare(participantId);
            showNotice("Запрос показа экрана отклонён");
            await loadData(false);
        } catch (e) {
            showNotice(e instanceof Error ? e.message : "Не удалось отклонить показ экрана");
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
                <EducationSidebar
                    roleLabel="Преподаватель"
                    activeLabel="Сессия"
                    onParticipantsClick={() => setParticipantsModalOpen(true)}
                    onFilesClick={() => setFilesModalOpen(true)}
                    onHistoryClick={() => setHistoryModalOpen(true)}
                />

                <section className="min-w-0 p-6">
                    <header className="mb-6 rounded-[32px] border border-slate-300 bg-white p-6 shadow-sm">
                        <div className="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <div className="mb-3 inline-flex rounded-xl bg-blue-50 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-700">
                                    Активная учебная сессия
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
                                        Код: {sessionCode}
                                        <ClipboardCopy size={18} />
                                    </button>

                                    <button
                                        type="button"
                                        onClick={() => setParticipantsModalOpen(true)}
                                        className="inline-flex items-center gap-2 rounded-2xl border border-slate-300 bg-white px-4 py-2 text-base font-black text-slate-700 hover:bg-slate-50"
                                    >
                                        <Users size={18} />
                                        Участники: {participants.length}
                                    </button>

                                    <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1.5 text-sm font-black text-emerald-700">
                                        <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />
                                        Активна
                                    </span>

                                    {studentScreenPc && (
                                        <button
                                            type="button"
                                            onClick={() => setShowTeacherScreen((current) => !current)}
                                            className="inline-flex items-center gap-2 rounded-2xl border border-blue-200 bg-white px-4 py-2 text-base font-black text-blue-700 hover:bg-blue-50"
                                        >
                                            <Monitor size={18} />
                                            {showTeacherScreen ? "Экран студента" : "Экран преподавателя"}
                                        </button>
                                    )}
                                </div>
                            </div>

                            <div className="flex flex-wrap items-center gap-3">
                                <button
                                    type="button"
                                    onClick={() => void loadData(false)}
                                    className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-slate-300 bg-white px-6 text-sm font-black text-slate-700 shadow-sm transition hover:bg-slate-50"
                                >
                                    <RefreshCw size={20} />
                                    Обновить
                                </button>

                                <button
                                    type="button"
                                    onClick={finishSession}
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
                        <LoadingBlock text="Загрузка учебной сессии..." />
                    ) : (
                        <div className="grid grid-cols-[minmax(0,1fr)_370px] gap-6 max-2xl:grid-cols-1">
                            <EducationStreamPanel
                                pc={streamPc}
                                title={streamTitle}
                                subtitle={streamSubtitle}
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
                        </div>
                    )}
                </section>
            </div>

            <TeacherToastStack
                toasts={toasts}
                onOpenParticipants={openParticipantsFromToast}
                onClose={removeToast}
            />

            <ParticipantsModal
                open={participantsModalOpen}
                participants={participants}
                onClose={() => setParticipantsModalOpen(false)}
                onApprove={approveParticipant}
                onReject={rejectParticipant}
                onGrantControl={grantControl}
                onRejectControl={rejectControl}
                onRevokeControl={revokeControl}
                onGrantScreenShare={grantScreenShare}
                onRejectScreenShare={rejectScreenShare}
            />

            <EducationFilesModal
                open={filesModalOpen}
                sessionCode={sessionCode}
                participants={participants}
                onClose={() => setFilesModalOpen(false)}
            />

            <HistoryModal
                open={historyModalOpen}
                events={filteredEvents}
                onClose={() => setHistoryModalOpen(false)}
            />
        </main>
    );
}

function TeacherToastStack({
                               toasts,
                               onOpenParticipants,
                               onClose,
                           }: {
    toasts: TeacherToast[];
    onOpenParticipants: (id: number) => void;
    onClose: (id: number) => void;
}) {
    if (toasts.length === 0) {
        return null;
    }

    return (
        <div className="fixed right-6 top-6 z-[70] grid w-[min(420px,calc(100vw-32px))] gap-3">
            {toasts.map((toast) => (
                <div
                    key={toast.id}
                    className={
                        toast.kind === "join"
                            ? "rounded-[26px] border border-blue-200 bg-white p-5 shadow-2xl shadow-blue-950/15"
                            : toast.kind === "control"
                                ? "rounded-[26px] border border-amber-200 bg-white p-5 shadow-2xl shadow-amber-950/15"
                                : "rounded-[26px] border border-violet-200 bg-white p-5 shadow-2xl shadow-violet-950/15"
                    }
                >
                    <div className="flex items-start gap-4">
                        <div
                            className={
                                toast.kind === "join"
                                    ? "flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-blue-50 text-blue-700"
                                    : toast.kind === "control"
                                        ? "flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-amber-50 text-amber-700"
                                        : "flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-violet-50 text-violet-700"
                            }
                        >
                            {toast.kind === "join" ? (
                                <Users size={24} />
                            ) : toast.kind === "control" ? (
                                <Monitor size={24} />
                            ) : (
                                <Share2 size={24} />
                            )}
                        </div>

                        <div className="min-w-0 flex-1">
                            <div className="font-black text-slate-950">
                                {toast.title}
                            </div>

                            <p className="mt-1 text-sm font-semibold leading-6 text-slate-600">
                                {toast.text}
                            </p>

                            <div className="mt-4 flex flex-wrap gap-2">
                                <button
                                    type="button"
                                    onClick={() => onOpenParticipants(toast.id)}
                                    className={
                                        toast.kind === "join"
                                            ? "rounded-2xl bg-blue-600 px-4 py-2 text-sm font-black text-white hover:bg-blue-700"
                                            : toast.kind === "control"
                                                ? "rounded-2xl bg-amber-500 px-4 py-2 text-sm font-black text-white hover:bg-amber-600"
                                                : "rounded-2xl bg-violet-600 px-4 py-2 text-sm font-black text-white hover:bg-violet-700"
                                    }
                                >
                                    Открыть участников
                                </button>

                                <button
                                    type="button"
                                    onClick={() => onClose(toast.id)}
                                    className="rounded-2xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50"
                                >
                                    Закрыть
                                </button>
                            </div>
                        </div>

                        <button
                            type="button"
                            onClick={() => onClose(toast.id)}
                            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                        >
                            <X size={18} />
                        </button>
                    </div>
                </div>
            ))}
        </div>
    );
}

function EducationSidebar({
                              roleLabel,
                              activeLabel,
                              onParticipantsClick,
                              onFilesClick,
                              onHistoryClick,
                          }: {
    roleLabel: string;
    activeLabel: string;
    onParticipantsClick: () => void;
    onFilesClick: () => void;
    onHistoryClick: () => void;
}) {
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
                <SidebarItem icon={<Monitor size={21} />} label={activeLabel} active />
                <SidebarItem icon={<Users size={21} />} label="Участники" onClick={onParticipantsClick} />
                <SidebarItem icon={<FileText size={21} />} label="Файлы" onClick={onFilesClick} />
                <SidebarItem icon={<ShieldCheck size={21} />} label="История" onClick={onHistoryClick} />
            </nav>

            <section className="mt-auto flex items-center gap-3 rounded-[24px] border border-slate-700 bg-slate-900 p-4 shadow-sm">
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-blue-500/20 text-sm font-black text-blue-200">
                    {getInitials(username)}
                </div>

                <div>
                    <div className="font-black text-white">{username}</div>
                    <div className="text-sm font-semibold text-slate-400">{roleLabel}</div>
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

function ParticipantsModal({
                               open,
                               participants,
                               onClose,
                               onApprove,
                               onReject,
                               onGrantControl,
                               onRejectControl,
                               onRevokeControl,
                               onGrantScreenShare,
                               onRejectScreenShare,
                           }: {
    open: boolean;
    participants: EducationParticipantResponse[];
    onClose: () => void;
    onApprove: (participantId?: number) => void;
    onReject: (participantId?: number) => void;
    onGrantControl: (participantId?: number) => void;
    onRejectControl: (participantId?: number) => void;
    onRevokeControl: (participantId?: number) => void;
    onGrantScreenShare: (participantId?: number) => void;
    onRejectScreenShare: (participantId?: number) => void;
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
            <section className="w-full max-w-5xl overflow-hidden rounded-[34px] border border-slate-300 bg-white shadow-2xl">
                <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-7 py-6">
                    <div>
                        <div className="mb-2 inline-flex rounded-xl bg-blue-50 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-700">
                            Управление участниками
                        </div>

                        <h2 className="text-3xl font-black text-slate-950">
                            Участники учебной сессии
                        </h2>

                        <p className="mt-2 text-sm font-semibold text-slate-500">
                            Подтверждение студентов, запросы управления, показ экрана и статусы подключения.
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={onClose}
                        className="flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-300 bg-white text-slate-500 transition hover:bg-slate-50 hover:text-slate-900"
                    >
                        <X size={22} />
                    </button>
                </div>

                <div className="max-h-[70vh] overflow-y-auto p-7">
                    {participants.length === 0 ? (
                        <EmptyState text="Студенты пока не подключились к сессии." />
                    ) : (
                        <div className="grid grid-cols-2 gap-4 max-lg:grid-cols-1">
                            {participants.map((participant, index) => (
                                <ParticipantCard
                                    key={`${participant.id || participant.username || "participant"}-${index}`}
                                    participant={participant}
                                    onApprove={onApprove}
                                    onReject={onReject}
                                    onGrantControl={onGrantControl}
                                    onRejectControl={onRejectControl}
                                    onRevokeControl={onRevokeControl}
                                    onGrantScreenShare={onGrantScreenShare}
                                    onRejectScreenShare={onRejectScreenShare}
                                />
                            ))}
                        </div>
                    )}
                </div>
            </section>
        </div>
    );
}

function ParticipantCard({
                             participant,
                             onApprove,
                             onReject,
                             onGrantControl,
                             onRejectControl,
                             onRevokeControl,
                             onGrantScreenShare,
                             onRejectScreenShare,
                         }: {
    participant: EducationParticipantResponse;
    onApprove: (participantId?: number) => void;
    onReject: (participantId?: number) => void;
    onGrantControl: (participantId?: number) => void;
    onRejectControl: (participantId?: number) => void;
    onRevokeControl: (participantId?: number) => void;
    onGrantScreenShare: (participantId?: number) => void;
    onRejectScreenShare: (participantId?: number) => void;
}) {
    const status = getParticipantStatus(participant);
    const displayName = getParticipantDisplayName(participant);
    const normalizedStatus = String(participant.status || "").toUpperCase();

    const waiting = isWaitingJoinRequest(participant);
    const controlRequested = isControlRequest(participant);
    const controlAllowed = isControlAllowed(participant);
    const screenShareRequested = isScreenShareRequest(participant);
    const screenSharing = isScreenSharingParticipant(participant);

    return (
        <article className="rounded-3xl border border-slate-300 bg-slate-50 p-4">
            <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-blue-100 text-sm font-black text-blue-700">
                    {getInitials(displayName)}
                </div>

                <div className="min-w-0 flex-1">
                    <h3 className="truncate font-black text-slate-950">
                        {displayName}
                    </h3>

                    <div className={`mt-1 text-xs font-bold ${status.className}`}>
                        ● {screenSharing ? "Показывает экран" : controlAllowed ? "Управление разрешено" : status.label}
                    </div>

                    {normalizedStatus && (
                        <div className="mt-1 text-[11px] font-bold uppercase tracking-wide text-slate-400">
                            {normalizedStatus}
                        </div>
                    )}
                </div>
            </div>

            {waiting && (
                <div className="mt-4 grid grid-cols-2 gap-2">
                    <button
                        type="button"
                        onClick={() => onApprove(participant.id)}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-2xl bg-blue-600 text-xs font-black text-white"
                    >
                        <Check size={16} />
                        Подтвердить
                    </button>

                    <button
                        type="button"
                        onClick={() => onReject(participant.id)}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-2xl border border-red-200 bg-white text-xs font-black text-red-600"
                    >
                        <X size={16} />
                        Отклонить
                    </button>
                </div>
            )}

            {controlAllowed && !waiting && (
                <div className="mt-4">
                    <button
                        type="button"
                        onClick={() => onRevokeControl(participant.id)}
                        className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-2xl border border-red-200 bg-white text-xs font-black text-red-600 transition hover:bg-red-50"
                    >
                        <X size={16} />
                        Отозвать управление
                    </button>
                </div>
            )}

            {screenShareRequested && !waiting && (
                <div className="mt-4 grid grid-cols-2 gap-2">
                    <button
                        type="button"
                        onClick={() => onGrantScreenShare(participant.id)}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-2xl bg-violet-600 text-xs font-black text-white"
                    >
                        <Share2 size={16} />
                        Разрешить показ
                    </button>

                    <button
                        type="button"
                        onClick={() => onRejectScreenShare(participant.id)}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-2xl border border-red-200 bg-white text-xs font-black text-red-600"
                    >
                        <X size={16} />
                        Отклонить
                    </button>
                </div>
            )}

            {controlRequested && !waiting && !controlAllowed && (
                <div className="mt-4 grid grid-cols-2 gap-2">
                    <button
                        type="button"
                        onClick={() => onGrantControl(participant.id)}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-2xl bg-blue-600 text-xs font-black text-white"
                    >
                        <Check size={16} />
                        Разрешить управление
                    </button>

                    <button
                        type="button"
                        onClick={() => onRejectControl(participant.id)}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-2xl border border-red-200 bg-white text-xs font-black text-red-600"
                    >
                        <X size={16} />
                        Отклонить
                    </button>
                </div>
            )}

            {screenSharing && !screenShareRequested && (
                <div className="mt-4 rounded-2xl border border-violet-200 bg-violet-50 px-4 py-3 text-xs font-black text-violet-700">
                    Экран этого студента сейчас может отображаться у преподавателя.
                </div>
            )}
        </article>
    );
}

function HistoryModal({
                          open,
                          events,
                          onClose,
                      }: {
    open: boolean;
    events: EducationEventResponse[];
    onClose: () => void;
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
            <section className="w-full max-w-4xl overflow-hidden rounded-[34px] border border-slate-300 bg-white shadow-2xl">
                <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-7 py-6">
                    <div>
                        <div className="mb-2 inline-flex rounded-xl bg-blue-50 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-700">
                            Журнал действий
                        </div>

                        <h2 className="text-3xl font-black text-slate-950">
                            История учебной сессии
                        </h2>

                        <p className="mt-2 text-sm font-semibold text-slate-500">
                            Здесь отображаются подключения, подтверждения, отказы, запросы управления и демонстрации экрана.
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={onClose}
                        className="flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-300 bg-white text-slate-500 transition hover:bg-slate-50 hover:text-slate-900"
                    >
                        <X size={22} />
                    </button>
                </div>

                <div className="max-h-[70vh] overflow-y-auto p-7">
                    {events.length === 0 ? (
                        <EmptyState text="Действий пока нет." />
                    ) : (
                        <div className="grid gap-4">
                            {events.map((event) => (
                                <div
                                    key={event.id || `${event.message}-${event.createdAt}`}
                                    className="flex gap-3 rounded-2xl bg-slate-50 p-4"
                                >
                                    <div className="mt-1 h-3 w-3 shrink-0 rounded-full bg-blue-600" />

                                    <div>
                                        <p className="text-sm font-bold leading-6 text-slate-800">
                                            {event.message || "Действие пользователя"}
                                        </p>

                                        <p className="mt-1 text-xs font-semibold text-slate-500">
                                            {formatEducationTime(event.createdAt)}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </section>
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

            <div className="mb-5 grid max-h-[640px] gap-4 overflow-y-auto pr-1">
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

function buildActiveScreenSharePc(activeScreenShare: ActiveScreenShareResponse | null): PcDetailsResponse | null {
    if (!activeScreenShare) {
        return null;
    }

    const active = activeScreenShare.active !== false;
    const agent = activeScreenShare.agent;

    const webrtcUrl = agent?.webrtcUrl || activeScreenShare.webrtcUrl || "";
    const streamName = agent?.streamName || activeScreenShare.streamName || "";

    if (!active || !webrtcUrl || !streamName) {
        return null;
    }

    return {
        id:
            agent?.pcId ||
            agent?.id ||
            activeScreenShare.pcId ||
            activeScreenShare.id ||
            activeScreenShare.participantId ||
            0,
        name:
            agent?.pcName ||
            agent?.name ||
            activeScreenShare.pcName ||
            activeScreenShare.name ||
            getActiveScreenShareDisplayName(activeScreenShare),
        macAddress:
            agent?.pcMacAddress ||
            agent?.macAddress ||
            activeScreenShare.pcMacAddress ||
            activeScreenShare.macAddress ||
            "",
        status:
            agent?.pcStatus ||
            agent?.status ||
            activeScreenShare.pcStatus ||
            activeScreenShare.status ||
            "ONLINE",
        lastConnection: null,
        screenWidth:
            agent?.screenWidth ||
            activeScreenShare.screenWidth ||
            1280,
        screenHeight:
            agent?.screenHeight ||
            activeScreenShare.screenHeight ||
            720,
        webrtcUrl,
        streamName,
    } as PcDetailsResponse;
}

function getActiveScreenShareDisplayName(activeScreenShare: ActiveScreenShareResponse | null): string {
    if (!activeScreenShare) {
        return "Студент";
    }

    return (
        activeScreenShare.participantDisplayName ||
        activeScreenShare.participantFullName ||
        activeScreenShare.displayName ||
        activeScreenShare.fullName ||
        activeScreenShare.participantUsername ||
        activeScreenShare.username ||
        "Студент"
    );
}

function isWaitingJoinRequest(participant: EducationParticipantResponse): boolean {
    const status = String(participant.status || "").toUpperCase();

    return (
        status === "WAITING" ||
        status === "PENDING" ||
        status === "REQUESTED" ||
        status.includes("WAIT")
    );
}

function isControlRequest(participant: EducationParticipantResponse): boolean {
    const status = String(participant.status || "").toUpperCase();

    return (
        participant.controlRequested === true ||
        status === "CONTROL_REQUESTED"
    );
}

function isControlAllowed(participant: EducationParticipantResponse): boolean {
    const status = String(participant.status || "").toUpperCase();

    return (
        participant.hasControl === true ||
        participant.controlAllowed === true ||
        status === "CONTROL_ALLOWED" ||
        status === "CONTROL_GRANTED" ||
        status === "HAS_CONTROL"
    );
}

function isScreenShareRequest(participant: EducationParticipantResponse): boolean {
    const status = String(participant.status || "").toUpperCase();

    return (
        participant.screenShareRequested === true ||
        participant.screenSharingRequested === true ||
        status === "SCREEN_SHARE_REQUESTED" ||
        status === "SCREEN_SHARING_REQUESTED" ||
        status.includes("SCREEN_SHARE_REQUEST")
    );
}

function isScreenSharingParticipant(participant: EducationParticipantResponse): boolean {
    const status = String(participant.status || "").toUpperCase();

    return (
        participant.screenSharing === true ||
        participant.screenShareActive === true ||
        status === "SCREEN_SHARING"
    );
}

function getParticipantNotificationKey(participant: EducationParticipantResponse): string {
    return String(
        participant.id ||
        participant.userId ||
        participant.studentId ||
        participant.username ||
        participant.displayName ||
        participant.fullName ||
        "unknown",
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

function filterActionEvents(events: EducationEventResponse[]): EducationEventResponse[] {
    return events.filter((event) => {
        const type = String(event.type || "").toUpperCase();
        const message = String(event.message || "").toLowerCase();

        if (type.includes("CHAT") || type.includes("MESSAGE")) {
            return false;
        }

        if (message.includes("сообщен") || message.includes("message") || message.includes("chat")) {
            return false;
        }

        return true;
    });
}