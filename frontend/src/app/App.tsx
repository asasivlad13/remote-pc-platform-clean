import { Navigate, Route, Routes } from "react-router-dom";
import { LoginPage } from "../pages/LoginPage";
import { RegisterPage } from "../pages/RegisterPage";
import { PcsPage } from "../pages/PcsPage";
import { RemoteAccessPage } from "../pages/RemoteAccessPage";
import { EducationTeacherPage } from "../pages/EducationTeacherPage";
import { EducationStudentPage } from "../pages/EducationStudentPage";
import { SupportHomePage } from "../pages/SupportHomePage";
import { SupportOperatorPage } from "../pages/SupportOperatorPage";
import { SupportClientPage } from "../pages/SupportClientPage";
import { HistoryPage } from "../pages/HistoryPage";
import { SettingsPage } from "../pages/SettingsPage";
import { MobileRemoteHubPage } from "../pages/MobileRemoteHubPage";
import { PresentationRemotePage } from "../pages/PresentationRemotePage";
import { TouchpadRemotePage } from "../pages/TouchpadRemotePage";
import { GamepadRemotePage } from "../pages/GamepadRemotePage";
import { ProtectedRoute } from "./ProtectedRoute";

export default function App() {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/pcs" replace />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            <Route
                path="/pcs"
                element={
                    <ProtectedRoute>
                        <PcsPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/history"
                element={
                    <ProtectedRoute>
                        <HistoryPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/settings"
                element={
                    <ProtectedRoute>
                        <SettingsPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/remote/:pcId"
                element={
                    <ProtectedRoute>
                        <RemoteAccessPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/education/teacher/:sessionCode"
                element={
                    <ProtectedRoute>
                        <EducationTeacherPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/education/student/:sessionCode"
                element={
                    <ProtectedRoute>
                        <EducationStudentPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/education-student"
                element={
                    <ProtectedRoute>
                        <EducationStudentPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/support"
                element={
                    <ProtectedRoute>
                        <SupportHomePage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/support/operator/:sessionCode"
                element={
                    <ProtectedRoute>
                        <SupportOperatorPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/support/client/:sessionCode"
                element={
                    <ProtectedRoute>
                        <SupportClientPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/mobile-remote"
                element={
                    <ProtectedRoute>
                        <MobileRemoteHubPage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/mobile-remote/presentation"
                element={
                    <ProtectedRoute>
                        <PresentationRemotePage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/mobile-remote/touchpad"
                element={
                    <ProtectedRoute>
                        <TouchpadRemotePage />
                    </ProtectedRoute>
                }
            />

            <Route
                path="/mobile-remote/gamepad"
                element={
                    <ProtectedRoute>
                        <GamepadRemotePage />
                    </ProtectedRoute>
                }
            />

            <Route path="*" element={<Navigate to="/pcs" replace />} />
        </Routes>
    );
}
