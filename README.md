# AVI – Registro Operativo por Voz

Aplicación Android nativa en Kotlin + Jetpack Compose para registrar fugas rápidamente desde una burbuja flotante, sin abandonar la app operativa que esté abierta.

## V1
- Burbuja flotante con `TYPE_APPLICATION_OVERLAY`.
- Panel flotante de registro.
- Reconocimiento de voz `es-PE`.
- Interpreta “Fuga vía 151” y números hablados.
- Convierte “Bravo Tango Lima dos cuatro cinco” en `BTL245`.
- Confirmación manual antes de guardar.
- Persistencia e historial en Supabase.

## Supabase
1. Ejecuta `supabase/migrations/001_create_registros.sql`.
2. Copia `local.properties.example` a `local.properties`.
3. El proyecto configurado para AVI es `osx-23's Project`, con referencia `adozechgzkbqopujaapp`.
4. Configura únicamente `SUPABASE_PUBLISHABLE_KEY` con la clave pública del proyecto.

Configuración esperada:

```properties
SUPABASE_URL=https://adozechgzkbqopujaapp.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

Nunca uses `service_role` o una secret key dentro del APK.

La política RLS incluida es solo para esta V1 de desarrollo y permite SELECT/INSERT con la publishable key. Antes de producción debe añadirse autenticación y autorización por usuario/rol.

## Compilar
Requiere JDK 17, Android SDK 36 y Gradle 8.13.

```bash
gradle testDebugUnitTest assembleDebug
```

## Flujo
Activa la burbuja → abre la app operativa → toca la burbuja → HABLAR → dicta “Fuga vía ciento cincuenta y uno placa Bravo Tango Lima dos cuatro cinco” → revisa FUGA / 151 / BTL245 → REGISTRAR.
