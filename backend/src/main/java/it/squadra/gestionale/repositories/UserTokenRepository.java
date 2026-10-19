package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.UserToken;
import it.squadra.gestionale.entities.UserToken.TokenPurpose;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    /** Lettura con lock di riga: due richieste simultanee con lo stesso link non possono usarlo entrambe. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserToken t join fetch t.user where t.tokenHash = :tokenHash and t.purpose = :purpose")
    Optional<UserToken> findForUpdate(@Param("tokenHash") String tokenHash, @Param("purpose") TokenPurpose purpose);

    /** Usato per non inviare più di un link in un breve intervallo allo stesso utente. */
    boolean existsByUserIdAndPurposeAndCreatedAtAfter(Long userId, TokenPurpose purpose, Instant after);

    /** Invalida i token ancora validi dello stesso tipo (nuovo link emesso o password reimpostata). */
    @Modifying(flushAutomatically = true)
    @Query("update UserToken t set t.usedAt = :now "
            + "where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateActive(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
                         @Param("now") Instant now);
}
