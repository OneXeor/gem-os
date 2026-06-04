FROM python:3.12-bookworm

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

RUN useradd -ms /bin/zsh gem

USER gem
WORKDIR /workspace/gem-os

ENV GEM_HOME=/workspace/gem-os
ENV PYTHONPATH=/workspace/gem-os/packages:/workspace/gem-os
ENV PYTHONUNBUFFERED=1
