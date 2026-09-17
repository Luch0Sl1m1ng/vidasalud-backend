# Backend VidaSalud — 3 microservicios listos para correr

Contiene:
- `ms-vidasalud-bff` (puerto 8080) — valida JWT y reenvía a los otros dos
- `ms-vidasalud-appointments` (puerto 8081) — CRUD + máquina de estados
- `ms-vidasalud-catalog` (puerto 8082) — CRUD de prestaciones/cupos

Vienen configurados con **H2 en memoria** para que corran YA, sin
necesidad de Oracle todavía. Cuando tengan la BD real en AWS, activan
el perfil `prod` (ver `application-prod.yml` en cada módulo).

## 1. Configurar las variables de Azure

Copia `.env.example` a `.env` y reemplaza con los datos reales de tu
App Registration (Parte A de la guía anterior):

```
AZURE_TENANT_ID=...
AZURE_CLIENT_ID=...
```

## 2. Correr cada servicio en local (sin Docker, para desarrollar)

En 3 terminales distintas, dentro de cada carpeta:

```bash
export AZURE_TENANT_ID=xxxx
export AZURE_CLIENT_ID=xxxx
mvn spring-boot:run
```

- appointments queda en `http://localhost:8081`
- catalog queda en `http://localhost:8082`
- bff queda en `http://localhost:8080` (y reenvía a los otros dos)

## 3. Correr todo junto con Docker Compose

```bash
docker-compose --env-file .env up --build
```

## 4. Probar sin token (debe dar 401)

```bash
curl -i http://localhost:8081/api/appointments
```

## 5. Conseguir un JWT real para probar con Postman

Como aún no tienen el frontend Angular corriendo, pueden generar un
token de prueba con **Postman**:

1. Nueva request → pestaña **Authorization** → tipo **OAuth 2.0**.
2. Grant type: **Authorization Code (PKCE)**.
3. Auth URL: `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/authorize`
4. Access Token URL: `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/token`
5. Client ID: tu `CLIENT_ID`.
6. Callback URL: `https://oauth.pstmn.io/v1/callback` (agrégala como
   Redirect URI tipo *SPA* o *Web* en tu App Registration).
7. Scope: `api://<CLIENT_ID>/access_as_user openid profile`.
8. **Get New Access Token** → inicia sesión con uno de tus usuarios de
   prueba (el que tiene rol Admin, por ejemplo) → copia el token.
9. Úsalo en el header `Authorization: Bearer <token>` contra
   `http://localhost:8081/api/appointments`.

Con eso ya tienes las 3 evidencias que pide la pauta: 401 sin token,
403 con rol incorrecto, 200 con token+rol correctos.

## 6. Endpoints disponibles

**Appointments**
- `POST /api/appointments` — crear
- `GET /api/appointments/{id}`
- `GET /api/appointments?status=CONFIRMADA`
- `PUT /api/appointments/{id}/status` — body `{"status": "CONFIRMADA"}`

**Catalog**
- `GET /api/catalog/services`
- `POST /api/catalog/services`
- `PUT /api/catalog/services/{id}`

## 7. Qué falta que ustedes agreguen (según cómo repartan el trabajo)

- `ms-vidasalud-notify` (consumer RabbitMQ) — no está en el alcance de
  EP1/EP2, es para el caso completo.
- `ms-vidasalud-report` y `ms-vidasalud-audit` (consumers Kafka) — ídem.
- Conectar `disminuirCupo()` de catalog con el cambio de estado a
  CONFIRMADA en appointments (hoy están separados; en el caso completo
  se comunican vía evento a Kafka/RabbitMQ, no llamada directa).
