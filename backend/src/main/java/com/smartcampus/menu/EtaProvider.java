package com.smartcampus.menu;

import com.smartcampus.menu.dto.EtaRange;

/**
 * Interface implemented by order module (EstimateService) to provide
 * per-item dynamic wait estimates for menu displays without circular dependencies.
 */
public interface EtaProvider {

    /**
     * Calculates the estimated wait range for a specific menu item under the current vendor queue load.
     *
     * @param vendorId vendor identifier
     * @param prepMinutes standard base preparation minutes of the item
     * @return dynamic low and high estimated minutes
     */
    EtaRange getItemEta(long vendorId, int prepMinutes);
}
