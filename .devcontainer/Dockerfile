FROM gradle:8.14.3-jdk21

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      bash \
      ca-certificates \
      curl \
      git \
      jq \
      less \
      make \
      openssh-client \
      ripgrep \
      sqlite3 \
      zsh \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace/gem-os

ENV GEM_HOME=/workspace/gem-os
