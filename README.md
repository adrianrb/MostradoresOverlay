# Mostradores

**Mostradores** é um aplicativo Android que exibe mostradores analógicos flutuantes (CPU, RAM e armazenamento) sobrepostos a qualquer outro app. Os gauges são desenhados programaticamente com `Canvas` e atualizados em tempo real, sem a necessidade de XML para a sobreposição.

## Capturas de tela

<p align="center">
  <img src="Screenshot_20261021-080909.png" width="250" alt="Tela principal do app" />
  <img src="Screenshot_20261021-081113.png" width="250" alt="Mostradores sobrepostos" />
</p>

## Características

- **Mostradores analógicos flutuantes**: CPU, RAM e armazenamento (interno + primeiro volume externo não-primário) exibidos em forma de mostrador circular
- **Sobreposição global**: Aparece sobre qualquer outro aplicativo
- **Configurável**: Tamanho, opacidade e intervalo de atualização ajustáveis pela tela principal
- **Arrastável**: Mova o painel flutuante arrastando-o pela tela
- **Fechamento rápido**: Toque no botão "✕" ou use a ação "Parar" na notificação
- **Detecção inteligente da CPU**: Usa `/proc/stat` com fallback para frequências por núcleo (`/sys/devices/system/cpu/*/cpufreq/`)
- **Suporte a Android 14+**: Usa `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` para manter o serviço ativo
- **Tratamento de crashes**: `CrashActivity` em processo separado para exibir erros de forma segura

## Requisitos

- Android 8.0 (API 26) ou superior
- Permissão de sobreposição (`SYSTEM_ALERT_WINDOW`)
- Notificações (API 33+)
- Permissão de acesso a todos os arquivos (`MANAGE_EXTERNAL_STORAGE`) para exibir o uso do armazenamento externo e usar as ferramentas de debug

## Arquitetura

| Componente | Descrição |
|------------|-----------|
| `MainActivity` | Interface de configuração. Controla permissões, inicia/para o serviço e ajusta escala/opacidade/intervalo |
| `OverlayService` | Serviço em primeiro plano que constrói toda a sobreposição programaticamente, gerencia o `WindowManager` e atualiza os mostradores periodicamente |
| `GaugeView` | View customizada desenhada com `Canvas` que renderiza os mostradores analógicos com ponteiro, marcações e valores |
| `Metrics` | Coleta dados do sistema: uso de CPU (via `/proc/stat` ou frequência dos núcleos), RAM (via `ActivityManager`) e armazenamento (via `StatFs`/`StorageVolume`) |
| `MostradoresApp` | Aplicação com handler global de exceções não tratadas |
| `CrashActivity` | Atividade em processo separado (`:crash`) para exibição de crashes |
| `DebugActivity` / `ArquivoTeste` | Ferramentas de teste para criar/remover arquivo dummy de 5GB no cartão SD externo |

Os dados de configuração são salvos no `SharedPreferences` com nome `"cfg"` e compartilhados entre `MainActivity` e `OverlayService`. Chaves: `escala`, `opacidade`, `intervalo`, `x`, `y`, `ativo`, `cpu_fonte`.

## Compilação

Este projeto **não utiliza Gradle**. É compilado diretamente com as ferramentas do Android SDK.

### Pré-requisitos

- `aapt2`, `javac`, `jar`, `keytool`, `zipalign`, `apksigner`, `unzip`, `zip`
- Android SDK Build-Tools 34.0.0 e Platform Android 34
- JDK 21
- `tools/android.jar` e `tools/r8.jar` (já incluídos no repositório)

### Build no Linux/WSL

No WSL (recomendado para este projeto), use o script wrapper:

```bash
./tools/wsl-build.sh
```

Este script cria symlinks temporários para as ferramentas `.exe` do Windows e chama `build.sh`. As variáveis `ANDROID_SDK`, `JAVA_HOME`, `ANDROID_JAR` e `R8_JAR` podem ser sobrescritas.

### Build direto

```bash
./build.sh
```

O APK gerado ficará em `out/mostradores-<versão>-debug.apk`. A versão é lida de `version.txt` (formato `X.Y.Z`), e `versionCode` é calculado como `X*10000 + Y*100 + Z`.

### Processo de build

1. Limpa `build/` e `out/`
2. `aapt2 compile` dos recursos
3. `aapt2 link` gerando `R.java` e `base.apk`
4. `javac --release 8` compilando fontes Java
5. Empacotamento em JAR
6. Dex com D8 (`r8.jar`)
7. Alinhamento com `zipalign`
8. Assinatura com keystore de debug (`tools/debug.keystore`)

## Instalação e uso

1. Instale o APK no dispositivo
2. Abra o app "Mostradores"
3. Conceda a permissão de "Sobrepor a outros apps"
4. (Opcional) Conceda permissão de notificações (Android 13+)
5. (Opcional) Conceda acesso a todos os arquivos para monitorar armazenamento externo
6. Ajuste tamanho, opacidade e intervalo conforme desejado
7. Toque em "Iniciar monitores" para exibir os mostradores flutuantes
8. Arraste o painel para reposicioná-lo. Toque em ✕ para fechar

## Debug

A atividade `DebugActivity` permite criar e remover um arquivo `dummy_5gb.bin` no armazenamento externo para testar a medição do cartão SD. Ela não está acessível pela interface principal, mas pode ser aberta via Intent ou ADB:

```bash
adb shell am start -n com.mostradores.overlay/.DebugActivity
```

## Tecnologias

- **Java puro** (sem Kotlin)
- **API Android Framework** (sem AndroidX)
- **Sem dependências externas**
- **Programmatic UI** para a sobreposição
- **Canvas API** para desenho dos mostradores

## Versão atual

`11.0.2`

## Licença

Este projeto é de uso pessoal/experimental.
