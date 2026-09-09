terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "snapplay-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "sa-east-1"
    encrypt        = true
    dynamodb_table = "snapplay-terraform-locks"
  }
}

provider "aws" {
  region = "sa-east-1"

  default_tags {
    tags = {
      Project     = "snapplay"
      Environment = "prod"
      ManagedBy   = "terraform"
    }
  }
}

module "networking" {
  source      = "../../modules/networking"
  environment = "prod"
  vpc_cidr    = "10.1.0.0/16"
}

module "secrets" {
  source      = "../../modules/secrets"
  environment = "prod"
}

module "compute" {
  source            = "../../modules/compute"
  environment       = "prod"
  vpc_id            = module.networking.vpc_id
  public_subnet_ids = module.networking.public_subnet_ids
  db_endpoint       = module.database.db_endpoint
  db_name           = module.database.db_name
  db_secret_arn     = module.database.db_master_secret_arn
  app_secrets_arn   = module.secrets.app_secrets_arn
}

module "database" {
  source                = "../../modules/database"
  environment           = "prod"
  vpc_id                = module.networking.vpc_id
  private_subnet_ids    = module.networking.private_subnet_ids
  app_security_group_id = module.compute.app_security_group_id
}

output "app_public_ip" { value = module.compute.app_public_ip }
output "db_endpoint" { value = module.database.db_endpoint }
