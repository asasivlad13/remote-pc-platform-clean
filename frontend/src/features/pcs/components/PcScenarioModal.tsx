import {
    GraduationCap,
    Headphones,
    Monitor,
    Smartphone,
    X,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { PcResponseDto } from "../pcTypes";

type PcScenarioModalProps = {
    pc: PcResponseDto | null;
    open: boolean;
    onClose: () => void;
};

export function PcScenarioModal({
                                    pc,
                                    open,
                                    onClose,
                                }: PcScenarioModalProps) {
    const navigate = useNavigate();

    if (!open || !pc) {
        return null;
    }

    const pcStatus = String(pc.status || "OFFLINE").toUpperCase();
    const isPcOnline = pcStatus === "ONLINE";

    function openPersonalAccess() {
        if (!isPcOnline) {
            alert("ПК сейчас offline. Личный доступ недоступен.");
            return;
        }

        navigate(
            `/remote/${pc.id}?pcName=${encodeURIComponent(pc.name || "PC")}&profile=personal`,
        );
    }

    function openEducationCreate() {
        if (!isPcOnline) {
            alert("Для создания учебной сессии ПК преподавателя должен быть online.");
            return;
        }

        navigate(
            `/education-create?pcId=${encodeURIComponent(String(pc.id))}&pcName=${encodeURIComponent(pc.name || "PC")}`,
        );
    }

    function openSupportCreate() {
        alert("Сценарий техподдержки перенесём после учебной сессии.");
    }

    function openMobileRemote() {
        if (!isPcOnline) {
            alert("ПК сейчас offline. Мобильный пульт недоступен.");
            return;
        }

        alert("Мобильный пульт перенесём после учебной сессии.");
    }

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-6 backdrop-blur-sm"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) {
                    onClose();
                }
            }}
        >
            <section className="w-full max-w-5xl overflow-hidden rounded-[34px] border border-slate-200 bg-white shadow-2xl">
                <div className="flex items-start justify-between gap-6 border-b border-slate-100 px-8 py-7">
                    <div>
                        <div className="mb-3 inline-flex rounded-xl bg-blue-50 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-700">
                            Выбор сценария
                        </div>

                        <h2 className="text-3xl font-black tracking-tight text-slate-950">
                            {pc.name || "Компьютер"}
                        </h2>

                        <p className="mt-2 text-base font-semibold text-slate-500">
                            Выберите, как использовать этот ПК.
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={onClose}
                        className="flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-500 transition hover:bg-slate-50 hover:text-slate-900"
                    >
                        <X size={22} />
                    </button>
                </div>

                <div className="grid grid-cols-2 gap-5 p-8 max-lg:grid-cols-1">
                    <ScenarioCard
                        icon={<Monitor size={32} />}
                        title="Личный доступ"
                        text="Подключиться к своему ПК: экран, мышь, клавиатура, файлы и полноэкранный режим."
                        badge="Готово"
                        disabled={!isPcOnline}
                        onClick={openPersonalAccess}
                    />

                    <ScenarioCard
                        icon={<GraduationCap size={32} />}
                        title="Создать учебную сессию"
                        text="Создать код занятия для студентов и открыть рабочее место преподавателя."
                        badge="Учитель"
                        disabled={!isPcOnline}
                        onClick={openEducationCreate}
                    />

                    <ScenarioCard
                        icon={<Headphones size={32} />}
                        title="Техподдержка"
                        text="Создать сессию помощи для клиента. Этот сценарий доделаем отдельно."
                        badge="Позже"
                        onClick={openSupportCreate}
                    />

                    <ScenarioCard
                        icon={<Smartphone size={32} />}
                        title="Мобильный пульт"
                        text="Управление презентацией, тачпадом и игровым пультом с телефона."
                        badge="Позже"
                        disabled={!isPcOnline}
                        onClick={openMobileRemote}
                    />
                </div>
            </section>
        </div>
    );
}

function ScenarioCard({
                          icon,
                          title,
                          text,
                          badge,
                          disabled,
                          onClick,
                      }: {
    icon: React.ReactNode;
    title: string;
    text: string;
    badge: string;
    disabled?: boolean;
    onClick: () => void;
}) {
    return (
        <button
            type="button"
            disabled={disabled}
            onClick={onClick}
            className="group min-h-[180px] rounded-[28px] border border-slate-200 bg-white p-6 text-left shadow-sm transition hover:-translate-y-1 hover:border-blue-300 hover:bg-blue-50 hover:shadow-xl disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 disabled:hover:border-slate-200 disabled:hover:bg-white disabled:hover:shadow-sm"
        >
            <div className="mb-5 flex items-start justify-between gap-4">
                <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-50 text-blue-600 transition group-hover:bg-blue-600 group-hover:text-white">
                    {icon}
                </div>

                <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-black uppercase tracking-wide text-slate-500">
                    {badge}
                </span>
            </div>

            <h3 className="text-xl font-black text-slate-950">
                {title}
            </h3>

            <p className="mt-3 text-sm font-semibold leading-6 text-slate-500">
                {text}
            </p>
        </button>
    );
}