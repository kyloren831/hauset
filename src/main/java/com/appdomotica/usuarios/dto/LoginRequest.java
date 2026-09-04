package com.appdomotica.usuarios.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String telefono;
    private String password;
}
