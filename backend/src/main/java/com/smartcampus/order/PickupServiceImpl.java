package com.smartcampus.order;

import com.smartcampus.common.exception.ForbiddenException;
import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.common.util.TokenGenerator;
import com.smartcampus.order.dto.PickupCredentialsResponse;
import com.smartcampus.order.dto.VerifyPickupRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PickupServiceImpl implements PickupService {

    private final OrderRepository orderRepository;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public void generatePickupCredentials(Order order) {
        LocalDate today = LocalDate.now();
        order.setPickupDate(today);

        // Generate short code e.g. A47 with collision retry
        String code = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            char letter = (char) ('A' + random.nextInt(26));
            int number = 10 + random.nextInt(90);
            String candidate = "" + letter + number;
            if (!orderRepository.existsByVendorIdAndPickupDateAndPickupCode(order.getVendorId(), today, candidate)) {
                code = candidate;
                break;
            }
        }
        order.setPickupCode(code != null ? code : "A" + (100 + random.nextInt(900)));
        order.setPickupSecret(TokenGenerator.generateRandomSecret(32));
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void verifyPickup(long vendorId, VerifyPickupRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public PickupCredentialsResponse getPickupDetails(long orderId, long studentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        if (!order.getStudentId().equals(studentId)) {
            throw new ForbiddenException("Cannot view pickup details of another student's order");
        }
        return new PickupCredentialsResponse(order.getId(), order.getPickupCode(), order.getPickupSecret(), order.getPickupDate());
    }
}
