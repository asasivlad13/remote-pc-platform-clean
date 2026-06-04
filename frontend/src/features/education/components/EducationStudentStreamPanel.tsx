import { useEffect, useState } from "react";
import { Maximize2, Monitor, Wifi } from "lucide-react";
import type { PcDetailsResponse } from "../../pcs/pcTypes";
import { useGStreamerWebRtc } from "../../remote/useGStreamerWebRtc";

type EducationStreamPanelProps = {
    pc: PcDetailsResponse | null;
    title: string;
    subtitle: string;
};

export function EducationStreamPanel({
                                         pc,
                                         title,
                                         subtitle,
                                     }: EducationStreamPanelProps) {
    const [isFullscreen, setIsFullscreen] = useState(false);

    const {
        videoRef,
        videoStatus,
        videoError,
        fps,
    } = useGStreamerWebRtc({
        pc,
        pcId: pc?.id || 0,
        pcName: pc?.name || title,
    });

    useEffect(() => {
        function handleFullscreenChange() {
            setIsFullscreen(document.fullscreenElement?.id === "education-stream-target");
        }

        document.addEventListener("fullscreenchange", handleFullscreenChange);
        handleFullscreenChange();

        return () => {
            document.removeEventListener("fullscreenchange", handleFullscreenChange);
        };
    }, []);

    function handleFullscreen() {
        const element = document.getElementById("education-stream-target");

        if (!element) {
            return;
        }

        if (!document.fullscreenElement) {
            void element.requestFullscreen();
            return;
        }

        void document.exitFullscreen();
    }

    const hasStream = Boolean(pc?.webrtcUrl && pc?.streamName);

    return (
        <section className="overflow-hidden rounded-[30px] border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between gap-4 px-5 py-4">
                <div>
                    <h2 className="text-xl font-black text-slate-950">
                        {title}
                    </h2>

                    <p className="mt-1 text-sm font-semibold text-slate-500">
                        {subtitle}
                    </p>
                </div>

                <div className="flex items-center gap-2">
                    <StatusPill status={videoStatus} />

                    {fps !== null && (
                        <span className="rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-black text-emerald-700">
                            FPS {fps}
                        </span>
                    )}

                    <button
                        type="button"
                        onClick={handleFullscreen}
                        className="flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-600 transition hover:bg-blue-50 hover:text-blue-700"
                    >
                        <Maximize2 size={19} />
                    </button>
                </div>
            </div>

            <div
                id="education-stream-target"
                className="relative mx-5 mb-5 flex aspect-video items-center justify-center overflow-hidden rounded-[26px] bg-slate-950"
            >
                {hasStream ? (
                    <video
                        ref={videoRef}
                        autoPlay
                        playsInline
                        muted
                        className="h-full w-full bg-black object-contain"
                    />
                ) : (
                    <div className="text-center">
                        <div className="mx-auto mb-5 flex h-20 w-20 items-center justify-center rounded-[28px] bg-white/10 text-blue-300">
                            <Monitor size={44} />
                        </div>

                        <h3 className="text-2xl font-black text-white">
                            Ожидание демонстрации
                        </h3>

                        <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-slate-400">
                            Трансляция экрана пока недоступна.
                        </p>
                    </div>
                )}

                {videoError && (
                    <div className="absolute bottom-5 left-1/2 w-[min(720px,calc(100%-40px))] -translate-x-1/2 rounded-2xl border border-red-400/25 bg-red-500/15 px-5 py-3 text-sm font-bold text-red-100 backdrop-blur">
                        {videoError}
                    </div>
                )}

                {isFullscreen && (
                    <button
                        type="button"
                        onClick={handleFullscreen}
                        className="absolute right-5 top-5 rounded-2xl bg-white/90 px-4 py-2 text-sm font-black text-slate-900 shadow-lg"
                    >
                        Выйти из полного экрана
                    </button>
                )}
            </div>
        </section>
    );
}

function StatusPill({ status }: { status: string }) {
    if (status === "playing" || status === "connected") {
        return (
            <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-black text-emerald-700">
                <Wifi size={14} />
                Трансляция активна
            </span>
        );
    }

    if (status === "error" || status === "closed") {
        return (
            <span className="inline-flex items-center gap-2 rounded-full bg-red-50 px-3 py-1.5 text-xs font-black text-red-700">
                <Wifi size={14} />
                Ошибка видео
            </span>
        );
    }

    return (
        <span className="inline-flex items-center gap-2 rounded-full bg-amber-50 px-3 py-1.5 text-xs font-black text-amber-700">
            <Wifi size={14} />
            Подключение
        </span>
    );
}