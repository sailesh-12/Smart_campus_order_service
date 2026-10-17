package com.smartcampus.vendor;

/**
 * Interface implemented by the order module (EstimateService) to provide
 * the current active backlog minutes for a vendor without creating a circular module dependency.
 */
public interface VendorLoadReader {

    /**
     * Computes the current active cooking backlog in minutes for a shop.
     * Backlog is the sum of ownMinutes across orders in PAID, ACCEPTED, and PREPARING,
     * deducting elapsed preparation time for items actively cooking.
     *
     * @param vendorId vendor identifier
     * @return current active backlog minutes
     */
    int getBacklogMinutes(long vendorId);
}
