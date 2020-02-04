# github-merge-bot

Automates the process of merging pull requests and keeping them up-to-date.

## Rationale

Many development teams have adopted a [pull request workflow](https://guides.github.com/introduction/flow/). They use continuous integration systems to run tests before a pull request (PR) is merged to ensure that master is always "green". However, simply running the tests when a PR is created doesn't necessarily ensure that all tests will pass after that PR is merged into master. Another PR that contains breaking changes might be merged before the first PR is merged. Let's see an example:

Say we have two PRs: _A_ and _B_. PR _B_ contains changes that are incompatible with PR _A_: it removes a function that is used in PR _A_.

1. PR _A_ is opened, the tests are automatically run, they pass
2. PR _B_ is opened, the tests are automatically run, they pass
3. PR _A_ is merged
4. PR _B_ has passed its tests so it's also merged. master now has a bug.

To solve this, some teams opt to enforce that all PRs are up-to-date with their base branch before merging (see [this GitHub article](https://help.github.com/articles/types-of-required-status-checks/)). However, this creates a new problem: every PR needs to be manually updated and then merged. This can cause a real strain on the team's productivity, especially on repos that receive many contributions.

Projects like [Bors-NG](https://github.com/bors-ng/bors-ng) aim to solve this by providing a fronted on top of the CI system. With Bors-NG there is no need to update PRs. Instead, it will automatically rebuild all open pull requests against the new HEAD of the base branch when a PR is merged. It does this by running the tests against a temporary merge of the base and head branches. The advantage of this approach is that it does not affect the branches that developers are working on. However, Bors-NG can't guarantee that all PRs have been built against the latest version of master before merging. Once the status checks for a PR have passed, GitHub will allow it to be merged. So Bors-NG must quickly set the status checks for a PR to pending. If Bors-NG fails, this can be a problem. All contributors have to know not to merge PRs through GitHub but to use the Bors-NG interface instead.

Another approach people have taken is to automate the process of updating PRs. This works by running an application that queries GitHub to find PRs to update. When it updates the PR and pushes new commits the CI automatically rebuilds that PR. This is the approach we decided we wanted to go with. We had the following additional requirements:

* Limit the number of builds on the CI
* Easy to deploy
  * Preferably it doesn't require a database

Before creating github-merge-bot, we considered the these projects:

* [Bulldozer](https://github.com/palantir/bulldozer)
  * \+ Project seems well supported
  * \- Keeps all PRs that are labelled with "UPDATE ME" up-to-date, potentially resulting in many CI builds
  * \- Requires a database

* [github-rebase-bot](https://github.com/nicolai86/github-rebase-bot)
  * \+ Was fairly easy to deploy
  * \- Keeps all PRs that are labelled with "LGTM" up-to-date, potentially resulting in many CI builds
  * \- We tried it and found it to not be very reliable

Seeing as these projects did not fulfil our requirements, we decided to create github-merge-bot.

## Backlog

- [ ] Support updating PRs by merging instead of rebasing

## Installation and deployment

github-merge-bot currently uses username and password or personal access token to authenticate with GitHub. A good way to integrate with GitHub is to create a "bot" user that github-merge-bot authenticates as. Then [create a personal access token](https://help.github.com/articles/creating-a-personal-access-token-for-the-command-line/) and specify that as the password for github-merge-bot.

### Settings

All github-merge-bot settings are configurable from the following environmental variables:

Setting | Default | Description
--- | --- | ---
GITHUB_MERGE_BOT_OWNER | | The github organisation of the github repository github-merge-bot will integrate with
GITHUB_MERGE_BOT_REPO | | The github repository github-merge-bot will integrate with
GITHUB_MERGE_BOT_USERNAME | | The github user username github-merge-bot will integrate using
GITHUB_MERGE_BOT_PASSWORD | | The github user password github-merge-bot will integrate using
GITHUB_MERGE_BOT_TRUSTED_TEAMS_PERMISSION_SCOPE | nil | The github teams permission scope (ex. admin, write) whose members approvals will be considered when re-approving pull requests after a rebase operation. By default, any review approvals will be trusted.

### Docker

github-merge-bot can be deployed easily as a Docker container.

To build and run the container:

```bash
docker build -t sdduursma/github-merge-bot:0.8.0 .
docker run -e GITHUB_MERGE_BOT_OWNER=<repo owner> -e GITHUB_MERGE_BOT_REPO=<repo name> -e GITHUB_MERGE_BOT_USERNAME=<username> -e GITHUB_MERGE_BOT_PASSWORD=<password or token> sdduursma/github-merge-bot:0.8.0
```

## Development

github-merge-bot is written in [Clojure](https://clojure.org/). To run it locally you will need to have the following:

* Java 8 – I recommend using Java 8, I've not tried using later versions. [jEnv](http://www.jenv.be) is a useful tool to manage multiple versions of Java on your system.
* [Clojure](https://clojure.org/guides/getting_started)
* [Leiningen](https://leiningen.org/) – The de facto build tool for Clojure

### Running locally

To run the project:

```bash
GITHUB_MERGE_BOT_OWNER=<repo owner> GITHUB_MERGE_BOT_REPO=<repo name> GITHUB_MERGE_BOT_USERNAME=<username> GITHUB_MERGE_BOT_PASSWORD=<password or token> lein run
```

To run the test suite:

```bash
lein test
```

### Why Clojure?

> Everything should be made as simple as possible, but not simpler. – Albert Einstein

Clojure is a dynamic, functional, general purpose programming language that focuses on simplicity and data orientation. It runs on the JVM, so a huge number of Java libraries are available for use.

If you're unfamiliar with Clojure, [this page](https://clojure.org/guides/learn/syntax) is a good place to start learning.

## Contributing

Contributions are welcome.
