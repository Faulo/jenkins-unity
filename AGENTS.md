# AGENTS.md

Shared instructions for coding agents. Project-specific information is kept in [README.md](README.md), read it before non-trivial changes.

## Jenkins

### Repository conventions

- Follow existing source layout and build system. Do not introduce Gradle,
  Maven, or another framework for local convenience.
- In Jenkins Shared Libraries, treat `vars/*.groovy` as global steps. Filename
  defines public step name; overloaded `call` methods define invocation forms.
- Keep public APIs small. Prefer a `Map` for programmatic Pipeline configuration
  and delegated `Closure` where a DSL improves readability.
- Keep reusable classes outside global-step scripts when they do not need
  Pipeline script binding. Static state is controller-JVM-wide across builds.
- Put stable user-facing behavior and examples in `README.md`.

### Groovy style

Match touched file. Do not normalize unrelated indentation or formatting.
Opening braces stay on declaration or control-flow lines. Prefer single-quoted
literals and double-quoted interpolation. Omit semicolons in new code. Use
camelCase for locals and methods, PascalCase for classes, and uppercase snake
case for public Pipeline configuration keys and environment variables.

### Jenkins CPS safety

- Do not retain non-serializable Jenkins, Hudson, iterator, matcher, stream, or
  platform objects across Pipeline step calls.
- Use `@NonCPS` only for pure computation invoking no Pipeline steps.
- Treat `node`, `stage`, `dir`, `sh`, `powershell`, `withEnv`,
  `withCredentials`, `stash`, and similar calls as suspension points.
- Do not store build-local state in ordinary static fields.
- Make scoped behavior lexical, nestable, concurrency-safe, and exception-safe.
- IDE unresolved-symbol warnings alone do not prove a dynamic global-step error.

### Errors, interruptions, and results

Preserve `FlowInterruptedException`: set build result when appropriate, then
rethrow it before broad `Throwable` catches. Do not convert aborts or timeouts
to ordinary failures. Preserve each helper's streamed-output, captured-stdout,
or numeric-exit-status contract.

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

### Jenkins validation

Prefer configured Jenkins integration over scraping HTML. Read-only build
inspection is allowed when relevant. Triggering, replaying, stopping, or
mutating builds requires explicit authorization. Standalone Groovy cannot
faithfully reproduce CPS; validate changed call chains, repository tests,
small safe runtime probes, then an authorized representative Pipeline when
needed.
