# Despliegue a producción (api.crhauset.com)

Arquitectura en la VPS (Ubuntu 24.04, ya endurecida con SOP-VPS-01 a 07):

```
Internet ──80/443──> Caddy (HTTPS automático) ──> 127.0.0.1:8080 backend (systemd, usuario hauset)
                                                        └──> 127.0.0.1:5432 Postgres 15 (Docker, sin root)
GitHub (push a master) ─> Actions: pruebas + jar ─1 conexión SSH (llave restringida)─> hauset-desplegar
```

- Postgres en Docker, publicado solo en `127.0.0.1:5432` (Docker se salta UFW) y sin root dentro del contenedor
  (usuario 999, sin capacidades, `no-new-privileges`). Nadie está en el grupo `docker`: se usa con sudo.
- Solo Caddy abre 80/443 (ya permitidos en UFW y hPanel). No hay que abrir puertos nuevos.
- La VPS no compila ni guarda credenciales de GitHub: Actions corre las pruebas, arma el jar y lo
  manda por **una sola** conexión SSH a un único comando permitido (`hauset-desplegar`), que respalda
  la base, cambia el jar, reinicia y vuelve a la versión anterior si el backend no responde.
- `deploy` solo puede correr `hauset-desplegar` sin contraseña; para todo lo demás, sudo sigue pidiéndola.

| Archivo | Va a | Para qué |
|---|---|---|
| `instalar.sh` | (se corre con sudo) | instala y configura todo; se puede repetir |
| `postgres.compose.yml` | `/opt/hauset/` | Postgres; clave en `/etc/hauset/postgres.env` (600) |
| `Caddyfile` | `/etc/caddy/Caddyfile` | HTTPS; quita `Forwarded` y `X-Forwarded-Prefix` del cliente |
| `hauset.env.example` | `/etc/hauset/hauset.env` (600) | entorno del backend con claves nuevas |
| `hauset.service` | `/etc/systemd/system/` | backend endurecido, con reinicio automático |
| `respaldo.sh` + `hauset-respaldo.*` | `/opt/hauset/`, `/etc/systemd/system/` | `pg_dump` y archivos locales cada día a las 03:30, 14 días |
| `hauset-desplegar` | `/usr/local/bin/` | recibe el jar desde GitHub Actions y lo pone en producción |
| `../../.github/workflows/backend.yml` | GitHub | pruebas en cada push; despliegue en cada push a `master` |

`instalar.sh` no toca `/etc/ssh`, UFW ni fail2ban. Si se repite, no regenera claves ni pisa `/etc/hauset/hauset.env`.

---

# SOP-VPS-08: DNS y TLS (Caddy)

**Objetivo:** que `api.crhauset.com` apunte a la VPS y tenga certificado HTTPS automático.

**Aplica a:** INF-001, APP-001

**Prerrequisitos:** puertos 80 y 443 abiertos (SOP-VPS-02 y 03, ya hechos).

**Riesgo y plan de recuperación:** si el DNS no apunta a la VPS, Let's Encrypt falla y Caddy reintenta solo; no afecta SSH.

**Pasos:**

1. hPanel → Dominios → `crhauset.com` → DNS / Nameservers → agregar registro **A**: nombre `api`, apunta a `179.197.93.79`, TTL 300.
   - No crees un **AAAA** para `api` salvo que apunte a la IPv6 de esta VPS; si ya existe uno con otro destino, bórralo (Let's Encrypt usa IPv6 si hay AAAA).
   - Si el dominio usa los DNS de Cloudflare y no los de Hostinger, el registro va en Cloudflare con la **nube gris** (*DNS only*).
2. Comprobar desde el PC (puede tardar unos minutos): `nslookup api.crhauset.com 1.1.1.1` → `179.197.93.79` (sin el `1.1.1.1`, algunos módems o proveedores responden *Query refused*: es el DNS de tu red, no el registro).
3. Caddy se instala y configura en SOP-API-01. Obtiene el certificado solo apenas el DNS resuelve.

**Verificación:** `sudo journalctl -u caddy -n 50 --no-pager | grep -i certificate` → `certificate obtained successfully`.

**Lecciones aprendidas:** Caddy renueva solo; no hace falta certbot ni cron.

---

# SOP-API-01: Instalación del backend

**Objetivo:** dejar listos Postgres, Caddy, el servicio del backend, sus claves y los respaldos.

**Aplica a:** INF-001, APP-001

**Prerrequisitos:** acceso `ssh deploy@179.197.93.79`; los archivos de esta carpeta ya en `master`.

**Riesgo y plan de recuperación:** no toca el acceso SSH ni el firewall. La llave `HAUSET_CREDENCIALES_LLAVE` es irrecuperable: si se pierde, las contraseñas de los equipos guardadas no se pueden leer.

**Pasos:**

1. Copiar esta carpeta a la VPS (una sola conexión, sin dar credenciales de GitHub a la VPS). Desde el PC, en la carpeta del repositorio actualizado:
   ```powershell
   scp -r deploy/produccion deploy@179.197.93.79:~/hauset-produccion
   ```
2. En la VPS (`ssh deploy@179.197.93.79`), instalar (pide tu contraseña de sudo una vez; tarda unos minutos):
   ```bash
   cd ~/hauset-produccion
   sudo ./instalar.sh
   ```
3. Guardar la llave de credenciales en tu gestor de contraseñas, fuera de la VPS y aparte de los respaldos:
   ```bash
   sudo grep HAUSET_CREDENCIALES_LLAVE /etc/hauset/hauset.env
   ```
4. Completar el entorno: `sudo nano /etc/hauset/hauset.env`
   - `HAUSET_PRIMER_JEFE_TELEFONO` (8 dígitos), `_NOMBRE` y `_PASSWORD` (8 a 72 caracteres).
   - Archivos: sin bucket todavía, cambia a `STORAGE_PROVEEDOR=DISCO_LOCAL`. Con R2, completa `R2_ACCOUNT_ID`, `R2_BUCKET`, `R2_ACCESS_KEY_ID` y `R2_SECRET_ACCESS_KEY` (bucket privado, token solo para ese bucket). Pasar a R2 después es cambiar esto y `sudo systemctl restart hauset`: los archivos se copian solos.
   - No toques `SPRING_DATASOURCE_PASSWORD`: Postgres ya se creó con esa clave (`/etc/hauset/postgres.env`).
5. Memoria: mira `free -h`. Con menos de 4 GB, baja `-Xmx1g` a `-Xmx512m` en `/etc/systemd/system/hauset.service` y corre `sudo systemctl daemon-reload`.

**Verificación:**
```bash
sudo docker ps                                    # postgres ... (healthy)
systemctl is-enabled docker caddy hauset hauset-respaldo.timer   # enabled x4
sudo ss -tlnp | grep -E ':5432|:8080|:80 |:443'   # 5432 solo en 127.0.0.1; 80/443 de caddy
sudo -l -U deploy | grep hauset-desplegar          # la única regla sin contraseña
```
El backend todavía no corre: arranca con el primer despliegue (SOP-API-02).

**Lecciones aprendidas:** al cambiar algún archivo de esta carpeta, vuelve a copiarla y repite `sudo ./instalar.sh`.

---

# SOP-API-02: Despliegue automático desde GitHub

**Objetivo:** cada push a `master` corre las pruebas y, si pasan, despliega el backend sin intervención.

**Aplica a:** INF-001, APP-001

**Prerrequisitos:** SOP-API-01; administrador del repositorio en GitHub.

**Riesgo y plan de recuperación:** quien tenga la llave de despliegue solo puede ejecutar `hauset-desplegar` (sin shell, sin reenvío de puertos ni agente). Para cortar el despliegue automático: borrar la variable `DESPLIEGUE_ACTIVO` en GitHub o la línea de la llave en `authorized_keys`. Una llave mal configurada cuenta como intento fallido para fail2ban; se prueba una sola vez desde el PC (paso 3).

**Pasos:**

1. En el PC, una llave solo para GitHub Actions. Cuando pida frase, Enter dos veces (Actions no puede escribirla):
   ```powershell
   ssh-keygen -t ed25519 -f hauset-despliegue -C github-actions-hauset
   ```
2. En la VPS, autorizarla **restringida al comando de despliegue**. Agrega una línea al final de `~/.ssh/authorized_keys` (`nano ~/.ssh/authorized_keys`), pegando al final el contenido de `hauset-despliegue.pub`. No borres tu llave personal:
   ```
   restrict,command="sudo /usr/local/bin/hauset-desplegar \"$SSH_ORIGINAL_COMMAND\"" ssh-ed25519 AAAA...resto... github-actions-hauset
   ```
3. Probar desde el PC (una vez) que la llave está encerrada:
   ```powershell
   ssh -i hauset-despliegue -o IdentitiesOnly=yes deploy@179.197.93.79 hola
   # → "Uso: hauset-desplegar <sha256> < jar"   (nunca una shell)
   ```
4. Huella del servidor, para que Actions no acepte un servidor falso:
   ```powershell
   ssh-keyscan -t ed25519 179.197.93.79 > known_hosts_vps
   ssh-keygen -lf known_hosts_vps
   ```
   Debe coincidir con la de la VPS: `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`.
5. En GitHub → repositorio `hauset` → Settings:
   - **Environments → New environment** `produccion`, con tres *Environment secrets*:
     - `VPS_HOST` = `179.197.93.79`
     - `VPS_SSH_KEY` = contenido completo de `hauset-despliegue` (la privada, con las líneas BEGIN/END)
     - `VPS_KNOWN_HOSTS` = contenido de `known_hosts_vps`
   - En ese entorno, **Deployment branches** → solo `master`. Opcional: *Required reviewers* para aprobar cada despliegue con un clic.
   - **Secrets and variables → Actions → Variables → New repository variable**: `DESPLIEGUE_ACTIVO` = `true`.
   - Recomendado: **Branches → Add rule** para `master` (exigir que pase *pruebas*).
6. Guarda `hauset-despliegue` en tu gestor de contraseñas y bórralo del PC.
7. Primer despliegue: GitHub → Actions → **Backend** → *Run workflow* (rama `master`), o cualquier push a `master`.
8. Cuando el backend esté arriba, entra a la app con el primer jefe, crea las demás cuentas, borra las tres líneas `HAUSET_PRIMER_JEFE_*` de `/etc/hauset/hauset.env` y `sudo systemctl restart hauset`.

**Verificación:** el job *desplegar* termina con `Backend arriba con la versión …`; desde el PC `https://api.crhauset.com/actuator/health` → `{"status":"UP"}`; en la VPS `sudo ls /var/backups/hauset` muestra el respaldo previo al despliegue.

**Lecciones aprendidas:** el jar viaja con su SHA-256 y se rechaza si llega incompleto o no es el backend de Hauset; un despliegue a la vez; cada despliegue es una sola conexión SSH (no dispara `ufw limit`).

---

# SOP-API-03: Operación, vuelta atrás y respaldos

**Objetivo:** revisar, revertir y respaldar el backend.

**Aplica a:** INF-001, APP-001

**Pasos:**

- Registros: `sudo journalctl -u hauset -f` · Estado: `systemctl status hauset`
- Si una versión nueva no responde en 3 minutos, `hauset-desplegar` vuelve sola a la anterior y el job queda en rojo.
- Volver atrás a mano: `sudo cp /opt/hauset/hauset.jar.anterior /opt/hauset/hauset.jar && sudo systemctl restart hauset`. Si la versión mala alcanzó a migrar la base, restaura también el respaldo previo.
- Redesplegar una versión vieja: GitHub → Actions → la ejecución de esa versión → *Re-run jobs* (los jar se guardan 14 días).
- Respaldos en `/var/backups/hauset/` (diario 03:30 y antes de cada despliegue). Copiarlos fuera de la VPS es SOP-VPS-09 (restic/borg); con R2, respalda también el bucket. La llave `HAUSET_CREDENCIALES_LLAVE` nunca va junto a los respaldos.
- Restaurar (sobre una base vacía):
  `gunzip -c /var/backups/hauset/base-….sql.gz | sudo docker compose -f /opt/hauset/postgres.compose.yml --env-file /etc/hauset/postgres.env exec -T postgres psql -U hauset -d app_domotica`
- Reinicio por kernel (SOP-VPS-06): antes, `systemctl is-enabled docker caddy hauset` debe decir `enabled` en los tres (el contenedor vuelve solo con `restart: unless-stopped`); después, comprobar `/actuator/health`.
