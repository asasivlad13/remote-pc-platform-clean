import { useEffect, useMemo, useState } from "react";
import {
    ArrowLeft,
    Clock3,
    Download,
    FileText,
    History,
    Loader2,
    Monitor,
    RefreshCw,
    Search,
    UserRound,
    Wifi,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { DashboardLayout } from "../shared/ui/DashboardLayout";
import { getConnectionHistory } from "../features/history/historyApi";
import type { ConnectionHistoryItem } from "../features/history/historyTypes";

export function HistoryPage() {
    const navigate = useNavigate();

    const [searchValue, setSearchValue] = useState("");
    const [filterValue, setFilterValue] = useState("");
    const [history, setHistory] = useState<ConnectionHistoryItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const filteredHistory = useMemo(() => {
        const query = filterValue.trim().toLowerCase();

        if (!query) {
            return history;
        }

        return history.filter((item) => {
            const values = [
                item.pcName,
                item.username,
                item.clientIp,
                item.clientInfo,
                item.mode,
            ];

            return values.some((value) =>
                String(value || "").toLowerCase().includes(query),
            );
        });
    }, [history, filterValue]);

    const stats = useMemo(() => buildHistoryStats(history), [history]);

    useEffect(() => {
        void loadHistory();
    }, []);

    async function loadHistory() {
        try {
            setLoading(true);
            setError("");

            const result = await getConnectionHistory();

            setHistory(
                [...result].sort((a, b) => {
                    const dateA = new Date(getConnectedAt(a) || 0).getTime();
                    const dateB = new Date(getConnectedAt(b) || 0).getTime();

                    return dateB - dateA;
                }),
            );
        } catch (e) {
            setHistory([]);
            setError(e instanceof Error ? e.message : "Ошибка загрузки истории");
        } finally {
            setLoading(false);
        }
    }

    function exportJson() {
        const blob = new Blob([JSON.stringify(filteredHistory, null, 2)], {
            type: "application/json;charset=utf-8",
        });

        downloadBlob(blob, "connection-history.json");
    }

    function exportCsv() {
        const rows = [
            [
                "ID",
                "User",
                "PC",
                "IP",
                "Client",
                "Mode",
                "Start",
                "End",
                "DurationSeconds",
                "FPS",
                "LatencyMs",
                "Files",
            ],
        ];

        filteredHistory.forEach((item) => {
            rows.push([
                String(item.id ?? ""),
                String(item.username ?? ""),
                String(item.pcName ?? ""),
                String(item.clientIp ?? ""),
                String(item.clientInfo ?? ""),
                String(item.mode ?? ""),
                String(getConnectedAt(item) ?? ""),
                String(item.disconnectedAt ?? ""),
                String(item.durationSeconds ?? ""),
                String(item.avgFps ?? ""),
                String(item.avgLatency ?? ""),
                String(getFilesCount(item)),
            ]);
        });

        const csv = rows
            .map((row) =>
                row
                    .map((value) => `"${String(value).replaceAll('"', '""')}"`)
                    .join(","),
            )
            .join("\n");

        const blob = new Blob([csv], {
            type: "text/csv;charset=utf-8",
        });

        downloadBlob(blob, "connection-history.csv");
    }

    return (
        <DashboardLayout searchValue={searchValue} onSearchChange={setSearchValue}>
            <div className="px-5 py-10 lg:px-12">
                <div className="mb-8 flex flex-wrap items-start justify-between gap-5">
                    <div>
                        <div className="mb-4 inline-flex items-center gap-2 rounded-2xl bg-blue-50 px-4 py-2 text-sm font-black text-blue-700">
                            <History size={18} />
                            Connection history
                        </div>

                        <h1 className="text-5xl font-black tracking-tight text-slate-950 max-sm:text-4xl">
                            История подключений
                        </h1>

                        <p className="mt-4 max-w-3xl text-lg font-medium leading-8 text-slate-500">
                            Журнал удалённых сессий: личное подключение, учебные сессии,
                            техподдержка, файлы, длительность и качество подключения.
                        </p>
                    </div>

                    <div className="flex flex-wrap gap-3">
                        <button
                            type="button"
                            onClick={exportJson}
                            disabled={filteredHistory.length === 0}
                            className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-slate-300 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:text-slate-300"
                        >
                            <Download size={19} />
                            JSON
                        </button>

                        <button
                            type="button"
                            onClick={exportCsv}
                            disabled={filteredHistory.length === 0}
                            className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-slate-300 bg-white px-5 text-sm font-black text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:text-slate-300"
                        >
                            <FileText size={19} />
                            CSV
                        </button>

                        <button
                            type="button"
                            onClick={() => void loadHistory()}
                            className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl border border-blue-200 bg-blue-50 px-5 text-sm font-black text-blue-700 shadow-sm transition hover:bg-blue-100"
                        >
                            <RefreshCw size={19} />
                            Обновить
                        </button>

                        <button
                            type="button"
                            onClick={() => navigate("/pcs")}
                            className="inline-flex h-14 items-center justify-center gap-3 rounded-2xl bg-slate-950 px-5 text-sm font-black text-white shadow-lg shadow-slate-950/15 transition hover:bg-slate-800"
                        >
                            <ArrowLeft size={19} />
                            Рабочий стол
                        </button>
                    </div>
                </div>

                <div className="mb-8 grid grid-cols-4 gap-4 max-2xl:grid-cols-2 max-md:grid-cols-1">
                    <HistoryStatCard
                        title="Всего сессий"
                        value={stats.total}
                        icon={<History size={24} />}
                        tone="blue"
                    />

                    <HistoryStatCard
                        title="Активные"
                        value={stats.active}
                        icon={<Wifi size={24} />}
                        tone="emerald"
                    />

                    <HistoryStatCard
                        title="Файлов"
                        value={stats.files}
                        icon={<FileText size={24} />}
                        tone="violet"
                    />

                    <HistoryStatCard
                        title="Средняя длительность"
                        value={stats.averageDurationLabel}
                        icon={<Clock3 size={24} />}
                        tone="amber"
                    />
                </div>

                <div className="mb-6 rounded-[30px] border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="relative">
                        <Search
                            size={21}
                            className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
                        />

                        <input
                            value={filterValue}
                            onChange={(event) => setFilterValue(event.target.value)}
                            placeholder="Фильтр по ПК, пользователю, IP, устройству или режиму..."
                            className="h-14 w-full rounded-2xl border border-slate-200 bg-white pl-12 pr-4 text-sm font-bold text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100"
                        />
                    </div>
                </div>

                {error && (
                    <div className="mb-6 rounded-3xl border border-red-200 bg-red-50 px-5 py-4 text-sm font-black text-red-700 shadow-sm">
                        {error}
                    </div>
                )}

                {loading ? (
                    <LoadingBlock />
                ) : filteredHistory.length === 0 ? (
                    <EmptyHistory />
                ) : (
                    <div className="grid gap-5">
                        {filteredHistory.map((item, index) => (
                            <HistoryCard
                                key={`${item.id ?? "history"}-${index}`}
                                item={item}
                            />
                        ))}
                    </div>
                )}
            </div>
        </DashboardLayout>
    );
}

function HistoryCard({ item }: { item: ConnectionHistoryItem }) {
    const active = !item.disconnectedAt;
    const mode = normalizeMode(item.mode);
    const files = getFilesCount(item);

    return (
        <article className="overflow-hidden rounded-[34px] border border-slate-200 bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-xl hover:shadow-slate-200/80">
            <div className="flex flex-wrap items-start justify-between gap-5 border-b border-slate-100 bg-slate-50 px-6 py-5">
                <div>
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                        <ModePill mode={mode} />

                        {active ? (
                            <span className="rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-black text-emerald-700">
                                Сессия активна
                            </span>
                        ) : (
                            <span className="rounded-full bg-slate-200 px-3 py-1.5 text-xs font-black text-slate-600">
                                Завершена
                            </span>
                        )}
                    </div>

                    <h2 className="text-2xl font-black text-slate-950">
                        {formatDateTime(getConnectedAt(item))}
                    </h2>

                    <p className="mt-1 text-sm font-semibold text-slate-500">
                        Отключение:{" "}
                        {item.disconnectedAt
                            ? formatDateTime(item.disconnectedAt)
                            : "сессия ещё активна"}
                    </p>
                </div>

                <div className="rounded-2xl bg-white px-5 py-4 text-right shadow-sm">
                    <div className="text-sm font-black uppercase tracking-wide text-slate-400">
                        Длительность
                    </div>

                    <div className="mt-1 text-xl font-black text-slate-950">
                        {formatDuration(item.durationSeconds)}
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-[minmax(0,1fr)_320px] gap-5 p-6 max-xl:grid-cols-1">
                <div className="grid gap-4">
                    <InfoRow
                        icon={<Monitor size={20} />}
                        label="ПК"
                        value={item.pcName || "—"}
                    />

                    <InfoRow
                        icon={<UserRound size={20} />}
                        label="Пользователь"
                        value={item.username || "—"}
                    />

                    <InfoRow
                        icon={<Wifi size={20} />}
                        label="IP / устройство"
                        value={`${item.clientIp || "—"} · ${item.clientInfo || "—"}`}
                    />
                </div>

                <div className="grid content-start gap-3">
                    <MetricBox
                        label="Средний FPS"
                        value={formatMetric(item.avgFps)}
                        suffix=""
                    />

                    <MetricBox
                        label="Средняя задержка"
                        value={formatMetric(item.avgLatency)}
                        suffix="ms"
                    />

                    <MetricBox
                        label="Файлы"
                        value={files}
                        suffix=""
                    />
                </div>
            </div>
        </article>
    );
}

function HistoryStatCard({
                             title,
                             value,
                             icon,
                             tone,
                         }: {
    title: string;
    value: number | string;
    icon: React.ReactNode;
    tone: "blue" | "emerald" | "violet" | "amber";
}) {
    const toneClass = {
        blue: "bg-blue-50 text-blue-700",
        emerald: "bg-emerald-50 text-emerald-700",
        violet: "bg-violet-50 text-violet-700",
        amber: "bg-amber-50 text-amber-700",
    }[tone];

    return (
        <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
            <div className={`mb-4 flex h-12 w-12 items-center justify-center rounded-2xl ${toneClass}`}>
                {icon}
            </div>

            <div className="text-3xl font-black text-slate-950">
                {value}
            </div>

            <div className="mt-1 text-sm font-black text-slate-400">
                {title}
            </div>
        </section>
    );
}

function InfoRow({
                     icon,
                     label,
                     value,
                 }: {
    icon: React.ReactNode;
    label: string;
    value: string;
}) {
    return (
        <div className="flex gap-3 rounded-2xl bg-slate-50 p-4">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-blue-700 shadow-sm">
                {icon}
            </div>

            <div className="min-w-0">
                <div className="text-xs font-black uppercase tracking-wide text-slate-400">
                    {label}
                </div>

                <div className="mt-1 break-words text-sm font-black text-slate-900">
                    {value}
                </div>
            </div>
        </div>
    );
}

function MetricBox({
                       label,
                       value,
                       suffix,
                   }: {
    label: string;
    value: number | string;
    suffix: string;
}) {
    return (
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-slate-400">
                {label}
            </div>

            <div className="mt-1 text-2xl font-black text-slate-950">
                {value}
                {suffix && <span className="ml-1 text-sm text-slate-400">{suffix}</span>}
            </div>
        </div>
    );
}

function ModePill({ mode }: { mode: string }) {
    if (mode === "Education") {
        return (
            <span className="rounded-full bg-violet-50 px-3 py-1.5 text-xs font-black text-violet-700">
                Учебная сессия
            </span>
        );
    }

    if (mode === "Support") {
        return (
            <span className="rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-black text-emerald-700">
                Техподдержка
            </span>
        );
    }

    return (
        <span className="rounded-full bg-blue-50 px-3 py-1.5 text-xs font-black text-blue-700">
            Личное подключение
        </span>
    );
}

function LoadingBlock() {
    return (
        <div className="rounded-[34px] border border-slate-200 bg-white p-12 text-center shadow-sm">
            <Loader2 className="mx-auto mb-4 animate-spin text-blue-600" size={46} />
            <p className="font-black text-slate-600">
                Загрузка истории подключений...
            </p>
        </div>
    );
}

function EmptyHistory() {
    return (
        <div className="rounded-[34px] border border-slate-200 bg-white p-12 text-center shadow-sm">
            <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-[28px] bg-slate-100 text-slate-500">
                <History size={42} />
            </div>

            <h2 className="text-3xl font-black text-slate-950">
                История пустая
            </h2>

            <p className="mx-auto mt-3 max-w-xl text-base font-semibold leading-7 text-slate-500">
                После удалённых подключений, учебных сессий или техподдержки записи появятся здесь.
            </p>
        </div>
    );
}

function buildHistoryStats(items: ConnectionHistoryItem[]) {
    const total = items.length;
    const active = items.filter((item) => !item.disconnectedAt).length;
    const files = items.reduce((sum, item) => sum + getFilesCount(item), 0);

    const completedDurations = items
        .map((item) => item.durationSeconds)
        .filter((value): value is number => typeof value === "number" && value > 0);

    const averageDuration =
        completedDurations.length > 0
            ? Math.round(
                completedDurations.reduce((sum, value) => sum + value, 0) /
                completedDurations.length,
            )
            : 0;

    return {
        total,
        active,
        files,
        averageDurationLabel: averageDuration > 0 ? formatDuration(averageDuration) : "—",
    };
}

function getConnectedAt(item: ConnectionHistoryItem): string | undefined {
    return item.connectedAt || item.timestamp;
}

function getFilesCount(item: ConnectionHistoryItem): number {
    if (typeof item.filesTotal === "number") {
        return item.filesTotal;
    }

    return Number(item.filesSent || 0) + Number(item.filesReceived || 0);
}

function normalizeMode(mode?: string): string {
    const value = String(mode || "").toLowerCase();

    if (value.includes("education") || value.includes("teacher") || value.includes("student")) {
        return "Education";
    }

    if (value.includes("support") || value.includes("operator") || value.includes("client")) {
        return "Support";
    }

    return "Personal";
}

function formatMetric(value?: number | null): string {
    if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) {
        return "—";
    }

    return String(Math.round(value * 10) / 10);
}

function formatDuration(seconds?: number | null): string {
    if (!seconds || seconds <= 0) {
        return "ещё активна";
    }

    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);

    if (h > 0) {
        return `${h} ч ${m} мин ${s} сек`;
    }

    if (m > 0) {
        return `${m} мин ${s} сек`;
    }

    return `${s} сек`;
}

function formatDateTime(value?: string | null): string {
    if (!value) {
        return "—";
    }

    try {
        return new Date(value).toLocaleString();
    } catch {
        return value;
    }
}

function downloadBlob(blob: Blob, filename: string) {
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");

    link.href = objectUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();

    URL.revokeObjectURL(objectUrl);
}