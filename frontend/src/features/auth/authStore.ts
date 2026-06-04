import { create } from "zustand";

type AuthState = {
    token: string | null;
    username: string | null;
    setAuth: (token: string, username: string) => void;
    logout: () => void;
};

const tokenFromStorage = localStorage.getItem("token");
const usernameFromStorage = localStorage.getItem("username");

export const useAuthStore = create<AuthState>((set) => ({
    token: tokenFromStorage,
    username: usernameFromStorage,

    setAuth: (token, username) => {
        localStorage.setItem("token", token);
        localStorage.setItem("username", username);
        set({ token, username });
    },

    logout: () => {
        localStorage.removeItem("token");
        localStorage.removeItem("username");
        set({ token: null, username: null });
    },
}));