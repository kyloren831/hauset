#!/bin/bash
BASE_DIR="src/main/java/com/appdomotica"

# Helper function
remove_lombok() {
    file=$1
    sed -i '/import lombok.Data;/d' "$file"
    sed -i '/@Data/d' "$file"
}

remove_lombok "$BASE_DIR/clientes/entity/Cliente.java"
cat << 'JAVA' >> "$BASE_DIR/clientes/entity/Cliente.java"
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
}
JAVA
sed -i '/^}$/d' "$BASE_DIR/clientes/entity/Cliente.java"
echo "}" >> "$BASE_DIR/clientes/entity/Cliente.java"

remove_lombok "$BASE_DIR/clientes/entity/Lead.java"
cat << 'JAVA' >> "$BASE_DIR/clientes/entity/Lead.java"
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public EstadoLead getEstado() { return estado; }
    public void setEstado(EstadoLead estado) { this.estado = estado; }
}
JAVA
sed -i '/^}$/d' "$BASE_DIR/clientes/entity/Lead.java"
echo "}" >> "$BASE_DIR/clientes/entity/Lead.java"

remove_lombok "$BASE_DIR/catalogo/entity/Proveedor.java"
cat << 'JAVA' >> "$BASE_DIR/catalogo/entity/Proveedor.java"
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCategoriasAsociadas() { return categoriasAsociadas; }
    public void setCategoriasAsociadas(String categoriasAsociadas) { this.categoriasAsociadas = categoriasAsociadas; }
    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }
}
JAVA
sed -i '/^}$/d' "$BASE_DIR/catalogo/entity/Proveedor.java"
echo "}" >> "$BASE_DIR/catalogo/entity/Proveedor.java"

remove_lombok "$BASE_DIR/catalogo/entity/ProductoCatalogo.java"
cat << 'JAVA' >> "$BASE_DIR/catalogo/entity/ProductoCatalogo.java"
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public CategoriaProducto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProducto categoria) { this.categoria = categoria; }
    public String getEspecificacion() { return especificacion; }
    public void setEspecificacion(String especificacion) { this.especificacion = especificacion; }
}
JAVA
sed -i '/^}$/d' "$BASE_DIR/catalogo/entity/ProductoCatalogo.java"
echo "}" >> "$BASE_DIR/catalogo/entity/ProductoCatalogo.java"

remove_lombok "$BASE_DIR/catalogo/entity/HistorialPrecio.java"
cat << 'JAVA' >> "$BASE_DIR/catalogo/entity/HistorialPrecio.java"
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public ProductoCatalogo getProducto() { return producto; }
    public void setProducto(ProductoCatalogo producto) { this.producto = producto; }
    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }
    public java.math.BigDecimal getPrecioNeto() { return precioNeto; }
    public void setPrecioNeto(java.math.BigDecimal precioNeto) { this.precioNeto = precioNeto; }
    public Boolean getDisponible() { return disponible; }
    public void setDisponible(Boolean disponible) { this.disponible = disponible; }
    public Usuario getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Usuario registradoPor) { this.registradoPor = registradoPor; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
JAVA
sed -i '/^}$/d' "$BASE_DIR/catalogo/entity/HistorialPrecio.java"
echo "}" >> "$BASE_DIR/catalogo/entity/HistorialPrecio.java"

remove_lombok "$BASE_DIR/usuarios/entity/Usuario.java"

