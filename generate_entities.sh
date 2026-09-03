#!/bin/bash
BASE_DIR="src/main/java/com/appdomotica"

# ----------------- USUARIOS -----------------
cat << 'JAVA' > "$BASE_DIR/usuarios/entity/RolUsuario.java"
package com.appdomotica.usuarios.entity;
public enum RolUsuario {
    JEFE_ELECTROMECANICO, JEFE_SISTEMAS, INGENIERO_INDUSTRIAL, INSTALADOR, SECRETARIA, MARKETING
}
JAVA

cat << 'JAVA' > "$BASE_DIR/usuarios/entity/Usuario.java"
package com.appdomotica.usuarios.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "usuarios")
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String nombre;

    @Enumerated(EnumType.STRING)
    private RolUsuario rol;

    private String telefono;

    private Boolean activo = true;
}
JAVA

# ----------------- CLIENTES -----------------
cat << 'JAVA' > "$BASE_DIR/clientes/entity/Cliente.java"
package com.appdomotica.clientes.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "clientes")
@Data
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String nombre;
    private String telefono;
    private String direccion;
    private String notas;
}
JAVA

cat << 'JAVA' > "$BASE_DIR/clientes/entity/EstadoLead.java"
package com.appdomotica.clientes.entity;
public enum EstadoLead { NUEVO, COTIZADO, DESCARTADO }
JAVA

cat << 'JAVA' > "$BASE_DIR/clientes/entity/Lead.java"
package com.appdomotica.clientes.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "leads")
@Data
public class Lead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    private String origen = "whatsapp";
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    private EstadoLead estado;
}
JAVA

# ----------------- CATALOGO -----------------
cat << 'JAVA' > "$BASE_DIR/catalogo/entity/Proveedor.java"
package com.appdomotica.catalogo.entity;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "proveedores")
@Data
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String nombre;
    private String categoriasAsociadas;
    private String contacto;
}
JAVA

cat << 'JAVA' > "$BASE_DIR/catalogo/entity/CategoriaProducto.java"
package com.appdomotica.catalogo.entity;
public enum CategoriaProducto { SEGURIDAD, ILUMINACION, CONTROL_ACCESO, AUDIO_AUTOMATIZACION }
JAVA

cat << 'JAVA' > "$BASE_DIR/catalogo/entity/ProductoCatalogo.java"
package com.appdomotica.catalogo.entity;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "catalogo_productos")
@Data
public class ProductoCatalogo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String nombre;
    @Enumerated(EnumType.STRING)
    private CategoriaProducto categoria;
    private String especificacion;
}
JAVA

cat << 'JAVA' > "$BASE_DIR/catalogo/entity/HistorialPrecio.java"
package com.appdomotica.catalogo.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.appdomotica.usuarios.entity.Usuario;

@Entity
@Table(name = "precios_historial")
@Data
public class HistorialPrecio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoCatalogo producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    private BigDecimal precioNeto;
    private Boolean disponible;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    private LocalDateTime fecha;
}
JAVA

