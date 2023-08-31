package meetona.data.repository;

import meetona.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    User findByUsername(String username);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
}
