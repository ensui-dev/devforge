package com.devforge.identity.api;

import com.devforge.identity.application.UserResponse;
import com.devforge.identity.contract.UserDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Validated
@Tag(name = "Users")
public class UserController {

    private final UserDirectory userDirectory;

    public UserController(UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }

    @GetMapping
    @Operation(summary = "Find users by name or email, for adding them to a workspace")
    public List<UserResponse> search(@RequestParam("q") @Size(min = 2, max = 320) String query) {
        return userDirectory.search(query).stream().map(UserResponse::from).toList();
    }
}
