# Stage 2 — matriz de estados, transiciones y conflictos

Fecha: 2026-09-14  
Estado: contrato de implementación del candidato local; no acredita aceptación.

## Fuentes de verdad y precedencia

| Fuente persistida | Proyección visible | Horas planificadas | Trabajo/cumplimiento | Pendiente/avisos/clima | Precedencia |
|---|---|---:|---:|---:|---|
| `Shift.status = PLANNED` | Próxima, en curso o completada por reloj | Sí | Según horario real o plan vigente | Sí mientras corresponda | Base temporal |
| `Shift.status = ABSENT` + situación | Ausencia | Sí, categoría ausencia | No | No | Sobre carpeta médica, vacaciones y reloj |
| `Shift.status = CANCELLED` + situación | Cancelada | Sí, categoría cancelada | No | No | Sobre carpeta médica, vacaciones y reloj |
| Carpeta médica | `CM` / Carpeta médica | Informa días, no horas | No para jornadas `PLANNED` alcanzadas | No | Sobre `PLANNED`; debajo de ausencia/cancelación |
| Vacaciones | `V` / Vacaciones | Informa días, no horas | No para jornadas `PLANNED` alcanzadas | No | Sobre `PLANNED`; excluyente con carpeta médica |
| `DAY_OFF` | `F` / Franco | No | No | No | Marca civil explícita; no reemplaza jornadas |
| `UNDEFINED` | `?` / Sin definir | No | No | No | Marca civil explícita; no equivale a vacío |
| Retén | Ventana de Retén | Separado | Nunca jornada trabajada | Según su motor propio | Independiente |

Las jornadas nocturnas pertenecen siempre a `localStartDate`. Un intervalo del día A que termina el día B no ocupa civilmente B para `F`, `?` o carpeta médica.

## Transiciones por jornada

| Origen | Destino | Requisitos | Efectos atómicos | Rechazo recuperable |
|---|---|---|---|---|
| `PLANNED` | `ABSENT` | motivo y nota opcionales; expectativa completa de jornada, situación, horario real/extras y ocurrencia | conserva jornada e instantáneas; crea situación; retira horario real/extras sólo con consentimiento; personaliza la ocurrencia recurrente | estado, situación, horario real/extras u ocurrencia cambiaron |
| `PLANNED` | `CANCELLED` | motivo obligatorio; actor incluido `No informado`; aviso obligatorio; nota e imagen opcionales | conserva jornada; crea situación; vincula evidencia privada; retira incompatibles sólo con consentimiento; personaliza ocurrencia | metadatos inválidos, evidencia inválida o concurrencia |
| `ABSENT` | `CANCELLED` | requisitos completos de cancelación | reemplaza la situación controladora; conserva jornada; agrega evidencia opcional | situación o jornada cambiaron |
| `CANCELLED` | `ABSENT` | motivo y nota opcionales; confirmación si se elimina evidencia | reemplaza situación; elimina vínculo de evidencia después del commit | falta consentimiento o concurrencia |
| `ABSENT` / `CANCELLED` | `PLANNED` | confirmación con lista de metadatos/evidencia a retirar | elimina situación; restaura proyección por reloj; ocurrencia queda personalizada sólo para esa fecha | falta consentimiento o concurrencia |
| Legacy `ABSENT` / `CANCELLED` sin situación | `ABSENT`, `CANCELLED` o `PLANNED` | mismos requisitos del destino; no se inventa motivo, actor, aviso ni evidencia previa | crea situación sólo si el destino la requiere; conserva jornada | jornada, horario real/extras u ocurrencia cambiaron |
| `PLANNED` sin situación | `PLANNED` | No aplica: es un no-op | Ninguno | se rechaza para no alterar `updatedAt` ni personalizar una ocurrencia |

`COMPLETED` nunca se persiste. Ninguna transición cambia UUID, intervalo planificado, instantáneas laborales o fecha de creación.

## Operaciones por fecha o rango

| Operación | Permitida | Conflictos y conservación |
|---|---|---|
| Marcar `?` | Sólo fecha sin jornada iniciada ese día ni `F` | No borra ni recorta carpeta médica, vacaciones, Retén u otra fuente coincidente; una jornada nocturna del día anterior no bloquea |
| Quitar `?` | Sólo si la expectativa sigue siendo `UNDEFINED` | Borra exclusivamente esa marca |
| Crear carpeta médica | Rango inclusivo válido sin vacaciones | Conserva jornadas y demás fuentes; no fusiona carpetas existentes |
| Editar carpeta médica | Mismo UUID y creación, actualización creciente; rango sin vacaciones | No fusiona, corta ni elimina otras carpetas |
| Eliminar carpeta médica | Confirmación y expectativa completa | Borra sólo el rango elegido |

## Privacidad y archivos

- Motivos, notas y actor de cancelación nunca se muestran en marcadores del Calendario, avisos, clima ni próximo evento.
- La cancelación admite como máximo una imagen elegida conscientemente, copiada con clave opaca al almacenamiento privado; no se persiste una ruta externa.
- No existen imágenes de carpeta médica ni afirmaciones de validez clínica, laboral o legal.
- Copias sin imágenes retiran el vínculo de evidencia. Copias con imágenes validan tipo, tamaño y huella antes de restaurar.
