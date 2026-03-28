package xyz.uthofficial.projectcirnobackend.service

import org.springframework.stereotype.Service
import xyz.uthofficial.projectcirnobackend.dto.UserIdentityResponse
import xyz.uthofficial.projectcirnobackend.repository.UserIdentityRepository
import java.util.UUID

@Service
class UserIdentityService(
    private val userIdentityRepository: UserIdentityRepository
) {

    fun getByIdentity(userId: UUID): UserIdentityResponse? {
        val record = userIdentityRepository.findByUserId(userId) ?: return null
        return UserIdentityResponse(identity = record.identity, goal = record.goal)
    }

    fun create(userId: UUID, identity: String, goal: String): UserIdentityResponse {
        val record = userIdentityRepository.create(userId, identity, goal)
        return UserIdentityResponse(identity = record.identity, goal = record.goal)
    }

    fun update(userId: UUID, identity: String?, goal: String?): UserIdentityResponse {
        val record = userIdentityRepository.update(userId, identity, goal)
        return UserIdentityResponse(identity = record.identity, goal = record.goal)
    }
}
