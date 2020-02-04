(ns github-merge-bot.core-test
  (:require [clojure.test :refer :all]
            [github-merge-bot.core :refer :all]
            [clj-jgit.porcelain :as git])
  (:import (java.util UUID)
           (org.eclipse.jgit.api Git)))

; NOTE enable for tracing purposes
;(require '[clojure.tools.trace :as trace])
;(trace/trace-ns 'github-merge-bot.core)

(defn pull-request-for-repo-branch [^Git origin-repo branch-name]
  {:number 1
   :head {:sha (.getName (.getObjectId (.findRef (.getRepository origin-repo) branch-name)))
          :ref branch-name}})

(deftest test-approved?
  (with-redefs [github-reviews (constantly [])
                github-repo-teams (constantly [{:name "team1" :id 1 :permission "admin"}])
                github-team-members (constantly [{:login "user1"}])]
    (is (not (approved? "foo" "bar" {:id 42} nil))))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:login "user1"}}])
                github-repo-teams (constantly [{:name "team1" :id 1 :permission "admin"}])
                github-team-members (constantly [{:login "user1"}])]
    (is (approved? "foo" "bar" {:id 42}  nil)))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:login "user1"}}])
                github-repo-teams (constantly [{:name "team1" :id 1 :permission "admin"}])
                github-team-members (constantly [{:login "user1"}])]
    (is (approved? "foo" "bar" {:id 42}  "admin")))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:login "user2"}}])
                github-repo-teams (constantly [{:name "team1" :id 1 :permission "write"}])
                github-team-members (constantly [{:login "user1"}])]
    (is (not (approved? "foo" "bar" {:id 42}  "write"))))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:login "user1"}}])
                github-repo-teams (constantly [{:name "team1" :id 1 :permission "write"}])
                github-team-members (constantly [{:login "user1"}])]
    (is (not (approved? "foo" "bar" {:id 42}  "admin"))))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:login "user2"}}])
                github-repo-teams (constantly nil)
                github-team-members (constantly nil)]
    (is (approved? "foo" "bar" {:id 42} nil)))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:login "user1"}}
                                            {:state "COMMENTED" :user {:login "user1"}}])
                github-repo-teams (constantly [{:name "team1" :id 1}])
                github-team-members (constantly [{:login "user1"}])]
    (is (approved? "foo" "bar" {:id 42} nil)))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:login "user1"}}
                                            {:state "COMMENTED" :user {:login "user2"}}])
                github-repo-teams (constantly [{:name "team1" :id 1}])
                github-team-members (constantly [{:login "user1"}])]
    (is (approved? "foo" "bar" {:id 42} nil)))

  (with-redefs [github-reviews (constantly [{:state "APPROVED" :user {:login "user1"}}
                                            {:state "CHANGES_REQUESTED" :user {:login "user1"}}])
                github-repo-teams (constantly [{:name "team1" :id 1}])
                github-team-members (constantly [{:login "user1"}])]
    (is (not (approved? "foo" "bar" {:id 42} nil))))

  (with-redefs [github-reviews (constantly [{:state "CHANGES_REQUESTED" :user {:login "user1"}}
                                            {:state "APPROVED" :user {:login "user1"}}])
                github-repo-teams (constantly [{:name "team1" :id 1}])
                github-team-members (constantly [{:login "user1"}])]
    (is (approved? "foo" "bar" {:id 42} nil)))

  (with-redefs [github-reviews (constantly [{:state "CHANGES_REQUESTED" :user {:login "user1"}}
                                            {:state "APPROVED" :user {:login "user2"}}])
                github-repo-teams (constantly [{:name "team1" :id 1 :permission "admin"}])
                github-team-members (constantly [{:login "user1"}])]
    (is (not (approved? "foo" "bar" {:id 42} "admin"))))

  (with-redefs [github-reviews (constantly [{:state "CHANGES_REQUESTED" :user {:login "user1"}}
                                            {:state "APPROVED" :user {:login "user2"}}
                                            {:state "APPROVED" :user {:login "user1"}}])
                github-repo-teams (constantly [{:name "team1" :id 1 :permission "admin"}])
                github-team-members (constantly [{:login "user1"}])]
    (is (approved? "foo" "bar" {:id 42} "admin")))

  (with-redefs [github-reviews (constantly [{:state "CHANGES_REQUESTED" :user {:login "user1"}}
                                            {:state "APPROVED" :user {:login "user2"}}
                                            {:state "APPROVED" :user {:login "user1"}}])
                github-repo-teams (constantly [{:name "team1" :id 1 :permission "write"}])
                github-team-members (constantly [{:login "user1"}, {:login "user2"}])]
    (is (not (approved? "foo" "bar" {:id 42} "admin")))))

(deftest test-update-pull-request
  (let [origin-repo-dir (str (System/getProperty "java.io.tmpdir") (UUID/randomUUID))
        origin-repo (git/git-init origin-repo-dir)
        rev-a (git/git-commit origin-repo "A")
        _ (git/git-commit origin-repo "B")
        _ (git/git-checkout origin-repo "feature-c" true false (.getName (.getId rev-a)))
        _ (git/git-commit origin-repo "C")
        _ (git/git-checkout origin-repo "master")
        repo (:repo (git/git-clone-full origin-repo-dir (str (System/getProperty "java.io.tmpdir") (UUID/randomUUID))))]
    (with-redefs [procure-repo (constantly repo)
                  github-reviews (constantly [])
                  github-repo-teams (constantly [{:name "team1" :id 1}])
                  github-team-members (constantly [{:login "user1"}])]
      (update-pull-request "foo" "bar" (pull-request-for-repo-branch origin-repo "feature-c") {:username "foo" :password "abc"} nil)
      (is (head-up-to-date-with-base? "foo" "bar" (pull-request-for-repo-branch origin-repo "feature-c"))))))

(deftest test-update-pull-request-with-conflict
  (let [origin-repo-dir (str (System/getProperty "java.io.tmpdir") (UUID/randomUUID))
        origin-repo (git/git-init origin-repo-dir)
        _ (println origin-repo-dir)
        _ (spit (str origin-repo-dir "/file.txt") "a")
        _ (git/git-add origin-repo "file.txt")
        rev-a (git/git-commit origin-repo "A")
        _ (spit (str origin-repo-dir "/file.txt") "b" :append true)
        _ (git/git-add-and-commit origin-repo "B")
        _ (git/git-checkout origin-repo "feature-c" true false (.getName (.getId rev-a)))
        _ (spit (str origin-repo-dir "/file.txt") "c" :append true)
        rev-c (git/git-add-and-commit origin-repo "C")
        _ (git/git-checkout origin-repo "master")
        repo (:repo (git/git-clone-full origin-repo-dir (str (System/getProperty "java.io.tmpdir") (UUID/randomUUID))))]
    (with-redefs [procure-repo (constantly repo)
                  github-reviews (constantly [])
                  github-repo-teams (constantly [{:name "team1" :id 1}])
                  github-team-members (constantly [{:login "user1"}])]
      (update-pull-request "foo" "bar" (pull-request-for-repo-branch origin-repo "feature-c") {:username "foo" :password "abc"} nil)
      (is (= (-> origin-repo .getRepository (.findRef "feature-c") .getObjectId .getName)
             (.getName rev-c))
          "There was a conflict, so the remote feature-c branch should not have been modified")
      (is (nil? (update-pull-request "foo" "bar" (pull-request-for-repo-branch origin-repo "feature-c") {:username "foo" :password "abc"} nil))
          "After the conflict the repo should return to a clean state and calling update-pull-request again should work"))))
