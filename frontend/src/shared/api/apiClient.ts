import axios from "axios";
import { useAuthStore } from "../../features/auth/authStore";

export const apiClient = axios.create({
    baseURL: "",
    timeout: 15000,
});

apiClient.interceptors.request.use((config) => {
    const token = useAuthStore.getState().token;

    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
});

apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error.response?.status;

        if (status === 401 || status === 403) {
            useAuthStore.getState().logout();
            window.location.href = "/login";
        }

        return Promise.reject(error);
    },
);