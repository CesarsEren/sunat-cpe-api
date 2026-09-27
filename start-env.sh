#!/usr/bin/env bash
set -e

if [ ! -f .env ]; then
    echo "ERROR: archivo .env no encontrado."
    echo "  cp .env.example .env && nano .env"
    exit 1
fi

set -a
source .env
set +a

echo "==> Iniciando sunat-cpe-api (production=$SUNAT_PRODUCTION)"
mvn spring-boot:run
