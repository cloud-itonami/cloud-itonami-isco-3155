(ns electronics-maintenance.store
  "SSoT for the ISCO-08 3155 ATC electronics maintenance administrative support actor.
  Store is a protocol injected into the `electronics-maintenance.actor` StateGraph
  — `MemStore` is the default, deterministic, zero-dep backend; a
  Datomic/kotoba-server-backed implementation can be swapped in without
  touching the actor or governor (itonami actor pattern, per
  ADR-2607011000 / CLAUDE.md Actors section).

  Domain:

    technician  — a registered electronics maintenance technician (:technician-id, :license, :facility-id)
    facility    — a registered ATC facility (:facility-id, :name, :type)
    operation   — a committed ground/administrative operation (maintenance log entry,
                 inspection report draft, equipment anomaly flag, maintenance scheduling)
                 — written ONLY via commit-operation!, never mutated in place
    ledger      — an append-only audit trail of every proposal/verdict/
                 disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (technician [s technician-id])
  (facility [s facility-id])
  (operations-of [s facility-id])
  (ledger [s])
  (register-technician! [s technician])
  (register-facility! [s facility])
  (commit-operation! [s operation])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (technician [_ technician-id] (get-in @a [:technicians technician-id]))
  (facility [_ facility-id] (get-in @a [:facilities facility-id]))
  (operations-of [_ facility-id]
    (filter #(= facility-id (:facility-id %)) (:operations @a)))
  (ledger [_] (:ledger @a))
  (register-technician! [s technician]
    (swap! a assoc-in [:technicians (:technician-id technician)] technician) s)
  (register-facility! [s facility]
    (swap! a assoc-in [:facilities (:facility-id facility)] facility) s)
  (commit-operation! [s operation]
    (swap! a update :operations (fnil conj []) operation) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:technicians {} :facilities {} :operations [] :ledger []} seed)))))
