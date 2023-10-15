# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EasyPan is a full-stack cloud network disk (网盘) application — a Chinese cloud storage platform similar to Baidu Cloud. Users can upload, manage, preview, share files, and recover from a recycle bin. Admins can manage users, files, and system settings.

## Build & Run Commands

### Backend (Spring Boot + Maven)
```bash
# Build (skip tests)
cd easypan && mvn clean package -DskipTests

# Run (Spring Boot fat jar)
cd easypan && mvn spring-boot:run
```

The backend runs on port **7090** with context path `/api`. Main class: `com.easypan.EasyPanApplication`.

### Frontend (Vue 3 + Vite)
```bash
cd easypan-front && npm install
npm run dev       # dev server on port 1024
npm run build     # production build to dist/
```

The Vite dev server proxies `/api` to `http://123.60.171.78:7090`.

## Architecture

### Backend (easypan/)

**Package structure:**
- `controller/` — REST controllers. `BaseController` provides shared file-serving, download, and image helpers.
- `service/` + `service/impl/` — Business logic layer.
- `mappers/` — MyBatis Plus DAOs (extend `BaseDao` which extends `BaseMapper<T>`). XML mapper files live in `src/main/resources/com/easypan/mappers/`.
- `entity/po/` — Persistent objects (POs) mapped to database tables.
- `entity/vo/` — View objects returned to the frontend.
- `entity/dto/` — Data transfer objects (session state, download codes, settings).
- `entity/enums/` — Enums for file categories, folder types, delete flags, upload status, etc.
- `entity/constants/` — Application constants (session keys, file paths, lengths).
- `convert/` — MapStruct converters between PO and VO.
- `query/` — Query parameter objects (extend `Query` which wraps MyBatis Plus `QueryWrapper`).
- `config/` — Spring configuration (CORS, MyBatis Plus, Redis, Web MVC, app config).
- `annotation/` — Custom annotations: `@GlobalInterceptor` (auth/param check) and `@VerifyParam` (field validation).
- `aspect/` — `GlobalOperationAspect` intercepts `@GlobalInterceptor`-annotated methods for login check, admin check, and parameter validation.
- `utils/` — Utilities: `RedisComponent` (Redis operations), `Result` (API response wrapper), `StringUtils`, `DateUtils`, `VerifyUtils`, etc.
- `exception/` — `FastException` (runtime exception), `ErrorCode` (error code constants), `FastExceptionHandler` (global exception handler).
- `handler/` — `FieldMetaObjectHandler` for MyBatis Plus auto-fill fields.

**Request flow:** Controller → `@GlobalInterceptor` (AOP auth/validation) → Service → Mapper (DAO) → DB.

**Key patterns:**
- `@GlobalInterceptor(checkLogin = true, checkAdmin = false, checkParams = false)` on controller methods controls auth. The AOP aspect reads the session from `HttpSession`.
- `@VerifyParam(required = true, max = 100, regex = ...)` on method parameters or PO fields triggers validation in the same aspect.
- All API responses are wrapped in `Result<T>` (code + message + data).
- `RedisComponent` handles session caching, email verification codes, download codes, and system settings.
- File storage is local disk-based under `project.folder` (configured in `application.properties`).

### Frontend (easypan-front/)

**Directory structure:**
- `src/views/` — Page-level components organized by feature: `main/` (file browser), `share/` (my shares), `recycle/`, `admin/`, `webshare/` (public share access).
- `src/components/` — Reusable components: `Table`, `Dialog`, `Window`, `Navigation`, `Avatar`, `FolderSelect`, and `preview/` subdirectory for file previewers (video, image, PDF, doc, excel, music, text, download fallback).
- `src/router/index.js` — Vue Router routes with `needLogin` meta for auth guard.
- `src/stores/` — Pinia stores (`useInfoStore`).
- `src/utils/` — `Request` (axios wrapper), `Message` (toast), `Confirm` (dialog), `Verify` (validation), `Utils`.

**Key patterns:**
- Utility modules (`Request`, `Message`, `Confirm`, `Verify`, `Utils`) are registered as global properties on the Vue app instance, accessed via `this.Request`, `this.Message`, etc.
- Auth is session-based via cookies. The router guard checks `VueCookies.get("userInfo")` against `meta.needLogin`.
- The `Preview` component dynamically selects the correct previewer (`PreviewVideo`, `PreviewImage`, `PreviewPdf`, etc.) based on file category.
- Element Plus is the UI framework; global components (Icon, Table, Dialog, etc.) are registered in `main.js`.

## Configuration

- **Backend config:** `easypan/src/main/resources/application.properties` — DB connection, Redis, mail server, admin emails, file upload limits.
- **Frontend proxy:** `easypan-front/vite.config.js` — dev proxy target, port, build chunk splitting.
- **Admin emails** are comma-separated in `admin.emails`. Users matching these emails get admin privileges.