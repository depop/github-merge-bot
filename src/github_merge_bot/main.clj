(ns github-merge-bot.main
    (:require [github-merge-bot.core :refer :all])
    (:import (java.util Timer TimerTask)))

(require '[clojure.tools.trace :as trace])

(let [trace-enabled (System/getenv "GITHUB_MERGE_BOT_TRACE_ENABLED")]
  (if (= trace-enabled "true")
    (trace/trace-ns 'github-merge-bot.core)))

(defn -main
  [& args]
  (let [timer-task (proxy [TimerTask] []
                          (run []
                               (run-in-env nil)))]
    (.schedule (Timer.) timer-task 0 30000)))
