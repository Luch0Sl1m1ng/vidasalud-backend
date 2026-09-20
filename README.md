# VidaSalud - Backend

Proyecto DSY1107 (Desarrollo Cloud Native I), caso VidaSalud. Este documento cubre unicamente la parte de backend: los tres microservicios, la configuracion de autenticacion con Azure Entra ID, el despliegue en Docker y la publicacion en AWS mediante API Gateway. El frontend (Angular) se desarrolla y documenta por separado.

Este documento esta pensado para que cualquier persona, sin conocimiento previo de este proyecto especifico, pueda instalar, ejecutar, probar y desplegar el backend completo siguiendo los pasos en orden.

## Indice

1. Descripcion general y arquitectura
2. Estructura del repositorio
3. Requisitos previos
4. Configuracion de Azure Entra ID
5. Clonar el proyecto
6. Ejecutar los microservicios en local
7. Probar el backend con un token real
8. Despliegue con Docker
9. Despliegue en AWS EC2
10. Configuracion del API Gateway
11. Referencia de endpoints
12. Problemas frecuentes y solucion

---

## 1. Descripcion general y arquitectura

El backend esta compuesto por tres microservicios Spring Boot independientes:

- ms-vidasalud-bff: puerto 8080. Es el punto de entrada. Valida el token JWT emitido por Azure Entra ID y reenvia la peticion al microservicio correspondiente.
- ms-vidasalud-appointments: puerto 8081. Administra las atenciones (crear, consultar, cambiar de estado). Valida el JWT de forma independiente.
- ms-vidasalud-catalog: puerto 8082. Administra el catalogo de prestaciones, boxes y cupos. Valida el JWT de forma independiente.

El flujo de una peticion real es el siguiente:

1. El usuario inicia sesion en Azure Entra ID (a traves del frontend Angular, o de una herramienta como Postman durante las pruebas) y obtiene un token JWT.
2. El cliente envia el token en el encabezado Authorization de cada peticion, con el formato: Authorization: Bearer <token>.
3. La peticion llega al API Gateway de AWS, que valida el token contra Azure (issuer, audience y firma) antes de dejarla pasar.
4. El API Gateway reenvia la peticion al BFF (ms-vidasalud-bff).
5. El BFF vuelve a validar el token y reenvia la peticion al microservicio de dominio correspondiente (appointments o catalog).
6. El microservicio de dominio valida el token una tercera vez, revisa el rol del usuario segun la operacion solicitada, ejecuta la logica de negocio y responde.

Esta validacion en tres capas (Gateway, BFF, microservicio) es intencional y forma parte de lo que exige el caso.

Los roles definidos en Azure son cuatro: Admin, Operador, Paciente y Auditor. El backend usa estos roles para decidir que operaciones puede hacer cada usuario. Por ejemplo, cambiar el estado de una atencion requiere el rol Admin u Operador; crear o editar el catalogo requiere el rol Admin.

## 2. Estructura del repositorio

Al clonar el repositorio se encuentran las siguientes carpetas y archivos en la raiz:

```
vidasalud-backend/
  ms-vidasalud-bff/
  ms-vidasalud-appointments/
  ms-vidasalud-catalog/
  docker-compose.yml
  README.md
```

Cada carpeta de microservicio es un proyecto Maven independiente, con esta estructura interna:

```
ms-vidasalud-appointments/
  src/main/java/com/vidasalud/appointments/
    config/          (seguridad y validacion de JWT)
    controller/       (endpoints REST)
    dto/              (objetos de entrada y salida)
    exception/        (manejo de errores)
    model/            (entidades JPA)
    repository/        (acceso a datos)
    service/           (logica de negocio)
    AppointmentsApplication.java
  src/main/resources/
    application.yml         (configuracion para ejecucion local, con base de datos H2)
    application-prod.yml     (configuracion para produccion, con base de datos Oracle)
  Dockerfile
  pom.xml
```

Las carpetas ms-vidasalud-bff y ms-vidasalud-catalog siguen la misma logica, adaptada a su funcion.

## 3. Requisitos previos

Antes de empezar, es necesario tener instalado en el computador:

- Java Development Kit version 21. Se puede descargar desde adoptium.net, eligiendo la version 21 (LTS). Para confirmar que quedo instalado, abrir una terminal y ejecutar: java -version. Debe mostrar un numero que empiece con 21.
- Un entorno de desarrollo para Java. Se recomienda IntelliJ IDEA, en su version Community (gratuita), disponible en jetbrains.com/idea/download. No es obligatorio usar IntelliJ, pero estas instrucciones asumen su uso en los pasos de ejecucion local.
- Git, para clonar el repositorio y subir cambios. En Windows se instala desde git-scm.com. En Mac, con el comando brew install git. En distribuciones basadas en Debian o Ubuntu, con sudo apt install git.
- Postman, para probar los endpoints de forma manual. Se descarga desde postman.com/downloads.
- Docker, para construir y ejecutar los tres microservicios de forma empaquetada. Se instala desde docker.com, o directamente en el servidor Linux donde se vaya a desplegar (ver seccion 9).
- Una cuenta con acceso a Azure Entra ID (portal.azure.com) y una cuenta con acceso a AWS (aws.amazon.com), si se va a repetir el proceso de despliegue completo.

## 4. Configuracion de Azure Entra ID

Azure Entra ID actua como el proveedor de identidad (IDaaS) del sistema. Es quien autentica a los usuarios y emite los tokens JWT que el backend valida. Esta seccion explica como configurarlo desde cero. Si el tenant y la aplicacion ya existen (por ejemplo, porque otro integrante del equipo ya los creo), se puede saltar directamente al punto 4.6 para obtener los datos necesarios.

### 4.1 Crear el tenant

1. Entrar a portal.azure.com e iniciar sesion.
2. Buscar "Microsoft Entra ID" en la barra de busqueda superior y entrar al servicio.
3. En el menu izquierdo, ir a Administrar tenants, y hacer clic en Crear.
4. Elegir el tipo Microsoft Entra ID (no Azure AD B2C).
5. Completar nombre de la organizacion, nombre de dominio inicial (por ejemplo, vidasalud, que resultara en vidasalud.onmicrosoft.com) y pais o region.
6. Confirmar la creacion y esperar a que el tenant quede disponible (puede tardar hasta un par de minutos).
7. Cambiarse al tenant recien creado desde el menu de perfil, arriba a la derecha.

### 4.2 Registrar la aplicacion

1. Dentro del tenant correcto, entrar a Microsoft Entra ID, y en el menu izquierdo ir a Registros de aplicaciones.
2. Hacer clic en Nuevo registro.
3. Asignar un nombre (por ejemplo, vidasalud).
4. En tipos de cuenta admitidos, dejar seleccionado "Solo este directorio organizativo" (single tenant).
5. En URI de redireccion, seleccionar el tipo SPA (aplicacion de pagina unica) e ingresar http://localhost:4200 (el puerto por defecto del frontend Angular en desarrollo). Este paso es para el frontend, pero conviene dejarlo configurado desde ahora.
6. Registrar la aplicacion.
7. En la pantalla de resumen que aparece, copiar el valor de Application (client) ID y el Directory (tenant) ID. Estos dos valores se usan en todo el resto de la configuracion, tanto en el backend como en el frontend.

### 4.3 Exponer la API

1. Dentro del registro de la aplicacion, ir a Exponer una API.
2. Hacer clic en Agregar junto a URI de identificador de la aplicacion, y aceptar el valor sugerido, que tiene la forma api://<client-id>.
3. Hacer clic en Agregar un ambito.
4. Nombre del ambito: access_as_user.
5. Quien puede dar el consentimiento: Administradores y usuarios.
6. Completar los campos de nombre y descripcion del consentimiento (el texto exacto no es relevante para el funcionamiento).
7. Guardar el ambito.

### 4.4 Crear los roles de aplicacion

1. En el menu izquierdo de la aplicacion, ir a Roles de aplicacion.
2. Crear cuatro roles, uno por uno, con el boton Crear rol de aplicacion. En cada uno, el campo Valor es el mas importante, porque es el texto que aparecera dentro del token JWT bajo el campo roles. Los cuatro roles a crear son:
   - Nombre: Admin. Valor: Admin.
   - Nombre: Operador. Valor: Operador.
   - Nombre: Paciente. Valor: Paciente.
   - Nombre: Auditor. Valor: Auditor.
3. En cada uno, el tipo de miembro permitido debe ser Usuarios o grupos, y el estado debe quedar habilitado.

### 4.5 Crear usuarios de prueba y asignar roles

1. Salir del registro de la aplicacion y, dentro del tenant, ir a Usuarios.
2. Crear un usuario nuevo por cada rol que se quiera probar. Por ejemplo: admin@<dominio>.onmicrosoft.com, operador@<dominio>.onmicrosoft.com, paciente@<dominio>.onmicrosoft.com, auditor@<dominio>.onmicrosoft.com. Anotar la contrasena generada para cada uno.
3. Ir a Identidad, luego Aplicaciones, luego Aplicaciones empresariales, y buscar la aplicacion registrada en el paso 4.2 (deberia aparecer automaticamente ahi).
4. Dentro de esa aplicacion empresarial, ir a Usuarios y grupos, y hacer clic en Agregar usuario o grupo.
5. Seleccionar un usuario, elegir el rol correspondiente en el desplegable, y asignar. Repetir para cada usuario.

### 4.6 Datos necesarios para el backend

Al terminar esta seccion se debe contar con los siguientes datos, que se usan en el resto del documento:

- Tenant ID (Directory ID): un identificador con formato de UUID.
- Client ID (Application ID): otro identificador con formato de UUID.
- Issuer: se construye como https://login.microsoftonline.com/<tenant-id>/v2.0
- Audience: es directamente el Client ID (sin prefijo api://, a pesar de que en el portal de Azure el URI de identificador de la aplicacion se muestre con ese prefijo; el token que Azure emite trae el audience sin ese prefijo).

## 5. Clonar el proyecto

En una terminal, ejecutar:

```
git clone https://github.com/Luch0Sl1m1ng/vidasalud-backend.git
cd vidasalud-backend
```

Esto descarga los tres microservicios en la carpeta actual.

## 6. Ejecutar los microservicios en local

Esta seccion explica como correr el backend directamente en el computador, sin Docker, usando IntelliJ. Es la forma mas simple de revisar o modificar el codigo mientras se desarrolla.

Cada microservicio esta configurado para usar una base de datos H2 en memoria, lo que quiere decir que no requiere instalar ningun motor de base de datos aparte, pero tambien que los datos se pierden cada vez que el microservicio se reinicia. Para produccion existe un perfil alternativo con Oracle, explicado en la seccion 9.

### 6.1 Configurar los valores de Azure en el codigo

Dentro de cada uno de los tres microservicios existe un archivo en la ruta src/main/resources/application.yml. En ese archivo hay dos lineas que deben contener los valores reales obtenidos en la seccion 4.6:

```
azure:
  issuer: https://login.microsoftonline.com/<tenant-id>/v2.0
  audience: <client-id>
```

Reemplazar <tenant-id> y <client-id> por los valores reales en los tres archivos (bff, appointments y catalog). Estos valores no son secretos (no son contrasenas ni claves privadas), por lo que no hay problema en que queden escritos en el codigo y se suban al repositorio.

### 6.2 Abrir y ejecutar cada microservicio

1. Abrir IntelliJ IDEA.
2. Usar Open y seleccionar la carpeta ms-vidasalud-appointments (una subcarpeta dentro de vidasalud-backend, no la carpeta raiz completa).
3. Esperar a que IntelliJ descargue automaticamente las dependencias del proyecto (Maven). Esto requiere conexion a internet y puede tardar varios minutos la primera vez.
4. Ubicar el archivo AppointmentsApplication.java, dentro de src/main/java/com/vidasalud/appointments.
5. Ejecutar la clase con el boton de flecha verde que aparece junto a la definicion de la clase, o usando el menu Run.
6. Esperar a que en la consola aparezca la linea Started AppointmentsApplication. Esto confirma que el servicio quedo activo en el puerto 8081.
7. Repetir el mismo proceso abriendo por separado las carpetas ms-vidasalud-catalog (que arranca en el puerto 8082) y ms-vidasalud-bff (que arranca en el puerto 8080). Se pueden tener las tres ventanas de IntelliJ abiertas y los tres servicios corriendo al mismo tiempo, cosa que es necesaria para que el bff pueda reenviar peticiones a los otros dos.

Si al ejecutar aparece un error indicando que el JDK del proyecto no esta definido, se debe indicar manualmente donde quedo instalado: en el mensaje de advertencia que aparece en la parte superior del editor, hacer clic en Setup SDK y seleccionar el JDK 21 instalado en el paso 3 de la seccion de requisitos previos.

Si en cambio aparece un error de arranque indicando "Unable to resolve the Configuration with the provided Issuer", significa que el valor de issuer en application.yml quedo con un texto de ejemplo (como CAMBIAR_TENANT_ID) en lugar del tenant real de Azure. Revisar el punto 6.1.

## 7. Probar el backend con un token real

Los endpoints protegidos no aceptan peticiones sin un token JWT valido. Para probarlos manualmente con Postman, es necesario obtener un token siguiendo el flujo de autenticacion de Azure. El backend no tiene un endpoint propio de login: el login siempre ocurre contra Azure directamente.

### 7.1 Registrar Postman como cliente autorizado en Azure

1. Volver al registro de la aplicacion en Azure (seccion 4.2), ir a Autenticacion.
2. Hacer clic en Agregar una plataforma y elegir Aplicaciones moviles y de escritorio (no Web ni SPA; estas dos ultimas tienen restricciones que impiden el flujo desde una herramienta como Postman).
3. Agregar como URI de redireccion: https://oauth.pstmn.io/v1/callback
4. Guardar.

### 7.2 Verificar la version del token

Dentro del registro de la aplicacion, ir a Manifiesto (en algunas versiones del portal aparece como "Manifiesto de aplicacion de Microsoft Graph"). Buscar el campo requestedAccessTokenVersion (en versiones antiguas del portal puede llamarse accessTokenAcceptedVersion) y confirmar que su valor sea 2, no null ni 1. Si no lo es, cambiarlo a 2 y guardar. Esto es necesario porque el backend espera tokens en formato version 2.0 (con issuer bajo login.microsoftonline.com/.../v2.0); si este valor queda en su version anterior, Azure emite tokens en un formato distinto que el backend rechaza.

### 7.3 Obtener un token en Postman

1. Crear una peticion nueva en Postman, con el metodo y la URL del endpoint que se quiera probar (por ejemplo, GET http://localhost:8081/api/appointments).
2. Ir a la pestana Authorization y elegir el tipo OAuth 2.0.
3. Completar los siguientes campos:
   - Grant Type: Authorization Code (With PKCE)
   - Callback URL: https://oauth.pstmn.io/v1/callback
   - Auth URL: https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/authorize
   - Access Token URL: https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/token
   - Client ID: <client-id>
   - Scope: api://<client-id>/access_as_user openid profile
   - Client Authentication: Send client credentials in body
4. Hacer clic en Get New Access Token. Se abrira una ventana de inicio de sesion de Microsoft. Iniciar sesion con uno de los usuarios de prueba creados en la seccion 4.5.
5. Al completar el inicio de sesion, Postman mostrara el token obtenido. Hacer clic en Use Token para aplicarlo a la peticion actual.
6. Enviar la peticion.

Los tokens de Azure tienen una duracion aproximada de una hora. Pasado ese tiempo, es necesario repetir el paso 4 para obtener uno nuevo.

### 7.4 Resultados esperados segun el caso

- Sin token: la respuesta debe ser 401 Unauthorized.
- Con un token valido, pero de un usuario cuyo rol no tiene permiso para la operacion solicitada (por ejemplo, un usuario con rol Paciente intentando cambiar el estado de una atencion): la respuesta debe ser 403 Forbidden.
- Con un token valido y un rol con permiso suficiente: la respuesta debe ser 200 OK (o 201 Created, en el caso de una creacion), con el cuerpo correspondiente.

## 8. Despliegue con Docker

Cada microservicio incluye un Dockerfile, y en la raiz del repositorio hay un archivo docker-compose.yml que construye y ejecuta los tres en conjunto.

Para levantar los tres microservicios con Docker, ubicarse en la carpeta raiz del repositorio y ejecutar:

```
docker compose up --build
```

En algunos entornos, especialmente en instancias Linux recientes de AWS, este comando puede fallar con un error relacionado a "buildx". En ese caso, usar en su lugar:

```
DOCKER_BUILDKIT=0 docker compose up --build
```

Para ejecutarlo en segundo plano (sin bloquear la terminal), agregar la opcion -d:

```
docker compose up -d --build
```

Para revisar el estado de los contenedores:

```
docker compose ps
```

Para revisar los registros (logs) de un servicio en particular, por ejemplo el bff:

```
docker compose logs bff --tail=50
```

Para reconstruir y reiniciar un unico servicio despues de un cambio de codigo, sin tocar los otros dos:

```
docker compose up -d --build bff
```

Dentro de la red interna que crea Docker Compose, cada contenedor puede llamar a los otros usando el nombre del servicio como si fuera un nombre de dominio (por ejemplo, appointments o catalog), en vez de localhost. Esto ya esta configurado en el archivo docker-compose.yml mediante las variables de entorno APPOINTMENTS_URL y CATALOG_URL que recibe el contenedor del bff.

## 9. Despliegue en AWS EC2

Esta seccion explica como desplegar el backend en una instancia EC2 de AWS, de forma que quede accesible desde internet.

### 9.1 Crear la instancia

1. En la consola de AWS, entrar al servicio EC2.
2. Ir a Instances y hacer clic en Launch instances.
3. Asignar un nombre a la instancia.
4. En Application and OS Images, dejar Amazon Linux (version 2023).
5. En Instance type, elegir al menos t3.small. No se recomienda t3.micro: correr tres aplicaciones Spring Boot simultaneamente mediante Docker consume mas de 1 GB de memoria RAM, que es el limite de una instancia t3.micro, y esto provoca que la instancia se vuelva lenta o deje de responder incluso por SSH.
6. En Key pair, crear un par de llaves nuevo, asignarle un nombre, y descargar el archivo generado. Este archivo es necesario para conectarse por SSH desde una terminal local; si solo se va a usar EC2 Instance Connect (ver punto siguiente), no es estrictamente necesario, pero se recomienda igual tenerlo guardado.
7. En Network settings, permitir trafico SSH desde cualquier origen (o restringido, segun la politica del curso).
8. Lanzar la instancia y esperar a que su estado pase a "En ejecucion".
9. Anotar la direccion IPv4 publica de la instancia, visible en la pestana de detalles.

### 9.2 Conectarse a la instancia

La forma mas simple, sin instalar nada adicional, es usar EC2 Instance Connect desde el navegador:

1. Seleccionar la instancia en la consola de AWS.
2. Hacer clic en Conectar.
3. Elegir la pestana EC2 Instance Connect y hacer clic en Conectar. Se abrira una terminal dentro del navegador, conectada directamente a la instancia.

Si aparece un error indicando que el puerto 22 no esta autorizado, es necesario editar las reglas de entrada del grupo de seguridad asociado a la instancia (pestana Seguridad de la instancia, luego el enlace al grupo de seguridad, luego Editar reglas de entrada) y agregar una regla de tipo SSH, puerto 22, con el origen que indique el mensaje de error (normalmente un rango de IP especifico del servicio EC2 Instance Connect para esa region).

### 9.3 Instalar Docker y Docker Compose

Dentro de la terminal conectada a la instancia, ejecutar en orden:

```
sudo dnf update -y
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
```

Despues del ultimo comando es necesario cerrar la sesion y volver a conectarse (repetir el paso 9.2) para que el cambio de permisos tome efecto.

Luego instalar Docker Compose:

```
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

Verificar con:

```
docker --version
docker-compose --version
```

### 9.4 Instalar git y clonar el repositorio

```
sudo dnf install -y git
git clone https://github.com/Luch0Sl1m1ng/vidasalud-backend.git
cd vidasalud-backend
```

### 9.5 Construir y levantar los contenedores

```
DOCKER_BUILDKIT=0 docker-compose up -d --build
```

Este proceso puede tardar varios minutos la primera vez, ya que descarga las imagenes base de Java y Maven y compila los tres proyectos. Al finalizar, confirmar que los tres contenedores esten activos:

```
docker-compose ps
```

Debe mostrar los tres servicios (bff, appointments, catalog) con estado Up.

Para probar rapidamente desde dentro de la propia instancia, sin depender de reglas de firewall todavia:

```
curl http://localhost:8081/api/ping
curl http://localhost:8082/api/ping
curl http://localhost:8080/api/ping
```

Cualquiera de los tres deberia responder 401 Unauthorized (no 404 ni connection refused), ya que /api/ping exige un token valido igual que el resto de los endpoints; un 401 en este punto en realidad confirma que el servicio esta arriba y respondiendo.

### 9.6 Abrir los puertos necesarios

Por defecto, el grupo de seguridad de la instancia solo permite trafico SSH. Para poder probar el backend desde afuera (por ejemplo, desde Postman en el computador local, o desde el API Gateway), es necesario abrir los puertos 8080, 8081 y 8082:

1. Ir a la pestana Seguridad de la instancia, hacer clic en el grupo de seguridad.
2. Editar reglas de entrada.
3. Agregar tres reglas de tipo Custom TCP, con los puertos 8080, 8081 y 8082 respectivamente, y como origen 0.0.0.0/0 (cualquier IP) o una IP especifica, segun el nivel de seguridad deseado.
4. Guardar.

Con esto, el backend deberia quedar accesible en http://<ip-publica-de-la-instancia>:8081/api/appointments, y de forma equivalente en los puertos 8080 y 8082.

### 9.7 Nota sobre instancias detenidas

Si la instancia EC2 se detiene y se vuelve a iniciar (no simplemente se reinicia, sino que se detiene por completo y luego se inicia), la direccion IP publica cambia. Esto obliga a actualizar cualquier configuracion externa que dependa de esa IP, en particular las integraciones del API Gateway explicadas en la seccion siguiente.

Si se trabaja dentro de un laboratorio academico de AWS (por ejemplo, AWS Academy Learner Lab, identificable porque la consola muestra un usuario con el formato voclabs/user...), es importante considerar que estos laboratorios suelen tener una duracion de sesion limitada y pueden reiniciarse o eliminarse automaticamente al finalizar, borrando todo lo configurado en AWS (la instancia, el API Gateway, etc.). El codigo fuente en GitHub y la configuracion en Azure Entra ID no se ven afectados por esto, pero toda la configuracion realizada directamente en la consola de AWS deberia volver a documentarse o repetirse si esto ocurre. Conviene verificar con el equipo docente la duracion y politica de reinicio del laboratorio que se este usando.

## 10. Configuracion del API Gateway

El API Gateway es el punto de entrada publico definitivo del backend. Es quien primero valida el token JWT (antes de que la peticion llegue siquiera a la instancia EC2), y luego reenvia la peticion hacia el BFF.

### 10.1 Crear la API

1. En la consola de AWS, entrar al servicio API Gateway.
2. Hacer clic en Crear API, y elegir el tipo HTTP API (no REST API).
3. Asignar un nombre (por ejemplo, vidasalud-api) y crear la API sin agregar integraciones todavia.

### 10.2 Crear el autorizador JWT

1. Dentro de la API, ir a Autorizacion (Authorizers), y crear uno nuevo.
2. Tipo: JWT.
3. Nombre: por ejemplo, entra-id-authorizer.
4. Origen de identidad: $request.header.Authorization
5. URL del emisor (Issuer): https://login.microsoftonline.com/<tenant-id>/v2.0
6. Audiencia: <client-id> (el mismo valor usado en el backend, sin el prefijo api://)
7. Crear el autorizador.

### 10.3 Crear las rutas

Es necesario crear varias rutas, porque el comodin de captura de API Gateway ({proxy+}) exige que exista al menos un segmento adicional despues del prefijo; no cubre la ruta exacta sin nada mas al final. Por eso se crean rutas separadas para el caso exacto y para el caso con parametros adicionales.

Crear las siguientes rutas, todas con metodo ANY:

- /api/appointments
- /api/appointments/{proxy+}
- /api/catalog/{proxy+}

### 10.4 Crear las integraciones

Cada ruta necesita una integracion que la conecte con el backend real. Todas las integraciones apuntan al mismo servicio, el bff, en el puerto 8080, porque es el bff quien despues reenvia internamente a appointments o catalog segun corresponda.

Para cada ruta, crear una integracion de tipo URI del HTTP, con metodo ANY, y la siguiente URL:

- Ruta /api/appointments: http://<ip-publica-de-ec2>:8080/api/appointments
- Ruta /api/appointments/{proxy+}: http://<ip-publica-de-ec2>:8080/api/appointments/{proxy}
- Ruta /api/catalog/{proxy+}: http://<ip-publica-de-ec2>:8080/api/catalog/{proxy}

Es importante escribir estas URL a mano en el campo correspondiente en vez de pegarlas, ya que en algunas ocasiones el copiar y pegar introduce caracteres invisibles que la consola de AWS rechaza con el mensaje "Invalid HTTP endpoint specified for URI".

### 10.5 Adjuntar el autorizador a las rutas

Para cada una de las tres rutas creadas en el punto 10.3, ir a la seccion de autorizacion de esa ruta especifica y adjuntar el autorizador creado en el punto 10.2. Sin este paso, el Gateway dejaria pasar peticiones sin token.

### 10.6 Configurar CORS

1. Ir a la seccion CORS de la API.
2. Access-Control-Allow-Origin: agregar http://localhost:4200 (y, mas adelante, el dominio donde se publique el frontend en produccion).
3. Access-Control-Allow-Headers: agregar Authorization y Content-Type.
4. Access-Control-Allow-Methods: seleccionar GET, POST, PUT, DELETE, OPTIONS.
5. Guardar.

### 10.7 Crear una etapa y desplegar

1. Ir a Implementacion, luego Etapas, y crear una etapa nueva (por ejemplo, dev), con la opcion de implementacion automatica activada.
2. Al crear la etapa, la consola mostrara la URL de invocacion definitiva, con un formato similar a: https://<identificador>.execute-api.<region>.amazonaws.com/<nombre-etapa>

Esta URL es la que debe usar el frontend (y cualquier cliente externo) para consumir el backend. Nunca se debe usar directamente la IP de la instancia EC2 como URL definitiva para el frontend, porque ademas de no pasar por la validacion del Gateway, esa IP puede cambiar si la instancia se detiene y se reinicia.

### 10.8 Actualizar la IP si la instancia cambia

Si la instancia EC2 se detiene y se inicia de nuevo, y su IP publica cambia (ver seccion 9.7), es necesario volver a la seccion 10.4 y actualizar las tres integraciones con la nueva IP. La URL del API Gateway en si misma no cambia; solo cambia la direccion interna a la que el Gateway reenvia las peticiones.

## 11. Referencia de endpoints

Todos los endpoints requieren un encabezado Authorization con un token JWT valido, salvo donde se indique lo contrario. Las rutas se describen relativas a la URL base (ya sea http://localhost:8080 en ejecucion local a traves del bff, o la URL del API Gateway en produccion).

### Atenciones (ms-vidasalud-appointments)

POST /api/appointments
Crea una nueva atencion. Cuerpo esperado (JSON):
```
{
  "pacienteId": "texto",
  "servicioId": numero,
  "boxId": numero
}
```
Responde 201 Created con la atencion creada, en estado SOLICITADA.

GET /api/appointments/{id}
Devuelve una atencion por su identificador. Responde 404 si no existe.

GET /api/appointments
Devuelve todas las atenciones. Acepta el parametro opcional status para filtrar por estado (por ejemplo, /api/appointments?status=CONFIRMADA).

PUT /api/appointments/{id}/status
Cambia el estado de una atencion. Requiere rol Admin u Operador. Cuerpo esperado:
```
{
  "status": "CONFIRMADA"
}
```
Los valores posibles de status son: SOLICITADA, CONFIRMADA, EN_ESPERA, EN_ATENCION, CERRADA, CANCELADA. Las transiciones siguen un orden obligatorio: no se puede pasar directamente a EN_ATENCION sin haber pasado antes por CONFIRMADA y EN_ESPERA. Un intento de transicion invalida responde 409 Conflict.

### Catalogo (ms-vidasalud-catalog)

GET /api/catalog/services
Devuelve todas las prestaciones registradas. Cualquier usuario autenticado puede consultar.

POST /api/catalog/services
Crea una prestacion nueva. Requiere rol Admin. Cuerpo esperado:
```
{
  "nombre": "texto",
  "precio": numero,
  "boxId": numero,
  "cupoDisponible": numero
}
```

PUT /api/catalog/services/{id}
Actualiza una prestacion existente (precio, cupo, etc). Requiere rol Admin.

## 12. Problemas frecuentes y solucion

Error de compilacion: "no suitable method found for requestMatchers(...)"
Esto ocurre cuando en un archivo SecurityConfig.java se intenta combinar dos metodos HTTP distintos (por ejemplo POST y PUT) en una sola llamada a requestMatchers. La solucion es separar en dos llamadas independientes, una por cada metodo. Si este error aparece al clonar el repositorio, generalmente significa que se esta trabajando con una copia desactualizada: se recomienda borrar la carpeta local y volver a clonar desde GitHub para asegurar que se tiene la version corregida.

Error al arrancar: "Unable to resolve the Configuration with the provided Issuer"
Significa que el valor de issuer configurado en application.yml no corresponde a un tenant real de Azure (por ejemplo, quedo con un valor de ejemplo o placeholder sin reemplazar, como CAMBIAR_TENANT_ID). Revisar el punto 6.1 de este documento.

Respuesta 401 con el mensaje "the token has expired"
El token JWT obtenido en Postman ya vencio (duran aproximadamente una hora). Es necesario volver a la pestana Authorization y generar un token nuevo con Get New Access Token.

Respuesta 401 al llamar a un endpoint sin haber tocado la configuracion
Revisar que el encabezado Authorization se este enviando realmente en la peticion (en Postman, esto se puede confirmar en la pestana Console, expandiendo el detalle de la peticion enviada). Es un error frecuente crear una peticion nueva en Postman y que esta no herede automaticamente el token de una peticion anterior. Tambien es normal recibir 401 al llamar a /actuator/health o /api/ping sin token: esos endpoints tambien exigen autenticacion en este proyecto, asi que un 401 ahi confirma que el servicio esta funcionando, no que algo este mal.

Respuesta 403 en un endpoint que si deberia responder
Confirmar que el usuario con el que se obtuvo el token tenga asignado el rol correspondiente en Azure (seccion 4.5), y que la regla de autorizacion en el codigo (SecurityConfig.java del microservicio correspondiente) este exigiendo el rol correcto para esa ruta y metodo especificos.

Respuesta 404 al llamar a traves del API Gateway, pero 200 en local
Generalmente indica un problema en la integracion del Gateway: o falta una ruta especifica (ver seccion 10.3, sobre el comodin {proxy+} que no cubre la ruta exacta sin segmentos adicionales), o la URL de la integracion no incluye el prefijo de ruta correcto antes de {proxy} (por ejemplo, apuntar solo a http://<ip>:8080/{proxy} en vez de http://<ip>:8080/api/appointments/{proxy}).

Error "Connection refused" en los registros del bff, dentro de Docker
Ocurre cuando el archivo application.yml del bff tiene escritas las URL de los otros microservicios como http://localhost:8081 y http://localhost:8082 de forma fija. Dentro de un contenedor Docker, localhost se refiere al propio contenedor, no a los otros. La solucion es que esas URL se lean desde variables de entorno (APPOINTMENTS_URL y CATALOG_URL), que docker-compose.yml ya define apuntando a los nombres de los servicios (http://appointments:8081 y http://catalog:8082), con localhost solo como valor de respaldo para cuando se ejecuta fuera de Docker.

Advertencia "The AZURE_TENANT_ID variable is not set. Defaulting to a blank string."
Es una advertencia normal de docker-compose cuando no existe un archivo .env con esas variables definidas en la maquina donde se ejecuta el comando. No impide que los contenedores levanten, porque los valores de issuer y audience ya estan escritos directamente en cada application.yml (seccion 6.1) y no dependen de esas variables de entorno del sistema operativo. Se puede ignorar.

Error "compose build requires buildx" al ejecutar docker compose up --build
Se soluciona anteponiendo la variable de entorno al comando: DOCKER_BUILDKIT=0 docker-compose up --build

Error "Invalid HTTP endpoint specified for URI" al configurar una integracion en API Gateway
Suele deberse a caracteres invisibles introducidos al copiar y pegar la URL. Se soluciona borrando el campo por completo y escribiendo la URL manualmente.

La instancia EC2 no responde, o el SSH se vuelve muy lento o falla
Si se esta usando una instancia t3.micro, es probable que se haya quedado sin memoria disponible al ejecutar los tres microservicios de forma simultanea dentro de Docker. La solucion es detener la instancia (no solo reiniciarla) y cambiar su tipo a uno con mas memoria, por ejemplo t3.small, antes de volver a iniciarla. Recordar que esto cambia la IP publica (ver seccion 9.7).
