-- =========================================================================
-- CRYPT0 CR3W CENTRAL [CCC] [by m0lt0rn] - SUPABASE DATABASE SCHEMA
-- OPSEC SAFE // ANTI-DOXING // ROW LEVEL SECURITY // RPC FUNCTIONS
-- =========================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -------------------------------------------------------------------------
-- 1. TABLA: PROFILES (Perfiles de Operativos)
-- OPSEC: Exclusivamente operative_handle (alias). Cero emails para prevenir doxing.
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  operative_handle TEXT UNIQUE NOT NULL,
  crew_id TEXT DEFAULT 'CCC',
  telemetry_score BIGINT DEFAULT 0,
  targets_indexed INT DEFAULT 0,
  wallets_matched INT DEFAULT 0,
  avg_hit NUMERIC DEFAULT 0,
  role TEXT DEFAULT 'OPERATIVE',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_handle ON public.profiles(operative_handle);
CREATE INDEX IF NOT EXISTS idx_profiles_crew_score ON public.profiles(crew_id, telemetry_score DESC);
CREATE INDEX IF NOT EXISTS idx_profiles_global_score ON public.profiles(telemetry_score DESC);

-- -------------------------------------------------------------------------
-- 2. TABLA: INTEL_TARGETS (Objetivos e Inteligencia de la Crew)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.intel_targets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ip_address TEXT NOT NULL,
  account_id TEXT,
  wallet_address TEXT,
  firewall_lvl INT DEFAULT 0,
  installed_software JSONB DEFAULT '{}'::jsonb,
  contributor TEXT NOT NULL, -- Almacena exclusivamente el operative_handle
  crew_id TEXT NOT NULL DEFAULT 'CCC',
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  CONSTRAINT uq_intel_target_ip_crew UNIQUE (ip_address, crew_id)
);

CREATE INDEX IF NOT EXISTS idx_intel_targets_ip ON public.intel_targets(ip_address);
CREATE INDEX IF NOT EXISTS idx_intel_targets_wallet ON public.intel_targets(wallet_address);
CREATE INDEX IF NOT EXISTS idx_intel_targets_crew ON public.intel_targets(crew_id);
CREATE INDEX IF NOT EXISTS idx_intel_targets_contributor ON public.intel_targets(contributor);

-- -------------------------------------------------------------------------
-- 3. TABLA: LOGS_ACTIVITY (Logs y Transacciones de Cripto)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.logs_activity (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  target_ip TEXT,
  crypto_stolen NUMERIC DEFAULT 0,
  log_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  contributor TEXT NOT NULL, -- Almacena exclusivamente el operative_handle
  crew_id TEXT NOT NULL DEFAULT 'CCC',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_logs_timestamp ON public.logs_activity(log_timestamp);
CREATE INDEX IF NOT EXISTS idx_logs_crew ON public.logs_activity(crew_id);

-- -------------------------------------------------------------------------
-- 4. TABLA: CREWS (Clanes y Rankings Globales)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.crews (
  crew_id TEXT PRIMARY KEY,
  crew_name TEXT NOT NULL,
  crew_password TEXT,
  creator_operative TEXT,
  total_score BIGINT DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Inserción de Crews iniciales por defecto
INSERT INTO public.crews (crew_id, crew_name, crew_password, creator_operative, total_score)
VALUES 
  ('CCC', 'Crypt0 Cr3w Central', 'werc-ccc', 'm0lt0rn', 450),
  ('ALPHA', 'Alpha Syndicate', 'alpha123', 'Admin_Alpha', 245),
  ('CYBER_NET_X', 'CyberNet X', 'CrewPass2026', 'Ghost_Sec', 110)
ON CONFLICT (crew_id) DO NOTHING;

-- -------------------------------------------------------------------------
-- 5. RPC FUNCTION: add_telemetry_points (Cálculo Atómico de Puntos)
-- Lógica: +10 IP, +15 ID, +25 Wallet, +20 Apps
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.add_telemetry_points(
  p_handle TEXT,
  p_crew_id TEXT,
  p_points INT,
  p_targets_count INT DEFAULT 0,
  p_wallets_count INT DEFAULT 0,
  p_avg_hit NUMERIC DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_profile RECORD;
BEGIN
  IF p_handle IS NULL OR TRIM(p_handle) = '' THEN
    RETURN jsonb_build_object('success', false, 'error', 'Handle cannot be empty');
  END IF;

  -- Upsert perfil con suma atómica de puntuación
  INSERT INTO public.profiles (
    operative_handle,
    crew_id,
    telemetry_score,
    targets_indexed,
    wallets_matched,
    avg_hit,
    updated_at
  )
  VALUES (
    TRIM(p_handle),
    COALESCE(NULLIF(TRIM(p_crew_id), ''), 'CCC'),
    GREATEST(0, p_points),
    GREATEST(0, p_targets_count),
    GREATEST(0, p_wallets_count),
    COALESCE(p_avg_hit, 0),
    NOW()
  )
  ON CONFLICT (operative_handle) DO UPDATE SET
    crew_id = CASE 
      WHEN EXCLUDED.crew_id IS NOT NULL AND EXCLUDED.crew_id <> 'UNASSIGNED' THEN EXCLUDED.crew_id 
      ELSE public.profiles.crew_id 
    END,
    telemetry_score = public.profiles.telemetry_score + EXCLUDED.telemetry_score,
    targets_indexed = public.profiles.targets_indexed + EXCLUDED.targets_indexed,
    wallets_matched = public.profiles.wallets_matched + EXCLUDED.wallets_matched,
    avg_hit = CASE 
      WHEN p_avg_hit IS NOT NULL AND p_avg_hit > 0 THEN p_avg_hit 
      ELSE public.profiles.avg_hit 
    END,
    updated_at = NOW()
  RETURNING * INTO v_profile;

  -- Actualizar puntuación total del clan (Crew)
  IF p_crew_id IS NOT NULL AND TRIM(p_crew_id) <> '' THEN
    INSERT INTO public.crews (crew_id, crew_name, total_score)
    VALUES (TRIM(p_crew_id), TRIM(p_crew_id), p_points)
    ON CONFLICT (crew_id) DO UPDATE SET
      total_score = public.crews.total_score + p_points,
      updated_at = NOW();
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'operative_handle', v_profile.operative_handle,
    'telemetry_score', v_profile.telemetry_score,
    'targets_indexed', v_profile.targets_indexed,
    'wallets_matched', v_profile.wallets_matched
  );
END;
$$;

-- -------------------------------------------------------------------------
-- 6. RPC FUNCTION: get_peak_window (Cálculo Dinámico de PEAK WINDOW)
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_peak_window(p_crew_id TEXT DEFAULT NULL)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_hour INT;
  v_max_crypto NUMERIC;
  v_total_records INT;
  v_window TEXT;
BEGIN
  -- Agrupar por hora de log_timestamp y sumar volumen de cripto robado
  SELECT
    EXTRACT(HOUR FROM log_timestamp)::INT AS h,
    SUM(crypto_stolen) AS total_crypto,
    COUNT(*) AS records_count
  INTO v_hour, v_max_crypto, v_total_records
  FROM public.logs_activity
  WHERE (p_crew_id IS NULL OR TRIM(p_crew_id) = '' OR crew_id = p_crew_id)
  GROUP BY EXTRACT(HOUR FROM log_timestamp)
  ORDER BY total_crypto DESC, records_count DESC
  LIMIT 1;

  -- Si no hay registros de logs_activity, evaluar timestamps de intel_targets
  IF v_hour IS NULL OR v_max_crypto IS NULL OR v_max_crypto = 0 THEN
    SELECT
      EXTRACT(HOUR FROM updated_at)::INT AS h,
      COUNT(*) * 500 AS total_crypto,
      COUNT(*) AS records_count
    INTO v_hour, v_max_crypto, v_total_records
    FROM public.intel_targets
    WHERE (p_crew_id IS NULL OR TRIM(p_crew_id) = '' OR crew_id = p_crew_id)
    GROUP BY EXTRACT(HOUR FROM updated_at)
    ORDER BY records_count DESC
    LIMIT 1;
  END IF;

  IF v_hour IS NULL THEN
    RETURN jsonb_build_object(
      'peak_window', 'CALCULATING... // NEED MORE LOGS',
      'max_crypto', 0,
      'total_records', 0,
      'is_calculating', true
    );
  END IF;

  v_window := TO_CHAR(v_hour, 'FM00') || ':00 - ' || TO_CHAR((v_hour + 1) % 24, 'FM00') || ':00';

  RETURN jsonb_build_object(
    'peak_window', v_window,
    'max_crypto', COALESCE(v_max_crypto, 0),
    'total_records', COALESCE(v_total_records, 0),
    'is_calculating', false
  );
END;
$$;

-- -------------------------------------------------------------------------
-- 7. OPSEC POLICIES (Row Level Security - RLS)
-- -------------------------------------------------------------------------
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.intel_targets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.logs_activity ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crews ENABLE ROW LEVEL SECURITY;

-- Profiles: Lectura pública de campos OPSEC / Modificación de perfil
DROP POLICY IF EXISTS "Profiles public read for rankings" ON public.profiles;
CREATE POLICY "Profiles public read for rankings"
  ON public.profiles FOR SELECT
  USING (true);

DROP POLICY IF EXISTS "Profiles upsert by operative" ON public.profiles;
CREATE POLICY "Profiles upsert by operative"
  ON public.profiles FOR ALL
  USING (true)
  WITH CHECK (operative_handle IS NOT NULL AND operative_handle <> '');

-- Intel Targets: Lectura de objetivos por Crew / Inserción por Operativo
DROP POLICY IF EXISTS "Intel targets select" ON public.intel_targets;
CREATE POLICY "Intel targets select"
  ON public.intel_targets FOR SELECT
  USING (true);

DROP POLICY IF EXISTS "Intel targets insert" ON public.intel_targets;
CREATE POLICY "Intel targets insert"
  ON public.intel_targets FOR INSERT
  WITH CHECK (contributor IS NOT NULL AND contributor <> '');

DROP POLICY IF EXISTS "Intel targets update" ON public.intel_targets;
CREATE POLICY "Intel targets update"
  ON public.intel_targets FOR UPDATE
  USING (true)
  WITH CHECK (contributor IS NOT NULL AND contributor <> '');

DROP POLICY IF EXISTS "Intel targets delete" ON public.intel_targets;
CREATE POLICY "Intel targets delete"
  ON public.intel_targets FOR DELETE
  USING (true);

-- Logs Activity: Registro y consulta de logs
DROP POLICY IF EXISTS "Logs activity select" ON public.logs_activity;
CREATE POLICY "Logs activity select"
  ON public.logs_activity FOR SELECT
  USING (true);

DROP POLICY IF EXISTS "Logs activity insert" ON public.logs_activity;
CREATE POLICY "Logs activity insert"
  ON public.logs_activity FOR INSERT
  WITH CHECK (contributor IS NOT NULL AND contributor <> '');

-- Crews: Consulta y creación de clanes
DROP POLICY IF EXISTS "Crews select for leaderboard" ON public.crews;
CREATE POLICY "Crews select for leaderboard"
  ON public.crews FOR SELECT
  USING (true);

DROP POLICY IF EXISTS "Crews insert/update" ON public.crews;
CREATE POLICY "Crews insert/update"
  ON public.crews FOR ALL
  USING (true)
  WITH CHECK (crew_id IS NOT NULL AND crew_id <> '');

-- -------------------------------------------------------------------------
-- 8. TABLAS: FEED_POSTS & FEED_COMMENTS (Foro/Chat Interactivo)
-- OPSEC: autor exclusivamente operative_handle.
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.feed_posts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  scope TEXT NOT NULL DEFAULT 'CREW', -- 'CREW' o 'GLOBAL'
  crew_id TEXT NOT NULL DEFAULT 'CCC',
  author TEXT NOT NULL,
  title TEXT DEFAULT '',
  tag TEXT NOT NULL DEFAULT 'INTEL', -- '[INTEL]', '[PLAN]', '[DISCUSIÓN]', '[ANUNCIO]'
  content TEXT NOT NULL,
  upvotes INT DEFAULT 0,
  comments_count INT DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feed_posts_scope_crew ON public.feed_posts(scope, crew_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.feed_comments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id UUID REFERENCES public.feed_posts(id) ON DELETE CASCADE,
  author TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feed_comments_post ON public.feed_comments(post_id, created_at ASC);

ALTER TABLE public.feed_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.feed_comments ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Feed posts select"
  ON public.feed_posts FOR SELECT
  USING (true);

CREATE POLICY "Feed posts insert"
  ON public.feed_posts FOR INSERT
  WITH CHECK (author IS NOT NULL AND author <> '');

CREATE POLICY "Feed comments select"
  ON public.feed_comments FOR SELECT
  USING (true);

CREATE POLICY "Feed comments insert"
  ON public.feed_comments FOR INSERT
  WITH CHECK (author IS NOT NULL AND author <> '');

