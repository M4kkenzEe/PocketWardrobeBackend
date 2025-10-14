package validation

object AuthValidator {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,30}$")
    private val ALLOWED_GENDERS = setOf("MALE", "FEMALE", "OTHER")

    fun validateRegisterRequest(email: String, password: String, username: String?, gender: String?) {
        require(email.isNotBlank()) { "Email is required" }
        require(email.length <= 100) { "Email must be at most 100 characters" }
        require(EMAIL_REGEX.matches(email)) { "Invalid email format" }

        if (username != null) {
            require(username.length in 3..30) { "Username must be between 3 and 30 characters" }
            require(USERNAME_REGEX.matches(username)) {
                "Username can only contain letters, numbers, and underscores"
            }
        }

        require(password.length >= 8) { "Password must be at least 8 characters" }
        require(password.length <= 128) { "Password must be at most 128 characters" }
        require(password.any { it.isUpperCase() }) { "Password must contain at least one uppercase letter" }
        require(password.any { it.isDigit() }) { "Password must contain at least one digit" }

        if (gender != null) {
            require(gender.uppercase() in ALLOWED_GENDERS) {
                "Gender must be one of: ${ALLOWED_GENDERS.joinToString(", ")}"
            }
        }
    }

    fun validateLoginRequest(login: String, password: String) {
        require(login.isNotBlank()) { "Username or email is required" }
        require(password.isNotBlank()) { "Password is required" }
    }
}
