-- Schéma principal — tableau de bord d'assiduité Attijari Bank

CREATE TABLE team (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL UNIQUE,
    is_synthetic BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE employee (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(40)  NOT NULL UNIQUE,      -- User_ID du fichier de pointage
    display_name        VARCHAR(120) NOT NULL,
    team_id             BIGINT REFERENCES team (id),
    hire_date           DATE,
    hire_date_fictive   BOOLEAN      NOT NULL DEFAULT TRUE, -- FALSE si la date provient du fichier source
    is_synthetic        BOOLEAN      NOT NULL DEFAULT FALSE,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_employee_team ON employee (team_id);

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'RH', 'MANAGER')),
    team_id       BIGINT REFERENCES team (id),             -- obligatoire pour un MANAGER
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_manager_team CHECK (role <> 'MANAGER' OR team_id IS NOT NULL)
);

CREATE TABLE terminal (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(40)  NOT NULL UNIQUE,     -- ex. TB, TA, SS1 B
    label                VARCHAR(120) NOT NULL,
    is_building_boundary BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE import_batch (
    id                 BIGSERIAL PRIMARY KEY,
    source_file        VARCHAR(255) NOT NULL,
    file_sha256        VARCHAR(64)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,              -- RUNNING, SUCCESS, FAILED
    rows_read          INT          NOT NULL DEFAULT 0,
    rows_inserted      INT          NOT NULL DEFAULT 0,
    rows_already_known INT          NOT NULL DEFAULT 0,    -- doublons exacts (ré-import) ignorés
    rows_rejected      INT          NOT NULL DEFAULT 0,
    near_duplicates    INT          NOT NULL DEFAULT 0,
    report             JSONB,
    triggered_by       VARCHAR(60),
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at        TIMESTAMPTZ
);

CREATE TABLE raw_punch (
    id              BIGSERIAL PRIMARY KEY,
    employee_id     BIGINT       NOT NULL REFERENCES employee (id),
    punch_time      TIMESTAMP    NOT NULL,                 -- heure locale (Africa/Tunis), sans fuseau
    direction       VARCHAR(3)   NOT NULL CHECK (direction IN ('IN', 'OUT')),
    terminal_id     BIGINT       NOT NULL REFERENCES terminal (id),
    terminal_raw    VARCHAR(80)  NOT NULL,
    is_duplicate    BOOLEAN      NOT NULL DEFAULT FALSE,   -- doublon proche (quelques secondes), ignoré dans les calculs
    source_file     VARCHAR(255) NOT NULL,
    import_batch_id BIGINT       NOT NULL REFERENCES import_batch (id),
    CONSTRAINT uq_raw_punch UNIQUE (employee_id, punch_time, terminal_id, direction)
);
CREATE INDEX idx_raw_punch_emp_time ON raw_punch (employee_id, punch_time);

CREATE TABLE public_holiday (
    id           BIGSERIAL PRIMARY KEY,
    holiday_date DATE         NOT NULL UNIQUE,
    label        VARCHAR(120) NOT NULL,
    is_religious BOOLEAN      NOT NULL DEFAULT FALSE,
    to_verify    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE special_period (
    id             BIGSERIAL PRIMARY KEY,
    kind           VARCHAR(20)  NOT NULL CHECK (kind IN ('SEANCE_UNIQUE', 'RAMADAN', 'AUTRE')),
    label          VARCHAR(120) NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    ref_start_time TIME,                                   -- NULL = l'horaire personnel reste la référence
    ref_end_time   TIME,
    CONSTRAINT chk_period_dates CHECK (end_date >= start_date)
);

CREATE TABLE work_schedule (
    id                BIGSERIAL PRIMARY KEY,
    employee_id       BIGINT      NOT NULL UNIQUE REFERENCES employee (id),
    start_time        TIME        NOT NULL,
    end_time          TIME,
    tolerance_minutes INT         NOT NULL DEFAULT 10,
    source            VARCHAR(10) NOT NULL CHECK (source IN ('AUTO', 'RH')),
    updated_by        VARCHAR(60),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE leave (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT       NOT NULL REFERENCES employee (id),
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    leave_type  VARCHAR(30)  NOT NULL DEFAULT 'CONGE_ANNUEL',
    comment     VARCHAR(255),
    created_by  VARCHAR(60),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_leave_dates CHECK (end_date >= start_date)
);
CREATE INDEX idx_leave_emp ON leave (employee_id, start_date);

CREATE TABLE app_settings (
    setting_key   VARCHAR(60)  PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    description   VARCHAR(255)
);

CREATE TABLE daily_attendance (
    id                   BIGSERIAL PRIMARY KEY,
    employee_id          BIGINT      NOT NULL REFERENCES employee (id),
    work_date            DATE        NOT NULL,
    working_day          BOOLEAN     NOT NULL,             -- FALSE : samedi/dimanche/férié travaillé
    status               VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT', 'RETARD', 'ABSENCE_PONCTUELLE', 'ABSENCE_LONGUE', 'CONGE')),
    first_in             TIME,
    last_out             TIME,
    late_minutes         INT         NOT NULL DEFAULT 0,
    worked_minutes       INT,                              -- somme des paires IN→OUT valides sur portes frontière
    punch_count          INT         NOT NULL DEFAULT 0,
    boundary_punch_count INT         NOT NULL DEFAULT 0,
    duplicate_count      INT         NOT NULL DEFAULT 0,
    incomplete           BOOLEAN     NOT NULL DEFAULT FALSE,
    reference_start      TIME,
    special_period_kind  VARCHAR(20),
    computed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily UNIQUE (employee_id, work_date)
);
CREATE INDEX idx_daily_date ON daily_attendance (work_date);

CREATE TABLE model_registry (
    id          BIGSERIAL PRIMARY KEY,
    version     VARCHAR(60)  NOT NULL UNIQUE,
    trained_at  TIMESTAMPTZ  NOT NULL,
    algorithm   VARCHAR(120) NOT NULL,
    metrics     JSONB        NOT NULL,
    thresholds  JSONB        NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT FALSE,
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_model_active ON model_registry (is_active) WHERE is_active;

CREATE TABLE prediction (
    id             BIGSERIAL PRIMARY KEY,
    employee_id    BIGINT       NOT NULL REFERENCES employee (id),
    reference_date DATE         NOT NULL,                  -- dernière journée de données utilisée
    target_date    DATE         NOT NULL,                  -- D1 : jour cible ; W1 : 1er jour de la fenêtre
    target_end     DATE,                                   -- W1 : dernier jour ouvré de la fenêtre
    horizon        VARCHAR(2)   NOT NULL CHECK (horizon IN ('D1', 'W1')),
    status         VARCHAR(30)  NOT NULL CHECK (status IN ('OK', 'INSUFFICIENT_HISTORY')),
    probability    NUMERIC(6, 5),
    risk_level     VARCHAR(10) CHECK (risk_level IN ('FAIBLE', 'MODERE', 'ELEVE')),
    expected_days  NUMERIC(5, 2),
    top_factors    JSONB,
    model_version  VARCHAR(60)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_prediction_emp_date ON prediction (employee_id, target_date);
CREATE INDEX idx_prediction_created ON prediction (created_at);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(60)  NOT NULL,
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(60),
    entity_id   VARCHAR(60),
    details     VARCHAR(500),
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_log (created_at);
