(ns github-merge-bot.core
  (:require [tentacles.pulls :as pulls]
            [tentacles.core :as tentacles]
            [clj-jgit.porcelain :as git])
  (:import (java.util UUID Timer TimerTask Date)
           (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.transport UsernamePasswordCredentialsProvider)
           (org.eclipse.jgit.revwalk RevWalk)
           (org.eclipse.jgit.revwalk.filter RevFilter)
           (org.eclipse.jgit.lib ObjectId))
  (:gen-class))

(defn github-ref-status [owner repo ref & [options]]
  (tentacles/api-call :get "repos/%s/%s/commits/%s/status" [owner repo ref] options))

(defn has-label [pull-request]
  (contains? (set (map :name (:labels pull-request)))
             "LGTM"))

(defn mergeable? [owner repo pull-id]
  (:mergeable (pulls/specific-pull owner repo pull-id)))

(defn pull-request-to-update
  "Pull request to update with its base branch."
  [owner repo pull-requests]
  ; TODO: Is `last` correct here?
  (last (filter #(and (has-label %)
                      (mergeable? owner repo (:number %))) (sort-by :created-at pull-requests))))

(defn merge-candidate [owner repo pull-requests]
  (last (filter #(and (has-label %)
                      (contains? #{"pending" "success"} (:state (github-ref-status owner repo (:ref (:head %))))))
                (sort-by :created-at pull-requests))))

(defn head-up-to-date-with-base? [owner repo pull-request]
  ; TODO: Avoid git clone duplication with update-pull.
  (let [repo (:repo (git/git-clone-full (str "https://github.com/" owner "/" repo ".git")
                                        (str "./tmp/" (UUID/randomUUID) owner "/" repo)))
        head-branch (:ref (:head pull-request))]
    (println "Cloned repo to" (.getPath (.getDirectory (.getRepository repo))))
    (git/git-fetch repo "origin")
    (git/git-checkout repo head-branch true false (str "origin/" head-branch))
    (let [rev-walk (RevWalk. (.getRepository repo))
          master (.parseCommit rev-walk (.getObjectId (.exactRef (.getRepository repo) "refs/heads/master")))
          _ (.reset rev-walk)
          base (.parseCommit rev-walk (ObjectId/fromString (:sha (:head pull-request))))
          _ (.reset rev-walk)
          merge-base (-> (doto rev-walk (.setRevFilter (RevFilter/MERGE_BASE))
                                        (.markStart [master base]))
                         (.next))]
      (let [merge-base-sha (.getName merge-base)
            master-sha (.getName master)]
        (println "merge base sha:" merge-base-sha)
        (println "master sha:" master-sha))
      (= (.getName merge-base)
         (.getName master)))))

(defn update-pull [owner repo pull-request credentials]
  ;; TODO: Clone every time?
  (println "Updating pull request" (:number pull-request) "by rebasing its head branch on master...")
  (let [repo (:repo (git/git-clone-full (str "https://github.com/" owner "/" repo ".git")
                                        (str "./tmp/" (UUID/randomUUID) owner "/" repo)))
        head-branch (:ref (:head pull-request))]
    (println "Cloned repo to" (.getPath (.getDirectory (.getRepository repo))))
    (git/git-fetch repo "origin")
    (git/git-checkout repo head-branch true false (str "origin/" head-branch))
    ; clj-jgit.porcelain/git-rebase hasn't been implemented yet so using JGit here directly instead.
    (-> repo .rebase (.setUpstream "origin/master") .call)
    ; clj-jgit.porcelain/with-credentials didn't seem to work so using JGit here directly instead.
    (-> repo
        (.push)
        (.setRemote "origin")
        (.setForce true)
        (.setCredentialsProvider (UsernamePasswordCredentialsProvider. (:username credentials) (:password credentials)))
        (.call))))

(defn merging-permitted? [owner repo pull-request]
  (= "success" (:state (github-ref-status owner repo (:ref (:head pull-request))))))

(defn merge-pull-request [owner repo pull-request credentials]
  (println (str "Merging pull request #" (:number pull-request) "..."))
  (println (pulls/merge owner repo (:number pull-request) {:auth (str (:username credentials) ":" (:password credentials))})))

(defn merge-pull-requests []
  (println "Checking pull requests...")
  (let [owner (System/getenv "GITHUB_MERGE_BOT_OWNER")
        repo (System/getenv "GITHUB_MERGE_BOT_REPO")
        credentials {:username (System/getenv "GITHUB_MERGE_BOT_USERNAME")
                     :password (System/getenv "GITHUB_MERGE_BOT_PASSWORD")}]
    (if-let [pr (merge-candidate "sdduursma" "github-merge-bot-test" (pulls/pulls owner repo))]
      (if (head-up-to-date-with-base? owner repo pr)
        (if (merging-permitted? owner repo pr)
          (merge-pull-request "sdduursma" "github-merge-bot-test" pr credentials)
          (println (str "Not permitted to merge pull request #" (:number pr) " yet.")))
        (update-pull "sdduursma" "github-merge-bot-test" pr credentials))
      (println "No pull requests found to merge or update."))
    #_(if-let [pull-request (pull-request-to-update owner repo (pulls/pulls owner repo))]

      (println "No pull requests to update."))))

(defn -main
  [& args]
  (let [timer-task (proxy [TimerTask] []
                     (run []
                       (merge-pull-requests)))]
    (.scheduleAtFixedRate (Timer.) timer-task 0 30000)))
