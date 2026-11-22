{{/*
Expand the name of the chart.
*/}}
{{- define "ecommerce.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "ecommerce.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "ecommerce.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Generate service name: <release>-<service>-service
*/}}
{{- define "ecommerce.serviceName" -}}
{{- printf "%s-%s-service" .context.Release.Name .serviceName | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Generate ServiceAccount name per service: <release>-<service>-service
*/}}
{{- define "ecommerce.serviceAccountName" -}}
{{- printf "%s-%s-service" .context.Release.Name .serviceName | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common chart labels
*/}}
{{- define "ecommerce.labels" -}}
helm.sh/chart: {{ include "ecommerce.chart" . }}
app.kubernetes.io/part-of: ecommerce
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Service-specific labels
*/}}
{{- define "ecommerce.serviceLabels" -}}
{{- $svc := index .context.Values.services .serviceName -}}
{{- $tag := .context.Chart.AppVersion -}}
{{- if and $svc $svc.image $svc.image.tag -}}
{{- $tag = $svc.image.tag -}}
{{- end -}}
app.kubernetes.io/name: {{ .serviceName }}-service
app.kubernetes.io/instance: {{ .context.Release.Name }}
app.kubernetes.io/part-of: ecommerce
app.kubernetes.io/managed-by: {{ .context.Release.Service }}
helm.sh/chart: {{ include "ecommerce.chart" .context }}
app: {{ .serviceName }}-service
version: {{ $tag | quote }}
{{- end }}

{{/*
Selector labels for deployments and services
*/}}
{{- define "ecommerce.selectorLabels" -}}
app.kubernetes.io/name: {{ .serviceName }}-service
app.kubernetes.io/instance: {{ .context.Release.Name }}
{{- end }}

{{/*
Construct SPRING_APPLICATION_JSON for client URLs specific to each service
product: none
cart: product-url
order: product-url, cart-url, payment-url
payment: order-url
*/}}
{{- define "ecommerce.clientUrlsJson" -}}
{{- $service := .serviceName -}}
{{- $release := .context.Release.Name -}}
{{- $port := .context.Values.service.port -}}
{{- if eq $service "cart" -}}
{{- dict "clients" (dict "product-url" (printf "http://%s-product-service:%v" $release $port)) | toJson -}}
{{- else if eq $service "order" -}}
{{- dict "clients" (dict "product-url" (printf "http://%s-product-service:%v" $release $port) "cart-url" (printf "http://%s-cart-service:%v" $release $port) "payment-url" (printf "http://%s-payment-service:%v" $release $port)) | toJson -}}
{{- else if eq $service "payment" -}}
{{- dict "clients" (dict "order-url" (printf "http://%s-order-service:%v" $release $port)) | toJson -}}
{{- end -}}
{{- end }}
