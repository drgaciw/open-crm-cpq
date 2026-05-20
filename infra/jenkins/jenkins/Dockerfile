# Custom Jenkins controller for OpenCRM CPQ monorepo CI
# Build context: infra/jenkins (see docker-compose.jenkins.yml)
FROM jenkins/jenkins:2.492.3-lts-jdk21

USER root

# Toolchain used by pipeline stages (mirror GitHub Actions ci.yml)
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    curl \
    ca-certificates \
    python3 \
    python3-pip \
    python3-venv \
    docker.io \
    && rm -rf /var/lib/apt/lists/*

# Node.js 22 (matches frontend CI)
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Maven 3.9.x (JDK 21 already in base image)
ARG MAVEN_VERSION=3.9.9
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    | tar -xz -C /opt \
    && ln -s "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven
ENV MAVEN_HOME=/opt/maven
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
