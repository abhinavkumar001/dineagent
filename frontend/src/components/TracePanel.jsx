export default function TracePanel({ run }) {
  return (
    <section>
      <h2>How the agent worked</h2>
      <ol className="trace">
        {!run && <li className="final"><span className="sum empty">Tool calls for your last message appear here.</span></li>}
        {run?.trace.map((t, i) => (
          <li key={i} className={t.error ? "err" : ""}>
            <code>{t.tool}</code>
            <span className="args">{JSON.stringify(t.args)}</span>
            <span className="sum">{t.summary} · {t.millis} ms</span>
          </li>
        ))}
        {run && (
          <li className="final">
            <span className="sum">
              Answered after {run.llmCalls} model call{run.llmCalls === 1 ? "" : "s"}, {(run.latencyMs / 1000).toFixed(1)} s,{" "}
              {run.inputTokens + run.outputTokens} tokens
            </span>
          </li>
        )}
      </ol>
    </section>
  );
}
