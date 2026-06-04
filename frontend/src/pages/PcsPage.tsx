import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import {
    ArrowRight,
    GraduationCap,
    Headphones,
    Loader2,
    LogIn,
    Monitor,
    MoreHorizontal,
    Power,
    ShieldCheck,
    Smartphone,
    Sparkles,
    UserRound,
    X,
} from "lucide-react";
import { DashboardLayout } from "../shared/ui/DashboardLayout";
import { getMyPcs } from "../features/pcs/pcApi";
import type { PcDetailsResponse } from "../features/pcs/pcTypes";
import { getUserDisplayName } from "../features/profile/userDisplayName";
import {
    createSupportSession,
    joinSupportSession,
} from "../features/support/supportApi";

type ModalName =
    | "connection"
    | "createEducation"
    | "createSupport"
    | "joinEducation"
    | "joinSupport"
    | null;

type ApiErrorLike = {
    message?: string;
    error?: string;
    code?: string;
};

type CreateEducationSessionResponse = {
    sessionCode: string;
    title?: string;
};

type JoinEducationParticipantResponse = {
    id?: number;
    sessionCode?: string;
    status?: string;
    displayName?: string;
    username?: string;
    approvedAt?: string | null;
};

function getApiBaseUrl(): string {
    const explicitUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

    if (explicitUrl && explicitUrl.trim()) {
        return explicitUrl.trim();
    }

    return `${window.location.protocol}//${window.location.hostname}:8080`;
}

function getAuthToken(): string {
    return localStorage.getItem("token") || "";
}

function getAuthHeaders(json = false): HeadersInit {
    const token = getAuthToken();
    const headers: Record<string, string> = {};

    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    if (json) {
        headers["Content-Type"] = "application/json";
    }

    return headers;
}

async function readJsonOrThrow<T>(
    response: Response,
    fallbackMessage: string,
): Promise<T> {
    const text = await response.text();

    if (!response.ok) {
        let message = text || fallbackMessage;

        try {
            const json = JSON.parse(text) as ApiErrorLike;
            message = json.message || json.error || json.code || fallbackMessage;
        } catch {
            // обычный текст
        }

        throw new Error(message);
    }

    if (!text.trim()) {
        return {} as T;
    }

    return JSON.parse(text) as T;
}

async function createEducationSessionRequest({
                                                 teacherPcId,
                                                 title,
                                             }: {
    teacherPcId: number;
    title: string;
}): Promise<CreateEducationSessionResponse> {
    const response = await fetch(`${getApiBaseUrl()}/api/education/sessions`, {
        method: "POST",
        headers: getAuthHeaders(true),
        body: JSON.stringify({
            teacherPcId,
            title: title.trim() || "Учебная сессия",
            teacherDisplayName: getUserDisplayName("Преподаватель"),
            maxStudents: 30,
            allowStudentControl: true,
            allowFileTransfer: true,
            allowStudentScreenShare: true,
        }),
    });

    return readJsonOrThrow<CreateEducationSessionResponse>(
        response,
        "Не удалось создать учебную сессию",
    );
}

async function joinEducationSessionRequest({
                                               sessionCode,
                                               displayName,
                                           }: {
    sessionCode: string;
    displayName: string;
}): Promise<JoinEducationParticipantResponse> {
    const response = await fetch(`${getApiBaseUrl()}/api/education/participants/join`, {
        method: "POST",
        headers: getAuthHeaders(true),
        body: JSON.stringify({
            sessionCode,
            displayName,
        }),
    });

    return readJsonOrThrow<JoinEducationParticipantResponse>(
        response,
        "Учебная сессия с таким кодом не найдена",
    );
}

async function getMyEducationParticipantStatus(
    sessionCode: string,
): Promise<JoinEducationParticipantResponse> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/participants/my/${encodeURIComponent(sessionCode)}`,
        {
            headers: getAuthHeaders(),
        },
    );

    return readJsonOrThrow<JoinEducationParticipantResponse>(
        response,
        "Не удалось проверить статус заявки",
    );
}

export function PcsPage() {
    const navigate = useNavigate();

    const educationWaitTimerRef = useRef<number | null>(null);

    const [pcs, setPcs] = useState<PcDetailsResponse[]>([]);
    const [searchValue, setSearchValue] = useState("");
    const [selectedPc, setSelectedPc] = useState<PcDetailsResponse | null>(null);
    const [activeModal, setActiveModal] = useState<ModalName>(null);

    /*
     * ВАЖНО:
     * На iPhone Safari обычный matchMedia("(pointer: coarse)") иногда ведёт себя нестабильно,
     * особенно если включена desktop-версия сайта. Поэтому проверяем сразу:
     * - ширину экрана;
     * - touch points;
     * - userAgent;
     * - coarse pointer / hover none.
     */
    const [mobileScenarioVisible, setMobileScenarioVisible] = useState(() => isMobileRemoteDevice());

    const [educationTitle, setEducationTitle] = useState("");
    const [supportTitle, setSupportTitle] = useState("");
    const [educationJoinCode, setEducationJoinCode] = useState("");
    const [supportJoinCode, setSupportJoinCode] = useState("");

    const [educationWaiting, setEducationWaiting] = useState(false);
    const [educationWaitStatus, setEducationWaitStatus] = useState("");
    const [educationWaitName, setEducationWaitName] = useState("");

    const [loading, setLoading] = useState(true);
    const [actionLoading, setActionLoading] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");

    const filteredPcs = useMemo(() => {
        const query = searchValue.trim().toLowerCase();

        if (!query) {
            return pcs;
        }

        return pcs.filter((pc) => {
            const name = getPcName(pc).toLowerCase();
            const mac = getPcMac(pc).toLowerCase();
            const status = getPcStatus(pc).toLowerCase();

            return (
                name.includes(query) ||
                mac.includes(query) ||
                status.includes(query)
            );
        });
    }, [pcs, searchValue]);

    const onlineCount = pcs.filter((pc) => isPcOnline(pc)).length;
    const offlineCount = Math.max(0, pcs.length - onlineCount);

    useEffect(() => {
        void loadPcs();

        const timer = window.setInterval(() => {
            void loadPcs(false);
        }, 7000);

        return () => {
            window.clearInterval(timer);
            stopEducationWaitingTimer();
        };
    }, []);

    useEffect(() => {
        function updateMobileScenarioVisibility() {
            setMobileScenarioVisible(isMobileRemoteDevice());
        }

        updateMobileScenarioVisibility();

        window.addEventListener("resize", updateMobileScenarioVisibility);
        window.addEventListener("orientationchange", updateMobileScenarioVisibility);

        const coarsePointer = window.matchMedia?.("(pointer: coarse)");
        const hoverNone = window.matchMedia?.("(hover: none)");
        const smallScreen = window.matchMedia?.("(max-width: 1100px)");

        coarsePointer?.addEventListener?.("change", updateMobileScenarioVisibility);
        hoverNone?.addEventListener?.("change", updateMobileScenarioVisibility);
        smallScreen?.addEventListener?.("change", updateMobileScenarioVisibility);

        return () => {
            window.removeEventListener("resize", updateMobileScenarioVisibility);
            window.removeEventListener("orientationchange", updateMobileScenarioVisibility);

            coarsePointer?.removeEventListener?.("change", updateMobileScenarioVisibility);
            hoverNone?.removeEventListener?.("change", updateMobileScenarioVisibility);
            smallScreen?.removeEventListener?.("change", updateMobileScenarioVisibility);
        };
    }, []);

    async function loadPcs(showLoading = true) {
        try {
            if (showLoading) {
                setLoading(true);
            }

            setError("");

            const result = await getMyPcs();
            setPcs(Array.isArray(result) ? result : []);
        } catch (e) {
            setPcs([]);
            setError(e instanceof Error ? e.message : "Не удалось загрузить список ПК");
        } finally {
            setLoading(false);
        }
    }

    function stopEducationWaitingTimer() {
        if (educationWaitTimerRef.current !== null) {
            window.clearInterval(educationWaitTimerRef.current);
            educationWaitTimerRef.current = null;
        }
    }

    function openConnectionModal(pc: PcDetailsResponse) {
        setSelectedPc(pc);
        setMobileScenarioVisible(isMobileRemoteDevice());
        setActiveModal("connection");
        setError("");
        setNotice("");
    }

    function closeModal() {
        stopEducationWaitingTimer();
        setActiveModal(null);
        setSelectedPc(null);
        setActionLoading(false);
        setEducationWaiting(false);
        setEducationWaitStatus("");
        setEducationWaitName("");
    }

    function openCreateEducationFromConnection() {
        if (!selectedPc) {
            return;
        }

        setEducationTitle("");
        setActiveModal("createEducation");
    }

    function openCreateSupportFromConnection() {
        setSupportTitle("");
        setActiveModal("createSupport");
    }

    function openMobileRemote() {
        if (!selectedPc) {
            return;
        }

        const pcId = getPcId(selectedPc);

        if (!pcId) {
            setError("У выбранного ПК нет корректного идентификатора");
            return;
        }

        navigate(
            `/mobile-remote?pcId=${encodeURIComponent(String(pcId))}&pcName=${encodeURIComponent(getPcName(selectedPc))}`,
        );
    }

    async function createEducationSession() {
        if (!selectedPc) {
            setError("Сначала выберите ПК преподавателя");
            return;
        }

        const teacherPcId = getPcId(selectedPc);

        if (!teacherPcId) {
            setError("У выбранного ПК нет корректного идентификатора");
            return;
        }

        try {
            setActionLoading(true);
            setError("");

            const session = await createEducationSessionRequest({
                teacherPcId,
                title: educationTitle,
            });

            navigate(`/education/teacher/${encodeURIComponent(session.sessionCode)}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Не удалось создать учебную сессию");
        } finally {
            setActionLoading(false);
        }
    }

    async function createSupportOperatorSession() {
        try {
            setActionLoading(true);
            setError("");

            const session = await createSupportSession(supportTitle.trim());

            navigate(`/support/operator/${encodeURIComponent(session.sessionCode)}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Не удалось создать сессию техподдержки");
        } finally {
            setActionLoading(false);
        }
    }

    async function joinEducationSession() {
        const code = educationJoinCode.trim();
        const displayName = getUserDisplayName("Student");

        if (!code) {
            setError("Введите код учебной сессии");
            return;
        }

        try {
            stopEducationWaitingTimer();

            setActionLoading(true);
            setError("");

            const participant = await joinEducationSessionRequest({
                sessionCode: code,
                displayName,
            });

            const actualCode = participant.sessionCode || code;

            localStorage.setItem("activeEducationSessionCode", actualCode);
            localStorage.setItem("educationSessionCode", actualCode);

            setEducationJoinCode(actualCode);
            setEducationWaitName(participant.displayName || participant.username || displayName);
            setEducationWaitStatus(participant.status || "WAITING");
            setEducationWaiting(true);

            if (isEducationApproved(participant.status)) {
                navigate(`/education/student/${encodeURIComponent(actualCode)}`);
                return;
            }

            educationWaitTimerRef.current = window.setInterval(() => {
                void pollEducationWaitingStatus(actualCode);
            }, 2000);
        } catch (e) {
            setEducationWaiting(false);
            setError(
                e instanceof Error
                    ? normalizeEducationJoinError(e.message)
                    : "Учебная сессия с таким кодом не найдена",
            );
        } finally {
            setActionLoading(false);
        }
    }

    async function pollEducationWaitingStatus(sessionCode: string) {
        try {
            const participant = await getMyEducationParticipantStatus(sessionCode);
            setEducationWaitStatus(participant.status || "WAITING");

            if (isEducationApproved(participant.status)) {
                stopEducationWaitingTimer();
                navigate(`/education/student/${encodeURIComponent(sessionCode)}`);
                return;
            }

            if (isEducationRejected(participant.status)) {
                stopEducationWaitingTimer();
                setEducationWaiting(false);
                setError("Преподаватель отклонил заявку на вход в учебную сессию");
            }
        } catch {
            // не показываем ошибку каждую секунду, просто ждём следующую попытку
        }
    }

    async function joinSupportSessionByCode() {
        const code = supportJoinCode.trim();

        if (!code) {
            setError("Введите код сессии техподдержки");
            return;
        }

        try {
            setActionLoading(true);
            setError("");

            const session = await joinSupportSession(code);

            navigate(`/support/client/${encodeURIComponent(session.sessionCode)}`);
        } catch (e) {
            setError(
                e instanceof Error
                    ? e.message
                    : "Сессия техподдержки с таким кодом не существует или завершилась",
            );
        } finally {
            setActionLoading(false);
        }
    }

    function connectPersonal() {
        if (!selectedPc) {
            return;
        }

        const pcId = getPcId(selectedPc);

        if (!pcId) {
            setError("У выбранного ПК нет корректного идентификатора");
            return;
        }

        navigate(`/remote/${pcId}`);
    }

    return (
        <DashboardLayout
            searchValue={searchValue}
            onSearchChange={setSearchValue}
            onEducationClick={() => {
                stopEducationWaitingTimer();
                setError("");
                setNotice("");
                setEducationJoinCode("");
                setEducationWaiting(false);
                setEducationWaitStatus("");
                setEducationWaitName("");
                setActiveModal("joinEducation");
            }}
            onSupportClick={() => {
                setError("");
                setNotice("");
                setSupportJoinCode("");
                setActiveModal("joinSupport");
            }}
        >
            <div className="px-5 py-10 lg:px-12">
                <div className="mb-10 grid grid-cols-[minmax(0,1fr)_360px] gap-6 max-xl:grid-cols-1">
                    <div>
                        <div className="mb-4 inline-flex items-center gap-2 rounded-2xl bg-blue-50 px-4 py-2 text-sm font-black text-blue-700">
                            <Monitor size={18} />
                            My Computers
                        </div>

                        <h1 className="text-5xl font-black tracking-tight text-slate-950 max-sm:text-4xl">
                            Рабочий стол
                        </h1>

                        <p className="mt-4 max-w-3xl text-lg font-medium leading-8 text-slate-500">
                            Выберите ПК и нажмите <b>Connection</b>, чтобы открыть обычное
                            подключение, создать учебную сессию или создать сессию техподдержки.
                        </p>
                    </div>

                    <PcHealthChart
                        total={pcs.length}
                        online={onlineCount}
                        offline={offlineCount}
                    />
                </div>

                {notice && (
                    <div className="mb-6 rounded-3xl border border-blue-200 bg-blue-50 px-5 py-4 text-sm font-black text-blue-700 shadow-sm">
                        {notice}
                    </div>
                )}

                {error && (
                    <div className="mb-6 rounded-3xl border border-red-200 bg-red-50 px-5 py-4 text-sm font-black text-red-700 shadow-sm">
                        {error}
                    </div>
                )}

                {loading ? (
                    <LoadingBlock text="Загрузка компьютеров..." />
                ) : filteredPcs.length === 0 ? (
                    <EmptyState
                        title="Компьютеры не найдены"
                        text="Проверьте, что агент запущен и пользователь вошёл в систему."
                    />
                ) : (
                    <div className="grid grid-cols-2 gap-6 max-2xl:grid-cols-1">
                        {filteredPcs.map((pc) => (
                            <PcCard
                                key={String(getPcId(pc) || getPcMac(pc) || getPcName(pc))}
                                pc={pc}
                                onConnect={() => openConnectionModal(pc)}
                            />
                        ))}
                    </div>
                )}
            </div>

            {activeModal === "connection" && selectedPc && (
                <ConnectionModal
                    pc={selectedPc}
                    onClose={closeModal}
                    onPersonal={connectPersonal}
                    onEducation={openCreateEducationFromConnection}
                    onSupport={openCreateSupportFromConnection}
                    onMobileRemote={openMobileRemote}
                    showMobileRemote={mobileScenarioVisible}
                />
            )}

            {activeModal === "createEducation" && selectedPc && (
                <BaseModal
                    title="Создать учебную сессию"
                    subtitle="Сценарий преподавателя создаётся через Connection выбранного ПК."
                    onClose={closeModal}
                >
                    <InfoBox>
                        Студенты будут входить по коду через кнопку Education слева на рабочем
                        столе сайта. Преподаватель увидит заявку и должен подтвердить вход.
                    </InfoBox>

                    <label className="mt-5 block text-sm font-black text-slate-700">
                        Название занятия
                    </label>

                    <input
                        value={educationTitle}
                        onChange={(event) => setEducationTitle(event.target.value)}
                        placeholder={`Например: Учебная сессия для ${getPcName(selectedPc)}`}
                        className="mt-3 h-14 w-full rounded-2xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100"
                    />

                    <button
                        type="button"
                        onClick={createEducationSession}
                        disabled={actionLoading}
                        className="mt-6 inline-flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-blue-600 px-6 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700 disabled:bg-slate-300"
                    >
                        {actionLoading ? <Loader2 className="animate-spin" size={20} /> : <GraduationCap size={20} />}
                        {actionLoading ? "Создание..." : "Создать сессию преподавателя"}
                    </button>
                </BaseModal>
            )}

            {activeModal === "createSupport" && (
                <BaseModal
                    title="Создать сессию техподдержки"
                    subtitle="Сценарий оператора создаётся через Connection, а клиент входит по коду через Support слева."
                    onClose={closeModal}
                >
                    <InfoBox>
                        Передайте клиенту код. Клиент войдёт через кнопку Support на рабочем
                        столе сайта, после чего оператор увидит экран клиента и сможет запросить
                        управление.
                    </InfoBox>

                    <label className="mt-5 block text-sm font-black text-slate-700">
                        Название обращения
                    </label>

                    <input
                        value={supportTitle}
                        onChange={(event) => setSupportTitle(event.target.value)}
                        placeholder="Например: помощь с настройкой программы"
                        className="mt-3 h-14 w-full rounded-2xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100"
                    />

                    <button
                        type="button"
                        onClick={createSupportOperatorSession}
                        disabled={actionLoading}
                        className="mt-6 inline-flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-blue-600 px-6 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700 disabled:bg-slate-300"
                    >
                        {actionLoading ? <Loader2 className="animate-spin" size={20} /> : <Headphones size={20} />}
                        {actionLoading ? "Создание..." : "Создать сессию оператора"}
                    </button>
                </BaseModal>
            )}

            {activeModal === "joinEducation" && (
                <BaseModal
                    title="Войти в учебную сессию"
                    subtitle="Это вход студента по коду преподавателя."
                    onClose={closeModal}
                >
                    {educationWaiting ? (
                        <EducationWaitingBlock
                            code={educationJoinCode}
                            name={educationWaitName}
                            status={educationWaitStatus}
                            onCancel={closeModal}
                        />
                    ) : (
                        <>
                            <InfoBox>
                                После отправки заявки преподаватель увидит уведомление и должен
                                подтвердить вход. Пока заявка не подтверждена, откроется окно ожидания.
                            </InfoBox>

                            <CodeInput
                                label="Код учебной сессии"
                                value={educationJoinCode}
                                onChange={setEducationJoinCode}
                                placeholder="955190"
                            />

                            <button
                                type="button"
                                onClick={joinEducationSession}
                                disabled={actionLoading}
                                className="mt-6 inline-flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-blue-600 px-6 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700 disabled:bg-slate-300"
                            >
                                {actionLoading ? <Loader2 className="animate-spin" size={20} /> : <LogIn size={20} />}
                                {actionLoading ? "Отправка заявки..." : "Отправить заявку преподавателю"}
                            </button>
                        </>
                    )}
                </BaseModal>
            )}

            {activeModal === "joinSupport" && (
                <BaseModal
                    title="Войти в сессию техподдержки"
                    subtitle="Это вход клиента по коду оператора."
                    onClose={closeModal}
                >
                    <InfoBox>
                        Создание сессии техподдержки выполняется через Connection на карточке ПК.
                        Здесь только вход клиента по готовому коду.
                    </InfoBox>

                    <CodeInput
                        label="Код сессии техподдержки"
                        value={supportJoinCode}
                        onChange={(value) => setSupportJoinCode(value.replace(/\D/g, "").slice(0, 6))}
                        placeholder="000000"
                    />

                    <button
                        type="button"
                        onClick={joinSupportSessionByCode}
                        disabled={actionLoading}
                        className="mt-6 inline-flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-emerald-600 px-6 text-sm font-black text-white shadow-lg shadow-emerald-600/20 transition hover:bg-emerald-700 disabled:bg-slate-300"
                    >
                        {actionLoading ? <Loader2 className="animate-spin" size={20} /> : <ShieldCheck size={20} />}
                        {actionLoading ? "Подключение..." : "Войти как клиент"}
                    </button>
                </BaseModal>
            )}
        </DashboardLayout>
    );
}

function EducationWaitingBlock({
                                   code,
                                   name,
                                   status,
                                   onCancel,
                               }: {
    code: string;
    name: string;
    status: string;
    onCancel: () => void;
}) {
    return (
        <div className="text-center">
            <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-[28px] bg-blue-50 text-blue-700">
                <Loader2 className="animate-spin" size={42} />
            </div>

            <h3 className="text-3xl font-black text-slate-950">
                Ожидание подтверждения
            </h3>

            <p className="mx-auto mt-3 max-w-md text-sm font-semibold leading-6 text-slate-500">
                Заявка отправлена преподавателю. После подтверждения вы автоматически
                попадёте в учебную сессию.
            </p>

            <div className="mt-6 rounded-3xl border border-blue-200 bg-blue-50 p-5 text-left">
                <div className="text-sm font-black text-blue-700">Код сессии</div>
                <div className="mt-1 text-3xl font-black tracking-[0.18em] text-slate-950">
                    {code}
                </div>

                <div className="mt-4 text-sm font-black text-blue-700">Студент</div>
                <div className="mt-1 text-base font-black text-slate-950">
                    {name || "Студент"}
                </div>

                <div className="mt-4 text-sm font-black text-blue-700">Статус</div>
                <div className="mt-1 inline-flex rounded-full bg-amber-100 px-3 py-1.5 text-sm font-black text-amber-700">
                    {translateEducationStatus(status)}
                </div>
            </div>

            <button
                type="button"
                onClick={onCancel}
                className="mt-6 inline-flex h-12 items-center justify-center rounded-2xl border border-slate-300 bg-white px-6 text-sm font-black text-slate-700 transition hover:bg-slate-50"
            >
                Закрыть окно
            </button>
        </div>
    );
}

function PcHealthChart({
                           total,
                           online,
                           offline,
                       }: {
    total: number;
    online: number;
    offline: number;
}) {
    const onlinePercent = total > 0 ? Math.round((online / total) * 100) : 0;

    return (
        <section className="overflow-hidden rounded-[34px] border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex items-center gap-6">
                <div
                    className="relative flex h-36 w-36 shrink-0 items-center justify-center rounded-full"
                    style={{
                        background:
                            total > 0
                                ? `conic-gradient(#10b981 0 ${onlinePercent}%, #cbd5e1 ${onlinePercent}% 100%)`
                                : "conic-gradient(#cbd5e1 0 100%)",
                    }}
                >
                    <div className="absolute inset-4 rounded-full bg-white" />
                    <div className="relative text-center">
                        <div className="text-4xl font-black text-slate-950">
                            {onlinePercent}%
                        </div>
                        <div className="text-xs font-black uppercase tracking-wide text-slate-400">
                            Online
                        </div>
                    </div>
                </div>

                <div className="min-w-0 flex-1">
                    <div className="mb-2 inline-flex rounded-xl bg-blue-50 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-700">
                        Account devices
                    </div>

                    <h2 className="text-2xl font-black text-slate-950">
                        ПК в аккаунте
                    </h2>

                    <div className="mt-5 grid grid-cols-3 gap-3">
                        <MiniStat label="Total" value={total} className="bg-slate-50 text-slate-900" />
                        <MiniStat label="Online" value={online} className="bg-emerald-50 text-emerald-700" />
                        <MiniStat label="Offline" value={offline} className="bg-slate-100 text-slate-600" />
                    </div>
                </div>
            </div>
        </section>
    );
}

function MiniStat({
                      label,
                      value,
                      className,
                  }: {
    label: string;
    value: number;
    className: string;
}) {
    return (
        <div className={`rounded-2xl p-4 text-center ${className}`}>
            <div className="text-2xl font-black">{value}</div>
            <div className="mt-1 text-[11px] font-black uppercase tracking-wide opacity-70">
                {label}
            </div>
        </div>
    );
}

function PcCard({
                    pc,
                    onConnect,
                }: {
    pc: PcDetailsResponse;
    onConnect: () => void;
}) {
    const previewUrl = getPcPreview(pc);
    const online = isPcOnline(pc);

    return (
        <article className="overflow-hidden rounded-[34px] border border-slate-200 bg-white p-6 shadow-sm transition duration-300 hover:-translate-y-0.5 hover:shadow-xl hover:shadow-slate-200/80">
            <div className="flex flex-wrap gap-6">
                <div className="relative flex h-44 w-44 shrink-0 items-center justify-center overflow-hidden rounded-[28px] bg-gradient-to-br from-blue-50 to-fuchsia-50">
                    {previewUrl ? (
                        <img
                            src={previewUrl}
                            alt="preview"
                            className="h-full w-full object-cover"
                        />
                    ) : (
                        <Monitor size={72} className="text-blue-600" />
                    )}

                    {previewUrl && (
                        <div className="absolute bottom-3 left-3 rounded-xl bg-slate-950/75 px-3 py-1 text-xs font-black uppercase tracking-wide text-white backdrop-blur">
                            Preview
                        </div>
                    )}
                </div>

                <div className="min-w-[260px] flex-1">
                    <div className="mb-2 flex items-start justify-between gap-4">
                        <div>
                            <h2 className="text-4xl font-black tracking-tight text-slate-950 max-md:text-3xl">
                                {getPcName(pc)}
                            </h2>

                            <p className="mt-2 text-lg font-black text-slate-500">
                                {getPcMac(pc) || "MAC не указан"}
                            </p>
                        </div>

                        <button
                            type="button"
                            className="flex h-11 w-11 items-center justify-center rounded-2xl text-slate-500 hover:bg-slate-100"
                        >
                            <MoreHorizontal size={24} />
                        </button>
                    </div>

                    <div
                        className={
                            online
                                ? "mt-5 inline-flex items-center gap-2 rounded-full bg-emerald-50 px-4 py-2 text-base font-black text-emerald-700"
                                : "mt-5 inline-flex items-center gap-2 rounded-full bg-slate-100 px-4 py-2 text-base font-black text-slate-500"
                        }
                    >
                        <span
                            className={
                                online
                                    ? "h-3 w-3 rounded-full bg-emerald-500"
                                    : "h-3 w-3 rounded-full bg-slate-400"
                            }
                        />
                        {online ? "Online" : "Offline"}
                    </div>

                    <p className="mt-5 text-base font-black text-slate-500">
                        Last seen: {formatDate(getPcLastConnection(pc))}
                    </p>

                    <div className="mt-8 grid grid-cols-2 gap-4 max-sm:grid-cols-1">
                        <button
                            type="button"
                            onClick={onConnect}
                            className="inline-flex h-16 items-center justify-center gap-3 rounded-2xl bg-blue-600 px-6 text-base font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700"
                        >
                            <Monitor size={22} />
                            Connection
                        </button>

                        <button
                            type="button"
                            className="inline-flex h-16 items-center justify-center gap-3 rounded-2xl border border-slate-200 bg-white px-6 text-base font-black text-slate-500 transition hover:bg-slate-50"
                        >
                            <Power size={22} />
                            Power
                        </button>
                    </div>
                </div>
            </div>
        </article>
    );
}

function ConnectionModal({
                             pc,
                             onClose,
                             onPersonal,
                             onEducation,
                             onSupport,
                             onMobileRemote,
                             showMobileRemote,
                         }: {
    pc: PcDetailsResponse;
    onClose: () => void;
    onPersonal: () => void;
    onEducation: () => void;
    onSupport: () => void;
    onMobileRemote: () => void;
    showMobileRemote: boolean;
}) {
    return (
        <div
            className="fixed inset-0 z-50 overflow-y-auto bg-slate-950/70 px-4 py-4 backdrop-blur-md sm:px-6 sm:py-6"
            style={{
                WebkitOverflowScrolling: "touch",
                paddingTop: "max(16px, env(safe-area-inset-top))",
                paddingBottom: "max(24px, env(safe-area-inset-bottom))",
            }}
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) {
                    onClose();
                }
            }}
        >
            <div className="flex min-h-full items-start justify-center py-2 sm:items-center">
                <section className="relative w-full max-w-7xl overflow-hidden rounded-[30px] border border-white/20 bg-white shadow-2xl sm:rounded-[40px]">
                    <div className="absolute -right-20 -top-20 h-72 w-72 animate-pulse rounded-full bg-blue-400/20 blur-3xl" />
                    <div className="absolute -bottom-24 -left-24 h-72 w-72 animate-pulse rounded-full bg-fuchsia-400/20 blur-3xl" />

                    <div className="relative border-b border-slate-200 bg-gradient-to-br from-slate-950 via-blue-950 to-slate-900 px-5 py-5 text-white sm:px-8 sm:py-8">
                        <div className="flex items-start justify-between gap-4">
                            <div className="min-w-0">
                                <div className="mb-3 inline-flex items-center gap-2 rounded-2xl bg-white/10 px-4 py-2 text-xs font-black text-blue-100 backdrop-blur sm:text-sm">
                                    <Sparkles size={17} />
                                    Choose scenario
                                </div>

                                <h2 className="text-3xl font-black tracking-tight sm:text-4xl">
                                    Выбор сценария подключения
                                </h2>

                                <p className="mt-3 break-words text-sm font-semibold text-blue-100 sm:text-base">
                                    ПК: {getPcName(pc)}
                                </p>

                                {showMobileRemote && (
                                    <p className="mt-2 inline-flex rounded-full bg-emerald-500/15 px-3 py-1 text-xs font-black text-emerald-200">
                                        Телефонный режим активен
                                    </p>
                                )}
                            </div>

                            <button
                                type="button"
                                onClick={onClose}
                                className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-white/10 bg-white/10 text-white transition hover:bg-white/20 sm:h-12 sm:w-12"
                            >
                                <X size={22} />
                            </button>
                        </div>
                    </div>

                    <div
                        className={
                            showMobileRemote
                                ? "relative grid grid-cols-1 gap-4 p-4 sm:grid-cols-2 sm:gap-5 sm:p-6 xl:grid-cols-4"
                                : "relative grid grid-cols-1 gap-4 p-4 sm:grid-cols-2 sm:gap-5 sm:p-6 xl:grid-cols-3"
                        }
                    >
                        <ScenarioCard
                            icon={<UserRound size={34} />}
                            title="Личное подключение"
                            text="Обычный удалённый доступ к выбранному ПК: просмотр, управление, файлы и команды питания."
                            buttonText="Открыть"
                            accent="blue"
                            onClick={onPersonal}
                        />

                        <ScenarioCard
                            icon={<GraduationCap size={34} />}
                            title="Учебная сессия"
                            text="Преподаватель создаёт код, студенты входят по нему, общаются в чате и запрашивают управление."
                            buttonText="Создать"
                            accent="violet"
                            onClick={onEducation}
                        />

                        <ScenarioCard
                            icon={<Headphones size={34} />}
                            title="Техподдержка"
                            text="Оператор создаёт код, клиент входит по нему, показывает экран и сам разрешает управление."
                            buttonText="Создать"
                            accent="emerald"
                            onClick={onSupport}
                        />

                        {showMobileRemote && (
                            <ScenarioCard
                                icon={<Smartphone size={34} />}
                                title="Многофункциональный пульт"
                                text="Телефон как пульт: презентация, тачпад/курсор и виртуальный игровой контроллер."
                                buttonText="Открыть"
                                accent="blue"
                                onClick={onMobileRemote}
                            />
                        )}
                    </div>
                </section>
            </div>
        </div>
    );
}

function ScenarioCard({
                          icon,
                          title,
                          text,
                          buttonText,
                          accent,
                          onClick,
                      }: {
    icon: ReactNode;
    title: string;
    text: string;
    buttonText: string;
    accent: "blue" | "violet" | "emerald";
    onClick: () => void;
}) {
    const accentClasses = {
        blue: {
            icon: "bg-blue-50 text-blue-700",
            button: "bg-blue-600 hover:bg-blue-700 shadow-blue-600/20",
            glow: "from-blue-500/15",
        },
        violet: {
            icon: "bg-violet-50 text-violet-700",
            button: "bg-violet-600 hover:bg-violet-700 shadow-violet-600/20",
            glow: "from-violet-500/15",
        },
        emerald: {
            icon: "bg-emerald-50 text-emerald-700",
            button: "bg-emerald-600 hover:bg-emerald-700 shadow-emerald-600/20",
            glow: "from-emerald-500/15",
        },
    }[accent];

    return (
        <article className="group relative overflow-hidden rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm transition duration-300 hover:-translate-y-2 hover:shadow-2xl hover:shadow-slate-300/60">
            <div className={`absolute inset-x-0 top-0 h-32 bg-gradient-to-b ${accentClasses.glow} to-transparent opacity-0 transition group-hover:opacity-100`} />

            <div className={`relative mb-6 flex h-16 w-16 items-center justify-center rounded-[22px] ${accentClasses.icon} transition duration-300 group-hover:scale-110`}>
                {icon}
            </div>

            <h3 className="relative text-2xl font-black text-slate-950">
                {title}
            </h3>

            <p className="relative mt-4 min-h-[96px] text-sm font-semibold leading-6 text-slate-500">
                {text}
            </p>

            <button
                type="button"
                onClick={onClick}
                className={`relative mt-6 inline-flex h-12 w-full items-center justify-center gap-2 rounded-2xl px-5 text-sm font-black text-white shadow-lg transition ${accentClasses.button}`}
            >
                {buttonText}
                <ArrowRight size={18} className="transition group-hover:translate-x-1" />
            </button>
        </article>
    );
}

function BaseModal({
                       title,
                       subtitle,
                       children,
                       onClose,
                   }: {
    title: string;
    subtitle: string;
    children: ReactNode;
    onClose: () => void;
}) {
    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-6 backdrop-blur-sm"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) {
                    onClose();
                }
            }}
        >
            <section className="w-full max-w-2xl overflow-hidden rounded-[34px] border border-slate-300 bg-white shadow-2xl">
                <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-7 py-6">
                    <div>
                        <h2 className="text-3xl font-black text-slate-950">
                            {title}
                        </h2>

                        <p className="mt-2 text-sm font-semibold text-slate-500">
                            {subtitle}
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={onClose}
                        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-slate-300 bg-white text-slate-500 transition hover:bg-slate-50 hover:text-slate-900"
                    >
                        <X size={22} />
                    </button>
                </div>

                <div className="p-7">{children}</div>
            </section>
        </div>
    );
}

function CodeInput({
                       label,
                       value,
                       onChange,
                       placeholder,
                   }: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    placeholder: string;
}) {
    return (
        <div className="mt-5">
            <label className="block text-sm font-black text-slate-700">
                {label}
            </label>

            <input
                value={value}
                onChange={(event) => onChange(event.target.value.toUpperCase())}
                placeholder={placeholder}
                className="mt-3 h-16 w-full rounded-2xl border border-slate-200 bg-white px-4 text-center text-3xl font-black tracking-[0.25em] text-slate-950 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100"
            />
        </div>
    );
}

function InfoBox({ children }: { children: ReactNode }) {
    return (
        <div className="rounded-3xl border border-blue-200 bg-blue-50 p-5 text-sm font-bold leading-6 text-blue-800">
            {children}
        </div>
    );
}

function LoadingBlock({ text }: { text: string }) {
    return (
        <div className="rounded-[34px] border border-slate-200 bg-white p-10 text-center shadow-sm">
            <Loader2 className="mx-auto mb-4 animate-spin text-blue-600" size={42} />
            <p className="font-black text-slate-600">{text}</p>
        </div>
    );
}

function EmptyState({ title, text }: { title: string; text: string }) {
    return (
        <div className="rounded-[34px] border border-slate-200 bg-white p-10 text-center shadow-sm">
            <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-[28px] bg-slate-100 text-slate-500">
                <Monitor size={42} />
            </div>

            <h2 className="text-3xl font-black text-slate-950">{title}</h2>
            <p className="mx-auto mt-3 max-w-xl text-base font-semibold leading-7 text-slate-500">
                {text}
            </p>
        </div>
    );
}

function isEducationApproved(status?: string): boolean {
    const value = String(status || "").toUpperCase();

    return (
        value === "APPROVED" ||
        value === "ONLINE" ||
        value === "ACTIVE" ||
        value === "CONNECTED"
    );
}

function isEducationRejected(status?: string): boolean {
    const value = String(status || "").toUpperCase();

    return value === "REJECTED" || value === "DECLINED" || value === "DENIED";
}

function translateEducationStatus(status?: string): string {
    const value = String(status || "").toUpperCase();

    if (isEducationApproved(value)) {
        return "Подтверждено";
    }

    if (isEducationRejected(value)) {
        return "Отклонено";
    }

    if (value === "WAITING" || value === "PENDING" || value === "REQUESTED") {
        return "Ожидает подтверждения";
    }

    return value || "Ожидает подтверждения";
}

function normalizeEducationJoinError(message: string): string {
    const lower = message.toLowerCase();

    if (
        lower.includes("не найден") ||
        lower.includes("not found") ||
        lower.includes("session") ||
        lower.includes("сесс")
    ) {
        return "Учебная сессия с таким кодом не найдена или уже завершена";
    }

    return message;
}

function getPcId(pc: PcDetailsResponse): number {
    const value = (pc as unknown as { id?: number | string }).id;
    return Number(value || 0);
}

function getPcName(pc: PcDetailsResponse): string {
    const value =
        (pc as unknown as { name?: string; pcName?: string }).name ||
        (pc as unknown as { pcName?: string }).pcName;

    return value || "Remote PC";
}

function getPcMac(pc: PcDetailsResponse): string {
    return (
        (pc as unknown as { macAddress?: string }).macAddress ||
        (pc as unknown as { pcMacAddress?: string }).pcMacAddress ||
        ""
    );
}

function getPcStatus(pc: PcDetailsResponse): string {
    return (
        (pc as unknown as { status?: string }).status ||
        (pc as unknown as { pcStatus?: string }).pcStatus ||
        "OFFLINE"
    );
}

function isPcOnline(pc: PcDetailsResponse): boolean {
    return getPcStatus(pc).toUpperCase() === "ONLINE";
}

function getPcLastConnection(pc: PcDetailsResponse): string | null {
    return (
        (pc as unknown as { lastConnection?: string | null }).lastConnection ||
        (pc as unknown as { lastSeen?: string | null }).lastSeen ||
        (pc as unknown as { lastSeenAt?: string | null }).lastSeenAt ||
        null
    );
}

function getPcPreview(pc: PcDetailsResponse): string | null {
    const directPreview =
        (pc as unknown as { previewUrl?: string }).previewUrl ||
        (pc as unknown as { lastPreviewUrl?: string }).lastPreviewUrl ||
        (pc as unknown as { screenshotUrl?: string }).screenshotUrl ||
        (pc as unknown as { thumbnailUrl?: string }).thumbnailUrl;

    if (isImageData(directPreview)) {
        return directPreview;
    }

    const id = String(getPcId(pc));
    const mac = getPcMac(pc);
    const normalizedMac = mac.replace(/[^a-zA-Z0-9]/g, "").toLowerCase();
    const name = getPcName(pc).toLowerCase();

    const knownKeys = [
        `pc-preview-${id}`,
        `pcPreview:${id}`,
        `remote-preview-${id}`,
        `remotePreview:${id}`,
        `last-preview-${id}`,
        `lastPreview:${id}`,
        `preview-${id}`,
        `screenshot-${id}`,
        mac ? `pc-preview-${mac}` : "",
        mac ? `pcPreview:${mac}` : "",
        mac ? `remote-preview-${mac}` : "",
        normalizedMac ? `pc-preview-${normalizedMac}` : "",
        normalizedMac ? `pcPreview:${normalizedMac}` : "",
    ].filter(Boolean);

    for (const key of knownKeys) {
        const value = localStorage.getItem(key);

        if (isImageData(value)) {
            return value;
        }
    }

    for (let i = 0; i < localStorage.length; i += 1) {
        const key = localStorage.key(i);

        if (!key) {
            continue;
        }

        const lowerKey = key.toLowerCase();
        const value = localStorage.getItem(key);

        const keyLooksRelevant =
            lowerKey.includes("preview") ||
            lowerKey.includes("screenshot") ||
            lowerKey.includes("thumbnail") ||
            lowerKey.includes("screen") ||
            lowerKey.includes("remote");

        const keyMatchesPc =
            lowerKey.includes(id) ||
            Boolean(normalizedMac && lowerKey.includes(normalizedMac)) ||
            Boolean(mac && lowerKey.includes(mac.toLowerCase())) ||
            Boolean(name && lowerKey.includes(name));

        if ((keyLooksRelevant || keyMatchesPc) && isImageData(value)) {
            return value;
        }

        if (value && value.startsWith("{")) {
            try {
                const parsed = JSON.parse(value) as Record<string, unknown>;

                for (const parsedValue of Object.values(parsed)) {
                    if (typeof parsedValue === "string" && isImageData(parsedValue)) {
                        return parsedValue;
                    }
                }
            } catch {
                // ignore
            }
        }
    }

    return null;
}

function isImageData(value?: string | null): value is string {
    if (!value) {
        return false;
    }

    return (
        value.startsWith("data:image/") ||
        value.startsWith("blob:") ||
        value.startsWith("http://") ||
        value.startsWith("https://")
    );
}

function formatDate(value: string | null): string {
    if (!value) {
        return "—";
    }

    try {
        return new Date(value).toLocaleString();
    } catch {
        return value;
    }
}

function isMobileRemoteDevice(): boolean {
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


