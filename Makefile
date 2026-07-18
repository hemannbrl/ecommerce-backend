# Convenience targets for the e-commerce backend monorepo.
# `make help` lists them.

.PHONY: help setup django spring express

help:  ## Show this help
	@grep -E '^[a-z-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-10s %s\n", $$1, $$2}'

setup:  ## Create the shared DB and load schema + seed (local, non-Docker)
	./db/setup.sh

django:  ## Run the Django backend + its DB with Docker (auto-seeds on first boot)
	cd django && docker compose up --build

spring:  ## Run the Spring Boot backend + its DB with Docker (auto-seeds)
	cd spring && docker compose up --build

express:  ## Run the Express backend + its DB with Docker (auto-seeds)
	cd express && docker compose up --build
