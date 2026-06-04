import { useNavigate, useSearchParams } from "react-router-dom";
import {
    ArrowLeft,
    Gamepad2,
    MousePointer2,
    Presentation,
    Smartphone,
} from "lucide-react";

export function MobileRemoteHubPage() {
    const navigate = useNavigate();
    const [params] = useSearchParams();

    const pcId = params.get("pcId") || "";
    const pcName = params.get("pcName") || "ПК";

    function open(path: string) {
        if (!pcId) {
            alert("ПК не выбран");
            navigate("/pcs");
            return;
        }

        navigate(
            `${path}?pcId=${encodeURIComponent(pcId)}&pcName=${encodeURIComponent(pcName)}`,
        );
    }

    return (
        <main className="min-h-screen bg-slate-950 p-4 text-white">
            <section className="mx-auto max-w-4xl">
                <header className="mb-6">
                    <button
                        type="button"
                        onClick={() => navigate("/pcs")}
                        className="mb-6 inline-flex h-12 items-center gap-2 rounded-2xl border border-white/10 bg-white/10 px-5 text-sm font-black text-white backdrop-blur transition active:scale-95"
                    >
                        <ArrowLeft size={20} />
                        Назад
                    </button>

                    <div className="rounded-[34px] border border-white/10 bg-white/10 p-6 shadow-2xl shadow-black/30 backdrop-blur">
                        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-[24px] bg-blue-600 text-white">
                            <Smartphone size={34} />
                        </div>

                        <h1 className="text-4xl font-black tracking-tight">
                            Многофункциональный пульт
                        </h1>

                        <p className="mt-3 text-base font-semibold leading-7 text-slate-300">
                            ПК: <span className="font-black text-white">{pcName}</span>
                        </p>
                    </div>
                </header>

                <div className="grid gap-4">
                    <RemoteModeCard
                        icon={<Presentation size={36} />}
                        title="Пульт презентации"
                        text="Управление слайдами, запуском показа, выходом из показа и громкостью."
                        accent="blue"
                        onClick={() => open("/mobile-remote/presentation")}
                    />

                    <RemoteModeCard
                        icon={<MousePointer2 size={36} />}
                        title="Тачпад / курсор"
                        text="Управление курсором, ЛКМ, ПКМ, двойным кликом и прокруткой."
                        accent="emerald"
                        onClick={() => open("/mobile-remote/touchpad")}
                    />

                    <RemoteModeCard
                        icon={<Gamepad2 size={36} />}
                        title="Игровой контроллер"
                        text="Виртуальный геймпад: стики, кнопки действий, плечевые кнопки и триггеры."
                        accent="violet"
                        onClick={() => open("/mobile-remote/gamepad")}
                    />
                </div>
            </section>
        </main>
    );
}

function RemoteModeCard({
                            icon,
                            title,
                            text,
                            accent,
                            onClick,
                        }: {
    icon: React.ReactNode;
    title: string;
    text: string;
    accent: "blue" | "emerald" | "violet";
    onClick: () => void;
}) {
    const classes = {
        blue: "bg-blue-600 shadow-blue-600/20",
        emerald: "bg-emerald-600 shadow-emerald-600/20",
        violet: "bg-violet-600 shadow-violet-600/20",
    }[accent];

    return (
        <button
            type="button"
            onClick={onClick}
            className="group rounded-[30px] border border-white/10 bg-white/10 p-6 text-left shadow-xl shadow-black/20 backdrop-blur transition active:scale-[0.98]"
        >
            <div className={`mb-5 flex h-16 w-16 items-center justify-center rounded-[24px] text-white shadow-lg ${classes}`}>
                {icon}
            </div>

            <h2 className="text-2xl font-black text-white">
                {title}
            </h2>

            <p className="mt-3 text-base font-semibold leading-7 text-slate-300">
                {text}
            </p>

            <span className="mt-5 inline-flex rounded-full bg-emerald-500/15 px-4 py-2 text-xs font-black uppercase tracking-wide text-emerald-300">
                Доступно
            </span>
        </button>
    );
}
