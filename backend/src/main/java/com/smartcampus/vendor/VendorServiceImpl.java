package com.smartcampus.vendor;

import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.vendor.dto.AdminResetPasswordRequest;
import com.smartcampus.vendor.dto.CreateVendorRequest;
import com.smartcampus.vendor.dto.UpdateVendorRequest;
import com.smartcampus.vendor.dto.VendorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {

    private final VendorRepository vendorRepository;
    private final BusyMeterService busyMeterService;

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
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorResponse> getAllVendors() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public VendorResponse createVendor(CreateVendorRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public VendorResponse updateVendor(long vendorId, UpdateVendorRequest request, long requestingUserId, boolean isAdmin) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public void resetVendorPassword(long vendorId, AdminResetPasswordRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public Vendor getVendorEntity(long vendorId) {
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new NotFoundException("Vendor not found with id: " + vendorId));
    }
}
