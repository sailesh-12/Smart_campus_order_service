package com.smartcampus.vendor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByOwnerUserId(Long ownerUserId);

    List<Vendor> findByIsSuspendedFalse();
}
