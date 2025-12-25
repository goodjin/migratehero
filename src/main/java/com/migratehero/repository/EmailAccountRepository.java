package com.migratehero.repository;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.User;
import com.migratehero.model.enums.ConnectionStatus;
import com.migratehero.model.enums.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 邮箱账户数据访问层
 */
@Repository
public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    List<EmailAccount> findByUser(User user);

    List<EmailAccount> findByUserOrderByCreatedAtDesc(User user);

    Optional<EmailAccount> findByIdAndUser(Long id, User user);

    Optional<EmailAccount> findByUserAndEmail(User user, String email);

    List<EmailAccount> findByUserAndProvider(User user, ProviderType provider);

    List<EmailAccount> findByUserAndStatus(User user, ConnectionStatus status);

    boolean existsByUserAndEmail(User user, String email);

    @Query("SELECT a FROM EmailAccount a WHERE a.user = :user AND a.status = 'CONNECTED'")
    List<EmailAccount> findConnectedAccountsByUser(@Param("user") User user);

    @Query("SELECT a FROM EmailAccount a WHERE a.tokenExpiresAt < :threshold AND a.status = 'CONNECTED'")
    List<EmailAccount> findAccountsWithExpiringTokens(@Param("threshold") Instant threshold);

    @Query("SELECT COUNT(a) FROM EmailAccount a WHERE a.user = :user AND a.status = 'CONNECTED'")
    long countConnectedAccountsByUser(@Param("user") User user);
}
