package com.smartcampus.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByMenuItemIdAsc(Long orderId);

    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Section 6: current prep_minutes of every line of the given orders, as rows [orderId, prepMinutes].
     * One query for the whole backlog of a shop.
     */
    @Query(value = "SELECT oi.order_id, m.prep_minutes FROM order_items oi " +
            "JOIN menu_items m ON m.id = oi.menu_item_id WHERE oi.order_id IN (:orderIds)", nativeQuery = true)
    List<Object[]> findPrepMinutesByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
