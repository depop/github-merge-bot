(defproject github-merge-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [irresponsible/tentacles "0.6.2"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.clojure/tools.trace "0.7.10"]
                 [org.clojure/core.match "0.3.0"]
                 [clj-jgit "0.8.10"]]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :main ^:skip-aot github-merge-bot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
