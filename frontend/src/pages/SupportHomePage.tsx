import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    ArrowLeft,
    ClipboardCopy,
    Headphones,
    LogIn,
    Monitor,
    Plus,
    ShieldCheck,
} from "lucide-react";
import { createSupportSession, joinSupportSession } from "../features/support/supportApi";
import { DashboardLayout } from "../shared/ui/DashboardLayout";

export function SupportHomePage() {
    const navigate = useNavigate();

    const [searchValue, setSearchValue] = useState("");
    const [title, setTitle] = useState("");
    const [joinCode, setJoinCode] = useState("");
    const [createdCode, setCreatedCode] = useState("");
    const [loadingCreate, setLoadingCreate] = useState(false);
    const [loadingJoin, setLoadingJoin] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");

    function showNotice(text: string) {
        setNotice(text);

        window.setTimeout(() => {
            setNotice("");
        }, 3000);
    }

    async function handleCreateSession() {
        try {
            setError("");
            setLoadingCreate(true);

            const session = await createSupportSession(title.trim());
            setCreatedCode(session.sessionCode);
            showNotice("Сессия техподдержки создана");
        } catch (e) {
            setError(e instanceof Error ? e.message : "Не удалось создать сессию");
        } finally {
            setLoadingCreate(false);
        }
    }

    async function handleJoinSession() {
        const normalizedCode = joinCode.trim();

        if (!normalizedCode) {
            setError("Введите код сессии техподдержки");
            return;
        }

        if (normalizedCode.length !== 6) {
            setError("Код сессии должен состоять из 6 символов");
            return;
        }

        try {
            setError("");
            setLoadingJoin(true);

            const session = await joinSupportSession(normalizedCode);
            navigate(`/support/client/${encodeURIComponent(session.sessionCode)}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Сессия техподдержки с таким кодом не найдена");
        } finally {
            setLoadingJoin(false);
        }
    }

    function copyCreatedCode() {
        if (!createdCode) {
            return;
        }

        void navigator.clipboard.writeText(createdCode);
        showNotice("Код скопирован");
    }

    return (
        <DashboardLayout searchValue={searchValue} onSearchChange={setSearchValue}>
            <div className="px-5 py-10 lg:px-12">
                <div className="mb-10 flex flex-wrap items-start justify-between gap-6">
                    <div>
                        <div className="mb-4 inline-flex items-center gap-2 rounded-2xl bg-blue-50 px-4 py-2 text-sm font-black text-blue-700">
                            <Headphones size={18} />
                            Technical Support
                        </div>

                        <h1 className="text-5xl font-black tracking-tight text-slate-950 max-sm:text-4xl">
                            Техническая поддержка
                        </h1>

                        <p className="mt-4 max-w-3xl text-lg font-medium leading-8 text-slate-500">
                            Оператор создаёт код сессии, клиент входит по коду со своего ПК,
                            после чего можно смотреть экран, общаться в чате, отправлять файлы
                            и запрашивать управление.
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={() => navigate("/pcs")}
                        className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-slate-300 bg-white px-6 text-sm font-black text-slate-700 shadow-sm transition hover:bg-slate-50"
                    >
                        <ArrowLeft size={20} />
                        Назад
                    </button>
                </div>

                {notice && (
                    <div className="mb-6 rounded-3xl border border-blue-200 bg-blue-50 px-5 py-4 font-bold text-blue-700">
                        {notice}
                    </div>
                )}

                {error && (
                    <div className="mb-6 rounded-3xl border border-red-200 bg-red-50 px-5 py-4 font-bold text-red-700">
                        {error}
                    </div>
                )}

                <div className="grid grid-cols-2 gap-6 max-xl:grid-cols-1">
                    <section className="rounded-[32px] border border-slate-200 bg-white p-8 shadow-sm">
                        <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-[24px] bg-blue-50 text-blue-700">
                            <Plus size={34} />
                        </div>

                        <h2 className="text-3xl font-black text-slate-950">
                            Создать сессию
                        </h2>

                        <p className="mt-3 text-base font-medium leading-7 text-slate-500">
                            Режим оператора: создайте код и передайте его клиенту.
                            После входа клиента откроется рабочее место поддержки.
                        </p>

                        <label className="mt-7 block text-sm font-black text-slate-700">
                            Название обращения
                        </label>

                        <input
                            value={title}
                            onChange={(event) => setTitle(event.target.value)}
                            placeholder="Например: помощь с настройкой программы"
                            className="mt-3 h-14 w-full rounded-2xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100"
                        />

                        <button
                            type="button"
                            onClick={handleCreateSession}
                            disabled={loadingCreate}
                            className="mt-5 inline-flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-blue-600 px-6 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700 disabled:bg-slate-300"
                        >
                            <Headphones size={20} />
                            {loadingCreate ? "Создание..." : "Создать сессию техподдержки"}
                        </button>

                        {createdCode && (
                            <div className="mt-7 rounded-[28px] border border-blue-200 bg-blue-50 p-6">
                                <div className="text-sm font-black uppercase tracking-wide text-blue-700">
                                    Передайте клиенту этот код
                                </div>

                                <button
                                    type="button"
                                    onClick={copyCreatedCode}
                                    className="mt-3 inline-flex items-center gap-3 text-5xl font-black tracking-[0.18em] text-slate-950 max-sm:text-4xl"
                                >
                                    {createdCode}
                                    <ClipboardCopy size={28} className="text-blue-600" />
                                </button>

                                <button
                                    type="button"
                                    onClick={() => navigate(`/support/operator/${encodeURIComponent(createdCode)}`)}
                                    className="mt-6 inline-flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-slate-950 px-6 text-sm font-black text-white shadow-lg shadow-slate-950/15 transition hover:bg-slate-800"
                                >
                                    <Monitor size={20} />
                                    Перейти в рабочее место оператора
                                </button>
                            </div>
                        )}
                    </section>

                    <section className="rounded-[32px] border border-slate-200 bg-white p-8 shadow-sm">
                        <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-[24px] bg-emerald-50 text-emerald-700">
                            <LogIn size={34} />
                        </div>

                        <h2 className="text-3xl font-black text-slate-950">
                            Войти как клиент
                        </h2>

                        <p className="mt-3 text-base font-medium leading-7 text-slate-500">
                            Режим клиента: введите код, который сообщил оператор.
                            После подключения оператор сможет видеть экран, но управление
                            будет доступно только после вашего разрешения.
                        </p>

                        <label className="mt-7 block text-sm font-black text-slate-700">
                            Код сессии
                        </label>

                        <input
                            value={joinCode}
                            onChange={(event) => setJoinCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
                            onKeyDown={(event) => {
                                if (event.key === "Enter") {
                                    void handleJoinSession();
                                }
                            }}
                            placeholder="000000"
                            maxLength={6}
                            className="mt-3 h-16 w-full rounded-2xl border border-slate-200 bg-white px-4 text-center text-3xl font-black tracking-[0.25em] text-slate-950 outline-none transition focus:border-emerald-400 focus:ring-4 focus:ring-emerald-100"
                        />

                        <button
                            type="button"
                            onClick={handleJoinSession}
                            disabled={loadingJoin}
                            className="mt-5 inline-flex h-14 w-full items-center justify-center gap-3 rounded-2xl bg-emerald-600 px-6 text-sm font-black text-white shadow-lg shadow-emerald-600/20 transition hover:bg-emerald-700 disabled:bg-slate-300"
                        >
                            <ShieldCheck size={20} />
                            {loadingJoin ? "Подключение..." : "Подключиться к поддержке"}
                        </button>
                    </section>
                </div>
            </div>
        </DashboardLayout>
    );
}