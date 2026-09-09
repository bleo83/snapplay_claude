variable "environment" { type = string }

locals {
  name_prefix = "snapplay-${var.environment}"
}

resource "aws_secretsmanager_secret" "app" {
  name                    = "${local.name_prefix}/app-secrets"
  description             = "Application secrets for ${var.environment} (webhook secrets, API keys)"
  recovery_window_in_days = var.environment == "prod" ? 30 : 7

  tags = { Name = "${local.name_prefix}-app-secrets", Environment = var.environment }
}

resource "aws_secretsmanager_secret_version" "app_initial" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    rappi_webhook_secret = "CHANGE_ME_AFTER_DEPLOY"
    rappi_api_key        = ""
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}

output "app_secrets_arn" { value = aws_secretsmanager_secret.app.arn }
