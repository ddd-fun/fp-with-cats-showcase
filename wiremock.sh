#!/bin/bash
docker run -it --rm -p 8989:8080 -v $PWD/docker/tooling/wiremock:/home/wiremock rodolpheche/wiremock
