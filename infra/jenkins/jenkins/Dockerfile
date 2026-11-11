# Custom Jenkins controller for OpenCRM CPQ monorepo CI
# Build context: infra/jenkins (see docker-compose.jenkins.yml)
# Jenkins WAR on image JDK 25 (LTS ≥2.541.1 supports Java 25; pinned 2.580.2-lts-jdk25).
FROM jenkins/jenkins:2.580.2-lts-jdk25

USER root

# Toolchain used by pipeline stages (mirror GitHub Actions ci.yml)
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    curl \
    ca-certificates \
    xz-utils \
    python3 \
    python3-pip \
    python3-venv \
    docker.io \
    && rm -rf /var/lib/apt/lists/*

# Node.js 24.22.3 LTS (matches GHA setup-node in ci.yml). Pin — do not float on nodesource latest.
ARG NODE_VERSION=24.22.3
RUN set -eux; \
    arch="$(dpkg --print-architecture)"; \
    case "$arch" in \
      amd64) node_arch=x64 ;; \
      arm64) node_arch=arm64 ;; \
      *) echo "unsupported arch: $arch" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${node_arch}.tar.xz" \
      | tar -xJ -C /usr/local --strip-components=1; \
    node -v | grep -F "v${NODE_VERSION}"

# Temurin 25 LTS for Maven (kept separate from image JDK for smallest change).
# Do not prepend JAVA_HOME/bin to PATH: jenkins.sh `exec java` must keep
# resolving the image JDK for the controller WAR.
RUN set -eux; \
    arch="$(dpkg --print-architecture)"; \
    case "$arch" in \
      amd64) jdk_arch=x64 ;; \
      arm64) jdk_arch=aarch64 ;; \
      *) echo "unsupported arch: $arch" >&2; exit 1 ;; \
    esac; \
    mkdir -p /opt/java/temurin-25; \
    curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/${jdk_arch}/jdk/hotspot/normal/eclipse?project=jdk" \
      | tar -xz --strip-components=1 -C /opt/java/temurin-25; \
    /opt/java/temurin-25/bin/java -version

# Maven 3.9.x (compiler/runtime via JAVA_HOME Temurin 25)
ARG MAVEN_VERSION=3.9.9
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    | tar -xz -C /opt \
    && ln -s "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven
ENV MAVEN_HOME=/opt/maven
ENV JAVA_HOME=/opt/java/temurin-25
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# ai-assist scripts (stdlib-only Python client)
COPY ai-assist /opt/ai-assist

# Install Jenkins plugins
COPY jenkins/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# JCasC
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc_configs/jenkins.yaml
COPY jenkins/casc/jenkins.yaml /usr/share/jenkins/ref/casc_configs/jenkins.yaml

RUN chmod +x /opt/ai-assist/*.py /opt/ai-assist/*.sh 2>/dev/null || true \
    && chown -R jenkins:jenkins /opt/ai-assist

# Allow jenkins user to run docker CLI when socket is mounted (dev/staging only)
RUN usermod -aG docker jenkins || true

USER jenkins
