# Image d'initialisation : l'image Garage n'a pas de shell.
FROM alpine:3.24
RUN apk add --no-cache curl jq aws-cli
COPY init.sh /init.sh
ENTRYPOINT ["sh", "/init.sh"]
