package com.smartcampus.notify;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserIdOrderByCreatedAtAsc(Long userId);

    void deleteByToken(String token);

    void deleteByUserId(Long userId);
}
