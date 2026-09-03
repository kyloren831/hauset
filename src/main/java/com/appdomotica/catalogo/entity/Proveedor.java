package com.appdomotica.catalogo.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "proveedores")
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String nombre;
    private String categoriasAsociadas;
    private String contacto;
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCategoriasAsociadas() { return categoriasAsociadas; }
    public void setCategoriasAsociadas(String categoriasAsociadas) { this.categoriasAsociadas = categoriasAsociadas; }
    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }
}
