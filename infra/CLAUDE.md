# infra — Agent Guidelines

Terraform for AWS, region **`sa-east-1`**. Two environments sharing four modules.

```
environments/staging/main.tf   vpc_cidr 10.0.0.0/16   state key staging/terraform.tfstate
environments/prod/main.tf      vpc_cidr 10.1.0.0/16   state key prod/terraform.tfstate
modules/networking  VPC, 2 public + 2 private subnets across sa-east-1a/1b, IGW
modules/compute     EC2 (t4g.micro, AL2023 arm64), IAM role, EIP, user-data.sh
modules/database    RDS PostgreSQL 16.15 (db.t4g.micro), private subnets only
modules/secrets     Secrets Manager entry for app secrets
```

State: S3 bucket `snapplay-terraform-state`, locked via DynamoDB `snapplay-terraform-locks`.

## Never apply

Run `terraform plan`, `validate`, `fmt`, and `show`. **Never** run `apply`, `destroy`,
`taint`, `import`, or `state rm` — these are operator actions, and prod carries
`deletion_protection = true` precisely so they are deliberate.

Present the plan output and stop. Let a human apply it.

## Free-tier constraints

The pilot runs inside free-tier/minimal-cost limits. These values are chosen, not accidental —
do not "upgrade" them for robustness without being asked:

- `db.t4g.micro` / `t4g.micro` instance classes
- `backup_retention_period = 1`, `multi_az = false`
- PostgreSQL `16.15` (pinned for free-tier compatibility — see `5f7df7a`)
- `allocated_storage = 20`, `max_allocated_storage = 50`

## Environment asymmetry

Several resources branch on `var.environment`. When editing, check both paths:

| Setting | staging | prod |
|---------|---------|------|
| `skip_final_snapshot` | `true` | `false` |
| `final_snapshot_identifier` | `null` | `snapplay-prod-final` |
| `deletion_protection` | `false` | `true` |
| Secrets `recovery_window_in_days` | 7 | 30 |

Adding a new environment-dependent value means updating this table.

## Known gaps — do not silently "fix"

- `modules/compute/main.tf` opens **port 22 to `0.0.0.0/0`** with the comment
  "restrict in production". SSM Session Manager is being added as the replacement path.
  Removing the SSH rule is a deliberate change — raise it, don't fold it into unrelated work.
- `modules/secrets/main.tf` seeds `rappi_webhook_secret = "CHANGE_ME_AFTER_DEPLOY"` under
  `lifecycle { ignore_changes = [secret_string] }`. Real values are set out-of-band. Never
  commit a real secret value here.

## Secrets

Secrets reach the instance at boot via `user-data.sh`, which reads Secrets Manager with the
EC2 instance role. Never put a credential in a `.tf` file, a variable default, or user-data
literal. The IAM policy grants `GetSecretValue` on exactly two ARNs — keep it that narrow.

## Deploy pipeline

`.github/workflows/deploy.yml` builds the Kotlin image, pushes to GHCR, and deploys.
Triggered by pushes to `main` under `apps/api-kotlin/**`, or manually via `workflow_dispatch`
with an environment choice. `.github/workflows/ci.yml` runs the npm workspace checks and the
Gradle `ktlintCheck` / `assemble` / `test` jobs.

Changes to workflow files are infra changes — same review bar as Terraform.

## Style

- Run `terraform fmt -recursive` before committing.
- Module inputs are declared as bare `variable "x" { type = ... }` at the top of `main.tf` —
  there are no separate `variables.tf` / `outputs.tf` files. Match that.
- Every module derives names from `local.name_prefix = "snapplay-${var.environment}"`.
