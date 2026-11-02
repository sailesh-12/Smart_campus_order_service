package com.smartcampus.vendor;

import com.smartcampus.audit.AuditService;
import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.exception.ForbiddenException;
import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.user.Role;
import com.smartcampus.user.User;
import com.smartcampus.user.UserService;
import com.smartcampus.vendor.dto.AdminResetPasswordRequest;
import com.smartcampus.vendor.dto.CreateVendorRequest;
import com.smartcampus.vendor.dto.UpdateVendorRequest;
import com.smartcampus.vendor.dto.VendorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {

    private final VendorRepository vendorRepository;
    private final BusyMeterService busyMeterService;
    private final VendorBacklogCache backlogCache;
    private final UserService userService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public long vendorIdOf(long userId) {
        return vendorRepository.findByOwnerUserId(userId)
                .map(Vendor::getId)
                .orElseThrow(() -> new NotFoundException("No vendor found for user id: " + userId));
    }

    @Override
    @Transactional(readOnly = true)
    public VendorResponse getVendor(long vendorId) {
        return toResponse(getVendorEntity(vendorId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorResponse> getAllVendors() {
        return vendorRepository.findByIsSuspendedFalseOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorResponse> getAllVendorsAdmin() {
        return vendorRepository.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public VendorResponse createVendor(CreateVendorRequest request, long adminUserId) {
        User owner = userService.findById(request.ownerUserId())
                .orElseThrow(() -> ApiException.badRequest("VALIDATION_ERROR", "ownerUserId", "Owner user does not exist"));
        if (owner.getRole() != Role.VENDOR || !owner.isActive()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "ownerUserId", "Owner must be an active user with role VENDOR");
        }
        if (vendorRepository.existsByOwnerUserId(owner.getId())) {
            throw new ConflictException("VENDOR_EXISTS", "This user already owns a vendor");
        }

        Vendor vendor = Vendor.builder()
                .name(request.name().trim())
                .type(request.type())
                .ownerUserId(owner.getId())
                .isOpen(false)
                .parallelCapacity(request.parallelCapacity() != null ? request.parallelCapacity() : 2)
                .slotMaxOrders(request.slotMaxOrders() != null ? request.slotMaxOrders() : 10)
                .avgPrepMinutes(request.avgPrepMinutes() != null ? request.avgPrepMinutes() : 10)
                .build();
        vendor = vendorRepository.save(vendor);
        auditService.recordAction(adminUserId, "CREATE_VENDOR", "VENDOR", vendor.getId(), vendor.getName());
        return toResponse(vendor);
    }

    @Override
    @Transactional
    public VendorResponse updateVendor(long vendorId, UpdateVendorRequest request, long requestingUserId, boolean isAdmin) {
        Vendor vendor = getVendorEntity(vendorId);
        if (!isAdmin) {
            if (!vendor.getOwnerUserId().equals(requestingUserId)) {
                throw new ForbiddenException("You can only change your own shop");
            }
            if (request.name() != null || request.isSuspended() != null) {
                throw new ForbiddenException("Only an admin can rename or suspend a shop");
            }
        }

        List<String> changes = new ArrayList<>();
        if (request.name() != null && !request.name().isBlank()) {
            vendor.setName(request.name().trim());
            changes.add("name");
        }
        if (request.isOpen() != null) {
            vendor.setOpen(request.isOpen());
            changes.add("isOpen=" + request.isOpen());
        }
        if (request.rushMode() != null) {
            vendor.setRushMode(request.rushMode());
            changes.add("rushMode=" + request.rushMode());
        }
        if (request.avgPrepMinutes() != null) {
            vendor.setAvgPrepMinutes(request.avgPrepMinutes());
            changes.add("avgPrepMinutes=" + request.avgPrepMinutes());
        }
        if (request.slotMaxOrders() != null) {
            vendor.setSlotMaxOrders(request.slotMaxOrders());
            changes.add("slotMaxOrders=" + request.slotMaxOrders());
        }
        if (request.parallelCapacity() != null) {
            vendor.setParallelCapacity(request.parallelCapacity());
            changes.add("parallelCapacity=" + request.parallelCapacity());
        }
        if (request.isSuspended() != null) {
            vendor.setSuspended(request.isSuspended());
            changes.add("isSuspended=" + request.isSuspended());
        }
        vendor = vendorRepository.save(vendor);
        // Rush mode and capacity change the queue maths, so drop the cached busy level
        backlogCache.evict(vendorId);

        if (isAdmin && !changes.isEmpty()) {
            auditService.recordAction(requestingUserId, "UPDATE_VENDOR", "VENDOR", vendorId,
                    truncate(String.join(", ", changes)));
        }
        return toResponse(vendor);
    }

    @Override
    @Transactional
    public void resetVendorPassword(long vendorId, AdminResetPasswordRequest request, long adminUserId) {
        Vendor vendor = getVendorEntity(vendorId);
        userService.setPasswordHash(vendor.getOwnerUserId(), passwordEncoder.encode(request.newPassword()));
        // Rule 5.13: JWTs issued before this moment stop working
        userService.updatePasswordChangedAt(vendor.getOwnerUserId(), clock.instant());
        auditService.recordAction(adminUserId, "RESET_VENDOR_PASSWORD", "VENDOR", vendorId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Vendor getVendorEntity(long vendorId) {
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new NotFoundException("Vendor not found with id: " + vendorId));
    }

    @Override
    public void onLoadChanged(long vendorId) {
        backlogCache.evict(vendorId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveVendors() {
        return vendorRepository.countByIsSuspendedFalse();
    }

    private VendorResponse toResponse(Vendor vendor) {
        BusyMeterService.BusySnapshot busy = busyMeterService.snapshot(vendor);
        return new VendorResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getType(),
                vendor.isOpen(),
                vendor.isRushMode(),
                vendor.getAvgPrepMinutes(),
                vendor.getSlotMaxOrders(),
                vendor.isSuspended(),
                vendor.getParallelCapacity(),
                busy.level(),
                busy.estimatedWaitMinutes()
        );
    }

    private static String truncate(String s) {
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
