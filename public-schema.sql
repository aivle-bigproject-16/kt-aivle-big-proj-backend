--
-- PostgreSQL database dump
--

\restrict 0I1WAzVeV0LONqK786JKM2tzdlaQxz2FcenwGxpZoWFD2TPHPu5IbcGjfhNUSis

-- Dumped from database version 17.6
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA public;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: api_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_log (
    id bigint NOT NULL,
    direction character varying(20) NOT NULL,
    endpoint character varying(200) NOT NULL,
    inspection_id bigint,
    http_status integer,
    latency_ms integer,
    request_body jsonb,
    response_body jsonb,
    error_message text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: api_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.api_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.api_log_id_seq OWNED BY public.api_log.id;


--
-- Name: battery_cell; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.battery_cell (
    id bigint NOT NULL,
    cell_serial_no character varying(100) NOT NULL,
    purchase_id character varying(100),
    product_id character varying(100),
    model_name character varying(100),
    cell_type character varying(50),
    manufactured_date date,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    cell_size jsonb DEFAULT '{}'::jsonb
);


--
-- Name: battery_cell_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.battery_cell_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: battery_cell_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.battery_cell_id_seq OWNED BY public.battery_cell.id;


--
-- Name: battery_cell_image; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.battery_cell_image (
    id bigint NOT NULL,
    battery_cell_id bigint NOT NULL,
    image_type character varying(20) NOT NULL,
    capture_set character varying(20) DEFAULT 'INITIAL'::character varying NOT NULL,
    recapture_no integer DEFAULT 0 NOT NULL,
    bucket_name character varying(100) NOT NULL,
    object_key character varying(500) NOT NULL,
    storage_type character varying(30) NOT NULL,
    file_name character varying(255),
    file_size bigint,
    content_type character varying(100),
    width integer,
    height integer,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: battery_cell_image_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.battery_cell_image_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: battery_cell_image_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.battery_cell_image_id_seq OWNED BY public.battery_cell_image.id;


--
-- Name: defect_result; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.defect_result (
    id bigint NOT NULL,
    inspection_id bigint NOT NULL,
    inspection_image_id bigint,
    image_type character varying(20),
    label character varying(20) NOT NULL,
    defect_type character varying(255),
    confidence numeric(5,4),
    bbox jsonb,
    raw_response jsonb,
    latency_ms integer,
    model_name character varying(100),
    model_version character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: defect_result_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.defect_result_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: defect_result_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.defect_result_id_seq OWNED BY public.defect_result.id;


--
-- Name: inspection; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inspection (
    id bigint NOT NULL,
    inspection_batch_id bigint NOT NULL,
    battery_cell_id bigint NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    final_label character varying(20),
    failure_reason character varying(255),
    analyzed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    point_groups jsonb DEFAULT '[]'::jsonb,
    ai_request_id character varying(100),
    inspection_type character varying(20) NOT NULL,
    ai_retry_count integer DEFAULT 0 NOT NULL
);


--
-- Name: inspection_batch; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inspection_batch (
    id bigint NOT NULL,
    simulation_run_id bigint,
    requested_by bigint,
    status character varying(30) NOT NULL,
    failure_reason character varying(255),
    captured_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone
);


--
-- Name: inspection_batch_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inspection_batch_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inspection_batch_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inspection_batch_id_seq OWNED BY public.inspection_batch.id;


--
-- Name: inspection_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inspection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inspection_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inspection_id_seq OWNED BY public.inspection.id;


--
-- Name: inspection_image; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inspection_image (
    id bigint NOT NULL,
    inspection_id bigint NOT NULL,
    image_type character varying(20) NOT NULL,
    bucket_name character varying(100) NOT NULL,
    object_key character varying(500) NOT NULL,
    storage_type character varying(30) NOT NULL,
    file_name character varying(255),
    file_size bigint,
    content_type character varying(100),
    width integer,
    height integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    source_object_key character varying(500),
    battery_cell_image_id bigint
);


--
-- Name: inspection_image_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inspection_image_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inspection_image_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inspection_image_id_seq OWNED BY public.inspection_image.id;


--
-- Name: notice; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notice (
    id bigint NOT NULL,
    title character varying(255) NOT NULL,
    content text NOT NULL,
    user_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: notice_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notice_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notice_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notice_id_seq OWNED BY public.notice.id;


--
-- Name: reports_daily; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reports_daily (
    id bigint NOT NULL,
    report_date date NOT NULL,
    status character varying(30) NOT NULL,
    title character varying(255),
    summary_json jsonb,
    content text,
    failure_reason character varying(255),
    dispatched_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone
);


--
-- Name: reports_daily_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reports_daily_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reports_daily_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reports_daily_id_seq OWNED BY public.reports_daily.id;


--
-- Name: reports_daily_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reports_daily_item (
    id bigint NOT NULL,
    daily_report_id bigint NOT NULL,
    inspection_id bigint NOT NULL
);


--
-- Name: reports_daily_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reports_daily_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reports_daily_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reports_daily_item_id_seq OWNED BY public.reports_daily_item.id;


--
-- Name: reports_individual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reports_individual (
    id bigint NOT NULL,
    battery_cell_id bigint NOT NULL,
    representative_inspection_id bigint,
    source_inspection_ids jsonb,
    version integer DEFAULT 1 NOT NULL,
    status character varying(30) NOT NULL,
    title character varying(255),
    content text,
    failure_reason character varying(255),
    dispatched_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone
);


--
-- Name: reports_individual_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reports_individual_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reports_individual_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reports_individual_id_seq OWNED BY public.reports_individual.id;


--
-- Name: simulation_run; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.simulation_run (
    id bigint NOT NULL,
    requested_by bigint,
    batch_count integer NOT NULL,
    cells_per_batch integer NOT NULL,
    interval_ms integer NOT NULL,
    status character varying(20) NOT NULL,
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ended_at timestamp without time zone,
    battery_cell_count integer
);


--
-- Name: simulation_run_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.simulation_run_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: simulation_run_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.simulation_run_id_seq OWNED BY public.simulation_run.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(50) NOT NULL,
    password_hash character varying(255) NOT NULL,
    name character varying(50) NOT NULL,
    role character varying(30) DEFAULT 'INSPECTOR'::character varying NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: api_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_log ALTER COLUMN id SET DEFAULT nextval('public.api_log_id_seq'::regclass);


--
-- Name: battery_cell id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battery_cell ALTER COLUMN id SET DEFAULT nextval('public.battery_cell_id_seq'::regclass);


--
-- Name: battery_cell_image id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battery_cell_image ALTER COLUMN id SET DEFAULT nextval('public.battery_cell_image_id_seq'::regclass);


--
-- Name: defect_result id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_result ALTER COLUMN id SET DEFAULT nextval('public.defect_result_id_seq'::regclass);


--
-- Name: inspection id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection ALTER COLUMN id SET DEFAULT nextval('public.inspection_id_seq'::regclass);


--
-- Name: inspection_batch id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_batch ALTER COLUMN id SET DEFAULT nextval('public.inspection_batch_id_seq'::regclass);


--
-- Name: inspection_image id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_image ALTER COLUMN id SET DEFAULT nextval('public.inspection_image_id_seq'::regclass);


--
-- Name: notice id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notice ALTER COLUMN id SET DEFAULT nextval('public.notice_id_seq'::regclass);


--
-- Name: reports_daily id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_daily ALTER COLUMN id SET DEFAULT nextval('public.reports_daily_id_seq'::regclass);


--
-- Name: reports_daily_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_daily_item ALTER COLUMN id SET DEFAULT nextval('public.reports_daily_item_id_seq'::regclass);


--
-- Name: reports_individual id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_individual ALTER COLUMN id SET DEFAULT nextval('public.reports_individual_id_seq'::regclass);


--
-- Name: simulation_run id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simulation_run ALTER COLUMN id SET DEFAULT nextval('public.simulation_run_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: api_log api_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_log
    ADD CONSTRAINT api_log_pkey PRIMARY KEY (id);


--
-- Name: battery_cell battery_cell_cell_serial_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battery_cell
    ADD CONSTRAINT battery_cell_cell_serial_no_key UNIQUE (cell_serial_no);


--
-- Name: battery_cell_image battery_cell_image_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battery_cell_image
    ADD CONSTRAINT battery_cell_image_pkey PRIMARY KEY (id);


--
-- Name: battery_cell_image ck_battery_cell_image_recapture_no; Type: CHECK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.battery_cell_image
    ADD CONSTRAINT ck_battery_cell_image_recapture_no CHECK ((recapture_no >= 0));


--
-- Name: battery_cell battery_cell_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battery_cell
    ADD CONSTRAINT battery_cell_pkey PRIMARY KEY (id);


--
-- Name: defect_result defect_result_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_result
    ADD CONSTRAINT defect_result_pkey PRIMARY KEY (id);


--
-- Name: inspection inspection_ai_request_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection
    ADD CONSTRAINT inspection_ai_request_id_key UNIQUE (ai_request_id);


--
-- Name: inspection_batch inspection_batch_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_batch
    ADD CONSTRAINT inspection_batch_pkey PRIMARY KEY (id);


--
-- Name: inspection_image inspection_image_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_image
    ADD CONSTRAINT inspection_image_pkey PRIMARY KEY (id);


--
-- Name: inspection inspection_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection
    ADD CONSTRAINT inspection_pkey PRIMARY KEY (id);


--
-- Name: notice notice_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notice
    ADD CONSTRAINT notice_pkey PRIMARY KEY (id);


--
-- Name: reports_daily_item reports_daily_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_daily_item
    ADD CONSTRAINT reports_daily_item_pkey PRIMARY KEY (id);


--
-- Name: reports_daily reports_daily_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_daily
    ADD CONSTRAINT reports_daily_pkey PRIMARY KEY (id);


--
-- Name: reports_daily reports_daily_report_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_daily
    ADD CONSTRAINT reports_daily_report_date_key UNIQUE (report_date);


--
-- Name: reports_individual reports_individual_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_individual
    ADD CONSTRAINT reports_individual_pkey PRIMARY KEY (id);


--
-- Name: simulation_run simulation_run_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simulation_run
    ADD CONSTRAINT simulation_run_pkey PRIMARY KEY (id);


--
-- Name: battery_cell_image uk_battery_cell_image_object; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battery_cell_image
    ADD CONSTRAINT uk_battery_cell_image_object UNIQUE (battery_cell_id, bucket_name, object_key);


--
-- Name: inspection_image uk_inspection_image_object; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_image
    ADD CONSTRAINT uk_inspection_image_object UNIQUE (bucket_name, object_key);


--
-- Name: reports_individual uk_reports_individual_cell_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_individual
    ADD CONSTRAINT uk_reports_individual_cell_version UNIQUE (battery_cell_id, version);


--
-- Name: users users_login_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_login_id_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_api_log_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_api_log_created_at ON public.api_log USING btree (created_at);


--
-- Name: idx_battery_cell_serial_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_battery_cell_serial_no ON public.battery_cell USING btree (cell_serial_no);


--
-- Name: ix_battery_cell_image_capture_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_battery_cell_image_capture_source ON public.battery_cell_image USING btree (battery_cell_id, image_type, recapture_no, id);


--
-- Name: idx_defect_result_image_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_defect_result_image_id ON public.defect_result USING btree (inspection_image_id);


--
-- Name: idx_defect_result_inspection_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_defect_result_inspection_id ON public.defect_result USING btree (inspection_id);


--
-- Name: idx_defect_result_label; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_defect_result_label ON public.defect_result USING btree (label);


--
-- Name: idx_inspection_batch_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_batch_id ON public.inspection USING btree (inspection_batch_id);


--
-- Name: idx_inspection_batch_run_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_batch_run_id ON public.inspection_batch USING btree (simulation_run_id);


--
-- Name: idx_inspection_batch_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_batch_status ON public.inspection_batch USING btree (status);


--
-- Name: idx_inspection_battery_cell_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_battery_cell_id ON public.inspection USING btree (battery_cell_id);


--
-- Name: idx_inspection_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_created_at ON public.inspection USING btree (created_at);


--
-- Name: idx_inspection_final_label; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_final_label ON public.inspection USING btree (final_label);


--
-- Name: idx_inspection_image_inspection_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_image_inspection_id ON public.inspection_image USING btree (inspection_id);


--
-- Name: idx_inspection_image_object; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_image_object ON public.inspection_image USING btree (bucket_name, object_key);


--
-- Name: idx_inspection_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_status ON public.inspection USING btree (status);


--
-- Name: idx_reports_daily_item_report_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_daily_item_report_id ON public.reports_daily_item USING btree (daily_report_id);


--
-- Name: idx_reports_daily_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_daily_status ON public.reports_daily USING btree (status);


--
-- Name: idx_reports_individual_cell_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_individual_cell_id ON public.reports_individual USING btree (battery_cell_id);


--
-- Name: idx_reports_individual_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_individual_status ON public.reports_individual USING btree (status);


--
-- Name: idx_simulation_run_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_simulation_run_status ON public.simulation_run USING btree (status);


--
-- Name: defect_result defect_result_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_result
    ADD CONSTRAINT defect_result_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES public.inspection(id);


--
-- Name: defect_result defect_result_inspection_image_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.defect_result
    ADD CONSTRAINT defect_result_inspection_image_id_fkey FOREIGN KEY (inspection_image_id) REFERENCES public.inspection_image(id);


--
-- Name: battery_cell_image fk_battery_cell_image_battery_cell; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battery_cell_image
    ADD CONSTRAINT fk_battery_cell_image_battery_cell FOREIGN KEY (battery_cell_id) REFERENCES public.battery_cell(id);


--
-- Name: inspection_image fk_inspection_image_battery_cell_image; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_image
    ADD CONSTRAINT fk_inspection_image_battery_cell_image FOREIGN KEY (battery_cell_image_id) REFERENCES public.battery_cell_image(id);


--
-- Name: notice fk_notice_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notice
    ADD CONSTRAINT fk_notice_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: inspection_batch inspection_batch_requested_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_batch
    ADD CONSTRAINT inspection_batch_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES public.users(id);


--
-- Name: inspection_batch inspection_batch_simulation_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_batch
    ADD CONSTRAINT inspection_batch_simulation_run_id_fkey FOREIGN KEY (simulation_run_id) REFERENCES public.simulation_run(id);


--
-- Name: inspection inspection_battery_cell_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection
    ADD CONSTRAINT inspection_battery_cell_id_fkey FOREIGN KEY (battery_cell_id) REFERENCES public.battery_cell(id);


--
-- Name: inspection_image inspection_image_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_image
    ADD CONSTRAINT inspection_image_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES public.inspection(id);


--
-- Name: inspection inspection_inspection_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection
    ADD CONSTRAINT inspection_inspection_batch_id_fkey FOREIGN KEY (inspection_batch_id) REFERENCES public.inspection_batch(id);


--
-- Name: reports_daily_item reports_daily_item_daily_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_daily_item
    ADD CONSTRAINT reports_daily_item_daily_report_id_fkey FOREIGN KEY (daily_report_id) REFERENCES public.reports_daily(id);


--
-- Name: reports_daily_item reports_daily_item_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_daily_item
    ADD CONSTRAINT reports_daily_item_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES public.inspection(id);


--
-- Name: reports_individual reports_individual_battery_cell_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_individual
    ADD CONSTRAINT reports_individual_battery_cell_id_fkey FOREIGN KEY (battery_cell_id) REFERENCES public.battery_cell(id);


--
-- Name: reports_individual reports_individual_representative_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports_individual
    ADD CONSTRAINT reports_individual_representative_inspection_id_fkey FOREIGN KEY (representative_inspection_id) REFERENCES public.inspection(id);


--
-- Name: simulation_run simulation_run_requested_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simulation_run
    ADD CONSTRAINT simulation_run_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES public.users(id);


--
-- Name: notice; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.notice ENABLE ROW LEVEL SECURITY;

--
-- PostgreSQL database dump complete
--

\unrestrict 0I1WAzVeV0LONqK786JKM2tzdlaQxz2FcenwGxpZoWFD2TPHPu5IbcGjfhNUSis

