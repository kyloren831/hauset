package com.appdomotica.usuarios.dto;

import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {
    private UUID id;
    private String nombre;
    private RolUsuario rol;
    private String telefono;
    private Boolean activo;

    public static UsuarioResponse fromEntity(Usuario usuario) {
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .rol(usuario.getRol())
                .telefono(usuario.getTelefono())
                .activo(usuario.getActivo())
                .build();
    }
}
