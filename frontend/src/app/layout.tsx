import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Ark Nova Async',
  description: 'Async multiplayer Ark Nova via Discord',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-stone-50 text-stone-900">
        <header className="bg-green-900 text-white px-6 py-4 flex items-center gap-4 shadow-md">
          <span className="text-2xl">🦁</span>
          <span className="font-bold text-xl tracking-tight">Ark Nova Async</span>
          <nav className="ml-auto flex gap-6 text-sm font-medium">
            <a href="/games" className="hover:text-green-300 transition-colors">Games</a>
            <a href="/cards" className="hover:text-green-300 transition-colors">Cards</a>
            <a href="/rules" className="hover:text-green-300 transition-colors">Rules</a>
          </nav>
        </header>
        <main className="max-w-7xl mx-auto px-4 py-8">
          {children}
        </main>
        <footer className="border-t border-stone-200 mt-16 py-6 text-center text-stone-400 text-sm">
          <p>
            Ark Nova Async — Open Source Fan Project |{' '}
            <a
              href="https://github.com/your-org/arknova-async"
              className="underline hover:text-stone-600"
            >
              GitHub
            </a>{' '}
            | Not affiliated with Capstone Games
          </p>
        </footer>
      </body>
    </html>
  );
}
