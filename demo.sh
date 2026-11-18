#!/usr/bin/env bash
# Demostracion de extremo a extremo de la plataforma.
#
# Recorre el flujo completo: crea el establecimiento, el profesional, el
# insumo y el servicio; reserva una cita; la cierra; y comprueba que los
# dos consumidores reaccionaron al evento sin que nadie se los pidiera.
#
#   chmod +x demo.sh
#   ./demo.sh
#
# Requiere que todo este levantado:  docker compose up --build

set -e

EST=http://localhost:8081
AGENDA=http://localhost:8080
OPER=http://localhost:8082
REL=http://localhost:8083

CLIENTE=44444444-4444-4444-4444-444444444444
INICIO="2026-11-10T09:00:00-05:00"
FIN="2026-11-10T10:00:00-05:00"

azul()  { printf "\n\033[1;34m%s\033[0m\n" "$1"; }
verde() { printf "\033[0;32m%s\033[0m\n" "$1"; }

# Extrae el primer "id" de una respuesta JSON, sin depender de jq.
id_de() { echo "$1" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4; }

azul "1. Crear el establecimiento"
R=$(curl -s -X POST $EST/establecimientos -H "Content-Type: application/json" \
  -d '{"nombre":"Salon de unas Neiva","telefono":"3001234567","ventanaConfirmacionHoras":24}')
EST_ID=$(id_de "$R")
verde "   establecimiento: $EST_ID"

azul "2. Registrar la profesional"
R=$(curl -s -X POST $EST/establecimientos/$EST_ID/profesionales \
  -H "Content-Type: application/json" -d '{"nombre":"Laura Rojas"}')
PROF_ID=$(id_de "$R")
verde "   profesional: $PROF_ID"

azul "3. Registrar el insumo en el inventario (existencia 9, umbral 8)"
R=$(curl -s -X POST $OPER/inventario -H "Content-Type: application/json" \
  -d "{\"establecimientoId\":\"$EST_ID\",\"nombre\":\"Esmalte semipermanente\",\"unidad\":\"frasco\",\"existencia\":9,\"umbralMinimo\":8}")
INSUMO_ID=$(id_de "$R")
verde "   insumo: $INSUMO_ID"

azul "4. Crear el servicio del catalogo"
R=$(curl -s -X POST $EST/servicios -H "Content-Type: application/json" \
  -d "{\"establecimientoId\":\"$EST_ID\",\"nombre\":\"Manicura semipermanente\",\"duracionMinutos\":60,\"precio\":45000,\"periodicidadDias\":28}")
SERV_ID=$(id_de "$R")
verde "   servicio: $SERV_ID"

azul "5. Asociar el insumo al servicio (la receta)"
curl -s -X POST $EST/servicios/$SERV_ID/insumos -H "Content-Type: application/json" \
  -d "{\"insumoId\":\"$INSUMO_ID\",\"cantidadEstimada\":1}" > /dev/null
verde "   el servicio consume 1 frasco"

azul "6. Reservar la cita  ->  publica citas.agendada"
R=$(curl -s -X POST $AGENDA/citas -H "Content-Type: application/json" \
  -d "{\"establecimientoId\":\"$EST_ID\",\"profesionalId\":\"$PROF_ID\",\"servicioId\":\"$SERV_ID\",\"clienteId\":\"$CLIENTE\",\"inicio\":\"$INICIO\",\"fin\":\"$FIN\"}")
CITA_ID=$(id_de "$R")
verde "   cita: $CITA_ID"

azul "7. Cerrar la cita  ->  publica citas.completada"
curl -s -X POST $AGENDA/citas/$CITA_ID/cierre -H "Content-Type: application/json" \
  -d '{"valorCobrado":45000}' > /dev/null
verde "   servicio prestado por 45000"

azul "Esperando a que los consumidores procesen el evento..."
sleep 5

azul "8. ms-operaciones liquido el ingreso (nadie se lo pidio)"
curl -s "$OPER/resumen?establecimientoId=$EST_ID&desde=2026-01-01T00:00:00-05:00&hasta=2026-12-31T23:59:59-05:00"
echo

azul "9. ms-operaciones descontio el insumo. Deberia quedar en 8 y en umbral"
curl -s "$OPER/inventario?establecimientoId=$EST_ID"
echo

azul "10. ms-relacion-cliente genero las notificaciones"
curl -s "$REL/notificaciones?establecimientoId=$EST_ID"
echo

azul "11. ms-relacion-cliente programo el recontacto a 28 dias"
curl -s "$REL/clientes/$CLIENTE/recurrencia?establecimientoId=$EST_ID"
echo

printf "\n\033[1;32mListo.\033[0m Un solo evento, dos servicios que reaccionaron sin conocerse.\n"
printf "Mira los mensajes en Kafka UI: http://localhost:8090\n\n"
