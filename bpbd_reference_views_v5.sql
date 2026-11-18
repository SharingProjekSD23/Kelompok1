-- BPBD Command Center: reference views for logistics transactions
-- Run this script in database bpbd_db as a user with ownership/DDL privileges.
-- It keeps the normalized source tables intact and exposes two joined reference datasets.
-- The legacy logistik_keluar.id_sumber column, if present, is intentionally NOT used.

BEGIN;

-- Outgoing transactions need their own transaction date (not disaster occurrence date).
ALTER TABLE bidang2.logistik_keluar
    ADD COLUMN IF NOT EXISTS tanggal_transaksi DATE;

-- The outgoing flow no longer captures a source. If the old source FK column is present,
-- allow it to be NULL so new outgoing rows do not need a source value. It is not exposed
-- by the outgoing reference view and is not used by the application.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='bidang2' AND table_name='logistik_keluar' AND column_name='id_sumber'
    ) THEN
        ALTER TABLE bidang2.logistik_keluar ALTER COLUMN id_sumber DROP NOT NULL;
    END IF;
END $$;

-- Existing rows receive today's date only if they do not yet have a transaction date.
UPDATE bidang2.logistik_keluar AS lk
SET tanggal_transaksi = COALESCE(kb.tanggal_kejadian, CURRENT_DATE)
FROM bidang2.kejadian_bencana AS kb
WHERE kb.id_bencana = lk.id_bencana
  AND lk.tanggal_transaksi IS NULL;

UPDATE bidang2.logistik_keluar
SET tanggal_transaksi = CURRENT_DATE
WHERE tanggal_transaksi IS NULL;

CREATE INDEX IF NOT EXISTS idx_logistik_keluar_tanggal_transaksi
    ON bidang2.logistik_keluar (tanggal_transaksi);

ALTER TABLE bidang2.logistik_keluar
    ALTER COLUMN tanggal_transaksi SET DEFAULT CURRENT_DATE,
    ALTER COLUMN tanggal_transaksi SET NOT NULL;

-- Reference view 1: incoming transaction + master barang + master sumber.
CREATE OR REPLACE VIEW bidang2.v_rujukan_logistik_masuk AS
SELECT
    lm.id_baris_masuk,
    lm.id AS id_transaksi_masuk,
    lm.tanggal,
    lm.id_barang,
    b.nama_barang,
    lm.id_sumber,
    s.nama_sumber,
    lm.jumlah,
    lm.keterangan,
    lm.status_approval
FROM bidang2.logistik_masuk AS lm
INNER JOIN master.barang AS b
    ON b.id_barang = lm.id_barang
INNER JOIN master.sumber AS s
    ON s.id_sumber = lm.id_sumber;

-- Reference view 2: outgoing transaction + disaster + disaster type + area + item.
-- Deliberately no join to master.sumber.
CREATE OR REPLACE VIEW bidang2.v_rujukan_logistik_keluar AS
SELECT
    lk.id_baris_keluar,
    lk.id AS id_transaksi_keluar,
    lk.tanggal_transaksi,
    lk.id_bencana,
    kb.id_jenis_bencana,
    jb.nama_bencana,
    kb.tanggal_lapor,
    kb.pukul_lapor,
    kb.tanggal_kejadian,
    kb.pukul_kejadian,
    kb.id_wilayah,
    w.kecamatan,
    w.kelurahan,
    w.kodepos_awal,
    w.kodepos_akhir,
    kb.alamat,
    kb.rt,
    kb.rw,
    kb.pemilik_terdampak,
    kb.jumlah_kk,
    kb.jumlah_jiwa,
    kb.total_kerugian,
    kb.kronologi,
    kb.korban_ps,
    kb.korban_md,
    kb.korban_lb,
    kb.korban_lr,
    kb.sumber_info,
    kb.no_kontak,
    kb.maps,
    kb.link_foto,
    lk.id_barang,
    b.nama_barang,
    lk.jumlah,
    lk.status_approval
FROM bidang2.logistik_keluar AS lk
INNER JOIN bidang2.kejadian_bencana AS kb
    ON kb.id_bencana = lk.id_bencana
INNER JOIN master.jenis_bencana AS jb
    ON jb.id_jenis_bencana = kb.id_jenis_bencana
INNER JOIN master.wilayah AS w
    ON w.id_wilayah = kb.id_wilayah
INNER JOIN master.barang AS b
    ON b.id_barang = lk.id_barang;

COMMIT;

-- Quick validation:
SELECT COUNT(*) AS jumlah_logistik_masuk
FROM bidang2.v_rujukan_logistik_masuk;

SELECT COUNT(*) AS jumlah_logistik_keluar
FROM bidang2.v_rujukan_logistik_keluar;
