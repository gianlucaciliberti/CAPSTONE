package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.enums.AccountStatus;
import it.squadra.gestionale.enums.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByStatus(AccountStatus status, Pageable pageable);

    List<User> findByStatusOrderByLastNameAscFirstNameAsc(AccountStatus status);

    long countByRoleAndStatus(Role role, AccountStatus status);
}
