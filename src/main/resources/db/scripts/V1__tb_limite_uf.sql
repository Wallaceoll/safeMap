-- =====================================================================
-- V1 — Limites oficiais das Unidades da Federação (IBGE) para validação
--      geográfica com PostGIS.
--
-- Execução: manual, no SQL Editor do Supabase (o projeto não usa Flyway).
-- Idempotente e não destrutivo: pode ser executado mais de uma vez.
--
-- Os polígonos NÃO são inseridos aqui: a aplicação baixa a malha oficial
-- do IBGE e preenche a tabela automaticamente ao iniciar
-- (LimiteUfImportService). Ver docs/limites-uf.md.
-- =====================================================================

-- PostGIS (no Supabase fica no schema "extensions"; sem efeito se já estiver ativo)
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions;

CREATE TABLE IF NOT EXISTS public.tb_limite_uf (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo_ibge  VARCHAR(2)   NOT NULL UNIQUE,
    nome         VARCHAR(100) NOT NULL,
    uf           CHAR(2)      NOT NULL UNIQUE,
    geometria    GEOMETRY(MULTIPOLYGON, 4326) NOT NULL,
    fonte        VARCHAR(300) NOT NULL,
    importado_em TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_tb_limite_uf_geometria_valida CHECK (ST_IsValid(geometria))
);

CREATE INDEX IF NOT EXISTS idx_tb_limite_uf_geometria
    ON public.tb_limite_uf USING GIST (geometria);

-- ---------------------------------------------------------------------
-- Conferência (após a aplicação importar os limites):
--
-- SELECT count(*)                         AS ufs,           -- esperado: 27
--        bool_and(ST_IsValid(geometria))  AS todas_validas, -- esperado: true
--        array_agg(DISTINCT ST_SRID(geometria)) AS srids    -- esperado: {4326}
-- FROM public.tb_limite_uf;
--
-- SELECT uf FROM public.tb_limite_uf
-- WHERE ST_Covers(geometria, ST_SetSRID(ST_MakePoint(-34.88, -8.06), 4326)); -- Recife → PE
-- ---------------------------------------------------------------------
