package com.smartcampus.vendor;

import com.smartcampus.vendor.dto.AdminResetPasswordRequest;
import com.smartcampus.vendor.dto.CreateVendorRequest;
import com.smartcampus.vendor.dto.UpdateVendorRequest;
import com.smartcampus.vendor.dto.VendorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/vendors")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class VendorAdminController {

    private final VendorService vendorService;

    @PostMapping
    public ResponseEntity<VendorResponse> createVendor(@Valid @RequestBody CreateVendorRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping
    public ResponseEntity<List<VendorResponse>> getAllVendorsAdmin() {
        throw new UnsupportedOperationException("TODO");
    }

    @PatchMapping("/{id}")
    public ResponseEntity<VendorResponse> updateVendorAdmin(
            @PathVariable long id,
            @Valid @RequestBody UpdateVendorRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetVendorPassword(
            @PathVariable long id,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        throw new UnsupportedOperationException("TODO");
    }
}
