package com.appdomotica.mantenimiento.service;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.mantenimiento.dto.MantenimientoDtos.*;
import com.appdomotica.mantenimiento.entity.CredencialAccesoLog;
import com.appdomotica.mantenimiento.entity.CredencialEquipo;
import com.appdomotica.mantenimiento.entity.EquipoInstalado;
import com.appdomotica.mantenimiento.repository.CredencialAccesoLogRepository;
import com.appdomotica.mantenimiento.repository.CredencialEquipoRepository;
import com.appdomotica.mantenimiento.repository.EquipoInstaladoRepository;
import com.appdomotica.proyectos.entity.EstadoProyecto;
import com.appdomotica.proyectos.entity.EstadoTarea;
import com.appdomotica.proyectos.entity.Proyecto;
import com.appdomotica.proyectos.repository.ChecklistTareaRepository;
import com.appdomotica.proyectos.repository.ProyectoRepository;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Equipos instalados y sus credenciales (RF-MAN-01..06).
 *
 * Las listas nunca devuelven contraseñas. Verla es una acción aparte que deja registro en la misma
 * transacción (si el registro falla, no se entrega). Nada se borra: equipos y credenciales se dan de baja.
 */
@Service
@RequiredArgsConstructor
public class EquiposService {
    private static final UUID NINGUNO = new UUID(0, 0);

    private final ProyectoRepository proyectos;
    private final EquipoInstaladoRepository equipos;
    private final CredencialEquipoRepository credenciales;
    private final CredencialAccesoLogRepository accesos;
    private final ProductoCatalogoRepository productos;
    private final ChecklistTareaRepository tareas;
    private final CifradoCredenciales cifrado;

    // ---------- consultas ----------

    @Transactional(readOnly = true)
    public EquiposProyecto deProyecto(UUID proyectoId) {
        return armar(proyecto(proyectoId));
    }

    /** Proyectos con equipos cuyo cliente, dirección, equipo, serie o MAC contiene el texto. */
    @Transactional(readOnly = true)
    public List<ProyectoConEquipos> buscar(String texto) {
        String buscado = normalizar(texto);
        // La MAC se encuentra escrita con dos puntos, guiones o sin separadores.
        String hexBuscado = buscado.replaceAll("[^0-9a-f]", "");
        List<EquipoInstalado> todos = equipos.activos();
        Map<UUID, Long> porEquipo = credenciales.activasDe(todos.stream().map(EquipoInstalado::getId).toList())
                .stream().collect(Collectors.groupingBy(c -> c.getEquipo().getId(), Collectors.counting()));
        Map<UUID, List<EquipoInstalado>> porProyecto = new LinkedHashMap<>();
        for (EquipoInstalado e : todos) {
            porProyecto.computeIfAbsent(e.getProyecto().getId(), k -> new ArrayList<>()).add(e);
        }
        List<ProyectoConEquipos> salida = new ArrayList<>();
        for (List<EquipoInstalado> lista : porProyecto.values()) {
            Proyecto p = lista.get(0).getProyecto();
            var cliente = p.getCotizacion().getCliente();
            boolean coincide = buscado.isEmpty()
                    || normalizar(cliente.getNombre()).contains(buscado)
                    || normalizar(cliente.getDireccion()).contains(buscado)
                    || lista.stream().anyMatch(e -> normalizar(e.getNombreReferencia()).contains(buscado)
                    || normalizar(e.getNumeroSerie()).contains(buscado)
                    || e.getDireccionIp() != null && buscado.contains(".") && e.getDireccionIp().contains(buscado)
                    || hexBuscado.length() >= 4 && e.getDireccionMac() != null
                    && e.getDireccionMac().replace(":", "").toLowerCase(Locale.ROOT).contains(hexBuscado));
            if (coincide) {
                salida.add(new ProyectoConEquipos(p.getId(), cliente.getNombre(), cliente.getDireccion(),
                        p.getEstado(), p.getFechaCierre(),
                        lista.stream().map(EquipoInstalado::getNombreReferencia).toList(),
                        (int) lista.stream().mapToLong(e -> porEquipo.getOrDefault(e.getId(), 0L)).sum()));
            }
        }
        salida.sort(Comparator.comparing(ProyectoConEquipos::clienteNombre, String.CASE_INSENSITIVE_ORDER));
        return salida;
    }

    @Transactional(readOnly = true)
    public List<Acceso> accesos(UUID credencialId) {
        credencial(credencialId);
        return accesos.deCredencial(credencialId).stream()
                .map(a -> new Acceso(a.getConsultadoPor().getNombre(), a.getFecha())).toList();
    }

    /** Ver la contraseña (RF-MAN-05/06): queda registrado quién y cuándo, sin excepción. */
    @Transactional
    public CredencialRevelada revelar(UUID credencialId, Usuario usuario) {
        CredencialEquipo c = credencial(credencialId);
        String contrasena = cifrado.descifrar(c.getId(), c.getContrasenaCifrada());
        accesos.saveAndFlush(CredencialAccesoLog.builder().credencial(c).consultadoPor(usuario).build());
        return new CredencialRevelada(c.getId(), contrasena);
    }

    // ---------- equipos ----------

    @Transactional
    public EquiposProyecto registrar(UUID proyectoId, EquipoRequest req, Usuario usuario) {
        Proyecto p = proyecto(proyectoId);
        EquipoInstalado e = EquipoInstalado.builder()
                .proyecto(p)
                .registradoPor(usuario)
                // Si el proyecto ya cerró, el equipo quedó instalado ese día (RF-MAN-02).
                .fechaInstalacion(p.getEstado() == EstadoProyecto.CERRADO ? p.getFechaCierre() : null)
                .build();
        aplicar(e, req);
        equipos.saveAndFlush(e);
        return armar(p);
    }

    @Transactional
    public EquiposProyecto editar(UUID equipoId, EquipoRequest req) {
        EquipoInstalado e = equipo(equipoId);
        versionIgual(req.version(), e.getVersion(), "el equipo");
        aplicar(e, req);
        e.setUpdatedAt(OffsetDateTime.now());
        equipos.saveAndFlush(e);
        return armar(e.getProyecto());
    }

    /** Baja del equipo y de sus credenciales; la bitácora de consultas se conserva. */
    @Transactional
    public EquiposProyecto retirar(UUID equipoId, Long version, Usuario usuario) {
        EquipoInstalado e = equipo(equipoId);
        versionIgual(version, e.getVersion(), "el equipo");
        for (CredencialEquipo c : credenciales.activasDe(List.of(e.getId()))) {
            darDeBaja(c, usuario);
        }
        e.setActivo(false);
        e.setUpdatedAt(OffsetDateTime.now());
        equipos.saveAndFlush(e);
        return armar(e.getProyecto());
    }

    // ---------- credenciales ----------

    @Transactional
    public EquiposProyecto agregarCredencial(UUID equipoId, CredencialRequest req, Usuario usuario) {
        EquipoInstalado e = equipo(equipoId);
        if (vacio(req.contrasena())) {
            throw new BadRequestException("Escribe la contraseña");
        }
        UUID id = UUID.randomUUID();
        CredencialEquipo c = CredencialEquipo.builder()
                .id(id)
                .equipo(e)
                .tipo(req.tipo())
                .usuario(limpio(req.usuario()))
                .notas(limpio(req.notas()))
                .contrasenaCifrada(cifrado.cifrar(id, req.contrasena()))
                .actualizadoPor(usuario)
                .build();
        credenciales.saveAndFlush(c);
        return armar(e.getProyecto());
    }

    @Transactional
    public EquiposProyecto editarCredencial(UUID credencialId, CredencialRequest req, Usuario usuario) {
        CredencialEquipo c = credencial(credencialId);
        versionIgual(req.version(), c.getVersion(), "la credencial");
        c.setTipo(req.tipo());
        c.setUsuario(limpio(req.usuario()));
        c.setNotas(limpio(req.notas()));
        if (!vacio(req.contrasena())) {
            c.setContrasenaCifrada(cifrado.cifrar(c.getId(), req.contrasena()));
        }
        c.setActualizadoPor(usuario);
        c.setFechaActualizacion(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        credenciales.saveAndFlush(c);
        return armar(c.getEquipo().getProyecto());
    }

    @Transactional
    public EquiposProyecto retirarCredencial(UUID credencialId, Long version, Usuario usuario) {
        CredencialEquipo c = credencial(credencialId);
        versionIgual(version, c.getVersion(), "la credencial");
        darDeBaja(c, usuario);
        credenciales.flush();
        return armar(c.getEquipo().getProyecto());
    }

    // ---------- apoyo ----------

    private void aplicar(EquipoInstalado e, EquipoRequest req) {
        UUID excepto = e.getId() == null ? NINGUNO : e.getId();
        ProductoCatalogo producto = null;
        if (req.productoId() != null) {
            producto = productos.findById(req.productoId())
                    .orElseThrow(() -> new BadRequestException("El producto no existe en el catálogo"));
        }
        String serie = limpio(req.numeroSerie());
        String mac = mac(req.direccionMac());
        if (mac != null && equipos.existsByDireccionMacAndActivoTrueAndIdNot(mac, excepto)) {
            throw new ConflictException("La MAC " + mac + " ya está registrada en otro equipo");
        }
        if (serie != null && producto != null && equipos.serieOcupada(producto.getId(), serie, excepto)) {
            throw new ConflictException("Ya hay un " + producto.getNombre() + " con la serie " + serie);
        }
        // "192.168.1.20/24" en el campo de la IP también vale, si el prefijo viene aparte vacío.
        String textoIp = req.direccionIp();
        Integer prefijo = req.prefijo();
        if (!vacio(textoIp) && textoIp.contains("/")) {
            String pegado = textoIp.substring(textoIp.indexOf('/') + 1).trim();
            textoIp = textoIp.substring(0, textoIp.indexOf('/'));
            if (prefijo == null && !pegado.isEmpty()) {
                prefijo = prefijo(pegado);
            }
        }
        String ip = ip(textoIp);
        if (prefijo != null && ip == null) {
            throw new BadRequestException("Escribe la IP del equipo para guardar el prefijo");
        }
        // Entre clientes las IP se repiten (redes privadas); dentro de un mismo proyecto, no.
        if (ip != null) {
            equipos.activosDe(e.getProyecto().getId()).stream()
                    .filter(otro -> ip.equals(otro.getDireccionIp()) && !otro.getId().equals(excepto))
                    .findFirst()
                    .ifPresent(otro -> {
                        throw new ConflictException("La IP " + ip + " ya la tiene " + otro.getNombreReferencia()
                                + " en este proyecto");
                    });
        }
        e.setNombreReferencia(req.nombreReferencia().trim());
        e.setProducto(producto);
        e.setNumeroSerie(serie);
        e.setDireccionMac(mac);
        e.setDireccionIp(ip);
        e.setPrefijo(prefijo);
    }

    private void darDeBaja(CredencialEquipo c, Usuario usuario) {
        c.setActivo(false);
        c.setActualizadoPor(usuario);
        c.setFechaActualizacion(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        credenciales.save(c);
    }

    private EquiposProyecto armar(Proyecto p) {
        var cliente = p.getCotizacion().getCliente();
        List<EquipoInstalado> lista = equipos.activosDe(p.getId());
        List<CredencialEquipo> creds = credenciales.activasDe(lista.stream().map(EquipoInstalado::getId).toList());
        Map<UUID, Long> consultas = new HashMap<>();
        if (!creds.isEmpty()) {
            for (Object[] fila : accesos.conteos(creds.stream().map(CredencialEquipo::getId).toList())) {
                consultas.put((UUID) fila[0], (Long) fila[1]);
            }
        }
        Map<UUID, List<Credencial>> porEquipo = new HashMap<>();
        for (CredencialEquipo c : creds) {
            porEquipo.computeIfAbsent(c.getEquipo().getId(), k -> new ArrayList<>()).add(new Credencial(
                    c.getId(), c.getTipo(), c.getUsuario(), c.getNotas(), c.getActualizadoPor().getNombre(),
                    c.getFechaActualizacion(), consultas.getOrDefault(c.getId(), 0L), c.getVersion()));
        }
        List<Equipo> salida = lista.stream().map(e -> new Equipo(e.getId(), e.getNombreReferencia(),
                e.getProducto() == null ? null : e.getProducto().getId(),
                e.getProducto() == null ? null : e.getProducto().getNombre(),
                e.getNumeroSerie(), e.getDireccionMac(), e.getDireccionIp(), e.getPrefijo(), e.getFechaInstalacion(),
                porEquipo.getOrDefault(e.getId(), List.of()), e.getVersion())).toList();
        // Productos del checklist para elegir al registrar (sin repetir ni los que "no aplican").
        Map<UUID, ProductoOpcion> opciones = new LinkedHashMap<>();
        tareas.vigentesDelProyecto(p.getId()).stream()
                .filter(t -> t.getProducto() != null && t.getEstado() != EstadoTarea.NO_APLICA)
                .forEach(t -> opciones.putIfAbsent(t.getProducto().getId(),
                        new ProductoOpcion(t.getProducto().getId(), t.getProducto().getNombre())));
        return new EquiposProyecto(p.getId(), cliente.getNombre(), cliente.getDireccion(), p.getEstado(),
                p.getFechaCierre(), salida, List.copyOf(opciones.values()));
    }

    private Proyecto proyecto(UUID id) {
        return proyectos.findById(id).orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado"));
    }

    private EquipoInstalado equipo(UUID id) {
        return equipos.findById(id).filter(EquipoInstalado::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo no encontrado"));
    }

    private CredencialEquipo credencial(UUID id) {
        return credenciales.findById(id).filter(CredencialEquipo::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Credencial no encontrada"));
    }

    private static void versionIgual(Long pedida, Long actual, String que) {
        if (pedida == null) {
            throw new BadRequestException("Falta la versión");
        }
        if (!pedida.equals(actual)) {
            throw new ConflictException("Alguien más modificó " + que + ". Recarga y vuelve a intentar.");
        }
    }

    /** AA:BB:CC:DD:EE:FF, venga como venga escrita (guiones, puntos, minúsculas). */
    static String mac(String texto) {
        if (vacio(texto)) {
            return null;
        }
        String hex = texto.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        if (hex.length() != 12 || texto.replaceAll("[0-9A-Fa-f:.\\-\\s]", "").length() > 0) {
            throw new BadRequestException("La MAC debe tener 12 dígitos hexadecimales (por ejemplo AA:BB:CC:11:22:33)");
        }
        return String.join(":", hex.split("(?<=\\G..)"));
    }

    /** IPv4 de cuatro números de 0 a 255; se guarda sin ceros a la izquierda (192.168.001.020 → 192.168.1.20). */
    static String ip(String texto) {
        if (vacio(texto)) {
            return null;
        }
        String[] partes = texto.trim().split("\\.", -1);
        if (partes.length != 4 || !Arrays.stream(partes).allMatch(n -> n.matches("\\d{1,3}") && Integer.parseInt(n) <= 255)) {
            throw new BadRequestException("La IP debe tener cuatro números de 0 a 255 (por ejemplo 192.168.1.20)");
        }
        return Arrays.stream(partes).map(n -> String.valueOf(Integer.parseInt(n))).collect(Collectors.joining("."));
    }

    private static int prefijo(String texto) {
        if (!texto.matches("\\d{1,2}") || Integer.parseInt(texto) > 32) {
            throw new BadRequestException("El prefijo va de 0 a 32");
        }
        return Integer.parseInt(texto);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String limpio(String s) {
        return vacio(s) ? null : s.trim();
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
