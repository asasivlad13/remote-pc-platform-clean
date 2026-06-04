import type { ReactNode } from "react";
import {
    Clock,
    GraduationCap,
    Headphones,
    Monitor,
    Search,
    Settings,
} from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "../../features/auth/authStore";

type DashboardLayoutProps = {
    children: ReactNode;
    searchValue: string;
    onSearchChange: (value: string) => void;
    onEducationClick?: () => void;
    onSupportClick?: () => void;
};

type NavItem = {
    label: string;
    shortLabel: string;
    icon: ReactNode;
    active?: boolean;
    onClick: () => void;
};

export function DashboardLayout({
                                    children,
                                    searchValue,
                                    onSearchChange,
                                    onEducationClick,
                                    onSupportClick,
                                }: DashboardLayoutProps) {
    const navigate = useNavigate();
    const location = useLocation();

    const username = useAuthStore((state) => state.username);
    const logout = useAuthStore((state) => state.logout);

    const isPcsPage = location.pathname.startsWith("/pcs");
    const isEducationPage =
        location.pathname.startsWith("/education") ||
        location.pathname.startsWith("/education-student");
    const isSupportPage = location.pathname.startsWith("/support");
    const isHistoryPage = location.pathname.startsWith("/history");
    const isSettingsPage = location.pathname.startsWith("/settings");

    function handleLogout() {
        logout();
        window.location.href = "/login";
    }

    function openEducation() {
        if (onEducationClick) {
            onEducationClick();
            return;
        }

        navigate("/pcs");
    }

    function openSupport() {
        if (onSupportClick) {
            onSupportClick();
            return;
        }

        navigate("/support");
    }

    const navItems: NavItem[] = [
        {
            label: "Computers",
            shortLabel: "ПК",
            icon: <Monitor size={22} />,
            active: isPcsPage,
            onClick: () => navigate("/pcs"),
        },
        {
            label: "Education",
            shortLabel: "Учёба",
            icon: <GraduationCap size={22} />,
            active: isEducationPage,
            onClick: openEducation,
        },
        {
            label: "Support",
            shortLabel: "Support",
            icon: <Headphones size={22} />,
            active: isSupportPage,
            onClick: openSupport,
        },
        {
            label: "History",
            shortLabel: "История",
            icon: <Clock size={22} />,
            active: isHistoryPage,
            onClick: () => navigate("/history"),
        },
        {
            label: "Settings",
            shortLabel: "Настр.",
            icon: <Settings size={22} />,
            active: isSettingsPage,
            onClick: () => navigate("/settings"),
        },
    ];

    return (
        <div className="min-h-screen bg-[#f8fafc] text-slate-950">
            <aside className="fixed inset-y-0 left-0 hidden w-[270px] border-r border-slate-200 bg-white xl:block">
                <div className="flex h-full flex-col px-5 py-8">
                    <div className="mb-10 flex items-center gap-3">
                        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-600 text-white shadow-lg shadow-blue-500/25">
                            <Monitor size={24} />
                        </div>

                        <div className="text-2xl font-black tracking-tight">
                            Remo<span className="text-blue-600">Desk</span>
                        </div>
                    </div>

                    <nav className="grid gap-2">
                        {navItems.map((item) => (
                            <button
                                key={item.label}
                                type="button"
                                onClick={item.onClick}
                                className={
                                    item.active
                                        ? "flex items-center gap-4 rounded-2xl bg-blue-50 px-4 py-4 text-left font-bold text-blue-700"
                                        : "flex items-center gap-4 rounded-2xl px-4 py-4 text-left font-semibold text-slate-600 transition hover:bg-slate-50 hover:text-slate-950"
                                }
                            >
                                {item.icon}
                                {item.label}
                            </button>
                        ))}
                    </nav>

                    <div className="mt-auto rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
                        <div className="mb-3 text-base font-black">Need Help?</div>

                        <p className="mb-5 text-sm leading-6 text-slate-500">
                            Войдите в сессию поддержки по коду оператора.
                        </p>

                        <button
                            type="button"
                            onClick={openSupport}
                            className="inline-flex w-full items-center justify-center gap-2 rounded-2xl border border-blue-100 bg-blue-50 px-4 py-3 text-sm font-bold text-blue-700 transition hover:bg-blue-100"
                        >
                            <Headphones size={18} />
                            Join Support
                        </button>
                    </div>
                </div>
            </aside>

            <main className="min-h-screen pb-24 xl:pb-0 xl:pl-[270px]">
                <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/85 px-4 py-4 backdrop-blur-xl lg:px-10">
                    <div className="flex flex-wrap items-center justify-between gap-4">
                        <button
                            type="button"
                            onClick={() => navigate("/pcs")}
                            className="xl:hidden"
                        >
                            <div className="flex items-center gap-3">
                                <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-blue-600 text-white">
                                    <Monitor size={22} />
                                </div>

                                <div className="text-xl font-black">
                                    Remo<span className="text-blue-600">Desk</span>
                                </div>
                            </div>
                        </button>

                        <div className="relative ml-auto w-full max-w-[360px] max-sm:order-3 max-sm:max-w-none">
                            <Search
                                className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
                                size={20}
                            />

                            <input
                                value={searchValue}
                                onChange={(event) => onSearchChange(event.target.value)}
                                placeholder="Search computers..."
                                className="h-12 w-full rounded-2xl border border-slate-200 bg-white pl-12 pr-4 text-sm font-medium text-slate-800 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100"
                            />
                        </div>

                        <button
                            type="button"
                            onClick={handleLogout}
                            className="flex items-center gap-3 rounded-2xl px-2 py-1 transition hover:bg-slate-50"
                        >
                            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-blue-100 to-slate-100 text-lg font-black text-blue-700">
                                {(username || "U").slice(0, 1).toUpperCase()}
                            </div>

                            <div className="hidden text-left sm:block">
                                <div className="text-sm font-black text-slate-950">
                                    {username || "User"}
                                </div>

                                <div className="text-xs font-medium text-slate-500">
                                    Logout
                                </div>
                            </div>
                        </button>
                    </div>
                </header>

                {children}
            </main>

            <nav
                className="fixed bottom-0 left-0 right-0 z-40 border-t border-slate-200 bg-white/95 px-2 pb-[max(8px,env(safe-area-inset-bottom))] pt-2 shadow-[0_-10px_30px_rgba(15,23,42,0.10)] backdrop-blur-xl xl:hidden"
                aria-label="Mobile navigation"
            >
                <div className="grid grid-cols-5 gap-1">
                    {navItems.map((item) => (
                        <button
                            key={item.label}
                            type="button"
                            onClick={item.onClick}
                            className={
                                item.active
                                    ? "flex min-h-[60px] flex-col items-center justify-center gap-1 rounded-2xl bg-blue-50 px-1 text-blue-700"
                                    : "flex min-h-[60px] flex-col items-center justify-center gap-1 rounded-2xl px-1 text-slate-500 transition active:scale-95 active:bg-slate-100"
                            }
                        >
                            <div
                                className={
                                    item.active
                                        ? "text-blue-700"
                                        : "text-slate-500"
                                }
                            >
                                {item.icon}
                            </div>

                            <span className="max-w-full truncate text-[11px] font-black leading-none">
                                {item.shortLabel}
                            </span>
                        </button>
                    ))}
                </div>
            </nav>
        </div>
    );
}