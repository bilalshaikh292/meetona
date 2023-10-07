package meetona.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import meetona.user.UserDto;
import meetona.shared.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final IAuthService authService;

    @PostMapping(value = "login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<UserDto>> authenticate(@Valid @RequestBody LoginDto loginDto) {
        return ResponseEntity.ok(authService.authenticate(loginDto));
    }
}