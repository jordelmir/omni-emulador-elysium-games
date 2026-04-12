package com.elysium.console.domain.usecase

/**
 * Interface for executing privileged shell commands.
 * Implemented in the :data module via Shizuku API.
 *
 * This abstraction keeps the :domain module free of Android dependencies
 * while allowing kernel-level operations like CPU governor manipulation.
 */
interface ShellCommandExecutor {
    /**
     * Executes a shell command with elevated privileges.
     *
     * @param command The shell command to execute
     * @return Result containing stdout on success, or an exception on failure
     */
    suspend fun execute(command: String): Result<String>

    /**
     * Returns true if the executor has the necessary permissions
     * to run privileged commands.
     */
    suspend fun isAvailable(): Boolean
}
