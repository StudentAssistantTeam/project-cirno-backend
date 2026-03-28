package xyz.uthofficial.projectcirnobackend.config

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    private fun writeJsonError(response: HttpServletResponse, status: HttpStatus, message: String) {
        response.status = status.value()
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, response: HttpServletResponse) {
        logger.warn("Bad request: ${ex.message}")
        writeJsonError(response, HttpStatus.BAD_REQUEST, """{"error":"${ex.message ?: "Bad request"}"}""")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, response: HttpServletResponse) {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") }
        logger.warn("Validation failed: $fieldErrors")
        val errorsJson = fieldErrors.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
        writeJsonError(response, HttpStatus.BAD_REQUEST, """{"error":"Validation failed","fieldErrors":{$errorsJson}}""")
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(ex: HandlerMethodValidationException, response: HttpServletResponse) {
        logger.warn("Method validation failed: ${ex.message}")
        writeJsonError(response, HttpStatus.BAD_REQUEST, """{"error":"${ex.message ?: "Validation failed"}"}""")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: HttpMessageNotReadableException, response: HttpServletResponse) {
        logger.warn("Unreadable request body: ${ex.message}")
        writeJsonError(response, HttpStatus.BAD_REQUEST, """{"error":"${ex.message ?: "Invalid request body"}"}""")
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, response: HttpServletResponse) {
        logger.error("Unhandled exception: ${ex.javaClass.name}: ${ex.message}", ex)
        writeJsonError(response, HttpStatus.INTERNAL_SERVER_ERROR, """{"error":"Internal server error"}""")
    }
}
