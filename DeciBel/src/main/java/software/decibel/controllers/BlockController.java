package software.decibel.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.decibel.dtos.user.BlockedUserDto;
import software.decibel.services.BlockService;
import software.decibel.services.JwtService;

/**
 * Controller for user blocking and unblocking operations.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /**
     * POST /api/users/{userId}/block
     * Blocks the specified user.
     */
    @PostMapping("/{userId}/block")
    public ResponseEntity<Void> blockUser(@PathVariable Long userId) {
        Long currentUserId = JwtService.getCurrentUserId();
        blockService.blockUser(currentUserId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/users/{userId}/block
     * Unblocks the specified user.
     */
    @DeleteMapping("/{userId}/block")
    public ResponseEntity<Void> unblockUser(@PathVariable Long userId) {
        Long currentUserId = JwtService.getCurrentUserId();
        blockService.unblockUser(currentUserId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/users/me/blocked
     * Retrieves a paginated list of blocked users for the current user.
     */
    @GetMapping("/me/blocked")
    public ResponseEntity<Page<BlockedUserDto>> getBlockedUsers(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(blockService.getBlockedUsers(currentUserId, pageable));
    }
}
