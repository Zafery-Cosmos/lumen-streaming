export default function PageHeader({ title, lede }: { title: string; lede: string }) {
  return (
    <div className="mb-14">
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{title}</h1>
      <p className="mt-3 max-w-2xl text-lumen-muted">{lede}</p>
    </div>
  );
}
