import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthCard } from "../shared/ui/AuthCard";
import { Button } from "../shared/ui/Button";
import { TextInput } from "../shared/ui/TextInput";
import { registerRequest } from "../features/auth/authApi";

export function RegisterPage() {
    const navigate = useNavigate();

    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");

    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [loading, setLoading] = useState(false);

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        setSuccess("");

        const trimmedUsername = username.trim();

        if (!trimmedUsername || !password) {
            setError("Введите логин и пароль");
            return;
        }

        if (password.length < 6) {
            setError("Пароль должен быть не короче 6 символов");
            return;
        }

        if (password !== confirmPassword) {
            setError("Пароли не совпадают");
            return;
        }

        try {
            setLoading(true);

            await registerRequest({
                username: trimmedUsername,
                password,
            });

            setSuccess("Аккаунт создан. Сейчас откроется страница входа.");

            setTimeout(() => {
                navigate("/login");
            }, 1200);
        } catch {
            setError("Не удалось создать аккаунт. Возможно, логин уже занят.");
        } finally {
            setLoading(false);
        }
    }

    return (
        <AuthCard icon="📝" title="Создание аккаунта" subtitle="Зарегистрируйтесь для доступа к системе">
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
                    autoComplete="new-password"
                />

                <TextInput
                    value={confirmPassword}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                    placeholder="Confirm password"
                    type="password"
                    autoComplete="new-password"
                />

                <Button type="submit" disabled={loading} className="w-full">
                    {loading ? "Создание..." : "Зарегистрироваться"}
                </Button>
            </form>

            {error && (
                <div className="mt-4 rounded-2xl border border-red-400/35 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                    ❌ {error}
                </div>
            )}

            {success && (
                <div className="mt-4 rounded-2xl border border-emerald-400/35 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200">
                    ✅ {success}
                </div>
            )}

            <div className="mt-6 border-t border-white/10 pt-5 text-sm text-white/55">
                Уже есть аккаунт?{" "}
                <Link to="/login" className="font-bold text-blue-300 hover:text-blue-200">
                    Войти
                </Link>
            </div>
        </AuthCard>
    );
}