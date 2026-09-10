(ns electronics-maintenance.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [electronics-maintenance.store :as store]
            [electronics-maintenance.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-facility! st {:facility-id "PHL" :name "Philadelphia International" :type "tower"})
    (store/register-technician! st {:technician-id "tech-1" :license "Avionics Certification" :facility-id "PHL"})
    st))

(deftest ok-on-log-maintenance-record
  (let [st (fresh-store)
        proposal {:op :log-maintenance-record :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest ok-on-draft-inspection-report
  (let [st (fresh-store)
        proposal {:op :draft-inspection-report :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest ok-on-schedule-maintenance-window
  (let [st (fresh-store)
        proposal {:op :schedule-maintenance-window :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-technician
  (let [st (fresh-store)
        proposal {:op :log-maintenance-record :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:technician-id "tech-9999" :facility-id "PHL"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-technician (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-facility
  (let [st (fresh-store)
        proposal {:op :log-maintenance-record :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:technician-id "tech-1" :facility-id "ZZZ"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-facility (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :log-maintenance-record :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-live-repair-attempt
  (let [st (fresh-store)
        proposal {:op :live-equipment-repair :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :forbidden-scope (:rule %)) (:violations v)))))

(deftest hard-on-calibration-work-attempt
  (let [st (fresh-store)
        proposal {:op :calibration-work :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :forbidden-scope (:rule %)) (:violations v)))))

(deftest hard-on-return-to-service-certification-attempt
  (let [st (fresh-store)
        proposal {:op :return-to-service-certification :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :forbidden-scope (:rule %)) (:violations v)))))

(deftest hard-on-flight-testing-attempt
  (let [st (fresh-store)
        proposal {:op :flight-testing :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :forbidden-scope (:rule %)) (:violations v)))))

(deftest escalates-on-equipment-anomaly
  (let [st (fresh-store)
        proposal {:op :flag-equipment-anomaly :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :log-maintenance-record :effect :propose :confidence 0.2 :stake :low}
        v (governor/check {:technician-id "tech-1" :facility-id "PHL"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-operations-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-operation! st {:technician-id "tech-1" :facility-id "PHL" :op :log-maintenance-record})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/operations-of st "PHL"))))
    (is (= 1 (count (store/ledger st))))))
