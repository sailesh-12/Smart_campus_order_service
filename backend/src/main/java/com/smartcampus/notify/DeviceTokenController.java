package com.smartcampus.notify;

import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.notify.dto.RegisterDeviceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final CurrentUser currentUser;

    @PostMapping
    public ResponseEntity<Void> registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        deviceTokenService.registerToken(currentUser.id(), request);
        return ResponseEntity.ok().build();
    }

    /** Called on logout. Deleting a token that is not yours (or is unknown) is a silent no-op. */
    @DeleteMapping("/{token}")
    public ResponseEntity<Void> unregisterDevice(@PathVariable String token) {
        deviceTokenService.deleteToken(token, currentUser.id());
        return ResponseEntity.ok().build();
    }
}
