{{- define "backend.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "backend.fullname" -}}
{{- if contains .Chart.Name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "backend.selectorLabels" -}}
app.kubernetes.io/name: {{ include "backend.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "backend.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{ include "backend.selectorLabels" . }}
app.kubernetes.io/version: {{ .Values.image.tag | trunc 12 | quote }}
app.kubernetes.io/part-of: smartcampus
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "backend.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "backend.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/* One env var read from the external Secret */}}
{{- define "backend.secretEnv" -}}
- name: {{ .env }}
  valueFrom:
    secretKeyRef:
      name: {{ .secret }}
      key: {{ .key }}
      {{- if .optional }}
      optional: true
      {{- end }}
{{- end -}}
