import { useEffect, useState } from "react";
import { GraduationCap } from "lucide-react";
import {
    getMyActiveStudentSession,
    getMyActiveTeacherSession,
} from "../educationApi";
import type { EducationSessionResponse } from "../educationTypes";

type ActiveEducationSession = {
    role: "teacher" | "student";
    session: EducationSessionResponse;
};

export function ReturnToEducationSessionButton() {
    const [activeSession, setActiveSession] = useState<ActiveEducationSession | null>(null);

    useEffect(() => {
        void checkActiveSession();

        const timer = window.setInterval(() => {
            void checkActiveSession();
        }, 5000);

        return () => window.clearInterval(timer);
    }, []);

    async function checkActiveSession() {
        try {
            const teacherSession = await getMyActiveTeacherSession();

            if (teacherSession?.sessionCode && !isSessionFinished(teacherSession)) {
                setActiveSession({
                    role: "teacher",
                    session: teacherSession,
                });
                return;
            }

            const studentSession = await getMyActiveStudentSession();

            if (studentSession?.sessionCode && !isSessionFinished(studentSession)) {
                setActiveSession({
                    role: "student",
                    session: studentSession,
                });
                return;
            }

            setActiveSession(null);
        } catch {
            setActiveSession(null);
        }
    }

    function returnToSession() {
        if (!activeSession) {
            return;
        }

        const code = encodeURIComponent(activeSession.session.sessionCode);

        if (activeSession.role === "teacher") {
            window.location.href = `/education/teacher/${code}`;
            return;
        }

        window.location.href = `/education/student/${code}`;
    }

    if (!activeSession) {
        return null;
    }

    return (
        <button
            type="button"
            onClick={returnToSession}
            className="inline-flex h-12 items-center justify-center gap-2 rounded-2xl border border-blue-200 bg-blue-50 px-4 text-sm font-black text-blue-700 transition hover:bg-blue-100"
        >
            <GraduationCap size={20} />
            Вернуться в учебную сессию
        </button>
    );
}

function isSessionFinished(session: EducationSessionResponse): boolean {
    const status = String(session.status || "").toUpperCase();

    return (
        status === "FINISHED" ||
        status === "CANCELLED" ||
        status === "CANCELED" ||
        status === "ENDED" ||
        status === "CLOSED"
    );
}