package com.devforge.instance.api;

import com.devforge.instance.application.AdminInstanceResponse;
import com.devforge.instance.application.CreateAccountRequest;
import com.devforge.instance.application.InstanceResponse;
import com.devforge.instance.application.InstanceService;
import com.devforge.instance.application.InstanceSettingsRequest;
import com.devforge.instance.application.InstanceUserResponse;
import com.devforge.instance.application.SetupRequest;
import com.devforge.instance.application.SetupResponse;
import com.devforge.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Instance")
public class InstanceController {

    private final InstanceService instanceService;

    public InstanceController(InstanceService instanceService) {
        this.instanceService = instanceService;
    }

    /**
     * Branding and registration policy, needed before anyone can sign in — the
     * client cannot render its own header without it.
     */
    @GetMapping("/api/public/instance")
    @SecurityRequirements
    @Operation(summary = "Describe this instance, including whether it has been set up")
    public InstanceResponse describe() {
        return instanceService.describe();
    }

    /**
     * First run only. Refuses once the instance is configured, so it cannot be used
     * to mint an administrator on a running deployment.
     */
    @PostMapping("/api/setup")
    @SecurityRequirements
    @Operation(summary = "Configure a new instance and create its first administrator")
    public SetupResponse setUp(@Valid @RequestBody SetupRequest request) {
        return instanceService.setUp(request);
    }

    @GetMapping("/api/instance")
    @Operation(summary = "Read the full instance settings (instance admin)")
    public AdminInstanceResponse settings(@CurrentUser UUID userId) {
        return instanceService.describeForAdmin(userId);
    }

    @PutMapping("/api/instance")
    @Operation(summary = "Change the instance settings (instance admin)")
    public AdminInstanceResponse update(
            @Valid @RequestBody InstanceSettingsRequest request,
            @CurrentUser UUID userId
    ) {
        return instanceService.update(request, userId);
    }

    /**
     * The way to add people to an instance that does not accept registrations.
     */
    @PostMapping("/api/instance/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account regardless of registration mode (instance admin)")
    public InstanceUserResponse createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @CurrentUser UUID userId
    ) {
        return instanceService.createAccount(request, userId);
    }

    @GetMapping("/api/instance/admins")
    @Operation(summary = "List the administrators of this instance (instance admin)")
    public List<InstanceUserResponse> administrators(@CurrentUser UUID userId) {
        return instanceService.administrators(userId);
    }

    @PutMapping("/api/instance/users/{targetUserId}/admin")
    @Operation(summary = "Grant or revoke instance administration (instance admin)")
    public InstanceUserResponse setInstanceAdmin(
            @PathVariable UUID targetUserId,
            @Valid @RequestBody InstanceAdminRequest request,
            @CurrentUser UUID userId
    ) {
        return instanceService.setInstanceAdmin(
                targetUserId, Boolean.TRUE.equals(request.instanceAdmin()), userId);
    }

    /** @param instanceAdmin the state to move to, so the call is idempotent */
    public record InstanceAdminRequest(@jakarta.validation.constraints.NotNull Boolean instanceAdmin) {
    }
}
