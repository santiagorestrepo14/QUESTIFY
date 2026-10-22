<div align="center">

# 🏆 QUESTIFY

**Tu vida sube de nivel.**

Juego nativo para Android que convierte tu día a día en una partida: retos con sensores, logros secretos, XP, niveles y rangos.

[![Google Play](https://img.shields.io/badge/Google%20Play-Disponible-34A853?logo=googleplay&logoColor=white)](https://play.google.com/store/apps/details?id=com.irlachievements.app)
![Android](https://img.shields.io/badge/Android-9%2B%20(API%2028)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Versión](https://img.shields.io/badge/versión-1.1.0-B8FF5A)

[Google Play](https://play.google.com/store/apps/details?id=com.irlachievements.app) · [Wireframe en Figma](https://www.figma.com/design/UwoU1Mq6kLKIBC9ObZW80W/LIFELVL-%25C2%25B7-Alta-fidelidad?node-id=0-1) · [Política de privacidad](https://sites.google.com/view/questify-privacidad/p%C3%A1gina-principal)

</div>

---

## 📖 Sobre el proyecto

QUESTIFY es el proyecto final (Entrega #4) del curso de **Aplicaciones Móviles**. Es una app nativa en Kotlin que usa casi todo el hardware del teléfono como control de juego: acelerómetro, giroscopio, brújula, sensor de luz, pantalla táctil, audio estéreo y micrófono.

Cada reto superado y cada hábito de la vida real (pasos, actividad, hora del día, batería) desbloquea **logros**. Los logros dan **XP**, la XP sube tu **nivel** y tu nivel desbloquea **rangos, marcos de perfil y logos coleccionables**.

| 🏅 Logros | 🎮 Retos jugables | 🔒 Logros secretos | ✅ Pruebas unitarias |
|:---:|:---:|:---:|:---:|
| **222** | **64** | **107** | **324** |

## 📱 Secciones

| # | Sección | Qué contiene |
|---|---|---|
| 1 | **Splash** | Entrada animada mientras se carga el progreso |
| 2 | **Inicio** | Nivel, XP, rango actual y próxima recompensa |
| 3 | **Retos** (juego) | Minijuegos por categoría, con niveles y tiempo |
| 4 | **Logros** (puntaje) | Logros visibles, secretos y de retos, con su XP |
| 5 | **Créditos** | Integrantes del equipo |
| + | **Ajustes** | Nombre del jugador, sonidos y colección de logos |

## 🎮 Cómo se juega

1. Eliges un reto en la sección **Retos**.
2. La app verifica que tu teléfono tenga el sensor necesario; si no lo tiene, el reto aparece como *no compatible*.
3. Superas **3 niveles** de dificultad creciente, muchos de ellos **contra el reloj**.
4. El resultado es éxito o fallo (con su motivo).
5. Al completarlo ganas el logro y su XP **una sola vez** (no se puede farmear).

**Ejemplo, *Eco de colores*:** repite una secuencia de 3, 4 y 5 símbolos, con 20 segundos por ronda.

### Tipos de retos

| Categoría | Ejemplos |
|---|---|
| 🌀 Movimiento | Giros, inclinación, estabilidad, sacudidas, brújula |
| 💡 Luz ambiental | Vampiro (oscuridad), Supernova (mucha luz) |
| 👆 Táctiles | Eco de colores, Señal tranquila, Orden ascendente, Encuentra el intruso |
| 🎧 Audio | Parejas sonoras, Escalera tonal, Brújula sonora (estéreo) |
| 🎙️ Micrófono | Afinador, Eco rítmico, Clave acústica, Paso silencioso |
| 🚶 Vida real | Pasos, distancia, actividad, hora y batería |

### XP y rangos

La XP es progreso permanente: no se gasta y el nivel nunca baja.

| Rareza | Común | Poco común | Raro | Épico | Legendario |
|---|:---:|:---:|:---:|:---:|:---:|
| **XP** | 10 | 25 | 50 | 100 | 250 |

- XP necesaria para el nivel *n*: `40 × (n − 1)²`
- Rangos: Novato → Explorador → Aventurero → Cazador → Especialista → Maestro → Leyenda → Mito (uno nuevo cada 3 niveles, cada uno con su marco de perfil).
- **9 logos coleccionables** que se desbloquean jugando y cambian incluso el ícono de la app en el launcher.

### 🥚 Easter eggs

- **Glitch in Reality**: logro legendario secreto con probabilidad de 1 entre 24 de aparecer cada vez que abres la app.
- **Glitch Relic**: logo oculto cuyo requisito no se muestra hasta descubrir suficientes secretos.
- **107 logros secretos** que se activan con la hora, la fecha, la batería o tus hábitos.

## ✅ Requisitos de la entrega

| Requisito | Cómo se cumple |
|---|---|
| Mínimo 5 secciones | Splash, Inicio, Retos, Logros, Créditos (+ Ajustes) |
| Niveles / tiempo | Minijuegos de 3 niveles con límite de tiempo |
| UI personalizada | Tema oscuro propio, logo, íconos por tipo de logro y 9 logos |
| Persistencia entre vistas | `AppViewModel` + Room + DataStore |
| Animaciones y sonidos | Splash animado, desbloqueos animados, señales de éxito/fallo y notas sintetizadas |
| Wireframe | [Figma](https://www.figma.com/design/UwoU1Mq6kLKIBC9ObZW80W/LIFELVL-%25C2%25B7-Alta-fidelidad?node-id=0-1) |
| Publicación | [Google Play](https://play.google.com/store/apps/details?id=com.irlachievements.app) |
| Bonus easter eggs | Glitch in Reality, Glitch Relic y logros secretos |

## 🏗️ Arquitectura

```
app/src/main/java/com/irlachievements/app/
├── ui/           Pantallas en Jetpack Compose, navegación y tableros de minijuegos
├── domain/       Reglas puras: catálogo y motor de logros, motores de minijuegos, XP
├── data/         Room, DataStore y acceso a sensores, pasos, batería y micrófono
└── background/   WorkManager: sincronización de pasos/uso y notificaciones
```

La capa `domain` no depende de Android, por eso todas las reglas del juego se prueban con pruebas unitarias sin necesidad de un teléfono.

### Persistencia

- **Room (SQLite):** logros desbloqueados, XP, estadísticas, pasos importados e intentos de retos.
- **DataStore:** nombre del jugador, sonidos activados y logo equipado.
- Todo se guarda **solo en el dispositivo**: sin cuentas ni servidores.

### Tecnologías

Kotlin · Jetpack Compose · Navigation Compose · Room · DataStore · WorkManager · Recording API · Activity Recognition

## 🔐 Privacidad y permisos

Todos los permisos son opcionales y la app funciona sin ellos (los retos que los necesitan aparecen deshabilitados).

| Permiso | Para qué |
|---|---|
| Reconocimiento de actividad | Logros de caminar, correr, bicicleta, etc. |
| Acceso de uso | Contar desbloqueos de pantalla (no se guarda qué apps usas) |
| Micrófono | Solo durante los 4 retos de micrófono; el audio **nunca se guarda ni se envía** |
| Notificaciones | Avisar cuando ganas un logro en segundo plano |

No se usan ubicación, cámara, contactos ni archivos. Política completa: [Política de privacidad](https://sites.google.com/view/questify-privacidad/p%C3%A1gina-principal).

## 🚀 Cómo ejecutarlo

**Requisitos:** Android Studio, JDK 17 o superior (verificado con JDK 24) y un dispositivo o emulador con Android 9 (API 28) o superior.

1. Clona el repositorio:
   ```bash
   git clone https://github.com/santiagorestrepo14/QUESTIFY.git
   ```
2. En Android Studio elige **Open** y selecciona la carpeta del proyecto.
3. Espera el Gradle Sync y ejecuta el módulo `app`.

Desde la terminal (Windows):

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

> Los sensores físicos (movimiento, luz, micrófono) deben probarse en un teléfono real; el emulador solo permite verificar navegación y estados.

## 👥 Equipo

| Integrante | Responsabilidad |
|---|---|
| Santiago Restrepo | [Responsabilidad] |
| Josué Pino | Reglas de logros, XP, minijuegos y pruebas de dominio |
| Sebastián Cruz | [Responsabilidad] |

## 🤖 Uso de IA

Se utilizó asistencia de IA para implementar y revisar código, proponer pruebas y preparar documentación y el diseño en Figma. Cada integrante revisó su parte y puede explicarla y modificarla. Las 324 pruebas unitarias verifican que las reglas del juego funcionan como se describe.

---

<div align="center">

Universidad Pontificia Bolivariana · Aplicaciones Móviles · 2026

</div>
