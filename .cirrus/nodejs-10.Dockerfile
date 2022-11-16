FROM eu.gcr.io/release-engineering-ci-prod/base:j11-latest

USER root

ENV NODE_VERSION v10.23.2

RUN  wget -U "nodejs" -q -O nodejs.tar.gz https://nodejs.org/dist/${NODE_VERSION}/node-${NODE_VERSION}-linux-x64.tar.gz \
    && tar -xzf "nodejs.tar.gz" -C /usr/local --strip-components=1 --no-same-owner \
    && rm nodejs.tar.gz \
    && ln -s /usr/local/bin/node /usr/local/bin/nodejs

ENV YARN_VERSION 1.22.5

RUN curl -fsSLO --compressed "https://yarnpkg.com/downloads/$YARN_VERSION/yarn-v$YARN_VERSION.tar.gz" \
      && mkdir -p /opt \
      && tar -xzf yarn-v$YARN_VERSION.tar.gz -C /opt/ \
      && ln -s /opt/yarn-v$YARN_VERSION/bin/yarn /usr/local/bin/yarn \
      && ln -s /opt/yarn-v$YARN_VERSION/bin/yarnpkg /usr/local/bin/yarnpkg \
      && rm yarn-v$YARN_VERSION.tar.gz


USER sonarsource
