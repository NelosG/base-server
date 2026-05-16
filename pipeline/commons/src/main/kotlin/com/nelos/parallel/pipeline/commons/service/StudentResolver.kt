package com.nelos.parallel.pipeline.commons.service

/**
 * Resolves an integration-specific external identifier (GitLab username,
 * future GitHub login, manual run-as, etc.) to an orchestrator-side `User`
 * id, auto-creating the student account on first contact.
 *
 * Implemented per integration (e.g. `GitlabStudentResolver` in
 * `gitlab/integration`). Pipeline core depends only on this interface -
 * never on any integration's concrete impl - so adding a new source of
 * submissions doesn't require touching pipeline code.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface StudentResolver {

    /**
     * Look up an existing user mapped to [externalId] or create a new one
     * (STUDENT type, OTP password). Returns the user id; the User row itself
     * is owned by `auth/data` and need not be exposed across module
     * boundaries.
     */
    fun resolveOrAutoCreate(externalId: String): Long
}
