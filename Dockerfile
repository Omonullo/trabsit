FROM clojure:alpine as dev-env

COPY --from=jrottenberg/ffmpeg:alpine /usr/local /usr/local/
COPY --from=jrottenberg/ffmpeg:alpine /usr/lib /usr/lib
COPY --from=jrottenberg/ffmpeg:alpine /lib /lib

FROM clojure:alpine as build-env

COPY --from=node:12-alpine3.12 /usr/local/bin /usr/local/bin
COPY --from=node:12-alpine3.12 /usr/local/lib /usr/local/lib

WORKDIR /app

COPY project.clj /app/
RUN lein deps

COPY package.json package-lock.json /app/
COPY resources/app/react/offense-map/package.json resources/app/react/offense-map/package-lock.json /app/resources/app/react/offense-map/
COPY resources/app/react/report-review/package.json resources/app/react/report-review/package-lock.json /app/resources/app/react/report-review/
COPY patches/ /app/patches
RUN echo "unsafe-perm = true" >> ~/.npmrc && npm ci --quiet \
    && cd /app/resources/app/react/offense-map/ && npm ci --quiet \
    && cd /app/resources/app/react/report-review/ && npm ci --quiet

COPY . /app
RUN lein uberjar

FROM clojure:alpine as final-env

COPY --from=jrottenberg/ffmpeg:alpine /usr/local /usr/local/
COPY --from=jrottenberg/ffmpeg:alpine /usr/lib /usr/lib
COPY --from=jrottenberg/ffmpeg:alpine /lib /lib
COPY --from=build-env /app/target/uberjar/jarima.jar /app/jarima.jar
COPY --from=build-env /app/CHECKS /app/CHECKS
WORKDIR /app
ENTRYPOINT ["sh", "-c",  "java $JAVA_OPTS -XX:-OmitStackTraceInFastThrow -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -jar /app/jarima.jar"]
