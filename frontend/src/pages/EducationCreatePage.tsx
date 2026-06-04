import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { ArrowLeft, GraduationCap, Loader2, Monitor } from "lucide-react";
import { createEducationSession } from "../features/education/educationApi";

export function EducationCreatePage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    const teacherPcId = useMemo(() => {
        const raw = searchParams.get("pcId");
        const parsed = Number(raw);

        return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
    }, [searchParams]);

    const pcName = searchParams.get("pcName") || "ПК преподавателя";

    const [title, setTitle] = useState("");
    const [maxStudents, setMaxStudents] = useState(30);
    const [allowStudentControl, setAllowStudentControl] = useState(true);
    const [allowFileTransfer, setAllowFileTransfer] = useState(true);
    const [allowStudentScreenShare, setAllowStudentScreenShare] = useState(true);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    async function createSession() {
        if (!teacherPcId) {
            setError("ПК преподавателя не выбран. Вернитесь на главную и создайте сессию через карточку ПК.");
            return;
        }

        const preparedTitle = title.trim();

        if (!preparedTitle) {
            setError("Введите название учебной сессии.");
            return;
        }

        if (!Number.isFinite(maxStudents) || maxStudents < 1) {
            setError("Количество студентов должно быть больше 0.");
            return;
        }

        try {
            setLoading(true);
            setError("");

            const session = await createEducationSession({
                teacherPcId,
                title: preparedTitle,
                maxStudents,
                allowStudentControl,
                allowFileTransfer,
                allowStudentScreenShare,
            });

            navigate(`/education/teacher/${encodeURIComponent(session.sessionCode)}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Не удалось создать учебную сессию");
        } finally {
            setLoading(false);
        }
    }

    return (
        <main className="min-h-screen bg-[#f8fafc] p-6 text-slate-950">
            <div className="mx-auto max-w-4xl">
                <button
                    type="button"
                    onClick={() => navigate("/pcs")}
                    className="mb-6 inline-flex h-12 items-center gap-3 rounded-2xl border border-slate-200 bg-white px-5 font-black text-slate-600 shadow-sm transition hover:bg-slate-50"
                >
                    <ArrowLeft size={20} />
                    Назад
                </button>

                <section className="overflow-hidden rounded-[34px] border border-slate-200 bg-white shadow-sm">
                    <div className="bg-gradient-to-br from-blue-600 to-blue-700 px-8 py-10 text-white">
                        <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-3xl bg-white/15">
                            <GraduationCap size={36} />
                        </div>

                        <h1 className="text-4xl font-black">
                            Создание учебной сессии
                        </h1>

                        <p className="mt-4 max-w-2xl text-lg font-medium text-blue-100">
                            Преподаватель создаёт код занятия. Студенты подключаются по этому коду через раздел Education.
                        </p>
                    </div>

                    <div className="grid gap-6 p-8">
                        <div className="rounded-3xl border border-blue-100 bg-blue-50 px-5 py-4">
                            <div className="text-xs font-black uppercase tracking-wide text-blue-700">
                                ПК преподавателя
                            </div>

                            <div className="mt-1 text-lg font-black text-slate-950">
                                {pcName}
                            </div>

                            <div className="mt-1 text-sm font-bold text-slate-500">
                                ID: {teacherPcId ?? "не выбран"}
                            </div>
                        </div>

                        <div>
                            <label className="mb-2 block text-sm font-black text-slate-700">
                                Название занятия
                            </label>

                            <input
                                value={title}
                                onChange={(event) => setTitle(event.target.value)}
                                placeholder="Например: Лабораторная работа по Java"
                                className="h-14 w-full rounded-2xl border border-slate-200 bg-white px-5 text-base font-bold text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                            />
                        </div>

                        <div>
                            <label className="mb-2 block text-sm font-black text-slate-700">
                                Максимум студентов
                            </label>

                            <input
                                type="number"
                                min={1}
                                max={100}
                                value={maxStudents}
                                onChange={(event) => {
                                    const value = Number(event.target.value);
                                    setMaxStudents(Number.isFinite(value) ? value : 1);
                                }}
                                className="h-14 w-full rounded-2xl border border-slate-200 bg-white px-5 text-base font-bold text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                            />
                        </div>

                        <div className="grid grid-cols-3 gap-4 max-lg:grid-cols-1">
                            <OptionCard
                                title="Управление студентом"
                                text="Студент может запросить управление."
                                checked={allowStudentControl}
                                onChange={setAllowStudentControl}
                            />

                            <OptionCard
                                title="Передача файлов"
                                text="Участники могут обмениваться файлами."
                                checked={allowFileTransfer}
                                onChange={setAllowFileTransfer}
                            />

                            <OptionCard
                                title="Экран студента"
                                text="Студент может запросить показ своего экрана."
                                checked={allowStudentScreenShare}
                                onChange={setAllowStudentScreenShare}
                            />
                        </div>

                        {error && (
                            <div className="rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm font-black text-red-700">
                                {error}
                            </div>
                        )}

                        <button
                            type="button"
                            onClick={createSession}
                            disabled={loading}
                            className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl bg-blue-600 px-7 text-base font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700 disabled:opacity-60"
                        >
                            {loading ? <Loader2 className="animate-spin" size={22} /> : <Monitor size={22} />}
                            Создать сессию
                        </button>
                    </div>
                </section>
            </div>
        </main>
    );
}

function OptionCard({
                        title,
                        text,
                        checked,
                        onChange,
                    }: {
    title: string;
    text: string;
    checked: boolean;
    onChange: (value: boolean) => void;
}) {
    return (
        <button
            type="button"
            onClick={() => onChange(!checked)}
            className={
                checked
                    ? "rounded-3xl border-2 border-blue-500 bg-blue-50 p-5 text-left"
                    : "rounded-3xl border border-slate-200 bg-white p-5 text-left transition hover:bg-slate-50"
            }
        >
            <div className="mb-3 flex items-center justify-between gap-3">
                <h3 className={checked ? "font-black text-blue-700" : "font-black text-slate-950"}>
                    {title}
                </h3>

                <span className={checked ? "h-6 w-11 rounded-full bg-blue-600 p-1" : "h-6 w-11 rounded-full bg-slate-200 p-1"}>
                    <span className={checked ? "block h-4 w-4 translate-x-5 rounded-full bg-white" : "block h-4 w-4 rounded-full bg-white"} />
                </span>
            </div>

            <p className="text-sm font-semibold leading-6 text-slate-500">
                {text}
            </p>
        </button>
    );
}