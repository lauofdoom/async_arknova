export default async function HomePage() {
  // Fetch active game count from bot API
  let activeGameCount = 0;
  let cardCount = 0;
  try {
    const [gamesRes, cardsRes] = await Promise.all([
      fetch(`${process.env.API_BASE_URL ?? 'http://localhost:8080'}/api/games?status=ACTIVE`, {
        next: { revalidate: 30 },
      }),
      fetch(`${process.env.API_BASE_URL ?? 'http://localhost:8080'}/api/cards/count`, {
        next: { revalidate: 3600 },
      }),
    ]);
    if (gamesRes.ok) {
      const games = await gamesRes.json();
      activeGameCount = Array.isArray(games) ? games.length : 0;
    }
    if (cardsRes.ok) {
      const counts = await cardsRes.json();
      cardCount = counts.total ?? 0;
    }
  } catch {
    // Bot may not be running in dev; show zeros gracefully
  }

  return (
    <div>
      {/* Hero */}
      <section className="text-center py-16">
        <h1 className="text-5xl font-bold text-green-900 mb-4">
          🦁 Ark Nova Async
        </h1>
        <p className="text-xl text-stone-600 max-w-2xl mx-auto mb-8">
          Play Ark Nova asynchronously with friends via Discord. Take your turn
          whenever you have a moment — across hours, days, or weeks.
        </p>
        <div className="flex gap-4 justify-center">
          <a
            href="/games"
            className="bg-green-800 text-white px-8 py-3 rounded-lg font-semibold
                       hover:bg-green-700 transition-colors shadow-sm"
          >
            View Active Games
          </a>
          <a
            href="/cards"
            className="border border-green-800 text-green-800 px-8 py-3 rounded-lg
                       font-semibold hover:bg-green-50 transition-colors"
          >
            Browse Cards
          </a>
        </div>
      </section>

      {/* Stats */}
      <section className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-16">
        <StatCard
          value={activeGameCount}
          label="Active Games"
          icon="🎮"
          href="/games"
        />
        <StatCard
          value={cardCount}
          label="Cards in Database"
          icon="🃏"
          href="/cards"
        />
        <StatCard
          value="0%"
          label="Cards Automated"
          icon="⚙️"
          note="Help implement card effects!"
          href="https://github.com/your-org/arknova-async/issues"
        />
      </section>

      {/* How to play */}
      <section className="bg-white border border-stone-200 rounded-xl p-8 mb-12">
        <h2 className="text-2xl font-bold text-green-900 mb-6">How to Play</h2>
        <ol className="space-y-4">
          {[
            {
              step: '1',
              title: 'Add the bot to your Discord server',
              desc: 'Invite the Ark Nova Async bot to any server where you want to play.',
            },
            {
              step: '2',
              title: 'Create a game with /arknova-ping',
              desc: 'Run /arknova create in any channel. The bot creates a dedicated thread for your game.',
            },
            {
              step: '3',
              title: 'Players join and choose maps',
              desc: 'Each player runs /arknova join and selects their starting zoo map.',
            },
            {
              step: '4',
              title: 'Take turns at your own pace',
              desc: 'Use the 5 action commands to take your turn. The bot pings you when it\'s your move.',
            },
          ].map(({ step, title, desc }) => (
            <li key={step} className="flex gap-4">
              <span className="flex-none w-8 h-8 rounded-full bg-green-800 text-white
                               flex items-center justify-center font-bold text-sm">
                {step}
              </span>
              <div>
                <p className="font-semibold text-stone-900">{title}</p>
                <p className="text-stone-600 text-sm">{desc}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {/* Contribute */}
      <section className="bg-amber-50 border border-amber-200 rounded-xl p-8">
        <h2 className="text-2xl font-bold text-amber-900 mb-3">
          Help Us Automate Card Effects
        </h2>
        <p className="text-amber-800 mb-4">
          This is an open-source project. Card effects are currently resolved manually
          by players — but we&apos;re progressively implementing them in the engine.
          Every card you implement makes the game better for everyone.
        </p>
        <a
          href="https://github.com/your-org/arknova-async/issues"
          className="inline-block bg-amber-700 text-white px-6 py-2 rounded-lg
                     font-medium hover:bg-amber-600 transition-colors"
        >
          Contribute on GitHub
        </a>
      </section>
    </div>
  );
}

function StatCard({
  value,
  label,
  icon,
  note,
  href,
}: {
  value: number | string;
  label: string;
  icon: string;
  note?: string;
  href: string;
}) {
  return (
    <a
      href={href}
      className="bg-white border border-stone-200 rounded-xl p-6 text-center
                 hover:border-green-400 hover:shadow-sm transition-all block"
    >
      <div className="text-4xl mb-2">{icon}</div>
      <div className="text-3xl font-bold text-green-900">{value}</div>
      <div className="text-stone-600 mt-1">{label}</div>
      {note && <div className="text-xs text-stone-400 mt-1">{note}</div>}
    </a>
  );
}
