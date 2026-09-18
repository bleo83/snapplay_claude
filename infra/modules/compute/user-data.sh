#!/bin/bash
set -euo pipefail

# --- Install Docker ---
dnf update -y
dnf install -y docker jq
systemctl enable docker
systemctl start docker
usermod -a -G docker ec2-user

# --- Install docker-compose ---
COMPOSE_VERSION="v2.29.1"
curl -fsSL "https://github.com/docker/compose/releases/download/$${COMPOSE_VERSION}/docker-compose-linux-aarch64" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# --- Install AWS CLI (already on AL2023) ---

# --- Fetch DB credentials from Secrets Manager ---
DB_SECRET=$(aws secretsmanager get-secret-value \
  --secret-id "${db_secret_arn}" \
  --region "${aws_region}" \
  --query SecretString --output text)

DB_USER=$(echo "$DB_SECRET" | jq -r '.username')
DB_PASS=$(echo "$DB_SECRET" | jq -r '.password')
DB_HOST=$(echo "${db_endpoint}" | cut -d: -f1)
DB_PORT=$(echo "${db_endpoint}" | cut -d: -f2)

# --- Fetch app secrets ---
APP_SECRET=$(aws secretsmanager get-secret-value \
  --secret-id "${app_secret_arn}" \
  --region "${aws_region}" \
  --query SecretString --output text 2>/dev/null || echo '{}')

RAPPI_WEBHOOK_SECRET=$(echo "$APP_SECRET" | jq -r '.rappi_webhook_secret // empty')

# --- Write environment file ---
mkdir -p /opt/snapplay
cat > /opt/snapplay/.env <<EOF
SNAPPLAY_DB_URL=jdbc:postgresql://$${DB_HOST}:$${DB_PORT}/${db_name}
SNAPPLAY_DB_USER=$${DB_USER}
SNAPPLAY_DB_PASSWORD=$${DB_PASS}
SNAPPLAY_PUBLIC_BASE_URL=http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)
RAPPI_WEBHOOK_SECRET=$${RAPPI_WEBHOOK_SECRET}
SPRING_PROFILES_ACTIVE=${environment}
EOF

# --- Write docker-compose.yml ---
cat > /opt/snapplay/docker-compose.yml <<'COMPOSE'
services:
  api:
    image: ghcr.io/bleo83/snapplay-api:latest
    restart: unless-stopped
    ports:
      - "80:8080"
    env_file:
      - .env
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 40s
    logging:
      driver: awslogs
      options:
        awslogs-region: sa-east-1
        awslogs-group: /snapplay/${environment}/api
        awslogs-create-group: "true"
COMPOSE

# --- Create CloudWatch log group ---
aws logs create-log-group \
  --log-group-name "/snapplay/${environment}/api" \
  --region "${aws_region}" 2>/dev/null || true

# --- Pull and start ---
cd /opt/snapplay

# ECR login (when using ECR instead of GHCR)
# aws ecr get-login-password --region sa-east-1 | docker login --username AWS --password-stdin <account>.dkr.ecr.sa-east-1.amazonaws.com

docker-compose pull || true
docker-compose up -d || true

echo "Snap Play ${environment} bootstrap complete"
