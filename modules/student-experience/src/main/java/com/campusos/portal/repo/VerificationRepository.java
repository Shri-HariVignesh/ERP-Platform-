package com.campusos.portal.repo;

import com.campusos.portal.domain.Verification;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface VerificationRepository extends Repository<Verification, String> {

    /** Public QR target — deliberately unscoped. See repo/README.txt. */
    Optional<Verification> findByVerifyId(String verifyId);

    Verification save(Verification v);
}
