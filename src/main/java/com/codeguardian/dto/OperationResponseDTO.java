package com.codeguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic operation response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationResponseDTO {
    
    /**
     * Whether the operation succeeded
     */
    private Boolean success;
    
    /**
     * Response message
     */
    private String message;
    
    /**
     * Additional data (optional)
     */
    private Object data;

    public static OperationResponseDTO success(String message) {
        return OperationResponseDTO.builder()
                .success(true)
                .message(message)
                .build();
    }
    
    public static OperationResponseDTO success(String message, Object data) {
        return OperationResponseDTO.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static OperationResponseDTO error(String message) {
        return OperationResponseDTO.builder()
                .success(false)
                .message(message)
                .build();
    }
}
