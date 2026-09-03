package com.appdomotica.usuarios.dto;
public class LoginRequest {
    private String telefono;
    private String password;
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
