# SMARTPREP

AI-based mock interview preparation portal.

## Stack

- **Frontend:** HTML, Tailwind CSS, JavaScript
- **Backend:** Java Spring Boot 3
- **Database:** MySQL

## Project structure

```
Smartprep_Ai/
├── frontend/          # Static UI pages
├── backend/           # Spring Boot API
└── database/          # MySQL schema
```

## Quick start

### 1. Database

Create a MySQL database and run:

```bash
mysql -u root -p < database/schema.sql
```

Update credentials in `backend/src/main/resources/application.properties`.

### 2. Backend

```bash
cd backend
mvn spring-boot:run
```

API runs at `http://localhost:8080`.

### 3. Frontend

Open `frontend/index.html` in a browser, or serve locally:

```bash
cd frontend
npx --yes serve -p 5500
```

Then visit `http://localhost:5500`.

## Default API base URL

Frontend calls `http://localhost:8080/api`.
