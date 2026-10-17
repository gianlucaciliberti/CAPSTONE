package it.squadra.gestionale.repositories;

import it.squadra.gestionale.entities.UserToken;
import it.squadra.gestionale.enums.TokenPurpose;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /** Invalida i token ancora validi dello stesso tipo quando ne viene emesso uno nuovo. */
    @Modifying
    @Query("update UserToken t set t.usedAt = :now "
            + "where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateActive(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
                         @Param("now") Instant now);
}
