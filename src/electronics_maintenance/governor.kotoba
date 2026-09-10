(ns electronics-maintenance.governor
  "ElectronicsMaintenanceGovernor — the independent safety/traceability layer for
  the ISCO-08 3155 ATC electronics maintenance administrative support actor. Wired as its
  own `:govern` node in `electronics-maintenance.actor`'s StateGraph, downstream of
  `:advise`.

  CRITICAL SCOPE EXCLUSION — What This Actor Does NOT Do
  =========================================================
  This is an ADMINISTRATIVE SUPPORT actor ONLY. It NEVER:
    • Performs or authorizes any live equipment repair, replacement, or component substitution
    • Performs or authorizes any calibration, alignment, or system parameter adjustment
    • Certifies, authorizes, or validates return-to-service status of safety-critical ATC systems
    • Performs or supervises any electrical/mechanical work on radar, comms, or navigation systems
    • Makes any safety-critical technical decision about equipment operational readiness
    • Bypasses or overrides any maintenance documentation requirement or safety sign-off protocol
    • Authorizes deviation from established preventative maintenance schedules
    • Issues or authorizes any equipment work order that commits maintenance to hardware
    • Validates or oversees any ground/flight testing of repaired or modified ATC equipment
    • Performs any time-critical action during active equipment troubleshooting or repair

  It ONLY supports ground/administrative / pre-shift / post-shift back-office workflows.
  All proposals that touch any of the above are HARD blocks (`:hold`), never
  overridable.

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. technician & facility provenance — request's technician-id and facility-id must
       be registered.
    2. no-actuation — proposal :effect must be :propose.
    3. no-live-equipment-work — any proposal op touching live repair, calibration, certification,
       or any safety-critical technical decision is permanently blocked (even :propose effect).
       HARD block, not escalation. Complete exclusion list in :disallowed-ops.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    4. :op :flag-equipment-anomaly (always escalates — equipment anomalies
       always surface to human review; never auto-proceed)
    5. low confidence (< `confidence-floor`)"
  (:require [electronics-maintenance.store :as store]))

(def confidence-floor 0.6)

(def ^:private disallowed-ops
  "Operations permanently blocked (no proposal effect will permit these).
  Any proposal with these ops is a :hard? violation."
  #{:live-equipment-repair
    :equipment-replacement
    :component-substitution
    :calibration-work
    :system-parameter-adjustment
    :return-to-service-certification
    :equipment-validation
    :electrical-mechanical-work
    :ground-testing
    :flight-testing
    :safety-determination
    :operational-readiness-decision
    :work-order-issuance
    :maintenance-schedule-override})

(def ^:private escalating-ops
  "Operations that always require human sign-off (escalate via :request-approval)."
  #{:flag-equipment-anomaly})

(defn- hard-violations [{:keys [proposal]} technician-record facility-record]
  (cond-> []
    (nil? technician-record)
    (conj {:rule :no-technician :detail "未登録 technician"})

    (nil? facility-record)
    (conj {:rule :no-facility :detail "未登録 facility"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (contains? disallowed-ops (:op proposal))
    (conj {:rule :forbidden-scope
           :detail "This operation touches live equipment repair, calibration, return-to-service certification, or any safety-critical technical decision. Permanently excluded from this actor's scope."})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a `store`
  implementing `store.Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [technician-record (store/technician store (:technician-id request))
        facility-record (store/facility store (:facility-id request))
        hard (hard-violations {:proposal proposal} technician-record facility-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating?))}))
