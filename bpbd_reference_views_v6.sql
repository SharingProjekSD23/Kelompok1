-- BPBD migration v6: outgoing notes + reference views refreshed
BEGIN;
ALTER TABLE bidang2.logistik_keluar ADD COLUMN IF NOT EXISTS keterangan TEXT;
CREATE OR REPLACE VIEW bidang2.v_rujukan_logistik_masuk AS
SELECT lm.id_baris_masuk, lm.id AS id_transaksi_masuk, lm.tanggal, lm.id_barang, b.nama_barang, lm.id_sumber, s.nama_sumber, lm.jumlah, lm.keterangan, lm.status_approval
FROM bidang2.logistik_masuk lm
INNER JOIN master.barang b ON b.id_barang=lm.id_barang
INNER JOIN master.sumber s ON s.id_sumber=lm.id_sumber;
CREATE OR REPLACE VIEW bidang2.v_rujukan_logistik_keluar AS
SELECT lk.id_baris_keluar, lk.id AS id_transaksi_keluar, lk.tanggal_transaksi, lk.id_bencana, kb.id_jenis_bencana, jb.nama_bencana, kb.tanggal_lapor, kb.pukul_lapor, kb.tanggal_kejadian, kb.pukul_kejadian, kb.id_wilayah, w.kecamatan, w.kelurahan, w.kodepos_awal, w.kodepos_akhir, kb.alamat, kb.rt, kb.rw, kb.pemilik_terdampak, kb.jumlah_kk, kb.jumlah_jiwa, kb.total_kerugian, kb.kronologi, kb.korban_ps, kb.korban_md, kb.korban_lb, kb.korban_lr, kb.sumber_info, kb.no_kontak, kb.maps, kb.link_foto, lk.id_barang, b.nama_barang, lk.jumlah, lk.status_approval, lk.keterangan
FROM bidang2.logistik_keluar lk
INNER JOIN bidang2.kejadian_bencana kb ON kb.id_bencana=lk.id_bencana
INNER JOIN master.jenis_bencana jb ON jb.id_jenis_bencana=kb.id_jenis_bencana
INNER JOIN master.wilayah w ON w.id_wilayah=kb.id_wilayah
INNER JOIN master.barang b ON b.id_barang=lk.id_barang;
COMMIT;
