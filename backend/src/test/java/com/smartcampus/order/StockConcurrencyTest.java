package com.smartcampus.order;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for concurrent stock reservation scenarios (Section 5.1, 5.2).
 */
class StockConcurrencyTest {

    @Test
    @Disabled("TODO: stock=1, two parallel orders — exactly one succeeds, the other gets OUT_OF_STOCK, final stock=0")
    void stock1_twoParallelOrders_exactlyOneSucceeds() {
    }

    @Test
    @Disabled("TODO: stock=5, twenty parallel orders — exactly 5 succeed, final stock=0")
    void stock5_twentyParallelOrders_exactlyFiveSucceed_finalStockZero() {
    }

    @Test
    @Disabled("TODO: multi-item cart where one item is out of stock leaves no stock or slot changed (full rollback)")
    void multiItemCart_oneItemOutOfStock_noStockOrSlotChanged() {
    }
}
