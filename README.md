# TradeMaster Backend

Spring Boot REST API backend for the **TradeMaster Inventory and Stock Management System**.

<p>
  <img src="https://img.shields.io/badge/Java-17-orange?style=flat-square" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring%20Boot-Backend-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/MySQL-Database-4479A1?style=flat-square&logo=mysql&logoColor=white" alt="MySQL">
  <img src="https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?style=flat-square&logo=springsecurity&logoColor=white" alt="Spring Security">
  <img src="https://img.shields.io/badge/Maven-Build-C71A36?style=flat-square&logo=apachemaven&logoColor=white" alt="Maven">
</p>

## Overview

TradeMaster Backend provides secure and modular REST APIs for inventory management, stock operations, sales, purchases, payments, financial workflows, reporting, notifications, audit logging, and user administration.

This backend is designed to work with the TradeMaster Angular frontend.

## Related Repository

- Angular Frontend: https://github.com/mdwoalinur/trademaster-angular

## Main Features

- JWT-based authentication and authorization
- Role-based access control
- User and role administration
- Product and product variation management
- Category, unit, supplier, customer, and warehouse management
- Purchase and purchase return workflows
- Sales and sales return workflows
- Inventory tracking and stock movement management
- Stock adjustments and warehouse transfers
- Low-stock monitoring
- Payment approval and posting workflow
- Financial account and ledger management
- Expense management
- Payment reconciliation and reporting
- Profit and loss reporting
- Sales, purchase, inventory, and tax reports
- Global search functionality
- Audit logging
- Notification management
- Email and invoice communication foundation
- File upload and secured download support

## Technology Stack

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- Hibernate
- MySQL
- Maven
- JWT Authentication
- REST API
- AOP Auditing
- SQL-based schema migration scripts

## Requirements

- Java 17
- MySQL
- Maven 3.9+
- A database named `trademaster` or a configured database URL

## Required Environment Variables

```text
TRADEMASTER_DB_URL=jdbc:mysql://localhost:3306/trademaster
TRADEMASTER_DB_USERNAME=root
TRADEMASTER_DB_PASSWORD=<database password>
TRADEMASTER_JWT_SECRET=<long random HS512 secret containing at least 64 characters>
