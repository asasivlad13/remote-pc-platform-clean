import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    ArrowLeft,
    CheckCircle2,
    GraduationCap,
    Loader2,
    LogIn,
    ShieldCheck,
} from "lucide-react";
import {
    getMyEducationParticipantStatus,
    joinEducationSession,
} from "../features/education/educationApi";
import type { EducationParticipantResponse } from "../features/education/educationTypes";

export function EducationJoinPage() {
    const navigate = useNavigate();

    const [sessionCode, setSessionCode] = useState("");
    const [pendingCode, setPendingCode] = useState("");
    const [participant, setParticipant] = useState<EducationParticipantResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [checking, setChecking] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!pendingCode) {
            return;
        }

        const timer = window.setInterval(() => {
            void checkParticipantStatus();
        }, 2500);

        return () => window.clearInterval(timer);
    }, [pendingCode]);

    async function joinSession() {
        const code = sessionCode.trim();

        if (!code) {
            setError("Введите код учебной сессии");
            return;
        }

        try {
            setLoading(true);
            setError("");

            const joined = await joinEducationSession(code);
            setParticipant(joined);
            setPendingCode(code);

            if (isApproved(joined)) {
                navigate(`/education/student/${encodeURIComponent(code)}`);
                return;
            }

            if (isRejected(joined)) {
                setPendingCode("");
                setError("Преподаватель отклонил подключение к учебной сессии.");
            }
        } catch (e) {
            setPendingCode("");
            setParticipant(null);
            setError(getJoinErrorMessage(e));
        } finally {
            setLoading(false);
        }
    }

    async function checkParticipantStatus() {
        if (!pendingCode || checking) {
            return;
        }

        try {
            setChecking(true);

            const status = await getMyEducationParticipantStatus(pendingCode);
            setParticipant(status);

            if (isApproved(status)) {
                navigate(`/education/student/${encodeURIComponent(pendingCode)}`);
                return;
            }

            if (isRejected(status)) {
                setPendingCode("");
                setError("Преподаватель отклонил подключение к учебной сессии.");
            }
        } catch {
            setPendingCode("");
            setParticipant(null);
            setError("Сессия с таким кодом не существует или уже завершилась.");
        } finally {
            setChecking(false);
        }
    }

    function cancelWaiting() {
        setPendingCode("");
        setParticipant(null);
        setError("");
    }

    return (
        <main className="min-h-screen bg-slate-100 p-6 text-slate-950">
            <div className="mx-auto max-w-3xl">
                <button
                    type="button"
                    onClick={() => navigate("/pcs")}
                    className="mb-6 inline-flex h-12 items-center gap-3 rounded-2xl border border-slate-300 bg-white px-5 font-black text-slate-700 shadow-sm transition hover:bg-slate-50"
                >
                    <ArrowLeft size={20} />
                    Назад
                </button>

                <section className="rounded-[34px] border border-slate-300 bg-white p-8 text-center shadow-sm">
                    <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-[28px] bg-blue-50 text-blue-600">
                        <GraduationCap size={42} />
                    </div>

                    <h1 className="text-4xl font-black text-slate-950">
                        Вход в учебную сессию
                    </h1>

                    <p className="mx-auto mt-4 max-w-xl text-lg font-medium leading-8 text-slate-600">
                        Введите код, который сообщил преподаватель. После ввода преподаватель должен подтвердить подключение.
                    </p>

                    {!pendingCode ? (
                        <>
                            <input
                                value={sessionCode}
                                onChange={(event) => setSessionCode(event.target.value.toUpperCase())}
                                onKeyDown={(event) => {
                                    if (event.key === "Enter") {
                                        void joinSession();
                                    }
                                }}
                                placeholder="КОД СЕССИИ"
                                className="mx-auto mt-8 h-16 w-full max-w-md rounded-3xl border border-slate-300 bg-white px-6 text-center text-2xl font-black tracking-[0.35em] text-blue-700 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                            />

                            {error && (
                                <div className="mx-auto mt-5 max-w-md rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm font-black text-red-700">
                                    {error}
                                </div>
                            )}

                            <button
                                type="button"
                                onClick={joinSession}
                                disabled={loading}
                                className="mx-auto mt-7 inline-flex h-14 items-center justify-center gap-3 rounded-2xl bg-blue-600 px-8 text-base font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700 disabled:opacity-60"
                            >
                                {loading ? <Loader2 className="animate-spin" size={22} /> : <LogIn size={22} />}
                                Отправить запрос на вход
                            </button>
                        </>
                    ) : (
                        <div className="mx-auto mt-8 max-w-xl rounded-[30px] border border-blue-200 bg-blue-50 p-7">
                            <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-3xl bg-white text-blue-600 shadow-sm">
                                <ShieldCheck size={34} />
                            </div>

                            <h2 className="text-2xl font-black text-slate-950">
                                Ожидание подтверждения
                            </h2>

                            <p className="mt-3 text-base font-semibold leading-7 text-slate-600">
                                Запрос отправлен преподавателю. Когда преподаватель подтвердит вход, страница автоматически откроет учебную сессию.
                            </p>

                            <div className="mt-5 inline-flex items-center gap-2 rounded-full bg-white px-4 py-2 text-sm font-black text-blue-700">
                                <CheckCircle2 size={18} />
                                Код: {pendingCode}
                            </div>

                            <div className="mt-5 text-sm font-bold text-slate-500">
                                Статус: {getWaitingLabel(participant)}
                            </div>

                            <button
                                type="button"
                                onClick={cancelWaiting}
                                className="mt-6 rounded-2xl border border-slate-300 bg-white px-5 py-3 text-sm font-black text-slate-700 transition hover:bg-slate-50"
                            >
                                Отменить ожидание
                            </button>
                        </div>
                    )}
                </section>
            </div>
        </main>
    );
}

function isApproved(participant: EducationParticipantResponse): boolean {
    const status = String(participant.status || "").toUpperCase();

    return (
        status === "APPROVED" ||
        status === "ACCEPTED" ||
        status === "ACTIVE" ||
        status === "CONNECTED"
    );
}

function isRejected(participant: EducationParticipantResponse): boolean {
    const status = String(participant.status || "").toUpperCase();

    return (
        status === "REJECTED" ||
        status === "DECLINED" ||
        status === "DENIED"
    );
}

function getWaitingLabel(participant: EducationParticipantResponse | null): string {
    if (!participant) {
        return "ожидает обработки";
    }

    const status = String(participant.status || "").toUpperCase();

    if (!status || status === "WAITING" || status === "PENDING" || status === "REQUESTED") {
        return "ожидает подтверждения преподавателя";
    }

    return status;
}

function getJoinErrorMessage(error: unknown): string {
    const message = error instanceof Error ? error.message : "";

    if (
        message.includes("не найд") ||
        message.includes("not found") ||
        message.includes("404") ||
        message.includes("заверш")
    ) {
        return "Сессия с таким кодом не существует или уже завершилась.";
    }

    if (message.trim()) {
        return message;
    }

    return "Сессия с таким кодом не существует или уже завершилась.";
}