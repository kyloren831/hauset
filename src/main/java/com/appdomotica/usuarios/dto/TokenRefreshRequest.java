package com.appdomotica.usuarios.dto;

import lombok.Data;

@Data
public class TokenRefreshRequest {
    private String refreshToken;
}
