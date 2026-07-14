(ns electronics-maintenance.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [electronics-maintenance.actor :as actor]
            [electronics-maintenance.store :as store]
            [electronics-maintenance.advisor :as advisor]))

(defn- fresh-graph []
  (let [st (store/mem-store)]
    (store/register-facility! st {:facility-id "PHL" :name "Philadelphia International" :type "tower"})
    (store/register-technician! st {:technician-id "tech-1" :license "Avionics Certification" :facility-id "PHL"})
    (actor/build-graph {:store st :advisor (advisor/mock-advisor)})))

(deftest run-request-ok-case
  (let [graph (fresh-graph)
        result (actor/run-request! graph
                                   {:technician-id "tech-1" :facility-id "PHL" :op :log-maintenance-record :stake :low}
                                   {}
                                   "thread-1")]
    (is (= :done (:status result)))
    (is (some #(= :commit (:node %)) (get-in result [:state :audit])))))

(deftest run-request-escalates-on-low-confidence
  (let [graph (fresh-graph)
        result (actor/run-request! graph
                                   {:technician-id "tech-1" :facility-id "PHL" :op :log-maintenance-record :stake :high}
                                   {}
                                   "thread-2")]
    (is (= :interrupted (:status result)))
    (is (some #(= :request-approval (:node %)) (get-in result [:state :audit])))))

(deftest run-request-holds-on-forbidden-op
  (let [graph (fresh-graph)
        result (actor/run-request! graph
                                   {:technician-id "tech-1" :facility-id "PHL" :op :live-equipment-repair :stake :low}
                                   {}
                                   "thread-3")]
    (is (= :done (:status result)))
    (is (some #(= :hold (:node %)) (get-in result [:state :audit])))))

(deftest approve-resumes-and-commits
  (let [graph (fresh-graph)
        result-1 (actor/run-request! graph
                                     {:technician-id "tech-1" :facility-id "PHL" :op :log-maintenance-record :stake :high}
                                     {}
                                     "thread-4")
        _ (is (= :interrupted (:status result-1)))
        result-2 (actor/approve! graph "thread-4")]
    (is (= :done (:status result-2)))
    (is (some #(= :commit (:node %)) (get-in result-2 [:state :audit])))))
