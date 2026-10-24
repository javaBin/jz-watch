# GENERATED FROM app.yaml — do not edit, changes will be overwritten
output "service_url" {
  value = "https://watch.javazone.no"
}

output "ecr_url" {
  value = module.ecr.repository_url
}

output "service_name" {
  value = module.service.service_name
}

output "task_role_arn" {
  value = module.task_role.role_arn
}

output "log_group" {
  value = module.service.log_group_name
}
