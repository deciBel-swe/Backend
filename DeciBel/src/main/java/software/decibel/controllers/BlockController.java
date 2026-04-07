package software.decibel.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.BlockResponseDto;
import software.decibel.dtos.user.BlockedUserDto;
import software.decibel.services.BlockService;
import software.decibel.services.JwtService;

/**
 * Controller for user blocking and unblocking operations.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /**
     * POST /users/{userId}/block Blocks the specified user.
     */
    @PostMapping("/{userId}/block")
    public ResponseEntity<BlockResponseDto> blockUser(@PathVariable Long userId) {
        Long currentUserId = JwtService.getCurrentUserId();
        blockService.blockUser(currentUserId, userId);
        return ResponseEntity.ok(new BlockResponseDto("User blocked successfully"));
    }

    /**
     * DELETE /users/{userId}/block Unblocks the specified user.
     */
    @DeleteMapping("/{userId}/block")
    public ResponseEntity<Void> unblockUser(@PathVariable Long userId) {
        Long currentUserId = JwtService.getCurrentUserId();
        blockService.unblockUser(currentUserId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /users/me/blocked Retrieves a paginated list of blocked users for the
     * current user.
     */
    @GetMapping("/me/blocked")
    public ResponseEntity<Page<BlockedUserDto>> getBlockedUsers(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(blockService.getBlockedUsers(currentUserId, pageable));
    }
}
