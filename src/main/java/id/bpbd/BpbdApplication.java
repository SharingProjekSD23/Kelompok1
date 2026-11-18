package id.bpbd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.*;

@SpringBootApplication
public class BpbdApplication {
    public static void main(String[] args) {
        SpringApplication.run(BpbdApplication.class, args);
    }
}

@Controller
class BpbdController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    BpbdController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Seluruh nama tabel dibatasi ke tabel yang memang ada pada ERD.
    private JsonNode ambilData(String tabel) {
        String sql = switch (tabel) {
            case "barang" -> "SELECT id_barang, nama_barang FROM master.barang ORDER BY nama_barang";
            case "jenis_bencana" -> "SELECT id_jenis_bencana, nama_bencana FROM master.jenis_bencana ORDER BY nama_bencana";
            case "wilayah" -> "SELECT id_wilayah, kecamatan, kelurahan, kodepos_awal, kodepos_akhir FROM master.wilayah ORDER BY kecamatan, kelurahan";
            case "sumber" -> "SELECT id_sumber, nama_sumber FROM master.sumber ORDER BY nama_sumber";
            case "kejadian_bencana" -> """
                SELECT k.*, j.nama_bencana, w.kecamatan, w.kelurahan
                FROM bidang2.kejadian_bencana k
                LEFT JOIN master.jenis_bencana j ON j.id_jenis_bencana=k.id_jenis_bencana
                LEFT JOIN master.wilayah w ON w.id_wilayah=k.id_wilayah
                ORDER BY k.tanggal_kejadian DESC, k.pukul_kejadian DESC
                """;
            case "logistik_masuk" -> """
                SELECT * FROM bidang2.v_rujukan_logistik_masuk
                ORDER BY tanggal DESC, id_baris_masuk DESC
                """;
            case "logistik_keluar" -> """
                SELECT id_baris_keluar, id_transaksi_keluar, id_bencana, tanggal_transaksi, nama_bencana,
                       kecamatan, kelurahan, nama_barang, jumlah, keterangan, status_approval
                FROM bidang2.v_rujukan_logistik_keluar
                ORDER BY tanggal_transaksi DESC, id_baris_keluar DESC
                """;
            case "v_rujukan_logistik_masuk" -> "SELECT * FROM bidang2.v_rujukan_logistik_masuk ORDER BY tanggal DESC, id_baris_masuk DESC";
            case "v_rujukan_logistik_keluar" -> "SELECT * FROM bidang2.v_rujukan_logistik_keluar ORDER BY tanggal_transaksi DESC, id_baris_keluar DESC";
            default -> throw new IllegalArgumentException("Tabel tidak dikenal: " + tabel);
        };
        List<Map<String,Object>> rows = jdbc.queryForList(sql);
        ArrayNode result = mapper.createArrayNode();
        for (Map<String,Object> row : rows) {
            ObjectNode item = mapper.createObjectNode();
            row.forEach((key,value) -> item.set(key, mapper.valueToTree(value)));
            result.add(item);
        }
        return result;
    }

    private int jumlah(String tabel) {
        return ambilData(tabel).size();
    }

    private String idAcak(String prefix, int panjang) {
        String s = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return (prefix + s).substring(0, panjang);
    }

    private String opsiMaster(String tabel, String idKolom, String namaKolom, String terpilih) {
        JsonNode rows = ambilData(tabel);
        StringBuilder html = new StringBuilder("<option value=''>Pilih...</option>");
        for (JsonNode row : rows) {
            String id = row.path(idKolom).asText("");
            String nama = row.path(namaKolom).asText("");
            if ("wilayah".equals(tabel)) {
                nama = row.path("kecamatan").asText("") + " - " + row.path("kelurahan").asText("") + " (" + id + ")";
            }
            html.append("<option value='").append(escape(id)).append("'")
                .append(id.equals(terpilih) ? " selected" : "")
                .append(">").append(escape(nama)).append("</option>");
        }
        return html.toString();
    }

    private String kartu(String ikon, String label, long nilai) {
        return """
        <div class="card"><div class="metric-top"><div class="metric-icon">%s</div><span class="soft-pill">Terdata</span></div>
        <div class="metric-label">%s</div><div class="metric-value">%d</div><div class="metric-foot">%s</div></div>
        """.formatted(escape(ikon), escape(label), nilai, "Jenis Barang".equals(label) ? "Jenis" : "Paket");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String pesan(String teks) {
        return "<div class='notice'>" + escape(teks) + "</div><p><a class='button' href='/'>Kembali ke Dashboard</a></p>";
    }

    private String halaman(String judul, String isi) {
        String html = """
        <!DOCTYPE html>
        <html lang="id">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>__JUDUL__ | BPBD Command Center</title>
          <style>
            :root{--orange:#f15a24;--orange-deep:#c83e12;--orange-soft:#fff0e8;--navy:#142b45;--ink:#172b40;--muted:#6b7c8f;--line:#e7edf2;--canvas:#f4f7fa;--shadow:0 16px 42px rgba(22,43,65,.07)}
            *{box-sizing:border-box}html{scroll-behavior:smooth}
            body{margin:0;color:var(--ink);font-family:"Plus Jakarta Sans","Inter","Segoe UI",Arial,sans-serif;font-size:14px;line-height:1.55;background:radial-gradient(ellipse at 8% 0%,rgba(241,90,36,.09),transparent 28%),radial-gradient(ellipse at 100% 20%,rgba(32,62,93,.08),transparent 30%),var(--canvas);min-height:100vh}
            .main{margin:0 auto;padding:30px 34px 24px;width:100%;max-width:1640px}
            .topbar{display:flex;justify-content:space-between;align-items:center;gap:24px;margin:0 0 25px;padding:31px 34px;min-height:190px;border-radius:24px;color:#fff;background:linear-gradient(118deg,#142b45 0%,#203e5d 48%,#f15a24 100%);box-shadow:0 18px 38px rgba(20,43,69,.18);position:relative;overflow:hidden;isolation:isolate}
            .topbar:before{content:"";position:absolute;z-index:-1;width:390px;height:390px;border:1px solid rgba(255,255,255,.14);border-radius:50%;right:13%;top:-265px;box-shadow:0 0 0 35px rgba(255,255,255,.035),0 0 0 75px rgba(255,255,255,.025)}
            .topbar:after{content:"";position:absolute;z-index:-1;width:180px;height:180px;border-radius:40px;transform:rotate(25deg);right:-45px;bottom:-110px;background:rgba(255,255,255,.08)}
            .hero-copy{max-width:690px}.eyebrow{display:inline-flex;align-items:center;gap:9px;color:#ffe4d6;font-size:11px;font-weight:800;letter-spacing:1.7px;text-transform:uppercase}.eyebrow:before{content:"";width:25px;height:3px;border-radius:8px;background:#ffb27d}
            h1{font-size:clamp(28px,3vw,38px);line-height:1.15;margin:12px 0 10px;letter-spacing:-1.1px;font-weight:800}h2{font-size:18px;line-height:1.3;margin:0 0 5px;letter-spacing:-.35px;font-weight:800;color:var(--navy)}h3{font-size:15px;margin:0;font-weight:800}
            .hero-subtitle{color:rgba(255,255,255,.82);font-size:14px;margin:0;max-width:560px}.top-actions{display:flex;align-items:center;gap:12px;position:relative;z-index:1;flex-shrink:0}.period-picker{display:flex;gap:5px;align-items:center;padding:5px 8px;border:1px solid rgba(255,255,255,.25);border-radius:12px;background:rgba(255,255,255,.12);backdrop-filter:blur(10px)}.period-picker select{width:auto;min-width:74px;margin:0;padding:7px 8px;border:0;background:rgba(255,255,255,.9);font-size:10px;border-radius:7px;color:#203e5d}.period-apply{padding:8px 10px;margin:0;font-size:10px;border-radius:8px;background:#f15a24}
            .date{background:rgba(255,255,255,.12);color:#fff;border:1px solid rgba(255,255,255,.25);border-radius:12px;padding:11px 14px;font-size:12px;font-weight:700;white-space:nowrap;backdrop-filter:blur(8px)}
            .button,button{display:inline-flex;align-items:center;justify-content:center;gap:9px;border:0;border-radius:12px;padding:12px 17px;background:linear-gradient(135deg,#f15a24,#ff7b35);color:#fff;font-weight:800;text-decoration:none;cursor:pointer;font-size:13px;line-height:1.2;box-shadow:0 8px 18px rgba(241,90,36,.22);transition:transform .18s,box-shadow .18s,filter .18s}
            .button:hover,button:hover{transform:translateY(-2px);filter:saturate(1.08);box-shadow:0 12px 24px rgba(241,90,36,.28)}
            .transaction-cta{min-height:68px;padding:10px 18px 10px 12px;border-radius:17px;color:#fff;background:linear-gradient(135deg,#ff7b35,#ed4c18);box-shadow:0 12px 26px rgba(8,22,39,.23);border:1px solid rgba(255,255,255,.2)}
            .transaction-cta:hover{background:linear-gradient(135deg,#ff8b4c,#db4314)}
            .plus-mark{display:grid;place-items:center;width:46px;height:46px;border-radius:12px;background:#fff;color:var(--orange-deep);font-size:31px;font-weight:500;line-height:1;box-shadow:inset 0 0 0 1px rgba(255,255,255,.55)}
            .cta-label{display:flex;flex-direction:column;align-items:flex-start;gap:2px}.cta-label strong{font-size:13px;letter-spacing:.1px}.cta-label small{font-size:10px;font-weight:600;color:#fff0e8}
            .section-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin:28px 2px 14px}.section-heading p{margin:5px 0 0;color:var(--muted);font-size:12px}
            .section-kicker{font-size:10px;text-transform:uppercase;letter-spacing:1.35px;color:var(--orange-deep);font-weight:900;margin-bottom:6px}
            .grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin:0 0 25px}
            .card,.panel{background:rgba(255,255,255,.94);border:1px solid rgba(221,230,237,.9);border-radius:18px;padding:21px;box-shadow:var(--shadow)}
            .card{position:relative;overflow:hidden;min-height:150px}.card:after{content:"";position:absolute;width:100px;height:100px;border-radius:50%;right:-39px;top:-40px;background:rgba(241,90,36,.07)}.card:before{content:"";position:absolute;left:0;top:20px;bottom:20px;width:4px;border-radius:0 5px 5px 0;background:linear-gradient(#ff9b62,#f15a24)}
            .metric-top{display:flex;align-items:center;justify-content:space-between;gap:12px}.metric-icon{display:grid;place-items:center;width:43px;height:43px;border-radius:13px;background:var(--orange-soft);color:var(--orange-deep);font-size:21px;font-weight:800}
            .metric-label{color:var(--muted);font-size:12px;font-weight:700;margin-top:16px}.metric-value{font-size:32px;line-height:1.15;font-weight:850;margin-top:5px;color:var(--navy);letter-spacing:-.8px}.metric-foot{font-size:11px;color:#8290a0;margin-top:8px}
            .panel{margin:0 0 22px;padding:23px 24px;overflow:hidden}.panel-head{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:18px}.panel-subtitle{color:var(--muted);font-size:12px;margin-top:5px}
            .soft-pill{display:inline-flex;align-items:center;gap:7px;border-radius:99px;background:#f0f5f8;color:#50657a;padding:7px 10px;font-size:10px;font-weight:800;white-space:nowrap}.soft-pill:before{content:"";width:6px;height:6px;border-radius:50%;background:#f15a24}
            .category-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:13px}.category-card{position:relative;border:1px solid var(--line);border-radius:15px;padding:17px;background:linear-gradient(145deg,#fff 0%,#fbfcfd 100%);overflow:hidden}.category-card:after{content:"";position:absolute;width:70px;height:70px;right:-34px;bottom:-36px;border:13px solid rgba(241,90,36,.07);border-radius:50%}
            .category-name{font-weight:800;font-size:14px;color:var(--navy);overflow-wrap:anywhere}.category-count{font-size:25px;font-weight:850;line-height:1.2;color:var(--orange-deep);margin-top:13px}.category-meta{font-size:11px;color:var(--muted);margin-top:4px}.category-bar{height:4px;border-radius:99px;background:#edf1f4;margin-top:14px;overflow:hidden}.category-bar span{display:block;height:100%;border-radius:99px;background:linear-gradient(90deg,#f15a24,#ffb16e)}
            .table-wrap{width:100%;overflow-x:auto;border:1px solid var(--line);border-radius:13px}table{border-collapse:separate;border-spacing:0;width:100%;font-size:12px;min-width:580px;background:#fff}th,td{padding:14px 15px;border-bottom:1px solid #edf1f4;text-align:left;white-space:nowrap}th{color:#65768a;background:#f5f8fa;font-size:10px;font-weight:900;letter-spacing:.65px;text-transform:uppercase}tbody tr:last-child td{border-bottom:0}tbody tr:hover{background:#fffaf7}
            .status-in,.status-out{display:inline-flex;align-items:center;gap:6px;padding:6px 9px;border-radius:99px;font-size:10px;font-weight:900}.status-in{background:#e8f7ef;color:#16764e}.status-out{background:#fff0e8;color:#c3471b}.empty-state{padding:28px;text-align:center;color:var(--muted);font-size:13px}
            .muted{color:var(--muted);font-size:13px}.footer{display:flex;justify-content:space-between;gap:12px;color:#8190a0;font-size:11px;padding:15px 2px 8px;border-top:1px solid #e4eaf0;margin-top:24px}
            .notice{padding:15px 17px;background:linear-gradient(135deg,#fff0e5,#fff8f2);border:1px solid #ffe0ca;border-radius:12px;color:#b74400;margin:18px 0;font-size:13px}
            input,select,textarea{width:100%;border:1px solid #dce4eb;border-radius:10px;padding:12px 13px;margin:7px 0 17px;font:inherit;background:#fbfcfd;color:var(--ink)}input:focus,select:focus,textarea:focus{outline:3px solid rgba(241,90,36,.15);border-color:#f15a24}label{font-size:12px;font-weight:800;color:#334b62}textarea{min-height:95px;resize:vertical}
            @media(max-width:1100px){.main{padding:22px}.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.category-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.topbar{padding:27px}}
            @media(max-width:700px){.main{padding:14px}.topbar{padding:22px;align-items:flex-start;flex-direction:column;border-radius:19px;min-height:0}.top-actions{width:100%;justify-content:space-between;flex-wrap:wrap}.date{padding:10px 12px}.transaction-cta{min-height:62px}.grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.card{padding:15px;min-height:138px}.metric-value{font-size:27px}.metric-label{font-size:11px}.category-grid{grid-template-columns:1fr}.panel{padding:17px 14px;border-radius:15px}.panel-head,.section-heading{align-items:flex-start;flex-direction:column}h1{font-size:28px}.footer{flex-direction:column}}
            @media(max-width:390px){.grid{grid-template-columns:1fr}.transaction-cta{width:100%}}
            /* BPBD visual system: warm orange, navy, and clean bordered analytical panels */
            .panel,.chart-panel{border:1px solid #dce5ed;border-top:4px solid #f15a24;box-shadow:0 12px 30px rgba(20,43,69,.075)}
            .chart-panel{border-top-color:#203e5d}
            .panel:nth-of-type(odd){border-top-color:#203e5d}
            .panel-head{align-items:flex-start}
            .chart-canvas{position:relative;width:100%;height:310px;min-height:260px}
            .chart-canvas canvas{width:100%!important;height:100%!important}
            .chart-grid{grid-template-columns:minmax(280px,.85fr) minmax(0,1.65fr);align-items:stretch}
            .chart-panel{min-width:0}
            .tracking-controls{display:flex;gap:16px;align-items:flex-end;flex-wrap:wrap;padding:16px;background:#f7f9fb;border:1px solid #e1e8ef;border-radius:14px;margin:16px 0}
            .tracking-columns{display:flex;gap:10px 16px;flex-wrap:wrap;flex:1 1 100%}
            .check-label{display:inline-flex;align-items:center;gap:7px;font-size:12px;font-weight:650;color:#40566d}
            .check-label input{width:auto;margin:0;accent-color:#f15a24}
            .tracking-controls select{width:auto;min-width:110px;margin:0}
            .table-actions{display:flex;gap:8px;flex-wrap:wrap}
            .table-actions button{padding:9px 13px;font-size:11px}
            .panel h2:before{content:"";display:inline-block;width:4px;height:18px;border-radius:4px;background:#f15a24;margin-right:10px;vertical-align:-3px}
            @media(max-width:760px){.chart-grid{grid-template-columns:1fr}.chart-canvas{height:260px}.tracking-controls{align-items:stretch}.tracking-controls select{width:100%}}

            /* Dashboard readability and data visualization */
            body { font-size:16px; line-height:1.6; }
            .main { max-width:1720px; padding:32px 38px 28px; }
            h1 { font-size:clamp(32px,3vw,42px); }
            h2 { font-size:21px; }
            .hero-subtitle { font-size:16px; line-height:1.65; }
            .eyebrow { font-size:12px; }
            .metric-label { font-size:14px; }
            .metric-value { font-size:36px; }
            .metric-foot,.panel-subtitle,.section-heading p { font-size:13px; }
            .panel { padding:26px 28px; margin-bottom:24px; }
            .panel-head { margin-bottom:22px; }
            .table-wrap { overflow-x:auto; border:1px solid var(--line); border-radius:14px; }
            table { font-size:14px; min-width:720px; }
            th { font-size:12px; padding:15px 16px; }
            td { font-size:14px; padding:15px 16px; }
            .transaction-cta { min-height:68px; }
            .plus-mark { width:48px;height:48px;font-size:34px; }
            .form-shell { max-width:1080px;margin:0 auto; }
            .form-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px; }
            .form-field.full { grid-column:1/-1; }
            .form-section { background:#f8fafc;border:1px solid #e5ebf0;border-radius:15px;padding:20px;margin:18px 0; }
            .form-section h3 { color:var(--navy);font-size:16px;margin-bottom:4px; }
            .form-section p { color:var(--muted);font-size:13px;margin:0 0 15px; }
            .item-row { display:grid;grid-template-columns:minmax(0,2fr) minmax(120px,1fr) 44px;gap:12px;align-items:end;padding:14px;background:#fff;border:1px solid #e5ebf0;border-radius:13px;margin:10px 0; }
            .item-row label { display:block; }
            .item-row select,.item-row input { margin-bottom:0; }
            .remove-item { height:44px;padding:0;background:#fff0e8;color:#b74400;box-shadow:none;border:1px solid #ffd6c1;font-size:20px; }
            .chart-grid { display:grid;grid-template-columns:1.25fr 1fr 1fr;gap:18px;margin-bottom:24px; }
            .chart-panel { min-width:0;background:#fff;border:1px solid #e1e8ef;border-radius:18px;padding:22px;box-shadow:var(--shadow); }
            .chart-panel h3 { font-size:16px;color:var(--navy);margin-bottom:4px; }
            .chart-panel .chart-desc { color:var(--muted);font-size:12px;margin-bottom:18px; }
            .chart-canvas { width:100%;min-height:190px; }
            .chart-canvas svg { width:100%;height:auto;display:block; }
            .chart-legend { display:flex;flex-wrap:wrap;gap:10px 14px;margin-top:14px;font-size:12px;color:#53667a; }
            .legend-dot { display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:6px; }
            .choice-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px; }
            .choice-card { text-decoration:none;color:var(--ink);display:block;padding:28px;border:1px solid #e1e8ef;border-radius:20px;background:#fff;box-shadow:var(--shadow);transition:transform .18s,box-shadow .18s; }
            .choice-card:hover { transform:translateY(-3px);box-shadow:0 18px 38px rgba(22,43,65,.12); }
            .choice-icon { display:grid;place-items:center;width:58px;height:58px;border-radius:17px;font-size:30px;font-weight:800;margin-bottom:18px; }
            .choice-in .choice-icon { background:#e6f7ef;color:#14764d; }
            .choice-out .choice-icon { background:#fff0e8;color:#c3471b; }
            .choice-card h2 { margin-bottom:8px; }
            .choice-card p { margin:0;color:var(--muted);font-size:14px; }
            .chart-canvas{height:330px;position:relative}.chart-panel .chart-canvas{height:300px}.tracking-controls{display:flex;align-items:end;gap:16px;flex-wrap:wrap;margin:15px 0}.tracking-columns{display:flex;gap:10px 14px;flex-wrap:wrap;flex:1 1 100%}.check-label{display:inline-flex;align-items:center;gap:5px;font-size:11px;font-weight:600}.check-label input{width:auto;margin:0}.tracking-controls select{width:auto;min-width:100px;margin:0}.tracking-controls button{margin:0}
            .form-title-row { display:flex;align-items:center;gap:14px;margin-bottom:20px; }
            .form-title-icon { display:grid;place-items:center;width:54px;height:54px;border-radius:16px;font-size:28px;font-weight:800; }
            .form-title-icon.in { background:#e6f7ef;color:#14764d; }
            .form-title-icon.out { background:#fff0e8;color:#c3471b; }
            .form-actions { display:flex;gap:12px;flex-wrap:wrap;margin-top:22px; }
            @media(max-width:1200px) { .chart-grid { grid-template-columns:1fr 1fr; } .chart-panel:first-child { grid-column:1/-1; } }
            @media(max-width:760px) {
              .main { padding:16px; }
              .panel { padding:19px 16px; }
              .chart-grid,.choice-grid,.form-grid { grid-template-columns:1fr; }
              .chart-panel:first-child { grid-column:auto; }
              .item-row { grid-template-columns:minmax(0,1fr) 100px 42px;gap:8px;padding:10px; }
              .item-row select,.item-row input { padding:10px 8px; }
              .form-field.full { grid-column:auto; }
              h2 { font-size:19px; }
              table { min-width:680px; }
            }


            .tracking-hero{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:25px 28px;margin:8px 0 20px;border-radius:20px;color:#fff;background:linear-gradient(120deg,#142b45 0%,#1e4566 70%,#f15a24 100%);box-shadow:0 16px 36px rgba(20,43,69,.18)}
            .tracking-hero h2{color:#fff;font-size:25px;margin:5px 0}.tracking-hero p{margin:0;color:#e4edf5;font-size:13px;max-width:720px}
            .tracking-count{min-width:115px;text-align:center;padding:12px 18px;border:1px solid rgba(255,255,255,.3);border-radius:15px;background:rgba(255,255,255,.12);backdrop-filter:blur(10px)}
            .tracking-count strong{display:block;font-size:28px;line-height:1.2}.tracking-count span{font-size:10px;text-transform:uppercase;letter-spacing:1px;color:#ffe2d2}
            .tracking-panel{border-top:4px solid #f15a24!important}.filter-drawer{border:1px solid #dce5ed;border-radius:16px;overflow:visible;background:#fff}
            .filter-drawer summary{display:flex;align-items:center;gap:14px;list-style:none;cursor:pointer;padding:17px 20px;background:linear-gradient(90deg,#f7f9fc,#fff);border-radius:14px}
            .filter-drawer summary::-webkit-details-marker{display:none}.filter-summary-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:12px;background:#fff0e8;color:#d94d18;font-size:25px;font-weight:800}
            .filter-drawer summary b{display:block;color:#18334e;font-size:14px}.filter-drawer summary small{display:block;color:#78899a;font-size:11px;margin-top:2px}.summary-end{margin-left:auto;font-size:9px;letter-spacing:1px;color:#c34b1d;font-weight:900}
            .filter-body{padding:22px;border-top:1px solid #e8edf2}.filter-section-title{font-size:11px;font-weight:900;letter-spacing:1.1px;color:#d34d1b;margin:18px 0 12px;padding-bottom:8px;border-bottom:1px solid #edf1f5}
            .filter-section-title:first-child{margin-top:0}.filter-section-title span{font-weight:500;letter-spacing:0;color:#8291a0;margin-left:8px}
            .date-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;max-width:650px}.filter-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:15px}
            .filter-field{min-width:0}.filter-field label{display:block;color:#314a63;font-size:11px;font-weight:850;margin-bottom:5px}.filter-field input,.filter-field select{margin:0;background:#fbfcfe;border:1px solid #d9e2eb;border-radius:10px;font-size:12px}
            .filter-field select[multiple]{height:116px;padding:7px}.filter-field small{display:block;color:#8a98a7;font-size:10px;margin-top:5px}
            .select-actions{display:flex;gap:6px;margin-top:7px}.tiny-btn{padding:6px 9px;font-size:10px;border-radius:7px;box-shadow:none;background:#203e5d}.tiny-btn.ghost{background:#eef2f6;color:#40566d}
            .table-config{display:grid;grid-template-columns:190px minmax(0,1fr);gap:20px;align-items:start}.columns-picker{position:relative}.column-summary{font-size:11px;color:#6e7e8f;margin:4px 0 8px}
            .secondary-btn,.primary-btn{display:inline-flex;align-items:center;justify-content:center;text-decoration:none;border-radius:10px;padding:10px 14px;font-size:11px;font-weight:850;cursor:pointer}
            .secondary-btn{background:#f0f4f8;color:#29435e;border:1px solid #dce5ed;box-shadow:none}.primary-btn{background:linear-gradient(135deg,#f15a24,#d94716);color:white;border:1px solid #ed5a24;box-shadow:0 7px 16px rgba(241,90,36,.18)}
            .column-menu{display:none;position:relative;margin-top:9px;padding:12px;border:1px solid #dce5ed;border-radius:12px;background:#fbfcfe;box-shadow:inset 0 1px 0 #fff}.column-menu.show{display:block}
            .column-menu-head{display:flex;gap:7px;margin-bottom:10px}.column-options{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px 12px;max-height:245px;overflow:auto}
            .column-options label{display:flex;align-items:center;gap:7px;font-size:10px;font-weight:650;color:#435a70;min-width:0}.column-options input{width:14px;height:14px;margin:0;accent-color:#f15a24}
            .filter-actions{display:flex;justify-content:flex-end;gap:9px;margin-top:20px;padding-top:15px;border-top:1px solid #edf1f5}
            .results-panel{border-top:4px solid #203e5d!important}.results-head{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:16px}.export-actions{display:flex;gap:8px;flex-wrap:wrap}
            .table-meta{font-size:11px;color:#748599;margin:0 0 10px}.table-meta b{color:#d64e1b}.tracking-back{margin:14px 2px}.tracking-back a{color:#244865;text-decoration:none;font-weight:800;font-size:12px}
            @media(max-width:900px){.filter-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.column-options{grid-template-columns:repeat(2,minmax(0,1fr))}}
            @media(max-width:650px){.tracking-hero,.results-head{align-items:flex-start;flex-direction:column}.date-grid,.filter-grid,.table-config{grid-template-columns:1fr}.filter-body{padding:14px}.column-options{grid-template-columns:1fr 1fr}.summary-end{display:none}.tracking-count{width:100%}}
            /* Compact, consistent filter UI */
            .main{max-width:1380px;padding:24px 26px}
            .tracking-hero{padding:19px 22px;margin:4px 0 14px;border-radius:16px}
            .tracking-hero h2{font-size:21px}.tracking-count{min-width:96px;padding:9px 13px}.tracking-count strong{font-size:23px}
            .tracking-panel,.results-panel{padding:18px 20px!important;margin-bottom:15px!important;border-radius:15px}
            .filter-drawer{border:1px solid #e1e8ef;border-radius:12px}
            .filter-drawer summary{padding:12px 14px;gap:10px}
            .filter-summary-icon{width:34px;height:34px;font-size:19px}
            .filter-body{padding:15px 16px}
            .filter-section-title{font-size:10px;margin:14px 0 9px;padding-bottom:6px}
            .date-grid{max-width:520px;gap:10px}
            .filter-grid{grid-template-columns:repeat(5,minmax(130px,1fr));gap:10px}
            .filter-field label{font-size:10px;margin-bottom:4px}
            .filter-field input,.filter-field select{padding:8px 10px;font-size:11px;border-radius:8px}
            .filter-select{position:relative;min-width:0}
            .filter-select summary{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:9px 10px;border:1px solid #dbe3eb;border-radius:9px;background:#fbfcfd;color:#30475e;font-size:11px;font-weight:700;cursor:pointer;list-style:none}
            .filter-select summary::-webkit-details-marker{display:none}
            .filter-select summary:after{content:'⌄';color:#e65a25;font-size:14px}
            .filter-select[open] summary{border-color:#e87545;box-shadow:0 0 0 3px rgba(241,90,36,.08)}
            .filter-popover{position:absolute;z-index:20;top:calc(100% + 5px);left:0;width:min(280px,80vw);max-height:250px;overflow:auto;padding:10px;background:#fff;border:1px solid #dce5ed;border-radius:11px;box-shadow:0 14px 32px rgba(20,43,69,.15)}
            .filter-popover .pop-actions{display:flex;gap:6px;padding-bottom:8px;margin-bottom:7px;border-bottom:1px solid #edf1f5}
            .filter-popover label{display:flex;align-items:center;gap:8px;padding:6px 5px;border-radius:6px;font-size:11px;font-weight:600;color:#40566d;cursor:pointer}
            .filter-popover label:hover{background:#fff5ef}
            .filter-popover input{width:14px;height:14px;margin:0;accent-color:#ed5a24}
            .table-config{grid-template-columns:145px minmax(0,1fr);gap:14px}
            .secondary-btn,.primary-btn{padding:8px 11px;font-size:10px;border-radius:8px}
            .column-menu{position:absolute;z-index:30;right:0;top:100%;width:min(520px,88vw);max-height:340px;overflow:auto;margin-top:5px;background:#fff;box-shadow:0 14px 32px rgba(20,43,69,.16)}
            .column-options{grid-template-columns:repeat(3,minmax(0,1fr));gap:5px 10px;max-height:none}
            .column-options label{font-size:10px;padding:4px 2px}.column-group-label{grid-column:1/-1;margin-top:7px;padding:5px 0;border-bottom:1px solid #e8edf2;color:#d65321;font-size:9px;font-weight:900;letter-spacing:.7px;text-transform:uppercase}
            .column-options input{flex:0 0 13px}
            .filter-actions{margin-top:13px;padding-top:11px}
            .results-head{margin-bottom:12px}
            .table-wrap{border-radius:10px}
            .table-wrap table{font-size:11px;min-width:620px}
            .table-wrap th,.table-wrap td{padding:9px 11px}
            .form-shell{max-width:820px;margin:0 auto}
            .form-section{padding:16px;margin:12px 0;border-radius:12px}
            .form-section h3{font-size:14px}.form-section p{font-size:11px;margin-bottom:10px}
            .form-grid{gap:0 14px}
            .item-row{grid-template-columns:minmax(0,2fr) minmax(110px,.8fr) 36px;padding:10px;gap:8px}
            .item-row select,.item-row input{padding:9px 10px;font-size:12px}
            .remove-item{height:37px}
            .disaster-choice{display:flex;align-items:center;gap:10px;padding:10px;border:1px solid #dce5ed;border-radius:10px;background:#fff;margin:8px 0}
            .disaster-choice select{margin:0;flex:1}
            .disaster-preview{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;padding:11px;background:#f6f8fa;border:1px solid #e7edf2;border-radius:10px;margin-top:8px}
            .disaster-preview div{min-width:0}.disaster-preview small{display:block;color:#8492a1;font-size:9px;text-transform:uppercase;letter-spacing:.5px}.disaster-preview strong{display:block;color:#243e58;font-size:11px;overflow-wrap:anywhere}
            .helper-note{padding:10px 12px;border-radius:9px;background:#f5f8fb;border-left:3px solid #f15a24;color:#65788c;font-size:11px;margin:10px 0}
            @media(max-width:900px){.filter-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.disaster-preview{grid-template-columns:repeat(2,minmax(0,1fr))}}
            @media(max-width:650px){.main{padding:14px}.filter-grid,.date-grid,.table-config{grid-template-columns:1fr}.filter-body{padding:12px}.column-options{grid-template-columns:1fr 1fr}.tracking-hero,.results-head{align-items:flex-start;flex-direction:column}.tracking-count{width:auto}.disaster-preview{grid-template-columns:1fr 1fr}}

            /* BPBD Glass UI: compact, restrained, readable */
            body{background:radial-gradient(ellipse at 12% 0%,rgba(241,90,36,.16),transparent 30%),radial-gradient(ellipse at 92% 8%,rgba(32,62,93,.16),transparent 32%),linear-gradient(145deg,#f5f1ef,#edf2f7 55%,#f7eeee);font-size:13px}
            .main{max-width:1450px;padding:20px 24px}
            .topbar{min-height:142px;padding:23px 27px;border:1px solid rgba(255,255,255,.28);background:linear-gradient(112deg,rgba(20,43,69,.96),rgba(28,68,103,.88) 58%,rgba(207,65,36,.91));backdrop-filter:blur(18px);box-shadow:0 16px 34px rgba(20,43,69,.17)}
            h1{font-size:clamp(26px,2.4vw,34px);text-shadow:0 2px 0 rgba(255,255,255,.08),0 5px 14px rgba(0,0,0,.16)}
            h2{font-size:17px;text-shadow:0 1px 0 rgba(255,255,255,.85),0 2px 5px rgba(20,43,69,.08)}
            h3{font-size:14px}
            .hero-subtitle{font-size:13px}
            .section-heading{margin:20px 2px 11px}
            .grid{gap:12px;margin-bottom:17px}
            .card{min-height:126px;padding:15px 17px;border-radius:16px;background:rgba(255,255,255,.72);backdrop-filter:blur(18px);border:1px solid rgba(255,255,255,.85);box-shadow:0 8px 22px rgba(20,43,69,.08),inset 0 1px 0 rgba(255,255,255,.95)}
            .metric-value{font-size:29px;text-shadow:0 1px 0 #fff,0 3px 8px rgba(20,43,69,.12)}
            .metric-label{margin-top:10px;font-size:11px}
            .metric-icon{width:36px;height:36px;border-radius:11px}
            .panel,.chart-panel{padding:16px 18px;margin-bottom:15px;border-radius:16px;background:rgba(255,255,255,.66);backdrop-filter:blur(18px);border:1px solid rgba(255,255,255,.9);border-top:3px solid rgba(241,90,36,.9);box-shadow:0 10px 26px rgba(20,43,69,.075),inset 0 1px 0 rgba(255,255,255,.95)}
            .chart-panel{border-top-color:rgba(32,62,93,.9)}
            .panel-head{margin-bottom:12px}
            .panel-subtitle,.chart-panel .chart-desc{font-size:11px}
            .chart-canvas{height:260px;min-height:220px}
            .chart-panel .chart-canvas{height:235px}
            .chart-grid{gap:14px;margin-bottom:16px}
            .table-wrap{border-radius:11px;border:1px solid rgba(32,62,93,.12);box-shadow:inset 0 1px 0 #fff}
            .table-wrap table{min-width:580px;font-size:11px}
            .table-wrap th{background:linear-gradient(100deg,rgba(20,43,69,.97),rgba(32,62,93,.9));color:#fff;font-size:9px;letter-spacing:.55px;padding:10px 12px}
            .table-wrap td{padding:9px 12px;font-size:11px}
            tbody tr:nth-child(even){background:rgba(238,243,248,.55)}
            tbody tr:hover{background:rgba(255,239,229,.8)}
            .tracking-hero{padding:17px 20px;border:1px solid rgba(255,255,255,.3);background:linear-gradient(115deg,rgba(20,43,69,.96),rgba(32,62,93,.9) 66%,rgba(213,72,34,.88));backdrop-filter:blur(18px);box-shadow:0 12px 26px rgba(20,43,69,.13)}
            .tracking-panel,.results-panel{padding:15px 16px!important}
            .filter-drawer,.filter-body,.filter-popover,.column-menu{background:rgba(255,255,255,.82);backdrop-filter:blur(16px)}
            .filter-drawer summary{padding:11px 13px}
            .filter-body{padding:14px}
            .filter-grid{grid-template-columns:repeat(4,minmax(125px,1fr))}
            .filter-select summary{padding:8px 9px}
            .form-shell{max-width:760px}
            .form-section{padding:14px 16px;background:rgba(255,255,255,.68);backdrop-filter:blur(12px)}
            .button,button,.primary-btn{box-shadow:0 5px 13px rgba(241,90,36,.16)}
            .footer{font-size:10px}
            @media(max-width:900px){.filter-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
            @media(max-width:650px){.main{padding:12px}.topbar{padding:18px}.chart-canvas,.chart-panel .chart-canvas{height:220px}.panel{padding:14px}.filter-grid{grid-template-columns:1fr 1fr}}


            /* Pemilih variabel sejajar di samping tombol, tanpa dropdown terpotong */
            .columns-picker{display:grid!important;grid-template-columns:150px minmax(0,1fr);grid-template-rows:auto auto;column-gap:12px;row-gap:5px;align-items:center;position:relative;min-width:0}
            .columns-picker>label{grid-column:1/-1;grid-row:1}
            .columns-picker>.column-summary{grid-column:1;grid-row:2;margin:0;align-self:end}
            .columns-picker>button{grid-column:1;grid-row:3;justify-self:start;align-self:start}
            .columns-picker>.column-menu{display:none;position:static!important;grid-column:2;grid-row:2/4;width:100%;min-width:0;max-height:270px;overflow:auto;margin:0;background:rgba(255,255,255,.96);box-shadow:0 8px 22px rgba(20,43,69,.09)}
            .columns-picker>.column-menu.show{display:block}
            @media(max-width:650px){.columns-picker{grid-template-columns:1fr;grid-template-rows:auto auto auto auto}.columns-picker>label{grid-column:1;grid-row:1}.columns-picker>.column-summary{grid-column:1;grid-row:2}.columns-picker>button{grid-column:1;grid-row:3}.columns-picker>.column-menu{grid-column:1;grid-row:4}}
          </style>
        </head>
        <body>
          <main class="main">
            <header class="topbar">
              <div class="hero-copy">
                <div class="eyebrow">BPBD KOTA SEMARANG · PUSAT LOGISTIK</div>
                <h1>__JUDUL_H1__</h1>
                <p class="hero-subtitle">__HERO_SUBTITLE__</p>
              </div>
              <div class="top-actions">
                __PERIOD_FILTER__
                <a class="button transaction-cta" href="/input-transaksi" aria-label="Tambah transaksi logistik" title="Tambah transaksi logistik">
                  <span class="plus-mark">+</span>
                  <span class="cta-label"><strong>Transaksi Logistik</strong></span>
                </a>
              </div>
            </header>
            __ISI__
            <div class="footer">
              <span>BPBD Kota Semarang · Sistem Logistik</span>
              <span>Dashboard operasional persediaan</span>
            </div>
          </main>
          <script>
            (function(){const type=document.getElementById('periodType'),part=document.getElementById('periodPart'),year=document.querySelector('[name=tahun]');if(!type||!part)return;const params=new URLSearchParams(location.search);const sets={tahun:[],semester:['Semester 1','Semester 2'],kuartal:['Kuartal 1','Kuartal 2','Kuartal 3','Kuartal 4'],triwulan:['Triwulan 1','Triwulan 2','Triwulan 3','Triwulan 4'],bulan:['Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember']};type.value=params.get('periode')||'tahun';if(year)year.value=params.get('tahun')||'2026';function sync(){const a=sets[type.value]||[];part.innerHTML=a.map((x,i)=>'<option value="'+(i+1)+'">'+x+'</option>').join('');part.style.display=a.length?'':'none';if(a.length)part.value=params.get('bagian')||'1';}type.addEventListener('change',sync);sync();})();
          </script>
        </body>
        </html>
        """;
        String periodFilter = "Dashboard Logistik".equals(judul) ? """
                <form class="date period-picker" method="get" action="/logistik" id="periodForm">
                  <select name="periode" id="periodType" aria-label="Jenis periode"><option value="tahun">Tahun</option><option value="semester">Semester</option><option value="kuartal">Kuartal</option><option value="triwulan">Triwulan</option><option value="bulan">Bulan</option></select>
                  <select name="tahun" aria-label="Tahun"><option value="2026" selected>2026</option><option value="2025">2025</option><option value="2024">2024</option></select>
                  <select name="bagian" id="periodPart" aria-label="Bagian periode" style="display:none"></select>
                  <button type="submit" class="period-apply">Terapkan</button>
                </form>
                """ : "";
        String heroSubtitle = "Dashboard Logistik".equals(judul)
                ? "Pantau stok gudang, penerimaan bantuan, dan penyaluran logistik BPBD Kota Semarang."
                : "BPBD Kota Semarang · Sistem pengelolaan logistik";
        return html.replace("__JUDUL__", escape(judul))
                .replace("__JUDUL_H1__", escape(judul))
                .replace("__HERO_SUBTITLE__", escape(heroSubtitle))
                .replace("__PERIOD_FILTER__", periodFilter)
                .replace("__TANGGAL__", LocalDate.now().toString())
                .replace("__ISI__", isi == null ? "" : isi);
    }

    @GetMapping("/")
    @ResponseBody
    public String dashboard() { return logistik("tahun",2026,1); }

    @GetMapping("/logistik")
    @ResponseBody
    public String logistik(@RequestParam(defaultValue="tahun") String periode,
                           @RequestParam(defaultValue="2026") int tahun,
                           @RequestParam(defaultValue="1") int bagian) {
        StringBuilder isi = new StringBuilder();
        try {
            LocalDate periodeMulai;
            LocalDate periodeAkhir;
            if ("semester".equalsIgnoreCase(periode)) {
                int sem=Math.max(1,Math.min(2,bagian)); periodeMulai=LocalDate.of(tahun,sem==1?1:7,1); periodeAkhir=periodeMulai.plusMonths(6);
            } else if ("kuartal".equalsIgnoreCase(periode) || "triwulan".equalsIgnoreCase(periode)) {
                int q=Math.max(1,Math.min(4,bagian)); periodeMulai=LocalDate.of(tahun,(q-1)*3+1,1); periodeAkhir=periodeMulai.plusMonths(3);
            } else if ("bulan".equalsIgnoreCase(periode)) {
                int m=Math.max(1,Math.min(12,bagian)); periodeMulai=LocalDate.of(tahun,m,1); periodeAkhir=periodeMulai.plusMonths(1);
            } else { periode="tahun"; periodeMulai=LocalDate.of(tahun,1,1); periodeAkhir=periodeMulai.plusYears(1); }
            String pStart=periodeMulai.toString(), pEnd=periodeAkhir.toString();
            JsonNode barang = ambilData("barang");
            List<Map<String,Object>> masukTerbaru = jdbc.queryForList(sqlPeriode("""
                SELECT tanggal, nama_barang, nama_sumber, jumlah, keterangan, status_approval
                FROM bidang2.v_rujukan_logistik_masuk
                WHERE tanggal >= DATE '2026-01-01' AND tanggal < DATE '2027-01-01'
                ORDER BY tanggal DESC, id_baris_masuk DESC LIMIT 5
                """,pStart,pEnd));
            List<Map<String,Object>> keluarTerbaru = jdbc.queryForList(sqlPeriode("""
                SELECT tanggal_transaksi, tanggal_kejadian, nama_bencana, kecamatan,
                       kelurahan, nama_barang, jumlah, keterangan, status_approval
                FROM bidang2.v_rujukan_logistik_keluar
                WHERE tanggal_transaksi >= DATE '2026-01-01' AND tanggal_transaksi < DATE '2027-01-01'
                ORDER BY tanggal_transaksi DESC, id_baris_keluar DESC LIMIT 5
                """,pStart,pEnd));

            long totalMasuk = jdbc.queryForObject(sqlPeriode("""
                SELECT COALESCE(SUM(jumlah),0)::bigint
                FROM bidang2.v_rujukan_logistik_masuk
                WHERE status_approval='Disetujui'
                  AND tanggal >= DATE '2026-01-01' AND tanggal < DATE '2027-01-01'
                """,pStart,pEnd), Long.class);
            long totalKeluar = jdbc.queryForObject(sqlPeriode("""
                SELECT COALESCE(SUM(jumlah),0)::bigint
                FROM bidang2.v_rujukan_logistik_keluar
                WHERE status_approval='Disetujui'
                  AND tanggal_transaksi >= DATE '2026-01-01' AND tanggal_transaksi < DATE '2027-01-01'
                """,pStart,pEnd), Long.class);

            List<Map<String,Object>> stok2026 = jdbc.queryForList(sqlPeriode("""
                SELECT b.id_barang, b.nama_barang,
                       COALESCE(m.total_masuk,0)-COALESCE(k.total_keluar,0) AS stok
                FROM master.barang b
                LEFT JOIN (
                    SELECT id_barang, SUM(jumlah) AS total_masuk
                    FROM bidang2.v_rujukan_logistik_masuk
                    WHERE status_approval='Disetujui'
                      AND tanggal >= DATE '2026-01-01' AND tanggal < DATE '2027-01-01'
                    GROUP BY id_barang
                ) m ON m.id_barang=b.id_barang
                LEFT JOIN (
                    SELECT id_barang, SUM(jumlah) AS total_keluar
                    FROM bidang2.v_rujukan_logistik_keluar
                    WHERE status_approval='Disetujui'
                      AND tanggal_transaksi >= DATE '2026-01-01' AND tanggal_transaksi < DATE '2027-01-01'
                    GROUP BY id_barang
                ) k ON k.id_barang=b.id_barang
                ORDER BY b.nama_barang
                """,pStart,pEnd));
            long stok = stok2026.stream().mapToLong(r -> ((Number)r.get("stok")).longValue()).sum();

            List<Map<String,Object>> bulanan = jdbc.queryForList(sqlPeriode("""
                WITH bulan AS (SELECT generate_series(1,12) AS no_bulan)
                SELECT bulan.no_bulan,
                       COALESCE(m.total,0) AS masuk,
                       COALESCE(k.total,0) AS keluar
                FROM bulan
                LEFT JOIN (
                    SELECT EXTRACT(MONTH FROM tanggal)::int AS no_bulan, SUM(jumlah) AS total
                    FROM bidang2.v_rujukan_logistik_masuk
                    WHERE status_approval='Disetujui'
                      AND tanggal >= DATE '2026-01-01' AND tanggal < DATE '2027-01-01'
                    GROUP BY 1
                ) m USING(no_bulan)
                LEFT JOIN (
                    SELECT EXTRACT(MONTH FROM tanggal_transaksi)::int AS no_bulan, SUM(jumlah) AS total
                    FROM bidang2.v_rujukan_logistik_keluar
                    WHERE status_approval='Disetujui'
                      AND tanggal_transaksi >= DATE '2026-01-01' AND tanggal_transaksi < DATE '2027-01-01'
                    GROUP BY 1
                ) k USING(no_bulan)
                ORDER BY bulan.no_bulan
                """,pStart,pEnd));
            List<Map<String,Object>> sumber = jdbc.queryForList(sqlPeriode("""
                SELECT COALESCE(nama_sumber,'Tidak diketahui') AS label, SUM(jumlah) AS nilai
                FROM bidang2.v_rujukan_logistik_masuk
                WHERE status_approval='Disetujui'
                  AND tanggal >= DATE '2026-01-01' AND tanggal < DATE '2027-01-01'
                GROUP BY nama_sumber ORDER BY nilai DESC
                """,pStart,pEnd));
            List<Map<String,Object>> kecamatan = jdbc.queryForList(sqlPeriode("""
                SELECT COALESCE(kecamatan,'Tidak diketahui') AS label, SUM(jumlah) AS nilai
                FROM bidang2.v_rujukan_logistik_keluar
                WHERE status_approval='Disetujui'
                  AND tanggal_transaksi >= DATE '2026-01-01' AND tanggal_transaksi < DATE '2027-01-01'
                GROUP BY kecamatan ORDER BY nilai DESC LIMIT 10
                """,pStart,pEnd));

            isi.append("<div class='section-heading'><div><h2>Ringkasan Logistik Berdasarkan Periode</h2><p>Stok dan pergerakan dihitung dari transaksi berstatus Disetujui.</p></div><span class='soft-pill'>Periode "+periodeMulai+" s.d. "+periodeAkhir.minusDays(1)+"</span></div>")
                .append("<div class='grid'>").append(kartu("▦","Jenis Barang",barang.size()))
                .append(kartu("▣","Estimasi Stok",stok)).append(kartu("↓","Total Barang Masuk",totalMasuk))
                .append(kartu("↑","Total Barang Keluar",totalKeluar)).append("</div>");

            isi.append("<div class='panel'><div class='panel-head'><div><h2>Stok Gudang per Jenis Barang</h2><p class='panel-subtitle'>Jumlah stok gudang per jenis barang · sesuai periode terpilih</p></div></div><div class='chart-canvas'><canvas id='stokChart'></canvas></div></div>");
            isi.append("<div class='panel'><div class='panel-head'><div><h2>Pergerakan Logistik per Bulan</h2><p class='panel-subtitle'>Perbandingan jumlah barang masuk dan keluar pada periode terpilih</p></div></div><div class='chart-canvas'><canvas id='trendChart'></canvas></div></div>");
            isi.append("<div class='chart-grid'><div class='chart-panel'><h3>Komposisi Sumber Penerimaan</h3><div class='chart-desc'>Komposisi jumlah barang masuk berdasarkan sumber pada periode terpilih</div><div class='chart-canvas'><canvas id='sumberChart'></canvas></div></div>")
                .append("<div class='chart-panel' style='grid-column:span 2'><h3>10 Kecamatan dengan Alokasi Terbesar</h3><div class='chart-desc'>Jumlah logistik keluar berdasarkan kecamatan kejadian bencana pada periode terpilih</div><div class='chart-canvas'><canvas id='kecamatanChart'></canvas></div></div></div>");
            isi.append("<div class='panel'><div class='panel-head'><div><h2>Logistik Masuk Terbaru</h2><p class='panel-subtitle'>Menampilkan 5 transaksi terbaru pada periode terpilih</p></div></div>")
                .append(tabelMap(masukTerbaru)).append("</div>");
            isi.append("<div class='panel'><div class='panel-head'><div><h2>Logistik Keluar Terbaru</h2><p class='panel-subtitle'>Menampilkan 5 transaksi terbaru pada periode terpilih</p></div></div>")
                .append(tabelMap(keluarTerbaru)).append("</div>");
            isi.append(panelTracking(periodeMulai,periodeAkhir));
            isi.append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script><script>")
                .append("const months=['Jan','Feb','Mar','Apr','Mei','Jun','Jul','Agu','Sep','Okt','Nov','Des'];")
                .append("const stokLabels=").append(jsonArray(stok2026,"nama_barang")).append(";const stokValues=").append(jsonNumbers(stok2026,"stok")).append(";")
                .append("const bulanMasuk=").append(jsonNumbers(bulanan,"masuk")).append(";const bulanKeluar=").append(jsonNumbers(bulanan,"keluar")).append(";")
                .append("const sumberLabels=").append(jsonArray(sumber,"label")).append(";const sumberValues=").append(jsonNumbers(sumber,"nilai")).append(";")
                .append("const kecLabels=").append(jsonArray(kecamatan,"label")).append(";const kecValues=").append(jsonNumbers(kecamatan,"nilai")).append(";")
                .append("Chart.defaults.font.family='Inter,Segoe UI,Arial';Chart.defaults.color='#607286';")
                .append("new Chart(document.getElementById('stokChart'),{type:'bar',data:{labels:stokLabels,datasets:[{label:'Stok gudang',data:stokValues,backgroundColor:'#f4773b',borderRadius:7}]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},scales:{x:{ticks:{autoSkip:false,maxRotation:55,minRotation:25}},y:{beginAtZero:true,title:{display:true,text:'Jumlah stok'}}}}});")
                .append("new Chart(document.getElementById('trendChart'),{type:'line',data:{labels:months,datasets:[{label:'Barang masuk',data:bulanMasuk,borderColor:'#169b69',backgroundColor:'#169b69',tension:.3},{label:'Barang keluar',data:bulanKeluar,borderColor:'#f15a24',backgroundColor:'#f15a24',tension:.3}]},options:{responsive:true,maintainAspectRatio:false,scales:{y:{beginAtZero:true,title:{display:true,text:'Jumlah barang'}}}}});")
                .append("new Chart(document.getElementById('sumberChart'),{type:'pie',data:{labels:sumberLabels,datasets:[{data:sumberValues,backgroundColor:['#f15a24','#203e5d','#f6a15c','#5a9b83','#8b78b8','#e5c45c','#76a9c8']}]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{position:'bottom'}}}});")
                .append("new Chart(document.getElementById('kecamatanChart'),{type:'line',data:{labels:kecLabels,datasets:[{label:'Alokasi logistik',data:kecValues,borderColor:'#203e5d',backgroundColor:'rgba(32,62,93,.12)',fill:true,tension:.25}]},options:{responsive:true,maintainAspectRatio:false,scales:{y:{beginAtZero:true,title:{display:true,text:'Jumlah alokasi'}}}}});")
                .append("function exportTable(id,kind){const t=document.getElementById(id);if(!t)return;let html=t.outerHTML;if(kind==='csv'){let rows=[...t.querySelectorAll('tr')].map(r=>[...r.children].map(c=>'\"'+c.innerText.replaceAll('\"','\"\"')+'\"').join(',')).join('\\n');let blob=new Blob(['\\ufeff'+rows],{type:'text/csv;charset=utf-8;'});let a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='tracking_distribusi_2026.csv';a.click();}else{let blob=new Blob(['<html><meta charset=utf-8>'+html+'</html>'],{type:'application/vnd.ms-excel'});let a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='tracking_distribusi_2026.xls';a.click();}}")
                .append("</script>");
        } catch (Exception e) { isi.append(pesan("Gagal memuat dashboard: " + e.getMessage())); }
        return halaman("Dashboard Logistik", isi.toString());
    }

    private String sqlPeriode(String sql, String start, String endExclusive) {
        return sql.replace("DATE '2026-01-01'", "DATE '"+start+"'")
                  .replace("DATE '2027-01-01'", "DATE '"+endExclusive+"'");
    }

    private String jsonArray(List<Map<String,Object>> rows, String key) {
        List<String> vals=new ArrayList<>(); for(Map<String,Object> r:rows) vals.add(mapper.valueToTree(r.get(key)).toString());
        return "["+String.join(",",vals)+"]";
    }
    private String jsonNumbers(List<Map<String,Object>> rows, String key) {
        List<String> vals=new ArrayList<>(); for(Map<String,Object> r:rows) { Object v=r.get(key); vals.add(v==null?"0":v.toString()); }
        return "["+String.join(",",vals)+"]";
    }
    private String tabelMap(List<Map<String,Object>> rows) {
        if(rows.isEmpty()) return "<div class='empty-state'>Belum ada transaksi pada tahun 2026.</div>";
        StringBuilder h=new StringBuilder("<div class='table-wrap'><table><thead><tr>");
        for(String c:rows.get(0).keySet()) h.append("<th>").append(escape(c)).append("</th>");
        h.append("</tr></thead><tbody>");
        for(Map<String,Object> r:rows){h.append("<tr>");for(Object v:r.values())h.append("<td>").append(escape(String.valueOf(v==null?"-":v))).append("</td>");h.append("</tr>");}
        return h.append("</tbody></table></div>").toString();
    }
    private static final LinkedHashMap<String,String> IN_FIELDS = new LinkedHashMap<>();
    private static final LinkedHashMap<String,String> OUT_FIELDS = new LinkedHashMap<>();
    static {
        IN_FIELDS.put("id_baris_masuk","v.id_baris_masuk");
        IN_FIELDS.put("id_transaksi_masuk","v.id_transaksi_masuk");
        IN_FIELDS.put("tanggal","v.tanggal");
        IN_FIELDS.put("id_barang","v.id_barang");
        IN_FIELDS.put("nama_barang","v.nama_barang");
        IN_FIELDS.put("id_sumber","v.id_sumber");
        IN_FIELDS.put("nama_sumber","v.nama_sumber");
        IN_FIELDS.put("jumlah","v.jumlah");
        IN_FIELDS.put("keterangan","v.keterangan");
        IN_FIELDS.put("status_approval","v.status_approval");

        OUT_FIELDS.put("id_baris_keluar","v.id_baris_keluar");
        OUT_FIELDS.put("id_transaksi_keluar","v.id_transaksi_keluar");
        OUT_FIELDS.put("tanggal_transaksi","v.tanggal_transaksi");
        OUT_FIELDS.put("id_bencana","v.id_bencana");
        OUT_FIELDS.put("nama_bencana","v.nama_bencana");
        OUT_FIELDS.put("id_jenis_bencana","v.id_jenis_bencana");
        OUT_FIELDS.put("tanggal_lapor","v.tanggal_lapor");
        OUT_FIELDS.put("pukul_lapor","v.pukul_lapor");
        OUT_FIELDS.put("tanggal_kejadian","v.tanggal_kejadian");
        OUT_FIELDS.put("pukul_kejadian","v.pukul_kejadian");
        OUT_FIELDS.put("id_wilayah","v.id_wilayah");
        OUT_FIELDS.put("kecamatan","v.kecamatan");
        OUT_FIELDS.put("kelurahan","v.kelurahan");
        OUT_FIELDS.put("kodepos_awal","v.kodepos_awal");
        OUT_FIELDS.put("kodepos_akhir","v.kodepos_akhir");
        OUT_FIELDS.put("alamat","v.alamat");
        OUT_FIELDS.put("rt","v.rt");
        OUT_FIELDS.put("rw","v.rw");
        OUT_FIELDS.put("pemilik_terdampak","v.pemilik_terdampak");
        OUT_FIELDS.put("jumlah_kk","v.jumlah_kk");
        OUT_FIELDS.put("jumlah_jiwa","v.jumlah_jiwa");
        OUT_FIELDS.put("total_kerugian","v.total_kerugian");
        OUT_FIELDS.put("kronologi","v.kronologi");
        OUT_FIELDS.put("korban_ps","v.korban_ps");
        OUT_FIELDS.put("korban_md","v.korban_md");
        OUT_FIELDS.put("korban_lb","v.korban_lb");
        OUT_FIELDS.put("korban_lr","v.korban_lr");
        OUT_FIELDS.put("sumber_info","v.sumber_info");
        OUT_FIELDS.put("no_kontak","v.no_kontak");
        OUT_FIELDS.put("maps","v.maps");
        OUT_FIELDS.put("link_foto","v.link_foto");
        OUT_FIELDS.put("id_barang","v.id_barang");
        OUT_FIELDS.put("nama_barang","v.nama_barang");
        OUT_FIELDS.put("jumlah","v.jumlah");
        OUT_FIELDS.put("status_approval","v.status_approval");
    }

    private String trackingPage(String type, List<String> selected, String startDate, String endDate,
                                List<String> jenis, List<String> barang, List<String> sumber, List<String> status,
                                List<String> kecamatan, List<String> kelurahan, int limit) {
        boolean masuk = "masuk".equalsIgnoreCase(type);
        String safeType = masuk ? "masuk" : "keluar";
        LinkedHashMap<String,String> fields = masuk ? IN_FIELDS : OUT_FIELDS;
        String view = masuk ? "bidang2.v_rujukan_logistik_masuk" : "bidang2.v_rujukan_logistik_keluar";
        String dateColumn = masuk ? "v.tanggal" : "v.tanggal_transaksi";
        String orderColumn = masuk ? "v.id_baris_masuk" : "v.id_baris_keluar";
        List<String> safeSelected=selected.stream().filter(fields::containsKey).distinct().toList();
        if(safeSelected.isEmpty()) safeSelected = masuk
            ? List.of("tanggal","nama_sumber","nama_barang","jumlah","status_approval")
            : List.of("tanggal_transaksi","tanggal_kejadian","nama_bencana","kecamatan","kelurahan","nama_barang","jumlah","status_approval");
        int max=(limit==10||limit==50||limit==100||limit==0)?limit:10;
        List<Object> args=new ArrayList<>();
        StringBuilder where=new StringBuilder(" WHERE "+dateColumn+" >= ? AND "+dateColumn+" <= ? ");
        args.add(java.sql.Date.valueOf(startDate)); args.add(java.sql.Date.valueOf(endDate));
        if(masuk) {
            addInFilter(where,args,"v.id_sumber",sumber);
        } else {
            addInFilter(where,args,"v.id_jenis_bencana",jenis);
            addInFilter(where,args,"v.kecamatan",kecamatan);
            addInFilter(where,args,"v.kelurahan",kelurahan);
        }
        addInFilter(where,args,"v.status_approval",status);
        addInFilter(where,args,"v.id_barang",barang);
        String select=String.join(",",safeSelected.stream().map(c->fields.get(c)+" AS \""+c+"\"").toList());
        String sql="SELECT "+select+" FROM "+view+" v "+where+" ORDER BY "+dateColumn+" DESC,"+orderColumn+" DESC";
        if(max>0) sql+=" LIMIT "+max;
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args.toArray());

        // Header utama sudah disediakan oleh halaman(); hindari header tracking ganda.
        StringBuilder h=new StringBuilder();
        h.append("<div class='panel tracking-panel'><form method='get' action='/tracking' id='trackingForm'>")
         .append("<div class='filter-grid' style='grid-template-columns:repeat(3,minmax(0,1fr));margin-bottom:12px'>")
         .append("<div class='filter-field'><label for='type'>Jenis laporan</label><select id='type' name='type' onchange='this.form.submit()'><option value='masuk' ").append(masuk?"selected":"").append(">Logistik Masuk</option><option value='keluar' ").append(!masuk?"selected":"").append(">Logistik Keluar</option></select></div>")
         .append("<div class='filter-field'><label for='startDate'>Tanggal mulai</label><input id='startDate' type='date' name='startDate' value='").append(escape(startDate)).append("' required></div>")
         .append("<div class='filter-field'><label for='endDate'>Tanggal akhir</label><input id='endDate' type='date' name='endDate' value='").append(escape(endDate)).append("' required></div></div>")
         .append("<details class='filter-drawer'><summary><span class='filter-summary-icon'>⌕</span><span><b>Filter kategori dan kolom laporan</b><small>Sesuaikan isi laporan tanpa berpindah halaman</small></span><span class='summary-end'>BUKA FILTER　⌄</span></summary><div class='filter-body'>");
        h.append("<div class='filter-section-title'>Filter kategori</div><div class='filter-grid'>");
        String barangOpts=filterOptions("SELECT id_barang AS value,nama_barang AS label FROM master.barang ORDER BY nama_barang","value","label",barang);
        String statusOpts=filterOptions("SELECT DISTINCT status_approval AS value,status_approval AS label FROM bidang2.logistik_"+(masuk?"masuk":"keluar")+" ORDER BY status_approval","value","label",status);
        h.append(multiFilter("Nama barang","barang",barangOpts,barang)).append(multiFilter("Status persetujuan","status",statusOpts,status));
        if(masuk){
            String sumberOpts=filterOptions("SELECT id_sumber AS value,nama_sumber AS label FROM master.sumber ORDER BY nama_sumber","value","label",sumber);
            h.append(multiFilter("Sumber logistik","sumber",sumberOpts,sumber));
        } else {
            String jenisOpts=filterOptions("SELECT id_jenis_bencana AS value,nama_bencana AS label FROM master.jenis_bencana ORDER BY nama_bencana","value","label",jenis);
            String kecOpts=filterOptions("SELECT DISTINCT kecamatan AS value,kecamatan AS label FROM master.wilayah WHERE kecamatan IS NOT NULL ORDER BY kecamatan","value","label",kecamatan);
            String kelOpts=filterOptions("SELECT DISTINCT kelurahan AS value,kelurahan AS label FROM master.wilayah WHERE kelurahan IS NOT NULL ORDER BY kelurahan","value","label",kelurahan);
            h.append(multiFilter("Jenis bencana","jenis",jenisOpts,jenis)).append(multiFilter("Kecamatan","kecamatan",kecOpts,kecamatan)).append(multiFilter("Kelurahan","kelurahan",kelOpts,kelurahan));
        }
        h.append("</div><div class='filter-section-title'>Tampilan hasil</div><div class='table-config'><div class='filter-field'><label for='limit'>Jumlah baris</label><select id='limit' name='limit'>")
         .append(optionLimit(10,max)).append(optionLimit(50,max)).append(optionLimit(100,max)).append(optionLimit(0,max)).append("</select></div>")
         .append("<div class='filter-field columns-picker'><label>Variabel yang ditampilkan</label><div class='column-summary' id='columnSummary'>").append(safeSelected.size()).append(" kolom dipilih</div><button type='button' class='secondary-btn' onclick='toggleColumnMenu()'>Pilih variabel　⌄</button><div class='column-menu' id='columnMenu'><div class='column-menu-head'><button type='button' class='secondary-btn' onclick='setColumns(true)'>Pilih semua</button><button type='button' class='secondary-btn' onclick='setColumns(false)'>Kosongkan</button></div><div class='column-options'>");
        String groupNow="";
        for(String c:fields.keySet()){
            String group;
            if(c.startsWith("id_baris")||c.startsWith("id_transaksi")||c.startsWith("tanggal")||c.equals("jumlah")||c.equals("status_approval")||c.equals("keterangan")) group="Informasi Transaksi";
            else if(c.contains("barang")||c.equals("nama_barang")) group="Informasi Barang";
            else if(c.contains("sumber")) group="Informasi Sumber";
            else if(c.contains("bencana")||c.startsWith("korban")||c.contains("kerugian")||c.equals("kronologi")||c.equals("alamat")||c.equals("rt")||c.equals("rw")||c.equals("pemilik_terdampak")||c.equals("jumlah_kk")||c.equals("jumlah_jiwa")||c.equals("sumber_info")||c.equals("no_kontak")||c.equals("maps")||c.equals("link_foto")||c.startsWith("pukul")) group="Informasi Kejadian Bencana";
            else group="Informasi Wilayah";
            if(!group.equals(groupNow)){h.append("<div class='column-group-label'>").append(escape(group)).append("</div>");groupNow=group;}
            h.append("<label class='field-choice'><input type='checkbox' name='columns' value='").append(escape(c)).append("'").append(safeSelected.contains(c)?" checked":"").append("><span>").append(escape(labelField(c))).append("</span></label>");
        }
        h.append("</div></div></div></div><div class='filter-actions'><a class='secondary-btn' href='/tracking?type=").append(safeType).append("'>Reset</a><button type='submit' class='primary-btn'>Terapkan filter</button></div></div></details></form></div>");
        h.append("<div class='panel results-panel'><div class='results-head'><div><span class='section-kicker'>HASIL FILTER · DATA SESUAI PILIHAN</span><h2 style='display:none'>Laporan Logistik ").append(masuk?"Masuk":"Keluar").append("</h2><p class='panel-subtitle'>Sumber data: ").append(masuk?"bidang2.v_rujukan_logistik_masuk":"bidang2.v_rujukan_logistik_keluar").append(".</p></div><div class='export-actions'><button type='button' class='secondary-btn' onclick='exportTracking(\"csv\")'>Export CSV</button><button type='button' class='primary-btn' onclick='exportTracking(\"xlsx\")'>Export XLSX</button></div></div>");
        if(rows.isEmpty()) h.append("<div class='empty-state'>Tidak ada data yang sesuai dengan pilihan filter.</div>");
        else {
            h.append("<div class='table-meta'>Menampilkan <b>").append(rows.size()).append("</b> baris · ").append(max==0?"Semua data":"Maksimal "+max+" baris").append(" · ").append(safeSelected.size()).append(" kolom</div><div class='table-wrap'><table id='trackingTable'><thead><tr>");
            for(String c:safeSelected) h.append("<th>").append(escape(labelField(c))).append("</th>");
            h.append("</tr></thead><tbody>");
            for(Map<String,Object> r:rows){h.append("<tr>");for(String c:safeSelected){Object v=r.get(c);h.append("<td>").append(escape(v==null?"-":String.valueOf(v))).append("</td>");}h.append("</tr>");}
            h.append("</tbody></table></div>");
        }
        h.append("</div>");
        h.append("<script src='https://cdn.jsdelivr.net/npm/xlsx@0.18.5/dist/xlsx.full.min.js'></script>");
        h.append("""
          <script>
          function filterSelectAll(btn,on){const p=btn.closest('.filter-popover');if(!p)return;p.querySelectorAll('input[type=checkbox]').forEach(x=>x.checked=on);const d=p.closest('details');const n=p.querySelectorAll('input:checked').length;d.querySelector('summary span').textContent=n?`${n} dipilih`:'Semua';}
          document.querySelectorAll('.filter-select').forEach(d=>{d.querySelectorAll('input').forEach(x=>x.addEventListener('change',()=>{const n=d.querySelectorAll('input:checked').length;d.querySelector('summary span').textContent=n?`${n} dipilih`:'Semua';}));});
          function toggleColumnMenu(){document.getElementById('columnMenu').classList.toggle('show');}
          function setColumns(on){document.querySelectorAll('.column-options input').forEach(x=>x.checked=on);updateColumnCount();}
          function updateColumnCount(){const n=document.querySelectorAll('.column-options input:checked').length;document.getElementById('columnSummary').textContent=n+' kolom dipilih';}
          document.querySelectorAll('.column-options input').forEach(x=>x.addEventListener('change',updateColumnCount));
          function exportTracking(kind){const t=document.getElementById('trackingTable');if(!t)return;if(kind==='xlsx'){if(!window.XLSX){alert('Pustaka Excel belum termuat. Periksa koneksi internet.');return;}const wb=XLSX.utils.table_to_book(t,{sheet:'Laporan Logistik'});XLSX.writeFile(wb,'laporan_logistik.xlsx');return;}const rows=[...t.querySelectorAll('tr')].map(r=>[...r.children].map(c=>'"'+c.innerText.replaceAll('"','""')+'"').join(',')).join('\\n');const blob=new Blob(['\\ufeff'+rows],{type:'text/csv;charset=utf-8;'});const a=document.createElement('a'),u=URL.createObjectURL(blob);a.href=u;a.download='laporan_logistik.csv';a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);}
          </script>
          """);
        return halaman("Tracking Logistik",h.toString());
    }

    @GetMapping("/tracking")
    @ResponseBody
    public String tracking(
            @RequestParam(defaultValue="masuk") String type,
            @RequestParam(required=false) List<String> columns,
            @RequestParam(defaultValue="2026-01-01") String startDate,
            @RequestParam(defaultValue="2026-12-31") String endDate,
            @RequestParam(required=false) List<String> jenis,
            @RequestParam(required=false) List<String> barang,
            @RequestParam(required=false) List<String> sumber,
            @RequestParam(required=false) List<String> status,
            @RequestParam(required=false) List<String> kecamatan,
            @RequestParam(required=false) List<String> kelurahan,
            @RequestParam(defaultValue="10") int limit) {
        try {
            LocalDate start=LocalDate.parse(startDate), end=LocalDate.parse(endDate);
            if(end.isBefore(start)) return halaman("Tracking Logistik",pesan("Tanggal akhir tidak boleh lebih awal dari tanggal mulai."));
            if(!type.equalsIgnoreCase("masuk")&&!type.equalsIgnoreCase("keluar")) type="masuk";
            return trackingPage(type,columns==null?List.of():columns,start.toString(),end.toString(),
                    jenis==null?List.of():jenis,barang==null?List.of():barang,sumber==null?List.of():sumber,status==null?List.of():status,
                    kecamatan==null?List.of():kecamatan,kelurahan==null?List.of():kelurahan,limit);
        } catch(Exception e) {
            return halaman("Tracking Logistik",pesan("Gagal memuat tracking: "+e.getMessage()));
        }
    }

    private void addInFilter(StringBuilder where,List<Object> args,String column,List<String> values) {
        if(values==null || values.isEmpty()) return;
        where.append(" AND ").append(column).append(" IN (")
             .append(String.join(",",Collections.nCopies(values.size(),"?"))).append(") ");
        args.addAll(values);
    }
    private String optionLimit(int value,int selected) {
        String label=value==0?"Semua":String.valueOf(value);
        return "<option value='"+value+"'"+(value==selected?" selected":"")+">"+label+"</option>";
    }
    private String labelField(String key) {
        return switch(key) {
            case "id_baris_keluar" -> "ID Baris Keluar";
            case "id_transaksi_keluar" -> "ID Transaksi Keluar";
            case "tanggal_transaksi" -> "Tanggal Transaksi Keluar";
            case "tanggal" -> "Tanggal Penerimaan";
            case "id_transaksi_masuk" -> "ID Transaksi Masuk";
            case "id_baris_masuk" -> "ID Baris Masuk";
            case "jumlah" -> "Jumlah Barang";
            case "keterangan" -> "Keterangan";
            case "id_bencana" -> "ID Bencana";
            case "nama_bencana" -> "Nama Bencana";
            case "id_jenis_bencana" -> "ID Jenis Bencana";
            case "tanggal_lapor" -> "Tanggal Lapor";
            case "pukul_lapor" -> "Waktu Lapor";
            case "tanggal_kejadian" -> "Tanggal Kejadian";
            case "pukul_kejadian" -> "Waktu Kejadian";
            case "id_wilayah" -> "ID Wilayah";
            case "kecamatan" -> "Kecamatan";
            case "kelurahan" -> "Kelurahan";
            case "kodepos_awal" -> "Kode Pos Awal";
            case "kodepos_akhir" -> "Kode Pos Akhir";
            case "pemilik_terdampak" -> "Pemilik Terdampak";
            case "jumlah_kk" -> "Jumlah KK";
            case "jumlah_jiwa" -> "Jumlah Jiwa";
            case "total_kerugian" -> "Total Kerugian";
            case "korban_ps" -> "Korban PS";
            case "korban_md" -> "Korban MD";
            case "korban_lb" -> "Korban LB";
            case "korban_lr" -> "Korban LR";
            case "sumber_info" -> "Sumber Informasi";
            case "no_kontak" -> "Nomor Kontak";
            case "id_barang" -> "ID Barang";
            case "nama_barang" -> "Nama Barang";
            case "id_sumber" -> "ID Sumber";
            case "nama_sumber" -> "Sumber Logistik";
            case "jumlah_logistik" -> "Jumlah Logistik";
            case "status_approval" -> "Status Approval";
            default -> key.replace('_',' ').toUpperCase(Locale.ROOT);
        };
    }

    private String panelTracking(LocalDate periodeMulai, LocalDate periodeAkhir) {
        List<Map<String,Object>> preview=jdbc.queryForList(sqlPeriode("""
            SELECT tanggal_transaksi, tanggal_kejadian, nama_bencana, kecamatan, kelurahan,
                   nama_barang, jumlah, keterangan, status_approval
            FROM bidang2.v_rujukan_logistik_keluar
            WHERE tanggal_transaksi >= DATE '2026-01-01' AND tanggal_transaksi < DATE '2027-01-01'
            ORDER BY tanggal_transaksi DESC,id_baris_keluar DESC LIMIT 10
            """,periodeMulai.toString(),periodeAkhir.toString()));
        StringBuilder h=new StringBuilder("<div class='panel tracking-preview'><div class='results-head'><div><h2>Tracking Laporan Logistik</h2><p class='panel-subtitle'>Susun laporan logistik masuk maupun keluar berdasarkan variabel rujukan masing-masing.</p></div><a class='primary-btn' href='/tracking'>Buka tracking lengkap　→</a></div>");
        h.append(tabelMap(preview)).append("</div>");
        return h.toString();
    }

    @GetMapping("/kejadian")
    @ResponseBody
    public String kejadian() {
        try {
            JsonNode data=ambilData("kejadian_bencana");
            String isi="<div class='grid'>"+kartu("⚠","Total Kejadian",data.size())+"</div>"
                +"<div class='panel'><div class='panel-head'><h2>Daftar Kejadian Bencana</h2><a class='button' href='/input-kejadian'>+ Input Kejadian</a></div>"
                +tabelJson(data)+"</div>";
            return halaman("Dashboard Kebencanaan",isi);
        } catch(Exception e) { return halaman("Dashboard Kebencanaan",pesan(e.getMessage())); }
    }

    @GetMapping("/input-kejadian")
    @ResponseBody
    public String formKejadian() {
        try {
            String isi="""
            <div class='panel'><h2>Formulir Kejadian Bencana</h2>
            <form method='post' action='/input-kejadian'>
            <label>Jenis Bencana</label><select name='id_jenis_bencana' required>%s</select>
            <label>Wilayah</label><select name='id_wilayah' required>%s</select>
            <label>Tanggal Lapor</label><input type='date' name='tanggal_lapor' value='%s' required>
            <label>Waktu Lapor</label><input type='time' name='pukul_lapor' value='%s'>
            <label>Tanggal Kejadian</label><input type='date' name='tanggal_kejadian' value='%s' required>
            <label>Waktu Kejadian</label><input type='time' name='pukul_kejadian'>
            <label>Alamat</label><textarea name='alamat' required></textarea>
            <label>RT</label><input name='rt'><label>RW</label><input name='rw'>
            <label>Pemilik Terdampak</label><input name='pemilik_terdampak'>
            <label>Jumlah KK</label><input type='number' name='jumlah_kk' min='0' value='0'>
            <label>Jumlah Jiwa</label><input type='number' name='jumlah_jiwa' min='0' value='0'>
            <label>Total Kerugian</label><input type='number' step='0.01' name='total_kerugian' min='0' value='0'>
            <label>Kronologi</label><textarea name='kronologi'></textarea>
            <label>Korban PS</label><input type='number' name='korban_ps' min='0' value='0'>
            <label>Korban MD</label><input type='number' name='korban_md' min='0' value='0'>
            <label>Korban LB</label><input type='number' name='korban_lb' min='0' value='0'>
            <label>Korban LR</label><input type='number' name='korban_lr' min='0' value='0'>
            <label>Sumber Informasi</label><input name='sumber_info'>
            <label>Nomor Kontak</label><input name='no_kontak'>
            <label>Maps</label><input name='maps' type='url'>
            <label>Link Foto</label><input name='link_foto' type='url'>
            <button type='submit'>Simpan Kejadian</button></form></div>
            """.formatted(opsiMaster("jenis_bencana","id_jenis_bencana","nama_bencana",""),
                opsiMaster("wilayah","id_wilayah","id_wilayah",""),LocalDate.now(),LocalTime.now().withSecond(0).withNano(0),LocalDate.now());
            return halaman("Input Kejadian",isi);
        } catch(Exception e) { return halaman("Input Kejadian",pesan(e.getMessage())); }
    }

    @GetMapping("/kejadian-baru")
    @ResponseBody
    public String halamanKejadianBaruPlaceholder() {
        String isi="<div class='panel'><h2>Input Kejadian Bencana</h2><p class='muted'>Halaman ini disiapkan sebagai titik integrasi formulir kejadian bencana yang sedang dikembangkan pada modul lain.</p><div class='notice'>Formulir kejadian baru belum dihubungkan. Silakan kembali dan pilih kejadian yang sudah tercatat.</div><a class='secondary-btn' href='/input-transaksi?jenis=Keluar'>Kembali ke transaksi keluar</a></div>";
        return halaman("Input Kejadian Baru",isi);
    }

    @PostMapping("/input-kejadian")
    @ResponseBody
    public String simpanKejadian(@RequestParam String id_jenis_bencana, @RequestParam String id_wilayah,
            @RequestParam String tanggal_lapor, @RequestParam(defaultValue="00:00") String pukul_lapor,
            @RequestParam String tanggal_kejadian, @RequestParam(defaultValue="00:00") String pukul_kejadian,
            @RequestParam String alamat, @RequestParam(defaultValue="") String rt,
            @RequestParam(defaultValue="") String rw, @RequestParam(defaultValue="") String pemilik_terdampak,
            @RequestParam(defaultValue="0") int jumlah_kk, @RequestParam(defaultValue="0") int jumlah_jiwa,
            @RequestParam(defaultValue="0") double total_kerugian, @RequestParam(defaultValue="") String kronologi,
            @RequestParam(defaultValue="0") int korban_ps, @RequestParam(defaultValue="0") int korban_md,
            @RequestParam(defaultValue="0") int korban_lb, @RequestParam(defaultValue="0") int korban_lr,
            @RequestParam(defaultValue="") String sumber_info, @RequestParam(defaultValue="") String no_kontak,
            @RequestParam(defaultValue="") String maps, @RequestParam(defaultValue="") String link_foto) {
        try {
            String idBencana=idAcak("B",10);
            jdbc.update("""
                INSERT INTO bidang2.kejadian_bencana
                (id_bencana,id_jenis_bencana,tanggal_lapor,pukul_lapor,tanggal_kejadian,pukul_kejadian,id_wilayah,
                 alamat,rt,rw,pemilik_terdampak,jumlah_kk,jumlah_jiwa,total_kerugian,kronologi,
                 korban_ps,korban_md,korban_lb,korban_lr,sumber_info,no_kontak,maps,link_foto)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, idBencana,id_jenis_bencana,java.sql.Date.valueOf(tanggal_lapor),java.sql.Time.valueOf(pukul_lapor+ (pukul_lapor.length()==5?":00":"")),
                java.sql.Date.valueOf(tanggal_kejadian),java.sql.Time.valueOf(pukul_kejadian+ (pukul_kejadian.length()==5?":00":"")),id_wilayah,
                alamat,rt,rw,pemilik_terdampak,jumlah_kk,jumlah_jiwa,total_kerugian,kronologi,korban_ps,korban_md,korban_lb,korban_lr,sumber_info,no_kontak,maps,link_foto);
            return halaman("Berhasil",pesan("Kejadian berhasil disimpan dengan ID "+idBencana));
        } catch(Exception e) { return halaman("Gagal",pesan("Gagal menyimpan kejadian: "+e.getMessage())); }
    }

    private String opsiBencana() {
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT k.id_bencana,k.tanggal_kejadian,j.nama_bencana,w.kecamatan,w.kelurahan,
                   k.sumber_info,k.pemilik_terdampak,k.jumlah_kk,k.jumlah_jiwa
            FROM bidang2.kejadian_bencana k
            INNER JOIN master.jenis_bencana j ON j.id_jenis_bencana=k.id_jenis_bencana
            INNER JOIN master.wilayah w ON w.id_wilayah=k.id_wilayah
            ORDER BY k.tanggal_kejadian DESC,k.pukul_kejadian DESC,k.tanggal_lapor DESC,k.pukul_lapor DESC,k.id_bencana DESC
            """);
        StringBuilder h=new StringBuilder("<option value=''>Pilih kejadian bencana...</option><option value='__NEW__'>＋ Input kejadian bencana baru</option>");
        for(Map<String,Object> r:rows){
            String id=String.valueOf(r.get("id_bencana"));
            String tanggal=String.valueOf(r.get("tanggal_kejadian"));
            String nama=String.valueOf(r.get("nama_bencana"));
            String kec=String.valueOf(r.get("kecamatan")); String kel=String.valueOf(r.get("kelurahan"));
            String pelapor=String.valueOf(r.get("sumber_info"));
            String terdampak=String.valueOf(r.get("pemilik_terdampak"));
            String kk=String.valueOf(r.get("jumlah_kk")); String jiwa=String.valueOf(r.get("jumlah_jiwa"));
            h.append("<option value='").append(escape(id)).append("' data-tanggal='").append(escape(tanggal))
             .append("' data-nama='").append(escape(nama)).append("' data-kecamatan='").append(escape(kec))
             .append("' data-kelurahan='").append(escape(kel)).append("' data-pelapor='").append(escape(pelapor))
             .append("' data-terdampak='").append(escape(terdampak)).append("' data-kk='").append(escape(kk)).append("' data-jiwa='").append(escape(jiwa))
             .append("'>").append(escape(tanggal+" · "+nama+" · "+kec+" / "+kel+" · "+id)).append("</option>");
        }
        return h.toString();
    }

    private String itemRow(String barangOptions) {
        return "<div class='item-row'><div><label>Nama barang</label><select name='id_barang' required>"+barangOptions+"</select></div>"+
               "<div><label>Jumlah</label><input type='number' name='jumlah' min='0.01' step='0.01' placeholder='0' required></div>"+
               "<button type='button' class='remove-item' onclick='removeItem(this)' title='Hapus barang'>×</button></div>";
    }

    @GetMapping("/input-transaksi")
    @ResponseBody
    public String formTransaksi(@RequestParam(required=false) String jenis) {
        if(jenis==null||jenis.isBlank()) return halaman("Transaksi Logistik","""
            <div class='form-shell'><div class='choice-grid'>
            <a class='choice-card choice-in' href='/input-transaksi?jenis=Masuk'><div class='choice-icon'>↓</div><h2>Barang Masuk</h2><p>Catat penerimaan barang dari sumber bantuan.</p></a>
            <a class='choice-card choice-out' href='/input-transaksi?jenis=Keluar'><div class='choice-icon'>↑</div><h2>Barang Keluar</h2><p>Catat penyaluran barang untuk kejadian bencana yang sudah terdaftar.</p></a></div></div>
            """);
        boolean masuk="Masuk".equalsIgnoreCase(jenis);
        if(!masuk&& !"Keluar".equalsIgnoreCase(jenis)) return halaman("Transaksi",pesan("Jenis transaksi tidak valid."));
        try {
            String barangOptions=opsiMaster("barang","id_barang","nama_barang","");
            StringBuilder f=new StringBuilder("<div class='form-shell'><div class='form-title-row'><div class='form-title-icon "+(masuk?"in":"out")+"'>"+(masuk?"↓":"↑")+"</div><div><div class='section-kicker'>FORM TRANSAKSI LOGISTIK</div><h2>Barang "+(masuk?"Masuk":"Keluar")+"</h2><p class='muted'>Isi informasi utama, lalu tambahkan satu atau beberapa jenis barang.</p></div></div>");
            f.append("<form method='post' action='/input-transaksi' id='transactionForm'><input type='hidden' name='jenis' value='").append(masuk?"Masuk":"Keluar").append("'>");
            f.append("<section class='form-section'><h3>01 · Informasi transaksi</h3><p>Informasi sumber dan tanggal pencatatan.</p><div class='form-grid'>");
            if(masuk) {
                f.append("<div class='form-field'><label>Tanggal penerimaan</label><input type='date' name='tanggal' value='").append(LocalDate.now()).append("' required></div>");
                f.append("<div class='form-field'><label>Sumber logistik</label><select name='id_sumber' required>").append(opsiMaster("sumber","id_sumber","nama_sumber","")).append("</select></div>");
            } else {
                f.append("<div class='form-field'><label>Tanggal transaksi keluar</label><input type='date' name='tanggal_transaksi' value='").append(LocalDate.now()).append("' required></div>");
            }
            f.append("</div></section>");
            if(!masuk){
                JsonNode bencana=ambilData("kejadian_bencana");
                f.append("<section class='form-section'><h3>02 · Tujuan penyaluran</h3><p>Pilih kejadian bencana yang sudah terdaftar. Periksa tanggal, jenis, wilayah, dan pelapor sebelum menyimpan.</p>");
                if(bencana.isEmpty()) f.append("<div class='notice'>Belum ada kejadian bencana yang tercatat. Input kejadian bencana terlebih dahulu melalui modul kejadian.</div><a class='secondary-btn' href='/kejadian-baru'>＋ Input kejadian bencana baru</a>");
                else f.append("<label>Kejadian bencana</label><select name='id_bencana' id='disasterSelect' required>").append(opsiBencana()).append("</select><div class='disaster-preview' id='disasterPreview'><div><small>Tanggal kejadian</small><strong id='dTanggal'>Belum dipilih</strong></div><div><small>Jenis bencana</small><strong id='dNama'>-</strong></div><div><small>Wilayah</small><strong id='dWilayah'>-</strong></div><div><small>Pelapor / sumber info</small><strong id='dPelapor'>-</strong></div><div><small>Pemilik terdampak</small><strong id='dTerdampak'>-</strong></div><div><small>KK / jiwa</small><strong id='dKorban'>-</strong></div></div>");
                f.append("</section>");
            }
            f.append("<section class='form-section'><h3>"+(masuk?"02":"03")+" · Daftar barang</h3><p>Tambahkan beberapa barang dalam satu transaksi. Setiap barang disimpan sebagai baris transaksi dengan ID transaksi yang sama.</p><div id='itemsContainer'>").append(itemRow(barangOptions)).append("</div><button type='button' class='secondary-btn' onclick='addItem()'>+ Tambah barang</button></section>");
            if(masuk) f.append("<section class='form-section'><h3>03 · Catatan tambahan</h3><p>Bagian ini opsional.</p><label>Keterangan</label><textarea name='keterangan' placeholder='Catatan penerimaan barang (opsional)'></textarea><label>Foto bukti penerimaan</label><input type='file' accept='image/*' disabled><div class='helper-note'>Fitur foto disiapkan untuk rancangan antarmuka dan belum aktif karena penyimpanan foto belum disiapkan.</div></section>");
            else f.append("<section class='form-section'><h3>04 · Catatan dan dokumentasi</h3><label>Keterangan penyaluran (opsional)</label><textarea name='keterangan' placeholder='Catatan tambahan penyaluran logistik'></textarea><label>Foto bukti penyaluran</label><input type='file' accept='image/*' disabled><div class='helper-note'>Komponen foto masih nonaktif. Keterangan akan disimpan pada kolom transaksi.</div></section>");
            f.append("<div class='form-actions'><a class='secondary-btn' href='/input-transaksi'>Batal</a><button type='submit' class='primary-btn' ").append(!masuk&&bencanaKosong()?"disabled":"").append(">Simpan transaksi</button></div></form></div>");
            f.append("""
              <script>
              const barangOptionHtml=document.querySelector('#itemsContainer .item-row select').innerHTML;
              function addItem(){const wrap=document.createElement('div');wrap.innerHTML=`<div class="item-row"><div><label>Nama barang</label><select name="id_barang" required>${barangOptionHtml}</select></div><div><label>Jumlah</label><input type="number" name="jumlah" min="0.01" step="0.01" placeholder="0" required></div><button type="button" class="remove-item" onclick="removeItem(this)" title="Hapus barang">×</button></div>`;document.getElementById('itemsContainer').appendChild(wrap.firstElementChild);}
              function removeItem(btn){const rows=document.querySelectorAll('#itemsContainer .item-row');if(rows.length>1)btn.closest('.item-row').remove();}
              const ds=document.getElementById('disasterSelect');if(ds)ds.addEventListener('change',()=>{const o=ds.selectedOptions[0];if(o.value==='__NEW__'){window.location.href='/kejadian-baru';return;}document.getElementById('dTanggal').textContent=o.dataset.tanggal||'Belum dipilih';document.getElementById('dNama').textContent=o.dataset.nama||'-';document.getElementById('dWilayah').textContent=o.dataset.kecamatan?(o.dataset.kecamatan+' / '+o.dataset.kelurahan):'-';document.getElementById('dPelapor').textContent=o.dataset.pelapor||'-';document.getElementById('dTerdampak').textContent=o.dataset.terdampak||'-';document.getElementById('dKorban').textContent=(o.dataset.kk||'0')+' KK / '+(o.dataset.jiwa||'0')+' jiwa';});
              document.getElementById('transactionForm').addEventListener('submit',e=>{const rows=[...document.querySelectorAll('#itemsContainer .item-row')];if(!rows.length||rows.some(r=>!r.querySelector('select').value||!(Number(r.querySelector('input').value)>0))){e.preventDefault();alert('Lengkapi nama barang dan jumlah untuk setiap baris.');}});
              </script>
              """);
            return halaman(masuk?"Barang Masuk":"Barang Keluar",f.toString());
        } catch(Exception e) { return halaman("Transaksi",pesan("Gagal menyiapkan form: "+e.getMessage())); }
    }

    private boolean bencanaKosong() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bidang2.kejadian_bencana",Integer.class)==0;
    }

    @PostMapping("/input-transaksi")
    @ResponseBody
    @Transactional
    public String simpanTransaksi(@RequestParam String jenis,
            @RequestParam(required=false) String tanggal,
            @RequestParam(required=false) String tanggal_transaksi,
            @RequestParam(required=false) String id_sumber,
            @RequestParam(name="id_barang") List<String> idBarang,
            @RequestParam(name="jumlah") List<BigDecimal> jumlah,
            @RequestParam(defaultValue="") String id_bencana,
            @RequestParam(defaultValue="") String keterangan) {
        try {
            if(idBarang==null||jumlah==null||idBarang.isEmpty()||idBarang.size()!=jumlah.size()) throw new IllegalArgumentException("Tambahkan minimal satu barang dan isi jumlahnya.");
            for(int i=0;i<idBarang.size();i++) if(idBarang.get(i)==null||idBarang.get(i).isBlank()||jumlah.get(i)==null||jumlah.get(i).compareTo(BigDecimal.ZERO)<=0) throw new IllegalArgumentException("Periksa pilihan barang dan jumlah pada setiap baris.");
            if("Masuk".equalsIgnoreCase(jenis)) {
                if(tanggal==null||tanggal.isBlank()) throw new IllegalArgumentException("Tanggal penerimaan wajib diisi.");
                if(id_sumber==null||id_sumber.isBlank()) throw new IllegalArgumentException("Pilih sumber logistik.");
                String trx=idAcak("T",15);
                for(int i=0;i<idBarang.size();i++) jdbc.update("""
                    INSERT INTO bidang2.logistik_masuk (id,tanggal,id_barang,id_sumber,jumlah,keterangan,status_approval)
                    VALUES (?,?,?,?,?,?,?)
                    """,trx,java.sql.Date.valueOf(tanggal),idBarang.get(i),id_sumber,jumlah.get(i),keterangan,"Menunggu");
                return halaman("Transaksi Tersimpan",pesan("Barang masuk berhasil dicatat dalam satu transaksi dengan "+idBarang.size()+" jenis barang. Status: Menunggu persetujuan."));
            }
            if("Keluar".equalsIgnoreCase(jenis)) {
                if(id_bencana.isBlank()) throw new IllegalArgumentException("Pilih kejadian bencana tujuan distribusi.");
                Integer valid=jdbc.queryForObject("SELECT COUNT(*) FROM bidang2.kejadian_bencana WHERE id_bencana=?",Integer.class,id_bencana);
                if(valid==null||valid==0) throw new IllegalArgumentException("Kejadian bencana tidak ditemukan. Pilih kejadian yang terdaftar.");
                if(tanggal_transaksi==null||tanggal_transaksi.isBlank()) throw new IllegalArgumentException("Tanggal transaksi keluar wajib diisi.");
                String trx=idAcak("T",10);
                for(int i=0;i<idBarang.size();i++) jdbc.update("""
                    INSERT INTO bidang2.logistik_keluar (id,tanggal_transaksi,id_bencana,id_barang,jumlah,keterangan,status_approval)
                    VALUES (?,?,?,?,?,?,?)
                    """,trx,java.sql.Date.valueOf(tanggal_transaksi),id_bencana,idBarang.get(i),jumlah.get(i),keterangan,"Menunggu");
                return halaman("Transaksi Tersimpan",pesan("Distribusi berhasil dicatat untuk kejadian "+id_bencana+" dengan "+idBarang.size()+" jenis barang. Status: Menunggu persetujuan."));
            }
            throw new IllegalArgumentException("Jenis transaksi tidak valid.");
        } catch(Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return halaman("Transaksi Gagal",pesan("Gagal menyimpan transaksi: "+e.getMessage()));
        }
    }

    @GetMapping("/data")
    @ResponseBody
    public String dataTersimpan(@RequestParam(defaultValue="kejadian_bencana") String table) {
        Set<String> allowed=Set.of("barang","jenis_bencana","wilayah","sumber","kejadian_bencana","logistik_masuk","logistik_keluar","v_rujukan_logistik_masuk","v_rujukan_logistik_keluar");
        if(!allowed.contains(table)) return halaman("Data",pesan("Tabel tidak diizinkan."));
        try {
            JsonNode rows=ambilData(table);
            StringBuilder options=new StringBuilder();
            for(String t:allowed) options.append("<option value='").append(t).append("'").append(t.equals(table)?" selected":"").append(">").append(t).append("</option>");
            String isi="<div class='panel'><h2>Data Database</h2><form method='get' action='/data'><label>Pilih tabel</label><select name='table'>"+options+"</select><button>Tampilkan</button></form><p class='muted'>"+escape(table)+" · "+rows.size()+" baris</p>"+tabelJson(rows)+"</div>";
            return halaman("Data Tersimpan",isi);
        } catch(Exception e) { return halaman("Data",pesan(e.getMessage())); }
    }

    private String tabelJson(JsonNode rows) {
        if(rows==null || !rows.isArray() || rows.isEmpty()) return "<div class='empty-state'>Belum ada data.</div>";
        LinkedHashSet<String> cols=new LinkedHashSet<>();
        rows.forEach(r -> r.fieldNames().forEachRemaining(cols::add));
        StringBuilder h=new StringBuilder("<div class='table-wrap'><table><thead><tr>");
        cols.forEach(c -> h.append("<th>").append(escape(c)).append("</th>"));
        h.append("</tr></thead><tbody>");
        for(JsonNode r:rows) { h.append("<tr>"); for(String c:cols) { JsonNode v=r.get(c); h.append("<td>").append(escape(v==null||v.isNull()?"-":v.asText())).append("</td>"); } h.append("</tr>"); }
        return h.append("</tbody></table></div>").toString();
    }

    // Helper pembentuk opsi filter tracking.
    private String filterOptions(String sql, String valueCol, String labelCol, List<String> selected) {
        List<Map<String,Object>> opts = jdbc.queryForList(sql);
        StringBuilder out = new StringBuilder();
        for (Map<String,Object> row : opts) {
            Object rawValue = row.get(valueCol);
            if (rawValue == null) continue;
            String value = String.valueOf(rawValue);
            String label = String.valueOf(row.get(labelCol));
            out.append("<label><input type=\"checkbox\" name=\"__NAME__\" value=\"")
               .append(escape(value)).append("\"")
               .append(selected != null && selected.contains(value) ? " checked" : "")
               .append("><span>").append(escape(label)).append("</span></label>");
        }
        return out.toString();
    }

    private String multiFilter(String label, String name, String options, List<String> selected) {
        String content = options.replace("__NAME__", escape(name));
        String summary = selected == null || selected.isEmpty() ? "Semua" : selected.size() + " dipilih";
        return "<div class=\"filter-field\"><label>" + escape(label) +
               "</label><details class=\"filter-select\"><summary><span>" + summary +
               "</span></summary><div class=\"filter-popover\"><div class=\"pop-actions\">" +
               "<button type=\"button\" class=\"secondary-btn\" onclick=\"filterSelectAll(this,true)\">Pilih semua</button>" +
               "<button type=\"button\" class=\"secondary-btn\" onclick=\"filterSelectAll(this,false)\">Kosongkan</button>" +
               "</div>" + content + "</div></details></div>";
    }

}
