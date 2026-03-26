package com.fingaurd.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class RefreshRequest {
    @NotBlank private String refreshToken;
}
