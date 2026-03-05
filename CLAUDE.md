# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

sql2bot is a Spring Boot 4.0.3 application using Java 17. The project integrates with MCP toolbox for advertising and media account management queries, likely converting SQL queries into chatbot-friendly responses.

## Build & Development Commands

```bash
# Build the project
./mvnw clean install

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=Sql2botApplicationTests

# Run the application
./mvnw spring-boot:run

# Package as JAR
./mvnw package
```

## Architecture

**Package Structure**: `com.tecdo.mac.sql2bot`

**Technology Stack**:
- Spring Boot 4.0.3
- Java 17
- Maven (with wrapper)

**MCP Toolbox Integration**: The project has access to MCP toolbox functions for:
- Advertising account management (quotas, spend, rebates)
- Media account operations (account info, BC auth, refresh tasks)
- Order processing (recharge, clearance, account opening)
- User lookups (UA users, BOP users, report access)
- Financial operations (refunds, payments, special approvals)

When implementing features, consider using these MCP tools for data retrieval rather than direct database queries where applicable.

## Code Conventions

- Follow Spring Boot best practices for controller/service/repository layering
- Use the existing package structure: `com.tecdo.mac.sql2bot`
- Leverage Spring Boot 4.0.3 features (latest stable release)
