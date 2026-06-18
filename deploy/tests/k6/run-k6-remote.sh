# Terminal 1: abre el túnel SSH (déjala corriendo)
ssh -L 9090:prometheus.observability.svc.cluster.local:9090 tu-usuario@janrax.es -N

# Terminal 2: ejecutas k6 desde tu máquina local
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
k6 run --out experimental-prometheus-rw \
  --vus 500 --duration 30s \
  --tag testid=catalog-read \
  catalog-read.js \
  -e BASE_URL=https://janrax.es
