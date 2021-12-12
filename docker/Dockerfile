ARG java=11-jre

FROM openjdk:${java}

ARG version="3.4.0-20210923"
ARG user="heritrix"
ARG userid=1000

LABEL version=${version}
LABEL user=${user}/$userid

# create user
RUN \
    groupadd -g $userid $user && \
    useradd -r -u $userid -g $user $user

WORKDIR /opt

# download latest version according to:
#   https://github.com/internetarchive/heritrix3/releases/tag/3.4.0-20210923
RUN \
    wget -O heritrix-${version}-dist.zip https://repo1.maven.org/maven2/org/archive/heritrix/heritrix/${version}/heritrix-${version}-dist.zip && \
    unzip heritrix-${version}-dist.zip && \
    rm heritrix-${version}-dist.zip && \
    mv heritrix-${version} heritrix && \
    chmod u+x heritrix/bin/heritrix && \
    chown -R $user:$user /opt/heritrix

ADD entrypoint.sh /opt/entrypoint.sh
RUN chmod +x /opt/entrypoint.sh && \
    chown $user:$user /opt/entrypoint.sh

WORKDIR /opt/heritrix

USER $user

ENV HERITRIX_HOME /opt/heritrix
# let it run in the foreground, required for docker
ENV FOREGROUND true

# standard webport
# NOTE: that the webpage is via HTTPS only available!
EXPOSE 8443

CMD ["/opt/entrypoint.sh"]
