package com.smartcampus.vendor;

import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.vendor.dto.UpdateVendorRequest;
import com.smartcampus.vendor.dto.VendorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;
    private final CurrentUser currentUser;

    @GetMapping("/vendors")
    public ResponseEntity<List<VendorResponse>> getAllVendors() {
        return ResponseEntity.ok(vendorService.getAllVendors());
    }

    @GetMapping("/vendors/{id}")
    public ResponseEntity<VendorResponse> getVendorById(@PathVariable long id) {
        return ResponseEntity.ok(vendorService.getVendor(id));
    }

    @GetMapping("/vendor/me")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorResponse> getMyVendor() {
        return ResponseEntity.ok(vendorService.getVendor(vendorService.vendorIdOf(currentUser.id())));
    }

    @PatchMapping("/vendors/{id}")
    @PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
    public ResponseEntity<VendorResponse> updateVendor(
            @PathVariable long id,
            @Valid @RequestBody UpdateVendorRequest request) {
        boolean isAdmin = "ADMIN".equals(currentUser.role());
        return ResponseEntity.ok(vendorService.updateVendor(id, request, currentUser.id(), isAdmin));
    }
}
