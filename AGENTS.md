# AGENTS.md

Shared instructions for coding agents working on Slothsoft Groovy projects. Jenkins-specific rules below apply when the repository is a Jenkins Shared Library or otherwise executes through Jenkins Pipeline. Put repository purpose, architecture, prerequisites, public APIs, and runtime topology in `README.md`. Keep active ticket requirements in the issue tracker and working plan, not in `AGENTS.md` or `README.md`.

## Meta Commands

These short messages have special handling when they appear alone in a user message:

- `ping`: Reply with `pong`.
- `.`: Reply with `.`.
- `?`: Continue the previous response or task after an interruption.
- `ticket <URI>`: Read the linked issue and all comments through the appropriate integration, inspect the repository, and inspect relevant Jenkins builds when needed. Explain the request, repository context, current behavior, risks, and proposed implementation plan. Do not edit files, trigger or replay builds, change remote state, commit, or push until the user approves the approach.

## Repository Conventions

- Follow the repository's existing source layout and build system. Do not introduce Gradle, Maven, Jenkins Shared Library structure, or another framework merely for local convenience.
- In Jenkins Shared Libraries, treat `vars/*.groovy` files as global steps. The filename defines the public step name; overloaded `call` methods define its invocation forms.
- Keep public APIs small. For Pipeline configuration, prefer a `Map` form for programmatic callers and a delegated `Closure` form when a DSL improves readability.
- Keep reusable classes outside Jenkins global-step scripts when they do not need Pipeline script binding. In Jenkins, remember that static state is controller-JVM-wide and shared across builds.
- Centralize repeated shell, notification, node-selection, credential, and error-handling behavior in focused helpers.
- Put stable user-facing behavior and examples in `README.md`. Do not document unfinished ticket designs as current behavior.

## Groovy Style

Match the touched file. Existing Slothsoft Groovy has both tab-indented and four-space-indented files; do not reformat unrelated lines or normalize a whole file while making a functional change.

- Put opening braces on the declaration or control-flow line.
- Use blank lines to separate setup, validation, execution, and reporting phases.
- Use `def` for local values unless an explicit type materially clarifies a method contract or Jenkins interoperability.
- Type public parameters and helper return values where the surrounding file does so. Keep Jenkins objects dynamically typed when their runtime proxies make concrete types misleading.
- Prefer single-quoted strings for literals and double-quoted strings for interpolation. Preserve surrounding style when editing older code.
- Use Groovy command syntax for simple Pipeline calls when it reads naturally, such as `callShell "command"`. Use parentheses for named arguments, chained calls, ambiguous expressions, or when required for clarity.
- Omit semicolons in new code. Do not remove existing semicolons as unrelated cleanup.
- Keep maps and long named-argument calls vertically formatted. Follow neighboring key alignment and quote style.
- Use camelCase for local variables and methods, PascalCase for classes, and uppercase snake case for user-facing Pipeline configuration keys and environment variables.
- Write comments as short explanations of why behavior is necessary. Do not narrate obvious code.
- Preserve file encoding, line endings, and meaningful Unicode text.

## Jenkins CPS Safety

Shared Library code is CPS-transformed Groovy, not ordinary standalone Groovy.

- Do not retain non-serializable Jenkins, Hudson, iterator, matcher, stream, or platform objects across Pipeline step calls.
- Use `@NonCPS` only for pure computation that invokes no Pipeline steps.
- Treat every `node`, `stage`, `dir`, `sh`, `powershell`, `withEnv`, `withCredentials`, `stash`, and similar call as a possible suspension point.
- Do not store build-local or scope-local state in ordinary static fields. If static state is unavoidable, make concurrency, controller restart, node replacement, and lifecycle semantics explicit.
- Make scoped behavior lexical, nestable, concurrency-safe, and exception-safe. Restore prior behavior after success, failure, or interruption.
- Jenkins global steps resolve dynamically. IDE unresolved-symbol warnings alone do not prove an error.
- Avoid holding controller model objects longer than necessary. Perform controller access narrowly and keep agent-side work in Pipeline steps.

## Errors, Interruptions, and Results

- Preserve `FlowInterruptedException`: set the build result when appropriate, then rethrow it before any broad `Throwable` catch.
- Do not convert aborts or timeouts into ordinary failures.
- Catch broad exceptions only where the step intentionally degrades, reports, or supplies a fallback. Keep that behavior visible in logs or build result.
- Preserve each helper's output contract: streamed output, captured stdout, or numeric exit status.
- Check native process exit codes where the selected Jenkins shell step would otherwise hide them.
- Do not weaken failure handling merely to make a build green.

## Shells, Paths, and External Processes

- Support Windows PowerShell and Linux POSIX shells where the library claims cross-platform behavior.
- Treat quoting as a multi-layer problem: Groovy, Jenkins, host shell, optional transport such as Docker or SSH, then target shell.
- Use Jenkins `pwd()`, `WORKSPACE`, and `WORKSPACE_TMP` for agent paths. Do not compute agent filesystem paths with controller-local `File` APIs.
- When an external runtime sees workspace files, require identical absolute paths or perform an explicit path mapping.
- Preserve current working directory and environment when routing commands through another process or container.
- Keep external process execution centralized so logging, exit codes, encoding, credentials, and cancellation stay consistent.
- Never assume an active Docker context. When Docker access is required during agent automation, pass the intended context explicitly and begin with read-only inspection.

## Credentials and Sensitive Data

- Bind secrets with Jenkins credential steps and keep their scope as small as practical.
- Never print tokens, passwords, inbound-agent secrets, credential files, or complete environments.
- Do not inspect process command lines when they may contain secrets.
- Forward credentials by environment-variable name where possible; do not embed secret values in labels, command arguments, generated files, or exception messages.
- Keep credential files inside paths available only for the required scope. Do not archive, stash, or publish them.
- Preserve Jenkins masking by avoiding transformations that make secret values unrecognizable to the masker.

## Jenkins Validation

Slothsoft Jenkins runs at `https://ci.slothsoft.net/`. Prefer the configured Jenkins integration over scraping HTML.

Read-only inspection of controller health, jobs, builds, logs, SCM data, replay scripts, and test results is allowed when relevant. Triggering, rebuilding, replaying, stopping, or mutating a build requires explicit user authorization for the current task.

Before an authorized replay:

- Fetch the current main script and loaded Shared Library scripts from the exact source build.
- Preserve unrelated script content and build parameters.
- State the job, source build, node, and intended modification.
- Avoid concurrent replays unless explicitly requested.
- Follow the queue item into its build and inspect final result, relevant logs, reports, and tests. A queued or started build is not validation.

Standalone Groovy checks cannot faithfully reproduce Jenkins CPS or dynamic global steps. Validate proportionately:

1. Inspect changed call chains and syntax.
2. Run any repository-provided unit tests or static checks.
3. Use the smallest safe runtime probe for external tools.
4. With authorization, validate through a representative Jenkins Pipeline.

Never use production deployment, publication, notifications, or destructive cleanup as a smoke test.

## Git

Git mutations are forbidden by default. Read-only commands such as `git status`, `git log`, `git diff`, `git show`, `git blame`, and `git branch --list` are allowed.

When the user explicitly authorizes Git mutations for the current task:

- Verify current branch and working-tree status before editing and again before committing.
- Treat unknown changes as user work. Do not edit, stage, restore, or commit them.
- Keep commits small and cohesive.
- Use Conventional Commits 1.0.0: `<type>[optional scope]: <description>`.
- Include the relevant issue or ticket URI in the commit footer.
- Read configured Git author name and email. Keep the email, append the agent name once to the author name, and pass that identity explicitly with `git commit --author`; do not change Git configuration.
- Do not force-push, amend, rebase, reset, or discard changes without explicit permission for that exact operation.

## Agent Workflow

- Work from the repository root.
- Read `README.md` and every affected call path before making non-trivial changes.
- Inspect existing usages before changing a public global step.
- Prefer focused edits over unrelated refactoring or formatting.
- Update `README.md` when prerequisites, public Pipeline syntax, configuration keys, or runtime behavior change.
- Do not add IDE-specific files unless explicitly requested. Existing untracked IDE files belong to the user.
- Use normal patch/edit tools and preserve user work.
