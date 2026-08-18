# AGENTS.md

Shared instructions for coding agents. Project-specific information is kept in [README.md](README.md), read it before non-trivial changes.

## Jenkins

### Repository conventions

- Follow the existing source layout and build system. Do not introduce or
  replace a build framework for local convenience.
- Put stable user-facing behavior and examples in `README.md`.
- Keep public Pipeline contracts backward-compatible unless a change explicitly
  permits a breaking release. Preserve each helper's streamed-output,
  captured-stdout, or numeric-exit-status contract.

### Groovy style

Match the touched file. Do not normalize unrelated indentation or formatting.
Opening braces stay on declaration or control-flow lines. Prefer single-quoted
literals and double-quoted interpolation. Omit semicolons in new code. Use
camelCase for locals and methods, PascalCase for classes, and uppercase snake
case for public Pipeline configuration keys and environment variables.

### Jenkins CPS safety

- Do not retain non-serializable Jenkins, Hudson, iterator, matcher, stream, or
  platform objects across Pipeline step calls.
- Treat `node`, `stage`, `dir`, `sh`, `powershell`, `withEnv`,
  `withCredentials`, `stash`, and similar calls as suspension points.
- Do not store build-local state in ordinary static fields.
- Make scoped behavior lexical, nestable, concurrency-safe, and exception-safe.

### Errors, interruptions, and results

Preserve `FlowInterruptedException`: set build result when appropriate, then
rethrow it before broad `Throwable` catches. Do not convert aborts or timeouts
to ordinary failures.

### Shells, paths, and external processes

Support Windows PowerShell and Linux POSIX shells where claimed. Treat quoting
as multiple layers: Groovy, Jenkins, host shell, optional transport, target
shell. Use Jenkins `pwd()`, `WORKSPACE`, and `WORKSPACE_TMP`; do not compute
agent paths with controller-local `File` APIs. Never assume active Docker
context.

### Credentials and sensitive data

Bind secrets with Jenkins credential steps and minimize scope. Never print
tokens, passwords, secrets, credential files, or full environments. Preserve
masking and never archive, stash, or publish credential files.

### Jenkins environment and validation

Prefer configured Jenkins integration over scraping HTML. Read-only build
inspection is allowed when relevant. Triggering, replaying, stopping, or
mutating builds requires explicit authorization. Local or standalone execution
cannot faithfully reproduce every Jenkins CPS, durability, agent, and plugin
integration behavior. Whenever integration tests are run, watch the complete
console log; scheduling a build alone does not establish success.

The durable test servers are `Mörkö`, a Linux server that also hosts the
Jenkins container; `Garl`, a Linux server with a GPU; and `Dende`, a Windows
server with a GPU. Other Jenkins agents are temporary build helpers: do not
mention them by name in `.jenkins/Jenkinsfile.groovy`. Target helpers only by
label, and require an available helper matching a tested label to pass the same
integration tests as the named servers.

## Jenkins Shared Library Development

This library is loading globally and implicitly in all jobs.

### Shared Library conventions

- Treat `vars/*.groovy` files as global Pipeline steps. The filename defines
  the public step name, and overloaded `call` methods define invocation forms.
- Keep public APIs small. Prefer a `Map` for programmatic Pipeline configuration
  and a delegated `Closure` where a DSL improves readability.
- Keep global-step scripts stateless. Put reusable classes in `src/<package>/`
  unless they require Pipeline script binding.
- Classes retained across Pipeline step calls must implement `Serializable` and
  contain only serializable state. Pass Pipeline steps and global values such as
  `env` explicitly; constructors must not invoke Pipeline steps.
- Use `@NonCPS` only for pure computation that invokes no Pipeline steps.
- IDE unresolved-symbol warnings alone do not prove that a dynamic global step
  is invalid.

### Validation

- Validate changed call chains and perform small, safe local runtime probes
  before live integration testing.
- Add local coverage for every changed public invocation form and its output,
  return-value, error, and interruption contracts.
- Keep this Shared Library's integration tests in
  `.jenkins/Jenkinsfile.groovy`. Add focused coverage for changed runtime
  behavior and retain assertions for previously discovered regressions. Do not
  invoke deployment or notification side effects unless the test explicitly
  requires and authorizes them.
- When relevant, test serialization across suspension points, controller
  restart and resume, nested or parallel invocations, and every claimed agent
  platform. Local mocks do not establish these durability guarantees.

### Release

When release operations are authorized, the complete release cycle is:

1. Implement the features and update the integration tests in `.jenkins/Jenkinsfile.groovy` as needed.
2. Run the local test suite.
3. Commit and push the changes.
4. Update the Jenkins Global Trusted Pipeline Libraries settings to use the branch pushed in step 3, then clear the Shared Library cache.
5. Run this library's job in `https://ci.slothsoft.net/job/jenkins/` and watch its complete console log.
6. If the candidate integration test fails, fix the issue, then repeat from step 2.
7. After the candidate passes, amend CHANGELOG.md, then commit, push and tag the final version.
8. Update the Jenkins Global Trusted Pipeline Libraries settings to use the pushed version tag.
9. Run this library's job in `https://ci.slothsoft.net/job/jenkins/` and watch its complete console log.
10. If any post-push check or final integration test fails, fix the issue and
    repeat the full cycle from step 1 with a new patch version.

## General

### Meta commands

These short messages have special handling when they appear alone in a user
message:

- `ping`: Reply with `pong`.
- `.`: Reply with `.`.
- `?`: Continue the previous response or task after an interruption.
- `ticket <URI>`: Read the linked ticket and all comments through the available
  integration. Inspect the project, reproduce the current behavior, and run
  relevant checks as needed. Then explain the request, project context,
  reproducibility, risks, and a proposed implementation plan. Do not edit
  files, change remote state, commit, or push until the user approves the
  approach.
- `can you <x>?` is a question about your knowledge, capabilities or permissions. It is not an instruction to perform `x`.

### Compatibility

Follow semantic versioning. Preserve backward compatibility for public APIs
unless the task explicitly permits a breaking change.

### Project conventions

`.editorconfig` is authoritative. Never edit `.editorconfig` unless expressly instructed by the user.

### Git

Git mutations are forbidden by default. Agents may use read-only inspection
commands such as `git status`, `git log`, `git diff`, `git show`, `git blame`,
and `git branch --list` without additional permission.

An agent may perform Git mutations only after the user explicitly opts in.
Permission is limited to the operations and task the user authorized; do not
treat prior authorization as standing permission for later mutations.

When Git mutations are authorized:

- The user is responsible for choosing the branch. Verify the current branch
  and working-tree status before editing and again before creating commits.
- Treat all unknown local changes as user work. Do not overwrite, stage,
  commit, restore, or otherwise alter them.
- Keep commits small and cohesive.
- Format agent-authored commits according to Conventional Commits 1.0.0:
  `<type>[optional scope]: <description>`.
- When working from a ticket, include the ticket key and URL in the commit
  footer.
- Before committing, read the configured Git author name and email. Keep the
  configured email, append the agent name once, in brackets to the configured author name (e.g. `Daniel Schulz (Codex)`),
  and pass that identity explicitly with `git commit --author`. Do not modify
  repository or global Git configuration.
- Do not force-push, amend, rebase, reset, or discard changes unless the user
  explicitly requests that specific operation.
