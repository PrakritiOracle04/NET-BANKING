package com.oracle.banking.auth.dto;

import com.oracle.banking.auth.entity.AppUser;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class UserOperationsDtos {
    private UserOperationsDtos() {}

    public record UserItem(
            String userId,
            String username,
            String email,
            String phone,
            String role,
            String status,
            Instant createdAt) {
        public static UserItem from(AppUser user) {
            return new UserItem(
                    user.getUserId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getRole().getRoleName(),
                    user.getStatus(),
                    user.getCreatedAt());
        }
    }

    public record UserPage(List<UserItem> items, int page, int size, long totalElements, int totalPages) {
        public static UserPage from(Page<AppUser> result) {
            return new UserPage(
                    result.getContent().stream().map(UserItem::from).toList(),
                    result.getNumber(),
                    result.getSize(),
                    result.getTotalElements(),
                    result.getTotalPages());
        }
    }

    public record UserSummary(long total, long active, long inactive) {}
}
