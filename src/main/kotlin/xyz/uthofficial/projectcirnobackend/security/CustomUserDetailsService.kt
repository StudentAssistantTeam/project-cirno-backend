package xyz.uthofficial.projectcirnobackend.security

import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import xyz.uthofficial.projectcirnobackend.repository.UserRepository

/**
 * Bridges Spring Security authentication with the Exposed user store.
 * Loads user by username and returns UserDetails for password verification.
 */
@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    /**
     * Locates the user by username and wraps it in Spring Security UserDetails.
     * Throws UsernameNotFoundException if the user does not exist.
     */
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found: $username")

        return User.builder()
            .username(user.username)
            .password(user.password)
            .build()
    }
}
