package com.nelos.parallel.pipeline.runner.exception

/**
 * Thrown by [com.nelos.parallel.pipeline.runner.service.RunnerManager] when
 * every configured runner declined the task (or no runners are configured).
 * Pipeline maps this onto a failed submission with the included reasons.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class NoRunnerAvailableException(
    val attempts: List<Attempt>,
) : RuntimeException(buildMessage(attempts)) {

    data class Attempt(val runnerName: String, val reason: String)

    companion object {
        private fun buildMessage(attempts: List<Attempt>): String =
            if (attempts.isEmpty()) "no runners configured"
            else "no runner accepted the task: " +
                    attempts.joinToString(", ") { "${it.runnerName}: ${it.reason}" }
    }
}
