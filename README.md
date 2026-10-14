CRYPT0 CR3W CENTRAL [CCC] - TACTICAL INTELLIGENCE & HACK EX 2 INDEXER
====================================================================

1. RESUMEN DEL PROYECTO Y CONTEXTO
Crypt0 Cr3w Central [CCC] es una aplicacion nativa para Android desarrollada en Kotlin y Jetpack Compose, disenada como una herramienta avanzada de inteligencia, analisis OCR y gestion de operaciones tacticas inspirada y optimizada para el juego movil Hack Ex 2 (disponible en la Play Store).

La aplicacion actua como un indexador y gestor centralizado de claves ficticias, perfiles de objetivos, monederos de criptomonedas y registros de software (Antivirus, Firewall, Rootkit, Keygen, Proxy, etc.). Permite a los jugadores simular operaciones de ciberseguridad, analizar capturas de pantalla del juego mediante inteligencia artificial y debatir hitos en un foro tactico integrado.

2. FUNCIONALIDADES PRINCIPALES
- Indexador y Visor de Inteligencia: Gestion local (Room) y en la nube (Firestore) de objetivos capturados, IPs, hashes y niveles de seguridad. Busqueda y filtrado avanzado.
- Pipeline de OCR y Vision Artificial: Preprocesamiento de imagenes (ImagePreprocessor) e integracion con una cadena de respaldo (Gemini Vertex AI, Groq, OpenRouter, ML Kit).
- Foro Tactico e Hitos: Publicacion de reportes de inmcursion y sistema de comentarios anidados tipo hilo de comandos.
- Terminal de Logs del Sistema: Bitacora tecnica independiente para registros de software y auditorias.

3. STACK TECNOLOGICO
- Lenguaje: Kotlin (Coroutines & Flows).
- UI: Jetpack Compose (estetica cyberpunk/terminal).
- Base de Datos Local: Room Database.
- Backend: Firebase Auth (Credential Manager) y Cloud Firestore.

4. ORGANIZACION MODULAR PARA IAs
- /data/local/: Entidades y DAOs de Room (TargetDao, FeedDao, CommentDao, LogEntryDao).
- /data/repository/: Capas de abstraccion con Flows reactivos.
- /service/ocr/ & /service/ai/: Procesamiento de imagenes y parsing de Hack Ex 2.
- /ui/screens/: Pantallas Jetpack Compose (TacticalFeedScreen, PostDetailScreen, IntelligenceDatabaseScreen, SystemLogScreen).
