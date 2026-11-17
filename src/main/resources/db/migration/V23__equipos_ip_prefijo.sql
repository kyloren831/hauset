-- IP (IPv4) del equipo en la red del cliente y el prefijo de esa red, ej. 192.168.1.20 /24 (RF-MAN-01).
-- No es única entre proyectos: las redes privadas se repiten de un cliente a otro.

ALTER TABLE equipos_instalados
    ADD COLUMN direccion_ip VARCHAR(15),
    ADD COLUMN prefijo_red SMALLINT,
    ADD CONSTRAINT ck_equipos_prefijo CHECK (prefijo_red BETWEEN 0 AND 32),
    ADD CONSTRAINT ck_equipos_prefijo_con_ip CHECK (prefijo_red IS NULL OR direccion_ip IS NOT NULL);
