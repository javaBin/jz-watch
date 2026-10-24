# GENERATED FROM app.yaml — do not edit, changes will be overwritten
terraform {
  backend "s3" {
    bucket         = "javabin-terraform-state-553637109631"
    key            = "apps/jzwatch/jz-watch/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "javabin-terraform-app-locks"
    encrypt        = true
  }
}
