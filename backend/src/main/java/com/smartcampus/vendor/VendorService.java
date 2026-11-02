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

    /** Public detail view, including busy level and estimated wait. */
    VendorResponse getVendor(long vendorId);

    /** Public list (GET /vendors): suspended vendors are hidden. */
    List<VendorResponse> getAllVendors();

    /** Admin list (GET /admin/vendors): every vendor, suspended or not. */
    List<VendorResponse> getAllVendorsAdmin();

    /**
     * Admin onboards a new vendor (more canteens join later, nothing is hard-coded to the seed vendors).
     * The owner must be an active user with role VENDOR who does not already own a shop.
     * Writes an admin_actions row (CREATE_VENDOR).
     */
    VendorResponse createVendor(CreateVendorRequest request, long adminUserId);

    /**
     * Updates vendor settings.
     * Enforces ownership: a vendor owner may only modify their own shop settings
     * (open/closed, rush mode, prep minutes, slot size, parallel capacity).
     * Name and suspension are admin-only. Admin can modify any shop; admin changes write an admin_actions row.
     */
    VendorResponse updateVendor(long vendorId, UpdateVendorRequest request, long requestingUserId, boolean isAdmin);

    /**
     * Business Rule (5.13):
     * Admin resets the vendor's owner password. Also sets users.password_changed_at = now
     * so existing active JWT sessions for the vendor owner are invalidated immediately.
     * Writes an admin_actions row (RESET_VENDOR_PASSWORD).
     */
    void resetVendorPassword(long vendorId, AdminResetPasswordRequest request, long adminUserId);

    Vendor getVendorEntity(long vendorId);

    /** Called by the order module when the shop's order set changes, so cached busy levels refresh. */
    void onLoadChanged(long vendorId);

    long countActiveVendors();
}
