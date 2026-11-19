ARG java=25-jre-noble
ARG version=3.19.0

FROM eclipse-temurin:${java} AS fetch-heritrix
ARG version
ADD https://github.com/internetarchive/heritrix3/releases/download/${version}/heritrix-${version}-dist.tar.gz /tmp/dist.tar.gz
RUN mkdir /opt/heritrix && tar -xzf /tmp/dist.tar.gz -C /opt/heritrix --strip-components=1

FROM eclipse-temurin:${java} AS fetch-contrib
ARG version
ARG TARGETARCH
ADD https://github.com/internetarchive/heritrix3/releases/download/${version}/heritrix-contrib-${version}-dist.tar.gz /tmp/dist.tar.gz
RUN mkdir /opt/heritrix && tar -xzf /tmp/dist.tar.gz -C /opt/heritrix --strip-components=1
# yt-dlp for ExtractorYoutubeDL
ADD --chmod=755 https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux /tmp/yt-dlp-amd64
ADD --chmod=755 https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64 /tmp/yt-dlp-arm64
RUN mv /tmp/yt-dlp-${TARGETARCH} /yt-dlp

FROM eclipse-temurin:${java} AS base
ARG version
ARG userid=1001
LABEL org.opencontainers.image.title="Heritrix" \
      org.opencontainers.image.version="${version}" \
      org.opencontainers.image.source="https://github.com/internetarchive/heritrix3"
RUN groupadd --gid ${userid} heritrix && \
    useradd --system --uid ${userid} --gid heritrix heritrix
COPY --chmod=755 entrypoint.sh /opt/entrypoint.sh
ENV HERITRIX_HOME=/opt/heritrix \
    FOREGROUND=true
# workaround for CVE-2026-114938, remove once the base image has a fixed JDK
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
# web UI (HTTPS only)
EXPOSE 8443
CMD ["/opt/entrypoint.sh"]

FROM base AS contrib
COPY --from=fetch-contrib /yt-dlp /usr/local/bin/yt-dlp
COPY --from=fetch-contrib --chown=heritrix:heritrix /opt/heritrix /opt/heritrix
WORKDIR /opt/heritrix
USER heritrix

FROM base AS heritrix
COPY --from=fetch-heritrix --chown=heritrix:heritrix /opt/heritrix /opt/heritrix
WORKDIR /opt/heritrix
USER heritrix
