package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.User;
import it.squadra.gestionale.entities.User.AccountStatus;
import it.squadra.gestionale.entities.User.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    Page<User> findByStatus(AccountStatus status, Pageable pageable);

    List<User> findByStatusOrderByLastNameAscFirstNameAsc(AccountStatus status);

    long countByRoleAndStatus(Role role, AccountStatus status);

    /** Registrazioni con email verificata in attesa di approvazione. */
    long countByStatusAndEmailVerifiedAtIsNotNull(AccountStatus status);

    /**
     * Admin attivi con lock di riga: due modifiche simultanee che toccano gli Admin vengono serializzate,
     * così non possono rimuovere entrambe "l'ultimo" Admin.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.status = :status")
    List<User> lockByRoleAndStatus(@Param("role") Role role, @Param("status") AccountStatus status);

    default List<User> lockActiveAdmins() {
        return lockByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE);
    }
}
