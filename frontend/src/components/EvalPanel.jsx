import { useState } from "react";
import { api } from "../api.js";

const pct = (x) => `${Math.round(x * 100)}%`;

export default function EvalPanel({ cases }) {
  const [report, setReport] = useState(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState("");

  const run = async () => {
    setRunning(true);
    setError("");
    try {
      setReport(await api.runEval());
    } catch (e) {
      setError(e.message);
    } finally {
      setRunning(false);
    }
  };

  return (
    <section>
      <h2>Evaluation</h2>
      <p className="empty">
        {running ? "Running… this makes real model calls and takes a minute or two."
          : report ? `${report.model}, prompt ${report.promptVersion}, ${report.cases} cases`
          : `Runs ${cases ?? 16} test conversations against the real model and scores them.`}
      </p>
      {error && <p className="error-text">{error}</p>}
      {report && (
        <>
          <div className="eval-grid">
            <Metric value={pct(report.taskSuccessRate)} label="Task success" />
            <Metric value={pct(report.toolSelectionAccuracy)} label="Right tools" />
            <Metric value={pct(report.constraintSatisfaction)} label="Filters respected" />
            <Metric value={pct(report.guardrailPassRate)} label="No unconfirmed orders" />
            <Metric value={`${report.avgGroundedness ?? "–"}/5`} label="Groundedness (judge)" />
            <Metric value={`${(report.p95LatencyMs / 1000).toFixed(1)} s`} label="p95 latency" />
          </div>
          <ul className="cases">
            {report.results.map((c) => (
              <li key={c.id}>
                <span className={c.passed ? "pass" : "fail"}>{c.passed ? "Pass" : "Fail"}</span> {c.id}
                {c.failures.length > 0 && <span className="why">{c.failures.join("; ")}</span>}
              </li>
            ))}
          </ul>
        </>
      )}
      <button className="btn" onClick={run} disabled={running}>{running ? "Running…" : "Run evaluation"}</button>
    </section>
  );
}

function Metric({ value, label }) {
  return <div className="metric"><b>{value}</b><small>{label}</small></div>;
}
