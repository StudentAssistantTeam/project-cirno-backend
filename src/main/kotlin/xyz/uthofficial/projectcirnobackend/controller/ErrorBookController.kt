package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import xyz.uthofficial.projectcirnobackend.dto.CreateErrorBookRequest
import xyz.uthofficial.projectcirnobackend.dto.EditErrorBookRequest
import xyz.uthofficial.projectcirnobackend.dto.ErrorBookResponse
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import xyz.uthofficial.projectcirnobackend.service.ErrorBookService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/errorbook")
class ErrorBookController(
    private val errorBookService: ErrorBookService,
    private val userRepository: UserRepository
) {

    private val uploadDir: Path = Paths.get("uploads/errorbook").toAbsolutePath()

    private fun resolveUserId(principal: Principal): UUID {
        val user = userRepository.findByUsername(principal.name)
            ?: throw IllegalArgumentException("User not found")
        return user.id.value
    }

    @PostMapping
    fun createError(
        @Valid @RequestBody request: CreateErrorBookRequest,
        principal: Principal
    ): ResponseEntity<Any> {
        try {
            val userId = resolveUserId(principal)
            val result = errorBookService.create(
                userId = userId,
                description = request.description,
                imagePath = null,
                tags = request.tags,
                date = request.date,
                eventId = request.eventId
            )
            return ResponseEntity.status(HttpStatus.CREATED).body(result)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun listErrors(
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(required = false) keywords: String?,
        principal: Principal
    ): ResponseEntity<Any> {
        try {
            val userId = resolveUserId(principal)
            val keywordList = keywords
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
            val errors = errorBookService.findAll(userId, tag, dateFrom, dateTo, keywordList)
            return ResponseEntity.ok(mapOf("errors" to errors))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/{id}")
    fun getError(
        @PathVariable id: UUID,
        principal: Principal
    ): ResponseEntity<Any> {
        try {
            val userId = resolveUserId(principal)
            val result = errorBookService.findById(userId, id)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Error record not found"))
            return ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PatchMapping("/{id}")
    fun editError(
        @PathVariable id: UUID,
        @Valid @RequestBody request: EditErrorBookRequest,
        principal: Principal
    ): ResponseEntity<Any> {
        try {
            val userId = resolveUserId(principal)
            val result = errorBookService.update(
                userId = userId,
                errorId = id,
                description = request.description,
                tags = request.tags,
                date = request.date,
                eventId = request.eventId
            )
            return ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{id}")
    fun deleteError(
        @PathVariable id: UUID,
        principal: Principal
    ): ResponseEntity<Any> {
        try {
            val userId = resolveUserId(principal)
            val oldImagePath = errorBookService.delete(userId, id)
            deleteImageFile(oldImagePath)
            return ResponseEntity.ok(mapOf("message" to "Error record deleted"))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadImage(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
        principal: Principal
    ): ResponseEntity<Any> {
        try {
            val userId = resolveUserId(principal)

            if (file.isEmpty) {
                return ResponseEntity.badRequest().body(mapOf("error" to "File is empty"))
            }

            val contentType = file.contentType ?: ""
            if (!contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(mapOf("error" to "Only image files are allowed"))
            }

            Files.createDirectories(uploadDir)

            val originalName = file.originalFilename ?: "image"
            val ext = originalName.substringAfterLast('.', "png")
            val safeName = "${UUID.randomUUID()}-${originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            val filePath = uploadDir.resolve(safeName)

            Files.copy(file.inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)

            val relativePath = "uploads/errorbook/$safeName"
            val oldPath = errorBookService.updateImagePath(userId, id, relativePath)
            deleteImageFile(oldPath)

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapOf("message" to "Image uploaded", "path" to relativePath))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to upload image: ${e.message}"))
        }
    }

    @GetMapping("/{id}/image")
    fun getImage(
        @PathVariable id: UUID,
        principal: Principal
    ): ResponseEntity<Any> {
        try {
            val userId = resolveUserId(principal)
            val error = errorBookService.findById(userId, id)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Error record not found"))

            val imagePath = error.imagePath
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "No image attached to this error record"))

            val file = Paths.get(imagePath).toFile()
            if (!file.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Image file not found on disk"))
            }

            val mediaType = try {
                MediaType.parseMediaType(Files.probeContentType(file.toPath()) ?: "application/octet-stream")
            } catch (e: Exception) {
                MediaType.APPLICATION_OCTET_STREAM
            }

            return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(file.length())
                .body(file.readBytes())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    private fun deleteImageFile(relativePath: String?) {
        if (relativePath != null) {
            try {
                val file = Paths.get(relativePath).toFile()
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
            }
        }
    }
}
