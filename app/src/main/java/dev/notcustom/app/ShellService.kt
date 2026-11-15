package dev.notcustom.app

class ShellService : IShellService.Stub {
    constructor() : super()

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            if (code != 0) {
                ERROR_PREFIX + output.trim().ifBlank { "exit $code" }
            } else {
                output.trim()
            }
        } catch (t: Throwable) {
            ERROR_PREFIX + (t.message ?: "command failed")
        }
    }

    override fun destroy() {
    }

    companion object {
        const val ERROR_PREFIX = "!!NOTCUSTOM_ERROR!!:"
    }
}
