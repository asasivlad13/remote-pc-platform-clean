import type { PcDetailsResponse } from "../pcs/pcTypes";
import type {
    EducationParticipantResponse,
    EducationSessionResponse,
} from "./educationTypes";

export function getEducationTitle(session: EducationSessionResponse | null): string {
    return session?.title?.trim() || "Учебная сессия";
}

export function getEducationParticipants(
    session: EducationSessionResponse | null,
): EducationParticipantResponse[] {
    if (!session) {
        return [];
    }

    return (
        session.participants ||
        session.students ||
        session.connectedStudents ||
        []
    );
}

export function buildTeacherPc(
    session: EducationSessionResponse | null,
): PcDetailsResponse | null {
    if (!session) {
        return null;
    }

    const webrtcUrl = session.teacherPcWebrtcUrl || session.webrtcUrl || "";
    const streamName = session.teacherPcStreamName || session.streamName || "";

    if (!webrtcUrl || !streamName) {
        return null;
    }

    return {
        id: session.teacherPcId || session.pcId || session.id || 0,
        name: session.teacherPcName || session.pcName || "Экран преподавателя",
        macAddress: session.teacherPcMacAddress || session.pcMacAddress || "",
        status: session.teacherPcStatus || session.pcStatus || "ONLINE",
        lastConnection: null,
        screenWidth: session.teacherPcScreenWidth || session.screenWidth || 1920,
        screenHeight: session.teacherPcScreenHeight || session.screenHeight || 1080,
        webrtcUrl,
        streamName,
    };
}

export function getParticipantDisplayName(participant: EducationParticipantResponse): string {
    return (
        participant.displayName ||
        participant.fullName ||
        participant.username ||
        "Пользователь"
    );
}

export function getParticipantStatus(participant: EducationParticipantResponse) {
    const status = String(participant.status || "").toUpperCase();

    if (participant.screenSharing || status === "SCREEN_SHARING") {
        return {
            label: "Демонстрирует экран",
            className: "text-violet-700",
            dotClass: "bg-violet-500",
        };
    }

    if (participant.controlAllowed || status === "CONTROL_ALLOWED") {
        return {
            label: "Управление разрешено",
            className: "text-blue-700",
            dotClass: "bg-blue-500",
        };
    }

    if (participant.controlRequested || status === "CONTROL_REQUESTED") {
        return {
            label: "Запросил управление",
            className: "text-amber-700",
            dotClass: "bg-amber-500",
        };
    }

    if (
        status === "WAITING" ||
        status === "PENDING" ||
        status === "REQUESTED" ||
        status.includes("WAIT")
    ) {
        return {
            label: "Ожидает подтверждения",
            className: "text-amber-700",
            dotClass: "bg-amber-500",
        };
    }

    if (status === "REJECTED" || status === "DECLINED" || status === "DENIED") {
        return {
            label: "Отклонён",
            className: "text-red-700",
            dotClass: "bg-red-500",
        };
    }

    if (participant.online === false || status === "OFFLINE") {
        return {
            label: "Оффлайн",
            className: "text-slate-500",
            dotClass: "bg-slate-300",
        };
    }

    if (status === "APPROVED") {
        return {
            label: "Подтверждён",
            className: "text-emerald-700",
            dotClass: "bg-emerald-500",
        };
    }

    return {
        label: "Онлайн",
        className: "text-emerald-700",
        dotClass: "bg-emerald-500",
    };
}

export function getInitials(name: string): string {
    return name
        .split(" ")
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0])
        .join("")
        .toUpperCase();
}

export function formatEducationTime(value?: string): string {
    if (!value) {
        return "—";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "—";
    }

    return date.toLocaleTimeString("ru-RU", {
        hour: "2-digit",
        minute: "2-digit",
    });
}