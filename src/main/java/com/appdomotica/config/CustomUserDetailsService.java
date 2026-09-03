package com.appdomotica.config;

import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String telefono) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByTelefono(telefono)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con teléfono: " + telefono));
        return new CustomUserDetails(usuario);
    }
}
