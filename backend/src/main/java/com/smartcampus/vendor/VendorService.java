package com.smartcampus.vendor;

import com.smartcampus.vendor.dto.AdminResetPasswordRequest;
import com.smartcampus.vendor.dto.CreateVendorRequest;
import com.smartcampus.vendor.dto.UpdateVendorRequest;
import com.smartcampus.vendor.dto.VendorResponse;

import java.util.List;

/**
 * Service managing vendor operations, shop settings, and owner lookups.
 */
public interface VendorService {

    /**
     * Resolves the vendor id belonging to the given user id.
     * Throws NotFoundException if the user does not own any vendor outlet.
     */
    long vendorIdOf(long userId);

    VendorResponse getVendor(long vendorId);

    List<VendorResponse> getAllVendors();

    VendorResponse createVendor(CreateVendorRequest request);

    /**
     * Updates vendor settings.
     * Enforces ownership: a vendor owner may only modify their own shop settings.
     * Admin can modify any shop.
     */
    VendorResponse updateVendor(long vendorId, UpdateVendorRequest request, long requestingUserId, boolean isAdmin);

    /**
     * Business Rule (5.13):
     * Admin resets the vendor's owner password. Also sets users.password_changed_at = now
     * so existing active JWT sessions for the vendor owner are invalidated immediately.
     */
    void resetVendorPassword(long vendorId, AdminResetPasswordRequest request);

    Vendor getVendorEntity(long vendorId);
}
