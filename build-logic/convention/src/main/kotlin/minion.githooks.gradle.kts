/**
 * Points git at the repo's tracked hooks.
 *
 * `.githooks/pre-push` is versioned, unlike `.git/hooks`, which git does not track — but a tracked
 * hook does nothing until `core.hooksPath` names its directory. Left as a line in the README that
 * is really a line in CLAUDE.md, that opt-in is a step every clone has to remember and most will
 * not, which makes the convention true only for whoever happened to read the file.
 *
 * Wiring it to `check` is the same reasoning as detekt's: an enforced convention beats one nobody
 * remembers to run.
 *
 * **Applied only by `minion.android.application`, so it exists once rather than in all 33 modules.**
 * Git hooks are a property of the checkout, not of a module, and `./gradlew build` always builds
 * `:app`. The cost is that `./gradlew :core:emv:test` alone will not install them; the next full
 * build will.
 */

// Resolved at configuration time and captured as plain values, so the task carries no reference to
// the Project into execution and the configuration cache can serialise it.
val repoRoot = rootProject.layout.projectDirectory.asFile
val isGitCheckout = repoRoot.resolve(".git").exists()

/**
 * Idempotent, and cheap enough to run unconditionally.
 *
 * No declared output, so it runs on every `check`. A marker file would let Gradle skip it, but the
 * state it would be asserting lives in `.git/config` — where a developer can unset it, and where a
 * stale marker would then be a confident lie. A `git config` call costs a few milliseconds; being
 * wrong about whether the hooks are installed costs a pushed branch that should have been refused.
 */
val installGitHooks = tasks.register<Exec>("installGitHooks") {
    group = "build setup"
    description = "Points git at .githooks so the tracked pre-push hook actually runs."

    workingDir = repoRoot
    commandLine("git", "config", "core.hooksPath", ".githooks")

    // A source archive, or a CI job that materialises the tree without a .git directory, has no
    // git config to write. Not an error — there is simply nothing to install into.
    //
    // `enabled`, not `onlyIf { }`: a lambda written in a precompiled script plugin captures the
    // script object, which the configuration cache cannot serialise. It fails with "cannot
    // serialize Gradle script object references", naming the task rather than the lambda. A plain
    // property assignment is evaluated at configuration time and stores a Boolean.
    enabled = isGitCheckout

    // git being absent from PATH is not a reason to fail a build that otherwise succeeds. The
    // hooks are a convenience for the person pushing, and they will find out on the push.
    isIgnoreExitValue = true
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(installGitHooks)
}
