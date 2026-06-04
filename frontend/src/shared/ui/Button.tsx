import type { ButtonHTMLAttributes } from "react";
import { clsx } from "clsx";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: "primary" | "glass" | "danger";
};

export function Button({ className, variant = "primary", ...props }: ButtonProps) {
    return (
        <button
            className={clsx(
                "inline-flex min-h-12 items-center justify-center rounded-2xl px-5 py-3 font-bold transition active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60",
                variant === "primary" &&
                "border-0 bg-gradient-to-br from-blue-500 to-blue-700 text-white shadow-lg shadow-blue-500/25 hover:translate-y-[-1px]",
                variant === "glass" &&
                "border border-white/15 bg-white/10 text-white backdrop-blur-md hover:bg-white/15",
                variant === "danger" &&
                "border border-red-400/40 bg-red-500/20 text-red-100 hover:bg-red-500/30",
                className,
            )}
            {...props}
        />
    );
}