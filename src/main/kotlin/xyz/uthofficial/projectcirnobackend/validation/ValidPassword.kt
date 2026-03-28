package xyz.uthofficial.projectcirnobackend.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PasswordValidator::class])
annotation class ValidPassword(
    val message: String = "Password must be 8-64 characters, include at least one letter and one digit, and contain only letters, digits, and ASCII special characters",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Any>> = []
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [UsernameValidator::class])
annotation class ValidUsername(
    val message: String = "Username must be 3-50 characters, only letters, digits, dashes, and underscores allowed",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Any>> = []
)

class UsernameValidator : ConstraintValidator<ValidUsername, String> {

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return false
        if (value.length < 3 || value.length > 50) return false
        return value.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }
}

class PasswordValidator : ConstraintValidator<ValidPassword, String> {

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return false

        if (value.length < 8 || value.length > 64) return false

        var hasLetter = false
        var hasDigit = false

        for (ch in value) {
            when {
                ch.isLetter() -> hasLetter = true
                ch.isDigit() -> hasDigit = true
                ch in ALLOWED_SPECIAL -> { /* ok */ }
                else -> return false
            }
        }

        return hasLetter && hasDigit
    }

    companion object {
        private val ALLOWED_SPECIAL = setOf(
            '~', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
            '_', '+', '-', '=', '{', '}', ':', '"', '<', '>', '?',
            '[', ']', ';', '\'', ',', '.', '/'
        )
    }
}
