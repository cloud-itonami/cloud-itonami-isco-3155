(ns electronics-maintenance.advisor
  "ElectronicsMaintenanceAdvisor — proposes an electronics maintenance support action
  (log maintenance record, draft inspection report, flag equipment anomaly,
  schedule maintenance window) for a registered technician/facility. The advisor
  is swappable: `mock-advisor` (deterministic, default in dev/tests/CI) or
  `llm-advisor` (wraps a real `langchain.model/ChatModel`). Either way the
  advisor ONLY produces a PROPOSAL — it never writes to the store and has no
  notion of technician/facility provenance or operational authority; `electronics-maintenance.governor`
  is the independent system that decides whether the proposal may proceed, per
  the itonami actor pattern.

  A proposal is a map:
    {:op :log-maintenance-record|:draft-inspection-report|:flag-equipment-anomaly|:schedule-maintenance-window
     :effect :propose        ; the advisor NEVER emits a raw store write
     :stake :low|:medium|:high
     :confidence 0.0-1.0
     :rationale str}

  LLM parse failures always yield `:confidence 0.0` (never fabricate
  confidence), which forces the governor to escalate/hold."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer
  "Deterministic mock inference: reads the request's declared op/stake
  straight through (a stand-in for what an LLM would extract from free
  text), with a stake-derived confidence. Higher stake correlates with
  lower confidence (advisor uncertainty on high-stakes decisions)."
  [_store {:keys [op stake] :as request}]
  {:op op
   :effect :propose
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.4 :medium 0.75 :low 0.95)
   :rationale (str "proposed " (name op) " for facility " (:facility-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are an ATC electronics maintenance administrative support advisor. Given a maintenance support
   request (maintenance log entry, inspection report draft, equipment anomaly flag,
   maintenance window scheduling), propose an :op, an honest :confidence (0.0-1.0),
   and a :stake (:low/:medium/:high). Never fabricate confidence you don't have.
   Remember: this is ADMINISTRATIVE SUPPORT ONLY — you have no authority over
   live equipment repair, calibration, return-to-service certification, or any safety-critical
   technical decisions. Your role is ground-based documentation and maintenance scheduling support.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  "Wraps a `langchain.model/ChatModel`. `gen-opts` is passed through to
  `model/-generate`. Kept decoupled from any concrete model so this ns
  has no hard dependency beyond `langchain.model`'s protocol."
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "support request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
