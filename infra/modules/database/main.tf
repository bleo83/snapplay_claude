variable "environment" { type = string }
variable "vpc_id" { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "app_security_group_id" { type = string }

locals {
  name_prefix = "snapplay-${var.environment}"
}

resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db-subnets"
  subnet_ids = var.private_subnet_ids

  tags = { Name = "${local.name_prefix}-db-subnets" }
}

resource "aws_security_group" "rds" {
  name_prefix = "${local.name_prefix}-rds-"
  vpc_id      = var.vpc_id
  description = "RDS PostgreSQL - only reachable from app"

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.app_security_group_id]
    description     = "PostgreSQL from app"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-rds-sg" }

  lifecycle { create_before_destroy = true }
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name_prefix}-pg"
  engine         = "postgres"
  engine_version = "16.4"
  instance_class = "db.t4g.micro"

  allocated_storage     = 20
  max_allocated_storage = 50
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "snapplay"
  username = "snapplay"
  # Password managed via Secrets Manager — initial value set manually or via TF
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = 7
  skip_final_snapshot     = var.environment == "staging"
  final_snapshot_identifier = var.environment == "staging" ? null : "${local.name_prefix}-final"

  deletion_protection = var.environment == "prod"

  tags = { Name = "${local.name_prefix}-pg", Environment = var.environment }
}

output "db_endpoint" { value = aws_db_instance.main.endpoint }
output "db_name" { value = aws_db_instance.main.db_name }
output "db_master_secret_arn" { value = aws_db_instance.main.master_user_secret[0].secret_arn }
output "rds_security_group_id" { value = aws_security_group.rds.id }
