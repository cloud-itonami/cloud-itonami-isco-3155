# cloud-itonami-isco-3155

Open Occupation Blueprint for **ISCO-08 3155**: Air Traffic Safety Electronics Technicians.

This repository designs a forkable OSS blueprint for ATC electronics maintenance **administrative support** as a governor-gated actor: supporting maintenance log documentation, equipment inspection report drafting, equipment anomaly flagging, and maintenance window scheduling under an independent **Electronics Maintenance Governor** that ensures no proposal performs, authorizes, or certifies any live equipment repair, calibration, or return-to-service validation on safety-critical ATC radar/comms/navigation systems.

## CRITICAL: Scope Exclusion — What This Actor Does NOT Do

**This is an ADMINISTRATIVE SUPPORT actor only, for maintenance back-office / pre-shift / post-shift documentation workflows.**

This actor NEVER:
- Performs or authorizes any live equipment repair, replacement, or component substitution
- Performs or authorizes any calibration, alignment, or system parameter adjustment on ATC equipment
- Certifies, authorizes, or validates the return-to-service status of any safety-critical ATC system
- Performs or supervises any electrical/mechanical work on radar, radio communications, or navigation systems
- Makes any safety-critical technical decision about equipment operational readiness
- Bypasses or overrides any maintenance documentation requirement or safety sign-off protocol
- Authorizes deviation from established preventative maintenance schedules
- Issues or authorizes any equipment work order that commits maintenance to hardware
- Validates or oversees any ground/flight testing of repaired or modified ATC equipment
- Performs any time-critical action required during active equipment troubleshooting or repair

**Any proposal attempting any of the above is a hard, permanent block** — even with `:propose` effect. Scope violations are never escalable to human sign-off; they are structurally excluded from this actor's vocabulary entirely.

## Electronics Maintenance Administrative Support Premise

All cloud-itonami verticals are designed on the premise that decision-making is gated by an independent governor. Here, an **Electronics Maintenance Governor** gates all proposals under strict safety rules: the advisor (mock or LLM) can only propose ground/administrative/documentation actions (maintenance log entries, inspection report drafting, equipment anomaly flags, maintenance scheduling). The governor rejects any proposal that strays into live equipment work, calibration authority, return-to-service certification, or any safety-critical technical decision. The governor never dispatches actions itself; equipment anomalies always escalate to human review.

## Core Contract

```text
maintenance support request (administrative/ground only)
        |
        v
Electronics Maintenance Advisor -> Electronics Maintenance Governor -> support action or human escalation
        |
        v
committed operation record + audit ledger
```

No automated advice can perform equipment repair, authorize maintenance work, certify equipment readiness, suppress an operating record, or touch live ATC systems/real-time equipment status.

## Capability Layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3155`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference Implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors section): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                          +-> :request-approval   (:escalate? true, interrupt-before)
                                          +-> :hold               (:hard? true)
```

- `src/electronics_maintenance/store.kotoba` — `Store` protocol + `MemStore`:
  registered technicians and facilities, committed operations, an append-only audit ledger.
- `src/electronics_maintenance/advisor.kotoba` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes an electronics maintenance support action from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a committed record, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/electronics_maintenance/governor.kotoba` — `ElectronicsMaintenanceGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered technician/facility, a proposal whose `:effect` isn't `:propose`,
  any operation touching live equipment work / calibration / certification) 
  always route to `:hold`. Escalation invariants (`:flag-equipment-anomaly`,
  or low advisor confidence) always route to `:request-approval` — an
  `interrupt-before` node that the graph checkpoints and only resumes on
  explicit human approval (`actor/approve!`).
- `src/electronics_maintenance/actor.kotoba` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

```bash
kbb -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
