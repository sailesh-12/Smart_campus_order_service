package com.smartcampus.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByVendorId(Long vendorId);

    List<MenuItem> findByVendorIdOrderByCategoryAscNameAsc(Long vendorId);

    List<MenuItem> findByVendorIdAndIsAvailableTrue(Long vendorId);

    List<MenuItem> findByIdInOrderByIdAsc(Collection<Long> ids);

    /**
     * Business Rule (5.1 & 5.2):
     * Conditional decrement to prevent overselling. Only decrements if stock_qty >= qty.
     * Returns 1 if decrement succeeded, 0 if stock was insufficient.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MenuItem m SET m.stockQty = m.stockQty - :qty WHERE m.id = :itemId AND m.stockQty >= :qty")
    int tryDecrementStock(@Param("itemId") Long itemId, @Param("qty") int qty);

    /**
     * Business Rule (5.2):
     * Adds stock back unconditionally (e.g., when order is cancelled, expired, or rejected).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MenuItem m SET m.stockQty = m.stockQty + :qty WHERE m.id = :itemId")
    int incrementStock(@Param("itemId") Long itemId, @Param("qty") int qty);

    /** Fresh read of the stock after a bulk update (bypasses the persistence-context copy). */
    @Query(value = "SELECT stock_qty FROM menu_items WHERE id = :itemId", nativeQuery = true)
    Integer currentStock(@Param("itemId") Long itemId);

    /**
     * How many order lines point at this item. Items that were ever ordered cannot be hard-deleted
     * (order_items.menu_item_id is a foreign key that keeps order history intact).
     */
    @Query(value = "SELECT COUNT(*) FROM order_items WHERE menu_item_id = :itemId", nativeQuery = true)
    long countOrderLines(@Param("itemId") Long itemId);
}
