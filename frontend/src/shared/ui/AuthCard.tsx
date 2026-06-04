type AuthCardProps = {
    icon: string;
    title: string;
    subtitle: string;
    children: React.ReactNode;
};

export function AuthCard({ icon, title, subtitle, children }: AuthCardProps) {
    return (
        <main className="flex min-h-screen items-center justify-center px-5 py-8">
            <section className="w-full max-w-md rounded-[28px] border border-white/15 bg-white/10 p-8 text-center shadow-2xl backdrop-blur-xl">
                <div className="mx-auto mb-5 flex h-[72px] w-[72px] items-center justify-center rounded-2xl bg-gradient-to-br from-blue-500 to-blue-700 text-4xl">
                    {icon}
                </div>

                <h1 className="mb-2 text-3xl font-black">{title}</h1>
                <p className="mb-8 text-sm text-white/55">{subtitle}</p>

                {children}
            </section>
        </main>
    );
}