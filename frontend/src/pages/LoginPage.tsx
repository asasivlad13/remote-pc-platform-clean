import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthCard } from "../shared/ui/AuthCard";
import { Button } from "../shared/ui/Button";
import { TextInput } from "../shared/ui/TextInput";
import { loginRequest } from "../features/auth/authApi";
import { useAuthStore } from "../features/auth/authStore";

export function LoginPage() {
    const navigate = useNavigate();
    const setAuth = useAuthStore((state) => state.setAuth);

    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");

        const trimmedUsername = username.trim();

        if (!trimmedUsername || !password) {
            setError("Введите логин и пароль");
            return;
        }

        try {
            setLoading(true);
            const response = await loginRequest({
                username: trimmedUsername,
                password,
            });

            setAuth(response.token, trimmedUsername);
            navigate("/pcs");
        } catch {
            setError("Неверный логин или пароль");
        } finally {
            setLoading(false);
        }
    }

    return (
        <AuthCard
            icon="💻"
            title="Remote PC"
            subtitle="Удалённый доступ, учебные сессии и техподдержка"
        >
            <form onSubmit={handleSubmit} className="grid gap-4">
                <TextInput
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    placeholder="Username"
                    autoComplete="username"
                />

                <TextInput
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="Password"
                    type="password"
                    autoComplete="current-password"
                />

                <Button type="submit" disabled={loading} className="w-full">
                    {loading ? "Вход..." : "Войти"}
                </Button>
            </form>

            {error && (
                <div className="mt-4 rounded-2xl border border-red-400/35 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                    ❌ {error}
                </div>
            )}

            <div className="mt-6 border-t border-white/10 pt-5 text-sm text-white/55">
                Нет аккаунта?{" "}
                <Link to="/register" className="font-bold text-blue-300 hover:text-blue-200">
                    Зарегистрироваться
                </Link>
            </div>
        </AuthCard>
    );
}