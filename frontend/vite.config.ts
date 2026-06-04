import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const backendTarget = "http://127.0.0.1:8080";

export default defineConfig({
    plugins: [
        react(),
        tailwindcss(),
    ],

    server: {
        host: "0.0.0.0",
        port: 5173,
        strictPort: true,

        proxy: {
            "/api": {
                target: backendTarget,
                changeOrigin: true,
                secure: false,
            },

            "/auth": {
                target: backendTarget,
                changeOrigin: true,
                secure: false,
            },

            "/pcs": {
                target: backendTarget,
                changeOrigin: true,
                secure: false,
            },

            "/ws": {
                target: backendTarget,
                ws: true,
                changeOrigin: true,
                secure: false,
            },
        },
    },
});