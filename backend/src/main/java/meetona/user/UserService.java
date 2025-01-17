package meetona.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import meetona.member.MemberRepository;
import meetona.shared.exception.ResourceNotFoundException;
import meetona.user.security.TokenProvider;
import meetona.shared.enums.AppRole;
import meetona.role.Role;
import meetona.role.RoleRepository;
import meetona.shared.exception.AppException;
import meetona.shared.exception.LoginException;
import meetona.shared.exception.InsertionFailedException;
import meetona.shared.response.ApiResponse;
import meetona.user.dtos.AuthDto;
import meetona.user.dtos.UserDto;
import meetona.user.dtos.UserRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements IUserService {

    private final UserMapper mapper;
    private final TokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserActionProducer userActionProducer;
    private final AuthenticationManager authenticationManager;

    @Override
    public ApiResponse<UserDto> authenticate(AuthDto authDto) {
        var authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                authDto.username(),
                authDto.password())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String accessToken = tokenProvider.createToken(authentication);

        User user = (User) authentication.getPrincipal();

        if (user == null) throw new LoginException("User not found");

        UserDto userDto = mapper.toDto(user).setAccessToken(accessToken);
        var response = new ApiResponse<>(userDto, true);

        userActionProducer.sendMessage(userDto);
        return response;
    }

    @Override
    @Cacheable("users")
    public ApiResponse<List<UserDto>> getAll(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);

        List<UserDto> userDto = users.stream()
                .map(mapper::toDto)
                .toList();

        ApiResponse<List<UserDto>> response = new ApiResponse<>(userDto, true);

        log.info("Fetched units => {}", userDto);
        return response;
    }

    @Override
    @Cacheable("user")
    public ApiResponse<UserDto> getById(UUID id) {
        Optional<User> userOptional = userRepository.findById(id);

        User user = userOptional.orElse(null); // Unwrap the Optional to get a Unit or null

        UserDto userDto = mapper.toDto(user);

        var response = new ApiResponse<>(userDto, true);

        log.info("Fetched unit => {}", userDto);
        return response;
    }

    @Transactional
    public ApiResponse<UserDto> add(UserRequest userRequest) {
        boolean isUsernameExists = userRepository.existsByUsername(userRequest.username());

        if (isUsernameExists) {
            throw new InsertionFailedException(userRequest.username(), " already exists");
        }


        User newUser = buildUser(userRequest);
        userRepository.save(newUser);

        UserDto userDto = mapper.toDto(newUser);

        var response = new ApiResponse<>(userDto, true);

        userActionProducer.sendMessage(userDto);
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = "user", key = "#user.id")
    public ApiResponse<UserDto> update(UUID id, UserRequest request) {
        boolean isUserExists = userRepository.existsById(id);

        if(!isUserExists) {
            throw new ResourceNotFoundException("User", "id", id);
        }

        User newUser = buildUser(request);

        userRepository.save(newUser);
        UserDto updatedUser = mapper.toDto(newUser);

        var response = new ApiResponse<>(updatedUser, true);

        userActionProducer.sendMessage(updatedUser);
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = "user", key = "#id")
    public ApiResponse<UserDto> delete(UUID id) {
        boolean isUserExists = userRepository.existsById(id);

        if(!isUserExists){
            throw new ResourceNotFoundException("User", "id", id);
        }

        userRepository.deleteById(id);
        UserDto deletedUser = new UserDto(id, null, null, null, null, null);

        var response = new ApiResponse<>(deletedUser, true);

        userActionProducer.sendMessage(deletedUser);
        return response;
    }

    private User buildUser(UserRequest request) {
        var member = memberRepository
                .findById(request.memberId())
                .orElseThrow(() -> new IllegalArgumentException(request.memberId() + " does not exist"));

        Set<Role> roles = request.roles().stream()
                .map(role -> roleRepository.findByName(AppRole.valueOf(role)))
                .collect(Collectors.toSet());

        return User.builder()
                .username(request.username())
                .email(request.email())
                .roles(roles)
                .isEmailVerified(false)
                .member(member)
                .password(passwordEncoder.encode(request.password()))
                .build();
    }
}
